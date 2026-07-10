package com.rag.rag.knowledge.face;

import org.junit.jupiter.api.Test;

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
    void shouldReturnHighScoreForSimilarEmbeddings() {
        float[] a = {0.9f, 0.1f, 0.2f};
        float[] b = {0.88f, 0.12f, 0.18f};
        assertTrue(FaceIdentityService.cosineSimilarity(a, b) > 0.99);
    }
}
