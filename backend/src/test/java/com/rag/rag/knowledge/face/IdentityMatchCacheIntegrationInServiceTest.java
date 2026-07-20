package com.rag.rag.knowledge.face;

import com.rag.rag.core.cache.IdentityMatchCacheService;
import com.rag.rag.core.cache.IdentityMatchCacheService.CachedIdentityMatch;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.graph.MentionEvidencePolicy;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves {@link FaceIdentityService#findBestEntityMatch} uses the shipped Redis cache façade
 * on hit (no gallery query) and populates cache on miss.
 */
class IdentityMatchCacheIntegrationInServiceTest {

    private FaceIdentityService service;
    private IdentityMatchCacheService cache;
    private KnowledgeEntityRepository entityRepository;
    private FaceEmbeddingRepository faceEmbeddingRepository;
    private IdentityResolutionService identityResolutionService;

    private final UUID entityId = UUID.randomUUID();
    private final KnowledgeEntity entity = KnowledgeEntity.builder()
            .id(entityId)
            .displayName("Anna")
            .type("PERSON")
            .build();

    @BeforeEach
    void setUp() {
        cache = mock(IdentityMatchCacheService.class);
        entityRepository = mock(KnowledgeEntityRepository.class);
        faceEmbeddingRepository = mock(FaceEmbeddingRepository.class);
        identityResolutionService = mock(IdentityResolutionService.class);
        when(identityResolutionService.isGenericPersonLabel(anyString())).thenReturn(false);

        service = new FaceIdentityService(
                null,
                faceEmbeddingRepository,
                null,
                null,
                identityResolutionService,
                new MentionEvidencePolicy(),
                null,
                null,
                cache,
                entityRepository
        );
        ReflectionTestUtils.setField(service, "vectorSearchEnabled", false);
        ReflectionTestUtils.setField(service, "suggestionThreshold", 0.50);
        ReflectionTestUtils.setField(service, "minMargin", 0.08);
        ReflectionTestUtils.setField(service, "minDetScore", 0.50);

        when(cache.buildKey(any(), any(), anyDouble())).thenReturn("stable-key");
    }

    @Test
    void cacheHitSkipsGalleryQuery() {
        when(cache.get("stable-key")).thenReturn(Optional.of(
                new CachedIdentityMatch(entityId, 0.95, 0.94, 0.15, false)));
        when(entityRepository.findById(entityId)).thenReturn(Optional.of(entity));

        float[] query = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});
        Optional<FaceIdentityService.EntityMatch> match =
                service.findBestEntityMatch(query, "dir://x.jpg", 0.50);

        assertTrue(match.isPresent());
        assertEquals(entityId, match.get().entity().getId());
        assertEquals(0.95, match.get().score(), 0.0001);
        verify(faceEmbeddingRepository, never()).findAllExceptFilePath(anyString());
        verify(faceEmbeddingRepository, never()).findAllConfirmedGallery();
        verify(cache, never()).putHit(anyString(), any(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void cacheMissRunsMatchAndStoresResult() {
        when(cache.get("stable-key")).thenReturn(Optional.empty());
        EntityMention certainMention = EntityMention.builder()
                .id(UUID.randomUUID())
                .filePath("dir://gallery.jpg")
                .label("Anna")
                .entityType("PERSON")
                .status(MentionStatus.CONFIRMED)
                .entity(entity)
                .confidence(BigDecimal.ONE)
                .build();
        when(faceEmbeddingRepository.findAllExceptFilePath("dir://x.jpg")).thenReturn(java.util.List.of(
                FaceEmbedding.builder()
                        .entity(entity)
                        .mention(certainMention)
                        .embedding(new float[] {1f, 0f, 0f})
                        .detScore(BigDecimal.valueOf(0.9))
                        .build()
        ));

        float[] query = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});
        Optional<FaceIdentityService.EntityMatch> match =
                service.findBestEntityMatch(query, "dir://x.jpg", 0.50);

        assertTrue(match.isPresent());
        assertEquals(entityId, match.get().entity().getId());
        verify(cache, times(1)).putHit(eq("stable-key"), eq(entityId), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void cacheNegativeHitReturnsEmptyWithoutGallery() {
        when(cache.get("stable-key")).thenReturn(Optional.of(CachedIdentityMatch.notFound()));

        float[] query = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});
        Optional<FaceIdentityService.EntityMatch> match =
                service.findBestEntityMatch(query, "dir://x.jpg", 0.50);

        assertTrue(match.isEmpty());
        verify(faceEmbeddingRepository, never()).findAllExceptFilePath(anyString());
    }
}
