package com.rag.rag.core.retrieval;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnswerContentInjectionTest {

    @Test
    void injectPutsGraphAndSegmentBodiesInUserMessage() {
        String answerPrompt = """
                [Pełny graf wiedzy dla wskazanych zdjęć]
                - entity=Olek; visual_cues=["czerwona koszulka"]; file=dir://photos/olek.jpg
                - scene=plaża latem

                [Instrukcja odpowiedzi]
                Odpowiedz z dowodów.

                Pytanie użytkownika: Co robi Olek?
                """;
        Content segment = Content.from(TextSegment.from(
                "Uczestnik: Olek. Czynności: stoi na plaży. Wygląd: czerwona koszulka.",
                Metadata.from(Map.of(
                        "filename", "olek.jpg",
                        "document_id", "photos",
                        "path", "dir://photos/olek.jpg"))));

        UserMessage injected = AnswerContentInjection.inject(List.of(segment), answerPrompt, 1500);
        String text = injected.singleText();

        assertTrue(text.contains("entity=Olek"));
        assertTrue(text.contains("czerwona koszulka"));
        assertTrue(text.contains("Dokumenty:"));
        assertTrue(text.contains("stoi na plaży"));
        assertTrue(text.contains("Co robi Olek?"));
        assertTrue(AnswerContentInjection.containsDocumentSegments(text));
        // Must not drop segment body to empty Dokumenty header only.
        assertFalse(text.trim().endsWith("Dokumenty:"));
    }

    @Test
    void injectWithEmptyContentsKeepsGraphAndDoesNotInventDocuments() {
        String answerPrompt = """
                [Pełny graf wiedzy]
                - entity=Olek; file=dir://a.jpg

                Pytanie użytkownika: Co robi Olek?
                """;
        UserMessage injected = AnswerContentInjection.inject(List.of(), answerPrompt, 1500);
        String text = injected.singleText();
        assertTrue(text.contains("entity=Olek"));
        assertTrue(text.contains("Co robi Olek?"));
        assertFalse(AnswerContentInjection.containsDocumentSegments(text));
    }

    @Test
    void injectWithoutGraphOrContentsRefusesInsteadOfFreeForm() {
        UserMessage injected = AnswerContentInjection.inject(
                List.of(), "Pytanie użytkownika: Co to jest?", 1500);
        String text = injected.singleText();
        assertTrue(text.contains("Nie znaleziono informacji w dokumentach"));
        assertTrue(text.contains("Co to jest?"));
    }

    @Test
    void extractorsPreferLastUserQuestionMarker() {
        String blob = """
                prefix
                Pytanie użytkownika: stare
                więcej
                Pytanie użytkownika: Co robi Olek?
                """;
        assertEquals("Co robi Olek?", AnswerContentInjection.extractUserQuestion(blob));
        assertTrue(AnswerContentInjection.extractGraphContext(blob).contains("prefix"));
        assertEquals("Co robi Olek?", AnswerContentInjection.extractRetrievalQuery(
                "Pytanie użytkownika: Co robi Olek? @plik.jpg"));
    }
}
