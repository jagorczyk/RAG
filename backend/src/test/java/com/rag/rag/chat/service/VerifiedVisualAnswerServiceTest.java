package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GroundedVisualClaim;
import com.rag.rag.knowledge.query.VisualMatchDecision;
import com.rag.rag.knowledge.query.VisualQueryMatch;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerifiedVisualAnswerServiceTest {

    @Test
    void rendersOnlyImmutableClaimTextSelectedByKnownId() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("""
                {"answer":"Olek gra w piłkę.","usedEvidenceIds":["D1"]}
                """);
        VerifiedVisualAnswerService service = service(model);

        assertEquals("Igor pozuje z grupą.", service.answer("Co robi Igor?", List.of(match())));
    }

    @Test
    void rejectsFreeFormPolishProseWithoutEvidenceIds() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("Olek gra w piłkę.");
        VerifiedVisualAnswerService service = service(model);

        assertEquals(VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER,
                service.answer("Co robi Igor?", List.of(match())));
    }

    @Test
    void unknownEvidenceIdsFallBackWithoutPromotingAnswer() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("""
                {"answer":"Olek gra w piłkę.","usedEvidenceIds":["D2"]}
                """);
        VerifiedVisualAnswerService service = service(model);

        assertEquals(VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER,
                service.answer("Co robi Igor?", List.of(match())));
    }

    @Test
    void blankModelOutputUsesSafeFallback() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("   ");
        VerifiedVisualAnswerService service = service(model);

        String answer = service.answer("Co robi Igor?", List.of(match()));
        assertEquals(VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER, answer);
        assertFalse(answer.isBlank());
    }

    @Test
    void identityOnlyEvidenceCanRenderCertainRoster() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        VerifiedVisualAnswerService service = service(model);

        assertEquals("Na zdjęciu są Olek, Piotrek i Igor.",
                service.answer("Kto jest na zdjęciu?", List.of(matchWithoutClaims()),
                        List.of("Olek", "Piotrek", "Igor")));
    }

    @Test
    void emptyMatchesReturnsVisualDenial() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        VerifiedVisualAnswerService service = service(model);

        assertEquals(VerifiedVisualAnswerService.NO_VISUAL_EVIDENCE,
                service.answer("Co widać?", List.of()));
    }

    private static VerifiedVisualAnswerService service(ChatLanguageModel model) {
        return new VerifiedVisualAnswerService(new ClaimAnswerComposer(model));
    }

    private VisualQueryMatch match() {
        GroundedVisualClaim claim = new GroundedVisualClaim("D1", UUID.randomUUID(), "Igor",
                "pozuje", "z grupą", "Igor pozuje z grupą.", "dir://photo.jpg",
                BigDecimal.valueOf(0.95), "PIXEL_VERIFICATION", "face_1");
        return new VisualQueryMatch("dir://photo.jpg", BigDecimal.valueOf(0.95), List.of(),
                VisualMatchDecision.Decision.MATCH, List.of(), BigDecimal.valueOf(0.8),
                BigDecimal.ONE, List.of(claim));
    }

    private VisualQueryMatch matchWithoutClaims() {
        return new VisualQueryMatch("dir://photo.jpg", BigDecimal.valueOf(0.95), List.of(),
                VisualMatchDecision.Decision.MATCH, List.of(), BigDecimal.valueOf(0.8), BigDecimal.ONE);
    }
}
