package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.EntityMatchMode;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pure decisions for chat retrieval under AGENTS.md:
 * people → GRAPH evidence; non-person → HYBRID; visual → VISUAL_VALIDATION.
 * No question-language routing — only planner fields and evidence sets.
 */
public final class ChatRetrievalPolicy {

    private ChatRetrievalPolicy() {
    }

    /**
     * Load person/relation graph evidence for this turn.
     * GRAPH always; also when the planner named people, requires joint-file co-presence,
     * or scoped files (load confirmed humans on those paths for grounding).
     * Pure open HYBRID/DOCUMENT without entities or fileScope skips the graph.
     */
    public static boolean needsGraphEvidence(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        if (plan.retrievalMode() == QueryPlan.RetrievalMode.GRAPH) {
            return true;
        }
        if (requiresJointFileEvidence(plan)) {
            return true;
        }
        if (hasNamedEntities(plan)) {
            return plan.retrievalMode() == QueryPlan.RetrievalMode.HYBRID
                    || plan.retrievalMode() == QueryPlan.RetrievalMode.DOCUMENT;
        }
        if (plan.fileScope() != null && !plan.fileScope().isEmpty()) {
            return plan.retrievalMode() == QueryPlan.RetrievalMode.HYBRID;
        }
        return false;
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
     * GRAPH plan with no certain graph evidence (and not a joint-file denial) falls back
     * to hybrid document retrieval for the answer turn — still subject to source gating.
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

    /** True when the planner named at least one person (human) for source/evidence scope. */
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
     * Empty visual MATCH list: fall through to graph/hybrid on the same plan when there is still a
     * technical path (fileScope, named entities, or non-pure-visual mode). Pure open visual search
     * with no scope keeps the hard visual denial. No phrase routing — plan fields only.
     */
    public static boolean shouldFallbackFromEmptyVisual(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        if (plan.retrievalMode() == QueryPlan.RetrievalMode.HYBRID
                || plan.retrievalMode() == QueryPlan.RetrievalMode.DOCUMENT
                || plan.retrievalMode() == QueryPlan.RetrievalMode.GRAPH) {
            return true;
        }
        if (plan.fileScope() != null && !plan.fileScope().isEmpty()) {
            return true;
        }
        return plan.entities() != null && !plan.entities().isEmpty();
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
