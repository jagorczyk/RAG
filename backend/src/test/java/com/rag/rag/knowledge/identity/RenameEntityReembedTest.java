package com.rag.rag.knowledge.identity;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.cache.IdentityMatchCacheService;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * End-to-end of shipped renameNamedEntity → document embedding refresh (no face recompute).
 */
@ExtendWith(MockitoExtension.class)
class RenameEntityReembedTest {

    private static final String PATH = "dir://photos/igor.jpg";

    @Mock private KnowledgeEntityRepository entityRepository;
    @Mock private EntityAliasRepository aliasRepository;
    @Mock private EntityMentionRepository mentionRepository;
    @Mock private IdentitySuggestionRepository suggestionRepository;
    @Mock private FaceEmbeddingRepository faceEmbeddingRepository;
    @Mock private FactRepository factRepository;
    @Mock private FileRepository fileRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private ChatLanguageModel chatModel;
    @Mock private IdentityMatchCacheService identityMatchCacheService;
    @Mock private EmbeddingStoreIngestor embeddingStoreIngestor;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private FaceIdentityService faceIdentityService;

    private IdentityResolutionService identityService;
    private CanonicalEmbeddingRefreshService refreshService;
    private UUID entityId;
    private KnowledgeEntity entity;

    @BeforeEach
    void setUp() {
        UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        entityId = UUID.randomUUID();
        entity = KnowledgeEntity.builder()
                .id(entityId).displayName("Igor").type("PERSON").ownerId(ownerId).build();

        refreshService = new CanonicalEmbeddingRefreshService(
                mentionRepository, factRepository, fileRepository, embeddingStoreIngestor, jdbcTemplate);

        @SuppressWarnings("unchecked")
        ObjectProvider<CanonicalEmbeddingRefreshService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(refreshService);

        identityService = new IdentityResolutionService(
                entityRepository, aliasRepository, mentionRepository, suggestionRepository,
                faceEmbeddingRepository, factRepository, fileRepository, currentUserService,
                chatModel, identityMatchCacheService, provider);
        ReflectionTestUtils.setField(identityService, "llmMatcherEnabled", false);

        lenient().when(currentUserService.findUserId()).thenReturn(Optional.of(ownerId));
    }

    @Test
    void renameNamedEntityRefreshesDocumentEmbeddingsWithNewNameAndSkipsFaceRecompute() {
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(entity)
                .filePath(PATH)
                .label("Igor")
                .entityType("PERSON")
                .confidence(BigDecimal.ONE)
                .status(MentionStatus.CONFIRMED)
                .build();
        FileEntity file = FileEntity.builder()
                .path(PATH)
                .fileName("igor.jpg")
                .fileType("image/jpeg")
                .imageScene("plaża")
                .build();

        when(entityRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId(
                "Bartek", "PERSON", entity.getOwnerId())).thenReturn(Optional.empty());
        when(aliasRepository.findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCaseAndEntity_OwnerId(
                "Bartek", "PERSON", entity.getOwnerId())).thenReturn(Optional.empty());
        when(entityRepository.save(any(KnowledgeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(mention));
        when(factRepository.findAllWithMentionAndEntity()).thenReturn(List.of());
        when(aliasRepository.findAll()).thenReturn(List.of());
        when(aliasRepository.findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCase("Igor", "PERSON"))
                .thenReturn(Optional.empty());

        // After rename labels refreshed, refresh service reloads mentions/file for embed text
        when(fileRepository.findByPath(PATH)).thenReturn(Optional.of(file));
        when(mentionRepository.findByFilePath(PATH)).thenReturn(List.of(mention));
        when(factRepository.findByFilePath(PATH)).thenReturn(List.of());

        KnowledgeEntity renamed = identityService.renameNamedEntity(entityId, "Bartek");

        assertTrue("Bartek".equals(renamed.getDisplayName()));
        verify(jdbcTemplate).update(
                eq("DELETE FROM embeddings WHERE metadata->>'path' = ?"), eq(PATH));
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(embeddingStoreIngestor).ingest(captor.capture());
        assertTrue(captor.getValue().text().contains("Bartek"));
        // Face identity service is not on the rename→document re-embed path
        verifyNoInteractions(faceIdentityService);
    }

    @Test
    void afterMentionIdentityAssignedRefreshesPathEmbeddings() {
        UUID mentionId = UUID.randomUUID();
        // Mention rename assigns to an entity already named Bartek (as controller does via findOrCreate).
        KnowledgeEntity bartek = KnowledgeEntity.builder()
                .id(entityId).displayName("Bartek").type("PERSON").ownerId(entity.getOwnerId()).build();
        EntityMention mention = EntityMention.builder()
                .id(mentionId)
                .entity(bartek)
                .filePath(PATH)
                .label("person 1")
                .entityType("PERSON")
                .confidence(BigDecimal.ONE)
                .status(MentionStatus.CONFIRMED)
                .build();
        FileEntity file = FileEntity.builder()
                .path(PATH).fileName("igor.jpg").fileType("image/jpeg").build();

        when(mentionRepository.findById(mentionId)).thenReturn(Optional.of(mention));
        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(mention));
        when(aliasRepository.findAll()).thenReturn(List.of());
        when(factRepository.findByFilePath(PATH)).thenReturn(List.of());
        when(fileRepository.findByPath(PATH)).thenReturn(Optional.of(file));
        when(mentionRepository.findByFilePath(PATH)).thenReturn(List.of(mention));

        identityService.afterMentionIdentityAssigned(mentionId, null, "Bartek");

        verify(jdbcTemplate).update(
                eq("DELETE FROM embeddings WHERE metadata->>'path' = ?"), eq(PATH));
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(embeddingStoreIngestor).ingest(captor.capture());
        assertTrue(captor.getValue().text().contains("Bartek"));
        verifyNoInteractions(faceIdentityService);
    }
}
