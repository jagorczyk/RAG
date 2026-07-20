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
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Nie jestem w stanie odpowiedzieć na to pytanie, ponieważ nie mam informacji, o kogo konkretnie pytasz."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Nie wiem, o kogo pytasz — podaj więcej szczegółów."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Aby odpowiedzieć na pytanie, potrzebuję więcej informacji. Czy możesz podać dostępne zdjęcia lub opisać, jak wygląda osoba?"));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Need more information — describe how they look."));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(
                "Na zdjęciu są Igor i Anna."));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(
                "Nie znaleziono informacji w dokumentach."));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(""));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(null));
    }

    @Test
    void detectsGeneralKnowledgeEssayShape() {
        assertTrue(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "To imię męskie, które jest zdrobnieniem od imienia. Imię pochodzi z języka greckiego."));
        assertTrue(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "W kulturze popularnej istnieje wiele postaci o tym imieniu."));
        assertFalse(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "Osoba stoi w siłowni w czarnej koszulce."));
        assertFalse(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "Nie znaleziono informacji w dokumentach."));
    }

    @Test
    void resolveAgainstIdentityIgnoranceUsesRosterWhenNamesExist() {
        String denial = "Nie jestem w stanie odpowiedzieć na to pytanie, ponieważ nie mam informacji, o kogo konkretnie pytasz. Jeśli podasz więcej szczegółów lub kontekst, postaram się pomóc!";
        String resolved = ChatAnswerGrounding.resolveAgainstDenial(
                denial, List.of("Olek", "Piotrek", "Bargiel", "Dawid", "Igor"), true);
        assertTrue(resolved.contains("Olek"));
        assertTrue(resolved.contains("Igor"));
        assertFalse(resolved.contains("animal"));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(resolved));
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
    void entityScopedPresenceDoesNotListUnrelatedCoPresentPeople() {
        String denial = "Potrzebuję więcej informacji. Opisz, jak wygląda ta osoba.";
        // Recovery names are only the plan entities — not the full multi-file roster.
        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                denial, List.of("Anna"), true, true);
        assertTrue(resolved.contains("Anna"));
        assertTrue(resolved.contains("potwierdzonych zdjęciach"));
        assertFalse(resolved.contains("Igor"));
        assertFalse(resolved.contains("Dawid"));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(resolved));
        // Full roster shape must not be used for entity-scoped recovery.
        assertFalse(resolved.startsWith("Na zdjęciu są"));
    }

    @Test
    void entityScopedGeneralKnowledgeEssayIsReplacedByPresence() {
        String essay = "To imię męskie, które jest zdrobnieniem. Imię pochodzi z języka greckiego i oznacza skałę. "
                + "W kulturze popularnej jest popularne.";
        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                essay, List.of("Anna"), true, true);
        assertEquals("Anna jest na potwierdzonych zdjęciach w bibliotece.", resolved);
        assertFalse(ChatAnswerGrounding.isGeneralKnowledgeEssay(resolved));
    }

    @Test
    void nonEntityScopedKeepsFullRosterOnDenial() {
        String denial = "Nie mogę zobaczyć zdjęć.";
        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                denial, List.of("Igor", "Anna", "Dawid"), true, false);
        assertEquals("Na zdjęciu są Igor, Anna i Dawid.", resolved);
    }

    @Test
    void goodEvidenceBackedAnswerUnchangedEvenWhenEntityScoped() {
        String good = "Osoba stoi w siłowni w czarnej koszulce i niebieskiej czapce.";
        assertEquals(good, ChatAnswerGrounding.resolveGroundedAnswer(
                good, List.of("Anna"), true, true));
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

    @Test
    void formatEntityScopedPresenceHasNoFilenames() {
        String one = ChatAnswerGrounding.formatEntityScopedPresence(List.of("Igor"));
        assertEquals("Igor jest na potwierdzonych zdjęciach w bibliotece.", one);
        assertFalse(one.contains(".jpg"));
        assertFalse(one.contains("dir://"));

        String multi = ChatAnswerGrounding.formatEntityScopedPresence(List.of("Igor", "Anna"));
        assertEquals("Igor i Anna są na potwierdzonych zdjęciach w bibliotece.", multi);
    }
}
