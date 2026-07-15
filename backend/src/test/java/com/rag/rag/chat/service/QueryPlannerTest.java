package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GraphQueryService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryPlannerTest {
    @Mock private GraphQueryService graphQueryService;
    @Mock private ChatLanguageModel chatModel;

    @Test
    void acceptsAnUnseenVisualAttributeWithoutApplicationVocabulary() {
        String question = "Czy Michał ma włosy koloru blond?";
        when(graphQueryService.availableEntityNames()).thenReturn(List.of("Michał"));
        when(graphQueryService.validateEntityNames(List.of("Michał"))).thenReturn(List.of("Michał"));
        when(graphQueryService.validateFilePaths(List.of())).thenReturn(List.of());
        when(chatModel.generate(anyString())).thenReturn("""
                {"entities":["Michał"],"condition":"włosy Michała mają kolor blond",
                "visualCondition":true,"ambiguous":false,"retrievalMode":"VISUAL_VALIDATION",
                "answerInstruction":"Odpowiedz wyłącznie z dowodów obrazu."}
                """);

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel).plan(question);

        assertEquals(List.of("Michał"), plan.entities());
        assertTrue(plan.visualCondition());
        assertEquals(QueryPlan.RetrievalMode.VISUAL_VALIDATION, plan.retrievalMode());
        assertTrue(plan.condition().contains("blond"));
    }

    @Test
    void fallsBackToGenericHybridRetrievalWhenPlannerOutputIsInvalid() {
        when(graphQueryService.availableEntityNames()).thenReturn(List.of());
        when(chatModel.generate(anyString())).thenReturn("not-json");

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel).plan("Jakie jest saldo na fakturze?");

        assertEquals(QueryPlan.RetrievalMode.HYBRID, plan.retrievalMode());
        assertEquals("Jakie jest saldo na fakturze?", plan.condition());
    }
}
