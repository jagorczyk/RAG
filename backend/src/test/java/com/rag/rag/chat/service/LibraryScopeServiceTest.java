package com.rag.rag.chat.service;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibraryScopeServiceTest {

    @Test
    void resolvesOnlyOwnedFolderAndExpandsItsExactFileSet() {
        FolderRepository folders = mock(FolderRepository.class);
        FileRepository files = mock(FileRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        UUID ownerId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = new FolderEntity();
        folder.setId(folderId);
        folder.setName("Wakacje 2024");
        folder.setOwnerId(ownerId);
        when(currentUser.findUserId()).thenReturn(Optional.of(ownerId));
        when(folders.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)).thenReturn(List.of(folder));
        when(folders.findByIdAndOwnerId(folderId, ownerId)).thenReturn(Optional.of(folder));
        when(files.findAllByOwnerId(ownerId)).thenReturn(List.of(
                FileEntity.builder().path("dir://Wakacje 2024/a.jpg").build(),
                FileEntity.builder().path("dir://Inne/b.jpg").build()));
        LibraryScopeService service = new LibraryScopeService(folders, files, currentUser);

        LibraryScopeService.FolderResolution resolution =
                service.resolveFolderNames(List.of("wakacje 2024"));

        assertEquals(List.of(folderId), resolution.ids());
        assertFalse(resolution.ambiguous());
        assertFalse(resolution.unresolved());
        assertEquals(List.of("dir://Wakacje 2024/a.jpg"),
                service.filePathsForFolders(resolution.ids()));
    }
}
