package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.graph.GraphQueryService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plans every request with the language model.  It deliberately has no
 * keyword, person-name, attribute or intent rules: the only finite set is
 * the technical retrieval capabilities exposed by the application.
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
                    You are a context-aware retrieval planner for a RAG application. Return JSON only.
                    Do not classify by a closed list of phrases, people, colours, clothes, actions or relations.
                    Preserve the user's meaning verbatim in condition. Choose only technical retrievalMode values:
                    DOCUMENT, GRAPH, HYBRID, VISUAL_VALIDATION.
                    Set visualCondition true only when answering requires validating image content.
                    Use ambiguous true when the available references do not identify one interpretation.
                    Known entities from this workspace: %s
                    Recent conversation and previously returned source paths: %s
                    JSON schema:
                    {"entities":["canonical name"],"fileScope":["previously supplied exact path"],
                    "retrievalQuery":"standalone semantic query resolved from the conversation",
                    "condition":"full semantic constraint", "visualCondition":false,
                    "ambiguous":false,"retrievalMode":"HYBRID",
                    "answerInstruction":"how to answer using evidence"}
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
            String condition = text(root, "condition", question);
            return new QueryPlan(question, entities, fileScope, text(root, "retrievalQuery", question), condition,
                    root.path("visualCondition").asBoolean(false), root.path("ambiguous").asBoolean(false),
                    mode, text(root, "answerInstruction", fallback.answerInstruction()));
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
