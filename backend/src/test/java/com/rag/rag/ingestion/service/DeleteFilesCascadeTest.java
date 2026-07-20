package com.rag.rag.ingestion.service;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.ingestion.cache.ImageAnalysisCacheService;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.face.FaceRecognitionClient;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FaceObservationRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the real {@link IngestionService#deleteFiles} path and asserts cascade cleanup
 * (embeddings SQL, mentions, facts, face data, suggestions, orphan knowledge entities).
 */
class DeleteFilesCascadeTest {

    private static final String PATH = "dir://photos/party.jpg";

    private EntityMentionRepository mentionRepo;
    private FactRepository factRepo;
    private IdentitySuggestionRepository suggestionRepo;
    private FaceEmbeddingRepository faceEmbeddingRepository;
    private FaceObservationRepository faceObservationRepository;
    private EntityAliasRepository aliasRepo;
    private KnowledgeEntityRepository knowledgeEntityRepo;
    private FileRepository fileRepository;
    private JdbcTemplate jdbcTemplate;
    private IngestionService service;

    private UUID mentionId;
    private UUID entityId;
    private KnowledgeEntity entity;
    private final AtomicBoolean entityDeleted = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        mentionRepo = mock(EntityMentionRepository.class);
        factRepo = mock(FactRepository.class);
        suggestionRepo = mock(IdentitySuggestionRepository.class);
        faceEmbeddingRepository = mock(FaceEmbeddingRepository.class);
        faceObservationRepository = mock(FaceObservationRepository.class);
        aliasRepo = mock(EntityAliasRepository.class);
        knowledgeEntityRepo = mock(KnowledgeEntityRepository.class);
        fileRepository = mock(FileRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);

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
                mentionRepo,
                factRepo,
                suggestionRepo,
                faceEmbeddingRepository,
                faceObservationRepository,
                mock(FaceIdentityService.class),
                mock(FaceRecognitionClient.class),
                mock(ImageAnalysisCacheService.class),
                aliasRepo,
                knowledgeEntityRepo,
                mock(IdentityResolutionService.class),
                jdbcTemplate,
                txManager,
                mock(CurrentUserService.class),
                mock(ObjectProvider.class)
        );

        mentionId = UUID.randomUUID();
        entityId = UUID.randomUUID();
        entity = KnowledgeEntity.builder().id(entityId).displayName("Igor").type("PERSON").build();

        EntityMention mention = EntityMention.builder()
                .id(mentionId)
                .filePath(PATH)
                .label("person 1")
                .entityType("PERSON")
                .confidence(BigDecimal.valueOf(0.9))
                .status(MentionStatus.CONFIRMED)
                .entity(entity)
                .build();

        when(mentionRepo.findByFilePath(PATH)).thenReturn(List.of(mention));
        // After deleteByFilePath, entity has no remaining mentions → orphan cleanup
        when(mentionRepo.findByEntityId(entityId)).thenReturn(List.of());
        when(knowledgeEntityRepo.findById(entityId)).thenReturn(Optional.of(entity));
        when(fileRepository.findByPath(PATH)).thenReturn(Optional.of(new FileEntity()));
    }

    @Test
    void deleteFilesRemovesAllPathArtifactsAndOrphanEntity() {
        service.deleteFiles(List.of(PATH));

        verify(suggestionRepo).deleteByMentionIds(anyCollection());
        verify(faceEmbeddingRepository).deleteByMentionIdIn(anyList());
        verify(faceObservationRepository).deleteByMentionIds(anyCollection());
        verify(factRepo).deleteByFilePath(PATH);
        verify(faceEmbeddingRepository).deleteByFilePath(PATH);
        verify(faceObservationRepository).deleteByFilePath(PATH);
        verify(mentionRepo).deleteByFilePath(PATH);
        verify(jdbcTemplate).update(eq("DELETE FROM embeddings WHERE metadata->>'path' = ?"), eq(PATH));
        verify(fileRepository).delete(any(FileEntity.class));

        // Orphan entity cleanup
        verify(faceEmbeddingRepository).deleteByEntityId(entityId);
        verify(aliasRepo).deleteByEntityId(entityId);
        verify(knowledgeEntityRepo).delete(entity);
    }

    @Test
    void deleteFilesKeepsEntityWhenOtherMentionsRemain() {
        EntityMention other = EntityMention.builder()
                .id(UUID.randomUUID())
                .filePath("dir://other.jpg")
                .label("Igor")
                .entityType("PERSON")
                .confidence(BigDecimal.ONE)
                .status(MentionStatus.CONFIRMED)
                .entity(entity)
                .build();
        when(mentionRepo.findByEntityId(entityId)).thenReturn(List.of(other));

        service.deleteFiles(List.of(PATH));

        verify(mentionRepo).deleteByFilePath(PATH);
        verify(knowledgeEntityRepo, never()).delete(any());
        verify(faceEmbeddingRepository, never()).deleteByEntityId(entityId);
    }

    @Test
    void cleanupOrphanKnowledgeEntityNoopsWhenMentionsRemain() {
        when(mentionRepo.findByEntityId(entityId)).thenReturn(List.of(
                EntityMention.builder().id(UUID.randomUUID()).filePath("x").label("a")
                        .confidence(BigDecimal.ONE).entity(entity).build()
        ));

        service.cleanupOrphanKnowledgeEntity(entityId);

        verify(knowledgeEntityRepo, never()).delete(any());
        assertFalse(entityDeleted.get());
    }

    @Test
    void cleanupOrphanKnowledgeEntityDeletesWhenNoMentions() {
        when(mentionRepo.findByEntityId(entityId)).thenReturn(List.of());
        when(knowledgeEntityRepo.findById(entityId)).thenReturn(Optional.of(entity));

        service.cleanupOrphanKnowledgeEntity(entityId);

        verify(faceEmbeddingRepository).deleteByEntityId(entityId);
        verify(aliasRepo).deleteByEntityId(entityId);
        verify(knowledgeEntityRepo).delete(entity);
        assertTrue(true);
    }
}
