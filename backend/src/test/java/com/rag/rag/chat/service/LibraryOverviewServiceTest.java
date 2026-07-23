package com.rag.rag.chat.service;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.entity.IngestionStatus;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.knowledge.graph.GraphEvidenceItem;
import com.rag.rag.knowledge.graph.GraphQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryOverviewServiceTest {

    @Test
    void buildsClosedCatalogInventoryWithUniqueCertainPeopleAndNoSemanticEvidence() {
        FileRepository files = mock(FileRepository.class);
        FolderRepository folders = mock(FolderRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        GraphQueryService graph = mock(GraphQueryService.class);
        UUID ownerId = UUID.randomUUID();
        FolderEntity wakacje = folder("Wakacje", ownerId);
        FolderEntity dokumenty = folder("Dokumenty", ownerId);
        FileEntity photoA = file("dir://Wakacje/a.jpg", "image/jpeg", IngestionStatus.READY);
        FileEntity photoB = file("dir://Wakacje/b.jpg", "image/jpeg", IngestionStatus.PENDING);
        FileEntity pdf = file("dir://Dokumenty/plan.pdf", "application/pdf",
                IngestionStatus.NEEDS_REVIEW);
        when(currentUser.findUserId()).thenReturn(Optional.of(ownerId));
        when(folders.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId))
                .thenReturn(List.of(wakacje, dokumenty));
        when(files.findAllByOwnerId(ownerId)).thenReturn(List.of(photoA, photoB, pdf));
        when(graph.certainParticipantNamesByPath(anyList())).thenReturn(Map.of(
                photoA.getPath(), List.of("Igor", "Olek"),
                photoB.getPath(), List.of("Igor")));

        LibraryOverviewService service = service(files, folders, currentUser, graph);
        QueryPlan plan = overviewPlan(QueryPlan.ScopeKind.UNRESTRICTED, List.of(), List.of());

        LibraryOverviewService.Overview overview = service.build(plan);

        assertEquals(3, overview.fileCount());
        assertEquals(2, overview.folderCount());
        assertEquals(2, overview.confirmedPersonCount());
        assertEquals(0, overview.omittedFileNameCount());
        assertEquals(0, overview.omittedPersonNameCount());
        assertTrue(overview.evidence().certainPaths().isEmpty());
        assertTrue(overview.evidence().context().contains("Igor"));
        assertTrue(overview.evidence().context().contains("Olek"));
        assertTrue(overview.evidence().context().contains("a.jpg"));
        assertTrue(overview.evidence().context().contains("plan.pdf"));
        assertTrue(overview.evidence().context().contains("Status analizy"));
        assertFalse(overview.evidence().context().contains("scene_summary"));
        assertFalse(overview.evidence().context().contains("Na zdjęciu widać"));
        assertTrue(overview.evidence().photos().stream()
                .flatMap(photo -> photo.items().stream())
                .allMatch(item -> item.kind() == GraphEvidenceItem.Kind.INVENTORY
                        && item.sourcePath().isBlank()));
        verify(graph, never()).buildEvidence(anyList(), anyList(), any());
    }

    @Test
    void limitsFileNamesToOneHundredAndReportsExactOmittedCount() {
        FileRepository files = mock(FileRepository.class);
        FolderRepository folders = mock(FolderRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        GraphQueryService graph = mock(GraphQueryService.class);
        UUID ownerId = UUID.randomUUID();
        FolderEntity folder = folder("Archiwum", ownerId);
        List<FileEntity> catalog = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            catalog.add(file("dir://Archiwum/a%03d.jpg".formatted(index),
                    "image/jpeg", IngestionStatus.READY));
        }
        catalog.add(file("dir://Archiwum/zzzz.jpg", "image/jpeg", IngestionStatus.READY));
        when(currentUser.findUserId()).thenReturn(Optional.of(ownerId));
        when(folders.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)).thenReturn(List.of(folder));
        when(files.findAllByOwnerId(ownerId)).thenReturn(catalog);
        when(graph.certainParticipantNamesByPath(anyList())).thenReturn(Map.of());

        LibraryOverviewService service = service(files, folders, currentUser, graph);
        ReflectionTestUtils.setField(service, "maxFileNames", 100);

        LibraryOverviewService.Overview overview = service.build(
                overviewPlan(QueryPlan.ScopeKind.UNRESTRICTED, List.of(), List.of()));

        assertEquals(101, overview.fileCount());
        assertEquals(1, overview.omittedFileNameCount());
        assertTrue(overview.evidence().context().contains("Pokazano 100 nazw, a 1 pominięto"));
        assertFalse(overview.evidence().context().contains("zzzz.jpg"));
        assertTrue(overview.inventoryAnswer().contains("pominięto 1 nazw"));
    }

    @Test
    void selectedEmptyFolderStillProducesCertainCatalogAnswer() {
        FileRepository files = mock(FileRepository.class);
        FolderRepository folders = mock(FolderRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        GraphQueryService graph = mock(GraphQueryService.class);
        UUID ownerId = UUID.randomUUID();
        FolderEntity folder = folder("Pusty", ownerId);
        when(currentUser.findUserId()).thenReturn(Optional.of(ownerId));
        when(folders.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)).thenReturn(List.of(folder));
        when(folders.findByIdAndOwnerId(folder.getId(), ownerId)).thenReturn(Optional.of(folder));
        when(files.findAllByPathInAndOwnerId(List.of(), ownerId)).thenReturn(List.of());

        LibraryOverviewService service = service(files, folders, currentUser, graph);
        QueryPlan plan = overviewPlan(QueryPlan.ScopeKind.FOLDER, List.of(folder.getId()), List.of());

        LibraryOverviewService.Overview overview = service.build(plan);

        assertTrue(overview.empty());
        assertFalse(overview.unavailable());
        assertEquals(1, overview.folderCount());
        assertTrue(overview.inventoryAnswer().contains("Folder „Pusty”: 0 plików"));
    }

    private static LibraryOverviewService service(
            FileRepository files, FolderRepository folders,
            CurrentUserService currentUser, GraphQueryService graph) {
        LibraryOverviewService service = new LibraryOverviewService(files, folders, currentUser, graph);
        ReflectionTestUtils.setField(service, "maxFileNames", 100);
        ReflectionTestUtils.setField(service, "maxPersonNames", 100);
        return service;
    }

    private static QueryPlan overviewPlan(
            QueryPlan.ScopeKind scopeKind, List<UUID> folderIds, List<String> fileScope) {
        return new QueryPlan("Co jest w zakresie?", List.of(), fileScope,
                "Co jest w zakresie?", "inwentarz", false, false,
                QueryPlan.RetrievalMode.HYBRID,
                com.rag.rag.knowledge.graph.EntityMatchMode.ANY, "",
                scopeKind, folderIds, true);
    }

    private static FolderEntity folder(String name, UUID ownerId) {
        return new FolderEntity(UUID.randomUUID(), name, ownerId,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static FileEntity file(String path, String type, IngestionStatus status) {
        return FileEntity.builder()
                .path(path)
                .fileName(path.substring(path.lastIndexOf('/') + 1))
                .fileType(type)
                .ingestionStatus(status)
                .build();
    }
}
