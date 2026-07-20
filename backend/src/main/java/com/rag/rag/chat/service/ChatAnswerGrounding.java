package com.rag.rag.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure post-answer decisions for chat: capability-denial detection, participant roster
 * formatting, entity-scoped presence fallback, and cleanup after technical path/filename stripping.
 * No question-phrase routing (AGENTS.md).
 */
public final class ChatAnswerGrounding {

    /** Short PL fallback when evidence exists but no certain participant names are available. */
    public static final String GROUNDED_NO_ROSTER_FALLBACK =
            "Znaleziono potwierdzone informacje w grafie wiedzy.";

    /**
     * When named entities have certain evidence but the model invents general knowledge
     * or refuses without using graph detail about appearance.
     */
    public static final String GROUNDED_NO_DETAIL_FALLBACK =
            "Brak potwierdzonego szczegółowego opisu w dowodach.";

    private static final Pattern EMPTY_QUOTE_SHELL = Pattern.compile(
            "[„\"«‚'']\\s*[”\"»‚'']");
    private static final Pattern DANGLING_COMMA_BEFORE_PUNCT = Pattern.compile(
            ",\\s*([,.;:!?])");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern SPACE_BEFORE_PUNCT = Pattern.compile(" +([,.;:!?])");

    private ChatAnswerGrounding() {
    }

    /**
     * True when the model prose refuses image/file capability or claims missing identity context
     * instead of using provided evidence. Detects answer shape only — not user question wording.
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
                "nie jestem w stanie określić kto jest na zdjęciu",
                "nie jestem w stanie okreslic kto jest na zdjeciu",
                "nie jestem w stanie odpowiedzieć na to pytanie",
                "nie jestem w stanie odpowiedziec na to pytanie",
                "nie mam informacji, o kogo",
                "nie mam informacji o kogo",
                "o kogo konkretnie pytasz",
                "nie wiem, o kogo",
                "nie wiem o kogo",
                "brakuje mi kontekstu",
                "brakuje mi informacji, o kogo",
                // Need-more-info / describe-how-they-look refusals (answer shape only)
                "potrzebuję więcej informacji",
                "potrzebuje wiecej informacji",
                "potrzebuję wiecej informacji",
                "potrzebuje więcej informacji",
                "podać dostępne zdjęcia",
                "podac dostepne zdjecia",
                "podaj dostępne zdjęcia",
                "podaj dostepne zdjecia",
                "opisz, jak wygląda",
                "opisz jak wygląda",
                "opisz, jak wyglada",
                "opisz jak wyglada",
                "opisać, jak wygląda",
                "opisac, jak wyglada",
                "opisać jak wygląda",
                "opisac jak wyglada",
                "czekam na więcej informacji",
                "czekam na wiecej informacji",
                "podaj więcej szczegółów",
                "podaj wiecej szczegolow",
                "podać więcej szczegółów",
                "podac wiecej szczegolow",
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
                "unable to see the image",
                "i don't know who you're asking",
                "i do not know who you're asking",
                "more details or context",
                "need more information",
                "need more info",
                "describe how they look",
                "describe what they look");
    }

    /**
     * True when the answer is free-form encyclopedic / etymology-style prose about a name
     * rather than evidence-backed photo content. Answer shape only — no person-name routing.
     */
    public static boolean isGeneralKnowledgeEssay(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "pochodzi z języka",
                "pochodzi z jezyka",
                "jest zdrobnieniem",
                "zdrobnienie od",
                "imię męskie",
                "imie meskie",
                "imię żeńskie",
                "imie zenskie",
                "jedno z najpopularniejszych imion",
                "w kulturze popularnej",
                "biblijne korzenie",
                "etymolog",
                "oznacza „skała",
                "oznacza \"skała",
                "oznacza „skala",
                "is a diminutive of",
                "derives from the greek",
                "derives from greek",
                "popular name meaning",
                "etymology of the name");
    }

    /**
     * Prefer a grounded roster (or non-denial fallback) when the model refuses vision/file
     * access despite certain graph paths or final sources.
     * Leaves non-denial answers unchanged. Uses full participant roster (not entity-scoped).
     */
    public static String resolveAgainstDenial(
            String modelAnswer,
            List<String> certainParticipantNames,
            boolean hasCertainEvidenceOrSources) {
        return resolveGroundedAnswer(
                modelAnswer, certainParticipantNames, hasCertainEvidenceOrSources, false);
    }

    /**
     * Grounds model answers when certain evidence exists.
     * <ul>
     *   <li>Capability denials → entity-scoped presence (when {@code entityScoped}) or full roster</li>
     *   <li>General-knowledge essays with named entities → entity-scoped presence / no-detail</li>
     *   <li>Otherwise leaves the model answer unchanged</li>
     * </ul>
     *
     * @param entityScoped when true, recovery names are plan entities only (not co-present people)
     */
    public static String resolveGroundedAnswer(
            String modelAnswer,
            List<String> recoveryNames,
            boolean hasCertainEvidenceOrSources,
            boolean entityScoped) {
        if (!hasCertainEvidenceOrSources) {
            return modelAnswer == null ? "" : modelAnswer;
        }
        boolean rewrite = isCapabilityDenial(modelAnswer)
                || (entityScoped && isGeneralKnowledgeEssay(modelAnswer));
        if (!rewrite) {
            return modelAnswer == null ? "" : modelAnswer;
        }
        List<String> names = normalizeNames(recoveryNames);
        if (names.isEmpty()) {
            return entityScoped ? GROUNDED_NO_DETAIL_FALLBACK : GROUNDED_NO_ROSTER_FALLBACK;
        }
        if (entityScoped) {
            return formatEntityScopedPresence(names);
        }
        return formatParticipantRoster(names);
    }

    /**
     * Short Polish presence statement for named entities with certain graph/file evidence.
     * No filenames in prose (sources list carries paths).
     */
    public static String formatEntityScopedPresence(List<String> entityNames) {
        List<String> names = normalizeNames(entityNames);
        if (names.isEmpty()) {
            return GROUNDED_NO_DETAIL_FALLBACK;
        }
        if (names.size() == 1) {
            return names.get(0) + " jest na potwierdzonych zdjęciach w bibliotece.";
        }
        if (names.size() == 2) {
            return names.get(0) + " i " + names.get(1)
                    + " są na potwierdzonych zdjęciach w bibliotece.";
        }
        String head = String.join(", ", names.subList(0, names.size() - 1));
        return head + " i " + names.get(names.size() - 1)
                + " są na potwierdzonych zdjęciach w bibliotece.";
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
