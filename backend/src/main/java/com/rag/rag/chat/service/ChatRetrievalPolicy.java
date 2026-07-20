package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.EntityMatchMode;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;

/**
 * Pure decisions for chat retrieval: joint-file denial and graph-miss → hybrid fallback.
 * No question-language routing (AGENTS.md).
 */
public final class ChatRetrievalPolicy {

    private ChatRetrievalPolicy() {
    }

    public static boolean requiresJointFileEvidence(QueryPlan plan) {
        return plan != null
                && plan.entityMatchMode() == EntityMatchMode.ALL_SAME_FILE
                && plan.entities() != null
                && !plan.entities().isEmpty();
    }

    public static boolean mustDenyJointFile(QueryPlan plan, GraphEvidenceResult evidence) {
        return requiresJointFileEvidence(plan)
                && (evidence == null || !evidence.hasEvidence());
    }

    /**
     * GRAPH plan with no certain graph evidence (and not a joint-file denial) must still
     * run document/hybrid retrieval for the answer turn.
     */
    public static boolean shouldFallbackFromGraph(QueryPlan plan, GraphEvidenceResult evidence) {
        if (plan == null || plan.retrievalMode() != QueryPlan.RetrievalMode.GRAPH) {
            return false;
        }
        if (mustDenyJointFile(plan, evidence)) {
            return false;
        }
        return evidence == null || !evidence.hasEvidence();
    }

    public static QueryPlan.RetrievalMode effectiveRetrievalMode(QueryPlan plan, GraphEvidenceResult evidence) {
        if (shouldFallbackFromGraph(plan, evidence)) {
            return QueryPlan.RetrievalMode.HYBRID;
        }
        return plan == null || plan.retrievalMode() == null
                ? QueryPlan.RetrievalMode.HYBRID
                : plan.retrievalMode();
    }
}
