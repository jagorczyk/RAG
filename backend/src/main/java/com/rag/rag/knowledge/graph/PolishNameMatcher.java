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
            "kto", "jest", "kim", "co", "robi", "robił", "robiła", "gdzie", "w", "na", "których", "ktorych",
            "zdjęciach", "zdjeciach", "zdjęciu", "zdjeciu", "zdjęcie", "zdjecie", "zdjęci", "zdjeci",
            "osoba", "postać", "postac", "kobieta", "mężczyzna", "mezczyzna", "ten", "ta", "to", "za",
            "obok", "lewej", "prawej", "stronie", "od", "siedzi", "stoi", "występuje", "wystepuje",
            "jakich", "plik", "plikach", "foto", "obraz", "obrazach", "powiedz", "opisz", "wiesz",
            "dokumentach", "oraz", "i", "a", "czy", "jak", "jaka", "jaki", "jakie", "która", "ktory",
            "które", "ktore", "się", "sie", "mnie", "mi", "ci", "nie", "tak", "tej", "tego", "tym",
            "tych", "jego", "jej", "ich", "nas", "was", "być", "byc", "był", "była", "bylo", "było",
            "lista", "znajduje", "znajdują", "znajduja", "którym", "ktorym", "którego", "ktorego"
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

        for (String suffix : SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length() + 2) {
                variants.add(normalized.substring(0, normalized.length() - suffix.length()));
            }
        }
        return variants;
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
}
