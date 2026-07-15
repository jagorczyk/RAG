package com.rag.rag.chat.service;

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
        String answerInstruction
) {
    public enum RetrievalMode { DOCUMENT, GRAPH, HYBRID, VISUAL_VALIDATION }

    public QueryPlan {
        question = question == null ? "" : question;
        entities = entities == null ? List.of() : List.copyOf(entities);
        fileScope = fileScope == null ? List.of() : List.copyOf(fileScope);
        retrievalQuery = retrievalQuery == null || retrievalQuery.isBlank() ? question : retrievalQuery;
        condition = condition == null ? "" : condition;
        retrievalMode = retrievalMode == null ? RetrievalMode.HYBRID : retrievalMode;
        answerInstruction = answerInstruction == null ? "" : answerInstruction;
    }

    /** Compatibility constructor for callers that have no explicit scope or rewritten query. */
    public QueryPlan(String question, List<String> entities, String ignoredFileScope, String condition,
                     boolean visualCondition, boolean ambiguous, RetrievalMode retrievalMode,
                     String answerInstruction) {
        this(question, entities, List.of(), question, condition, visualCondition, ambiguous,
                retrievalMode, answerInstruction);
    }

    /** Safe, semantic-neutral fallback used only when the planner is unavailable. */
    public static QueryPlan fallback(String question, List<String> entities) {
        return new QueryPlan(question, entities, List.of(), question, question, false, false,
                RetrievalMode.HYBRID, "Answer from the available evidence and state uncertainty.");
    }
}
