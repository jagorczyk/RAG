package com.rag.rag.Controller;

import com.rag.rag.Dto.FileDto;
import com.rag.rag.Dto.FileMoveDto;
import com.rag.rag.Dto.FileRenameDto;
import com.rag.rag.Entity.FileEntity;
import com.rag.rag.Entity.FolderEntity;
import com.rag.rag.Repository.FileRepository;
import com.rag.rag.Repository.FolderRepository;
import com.rag.rag.Service.IngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.List;

@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class IngestionController {
    private final IngestionService ingestionService;
    private final JdbcTemplate jdbcTemplate;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;

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
                                i.getFileType()
                        )
                ).toList();

        return ResponseEntity.ok(files);
    }
}
