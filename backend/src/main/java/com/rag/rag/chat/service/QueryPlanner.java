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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Plans every request with the language model (AGENTS.md variant A).
 * No keyword / phrase / name lists in application code — only technical mode enums.
 */
@Slf4j
@Component
public class QueryPlanner {
    private static final int MAX_KNOWN_PEOPLE_IN_PROMPT = 80;

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
        QueryPlan fallback = enrichedFallback(safeQuestion, knownEntities);
        if (chatModel == null || safeQuestion.isBlank()) {
            return fallback;
        }

        try {
            String knownForPrompt = knownEntities.size() <= MAX_KNOWN_PEOPLE_IN_PROMPT
                    ? knownEntities.toString()
                    : knownEntities.subList(0, MAX_KNOWN_PEOPLE_IN_PROMPT) + " … (+"
                    + (knownEntities.size() - MAX_KNOWN_PEOPLE_IN_PROMPT) + ")";
            String response = chatModel.generate("""
                    Jesteś planerem retrieval dla biblioteki zdjęć i dokumentów (GraphRAG). Zwróć wyłącznie JSON.
                    Nie używaj zamkniętej listy fraz, kolorów, ubrań ani czynności w kodzie aplikacji —
                    wybierz retrievalMode z znaczenia pytania, znanych osób i historii rozmowy.
                    Zachowaj sens użytkownika w polu condition (po polsku).

                    Tryby (dokładnie jeden):
                    - GRAPH: pytanie o ludzi (kim są, współobecność, relacje, ubiór/czynności z grafu).
                      entities = kanoniczne imiona z listy znanych osób (mianownik, np. Olek nie Olka).
                      Zwierzęta i same obiekty NIE wybierają GRAPH.
                    - HYBRID: pytanie NIE o tożsamość ludzi (dokumenty, scena, obiekty, tło). Domyślne dla non-person.
                    - VISUAL_VALIDATION: trzeba zweryfikować wygląd/pozycję na obrazie, gdy graf/embeddingi nie wystarczą.
                      Ustaw visualCondition=true. Preferuj GRAPH/HYBRID, gdy visual_cues/claimy już są w grafie.
                    - DOCUMENT: rzadko; preferuj HYBRID.

                    Gdy w historii jest SOURCES: ze ścieżkami, skopiuj te exact paths do fileScope przy follow-upach
                    o tym samym zdjęciu (chyba że użytkownik zmienia temat lub podaje inny @plik).
                    Puste entities + niepusty fileScope jest OK dla GRAPH (ładujemy uczestników pliku).
                    ambiguous=true gdy odniesienia są niejednoznaczne.
                    Znane osoby (ludzie) w workspace: %s
                    Ostatnia rozmowa i ścieżki źródeł: %s
                    Schema JSON:
                    {"entities":["kanoniczne imię"],"fileScope":["dokładna ścieżka z historii"],
                    "retrievalQuery":"samodzielne zapytanie semantyczne po polsku",
                    "condition":"pełne ograniczenie semantyczne po polsku","visualCondition":false,
                    "ambiguous":false,"retrievalMode":"HYBRID","entityMatchMode":"ANY",
                    "answerInstruction":"odpowiedz po polsku z dowodów: ubiór, czynności, relacje, scena; bez list plików"}
                    entityMatchMode=ALL_SAME_FILE gdy odpowiedź wymaga współobecności wszystkich wybranych osób na jednym pliku;
                    wtedy retrievalMode=GRAPH.
                    Pytanie użytkownika: %s
                    """.formatted(knownForPrompt,
                    conversationContext == null ? "" : conversationContext, safeQuestion));
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
            // Planner missed declined Polish names — resolve from question tokens.
            if (entities.isEmpty()) {
                entities = graphQueryService.resolveEntityNamesFromText(question);
            } else {
                // Merge any extra resolved names from the raw question (Olka + Piotrek).
                LinkedHashSet<String> merged = new LinkedHashSet<>(entities);
                merged.addAll(graphQueryService.resolveEntityNamesFromText(question));
                entities = List.copyOf(merged);
            }
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

    private QueryPlan enrichedFallback(String question, List<String> knownEntities) {
        List<String> resolved = graphQueryService.resolveEntityNamesFromText(question);
        if (resolved.isEmpty()) {
            return QueryPlan.fallback(question, knownEntities);
        }
        return new QueryPlan(
                question,
                resolved,
                List.of(),
                question,
                question,
                false,
                false,
                QueryPlan.RetrievalMode.GRAPH,
                EntityMatchMode.ANY,
                "Odpowiedz po polsku z dowodów grafu: ubiór, czynności, relacje, scena; bez list plików.");
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
