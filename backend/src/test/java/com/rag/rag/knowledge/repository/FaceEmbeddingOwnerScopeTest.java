package com.rag.rag.knowledge.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural proof that shipped gallery queries accept owner scoping
 * (cross-user face match exclusion).
 */
class FaceEmbeddingOwnerScopeTest {

    @Test
    void ownerScopedGalleryMethodsExistWithOwnerFilterInQuery() throws Exception {
        Method exceptOwner = FaceEmbeddingRepository.class.getMethod(
                "findAllExceptFilePathForOwner", String.class, UUID.class);
        Method galleryOwner = FaceEmbeddingRepository.class.getMethod(
                "findAllConfirmedGalleryForOwner", UUID.class);
        Method vectorOwner = FaceEmbeddingRepository.class.getMethod(
                "findTopKByVectorDistanceForOwner",
                String.class, String.class, UUID.class, java.math.BigDecimal.class, int.class);

        assertTrue(exceptOwner.getAnnotation(Query.class).value().contains("ownerId"));
        assertTrue(galleryOwner.getAnnotation(Query.class).value().contains("ownerId"));
        assertTrue(vectorOwner.getAnnotation(Query.class).value().toLowerCase().contains("owner_id"));
        assertNotNull(exceptOwner);
        assertNotNull(galleryOwner);
        assertNotNull(vectorOwner);
    }
}
