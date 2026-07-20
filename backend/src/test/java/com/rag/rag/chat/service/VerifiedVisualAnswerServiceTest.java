package com.rag.rag.chat.service;

import com.rag.rag.knowledge.query.VisualMatchDecision;
import com.rag.rag.knowledge.query.VisualQueryMatch;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerifiedVisualAnswerServiceTest {

    @Test
    void returnsOnlyAnswerThatReferencesExistingEvidenceIds() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("""
                {"answer":"Igor pozuje z grupą.","usedEvidenceIds":["D1"]}
                """);
        VerifiedVisualAnswerService service = new VerifiedVisualAnswerService(model);

        assertEquals("Igor pozuje z grupą.", service.answer("Co robi Igor?", List.of(match())));
    }

    @Test
    void acceptsFreeFormPolishProseWhenJsonSchemaIsSkipped() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        VerifiedVisualAnswerService service = new VerifiedVisualAnswerService(model);
        when(model.generate(anyString())).thenReturn("Olek gra w piłkę.");

        assertEquals("Olek gra w piłkę.", service.answer("Kto jest na zdjęciu?", List.of(match())));
    }

    @Test
    void unknownEvidenceIdsFallBackToShortPolishMatchMessage() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        VerifiedVisualAnswerService service = new VerifiedVisualAnswerService(model);
        when(model.generate(anyString())).thenReturn("""
                {"answer":"Olek gra w piłkę.","usedEvidenceIds":["D2"]}
                """);

        // Invalid D2 must not promote the JSON answer; MATCH still warrants a short PL reply.
        assertEquals(VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER,
                service.answer("Kto jest na zdjęciu?", List.of(match())));
    }

    @Test
    void blankModelOutputWithMatchesUsesFallbackNotEmpty() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("   ");
        VerifiedVisualAnswerService service = new VerifiedVisualAnswerService(model);

        String answer = service.answer("Co widać?", List.of(match()));
        assertEquals(VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER, answer);
        assertFalse(answer.isBlank());
    }

    @Test
    void blankModelWithCertainNamesUsesParticipantRoster() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("   ");
        VerifiedVisualAnswerService service = new VerifiedVisualAnswerService(model);

        String answer = service.answer("Kto jest na zdjęciu?", List.of(match()),
                List.of("Olek", "Piotrek", "Igor"));
        assertEquals("Na zdjęciu są Olek, Piotrek i Igor.", answer);
    }

    @Test
    void injectsCertainNamesIntoEvidencePrompt() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            assertTrue(prompt.contains("potwierdzone tożsamości uczestników"));
            assertTrue(prompt.contains("Olek"));
            assertTrue(prompt.contains("Igor"));
            return """
                    {"answer":"Na zdjęciu są Olek i Igor.","usedEvidenceIds":["D1","D2"]}
                    """;
        });
        VerifiedVisualAnswerService service = new VerifiedVisualAnswerService(model);

        assertEquals("Na zdjęciu są Olek i Igor.",
                service.answer("jak mają na imię?", List.of(match()), List.of("Olek", "Igor")));
    }

    @Test
    void emptyMatchesReturnsVisualDenial() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        VerifiedVisualAnswerService service = new VerifiedVisualAnswerService(model);

        assertEquals("Nie znaleziono potwierdzonych dowodów wizualnych.",
                service.answer("Co widać?", List.of()));
    }

    private VisualQueryMatch match() {
        return new VisualQueryMatch("dir://photo.jpg", BigDecimal.valueOf(0.95),
                List.of("Igor pozuje z grupą"), VisualMatchDecision.Decision.MATCH,
                List.of(), BigDecimal.valueOf(0.8), BigDecimal.ONE);
    }
}
