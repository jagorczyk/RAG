package com.rag.rag.ingestion.controller;

import com.rag.rag.folder.dto.FileDeleteDto;
import com.rag.rag.folder.dto.FileDto;
import com.rag.rag.folder.dto.FileMoveDto;
import com.rag.rag.folder.dto.FileRenameDto;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.ingestion.service.IngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class IngestionController {
    private final IngestionService ingestionService;
    private final JdbcTemplate jdbcTemplate;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final Map<UUID, ReanalysisJob> reanalysisJobs = new ConcurrentHashMap<>();

    private static final class ReanalysisJob {
        final int total;
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        volatile String status = "RUNNING";

        ReanalysisJob(int total) { this.total = total; }
    }

    public IngestionController(
            IngestionService ingestionService,
            JdbcTemplate jdbcTemplate,
            FileRepository fileRepository,
            FolderRepository folderRepository
    ) {
        this.ingestionService = ingestionService;
        this.jdbcTemplate = jdbcTemplate;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional
    @PostMapping("/files/delete")
    public ResponseEntity<?> deleteFiles(@RequestBody FileDeleteDto deleteDto) {
        if (deleteDto.filePaths() == null || deleteDto.filePaths().isEmpty()) {
            return ResponseEntity.badRequest().body("No file paths provided.");
        }

        ingestionService.deleteFiles(deleteDto.filePaths());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/images/reanalyze-context")
    public ResponseEntity<Map<String, Object>> reanalyzeImageContext() {
        List<FileEntity> images = fileRepository.findAll().stream()
                .filter(file -> file.getImageData() != null && file.getImageData().length > 0)
                .filter(file -> file.getFileType() != null && file.getFileType().toLowerCase(Locale.ROOT).contains("image"))
                .toList();
        UUID jobId = UUID.randomUUID();
        ReanalysisJob job = new ReanalysisJob(images.size());
        reanalysisJobs.put(jobId, job);
        CompletableFuture.runAsync(() -> {
            for (FileEntity image : images) {
                try {
                    ingestionService.reanalyzeExistingImage(image);
                    job.completed.incrementAndGet();
                } catch (Exception e) {
                    job.failed.incrementAndGet();
                }
            }
            job.status = "COMPLETED";
        });
        return ResponseEntity.accepted().body(reanalysisStatus(jobId, job));
    }

    @GetMapping("/images/reanalyze-context/{jobId}")
    public ResponseEntity<Map<String, Object>> reanalysisStatus(@PathVariable UUID jobId) {
        ReanalysisJob job = reanalysisJobs.get(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(reanalysisStatus(jobId, job));
    }

    @PostMapping("/images/reanalyze-faces")
    public ResponseEntity<Map<String, Object>> reanalyzeFaces() {
        List<FileEntity> images = fileRepository.findAll().stream()
                .filter(file -> file.getImageData() != null && file.getImageData().length > 0)
                .filter(file -> file.getFileType() != null && file.getFileType().toLowerCase(Locale.ROOT).contains("image"))
                .toList();
        UUID jobId = UUID.randomUUID();
        ReanalysisJob job = new ReanalysisJob(images.size());
        reanalysisJobs.put(jobId, job);
        CompletableFuture.runAsync(() -> {
            for (FileEntity image : images) {
                try {
                    ingestionService.reanalyzeExistingFaces(image);
                    job.completed.incrementAndGet();
                } catch (Exception e) {
                    job.failed.incrementAndGet();
                }
            }
            job.status = "COMPLETED";
        });
        return ResponseEntity.accepted().body(reanalysisStatus(jobId, job));
    }

    @GetMapping("/images/reanalyze-faces/{jobId}")
    public ResponseEntity<Map<String, Object>> faceReanalysisStatus(@PathVariable UUID jobId) {
        return reanalysisStatus(jobId);
    }

    private Map<String, Object> reanalysisStatus(UUID jobId, ReanalysisJob job) {
        return Map.of("jobId", jobId, "status", job.status, "total", job.total,
                "completed", job.completed.get(), "failed", job.failed.get(),
                "remaining", Math.max(0, job.total - job.completed.get() - job.failed.get()));
    }

    @Transactional
    @PostMapping("/files/move")
    public ResponseEntity<?> moveFiles(@RequestBody FileMoveDto moveDto) {
        Optional<FolderEntity> targetFolderOpt = folderRepository.findById(moveDto.targetFolderId());
        if (targetFolderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FolderEntity targetFolder = targetFolderOpt.get();
        String targetFolderName = targetFolder.getName();

        for (String oldPath : moveDto.filePaths()) {
            Optional<FileEntity> fileOpt = fileRepository.findByPath(oldPath);
            if (fileOpt.isPresent()) {
                FileEntity file = fileOpt.get();
                String newPath = "dir://" + targetFolderName + "/" + file.getFileName();

                
                file.setPath(newPath);
                fileRepository.save(file);

                
                
                String sql = "UPDATE embeddings SET metadata = metadata::jsonb || " +
                             "jsonb_build_object('path', ?, 'document_id', ?) " +
                             "WHERE metadata->>'path' = ?";
                
                jdbcTemplate.update(sql, newPath, targetFolderName, oldPath);
            }
        }

        return ResponseEntity.ok().build();
    }

    @Transactional
    @PostMapping("/files/rename")
    public ResponseEntity<?> renameFile(@RequestBody FileRenameDto renameDto) {
        Optional<FileEntity> fileOpt = fileRepository.findByPath(renameDto.oldPath());
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileEntity file = fileOpt.get();
        String oldPath = file.getPath();
        
        
        String folderPart = oldPath.substring(0, oldPath.lastIndexOf("/") + 1);
        String newPath = folderPart + renameDto.newName();

        
        file.setFileName(renameDto.newName());
        file.setPath(newPath);
        fileRepository.save(file);

        
        String sql = "UPDATE embeddings SET metadata = metadata::jsonb || " +
                     "jsonb_build_object('filename', ?, 'path', ?) " +
                     "WHERE metadata->>'path' = ?";
        
        jdbcTemplate.update(sql, renameDto.newName(), newPath, oldPath);

        return ResponseEntity.ok().build();
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

    @Transactional
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, String>> clearAllData() {
        ingestionService.clearAllData();
        return ResponseEntity.ok(Map.of(
                "message", "Wyczyszczono foldery, pliki, embeddingi i graf wiedzy."
        ));
    }



















    @GetMapping("/files")
    public ResponseEntity<List<FileDto>> getFiles() {
        List<FileEntity> images = fileRepository.findAll();
        List<FileDto> files = images
                .stream()
                .map(
                        i -> new FileDto(
                                i.getPath(),
                                i.getFileName(),
                                i.getImageData() != null ?
                                Base64.getEncoder().encodeToString(i.getImageData()) : null,
                                i.getFileType(),
                                getExtractedText(i.getPath())
                        )
                ).toList();

        return ResponseEntity.ok(files);
    }

    /** Streams an image/document for native clients instead of forcing Base64 in a list response. */
    @GetMapping("/files/content")
    public ResponseEntity<byte[]> getFileContent(@RequestParam("path") String path) {
        Optional<FileEntity> fileOpt = fileRepository.findByPath(path);
        if (fileOpt.isEmpty() || fileOpt.get().getImageData() == null) {
            return ResponseEntity.notFound().build();
        }
        FileEntity file = fileOpt.get();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.getFileType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getFileType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"")
                .contentType(mediaType)
                .body(file.getImageData());
    }

    private List<String> getEmbeddingChunks(String path) {
        return jdbcTemplate.queryForList(
                "SELECT text FROM embeddings WHERE metadata->>'path' = ?",
                String.class,
                path
        );
    }

    private String getExtractedText(String path) {
        List<String> chunks = getEmbeddingChunks(path);
        if (chunks.isEmpty()) {
            return null;
        }

        String text = chunks.get(0).trim();
        if (text.isEmpty()) {
            return null;
        }

        int maxLength = 200;
        if (text.length() > maxLength) {
            return text.substring(0, maxLength) + "…";
        }
        return text;
    }

    @GetMapping("/files/embeddings")
    public ResponseEntity<Map<String, String>> getFileEmbeddings(@RequestParam("path") String path) {
        Optional<FileEntity> fileOpt = fileRepository.findByPath(path);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileEntity file = fileOpt.get();
        List<String> chunks = getEmbeddingChunks(path);
        String content = chunks.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(chunk -> !chunk.isEmpty())
                .reduce((a, b) -> a + "\n\n---\n\n" + b)
                .orElse("Brak embeddingów dla tego pliku.");

        return ResponseEntity.ok(Map.of(
                "title", file.getFileName(),
                "content", content,
                "chunkCount", String.valueOf(chunks.size())
        ));
    }

    @GetMapping("/files/preview")
    public ResponseEntity<Map<String, String>> getFilePreview(@RequestParam("path") String path) {
        Optional<FileEntity> fileOpt = fileRepository.findByPath(path);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileEntity file = fileOpt.get();
        String mimeType = file.getFileType() != null ? file.getFileType() : "application/octet-stream";

        if (mimeType.startsWith("image/") && file.getImageData() != null) {
            String imageBase64 = Base64.getEncoder().encodeToString(file.getImageData());
            String dataUrl = "data:" + mimeType + ";base64," + imageBase64;
            return ResponseEntity.ok(Map.of(
                    "kind", "image",
                    "title", file.getFileName(),
                    "mimeType", mimeType,
                    "content", dataUrl,
                    "path", path
            ));
        }

        if (mimeType.contains("pdf") || mimeType.equals("text/plain")) {
            List<String> chunks = getEmbeddingChunks(path);

            String previewText = chunks.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(chunk -> !chunk.isEmpty())
                    .reduce((a, b) -> a + "\n\n" + b)
                    .orElse("Brak dostępnej treści podglądu dla tego pliku.");

            int maxPreviewLength = 24000;
            if (previewText.length() > maxPreviewLength) {
                previewText = previewText.substring(0, maxPreviewLength) + "\n\n[...]";
            }

            return ResponseEntity.ok(Map.of(
                    "kind", mimeType.contains("pdf") ? "pdf" : "text",
                    "title", file.getFileName(),
                    "mimeType", mimeType,
                    "content", previewText,
                    "path", path
            ));
        }

        String fallback = "Podgląd nie jest dostępny dla typu: " + mimeType;
        return ResponseEntity.ok(Map.of(
                "kind", "other",
                "title", file.getFileName(),
                "mimeType", mimeType,
                "content", fallback,
                "path", path
        ));
    }
}
