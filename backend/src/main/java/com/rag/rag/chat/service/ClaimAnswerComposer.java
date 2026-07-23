package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.fact.FactStatementRewriter;
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
    /** Keep answers short — avoid five near-duplicate appearance lines. */
    public static final int MAX_CLAIM_SENTENCES = 3;

    @Qualifier("structuredControlLanguageModel")
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
                Wybierz maksymalnie %d różnych dowodów; unikaj powtórzeń tej samej informacji.
                Nie twórz odpowiedzi ani nowych faktów. Zwróć tylko JSON:
                {"usedEvidenceIds":["F-..."]}.
                Gdy żaden dowód nie odpowiada, zwróć pustą listę.
                Pytanie: %s
                Dowody:
                %s
                """.formatted(MAX_CLAIM_SENTENCES, question == null ? "" : question, evidence);

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

        return renderSelected(selected, byId);
    }

    public ClaimAnswerResult answerFromClaims(String question, List<GroundedVisualClaim> claims) {
        return answerFromClaims(question, claims, List.of());
    }

    private ClaimAnswerResult renderSelected(List<String> selected, Map<String, GroundedVisualClaim> byId) {
        List<String> usedIds = new ArrayList<>();
        Set<String> usedPaths = new LinkedHashSet<>();
        Set<String> seenSentences = new LinkedHashSet<>();
        StringBuilder prose = new StringBuilder();
        for (String id : selected) {
            if (usedIds.size() >= MAX_CLAIM_SENTENCES) {
                break;
            }
            GroundedVisualClaim claim = byId.get(id);
            if (claim == null) {
                continue;
            }
            String sentence = ensureSentence(claim.statementPl());
            if (sentence.isBlank()) {
                continue;
            }
            String norm = normalizeSentence(sentence);
            if (seenSentences.contains(norm) || isNearDuplicate(norm, seenSentences)) {
                continue;
            }
            seenSentences.add(norm);
            usedIds.add(id);
            if (claim.filePath() != null && !claim.filePath().isBlank()) {
                usedPaths.add(claim.filePath().trim());
            }
            if (!prose.isEmpty()) {
                prose.append(' ');
            }
            prose.append(sentence);
        }
        if (prose.isEmpty()) {
            return ClaimAnswerResult.empty();
        }
        return new ClaimAnswerResult(prose.toString(), List.copyOf(usedIds), List.copyOf(usedPaths), true);
    }

    /**
     * Soft fallback: diversify by claim kind (action / appearance / relation / object), max 3.
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
        List<GroundedVisualClaim> matching = byId.values().stream()
                .filter(c -> c.entityName() != null
                        && names.stream().anyMatch(n -> c.entityName().trim().equalsIgnoreCase(n)))
                .toList();
        if (matching.isEmpty()) {
            return List.of();
        }
        List<String> selected = new ArrayList<>();
        Set<String> usedKinds = new LinkedHashSet<>();
        // Prefer actions and relations before dumping many appearance lines.
        for (String kind : List.of("action", "relation", "object", "appearance", "other")) {
            for (GroundedVisualClaim claim : matching) {
                if (selected.size() >= MAX_CLAIM_SENTENCES) {
                    break;
                }
                if (!kind.equals(claimKind(claim))) {
                    continue;
                }
                // At most one appearance claim in soft mode.
                if ("appearance".equals(kind) && usedKinds.contains("appearance")) {
                    continue;
                }
                selected.add(claim.id());
                usedKinds.add(kind);
            }
        }
        return selected;
    }

    private static String claimKind(GroundedVisualClaim claim) {
        String pred = claim.predicate() == null ? "" : claim.predicate().trim().toUpperCase(Locale.ROOT);
        if (FactStatementRewriter.ACTION_APPEARANCE.equals(pred) || pred.contains("WYGLĄD")) {
            return "appearance";
        }
        if (FactStatementRewriter.ACTION_RELATED_OBJECT.equals(pred)
                || FactStatementRewriter.ACTION_NEAR_OBJECT.equals(pred)
                || FactStatementRewriter.ACTION_NEAR_TEXT.equals(pred)) {
            return "object";
        }
        if (pred.contains("LEFT") || pred.contains("RIGHT") || pred.contains("OBOK")
                || pred.contains("LEWEJ") || pred.contains("PRAWEJ") || pred.contains("Z ")) {
            return "relation";
        }
        if (!pred.isBlank()) {
            return "action";
        }
        return "other";
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

    private static String normalizeSentence(String sentence) {
        return sentence.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s]+", " ")
                .replaceAll("[.!?]+$", "")
                .trim();
    }

    private static boolean isNearDuplicate(String norm, Set<String> seen) {
        for (String other : seen) {
            if (other.equals(norm)) {
                continue;
            }
            if (other.contains(norm) || norm.contains(other)) {
                return true;
            }
        }
        return false;
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
