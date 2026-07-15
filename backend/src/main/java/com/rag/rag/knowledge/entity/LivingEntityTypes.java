package com.rag.rag.knowledge.entity;

import java.util.Locale;
import java.util.Set;

public final class LivingEntityTypes {

    public static final String PERSON = "PERSON";
    public static final String ANIMAL = "ANIMAL";

    private static final Set<String> SUPPORTED_TYPES = Set.of(PERSON, ANIMAL);

    private LivingEntityTypes() {
    }

    public static String normalize(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_TYPES.contains(normalized) ? normalized : null;
    }

    public static boolean isSupported(String type) {
        return normalize(type) != null;
    }
}
