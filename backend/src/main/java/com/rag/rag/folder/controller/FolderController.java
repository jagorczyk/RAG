package com.rag.rag.folder.controller;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.exception.ApiException;
import com.rag.rag.folder.dto.UploadResultDto;
import com.rag.rag.folder.dto.FolderDto;
import com.rag.rag.folder.dto.FileDto;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.ingestion.service.IngestionService;
import com.rag.rag.ingestion.service.InvalidImageException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final IngestionService ingestionService;
    private final CurrentUserService currentUserService;

    @PostMapping("/create")
    public FolderEntity create(@Valid @RequestBody FolderDto folder) {
        UUID ownerId = currentUserService.requireUserId();
        log.info("Creating folder '{}' for owner {}", folder.name(), ownerId);
        FolderEntity newFolder = new FolderEntity();
        newFolder.setName(folder.name().trim());
        newFolder.setOwnerId(ownerId);
        return folderRepository.save(newFolder);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        UUID ownerId = currentUserService.requireUserId();
        FolderEntity folder = requireOwnedFolder(id, ownerId);
        log.info("Deleting folder {} for owner {}", id, ownerId);
        folderRepository.delete(folder);
    }

    @GetMapping
    public List<FolderEntity> findAll() {
        UUID ownerId = currentUserService.requireUserId();
        return folderRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    /** Mobile-friendly folder listing that avoids returning every image in the database. */
    @GetMapping("/{id}/files")
    public List<FileDto> files(@PathVariable UUID id) {
        UUID ownerId = currentUserService.requireUserId();
        FolderEntity folder = requireOwnedFolder(id, ownerId);
        String prefix = "dir://" + folder.getName() + "/";
        return fileRepository.findAllByOwnerId(ownerId).stream()
                .filter(file -> file.getPath() != null && file.getPath().startsWith(prefix))
                .map(file -> new FileDto(file.getPath(), file.getFileName(), null, file.getFileType(), null))
                .toList();
    }

    @PostMapping("/{id}/upload")
    public UploadResultDto upload(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "entityTag", required = false) String entityTag
    ) {
        UUID ownerId = currentUserService.requireUserId();
        log.info("Uploading file {} to folder id: {} (entityTag={})", file.getOriginalFilename(), id, entityTag);
        FolderEntity folder = requireOwnedFolder(id, ownerId);

        try {
            String path = ingestionService.ingestMultipartFile(file, folder, entityTag, ownerId);
            folder.setUpdatedAt(LocalDateTime.now());
            folderRepository.save(folder);
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            boolean image = isImageFile(fileName, file.getContentType());
            return new UploadResultDto(path, fileName, image);
        } catch (IOException e) {
            log.error("Failed to upload file to folder {}", folder.getName(), e);
            throw ApiException.badRequest("UPLOAD_FAILED", "Nie udało się przetworzyć wgranego pliku.");
        }
    }

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<Map<String, String>> handleInvalidImage(InvalidImageException exception) {
        log.warn("Rejected invalid image upload: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.of("message", exception.getMessage()));
    }

    private FolderEntity requireOwnedFolder(UUID id, UUID ownerId) {
        return folderRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("FOLDER_NOT_FOUND", "Folder nie istnieje."));
    }

    private boolean isImageFile(String fileName, String contentType) {
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return true;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }
}
