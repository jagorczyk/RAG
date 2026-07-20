package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.rag.rag.knowledge.query.VisualQueryMatch;

import java.util.List;
import java.util.stream.IntStream;

/** Produces a short visual answer without document retrieval or evidence narration. */
@Service
@RequiredArgsConstructor
public class VerifiedVisualAnswerService {

    /** Short PL prose when MATCH evidence exists but the model did not return usable text. */
    public static final String MATCH_FALLBACK_ANSWER = "Oto potwierdzone zdjęcia.";

    @Qualifier("chatLanguageModel")
    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * One short Polish sentence for confirmed visual matches.
     * Evidence details belong in the UI sources list, not in the prose answer.
     * Never returns blank when {@code matches} is non-empty (criterion 1: MATCH is grounding).
     */
    public String answer(String question, List<VisualQueryMatch> matches) {
        return answer(question, matches, List.of());
    }

    /**
     * Same as {@link #answer(String, List)} with optional certain graph participant names
     * for the matched files (principle 2/3: confirmed identities may be used in the answer).
     */
    public String answer(String question, List<VisualQueryMatch> matches,
                         List<String> certainParticipantNames) {
        int confirmedCount = matches == null ? 0 : matches.size();
        if (confirmedCount <= 0) {
            return "Nie znaleziono potwierdzonych dowodów wizualnych.";
        }
        String evidence = IntStream.range(0, matches.size())
                .mapToObj(index -> {
                    VisualQueryMatch match = matches.get(index);
                    String reasons = match.reasons() == null || match.reasons().isEmpty()
                            ? "potwierdzone dopasowanie obrazu"
                            : String.join("; ", match.reasons());
                    return "D" + (index + 1) + ": " + reasons;
                })
                .collect(java.util.stream.Collectors.joining("\n"));
        List<String> names = certainParticipantNames == null ? List.of() : certainParticipantNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (!names.isEmpty()) {
            evidence = evidence + "\nD" + (confirmedCount + 1)
                    + ": potwierdzone tożsamości uczestników z grafu wiedzy: "
                    + String.join(", ", names);
            confirmedCount = confirmedCount + 1;
        }
        String prompt = """
                %s

                Zwróć wyłącznie JSON: {"answer":"...","usedEvidenceIds":["D1"]}.
                Pole answer ma zawierać odpowiedź po polsku w jednym lub dwóch krótkich zdaniach.
                Odpowiedz konkretnie na wszystkie części pytania, używając wyłącznie faktów z sekcji Dowody.
                Jeżeli dowody potwierdzają różne czynności na kilku zdjęciach, krótko je zagreguj.
                Zasady:
                - gdy pytanie dotyczy tego, kto jest na zdjęciu lub jak mają na imię, użyj potwierdzonych imion z Dowodów
                - podaj wygląd, czynność lub scenę tylko wtedy, gdy użytkownik o to pyta
                - nie podawaj pewności, score ani dowodów
                - nie wymieniaj plików, ścieżek ani liczby w formie technicznej
                - nie zaczynaj od "Na podstawie dowodów"
                - nie dopowiadaj imion ani faktów nieobecnych w Dowodach
                - nie używaj placeholderów vision (person 1, animal 1) jako imion

                Pytanie użytkownika: %s
                Dowody:
                %s
                """.formatted(ChatService.ANSWER_INSTRUCTIONS, question == null ? "" : question, evidence);
        String parsed = parseGroundedAnswer(chatModel.generate(prompt), confirmedCount);
        if (parsed == null || parsed.isBlank()) {
            // MATCH already proven by the matcher — keep a short PL answer for the UI.
            // Prefer certain identity roster when available (stronger than generic fallback).
            if (!names.isEmpty()) {
                return ChatAnswerGrounding.formatParticipantRoster(names);
            }
            return MATCH_FALLBACK_ANSWER;
        }
        return parsed;
    }

    /**
     * Prefer validated JSON; accept short free-form Polish when the model skips the schema
     * (common in practice). Reject JSON that cites unknown evidence ids.
     */
    private String parseGroundedAnswer(String response, int evidenceCount) {
        if (response == null) {
            return "";
        }
        String trimmed = response.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                JsonNode root = objectMapper.readTree(trimmed.substring(start, end + 1));
                String answer = root.path("answer").asText("").trim();
                JsonNode used = root.path("usedEvidenceIds");
                if (!answer.isBlank() && used.isArray() && !used.isEmpty()) {
                    for (JsonNode value : used) {
                        String id = value.asText("");
                        if (!id.matches("D[1-9][0-9]*")) {
                            return "";
                        }
                        int index = Integer.parseInt(id.substring(1));
                        if (index < 1 || index > evidenceCount) {
                            return "";
                        }
                    }
                    return answer;
                }
                // JSON without usable usedEvidenceIds — do not promote its answer field alone.
                // Fall through to free-form only when the whole response is plain prose.
                if (start == 0) {
                    return "";
                }
            }
        } catch (Exception ignored) {
            // Free-form path below.
        }
        return sanitizeFreeForm(trimmed);
    }

    /** Accept short free-form model prose; refuse instruction blobs and multi-paragraph dumps. */
    private static String sanitizeFreeForm(String text) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("{") || value.contains("\"usedEvidenceIds\"")) {
            return "";
        }
        if (value.contains("Jesteś asystentem")
                || value.contains("Nie znaleziono informacji w dokumentach")
                || value.contains("Nie znaleziono potwierdzonych dowodów wizualnych")) {
            return "";
        }
        // Keep at most two short sentences worth of text.
        if (value.length() > 400) {
            value = value.substring(0, 400).trim();
        }
        return value;
    }
}
