package com.rag.rag.Controller;

import com.rag.rag.Dto.FolderDto;
import com.rag.rag.Entity.FolderEntity;
import com.rag.rag.Repository.FolderRepository;
import com.rag.rag.Service.IngestionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
@CrossOrigin(origins = "*")
public class FolderController {

    private final FolderRepository folderRepository;
    private final IngestionService ingestionService;

    public FolderController(FolderRepository folderRepository, IngestionService ingestionService) {
        this.folderRepository = folderRepository;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/create")
    public FolderEntity create(@RequestBody FolderDto folder) {
        FolderEntity newFolder = new FolderEntity();
        newFolder.setName(folder.name());
        return folderRepository.save(newFolder);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        folderRepository.deleteById(id);
    }

    @GetMapping
    public List<FolderEntity> findAll() {
        return folderRepository.findAll();
    }

    @PostMapping("/{id}/upload")
    public void upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        FolderEntity folder = folderRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found."));

        ingestionService.ingestMultipartFile(file, folder);
    }
}
