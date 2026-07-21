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

    @Test
    void namedEntitiesAllowOnlyCertainGraphPathsAsHybridSources() {
        QueryPlan plan = new QueryPlan("q", List.of("Igor"), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        GraphEvidenceResult evidence = new GraphEvidenceResult("ctx", List.of("dir://igor.jpg"));

        assertTrue(ChatRetrievalPolicy.allowsHybridSourceForNamedEntities(plan, evidence, "dir://igor.jpg"));
        assertFalse(ChatRetrievalPolicy.allowsHybridSourceForNamedEntities(plan, evidence, "dir://other-person.jpg"));
        assertFalse(ChatRetrievalPolicy.allowsHybridSourceForNamedEntities(plan, evidence, null));
    }

    @Test
    void withoutNamedEntitiesHybridSourcesPassThrough() {
        QueryPlan plan = new QueryPlan("q", List.of(), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        GraphEvidenceResult evidence = new GraphEvidenceResult("", List.of());

        assertTrue(ChatRetrievalPolicy.allowsHybridSourceForNamedEntities(plan, evidence, "dir://any.jpg"));
        assertFalse(ChatRetrievalPolicy.hasNamedEntities(plan));
    }

    @Test
    void lacksGroundingWhenNoGraphAndNoFinalSources() {
        GraphEvidenceResult empty = new GraphEvidenceResult("", List.of());
        assertTrue(ChatRetrievalPolicy.lacksGrounding(empty, false));
        assertTrue(ChatRetrievalPolicy.lacksGrounding(null, false));
    }

    @Test
    void hasGroundingWhenGraphEvidenceOrFinalSourcesExist() {
        GraphEvidenceResult hit = new GraphEvidenceResult("ctx", List.of("dir://a.jpg"));
        assertFalse(ChatRetrievalPolicy.lacksGrounding(hit, false));
        assertFalse(ChatRetrievalPolicy.lacksGrounding(new GraphEvidenceResult("", List.of()), true));
    }

    @Test
    void retrievalScopePrefersPlannerFileScopeThenCertainGraphPaths() {
        QueryPlan withScope = new QueryPlan("q", List.of("Igor"), List.of("dir://folder/a.jpg"), "q", "",
                false, false, QueryPlan.RetrievalMode.HYBRID, "instr");
        GraphEvidenceResult evidence = new GraphEvidenceResult("ctx", List.of("dir://graph.jpg"));
        assertEquals(List.of("dir://folder/a.jpg"),
                ChatRetrievalPolicy.retrievalScope(withScope, evidence));

        QueryPlan namedOnly = new QueryPlan("q", List.of("Igor"), List.of(), "q", "",
                false, false, QueryPlan.RetrievalMode.GRAPH, "instr");
        assertEquals(List.of("dir://graph.jpg"),
                ChatRetrievalPolicy.retrievalScope(namedOnly, evidence));

        QueryPlan open = new QueryPlan("q", List.of(), List.of(), "q", "",
                false, false, QueryPlan.RetrievalMode.HYBRID, "instr");
        assertTrue(ChatRetrievalPolicy.retrievalScope(open, evidence).isEmpty());
    }

    @Test
    void emptyVisualFallbackWhenFileScopeEntitiesOrNonVisualMode() {
        QueryPlan pureVisual = new QueryPlan("q", List.of(), List.of(), "q", "c", true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, "instr");
        assertFalse(ChatRetrievalPolicy.shouldFallbackFromEmptyVisual(pureVisual));

        QueryPlan withScope = new QueryPlan("q", List.of(), List.of("dir://a.jpg"), "q", "c", true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, "instr");
        assertTrue(ChatRetrievalPolicy.shouldFallbackFromEmptyVisual(withScope));

        QueryPlan withEntity = new QueryPlan("q", List.of("Igor"), List.of(), "q", "c", true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, "instr");
        assertTrue(ChatRetrievalPolicy.shouldFallbackFromEmptyVisual(withEntity));

        QueryPlan hybridVisual = new QueryPlan("q", List.of(), List.of(), "q", "c", true, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        assertTrue(ChatRetrievalPolicy.shouldFallbackFromEmptyVisual(hybridVisual));
    }

    @Test
    void needsGraphEvidenceForGraphModeOrNamedPeopleOnHybrid() {
        QueryPlan graph = new QueryPlan("q", List.of(), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.GRAPH, "instr");
        assertTrue(ChatRetrievalPolicy.needsGraphEvidence(graph));

        QueryPlan hybridPeople = new QueryPlan("q", List.of("Igor"), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        assertTrue(ChatRetrievalPolicy.needsGraphEvidence(hybridPeople));

        QueryPlan hybridDocs = new QueryPlan("q", List.of(), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        assertFalse(ChatRetrievalPolicy.needsGraphEvidence(hybridDocs));

        QueryPlan hybridScoped = new QueryPlan("q", List.of(), List.of("dir://a.jpg"), "q", "", false, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        assertTrue(ChatRetrievalPolicy.needsGraphEvidence(hybridScoped));

        QueryPlan documentScoped = new QueryPlan("q", List.of(), List.of("dir://a.jpg"), "q", "", false, false,
                QueryPlan.RetrievalMode.DOCUMENT, "instr");
        assertTrue(ChatRetrievalPolicy.needsGraphEvidence(documentScoped));

        QueryPlan documentOnly = new QueryPlan("q", List.of(), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.DOCUMENT, "instr");
        assertFalse(ChatRetrievalPolicy.needsGraphEvidence(documentOnly));
    }

    @Test
    void preferClaimAnswerDisabledOnFreeformBranch() {
        // Free-form branch: full graph → LLM formulates; claim short-circuit off.
        GraphEvidenceResult withClaims = new GraphEvidenceResult(
                "ctx", List.of("dir://a.jpg"),
                List.of(new com.rag.rag.knowledge.graph.GroundedVisualClaim(
                        "F-1", null, "Igor", "stoi", "", "Igor stoi.", "dir://a.jpg",
                        java.math.BigDecimal.ONE, "VISION", "face_1")));

        QueryPlan graph = new QueryPlan("q", List.of("Igor"), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.GRAPH, "instr");
        assertFalse(ChatRetrievalPolicy.preferClaimAnswer(graph, withClaims));

        QueryPlan hybridScoped = new QueryPlan("q", List.of(), List.of("dir://a.jpg"), "q", "", false, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        assertFalse(ChatRetrievalPolicy.preferClaimAnswer(hybridScoped, withClaims));

        QueryPlan hybridOpen = new QueryPlan("q", List.of(), List.of(), "q", "", false, false,
                QueryPlan.RetrievalMode.HYBRID, "instr");
        assertFalse(ChatRetrievalPolicy.preferClaimAnswer(hybridOpen, withClaims));
        assertFalse(ChatRetrievalPolicy.preferClaimAnswer(null, null));
    }
}
