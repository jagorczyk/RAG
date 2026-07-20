package com.rag.rag.chat.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatAnswerGroundingTest {

    @Test
    void detectsPolishAndEnglishCapabilityDenials() {
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Niestety, nie mogę zobaczyć zdjęć ani obrazów."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Nie mam dostępu do konkretnych plików, zdjęć ani obrazów."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "I cannot see the image you mentioned."));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(
                "Na zdjęciu są Igor i Anna."));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(
                "Nie znaleziono informacji w dokumentach."));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(""));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(null));
    }

    @Test
    void formatsParticipantRosterInPolish() {
        assertEquals("Na zdjęciu jest Igor.",
                ChatAnswerGrounding.formatParticipantRoster(List.of("Igor")));
        assertEquals("Na zdjęciu są Igor i Anna.",
                ChatAnswerGrounding.formatParticipantRoster(List.of("Igor", "Anna")));
        assertEquals("Na zdjęciu są Igor, Anna i Dawid.",
                ChatAnswerGrounding.formatParticipantRoster(List.of("Igor", "Anna", "Dawid")));
        assertEquals(ChatAnswerGrounding.GROUNDED_NO_ROSTER_FALLBACK,
                ChatAnswerGrounding.formatParticipantRoster(List.of()));
    }

    @Test
    void resolveAgainstDenialReplacesRefusalWhenEvidenceExists() {
        String denial = "Nie mogę zobaczyć zdjęć, więc nie wiem, kto tam jest.";
        String resolved = ChatAnswerGrounding.resolveAgainstDenial(
                denial, List.of("Igor", "Piotrek", "Bargiel"), true);
        assertTrue(resolved.contains("Igor"));
        assertTrue(resolved.contains("Piotrek"));
        assertTrue(resolved.contains("Bargiel"));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(resolved));
    }

    @Test
    void resolveAgainstDenialKeepsGoodAnswerAndPreservesNoEvidencePath() {
        String good = "Na zdjęciu stoi Igor w garniturze.";
        assertEquals(good, ChatAnswerGrounding.resolveAgainstDenial(good, List.of("Igor"), true));

        String denial = "Nie mam dostępu do plików.";
        // No certain evidence → leave denial as-is (caller handles no-grounding separately).
        assertEquals(denial, ChatAnswerGrounding.resolveAgainstDenial(denial, List.of(), false));

        // Evidence without names → non-denial grounded fallback, not vision refusal.
        String fallback = ChatAnswerGrounding.resolveAgainstDenial(denial, List.of(), true);
        assertEquals(ChatAnswerGrounding.GROUNDED_NO_ROSTER_FALLBACK, fallback);
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(fallback));
    }

    @Test
    void cleanEmptyQuoteArtifactsRemovesShellsAfterFilenameStrip() {
        String stripped = "Nie mam dostępu do plików, takich jak „”, więc nie mogę pomóc.";
        String cleaned = ChatAnswerGrounding.cleanEmptyQuoteArtifacts(stripped);
        assertFalse(cleaned.contains("„”"));
        assertFalse(cleaned.contains("\"\""));
        assertFalse(cleaned.toLowerCase().contains("takich jak"));

        String withEmptyDouble = "Brak pliku \"\" w bibliotece.";
        String cleaned2 = ChatAnswerGrounding.cleanEmptyQuoteArtifacts(withEmptyDouble);
        assertFalse(cleaned2.contains("\"\""));
    }
}
