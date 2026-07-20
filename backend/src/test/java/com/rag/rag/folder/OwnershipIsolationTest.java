package com.rag.rag.folder;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.exception.ApiException;
import com.rag.rag.folder.controller.FolderController;
import com.rag.rag.folder.dto.FolderDto;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * User A creates a folder; user B must not see it (empty list / 404).
 */
class OwnershipIsolationTest {

    private FolderRepository folderRepository;
    private FileRepository fileRepository;
    private CurrentUserService currentUserService;
    private FolderController controller;

    private final UUID userA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID userB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID folderId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        folderRepository = mock(FolderRepository.class);
        fileRepository = mock(FileRepository.class);
        currentUserService = mock(CurrentUserService.class);
        IngestionService ingestionService = mock(IngestionService.class);
        controller = new FolderController(folderRepository, fileRepository, ingestionService, currentUserService);
    }

    @Test
    void createAssignsCurrentUserAsOwner() {
        when(currentUserService.requireUserId()).thenReturn(userA);
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> {
            FolderEntity folder = invocation.getArgument(0);
            folder.setId(folderId);
            return folder;
        });

        FolderEntity created = controller.create(new FolderDto("Zdjęcia"));

        assertEquals(userA, created.getOwnerId());
        assertEquals("Zdjęcia", created.getName());
    }

    @Test
    void listReturnsOnlyOwnFolders() {
        FolderEntity aFolder = new FolderEntity();
        aFolder.setId(folderId);
        aFolder.setName("A");
        aFolder.setOwnerId(userA);

        when(currentUserService.requireUserId()).thenReturn(userB);
        when(folderRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userB)).thenReturn(List.of());

        List<FolderEntity> folders = controller.findAll();
        assertEquals(0, folders.size());
    }

    @Test
    void getFilesOfForeignFolderReturns404() {
        when(currentUserService.requireUserId()).thenReturn(userB);
        when(folderRepository.findByIdAndOwnerId(folderId, userB)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> controller.files(folderId));
        assertEquals("FOLDER_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void deleteForeignFolderReturns404() {
        when(currentUserService.requireUserId()).thenReturn(userB);
        when(folderRepository.findByIdAndOwnerId(folderId, userB)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> controller.delete(folderId));
        assertEquals("FOLDER_NOT_FOUND", ex.getCode());
    }
}
