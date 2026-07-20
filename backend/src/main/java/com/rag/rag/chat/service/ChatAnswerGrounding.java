package com.rag.rag.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure post-answer decisions for chat: capability-denial detection, participant roster
 * formatting, and cleanup after technical path/filename stripping.
 * No question-phrase routing (AGENTS.md).
 */
public final class ChatAnswerGrounding {

    /** Short PL fallback when evidence exists but no certain participant names are available. */
    public static final String GROUNDED_NO_ROSTER_FALLBACK =
            "Znaleziono potwierdzone informacje w grafie wiedzy.";

    private static final Pattern EMPTY_QUOTE_SHELL = Pattern.compile(
            "[„\"«‚'']\\s*[”\"»‚'']");
    private static final Pattern DANGLING_COMMA_BEFORE_PUNCT = Pattern.compile(
            ",\\s*([,.;:!?])");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern SPACE_BEFORE_PUNCT = Pattern.compile(" +([,.;:!?])");

    private ChatAnswerGrounding() {
    }

    /**
     * True when the model prose refuses image/file capability instead of using provided evidence.
     * Detects answer shape only — not user question wording.
     */
    public static boolean isCapabilityDenial(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "nie mogę zobaczyć",
                "nie moge zobaczyc",
                "nie widzę zdję",
                "nie widze zdje",
                "nie widzę obraz",
                "nie widze obraz",
                "nie mam dostępu do",
                "nie mam dostepu do",
                "nie mam dostępu do konkretnych plik",
                "nie mam dostepu do konkretnych plik",
                "nie jestem w stanie zobaczyć",
                "nie jestem w stanie zobaczyc",
                "nie jestem w stanie określić, kto jest na zdjęciu",
                "nie jestem w stanie okreslic, kto jest na zdjeciu",
                "cannot see image",
                "can't see image",
                "i cannot see",
                "i can't see",
                "don't have access to",
                "do not have access to",
                "no access to files",
                "no access to images",
                "unable to view",
                "unable to see the photo",
                "unable to see the image");
    }

    /**
     * Prefer a grounded roster (or non-denial fallback) when the model refuses vision/file
     * access despite certain graph paths or final sources.
     * Leaves non-denial answers unchanged.
     */
    public static String resolveAgainstDenial(
            String modelAnswer,
            List<String> certainParticipantNames,
            boolean hasCertainEvidenceOrSources) {
        if (!hasCertainEvidenceOrSources || !isCapabilityDenial(modelAnswer)) {
            return modelAnswer == null ? "" : modelAnswer;
        }
        List<String> names = normalizeNames(certainParticipantNames);
        if (!names.isEmpty()) {
            return formatParticipantRoster(names);
        }
        return GROUNDED_NO_ROSTER_FALLBACK;
    }

    /** Polish one-sentence roster of certain participant display names. */
    public static String formatParticipantRoster(List<String> displayNames) {
        List<String> names = normalizeNames(displayNames);
        if (names.isEmpty()) {
            return GROUNDED_NO_ROSTER_FALLBACK;
        }
        if (names.size() == 1) {
            return "Na zdjęciu jest " + names.get(0) + ".";
        }
        if (names.size() == 2) {
            return "Na zdjęciu są " + names.get(0) + " i " + names.get(1) + ".";
        }
        String head = String.join(", ", names.subList(0, names.size() - 1));
        return "Na zdjęciu są " + head + " i " + names.get(names.size() - 1) + ".";
    }

    /**
     * After path/filename stripping, remove empty quote shells and tidy dangling punctuation.
     */
    public static String cleanEmptyQuoteArtifacts(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        String cleaned = EMPTY_QUOTE_SHELL.matcher(answer).replaceAll("");
        cleaned = DANGLING_COMMA_BEFORE_PUNCT.matcher(cleaned).replaceAll("$1");
        cleaned = cleaned.replaceAll("(?i)\\btakich jak\\s*[,.]?", "");
        cleaned = cleaned.replaceAll("(?i)\\bjak\\s*[,.]?\\s*(?=[.!?]|$)", "");
        cleaned = SPACE_BEFORE_PUNCT.matcher(cleaned).replaceAll("$1");
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    private static List<String> normalizeNames(List<String> displayNames) {
        if (displayNames == null || displayNames.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String name : displayNames) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return new ArrayList<>(unique);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
