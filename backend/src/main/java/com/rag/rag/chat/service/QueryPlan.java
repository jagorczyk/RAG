package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.EntityMatchMode;
import java.util.List;
import java.util.UUID;

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
        String answerInstruction,
        ScopeKind scopeKind,
        List<UUID> folderScope,
        boolean collectionOverview
) {
    public enum RetrievalMode { DOCUMENT, GRAPH, HYBRID, VISUAL_VALIDATION }
    public enum VisualExecutionMode { FILE_QA, ENTITY_SUMMARY, VISUAL_SEARCH }
    public enum ScopeKind { UNRESTRICTED, FOLDER, FILE }

    public QueryPlan {
        question = question == null ? "" : question;
        entities = entities == null ? List.of() : List.copyOf(entities);
        fileScope = fileScope == null ? List.of() : List.copyOf(fileScope);
        retrievalQuery = retrievalQuery == null || retrievalQuery.isBlank() ? question : retrievalQuery;
        condition = condition == null ? "" : condition;
        retrievalMode = retrievalMode == null ? RetrievalMode.HYBRID : retrievalMode;
        entityMatchMode = entityMatchMode == null ? EntityMatchMode.ANY : entityMatchMode;
        answerInstruction = answerInstruction == null ? "" : answerInstruction;
        folderScope = folderScope == null ? List.of() : List.copyOf(folderScope);
        scopeKind = scopeKind == null
                ? (!folderScope.isEmpty() ? ScopeKind.FOLDER
                : !fileScope.isEmpty() ? ScopeKind.FILE : ScopeKind.UNRESTRICTED)
                : scopeKind;
    }

    /** Compatibility constructor for callers predating collection/folder scope. */
    public QueryPlan(String question, List<String> entities, List<String> fileScope, String retrievalQuery,
                     String condition, boolean visualCondition, boolean ambiguous, RetrievalMode retrievalMode,
                     EntityMatchMode entityMatchMode, String answerInstruction) {
        this(question, entities, fileScope, retrievalQuery, condition, visualCondition, ambiguous,
                retrievalMode, entityMatchMode, answerInstruction,
                fileScope == null || fileScope.isEmpty() ? ScopeKind.UNRESTRICTED : ScopeKind.FILE,
                List.of(), false);
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
        ScopeKind resolvedKind = resolvedFileScope == null || resolvedFileScope.isEmpty()
                ? scopeKind : ScopeKind.FILE;
        return new QueryPlan(question, entities, resolvedFileScope, retrievalQuery, condition,
                visualCondition, ambiguous, retrievalMode, entityMatchMode, answerInstruction,
                resolvedKind, resolvedKind == ScopeKind.FOLDER ? folderScope : List.of(),
                collectionOverview);
    }

    /** Applies a server-resolved folder and its owned file paths without changing planner intent. */
    public QueryPlan withFolderScope(List<UUID> resolvedFolderScope, List<String> resolvedFileScope) {
        return new QueryPlan(question, entities, resolvedFileScope, retrievalQuery, condition,
                visualCondition, ambiguous, retrievalMode, entityMatchMode, answerInstruction,
                ScopeKind.FOLDER, resolvedFolderScope, collectionOverview);
    }

    /** Technical visual operation selected only from resolved plan fields, never question phrases. */
    public VisualExecutionMode visualExecutionMode() {
        if (!fileScope.isEmpty()) return VisualExecutionMode.FILE_QA;
        if (!entities.isEmpty()) return VisualExecutionMode.ENTITY_SUMMARY;
        return VisualExecutionMode.VISUAL_SEARCH;
    }
}
