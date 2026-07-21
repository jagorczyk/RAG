package com.rag.rag.ingestion.controller;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.exception.ApiException;
import com.rag.rag.folder.dto.FileDeleteDto;
import com.rag.rag.folder.dto.FileDto;
import com.rag.rag.folder.dto.FileMoveDto;
import com.rag.rag.folder.dto.FileRenameDto;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.entity.IngestionStatus;
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
public class IngestionController {
    private final IngestionService ingestionService;
    private final JdbcTemplate jdbcTemplate;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;
    private final Map<UUID, ReanalysisJob> reanalysisJobs = new ConcurrentHashMap<>();

    private static final class ReanalysisJob {
        final int total;
        final UUID ownerId;
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        volatile String status = "RUNNING";

        ReanalysisJob(int total, UUID ownerId) {
            this.total = total;
            this.ownerId = ownerId;
        }
    }

    public IngestionController(
            IngestionService ingestionService,
            JdbcTemplate jdbcTemplate,
            FileRepository fileRepository,
            FolderRepository folderRepository,
            CurrentUserService currentUserService
    ) {
        this.ingestionService = ingestionService;
        this.jdbcTemplate = jdbcTemplate;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    @PostMapping("/files/delete")
    public ResponseEntity<?> deleteFiles(@RequestBody FileDeleteDto deleteDto) {
        if (deleteDto.filePaths() == null || deleteDto.filePaths().isEmpty()) {
            return ResponseEntity.badRequest().body("No file paths provided.");
        }

        UUID ownerId = currentUserService.requireUserId();
        List<String> ownedPaths = deleteDto.filePaths().stream()
                .filter(path -> fileRepository.findByPathAndOwnerId(path, ownerId).isPresent())
                .toList();
        if (ownedPaths.isEmpty()) {
            throw ApiException.notFound("FILE_NOT_FOUND", "Nie znaleziono plików do usunięcia.");
        }
        ingestionService.deleteFiles(ownedPaths);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/images/reanalyze-context")
    public ResponseEntity<Map<String, Object>> reanalyzeImageContext() {
        UUID ownerId = currentUserService.requireUserId();
        List<FileEntity> images = ownerImages(ownerId);
        UUID jobId = UUID.randomUUID();
        ReanalysisJob job = new ReanalysisJob(images.size(), ownerId);
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
        ReanalysisJob job = requireOwnedJob(jobId);
        return ResponseEntity.ok(reanalysisStatus(jobId, job));
    }

    @PostMapping("/images/reanalyze-faces")
    public ResponseEntity<Map<String, Object>> reanalyzeFaces() {
        UUID ownerId = currentUserService.requireUserId();
        List<FileEntity> images = ownerImages(ownerId);
        UUID jobId = UUID.randomUUID();
        ReanalysisJob job = new ReanalysisJob(images.size(), ownerId);
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
        UUID ownerId = currentUserService.requireUserId();
        FolderEntity targetFolder = folderRepository.findByIdAndOwnerId(moveDto.targetFolderId(), ownerId)
                .orElseThrow(() -> ApiException.notFound("FOLDER_NOT_FOUND", "Folder nie istnieje."));

        String targetFolderName = targetFolder.getName();

        for (String oldPath : moveDto.filePaths()) {
            Optional<FileEntity> fileOpt = fileRepository.findByPathAndOwnerId(oldPath, ownerId);
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
        UUID ownerId = currentUserService.requireUserId();
        FileEntity file = fileRepository.findByPathAndOwnerId(renameDto.oldPath(), ownerId)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "Plik nie istnieje."));

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
        // Intentionally limited: no global truncate of shared embeddings in multi-user mode.
        return ResponseEntity.status(405).body("Użyj DELETE /api/data/clear-all dla danych bieżącego użytkownika.");
    }

    @Transactional
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, String>> clearAllData() {
        ingestionService.clearAllData();
        return ResponseEntity.ok(Map.of(
                "message", "Wyczyszczono osoby, rozmowy, wiadomości, foldery i pliki bieżącego użytkownika."
        ));
    }

    /** Poll ingestion job status after async upload (Sprint 2). */
    @GetMapping("/files/ingestion-status")
    public ResponseEntity<Map<String, String>> ingestionStatus(@RequestParam("path") String path) {
        UUID ownerId = currentUserService.requireUserId();
        IngestionStatus status = ingestionService.getIngestionStatus(path, ownerId)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "Plik nie istnieje."));
        return ResponseEntity.ok(Map.of(
                "path", path,
                "status", status.name()
        ));
    }

    @GetMapping("/files")
    public ResponseEntity<List<FileDto>> getFiles() {
        UUID ownerId = currentUserService.requireUserId();
        List<FileEntity> images = fileRepository.findAllByOwnerId(ownerId);
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
        UUID ownerId = currentUserService.requireUserId();
        FileEntity file = fileRepository.findByPathAndOwnerId(path, ownerId)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "Plik nie istnieje."));
        if (file.getImageData() == null) {
            throw ApiException.notFound("FILE_NOT_FOUND", "Plik nie istnieje.");
        }
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
        UUID ownerId = currentUserService.requireUserId();
        FileEntity file = fileRepository.findByPathAndOwnerId(path, ownerId)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "Plik nie istnieje."));

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
        UUID ownerId = currentUserService.requireUserId();
        FileEntity file = fileRepository.findByPathAndOwnerId(path, ownerId)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "Plik nie istnieje."));

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

    private List<FileEntity> ownerImages(UUID ownerId) {
        return fileRepository.findAllByOwnerId(ownerId).stream()
                .filter(file -> file.getImageData() != null && file.getImageData().length > 0)
                .filter(file -> file.getFileType() != null && file.getFileType().toLowerCase(Locale.ROOT).contains("image"))
                .toList();
    }

    private ReanalysisJob requireOwnedJob(UUID jobId) {
        UUID ownerId = currentUserService.requireUserId();
        ReanalysisJob job = reanalysisJobs.get(jobId);
        if (job == null || !ownerId.equals(job.ownerId)) {
            throw ApiException.notFound("JOB_NOT_FOUND", "Zadanie nie istnieje.");
        }
        return job;
    }
}
