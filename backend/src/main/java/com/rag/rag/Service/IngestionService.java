package com.rag.rag.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

@Service
public class IngestionService {

    private final ChatLanguageModel visionModel;
    private final EmbeddingStoreIngestor ingestor;

    public IngestionService(
            @Qualifier("visionModel") ChatLanguageModel visionModel,
            EmbeddingStoreIngestor ingestor
    ) {
        this.visionModel = visionModel;
        this.ingestor = ingestor;
    }

    public void ingestFilesFromDirectory(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(this::isFileSupported)
                    .forEach(this::chooseParser);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            System.out.println("Zaladowano dane z folderu: " + directory);
        }
    }

    private void chooseParser(Path file) {
        switch (getFileExtension(file)) {
            case ".txt" -> processTextFile(file);
            case ".pdf" -> processPdfFile(file);
            case ".png", ".jpg", ".jpeg" -> processImage(file);
            default -> System.out.println("Pominieto plik: " + file + ", nieobslugiwany format.");
        }
    }

    public void processTextFile(Path filePath) {
        try {
            Document document = FileSystemDocumentLoader.loadDocument(filePath, new TextDocumentParser());
            enrichMetadata(document, filePath);

            ingestor.ingest(document);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void processPdfFile(Path filePath) {
        try {
            Document document = FileSystemDocumentLoader.loadDocument(filePath, new ApacheTikaDocumentParser());
            enrichMetadata(document, filePath);

            ingestor.ingest(document);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void processImage(Path imagePath) {
        try {
            String fileName = imagePath.getFileName().toString();

            byte[] fileContent = Files.readAllBytes(imagePath);
            String base64Image = Base64.getEncoder().encodeToString(fileContent);
            String format = fileName.endsWith(".png") ? "png" : "jpg";
            String mimeType = "image/" + (format.equals("png") ? "png" : "jpeg");

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
            String group = getFileGroup(imagePath);

            Metadata metadata = Metadata
                    .from("document_id", group)
                    .put("filename", fileName)
                    .put("path", imagePath.toString());

            Document document = Document.from(description, metadata);
            ingestor.ingest(document);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getFileGroup(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            return (parent != null) ? parent.getFileName().toString() : "root";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void enrichMetadata(Document document, Path file) {
        String group = getFileGroup(file);
        document.metadata()
                .put("document_id", group)
                .put("filename", file.getFileName().toString())
                .put("path", file.toString());
    }

    private String getFileExtension(Path file) {
        return file.getFileName().toString().substring(file.getFileName().toString().lastIndexOf("."));
    }

    private boolean isFileSupported(Path file) {
        return file.getFileName().toString().endsWith(".png") ||
                file.getFileName().toString().endsWith(".jpg") ||
                file.getFileName().toString().endsWith(".jpeg") ||
                file.getFileName().toString().endsWith(".txt") ||
                file.getFileName().toString().endsWith(".pdf");
    }

}
