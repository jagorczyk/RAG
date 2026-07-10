package com.rag.rag.folder.controller;

import com.rag.rag.folder.dto.FolderDto;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/folders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FolderController {

    private final FolderRepository folderRepository;
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

    @PostMapping("/{id}/upload")
    public void upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        log.info("Uploading file {} to folder id: {}", file.getOriginalFilename(), id);
        FolderEntity folder = folderRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found."));

        try {
            ingestionService.ingestMultipartFile(file, folder);
            folder.setUpdatedAt(LocalDateTime.now());
            folderRepository.save(folder);
        } catch (IOException e) {
            log.error("Failed to upload file to folder {}", folder.getName(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process the uploaded file.");
        }
    }
}