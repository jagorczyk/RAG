package com.rag.rag.ingestion.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageAnalysisCacheServiceTest {

    @Test
    void rejectsLegacyLobOidPayloads() {
        assertFalse(ImageAnalysisCacheService.isUsableCachePayload(null));
        assertFalse(ImageAnalysisCacheService.isUsableCachePayload(""));
        assertFalse(ImageAnalysisCacheService.isUsableCachePayload("162191"));
        assertFalse(ImageAnalysisCacheService.isUsableCachePayload("42"));
        assertTrue(ImageAnalysisCacheService.isUsableCachePayload(
                "{\"resultDto\":null,\"rawText\":\"ok\",\"structured\":false}"));
        assertTrue(ImageAnalysisCacheService.isUsableCachePayload("[{\"faces\":[]}]"));
    }
}
