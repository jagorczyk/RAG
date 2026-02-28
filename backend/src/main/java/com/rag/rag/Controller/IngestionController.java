package com.rag.rag.Controller;

import com.rag.rag.Service.IngestionService;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/data")
public class IngestionController {
    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/load")
    public Map<String, String> loadData() {
        String directory = "data";
        Path path = Paths.get(directory);

        if (!Files.exists(path)) {
            System.out.println("Path " + path + " does not exist.");
            return null;
        }

        try (Stream<Path> paths = Files.list(path)) {
            paths.filter(Files::isDirectory).forEach(ingestionService::ingestFilesFromDirectory);
            return Map.of("loading", "successful");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
