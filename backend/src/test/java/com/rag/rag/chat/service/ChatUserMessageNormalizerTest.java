package com.rag.rag.chat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatUserMessageNormalizerTest {

    @Test
    void extractsLegacyDbPromptFormat() {
        String blob = "PYTANIE UŻYTKOWNIKA: What is this?\n\nDANE Z BAZY DANYCH:\nsome db data";
        assertEquals("What is this?", ChatUserMessageNormalizer.extractOriginalQuestion(blob));
    }

    @Test
    void extractsCurrentAnswerPromptWithUserQuestionMarker() {
        String blob = ChatService.ANSWER_INSTRUCTIONS + """

                [Styl odpowiedzi]
                Jedno lub dwa krótkie zdania po polsku.

                [Instrukcja odpowiedzi]
                Odpowiedz z dokumentów.
                Odpowiedź: jedno krótkie zdanie po polsku.

                Pytanie użytkownika: Jakie jest saldo na fakturze?
                """;
        assertEquals("Jakie jest saldo na fakturze?",
                ChatUserMessageNormalizer.extractOriginalQuestion(blob));
        assertFalse(ChatUserMessageNormalizer.extractOriginalQuestion(blob)
                .contains("Jesteś asystentem"));
    }

    @Test
    void extractsGraphVerifiedPrompt() {
        String blob = """
                [Kontekst zweryfikowany]
                - entity=Igor; file=dir://a.jpg

                [Instrukcja odpowiedzi]
                Krótko.

                Pytanie użytkownika: Co robi Igor?
                """;
        assertEquals("Co robi Igor?", ChatUserMessageNormalizer.extractOriginalQuestion(blob));
    }

    @Test
    void leavesBareQuestionUnchanged() {
        assertEquals("Cześć, co wiesz o fakturze?",
                ChatUserMessageNormalizer.extractOriginalQuestion("Cześć, co wiesz o fakturze?"));
    }

    @Test
    void stripsDocumentScaffoldAfterQuestionMarker() {
        String blob = """
                Pytanie użytkownika: Kto jest na zdjęciu?
                Dokumenty:
                [Folder: x, Plik: y]
                text
                """;
        assertEquals("Kto jest na zdjęciu?", ChatUserMessageNormalizer.extractOriginalQuestion(blob));
    }
}
