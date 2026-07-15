package com.rag.rag.knowledge.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivingEntityTypesTest {

    @Test
    void shouldNormalizeSupportedLivingTypes() {
        assertEquals("PERSON", LivingEntityTypes.normalize(" person "));
        assertEquals("ANIMAL", LivingEntityTypes.normalize("animal"));
    }

    @Test
    void shouldRejectObjectsAndMissingTypes() {
        assertFalse(LivingEntityTypes.isSupported("OBJECT"));
        assertFalse(LivingEntityTypes.isSupported("VEHICLE"));
        assertNull(LivingEntityTypes.normalize(null));
        assertTrue(LivingEntityTypes.isSupported("PERSON"));
    }
}
