package com.rag.rag.knowledge.graph;

import java.util.Locale;
import java.util.Set;

public final class RelationConstants {

    public static final String NEXT_TO = "REL_NEXT_TO";
    public static final String LEFT_OF = "REL_LEFT_OF";
    public static final String RIGHT_OF = "REL_RIGHT_OF";
    public static final String IN_FRONT_OF = "REL_IN_FRONT_OF";
    public static final String BEHIND = "REL_BEHIND";

    private static final Set<String> SUPPORTED_VISION_RELATIONS = Set.of(
            "NEXT_TO", "LEFT_OF", "RIGHT_OF", "IN_FRONT_OF", "BEHIND"
    );

    private RelationConstants() {
    }

    public static String toFactAction(String visionRelation) {
        if (visionRelation == null || visionRelation.isBlank()) {
            return null;
        }
        String normalized = visionRelation.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_VISION_RELATIONS.contains(normalized)) {
            return null;
        }
        return "REL_" + normalized;
    }

    public static String prettyAction(String action) {
        if (action == null || action.isBlank()) {
            return "";
        }
        if (action.startsWith("REL_")) {
            return prettyRelation(action);
        }
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "near_object" -> "ma w pobliżu obiekt";
            case "near_text" -> "ma obok napis";
            case "sitting" -> "siedzi";
            case "standing" -> "stoi";
            case "smiling" -> "uśmiecha się";
            case "walking" -> "idzie";
            case "running" -> "biegnie";
            case "talking" -> "rozmawia";
            case "eating" -> "je";
            case "drinking" -> "pije";
            case "reading" -> "czyta";
            case "writing" -> "pisze";
            case "looking" -> "patrzy";
            case "holding" -> "trzyma";
            case "wearing" -> "ma na sobie";
            default -> action;
        };
    }

    public static String prettyRelation(String factAction) {
        if (factAction == null) {
            return "";
        }
        return switch (factAction) {
            case NEXT_TO -> "jest obok";
            case LEFT_OF -> "jest po lewej od";
            case RIGHT_OF -> "jest po prawej od";
            case IN_FRONT_OF -> "jest przed";
            case BEHIND -> "jest za";
            default -> factAction;
        };
    }

    public static boolean isSymmetric(String factAction) {
        return NEXT_TO.equals(factAction);
    }
}
