package com.rag.rag.ingestion.service;

import com.rag.rag.ingestion.detector.ROIDetector;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class IngestionService {

    @Value("${vision.prompt}")
    private String VISION_PROMPT;

    @Value("${vision.image.max-width:800}")
    private int MAX_WIDTH;

    @Value("${vision.image.max-height:800}")
    private int MAX_HEIGHT;

    private final ChatLanguageModel visionModel;
    private final EmbeddingStoreIngestor ingestor;
    private final ROIDetector detector;
    private final FileRepository fileRepository;

    public IngestionService(
            @Qualifier("visionModel") ChatLanguageModel visionModel,
            EmbeddingStoreIngestor ingestor,
            ROIDetector detector,
            FileRepository fileRepository
    ) {
        this.visionModel = visionModel;
        this.ingestor = ingestor;
        this.detector = detector;
        this.fileRepository = fileRepository;
    }

    private Document chooseParser(byte[] fileData, String path, String fileName, String extension) {
        if (extension == null) return null;

        return switch (extension.toLowerCase()) {
            case "txt" -> processTextFile(fileData, path, fileName);
            case "pdf" -> processPdfFile(fileData, path, fileName);
            case "png", "jpg", "jpeg" -> processImage(fileData, path, fileName);
            default -> {
                log.warn("Skipped file: {}, unsupported file extension.", path);
                yield null;
            }
        };
    }

    private void ensureFileEntityExists(String path, String fileName, String mimeType, byte[] imageData) {
        if (fileRepository.findByPath(path).isEmpty()) {
            FileEntity entity = FileEntity.builder()
                    .path(path)
                    .fileName(fileName)
                    .fileType(mimeType)
                    .imageData(imageData)
                    .build();
            fileRepository.save(entity);
        }
    }

    public Document processTextFile(byte[] fileData, String path, String fileName) {
        try (InputStream stream = new ByteArrayInputStream(fileData)) {
            ensureFileEntityExists(path, fileName, "text/plain", null);
            return new TextDocumentParser().parse(stream);
        } catch (Exception e) {
            throw new RuntimeException("Error while processing text file: " + path, e);
        }
    }

    public Document processPdfFile(byte[] fileData, String path, String fileName) {
        try (InputStream stream = new ByteArrayInputStream(fileData)) {
            ensureFileEntityExists(path, fileName, "application/pdf", null);
            return new ApacheTikaDocumentParser().parse(stream);
        } catch (Exception e) {
            throw new RuntimeException("Error while processing pdf file: " + path, e);
        }
    }

    public Document processImage(byte[] imageData, String path, String fileName) {
        try {
            String format = fileName.toLowerCase().endsWith(".png") ? "png" : "jpg";
            String mimeType = "image/" + format;

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(imageData))
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .outputFormat(format)
                    .toOutputStream(outputStream);

            byte[] processedBytes = outputStream.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(processedBytes);

            ensureFileEntityExists(path, fileName, mimeType, processedBytes);

            UserMessage message = UserMessage.from(
                    TextContent.from(VISION_PROMPT),
                    ImageContent.from(base64Image, mimeType)
            );

            return Document.from(visionModel.generate(message).content().text());

        } catch (Exception e) {
            throw new RuntimeException("Error while processing image: " + path, e);
        }
    }

    @Transactional
    public void ingestMultipartFile(MultipartFile file, FolderEntity folder) throws IOException {
        String fileName = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(fileName);
        String group = folder.getName();

        String path = "dir://" + folder.getName() + "/" + fileName;
        byte[] data = file.getBytes();

        Document parsedDocument = chooseParser(data, path, fileName, extension);
        if (parsedDocument != null) {
            parsedDocument.metadata()
                    .put("document_id", group)
                    .put("filename", fileName)
                    .put("path", path);
            ingestor.ingest(parsedDocument);
            log.info("Successfully ingested file: {}", path);
        }
    }

    public SourceDto createSourceDto(String path, String metadataFileName, Double score) {
        if (path == null) {
            return new SourceDto(null, metadataFileName, score, null, "OTHER");
        }

        String base64 = null;
        String type = "OTHER";
        String finalFileName = metadataFileName != null ? metadataFileName : path;

        var fileOpt = fileRepository.findByPath(path);
        if (fileOpt.isPresent()) {
            FileEntity file = fileOpt.get();
            finalFileName = file.getFileName();
            String mimeType = file.getFileType().toLowerCase();
            
            if (mimeType.contains("image")) {
                type = "IMAGE";
                if (file.getImageData() != null) {
                    base64 = Base64.getEncoder().encodeToString(file.getImageData());
                }
            } else if (mimeType.contains("pdf")) {
                type = "PDF";
            } else if (mimeType.contains("txt") || mimeType.equals("text/plain")) {
                type = "TEXT";
            }
        } else {
            String lowerPath = path.toLowerCase();
            if (lowerPath.endsWith(".pdf")) type = "PDF";
            else if (lowerPath.endsWith(".txt")) type = "TEXT";
            else if (lowerPath.matches(".*\\.(jpg|jpeg|png)$")) type = "IMAGE";
        }

        return new SourceDto(path, finalFileName, score, base64, type);
    }

    public List<SourceDto> getSources(Result<String> result) {
        if (result.sources() == null) {
            return List.of();
        }
        return result.sources().stream()
                .map(source -> {
                    var metadata = source.textSegment().metadata();
                    String path = metadata.getString("path");
                    String fileName = metadata.getString("filename");
                    
                    double score = 0.0;
                    String scoreStr = metadata.getString("score");
                    if (scoreStr != null) {
                        try {
                            score = Double.parseDouble(scoreStr);
                        } catch (NumberFormatException ignored) {}
                    }

                    return createSourceDto(path, fileName, score);
                })
                .distinct()
                .toList();
    }
}