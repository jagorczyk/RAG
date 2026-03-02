package com.rag.rag.Controller;

import com.rag.rag.Service.IngestionService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final JdbcTemplate jdbcTemplate;

    public IngestionController(IngestionService ingestionService, JdbcTemplate jdbcTemplate) {
        this.ingestionService = ingestionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DeleteMapping("/clear")
    public ResponseEntity<String> clearEmbeddingsTable() {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE embeddings");
            return ResponseEntity.ok("Truncated embeddings.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(e.getMessage());
        }
    }

    @PostMapping("/load")
    public ResponseEntity<String> loadData() {
        String directory = "data";
        Path path = Paths.get(directory);

        if (!Files.exists(path)) {
            System.out.println("Path " + path + " does not exist.");
            return null;
        }

        try (Stream<Path> paths = Files.list(path)) {
            paths.filter(Files::isDirectory).forEach(ingestionService::ingestFilesFromDirectory);
            return ResponseEntity.accepted().body("Ingestion successful.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
