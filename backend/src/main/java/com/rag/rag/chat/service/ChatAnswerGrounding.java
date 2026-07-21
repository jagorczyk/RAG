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
                "potrzebuję więcej szczegółów",
                "potrzebuje wiecej szczegolow",
                "potrzebuję wiecej szczegółów",
                "potrzebuje więcej szczegółów",
                "więcej szczegółów na jego temat",
                "wiecej szczegolow na jego temat",
                "więcej szczegółów na jej temat",
                "więcej szczegółów na temat zdjęcia",
                "wiecej szczegolow na temat zdjecia",
                "jeśli masz więcej szczegółów",
                "jesli masz wiecej szczegolow",
                "jeśli masz wiecej szczegółów",
                "jesli masz więcej szczegółów",
                "mogę spróbować bardziej precyzyjnie",
                "moge sprobowac bardziej precyzyjnie",
                "aby opisać zdjęcie",
                "aby opisac zdjecie",
                "żeby opisać zdjęcie",
                "zeby opisac zdjecie",
                "możesz mi powiedzieć, co jest na zdjęciu",
                "mozesz mi powiedziec, co jest na zdjeciu",
                "możesz mi powiedzieć co jest na zdjęciu",
                "jakie kolory dominują",
                "jakie kolory dominuja",
                "im więcej szczegółów podasz",
                "im wiecej szczegolow podasz",
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
                "tell me more about the photo",
                "what is in the photo",
                "describe how they look",
                "describe what they look");
    }

    /**
     * True when the model returned an empty shell or generic assistant greeting instead of
     * using retrieval/graph evidence. Answer shape only — not user-question routing.
     */
    public static boolean isEmptyOrGreetingNonAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String trimmed = answer.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        // Very short pure greetings / openers with no substance.
        if (trimmed.length() <= 80 && containsAny(lower,
                "how can i assist you",
                "how can i help you",
                "how may i assist you",
                "how may i help you",
                "what can i help you with",
                "what can i do for you",
                "w czym mogę pomóc",
                "w czym moge pomoc",
                "jak mogę pomóc",
                "jak moge pomoc",
                "czym mogę pomóc",
                "czym moge pomoc",
                "jak mogę ci pomóc",
                "jak moge ci pomoc")) {
            return true;
        }
        // Exact-ish greeting-only lines (optionally with Hello/Hi/Cześć prefix).
        if (lower.matches("^(hello|hi|hey|cześć|czesc|witaj)[!.,\\s].{0,60}$")
                && containsAny(lower, "assist", "help", "pomóc", "pomoc")) {
            return true;
        }
        return lower.equals("hello!")
                || lower.equals("hello")
                || lower.equals("hi!")
                || lower.equals("hi")
                || lower.equals("hey!")
                || lower.equals("cześć!")
                || lower.equals("czesc!");
    }

    /**
     * True when the answer is free-form encyclopedic / etymology / textbook-definition prose
     * rather than evidence-backed photo content. Answer shape only — no person-name routing.
     * Catches name essays and off-topic lectures (e.g. linguistics definitions) that ignore
     * the photo library.
     */
    public static boolean isGeneralKnowledgeEssay(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        if (containsAny(lower,
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
                "etymology of the name")) {
            return true;
        }
        // Off-topic definitional / textbook lectures (live failure: @photo → linguistics essay).
        return isOffTopicDefinitionalLecture(lower, answer.trim().length());
    }

    /**
     * Long abstract definition essay with numbered sections and no photo grounding vocabulary.
     * Answer shape only — does not inspect the user question.
     */
    static boolean isOffTopicDefinitionalLecture(String lower, int length) {
        if (lower == null || lower.isBlank() || length < 160) {
            return false;
        }
        // Photo/library grounding — keep short factual answers about images.
        if (containsAny(lower,
                "na zdjęciu",
                "na zdjeciu",
                "na potwierdzonych",
                "w bibliotece",
                "grafie wiedzy",
                "nie znaleziono",
                "brak potwierdz",
                "stoi ",
                "siedzi ",
                "ubrany",
                "ubrana",
                "koszul",
                "spodni",
                "włos",
                "wlos",
                "osoba ",
                "osoby ",
                "uczestnik")) {
            return false;
        }
        boolean definitional = containsAny(lower,
                "odnosi się do sytuacji",
                "odnosi sie do sytuacji",
                "odnosi się do",
                "odnosi sie do",
                "jest przeciwieństwem",
                "jest przeciwiestwem",
                "jest przeciwieństwem niepełnego",
                "pełne ograniczenie semantyczne",
                "pelne ograniczenie semantyczne",
                "ograniczenie semantyczne",
                "full semantic constraint",
                "ściśle określone znaczenie",
                "scisle okreslone znaczenie",
                "ściśle określone",
                "w języku polskim",
                "w jezyku polskim",
                "terminy specjalistyczne",
                "frazeologizm",
                "reguły gramatyczne",
                "reguly gramatyczne",
                "przykładem może być",
                "przykladem moze byc",
                "przykładem może być słowo",
                "w konkretnych dziedzinach",
                "in linguistics",
                "semantic constraint",
                "ang. *",
                "(ang.");
        boolean numberedLecture = hasNumberedOptionMenu(lower)
                || (lower.contains("1.") && lower.contains("2.") && lower.contains("3."));
        // Multi-section textbook dump without any image vocabulary.
        return definitional && (numberedLecture || length > 400);
    }

    /**
     * True when the model asks the user to clarify instead of using provided evidence.
     * Answer shape only — not user-question wording (AGENTS.md).
     */
    public static boolean isClarificationSeekingNonAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "could you clarify",
                "can you clarify",
                "not entirely sure what you mean",
                "i'm not entirely sure what you mean",
                "i am not entirely sure what you mean",
                "not sure what you mean",
                "i'm not sure what you mean",
                "i am not sure what you mean",
                "what do you mean",
                "provide more context",
                "provide more information",
                "are you referring to something specific",
                "let me know so i can assist",
                "let me know so i can help",
                "nie jestem pewien, co masz na myśli",
                "nie jestem pewien co masz na myśli",
                "nie jestem pewna, co masz na myśli",
                "nie jestem pewna co masz na myśli",
                "nie wiem, co masz na myśli",
                "nie wiem co masz na myśli",
                "czy możesz doprecyzować",
                "czy mozesz doprecyzowac",
                "doprecyzuj proszę",
                "doprecyzuj prosze",
                "o co dokładnie pytasz",
                "o co dokladnie pytasz",
                "nie do końca rozumiem",
                "nie do konca rozumiem",
                "could you rephrase",
                "can you rephrase",
                "możesz mi powiedzieć, co jest na zdjęciu",
                "mozesz mi powiedziec co jest na zdjeciu",
                "aby opisać zdjęcie, potrzebuję",
                "aby opisac zdjecie, potrzebuje",
                "oczywiście! aby opisać",
                "oczywiście! aby opisac");
    }

    /**
     * True when the assistant reply is predominantly English assistant-style prose
     * despite system instructions requiring Polish. Answer shape only.
     */
    public static boolean isEnglishAssistantNonAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String trimmed = answer.trim();
        // Polish diacritics → treat as Polish (good answers may mix a proper name + PL).
        if (trimmed.matches("(?s).*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ].*")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        // Common Polish stems / phrases without diacritics — keep such answers.
        String padded = " " + lower + " ";
        if (padded.contains(" jest ")
                || padded.contains(" są ")
                || padded.contains(" sa ")
                || lower.contains("zdję")
                || lower.contains("zdjec")
                || lower.contains("potwierdz")
                || lower.contains("bibliotece")
                || lower.contains("znaleziono")
                || lower.contains("dokumentach")
                || lower.contains("osoba")
                || lower.contains("osoby")) {
            return false;
        }
        // Leading English assistant openers / hedging without Polish content.
        return containsAny(lower,
                "it seems like",
                "it looks like",
                "i'm not entirely sure",
                "i am not entirely sure",
                "i'm not sure",
                "i am not sure",
                "could you",
                "can you clarify",
                "let me know so i can",
                "how can i assist",
                "how can i help",
                "feel free to",
                "happy to help");
    }

    /**
     * True when the model moralizes / gives safety-policy advice instead of using photo evidence
     * (e.g. knife → call the police). Answer shape only — not user-question routing.
     */
    public static boolean isSafetyOrOfftopicLecture(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        boolean safetyAdvice = containsAny(lower,
                "zachować ostrożność",
                "zachowac ostroznosc",
                "skontaktować się z odpowiednimi służbami",
                "skontaktowac sie z odpowiednimi sluzbami",
                "skontaktować się z policją",
                "skontaktowac sie z policja",
                "skontaktuj się z policją",
                "skontaktuj sie z policja",
                "zagrożenie dla czyjegoś bezpieczeństwa",
                "zagrozenie dla czyjegos bezpieczenstwa",
                "projekt artystyczny",
                "sytuacja fikcyjna",
                "używanie noża może być niebezpieczne",
                "uzywanie noza moze byc niebezpieczne",
                "odpowiedzialność. używanie",
                "odpowiedzialnosc. uzywanie",
                "call the police",
                "contact the authorities",
                "safety first",
                "can be dangerous");
        boolean helpOffer = containsAny(lower,
                "daj mi znać, a postaram się pomóc",
                "daj mi znac, a postaram sie pomoc",
                "jeśli masz konkretne pytanie",
                "jesli masz konkretne pytanie",
                "if you have a specific question",
                "let me know so i can help");
        // Long moralizing lecture without photo grounding shape.
        boolean longLecture = answer.trim().length() > 280
                && containsAny(lower, "ostrożność", "ostroznosc", "bezpieczeństw", "bezpieczenstw",
                "policj", "służb", "sluzb", "niebezpieczn");
        if (safetyAdvice) {
            return true;
        }
        return longLecture && helpOffer;
    }

    /**
     * True when certain participant names exist in evidence but the model answer names none of them
     * and instead digresses. Avoids rewriting short grounded answers that paraphrase without names.
     */
    public static boolean isAnswerMissingEvidenceNames(String answer, List<String> evidenceNames) {
        if (answer == null || answer.isBlank() || evidenceNames == null || evidenceNames.isEmpty()) {
            return false;
        }
        // Only flag long digressions / lectures — short factual lines may omit names intentionally.
        if (answer.trim().length() < 120) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        for (String name : evidenceNames) {
            if (name != null && !name.isBlank() && lower.contains(name.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        // Digression markers: advice, meta-help, hypothetical framing.
        return containsAny(lower,
                "jeśli masz na myśli",
                "jesli masz na mysli",
                "ważne jest",
                "wazne jest",
                "postaram się pomóc",
                "postaram sie pomoc",
                "daj mi znać",
                "daj mi znac",
                "if you mean",
                "it is important",
                "feel free to");
    }

    /**
     * True when the model invents a multi-option "what they might be doing" menu
     * instead of answering from provided graph/embedding evidence.
     * Answer shape only — no user-question phrase routing (AGENTS.md).
     */
    public static boolean isSpeculativeHypothesisList(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);

        // Strong single signals from the known failure mode (Olek-style).
        if (containsAny(lower,
                "może robić różne rzeczy",
                "moze robic rozne rzeczy",
                "może robić rozne rzeczy",
                "moze robić różne rzeczy",
                "could be doing various things",
                "might be doing various things",
                "can be doing various things")) {
            return true;
        }

        boolean hedging = containsAny(lower,
                "w zależności od kontekstu",
                "w zaleznosci od kontekstu",
                "zależnie od kontekstu",
                "zaleznie od kontekstu",
                "depending on the context",
                "depending on context",
                "może na przykład",
                "moze na przyklad",
                "na przykład:",
                "na przyklad:",
                "for example:");
        boolean numberedMenu = hasNumberedOptionMenu(lower);
        boolean asksForMore = containsAny(lower,
                "jeśli masz więcej szczegółów",
                "jesli masz wiecej szczegolow",
                "jeśli masz wiecej szczegółów",
                "jesli masz więcej szczegółów",
                "jeśli podasz więcej",
                "jesli podasz wiecej",
                "mogę spróbować bardziej precyzyjnie",
                "moge sprobowac bardziej precyzyjnie",
                "mogę bardziej precyzyjnie",
                "moge bardziej precyzyjnie",
                "if you have more details",
                "if you provide more details",
                "if you give me more details");
        // Typical speculative activity catalogue (need several to avoid false positives).
        int activityHits = 0;
        if (containsAny(lower, "pozować", "pozowac", "posing")) {
            activityHits++;
        }
        if (containsAny(lower, "grać w piłkę", "grac w pilke", "jeździć na rowerze", "jezdzic na rowerze")) {
            activityHits++;
        }
        if (containsAny(lower, "na plaży", "na plazy", "w górach", "w gorach", "na imprezie")) {
            activityHits++;
        }
        if (containsAny(lower, "interagować", "interagowac", "bawić się z", "bawic sie z")) {
            activityHits++;
        }
        if (containsAny(lower, "uchwycony w naturalnej", "naturalnej chwili")) {
            activityHits++;
        }

        if (hedging && (numberedMenu || asksForMore || activityHits >= 2)) {
            return true;
        }
        if (numberedMenu && asksForMore) {
            return true;
        }
        return numberedMenu && activityHits >= 3;
    }

    /**
     * True when the model answer must not be shown as-is despite certain evidence.
     */
    public static boolean shouldRewriteUngroundedAnswer(String modelAnswer, boolean entityScoped) {
        return shouldRewriteUngroundedAnswer(modelAnswer, entityScoped, List.of());
    }

    /**
     * @param evidenceNames certain participant names from graph/sources; used to catch long digressions
     *                      that never mention any of them (e.g. safety lecture on a knife photo).
     */
    public static boolean shouldRewriteUngroundedAnswer(
            String modelAnswer, boolean entityScoped, List<String> evidenceNames) {
        return isCapabilityDenial(modelAnswer)
                || isEmptyOrGreetingNonAnswer(modelAnswer)
                || isClarificationSeekingNonAnswer(modelAnswer)
                || isEnglishAssistantNonAnswer(modelAnswer)
                || isSpeculativeHypothesisList(modelAnswer)
                || isSafetyOrOfftopicLecture(modelAnswer)
                || isAnswerMissingEvidenceNames(modelAnswer, evidenceNames)
                // Etymology / encyclopedic essays never answer from photo evidence (scoped or not).
                || isGeneralKnowledgeEssay(modelAnswer);
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
        List<String> names = normalizeNames(recoveryNames);
        // Free-form branch: keep natural photo prose. Only rewrite hard failure shapes
        // (capability denial, greeting, essay, speculative menu, safety lecture).
        if (isPhotoGroundedProse(modelAnswer)
                && !isCapabilityDenial(modelAnswer)
                && !isEmptyOrGreetingNonAnswer(modelAnswer)
                && !isGeneralKnowledgeEssay(modelAnswer)
                && !isSpeculativeHypothesisList(modelAnswer)
                && !isSafetyOrOfftopicLecture(modelAnswer)
                && !isClarificationSeekingNonAnswer(modelAnswer)
                && !isEnglishAssistantNonAnswer(modelAnswer)) {
            return modelAnswer;
        }
        // Speculative multi-option menus never leave the system when evidence exists —
        // prefer an explicit no-detail notice over inventing activities.
        if (isSpeculativeHypothesisList(modelAnswer)) {
            return GROUNDED_NO_DETAIL_FALLBACK;
        }
        // Free encyclopedic / textbook essays: roster when we know who is on the photo,
        // otherwise no-detail (never leave definition lectures in the UI).
        if (isGeneralKnowledgeEssay(modelAnswer)) {
            if (!names.isEmpty()) {
                if (entityScoped && names.size() == 1) {
                    return formatEntityScopedPresence(names);
                }
                return formatParticipantRoster(names);
            }
            return GROUNDED_NO_DETAIL_FALLBACK;
        }
        if (!shouldRewriteUngroundedAnswer(modelAnswer, entityScoped, names)) {
            return modelAnswer == null ? "" : modelAnswer;
        }
        // Safety / digression with a multi-person roster → answer who is on the photo, not presence template.
        if (isSafetyOrOfftopicLecture(modelAnswer) || isAnswerMissingEvidenceNames(modelAnswer, names)) {
            if (names.isEmpty()) {
                return GROUNDED_NO_ROSTER_FALLBACK;
            }
            if (!entityScoped || names.size() > 1) {
                return formatParticipantRoster(names);
            }
            return formatEntityScopedPresence(names);
        }
        if (names.isEmpty()) {
            return entityScoped ? GROUNDED_NO_DETAIL_FALLBACK : GROUNDED_NO_ROSTER_FALLBACK;
        }
        if (entityScoped) {
            return formatEntityScopedPresence(names);
        }
        return formatParticipantRoster(names);
    }

    /**
     * True when the answer already reads like a natural photo description (not a template /
     * refusal / lecture). Free-form branch keeps such prose instead of rewriting to roster.
     */
    public static boolean isPhotoGroundedProse(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        if (isCapabilityDenial(answer) || isEmptyOrGreetingNonAnswer(answer)
                || isGeneralKnowledgeEssay(answer) || isSpeculativeHypothesisList(answer)
                || isSafetyOrOfftopicLecture(answer)) {
            return false;
        }
        // Stiff recovery templates — not free-form photo prose.
        if (lower.contains("potwierdzonych zdjęciach w bibliotece")
                || lower.equals(GROUNDED_NO_ROSTER_FALLBACK.toLowerCase(Locale.ROOT))
                || lower.equals(GROUNDED_NO_DETAIL_FALLBACK.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return containsAny(lower,
                "na zdjęciu",
                "na zdjeciu",
                "widać",
                "widac",
                "stoi ",
                "siedzi ",
                "ubrany",
                "ubrana",
                "koszul",
                "spodni",
                "włos",
                "wlos",
                "obok ",
                "scena",
                "tło",
                "tlo",
                "uśmiech",
                "usmiech",
                "trzyma ",
                "idzie ",
                "patrzy ",
                "pozuje");
    }

    /**
     * Short Polish presence statement for named entities with certain graph/file evidence.
     * No filenames in prose (sources list carries paths).
     * Soft phrasing — free-form branch avoids bureaucratic "potwierdzonych w bibliotece" tone
     * when this is only a last-resort recovery after a model failure.
     */
    public static String formatEntityScopedPresence(List<String> entityNames) {
        List<String> names = normalizeNames(entityNames);
        if (names.isEmpty()) {
            return GROUNDED_NO_DETAIL_FALLBACK;
        }
        if (names.size() == 1) {
            return names.get(0) + " pojawia się na zdjęciach w Twojej bibliotece.";
        }
        if (names.size() == 2) {
            return names.get(0) + " i " + names.get(1)
                    + " pojawiają się na zdjęciach w Twojej bibliotece.";
        }
        String head = String.join(", ", names.subList(0, names.size() - 1));
        return head + " i " + names.get(names.size() - 1)
                + " pojawiają się na zdjęciach w Twojej bibliotece.";
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

    /** Numbered 1./2. (optionally 3.) option menu — common LLM hedge shape. */
    private static boolean hasNumberedOptionMenu(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        boolean one = lower.contains("\n1.") || lower.contains("\n1)")
                || lower.contains("1. ") || lower.contains("1) ");
        boolean two = lower.contains("\n2.") || lower.contains("\n2)")
                || lower.contains("2. ") || lower.contains("2) ");
        boolean three = lower.contains("\n3.") || lower.contains("\n3)")
                || lower.contains("3. ") || lower.contains("3) ");
        return one && two && three;
    }
}
