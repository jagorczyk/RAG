package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.EntityMatchMode;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRetrievalPolicyTest {

    @Test
    void graphEmptyTriggersHybridFallback() {
        QueryPlan plan = new QueryPlan("q", List.of("Igor"), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.GRAPH, "instr");
        GraphEvidenceResult empty = new GraphEvidenceResult("", List.of());

        assertTrue(ChatRetrievalPolicy.shouldFallbackFromGraph(plan, empty));
        assertEquals(QueryPlan.RetrievalMode.HYBRID,
                ChatRetrievalPolicy.effectiveRetrievalMode(plan, empty));
        assertFalse(ChatRetrievalPolicy.mustDenyJointFile(plan, empty));
    }

    @Test
    void graphWithEvidenceDoesNotFallback() {
        QueryPlan plan = new QueryPlan("q", List.of("Igor"), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.GRAPH, "instr");
        GraphEvidenceResult hit = new GraphEvidenceResult("ctx", List.of("dir://a.jpg"));

        assertFalse(ChatRetrievalPolicy.shouldFallbackFromGraph(plan, hit));
        assertEquals(QueryPlan.RetrievalMode.GRAPH,
                ChatRetrievalPolicy.effectiveRetrievalMode(plan, hit));
    }

    @Test
    void allSameFileEmptyIsHardDenialNotFallback() {
        QueryPlan plan = new QueryPlan("q", List.of("Igor", "Anna"), List.of(), "q", "q", false, false,
                QueryPlan.RetrievalMode.GRAPH, EntityMatchMode.ALL_SAME_FILE, "instr");
        GraphEvidenceResult empty = new GraphEvidenceResult("", List.of());

        assertTrue(ChatRetrievalPolicy.mustDenyJointFile(plan, empty));
        assertFalse(ChatRetrievalPolicy.shouldFallbackFromGraph(plan, empty));
    }

    @Test
    void hybridModeDoesNotUseGraphFallbackFlag() {
        QueryPlan plan = new QueryPlan("q", List.of("Igor"), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        assertFalse(ChatRetrievalPolicy.shouldFallbackFromGraph(plan, new GraphEvidenceResult("", List.of())));
    }
}
