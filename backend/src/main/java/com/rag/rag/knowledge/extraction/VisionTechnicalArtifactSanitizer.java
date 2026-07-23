package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps face-anchor annotations out of semantic observations. The anchors are rendered on a
 * temporary analysis copy and are useful only for joining a vision person to a detected face.
 */
public final class VisionTechnicalArtifactSanitizer {

    private static final Pattern GENERATED_FACE_ANCHOR =
            Pattern.compile("(?i)(?<![\\p{L}\\p{N}])face[_ -]?\\d+(?![\\p{L}\\p{N}])");

    private VisionTechnicalArtifactSanitizer() {
    }

    /** Sanitize the DTO before it is persisted and projected, while retaining valid join anchors. */
    public static VisionResultDto sanitize(VisionResultDto dto, Set<String> allowedAnchors) {
        if (dto == null) {
            return null;
        }
        Set<String> anchors = normalizedAnchors(allowedAnchors);
        if (dto.getEntities() != null) {
            int personIndex = 0;
            for (ExtractedEntityDto entity : dto.getEntities()) {
                if (entity == null) {
                    continue;
                }
                personIndex++;
                if (!isAllowedAnchor(entity.getFaceAnchorId(), anchors)) {
                    entity.setFaceAnchorId(null);
                }
                if (containsTechnicalAnchor(entity.getLabel(), anchors)) {
                    entity.setLabel("person " + personIndex);
                }
                entity.setActions(filterValues(entity.getActions(), anchors));
                entity.setObjects(filterValues(entity.getObjects(), anchors));
                entity.setNearbyObjects(filterValues(entity.getNearbyObjects(), anchors));
                entity.setNearbyText(filterValues(entity.getNearbyText(), anchors));
                entity.setVisualCues(filterValues(entity.getVisualCues(), anchors));
            }
        }
        if (dto.getRelations() != null) {
            dto.setRelations(dto.getRelations().stream()
                    .filter(relation -> relation != null)
                    .filter(relation -> !containsTechnicalAnchor(relation.getSubjectLabel(), anchors))
                    .filter(relation -> !containsTechnicalAnchor(relation.getObjectLabel(), anchors))
                    .filter(relation -> !containsTechnicalAnchor(relation.getRelation(), anchors))
                    .toList());
        }
        dto.setScene(removeContaminatedSentences(dto.getScene(), anchors));
        dto.setSceneSummary(removeContaminatedSentences(dto.getSceneSummary(), anchors));
        dto.setSetting(removeContaminatedSentences(dto.getSetting(), anchors));
        dto.setLighting(removeContaminatedSentences(dto.getLighting(), anchors));
        dto.setBackground(filterValues(dto.getBackground(), anchors));
        if (dto.getVisibleTexts() != null) {
            dto.setVisibleTexts(dto.getVisibleTexts().stream()
                    .filter(value -> value != null && !containsTechnicalAnchor(value.getText(), anchors))
                    .toList());
        }
        return dto;
    }

    /**
     * Returns a deep copy safe for text embeddings. Unlike the stored DTO, this form also removes
     * {@code face_anchor_id} itself because it is metadata, not visual content.
     */
    public static JsonNode sanitizeEmbeddingJson(JsonNode input) {
        if (input == null) {
            return null;
        }
        JsonNode copy = input.deepCopy();
        Set<String> anchors = new LinkedHashSet<>();
        collectAnchorValues(copy, anchors);
        sanitizeNode(copy, normalizedAnchors(anchors));
        return copy;
    }

    /** Removes annotation ids from the searchable raw fallback without discarding its observations. */
    public static String sanitizeFallbackText(String text, Set<String> knownAnchors) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String sanitized = GENERATED_FACE_ANCHOR.matcher(text).replaceAll("");
        for (String anchor : normalizedAnchors(knownAnchors)) {
            sanitized = Pattern.compile(
                            "(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(anchor)
                                    + "(?![\\p{L}\\p{N}])")
                    .matcher(sanitized)
                    .replaceAll("");
        }
        return sanitized.replaceAll("[ \\t]+([,.;:!?])", "$1")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }

    private static void sanitizeNode(JsonNode node, Set<String> anchors) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.remove("face_anchor_id");
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> remove = new ArrayList<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value != null && value.isTextual()
                        && containsTechnicalAnchor(value.asText(), anchors)) {
                    String cleaned = removeContaminatedSentences(value.asText(), anchors);
                    if (cleaned == null || cleaned.isBlank()) {
                        remove.add(field.getKey());
                    } else {
                        object.put(field.getKey(), cleaned);
                    }
                } else {
                    sanitizeNode(value, anchors);
                }
            }
            remove.forEach(object::remove);
            return;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int index = array.size() - 1; index >= 0; index--) {
                JsonNode value = array.get(index);
                if (value != null && value.isTextual()
                        && containsTechnicalAnchor(value.asText(), anchors)) {
                    array.remove(index);
                } else if (isTechnicalVisibleText(value, anchors)) {
                    array.remove(index);
                } else {
                    sanitizeNode(value, anchors);
                }
            }
        }
    }

    private static boolean isTechnicalVisibleText(JsonNode value, Set<String> anchors) {
        return value != null && value.isObject() && value.has("text")
                && containsTechnicalAnchor(value.path("text").asText(""), anchors);
    }

    private static void collectAnchorValues(JsonNode node, Set<String> anchors) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if ("face_anchor_id".equals(field.getKey()) && field.getValue().isTextual()) {
                    String value = field.getValue().asText("").trim();
                    if (!value.isBlank()) {
                        anchors.add(value);
                    }
                }
                collectAnchorValues(field.getValue(), anchors);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectAnchorValues(child, anchors));
        }
    }

    private static List<String> filterValues(List<String> values, Set<String> anchors) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !containsTechnicalAnchor(value, anchors))
                .toList();
    }

    private static String removeContaminatedSentences(String text, Set<String> anchors) {
        if (text == null || text.isBlank() || !containsTechnicalAnchor(text, anchors)) {
            return text;
        }
        String[] sentences = text.trim().split("(?<=[.!?])\\s+");
        StringBuilder clean = new StringBuilder();
        for (String sentence : sentences) {
            if (!containsTechnicalAnchor(sentence, anchors)) {
                if (!clean.isEmpty()) {
                    clean.append(' ');
                }
                clean.append(sentence.trim());
            }
        }
        return clean.toString();
    }

    private static boolean isAllowedAnchor(String value, Set<String> allowedAnchors) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return allowedAnchors.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean containsTechnicalAnchor(String value, Set<String> anchors) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (GENERATED_FACE_ANCHOR.matcher(value).find()) {
            return true;
        }
        return anchors.stream().anyMatch(anchor -> containsToken(lower, anchor));
    }

    private static boolean containsToken(String text, String token) {
        int from = 0;
        while (from < text.length()) {
            int index = text.indexOf(token, from);
            if (index < 0) {
                return false;
            }
            int before = index - 1;
            int after = index + token.length();
            boolean leftBoundary = before < 0 || !Character.isLetterOrDigit(text.charAt(before));
            boolean rightBoundary = after >= text.length() || !Character.isLetterOrDigit(text.charAt(after));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            from = index + token.length();
        }
        return false;
    }

    private static Set<String> normalizedAnchors(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }
}
