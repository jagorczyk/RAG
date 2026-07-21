package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.graph.GroundedVisualClaim;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Evidence-first answers: the LLM only selects claim IDs; prose is immutable {@code statementPl}.
 * Shared by VISUAL_VALIDATION and GRAPH paths.
 */
@Service
@RequiredArgsConstructor
public class ClaimAnswerComposer {

    public static final String EMPTY_FALLBACK = "Brak potwierdzonego szczegółowego opisu w dowodach.";
    public static final int MAX_CLAIM_SENTENCES = 5;

    @Qualifier("chatLanguageModel")
    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param question      answer-facing question (may be planner expansion)
     * @param claims        candidate grounded claims
     * @param entityFilter  optional plan entity names — used only for soft fallback when ID select is empty
     */
    public ClaimAnswerResult answerFromClaims(
            String question, List<GroundedVisualClaim> claims, List<String> entityFilter) {
        if (claims == null || claims.isEmpty()) {
            return ClaimAnswerResult.empty();
        }
        Map<String, GroundedVisualClaim> byId = new LinkedHashMap<>();
        claims.stream()
                .filter(claim -> claim != null && !claim.id().isBlank() && !claim.statementPl().isBlank())
                .forEach(claim -> byId.putIfAbsent(claim.id(), claim));
        if (byId.isEmpty()) {
            return ClaimAnswerResult.empty();
        }

        String evidence = byId.values().stream()
                .map(claim -> claim.id() + ": " + claim.statementPl())
                .collect(java.util.stream.Collectors.joining("\n"));
        String prompt = """
                Wybierz wyłącznie identyfikatory dowodów, które bezpośrednio odpowiadają na pytanie.
                Nie twórz odpowiedzi ani nowych faktów. Zwróć tylko JSON:
                {"usedEvidenceIds":["F-..."]}.
                Gdy żaden dowód nie odpowiada, zwróć pustą listę.
                Pytanie: %s
                Dowody:
                %s
                """.formatted(question == null ? "" : question, evidence);

        List<String> selected = List.of();
        if (chatModel != null) {
            selected = parseSelectedIds(chatModel.generate(prompt), byId);
        }
        if (selected.isEmpty()) {
            selected = softSelectForEntities(byId, entityFilter);
        }
        if (selected.isEmpty()) {
            return ClaimAnswerResult.empty();
        }

        List<String> usedIds = selected.stream().limit(MAX_CLAIM_SENTENCES).toList();
        Set<String> usedPaths = new LinkedHashSet<>();
        StringBuilder prose = new StringBuilder();
        for (String id : usedIds) {
            GroundedVisualClaim claim = byId.get(id);
            if (claim == null) {
                continue;
            }
            if (claim.filePath() != null && !claim.filePath().isBlank()) {
                usedPaths.add(claim.filePath().trim());
            }
            String sentence = ensureSentence(claim.statementPl());
            if (sentence.isBlank()) {
                continue;
            }
            if (!prose.isEmpty()) {
                prose.append(' ');
            }
            prose.append(sentence);
        }
        if (prose.isEmpty()) {
            return ClaimAnswerResult.empty();
        }
        return new ClaimAnswerResult(prose.toString(), usedIds, List.copyOf(usedPaths), true);
    }

    public ClaimAnswerResult answerFromClaims(String question, List<GroundedVisualClaim> claims) {
        return answerFromClaims(question, claims, List.of());
    }

    /**
     * When the model returns no IDs, prefer a few claims for named plan entities over free-form LLM.
     */
    private static List<String> softSelectForEntities(
            Map<String, GroundedVisualClaim> byId, List<String> entityFilter) {
        if (entityFilter == null || entityFilter.isEmpty() || byId.isEmpty()) {
            return List.of();
        }
        List<String> names = entityFilter.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (names.isEmpty()) {
            return List.of();
        }
        List<String> selected = new ArrayList<>();
        for (GroundedVisualClaim claim : byId.values()) {
            String entity = claim.entityName() == null ? "" : claim.entityName().trim().toLowerCase(Locale.ROOT);
            if (entity.isBlank()) {
                continue;
            }
            boolean match = names.stream().anyMatch(entity::equals);
            if (match) {
                selected.add(claim.id());
            }
            if (selected.size() >= MAX_CLAIM_SENTENCES) {
                break;
            }
        }
        return selected;
    }

    private List<String> parseSelectedIds(String response, Map<String, GroundedVisualClaim> byId) {
        try {
            if (response == null) {
                return List.of();
            }
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return List.of();
            }
            JsonNode ids = objectMapper.readTree(response.substring(start, end + 1)).path("usedEvidenceIds");
            if (!ids.isArray()) {
                return List.of();
            }
            List<String> selected = new ArrayList<>();
            ids.forEach(node -> {
                String id = node.asText("");
                if (byId.containsKey(id) && !selected.contains(id)) {
                    selected.add(id);
                }
            });
            return List.copyOf(selected);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String ensureSentence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.matches(".*[.!?]$") ? trimmed : trimmed + ".";
    }

    public record ClaimAnswerResult(
            String answer,
            List<String> usedClaimIds,
            List<String> usedFilePaths,
            boolean fromClaims
    ) {
        public ClaimAnswerResult {
            answer = answer == null ? "" : answer;
            usedClaimIds = usedClaimIds == null ? List.of() : List.copyOf(usedClaimIds);
            usedFilePaths = usedFilePaths == null ? List.of() : List.copyOf(usedFilePaths);
        }

        public static ClaimAnswerResult empty() {
            return new ClaimAnswerResult(EMPTY_FALLBACK, List.of(), List.of(), false);
        }

        public boolean hasGroundedProse() {
            return fromClaims && answer != null && !answer.isBlank() && !EMPTY_FALLBACK.equals(answer);
        }
    }
}
