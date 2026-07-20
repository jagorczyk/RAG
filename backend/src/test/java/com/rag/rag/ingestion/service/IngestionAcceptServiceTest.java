package com.rag.rag.ingestion.service;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.folder.dto.UploadResultDto;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.entity.IngestionStatus;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.ingestion.cache.ImageAnalysisCacheService;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.face.FaceRecognitionClient;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionAcceptServiceTest {

    private FileRepository fileRepository;
    private IngestionService service;
    private final UUID ownerId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private final AtomicReference<FileEntity> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };

        service = new IngestionService(
                mock(EmbeddingStoreIngestor.class),
                fileRepository,
                mock(FolderRepository.class),
                mock(StructuredVisionExtractor.class),
                mock(EntityMentionRepository.class),
                mock(FactRepository.class),
                mock(IdentitySuggestionRepository.class),
                mock(FaceEmbeddingRepository.class),
                mock(FaceIdentityService.class),
                mock(FaceRecognitionClient.class),
                mock(ImageAnalysisCacheService.class),
                mock(EntityAliasRepository.class),
                mock(KnowledgeEntityRepository.class),
                mock(IdentityResolutionService.class),
                mock(JdbcTemplate.class),
                txManager,
                mock(CurrentUserService.class),
                mock(ObjectProvider.class)
        );
        // Do not run processQueued in this unit — only accept + idempotency
        ReflectionTestUtils.setField(service, "asyncIngestEnabled", true);

        when(fileRepository.findByPath(any())).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(fileRepository.findByPathAndOwnerId(any(), any())).thenAnswer(inv -> {
            FileEntity e = stored.get();
            if (e == null) return Optional.empty();
            UUID requestedOwner = inv.getArgument(1);
            if (requestedOwner != null && requestedOwner.equals(e.getOwnerId())) {
                return Optional.of(e);
            }
            return Optional.empty();
        });
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> {
            FileEntity e = inv.getArgument(0);
            stored.set(e);
            return e;
        });
    }

    @Test
    void acceptUploadStoresPendingAndReturnsPendingWhenAsync() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello world".getBytes(StandardCharsets.UTF_8)
        );
        FolderEntity folder = new FolderEntity();
        folder.setName("docs");
        folder.setOwnerId(ownerId);

        // No publisher bean → inline process would run; force async path with empty publisher
        // ObjectProvider.getIfAvailable returns null by default on mock → inline process.
        // Set a no-op publisher via ObjectProvider mock:
        @SuppressWarnings("unchecked")
        ObjectProvider<com.rag.rag.ingestion.messaging.DocumentIngestPublisher> provider =
                mock(ObjectProvider.class);
        com.rag.rag.ingestion.messaging.DocumentIngestPublisher publisher =
                mock(com.rag.rag.ingestion.messaging.DocumentIngestPublisher.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        ReflectionTestUtils.setField(service, "documentIngestPublisher", provider);

        UploadResultDto result = service.acceptUpload(file, folder, null, ownerId);

        assertEquals("dir://docs/notes.txt", result.path());
        assertEquals(IngestionStatus.PENDING.name(), result.status());
        assertEquals(IngestionStatus.PENDING, stored.get().getIngestionStatus());
        assertEquals(ownerId, stored.get().getOwnerId());
    }

    @Test
    void idempotentReadySkipsRequeue() throws Exception {
        byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        FileEntity ready = FileEntity.builder()
                .path("dir://docs/notes.txt")
                .fileName("notes.txt")
                .ownerId(ownerId)
                .contentHash(hash)
                .ingestionStatus(IngestionStatus.READY)
                .build();
        stored.set(ready);

        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", bytes);
        FolderEntity folder = new FolderEntity();
        folder.setName("docs");
        folder.setOwnerId(ownerId);

        UploadResultDto result = service.acceptUpload(file, folder, null, ownerId);
        assertEquals(IngestionStatus.READY.name(), result.status());
    }

    @Test
    void getIngestionStatusScopedByOwner() {
        FileEntity entity = FileEntity.builder()
                .path("dir://docs/a.txt")
                .ownerId(ownerId)
                .ingestionStatus(IngestionStatus.PENDING)
                .build();
        stored.set(entity);

        assertEquals(Optional.of(IngestionStatus.PENDING),
                service.getIngestionStatus("dir://docs/a.txt", ownerId));
        assertEquals(Optional.empty(),
                service.getIngestionStatus("dir://docs/a.txt", UUID.randomUUID()));
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] dig = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder();
        for (byte b : dig) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
