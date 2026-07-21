package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.EntityMatchMode;
import java.util.List;

/**
 * A model-produced description of how to answer one user request.  The
 * fields intentionally carry user language instead of application-specific
 * intent names or a vocabulary of visual attributes.
 */
public record QueryPlan(
        String question,
        List<String> entities,
        List<String> fileScope,
        String retrievalQuery,
        String condition,
        boolean visualCondition,
        boolean ambiguous,
        RetrievalMode retrievalMode,
        EntityMatchMode entityMatchMode,
        String answerInstruction
) {
    public enum RetrievalMode { DOCUMENT, GRAPH, HYBRID, VISUAL_VALIDATION }
    public enum VisualExecutionMode { FILE_QA, ENTITY_SUMMARY, VISUAL_SEARCH }

    public QueryPlan {
        question = question == null ? "" : question;
        entities = entities == null ? List.of() : List.copyOf(entities);
        fileScope = fileScope == null ? List.of() : List.copyOf(fileScope);
        retrievalQuery = retrievalQuery == null || retrievalQuery.isBlank() ? question : retrievalQuery;
        condition = condition == null ? "" : condition;
        retrievalMode = retrievalMode == null ? RetrievalMode.HYBRID : retrievalMode;
        entityMatchMode = entityMatchMode == null ? EntityMatchMode.ANY : entityMatchMode;
        answerInstruction = answerInstruction == null ? "" : answerInstruction;
    }

    /** Compatibility constructor for callers that have no explicit scope or rewritten query. */
    public QueryPlan(String question, List<String> entities, String ignoredFileScope, String condition,
                     boolean visualCondition, boolean ambiguous, RetrievalMode retrievalMode,
                     String answerInstruction) {
        this(question, entities, List.of(), question, condition, visualCondition, ambiguous,
                retrievalMode, EntityMatchMode.ANY, answerInstruction);
    }

    /** Compatibility constructor for callers predating explicit entity set semantics. */
    public QueryPlan(String question, List<String> entities, List<String> fileScope, String retrievalQuery,
                     String condition, boolean visualCondition, boolean ambiguous, RetrievalMode retrievalMode,
                     String answerInstruction) {
        this(question, entities, fileScope, retrievalQuery, condition, visualCondition, ambiguous,
                retrievalMode, EntityMatchMode.ANY, answerInstruction);
    }

    /** Safe, semantic-neutral fallback used only when the planner is unavailable. */
    public static QueryPlan fallback(String question, List<String> entities) {
        return new QueryPlan(question, List.of(), List.of(), question, question, false, false,
                RetrievalMode.HYBRID, EntityMatchMode.ANY,
                "Answer requested details briefly in Polish; do not list files.");
    }

    /** Applies a server-resolved technical file scope without asking the planner to reproduce paths. */
    public QueryPlan withFileScope(List<String> resolvedFileScope) {
        return new QueryPlan(question, entities, resolvedFileScope, retrievalQuery, condition,
                visualCondition, ambiguous, retrievalMode, entityMatchMode, answerInstruction);
    }

    /** Technical visual operation selected only from resolved plan fields, never question phrases. */
    public VisualExecutionMode visualExecutionMode() {
        if (!fileScope.isEmpty()) return VisualExecutionMode.FILE_QA;
        if (!entities.isEmpty()) return VisualExecutionMode.ENTITY_SUMMARY;
        return VisualExecutionMode.VISUAL_SEARCH;
    }
}
