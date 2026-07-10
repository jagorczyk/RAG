package com.rag.rag.knowledge.face;

import com.rag.rag.knowledge.entity.KnowledgeEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceIdentityServiceTest {

    @Test
    void shouldReturnOneForIdenticalEmbeddings() {
        float[] vector = {1f, 0f, 0f};
        assertEquals(1.0, FaceIdentityService.cosineSimilarity(vector, vector), 0.0001);
    }

    @Test
    void shouldReturnZeroForOrthogonalEmbeddings() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertEquals(0.0, FaceIdentityService.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void shouldPickBestScorePerEntityWhenMatching() {
        FaceIdentityService service = new FaceIdentityService(null, null, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "suggestionThreshold", 0.36);
        float[] query = {1f, 0f, 0f};

        KnowledgeEntity entityA = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("A").build();
        KnowledgeEntity entityB = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("B").build();

        List<FaceEmbedding> stored = List.of(
                FaceEmbedding.builder().entity(entityA).embedding(new float[] {0.95f, 0.05f, 0f}).build(),
                FaceEmbedding.builder().entity(entityA).embedding(new float[] {0.5f, 0.5f, 0f}).build(),
                FaceEmbedding.builder().entity(entityB).embedding(new float[] {0.99f, 0.01f, 0f}).build()
        );

        Optional<FaceIdentityService.EntityMatch> match = service.findBestEntityMatchFrom(stored, query);

        assertTrue(match.isPresent());
        assertEquals(entityB.getId(), match.get().entity().getId());
        assertTrue(match.get().score() > 0.99);
    }
}
