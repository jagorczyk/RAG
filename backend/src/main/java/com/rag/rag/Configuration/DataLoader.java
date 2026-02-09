package com.rag.rag.Configuration;

import com.rag.rag.Service.IngestionService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

@Configuration
public class DataLoader {

    private final IngestionService ingestionService;

    public DataLoader(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Bean
    CommandLineRunner loadData() {
        return args -> {
            String directory = "data";
            Path path = Paths.get(directory);

            if (!Files.exists(path)) {
                System.out.println("Path " + path + " does not exist.");
                return;
            }

            try (Stream<Path> paths = Files.list(path)) {
                paths.filter(Files::isDirectory).forEach(ingestionService::ingestFilesFromDirectory);
            };
        };
    }

}
