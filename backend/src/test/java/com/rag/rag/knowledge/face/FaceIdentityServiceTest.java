package com.rag.rag.knowledge.face;

import com.rag.rag.knowledge.entity.KnowledgeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceIdentityServiceTest {

    private FaceIdentityService service;

    @BeforeEach
    void setUp() {
        service = new FaceIdentityService(null, null, null, null, null);
        ReflectionTestUtils.setField(service, "suggestionThreshold", 0.50);
        ReflectionTestUtils.setField(service, "minMargin", 0.08);
    }

    @Test
    void shouldReturnOneForIdenticalNormalizedEmbeddings() {
        float[] vector = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});
        assertEquals(1.0, FaceIdentityService.cosineSimilarity(vector, vector), 0.0001);
    }

    @Test
    void shouldReturnZeroForOrthogonalEmbeddings() {
        float[] a = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f});
        float[] b = FaceIdentityService.normalizeEmbedding(new float[] {0f, 1f});
        assertEquals(0.0, FaceIdentityService.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void shouldPickBestEntityUsingTopEmbeddingAverage() {
        float[] query = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});

        KnowledgeEntity entityA = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("A").build();
        KnowledgeEntity entityB = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("B").build();

        List<FaceEmbedding> stored = List.of(
                FaceEmbedding.builder().entity(entityA).embedding(new float[] {0.80f, 0.60f, 0f}).build(),
                FaceEmbedding.builder().entity(entityA).embedding(new float[] {0.5f, 0.5f, 0f}).build(),
                FaceEmbedding.builder().entity(entityB).embedding(new float[] {0.99f, 0.01f, 0f}).build()
        );

        Optional<FaceIdentityService.EntityMatch> match = service.rankEntityMatches(stored, query, 0.50);

        assertTrue(match.isPresent());
        assertEquals(entityB.getId(), match.get().entity().getId());
        assertTrue(match.get().score() > 0.99);
    }

    @Test
    void shouldRejectAmbiguousMatchWhenMarginIsTooSmall() {
        float[] query = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});

        KnowledgeEntity entityA = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("A").build();
        KnowledgeEntity entityB = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("B").build();

        List<FaceEmbedding> stored = List.of(
                FaceEmbedding.builder().entity(entityA).embedding(new float[] {0.92f, 0.08f, 0f}).build(),
                FaceEmbedding.builder().entity(entityB).embedding(new float[] {0.90f, 0.10f, 0f}).build()
        );

        Optional<FaceIdentityService.EntityMatch> match = service.rankEntityMatches(stored, query, 0.50);

        assertFalse(match.isPresent());
    }
}
