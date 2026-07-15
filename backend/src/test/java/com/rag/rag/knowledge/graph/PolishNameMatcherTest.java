package com.rag.rag.knowledge.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PolishNameMatcherTest {

    @ParameterizedTest
    @CsvSource({
            "Bartek, bartka",
            "Bartka, bartek",
            "Bartku, bartek",
            "Bartkiem, bartek",
            "Igor, igora",
            "Igora, igor"
    })
    void shouldGeneratePolishNameInflectionVariants(String input, String expectedVariant) {
        assertTrue(PolishNameMatcher.generateVariants(input).contains(expectedVariant));
    }

    @Test
    void shouldMatchGenitiveNameInQuestion() {
        String question = "kto siedzi obok Bartka";
        assertTrue(PolishNameMatcher.generateVariants("Bartek").stream()
                .anyMatch(variant -> question.toLowerCase().contains(variant)));
    }
}
