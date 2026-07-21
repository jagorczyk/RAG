package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.graph.GroundedVisualClaim;
import com.rag.rag.knowledge.query.VisualQueryMatch;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Selects evidence ids with an LLM, but renders only immutable claim text. */
@Service
@RequiredArgsConstructor
public class VerifiedVisualAnswerService {

    public static final String MATCH_FALLBACK_ANSWER = "Brak potwierdzonego szczegółowego opisu w dowodach.";
    public static final String NO_VISUAL_EVIDENCE = "Nie znaleziono potwierdzonych dowodów wizualnych.";

    @Qualifier("chatLanguageModel")
    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String answer(String question, List<VisualQueryMatch> matches) {
        return answer(question, matches, List.of());
    }

    public String answer(String question, List<VisualQueryMatch> matches, List<String> certainParticipantNames) {
        if (matches == null || matches.isEmpty()) return NO_VISUAL_EVIDENCE;
        List<GroundedVisualClaim> claims = matches.stream()
                .flatMap(match -> match.claims().stream())
                .distinct()
                .toList();
        if (claims.isEmpty()) {
            List<String> names = certainParticipantNames == null ? List.of() : certainParticipantNames.stream()
                    .filter(name -> name != null && !name.isBlank()).distinct().toList();
            return names.isEmpty() ? MATCH_FALLBACK_ANSWER : ChatAnswerGrounding.formatParticipantRoster(names);
        }
        return answerFromClaims(question, claims);
    }

    public String answerFromClaims(String question, List<GroundedVisualClaim> claims) {
        if (claims == null || claims.isEmpty()) return MATCH_FALLBACK_ANSWER;
        Map<String, GroundedVisualClaim> byId = new LinkedHashMap<>();
        claims.stream().filter(claim -> claim != null && !claim.id().isBlank()
                        && !claim.statementPl().isBlank())
                .forEach(claim -> byId.putIfAbsent(claim.id(), claim));
        if (byId.isEmpty()) return MATCH_FALLBACK_ANSWER;

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
        List<String> selected = parseSelectedIds(chatModel.generate(prompt), byId);
        if (selected.isEmpty()) return MATCH_FALLBACK_ANSWER;
        return selected.stream().limit(5).map(byId::get).map(GroundedVisualClaim::statementPl)
                .map(VerifiedVisualAnswerService::ensureSentence)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private List<String> parseSelectedIds(String response, Map<String, GroundedVisualClaim> byId) {
        try {
            if (response == null) return List.of();
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) return List.of();
            JsonNode ids = objectMapper.readTree(response.substring(start, end + 1)).path("usedEvidenceIds");
            if (!ids.isArray()) return List.of();
            List<String> selected = new java.util.ArrayList<>();
            ids.forEach(node -> {
                String id = node.asText("");
                if (byId.containsKey(id) && !selected.contains(id)) selected.add(id);
            });
            return List.copyOf(selected);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String ensureSentence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) return trimmed;
        return trimmed.matches(".*[.!?]$") ? trimmed : trimmed + ".";
    }
}
