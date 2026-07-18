package com.rag.rag.folder.controller;

import com.rag.rag.folder.dto.UploadResultDto;
import com.rag.rag.folder.dto.FolderDto;
import com.rag.rag.folder.dto.FileDto;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.ingestion.service.IngestionService;
import com.rag.rag.ingestion.service.InvalidImageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/folders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FolderController {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final IngestionService ingestionService;

    @PostMapping("/create")
    public FolderEntity create(@RequestBody FolderDto folder) {
        log.info("Creating new folder with name: {}", folder.name());
        FolderEntity newFolder = new FolderEntity();
        newFolder.setName(folder.name());
        return folderRepository.save(newFolder);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        log.info("Deleting folder with id: {}", id);
        folderRepository.deleteById(id);
    }

    @GetMapping
    public List<FolderEntity> findAll() {
        return folderRepository.findAll();
    }

    /** Mobile-friendly folder listing that avoids returning every image in the database. */
    @GetMapping("/{id}/files")
    public List<FileDto> files(@PathVariable UUID id) {
        FolderEntity folder = folderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found."));
        String prefix = "dir://" + folder.getName() + "/";
        return fileRepository.findAll().stream()
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
        log.info("Uploading file {} to folder id: {} (entityTag={})", file.getOriginalFilename(), id, entityTag);
        FolderEntity folder = folderRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found."));

        try {
            String path = ingestionService.ingestMultipartFile(file, folder, entityTag);
            folder.setUpdatedAt(LocalDateTime.now());
            folderRepository.save(folder);
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            boolean image = isImageFile(fileName, file.getContentType());
            return new UploadResultDto(path, fileName, image);
        } catch (IOException e) {
            log.error("Failed to upload file to folder {}", folder.getName(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process the uploaded file.");
        }
    }

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<Map<String, String>> handleInvalidImage(InvalidImageException exception) {
        log.warn("Rejected invalid image upload: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.of("message", exception.getMessage()));
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
