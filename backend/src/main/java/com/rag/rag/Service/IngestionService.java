package com.rag.rag.Service;

import com.rag.rag.Detectors.ROIDetector;
import com.rag.rag.Dto.SourceDto;
import com.rag.rag.Entity.FolderEntity;
import com.rag.rag.Entity.ImageEntity;
import com.rag.rag.Repository.ImageRepository;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class IngestionService {

    private final ChatLanguageModel visionModel;
    private final EmbeddingStoreIngestor ingestor;
    private final ROIDetector detector;
    private final ImageRepository imageRepository;

    public IngestionService(
            @Qualifier("visionModel") ChatLanguageModel visionModel,
            EmbeddingStoreIngestor ingestor,
            ROIDetector detector,
            ImageRepository imageRepository
    ) {
        this.visionModel = visionModel;
        this.ingestor = ingestor;
        this.detector = detector;
        this.imageRepository = imageRepository;
    }

    private Document chooseParser(byte[] fileData, String path, String fileName, String extension) {
        Document document = null;

        switch (extension) {
            case "txt" -> document = processTextFile(fileData);
            case "pdf" -> document = processPdfFile(fileData);
            case "png", "jpg", "jpeg" -> document = processImage(fileData, path, fileName);
            default -> System.out.println("Skipped file: " + path + ", wrong file extension.");
        }

        return document;
    }

    public Document processTextFile(byte[] fileData) {
        try (InputStream stream = new ByteArrayInputStream(fileData)) {
            TextDocumentParser parser = new TextDocumentParser();
            return parser.parse(stream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Document processPdfFile(byte[] fileData) {
        try (InputStream stream = new ByteArrayInputStream(fileData)) {
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
                    .size(1024, 1024)
                    .outputFormat(format)
                    .toOutputStream(outputStream);

            byte[] processedBytes = outputStream.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(processedBytes);

            if (imageRepository.findByPath(path).isEmpty()) {
                ImageEntity imageEntity = ImageEntity.builder()
                        .path(path)
                        .fileName(fileName)
                        .fileType(mimeType)
                        .imageData(processedBytes)
                        .build();
                imageRepository.save(imageEntity);
            }

            UserMessage message = UserMessage.from(
                    TextContent.from("""
                Provide a detailed technical description of this image for retrieval purposes.
                
                Step 1: Identify the main subject (e.g., device type, model, object).
                Step 2: Transcribe any visible text, labels, or serial numbers strictly.
                Step 3: Describe the condition of the object (e.g., damaged, new, dirty).
                Step 4: Describe the environment or context if relevant.
                
                Focus on factual keywords that a user might search for.
                """),
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

        String lowerPath = path.toLowerCase();
        String extension = "";

        int dotIndex = lowerPath.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = lowerPath.substring(dotIndex);
        }

        String type = switch (extension) {
            case ".pdf" -> "PDF";
            case ".txt" -> "TEXT";
            case ".jpg", ".jpeg", ".png" -> "IMAGE";
            default -> "OTHER";
        };

        String base64 = null;
        String finalFileName = metadataFileName != null ? metadataFileName : path;

        var imageOpt = imageRepository.findByPath(path);
        if (imageOpt.isPresent()) {
            finalFileName = imageOpt.get().getFileName();
            if ("IMAGE".equals(type)) {
                base64 = Base64.getEncoder().encodeToString(imageOpt.get().getImageData());
            }
        }

        return new SourceDto(path, finalFileName, score, base64, type);
    }

    public List<SourceDto> getSources(Result<String> result) {
        return result.sources().stream()
                .map(source -> {
                    var metadata = source.textSegment().metadata();
                    String path = metadata.getString("path");
                    String fileName = metadata.getString("filename");
                    Double score = metadata.getDouble("score");

                    return createSourceDto(path, fileName, score);
                })
                .distinct()
                .toList();
    }
}
