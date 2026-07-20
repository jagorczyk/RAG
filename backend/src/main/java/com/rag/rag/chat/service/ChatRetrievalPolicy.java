package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.EntityMatchMode;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pure decisions for chat retrieval: joint-file denial, graph-miss → hybrid fallback,
 * certain-source gating, and no-grounding denial. No question-language routing (AGENTS.md).
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

    /** True when the planner named at least one entity (person/animal scope for sources). */
    public static boolean hasNamedEntities(QueryPlan plan) {
        return plan != null && plan.entities() != null && !plan.entities().isEmpty();
    }

    /**
     * When entities are named, hybrid/document sources are allowed only if the path already has
     * certain graph evidence for those people (prevents sources tied solely to other people).
     * With no named entities, hybrid sources pass through (still subject to file scope).
     */
    public static boolean allowsHybridSourceForNamedEntities(
            QueryPlan plan, GraphEvidenceResult evidence, String path) {
        if (!hasNamedEntities(plan)) {
            return true;
        }
        if (path == null || path.isBlank() || evidence == null) {
            return false;
        }
        return evidence.certainPaths() != null && evidence.certainPaths().contains(path);
    }

    /**
     * No certain graph evidence and no final sources after filtering → refuse to invent an answer.
     * Graph context alone counts as grounding (LLM answers from verified graph block).
     */
    public static boolean lacksGrounding(GraphEvidenceResult evidence, boolean hasFinalSources) {
        if (evidence != null && evidence.hasEvidence()) {
            return false;
        }
        return !hasFinalSources;
    }

    /**
     * Path scope for hybrid retrieval this turn:
     * <ul>
     *   <li>planner {@code fileScope} when present</li>
     *   <li>else certain graph paths when entities are named (keep RAG on proven files)</li>
     *   <li>else unrestricted (empty)</li>
     * </ul>
     * Technical set ops only — no phrase routing.
     */
    public static List<String> retrievalScope(QueryPlan plan, GraphEvidenceResult evidence) {
        LinkedHashSet<String> scope = new LinkedHashSet<>();
        if (plan != null && plan.fileScope() != null) {
            for (String path : plan.fileScope()) {
                if (path != null && !path.isBlank()) {
                    scope.add(path.trim());
                }
            }
        }
        if (!scope.isEmpty()) {
            return List.copyOf(scope);
        }
        if (hasNamedEntities(plan)
                && evidence != null
                && evidence.certainPaths() != null
                && !evidence.certainPaths().isEmpty()) {
            for (String path : evidence.certainPaths()) {
                if (path != null && !path.isBlank()) {
                    scope.add(path.trim());
                }
            }
        }
        return scope.isEmpty() ? List.of() : List.copyOf(new ArrayList<>(scope));
    }
}
