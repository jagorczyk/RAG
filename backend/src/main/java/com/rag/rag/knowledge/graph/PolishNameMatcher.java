package com.rag.rag.knowledge.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PolishNameMatcher {

    private static final String[] SUFFIXES = {
            "owi", "ego", "iem", "ami", "ach", "ą", "ę", "a", "u", "y", "i", "ie", "em"
    };

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}0-9_-]+");

    private static final Set<String> STOP_WORDS = Set.of(
            "kto", "jest", "kim", "co", "robi", "robił", "robiła", "robią", "robia", "gdzie", "w", "na", "których", "ktorych",
            "zdjęciach", "zdjeciach", "zdjęciu", "zdjeciu", "zdjęcie", "zdjecie", "zdjęci", "zdjeci",
            "osoba", "osoby", "osób", "osob", "postać", "postac", "postaci", "kobieta", "mężczyzna", "mezczyzna",
            "dziewczyna", "chłopak", "chlopak", "pan", "pani", "gość", "gosc", "twarz", "twarze",
            "ten", "ta", "to", "za", "obok", "przy", "lewej", "prawej", "stronie", "od", "siedzi", "stoi",
            "występuje", "wystepuje", "pojawia", "widać", "widac", "jakich", "plik", "plikach", "foto", "obraz", "obrazach",
            "powiedz", "opisz", "wiesz", "opowiedz", "przedstaw", "scharakteryzuj", "dokumentach", "materiałach", "materialach",
            "folderach", "katalogach", "oraz", "i", "a", "czy", "jak", "jaka", "jaki", "jakie", "która", "ktory",
            "które", "ktore", "się", "sie", "mnie", "mi", "ci", "nie", "tak", "tej", "tego", "tym",
            "tych", "jego", "jej", "ich", "nas", "was", "być", "byc", "był", "była", "byli", "były", "bylo", "było",
            "lista", "znajduje", "znajdują", "znajduja", "którym", "ktorym", "którego", "ktorego",
            "razem", "wspólnie", "wspolnie", "towarzystwie", "towarzyszy", "jeszcze", "poza", "oprócz", "oprocz",
            "wymień", "wymien", "ilu", "ile", "ludzi", "pokaż", "pokaz", "wyświetl", "wyswietl",
            "informacje", "informacja", "dane", "charakterystyka", "wygląda", "wyglada", "znajdę", "znajde",
            "można", "mozna", "zobaczyć", "zobaczyc", "rozpoznaj", "zidentyfikuj", "identyfikuj", "imię", "imie", "imiona",
            "nazwisko", "nazwiska", "ludzie", "człowiek", "czlowiek", "znajomy", "znajomi", "kolega", "koleżanka",
            "goście", "gosci", "sąsiad", "sasiad", "najbliżej", "najblizej", "pobliżu", "poblizu", "lewo", "prawo",
            "porabia", "wykonuje", "wykonywał", "wykonywala", "zajmuje", "zajmował", "zajmowal", "zajmowała", "zajmowala",
            "aktywności", "aktywnosci", "czynności", "czynnosci", "aktualnie", "pracuje", "fotografuje", "pozuje",
            "widzian", "widziana", "widziany", "parze", "parach", "wszyscy", "którymi", "ktorymi", "nazywają", "nazywaja"
    );

    private PolishNameMatcher() {
    }

    public static boolean isStopWord(String token) {
        return token == null || token.isBlank() || STOP_WORDS.contains(token.toLowerCase(Locale.ROOT));
    }

    public static Set<String> generateVariants(String token) {
        String normalized = token.toLowerCase(Locale.ROOT).trim();
        Set<String> variants = new LinkedHashSet<>();
        if (normalized.isEmpty() || isStopWord(normalized)) {
            return variants;
        }
        variants.add(normalized);
        addPolishNameInflections(normalized, variants);

        for (String suffix : SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length() + 2) {
                variants.add(normalized.substring(0, normalized.length() - suffix.length()));
            }
        }
        return variants;
    }

    private static void addPolishNameInflections(String normalized, Set<String> variants) {
        if (normalized.endsWith("ek") && normalized.length() > 3) {
            String stem = normalized.substring(0, normalized.length() - 2);
            variants.add(stem + "ka");
            variants.add(stem + "ku");
            variants.add(stem + "kiem");
            variants.add(stem + "kowi");
            variants.add(stem + "ki");
            return;
        }

        if (normalized.endsWith("ka") && normalized.length() > 4) {
            variants.add(normalized.substring(0, normalized.length() - 2) + "ek");
        }
        if (normalized.endsWith("ku") && normalized.length() > 4) {
            variants.add(normalized.substring(0, normalized.length() - 2) + "ek");
        }
        if (normalized.endsWith("kiem") && normalized.length() > 5) {
            variants.add(normalized.substring(0, normalized.length() - 4) + "ek");
        }
        if (normalized.endsWith("kowi") && normalized.length() > 5) {
            variants.add(normalized.substring(0, normalized.length() - 4) + "ek");
        }

        if (normalized.endsWith("a") && normalized.length() > 3 && !normalized.endsWith("ka")) {
            variants.add(normalized.substring(0, normalized.length() - 1));
        }
        if (!normalized.endsWith("a") && !normalized.endsWith("ek") && normalized.length() > 3) {
            variants.add(normalized + "a");
            variants.add(normalized + "em");
            variants.add(normalized + "u");
        }
    }

    public static List<String> extractTokens(String question) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(question.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() > 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public static List<String> extractEntityTokens(String question) {
        return extractTokens(question).stream()
                .filter(token -> !isStopWord(token))
                .toList();
    }

    /**
     * True when {@code requested} is the same person name as {@code displayName}
     * allowing Polish case/diminutive variants (Olka↔Olek, Piotrka↔Piotrek).
     */
    public static boolean namesMatch(String requested, String displayName) {
        if (requested == null || displayName == null) {
            return false;
        }
        String a = requested.trim().toLowerCase(Locale.ROOT);
        String b = displayName.trim().toLowerCase(Locale.ROOT);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        Set<String> left = generateVariants(a);
        Set<String> right = generateVariants(b);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String v : left) {
            if (right.contains(v)) {
                return true;
            }
        }
        return false;
    }
}
