package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GroundedVisualClaim;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaimAnswerComposerTest {

    @Test
    void selectsClaimIdsAndJoinsImmutableStatements() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("{\"usedEvidenceIds\":[\"F-1\",\"F-2\"]}");
        ClaimAnswerComposer composer = new ClaimAnswerComposer(model);

        ClaimAnswerComposer.ClaimAnswerResult result = composer.answerFromClaims(
                "Co robi Olek?",
                List.of(
                        claim("F-1", "Olek", "trzyma nóż", "Olek trzyma nóż.", "dir://a.jpg"),
                        claim("F-2", "Olek", "HAS_APPEARANCE", "Olek ma czarną kurtkę.", "dir://a.jpg"),
                        claim("F-3", "Bartek", "siedzi", "Bartek siedzi.", "dir://a.jpg")));

        assertTrue(result.hasGroundedProse());
        assertTrue(result.answer().contains("Olek trzyma nóż"));
        assertTrue(result.answer().contains("czarną kurtkę") || result.answer().contains("kurtk"));
        assertTrue(result.usedClaimIds().contains("F-1"));
        assertEquals(List.of("dir://a.jpg"), result.usedFilePaths());
    }

    @Test
    void softSelectsEntityClaimsWhenModelReturnsNoIds() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("{\"usedEvidenceIds\":[]}");
        ClaimAnswerComposer composer = new ClaimAnswerComposer(model);

        ClaimAnswerComposer.ClaimAnswerResult result = composer.answerFromClaims(
                "W czym jest Olek?",
                List.of(
                        claim("F-1", "Olek", "HAS_APPEARANCE", "Olek ma czarną kurtkę.", "dir://a.jpg"),
                        claim("F-2", "Bartek", "HAS_APPEARANCE", "Bartek ma blond włosy.", "dir://a.jpg")),
                List.of("Olek"));

        assertTrue(result.hasGroundedProse());
        assertTrue(result.answer().contains("Olek"));
        assertFalse(result.answer().contains("Bartek"));
    }

    @Test
    void emptyWhenNoClaims() {
        ClaimAnswerComposer composer = new ClaimAnswerComposer(mock(ChatLanguageModel.class));
        ClaimAnswerComposer.ClaimAnswerResult result = composer.answerFromClaims("x", List.of());
        assertFalse(result.hasGroundedProse());
        assertEquals(ClaimAnswerComposer.EMPTY_FALLBACK, result.answer());
    }

    private static GroundedVisualClaim claim(String id, String entity, String pred, String statement, String path) {
        return new GroundedVisualClaim(id, UUID.randomUUID(), entity, pred, "", statement, path,
                BigDecimal.valueOf(0.9), "VISION_STRUCTURED", "face_1");
    }
}
