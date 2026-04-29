package com.rag.rag.Service;

import com.rag.rag.Detectors.ROIDetector;
import com.rag.rag.Dto.SourceDto;
import com.rag.rag.Entity.FolderEntity;
import com.rag.rag.Entity.FileEntity;
import com.rag.rag.Repository.FileRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Qualifier;
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

@Service
public class IngestionService {

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
        Document document = null;
        if (extension == null) return null;

        switch (extension.toLowerCase()) {
            case "txt" -> document = processTextFile(fileData, path, fileName);
            case "pdf" -> document = processPdfFile(fileData, path, fileName);
            case "png", "jpg", "jpeg" -> document = processImage(fileData, path, fileName);
            default -> System.out.println("Skipped file: " + path + ", wrong file extension.");
        }

        return document;
    }

    public Document processTextFile(byte[] fileData, String path, String fileName) {
        try (InputStream stream = new ByteArrayInputStream(fileData)) {
            if (fileRepository.findByPath(path).isEmpty()) {
                FileEntity txtEntity = FileEntity.builder()
                        .path(path)
                        .fileName(fileName)
                        .fileType("txt")
                        .imageData(null)
                        .build();
                fileRepository.save(txtEntity);
            }
            TextDocumentParser parser = new TextDocumentParser();
            return parser.parse(stream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Document processPdfFile(byte[] fileData, String path, String fileName) {
        try (InputStream stream = new ByteArrayInputStream(fileData)) {
            if (fileRepository.findByPath(path).isEmpty()) {
                FileEntity pdfEntity = FileEntity.builder()
                        .path(path)
                        .fileName(fileName)
                        .fileType("application/pdf")
                        .imageData(null)
                        .build();
                fileRepository.save(pdfEntity);
            }
            ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();
            return parser.parse(stream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Document processImage(byte[] imageData, String path, String fileName) {
        try {
            String format = fileName.toLowerCase().endsWith(".png") ? "png" : "jpg";
            String mimeType = "image/" + (format.equals("png") ? "png" : "jpeg");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(imageData))
                    .size(800, 800)
                    .outputFormat(format)
                    .toOutputStream(outputStream);

            byte[] processedBytes = outputStream.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(processedBytes);

            if (fileRepository.findByPath(path).isEmpty()) {
                FileEntity imageEntity = FileEntity.builder()
                        .path(path)
                        .fileName(fileName)
                        .fileType(mimeType)
                        .imageData(processedBytes)
                        .build();
                fileRepository.save(imageEntity);
            }

            UserMessage message = UserMessage.from(
                    TextContent.from("What exactly is in this picture? Transcribe all visible codes, numbers, identifiers, and describe their meaning. Be very detailed. Respond in English."),
                    ImageContent.from(base64Image, mimeType)
            );

            String description = visionModel.generate(message).content().text();
            return Document.from(description);

        } catch (Exception e) {
            throw new RuntimeException("Error while processing image: " + path, e);
        }
    }

    @Transactional
    public void ingestMultipartFile(MultipartFile file, FolderEntity folder) throws IOException {
        String fileName = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(fileName);
        String group = folder.getName();

        String path = "dir://" + folder.getName() + "/" + file.getOriginalFilename();
        byte[] data = file.getBytes();

        Document parsedDocument = chooseParser(data, path, fileName, extension);
        if (parsedDocument != null) {
            parsedDocument.metadata()
                    .put("document_id", group)
                    .put("filename", fileName)
                    .put("path", path);
            ingestor.ingest(parsedDocument);
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
            else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".png")) type = "IMAGE";
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
