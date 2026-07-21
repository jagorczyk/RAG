package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.EntityMatchMode;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plans every request with the language model (AGENTS.md variant A).
 * No keyword / phrase / name lists in application code — only technical mode enums.
 */
@Slf4j
@Component
public class QueryPlanner {
    private final GraphQueryService graphQueryService;
    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueryPlanner(GraphQueryService graphQueryService,
                        @Qualifier("chatLanguageModel") ChatLanguageModel chatModel) {
        this.graphQueryService = graphQueryService;
        this.chatModel = chatModel;
    }

    public QueryPlan plan(String question, String conversationContext) {
        String safeQuestion = question == null ? "" : question.trim();
        List<String> knownEntities = graphQueryService.availableEntityNames();
        QueryPlan fallback = QueryPlan.fallback(safeQuestion, knownEntities);
        if (chatModel == null || safeQuestion.isBlank()) {
            return fallback;
        }

        try {
            String response = chatModel.generate("""
                    You are a context-aware retrieval planner for a photo/document RAG library. Return JSON only.
                    Do not use a closed list of phrases, colours, clothes, actions or relation words in the app sense;
                    choose a technical retrievalMode from meaning + known people + conversation.
                    Preserve the user's meaning verbatim in condition.

                    Retrieval modes (pick exactly one):
                    - GRAPH: the question is about people (humans) — who they are, co-presence, relations,
                      what they wear/do/look like when those details live in the knowledge graph or embeddings
                      for those people. Fill entities with canonical human names when identifiable.
                      Animals and objects alone must NOT select GRAPH.
                    - HYBRID: the question is NOT about people (documents, scenes without identity, objects,
                      general file facts). Default for non-person questions. Prefer HYBRID over DOCUMENT.
                    - VISUAL_VALIDATION: answering requires validating appearance, clothing, pose, action,
                      scene layout against the image when graph/embeddings are insufficient. Set
                      visualCondition=true with this mode. Prefer GRAPH/HYBRID first when the library
                      already stores visual_cues, clothing, hair or actions for the people/files.
                    - DOCUMENT: rare; only when pure document retrieval is clearly enough. Prefer HYBRID.

                    When the recent conversation lists SOURCES: paths, copy those exact paths into fileScope
                    for follow-up questions about the same photo unless the user clearly switches topic or
                    names a different @file. Empty entities + non-empty fileScope is valid for GRAPH:
                    the graph loads confirmed human participants for those files.
                    Use ambiguous true when the available references do not identify one interpretation.
                    Known people (humans) from this workspace: %s
                    Recent conversation and previously returned source paths: %s
                    JSON schema:
                    {"entities":["canonical human name"],"fileScope":["previously supplied exact path"],
                    "retrievalQuery":"standalone semantic query resolved from the conversation",
                    "condition":"full semantic constraint", "visualCondition":false,
                    "ambiguous":false,"retrievalMode":"HYBRID","entityMatchMode":"ANY",
                    "answerInstruction":"answer all requested appearance, clothing, action and relation details in Polish from evidence; never list files"}
                    Use entityMatchMode ALL_SAME_FILE when the answer depends on co-presence of all selected
                    people in one file; otherwise use ANY. This is a technical set operation.
                    When ALL_SAME_FILE is chosen, use retrievalMode GRAPH so joint evidence is checked.
                    User request: %s
                    """.formatted(knownEntities, conversationContext == null ? "" : conversationContext, safeQuestion));
            return fromJson(safeQuestion, knownEntities, response, fallback);
        } catch (Exception e) {
            log.warn("Dynamic query planning failed; using hybrid fallback: {}", e.getMessage());
            return fallback;
        }
    }

    public QueryPlan plan(String question) {
        return plan(question, "");
    }

    private QueryPlan fromJson(String question, List<String> knownEntities, String response, QueryPlan fallback) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            List<String> entities = graphQueryService.validateEntityNames(readStrings(root.path("entities")));
            List<String> fileScope = graphQueryService.validateFilePaths(readStrings(root.path("fileScope")));
            QueryPlan.RetrievalMode mode = parseMode(root.path("retrievalMode").asText(""), fallback.retrievalMode());
            EntityMatchMode entityMatchMode = parseEntityMatchMode(root.path("entityMatchMode").asText(""));
            String condition = text(root, "condition", question);
            return new QueryPlan(question, entities, fileScope, text(root, "retrievalQuery", question), condition,
                    root.path("visualCondition").asBoolean(false), root.path("ambiguous").asBoolean(false),
                    mode, entityMatchMode, text(root, "answerInstruction", fallback.answerInstruction()));
        } catch (Exception e) {
            log.warn("Dynamic query plan was not valid JSON; using hybrid fallback: {}", e.getMessage());
            return fallback;
        }
    }

    private List<String> readStrings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> resolved = new ArrayList<>();
        node.forEach(value -> {
            String requested = value.asText("").trim();
            if (!requested.isBlank()) resolved.add(requested);
        });
        return resolved.stream().distinct().toList();
    }

    private QueryPlan.RetrievalMode parseMode(String value, QueryPlan.RetrievalMode fallback) {
        try {
            return QueryPlan.RetrievalMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private EntityMatchMode parseEntityMatchMode(String value) {
        try {
            return EntityMatchMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return EntityMatchMode.ANY;
        }
    }

    private String text(JsonNode root, String name, String fallback) {
        String value = root.path(name).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return start >= 0 && end > start ? response.substring(start, end + 1) : response;
    }
}
