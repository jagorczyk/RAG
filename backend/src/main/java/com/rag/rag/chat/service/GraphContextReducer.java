package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.graph.GraphEvidenceItem;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import com.rag.rag.knowledge.graph.GraphPhotoEvidence;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Token-aware GraphRAG context compression. Every evidence item is presented to
 * the deterministic control model. The answer model only receives original,
 * server-validated evidence text, never free-form mapper summaries.
 */
@Slf4j
@Service
public class GraphContextReducer {

    private final ChatLanguageModel controlModel;
    private final Tokenizer tokenizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.answer.context-window-tokens:32768}")
    private int contextWindowTokens = 32768;

    @Value("${llm.answer.max-tokens:768}")
    private int answerMaxTokens = 768;

    @Value("${rag.graph.reducer.max-input-tokens:8192}")
    private int reducerMaxInputTokens = 8192;

    public GraphContextReducer(@Qualifier("structuredControlLanguageModel") ChatLanguageModel controlModel,
                               Tokenizer tokenizer) {
        this.controlModel = controlModel;
        this.tokenizer = tokenizer;
    }

    public Selection select(GraphEvidenceResult evidence, String question, String fixedPrompt) {
        if (evidence == null || evidence.context().isBlank()) {
            return new Selection("", List.of(), false, 0, 0, 0, 0);
        }
        int rawTokens = tokens(evidence.context());
        int fixedTokens = tokens(fixedPrompt) + tokens(question);
        int available = Math.max(512,
                (int) Math.floor((Math.max(1024, contextWindowTokens) - answerMaxTokens - fixedTokens) * 0.80));
        if (rawTokens <= available || evidence.photos().isEmpty()) {
            return new Selection(evidence.context(), evidence.certainPaths(), false,
                    rawTokens, rawTokens, evidence.photos().stream().mapToInt(photo -> photo.items().size()).sum(),
                    evidence.photos().stream().mapToInt(photo -> photo.items().size()).sum());
        }

        List<GraphEvidenceItem> allItems = evidence.photos().stream()
                .flatMap(photo -> photo.items().stream())
                .filter(item -> !item.id().isBlank() && !item.statementPl().isBlank())
                .toList();
        if (allItems.isEmpty()) {
            return new Selection("", List.of(), true, rawTokens, 0, 0, 0);
        }

        Map<String, GraphEvidenceItem> byId = new LinkedHashMap<>();
        allItems.forEach(item -> byId.putIfAbsent(item.id(), item));
        List<GraphEvidenceItem> pinnedItems = allItems.stream()
                .filter(item -> item.kind() == GraphEvidenceItem.Kind.INVENTORY)
                .toList();
        Set<String> pinnedIds = pinnedItems.stream().map(GraphEvidenceItem::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<GraphEvidenceItem> selectableItems = allItems.stream()
                .filter(item -> !pinnedIds.contains(item.id()))
                .toList();
        List<List<GraphEvidenceItem>> batches = batches(selectableItems,
                Math.max(512, (int) Math.floor(reducerMaxInputTokens * 0.75)));
        double targetRatio = Math.min(1.0, Math.max(0.02, (double) available / Math.max(1, rawTokens)));
        LinkedHashSet<String> selectedIds = new LinkedHashSet<>(pinnedIds);

        for (List<GraphEvidenceItem> batch : batches) {
            int cap = Math.max(1, (int) Math.ceil(batch.size() * targetRatio));
            List<String> ids = selectIds(question, batch, cap);
            for (String id : ids) {
                if (byId.containsKey(id) && selectedIds.size() < allItems.size()) {
                    selectedIds.add(id);
                }
            }
        }

        List<GraphEvidenceItem> selected = selectedIds.stream().map(byId::get)
                .filter(java.util.Objects::nonNull).toList();
        if (selected.isEmpty()) {
            log.info("Graph reducer selected no evidence: rawTokens={}, items={}, batches={}",
                    rawTokens, allItems.size(), batches.size());
            return new Selection("", List.of(), true, rawTokens, 0, allItems.size(), 0);
        }

        String rendered = renderSelected(evidence.photos(), selectedIds);
        if (tokens(rendered) > available) {
            List<GraphEvidenceItem> fitting = new ArrayList<>(pinnedItems);
            for (GraphEvidenceItem item : selected) {
                if (pinnedIds.contains(item.id())) continue;
                fitting.add(item);
                String candidate = renderSelected(evidence.photos(), fitting.stream()
                        .map(GraphEvidenceItem::id)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
                if (tokens(candidate) > available) {
                    fitting.remove(fitting.size() - 1);
                    break;
                }
            }
            selectedIds.clear();
            fitting.forEach(item -> selectedIds.add(item.id()));
            rendered = renderSelected(evidence.photos(), selectedIds);
        }

        LinkedHashSet<String> selectedPaths = new LinkedHashSet<>();
        selectedIds.stream().map(byId::get).map(GraphEvidenceItem::sourcePath)
                .filter(path -> path != null && !path.isBlank())
                .forEach(selectedPaths::add);
        int finalTokens = tokens(rendered);
        log.info("Graph reducer coverage: rawTokens={}, finalTokens={}, inputItems={}, selectedItems={}, batches={}",
                rawTokens, finalTokens, allItems.size(), selectedIds.size(), batches.size());
        return new Selection(rendered, List.copyOf(selectedPaths), true,
                rawTokens, finalTokens, allItems.size(), selectedIds.size());
    }

    private List<String> selectIds(String question, List<GraphEvidenceItem> items, int cap) {
        String evidence = items.stream().map(GraphEvidenceItem::render)
                .collect(java.util.stream.Collectors.joining("\n"));
        String prompt = """
                Jesteś deterministycznym selektorem dowodów GraphRAG. Wybierz najmniejszy zestaw
                elementów wystarczający do odpowiedzi na pytanie. Nie twórz odpowiedzi ani nowych faktów.
                Zwróć wyłącznie JSON: {"selectedEvidenceIds":["E1.1"]}.
                Wolno zwrócić maksymalnie %d identyfikatorów i wyłącznie identyfikatory z wejścia.
                Dla pytania otwartego wybierz różnorodne, konkretne fakty; dla braku związku zwróć pustą listę.

                Pytanie: %s
                Dowody:
                %s
                """.formatted(cap, question == null ? "" : question, evidence);
        List<String> parsed = parseIds(generate(prompt), items, cap);
        if (!parsed.isEmpty()) return parsed;
        String repair = """
                Poprzednia odpowiedź nie dała poprawnej listy identyfikatorów. Zwróć tylko JSON
                {"selectedEvidenceIds":[]} z maksymalnie %d identyfikatorami z poniższego wejścia.
                Pytanie: %s
                Dowody:
                %s
                """.formatted(cap, question == null ? "" : question, evidence);
        return parseIds(generate(repair), items, cap);
    }

    private String generate(String prompt) {
        try {
            return controlModel == null ? "" : controlModel.generate(prompt);
        } catch (Exception e) {
            log.warn("Graph evidence selection failed: {}", e.getMessage());
            return "";
        }
    }

    private List<String> parseIds(String response, List<GraphEvidenceItem> allowedItems, int cap) {
        if (response == null || response.isBlank()) return List.of();
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            String json = start >= 0 && end > start ? response.substring(start, end + 1) : response;
            JsonNode node = objectMapper.readTree(json).path("selectedEvidenceIds");
            if (!node.isArray()) return List.of();
            Set<String> allowed = allowedItems.stream().map(GraphEvidenceItem::id)
                    .collect(java.util.stream.Collectors.toSet());
            LinkedHashSet<String> result = new LinkedHashSet<>();
            node.forEach(value -> {
                String id = value.asText("").trim();
                if (allowed.contains(id) && result.size() < cap) result.add(id);
            });
            return List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<List<GraphEvidenceItem>> batches(List<GraphEvidenceItem> items, int tokenLimit) {
        List<List<GraphEvidenceItem>> result = new ArrayList<>();
        List<GraphEvidenceItem> current = new ArrayList<>();
        int currentTokens = 0;
        for (GraphEvidenceItem item : items) {
            int itemTokens = Math.max(1, tokens(item.render()));
            if (!current.isEmpty() && currentTokens + itemTokens > tokenLimit) {
                result.add(List.copyOf(current));
                current.clear();
                currentTokens = 0;
            }
            current.add(item);
            currentTokens += itemTokens;
        }
        if (!current.isEmpty()) result.add(List.copyOf(current));
        return result;
    }

    private String renderSelected(List<GraphPhotoEvidence> photos, Set<String> selectedIds) {
        List<String> blocks = new ArrayList<>();
        for (GraphPhotoEvidence photo : photos) {
            List<GraphEvidenceItem> items = photo.items().stream()
                    .filter(item -> selectedIds.contains(item.id()))
                    .toList();
            if (!items.isEmpty()) {
                blocks.add(new GraphPhotoEvidence(
                        photo.id(), photo.sourcePath(), items, photo.heading()).render());
            }
        }
        return String.join("\n", blocks);
    }

    private int tokens(String text) {
        return text == null || text.isBlank() ? 0 : tokenizer.estimateTokenCountInText(text);
    }

    public record Selection(String context, List<String> selectedPaths, boolean reduced,
                            int rawTokens, int finalTokens, int inputItems, int selectedItems) {
        public Selection {
            context = context == null ? "" : context;
            selectedPaths = selectedPaths == null ? List.of() : List.copyOf(selectedPaths);
        }

        public boolean hasEvidence() {
            return !context.isBlank();
        }
    }
}
