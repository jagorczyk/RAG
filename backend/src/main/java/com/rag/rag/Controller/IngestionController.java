package com.rag.rag.Controller;

import com.rag.rag.Dto.FileDto;
import com.rag.rag.Entity.ImageEntity;
import com.rag.rag.Repository.ImageRepository;
import com.rag.rag.Service.IngestionService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class IngestionController {
    private final IngestionService ingestionService;
    private final JdbcTemplate jdbcTemplate;
    private final ImageRepository imageRepository;

    public IngestionController(
            IngestionService ingestionService,
            JdbcTemplate jdbcTemplate,
            ImageRepository imageRepository
    ) {
        this.ingestionService = ingestionService;
        this.jdbcTemplate = jdbcTemplate;
        this.imageRepository = imageRepository;
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

//    @PostMapping("/load")
//    public ResponseEntity<String> loadData() {
//        String directory = "data";
//        Path path = Paths.get(directory);
//
//        if (!Files.exists(path)) {
//            System.out.println("Path " + path + " does not exist.");
//            return null;
//        }
//
//        try (Stream<Path> paths = Files.list(path)) {
//            paths.filter(Files::isDirectory).forEach(ingestionService::ingestFilesFromDirectory);
//            return ResponseEntity.accepted().body("Ingestion successful.");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

    @GetMapping("/files")
    public ResponseEntity<List<FileDto>> getFiles() {
        List<ImageEntity> images = imageRepository.findAll();
        List<FileDto> files = new ArrayList<>();
        files = images
                .stream()
                .map(
                        i -> new FileDto(
                                i.getPath(),
                                i.getFileName(),
                                Base64.getEncoder().encodeToString(i.getImageData())
                        )
                ).toList();

        return ResponseEntity.ok(files);
    }
}
