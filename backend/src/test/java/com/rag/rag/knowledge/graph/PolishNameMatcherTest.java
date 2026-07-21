package com.rag.rag.knowledge.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolishNameMatcherTest {

    @Test
    void matchesPolishCaseVariants() {
        assertTrue(PolishNameMatcher.namesMatch("Olka", "Olek"));
        assertTrue(PolishNameMatcher.namesMatch("olek", "Olek"));
        assertTrue(PolishNameMatcher.namesMatch("Piotrka", "Piotrek"));
        assertTrue(PolishNameMatcher.namesMatch("Anny", "Anna"));
        assertFalse(PolishNameMatcher.namesMatch("Olek", "Bartek"));
        assertFalse(PolishNameMatcher.namesMatch("kto", "Olek"));
    }
}
