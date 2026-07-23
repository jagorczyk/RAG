package com.rag.rag.knowledge.graph;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rag.rag.core.text.Utf8MojibakeRepair;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor;
import com.rag.rag.knowledge.extraction.VisionTechnicalArtifactSanitizer;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.fact.FactStatementRewriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a single structured JSON document for image embeddings.
 * Same shape for ingest and identity refresh — no free-form prose mishmash.
 *
 * <pre>
 * {
 *   "type": "image_knowledge",
 *   "scene": { "label", "summary", "setting", "lighting", "background": [] },
 *   "participants": [{
 *     "name", "type", "appearance": [], "actions": [],
 *     "held_objects": [], "nearby_objects": [], "nearby_text": [],
 *     "neighbors": [], "relations": [{ "to", "relation", "statement" }]
 *   }],
 *   "visible_texts": []
 * }
 * </pre>
 */
public final class ImageEmbeddingDocumentBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 2-space indent, LF newlines — readable in UI and logs. */
    private static final ObjectWriter PRETTY = MAPPER.writer(readablePrettyPrinter());

    private ImageEmbeddingDocumentBuilder() {
    }

    private static DefaultPrettyPrinter readablePrettyPrinter() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    /** Pretty-print any JSON tree/text for display (also used by embeddings API). */
    public static String formatReadable(String jsonOrText) {
        if (jsonOrText == null || jsonOrText.isBlank()) {
            return jsonOrText == null ? "" : jsonOrText;
        }
        String repaired = Utf8MojibakeRepair.repair(jsonOrText);
        String trimmed = repaired.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return repaired;
        }
        try {
            JsonNode node = MAPPER.readTree(trimmed);
            return PRETTY.writeValueAsString(node);
        } catch (Exception e) {
            return repaired;
        }
    }

    public static String build(FileEntity file, List<EntityMention> mentions, List<Fact> facts) {
        List<EntityMention> people = filterPeople(mentions);
        Map<String, String> canonicalLabels = canonicalLabels(people);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "image_knowledge");
        if (file != null) {
            JsonNode vision = enrichVisionWithCanonicalNames(recoverVisionPayload(file), canonicalLabels);
            root.set("scene", buildScene(file, vision));
            ArrayNode texts = parseStringArrayNode(file.getVisibleTexts());
            if ((texts == null || texts.isEmpty()) && vision != null
                    && vision.has("visible_texts") && vision.get("visible_texts").isArray()) {
                texts = normalizeVisibleTexts(vision.get("visible_texts"));
            }
            if (texts != null && !texts.isEmpty()) {
                root.set("visible_texts", texts);
            }
            if (vision != null) {
                // Retain every recovered observation for recall instead of reducing it to a summary.
                root.set("vision_observations", vision);
            }
        } else {
            root.set("scene", MAPPER.createObjectNode());
        }

        Map<UUID, EntityMention> byId = new LinkedHashMap<>();
        for (EntityMention m : people) {
            if (m.getId() != null) {
                byId.put(m.getId(), m);
            }
        }

        Map<UUID, List<Fact>> factsByMention = new LinkedHashMap<>();
        if (facts != null) {
            for (Fact fact : facts) {
                if (fact == null || fact.getMention() == null || fact.getMention().getId() == null) {
                    continue;
                }
                factsByMention
                        .computeIfAbsent(fact.getMention().getId(), ignored -> new ArrayList<>())
                        .add(fact);
            }
        }

        ArrayNode participants = MAPPER.createArrayNode();
        for (EntityMention mention : people) {
            participants.add(buildParticipant(mention, factsByMention.getOrDefault(mention.getId(), List.of()),
                    byId, canonicalLabels));
        }
        root.set("participants", participants);

        JsonNode semanticRoot = VisionTechnicalArtifactSanitizer.sanitizeEmbeddingJson(root);

        try {
            return PRETTY.writeValueAsString(semanticRoot);
        } catch (Exception e) {
            // last resort: still try single-line JSON rather than Java object dump
            try {
                return MAPPER.writeValueAsString(semanticRoot);
            } catch (Exception ignored) {
                return semanticRoot.toString();
            }
        }
    }

    private static ObjectNode buildScene(FileEntity file, JsonNode vision) {
        ObjectNode scene = MAPPER.createObjectNode();
        if (file.getImageScene() != null && !file.getImageScene().isBlank()) {
            scene.put("label", clean(file.getImageScene()));
        } else if (textValue(vision, "scene") != null) {
            scene.put("label", textValue(vision, "scene"));
        }
        if (file.getImageSummary() != null && !file.getImageSummary().isBlank()) {
            String storedSummary = clean(file.getImageSummary());
            String recoveredSummary = textValue(vision, "scene_summary");
            scene.put("summary", looksLikeJson(storedSummary) && recoveredSummary != null
                    ? recoveredSummary : storedSummary);
        } else if (textValue(vision, "scene_summary") != null) {
            scene.put("summary", textValue(vision, "scene_summary"));
        }
        JsonNode attrs = parseJson(file.getSceneAttributes());
        if (attrs != null && attrs.isObject()) {
            if (attrs.hasNonNull("setting") && !attrs.get("setting").asText("").isBlank()) {
                scene.put("setting", clean(attrs.get("setting").asText()));
            }
            if (attrs.hasNonNull("lighting") && !attrs.get("lighting").asText("").isBlank()) {
                scene.put("lighting", clean(attrs.get("lighting").asText()));
            }
            if (attrs.has("background") && attrs.get("background").isArray()) {
                scene.set("background", attrs.get("background"));
            }
        }
        if (!scene.has("setting") && textValue(vision, "setting") != null) {
            scene.put("setting", textValue(vision, "setting"));
        }
        if (!scene.has("lighting") && textValue(vision, "lighting") != null) {
            scene.put("lighting", textValue(vision, "lighting"));
        }
        if (!scene.has("background") && vision != null
                && vision.has("background") && vision.get("background").isArray()) {
            scene.set("background", vision.get("background"));
        }
        return scene;
    }

    private static ObjectNode buildParticipant(
            EntityMention mention, List<Fact> mentionFacts, Map<UUID, EntityMention> peopleById,
            Map<String, String> canonicalLabels) {
        ObjectNode person = MAPPER.createObjectNode();
        String subjectName = clean(displayName(mention));
        person.put("name", subjectName);
        String type = mention.getEntityType() != null ? mention.getEntityType() : "PERSON";
        person.put("type", clean(type));

        LinkedHashSet<String> appearance = new LinkedHashSet<>(parseStringList(mention.getVisualCues()));
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        LinkedHashSet<String> held = new LinkedHashSet<>();
        LinkedHashSet<String> nearby = new LinkedHashSet<>();
        LinkedHashSet<String> nearbyText = new LinkedHashSet<>(parseStringList(mention.getNearbyText()));
        // contextObjects may mix held+nearby; prefer fact classification when present.
        LinkedHashSet<String> contextObjects = new LinkedHashSet<>(parseStringList(mention.getContextObjects()));

        LinkedHashSet<String> neighbors = new LinkedHashSet<>();
        ArrayNode relations = MAPPER.createArrayNode();
        LinkedHashSet<String> relationKeys = new LinkedHashSet<>();

        for (Fact fact : mentionFacts) {
            if (fact == null) {
                continue;
            }
            String action = fact.getAction() == null ? "" : clean(fact.getAction());
            String value = canonicalizeReference(resolveFactValue(fact, peopleById), canonicalLabels);
            String statement = FactStatementRewriter.buildStatement(subjectName, action, value);
            if (statement == null || statement.isBlank()) {
                statement = clean(fact.getStatementPl());
            }

            if (FactStatementRewriter.ACTION_APPEARANCE.equalsIgnoreCase(action)) {
                if (value != null && !value.isBlank()) {
                    appearance.add(value.trim());
                }
                continue;
            }
            if (FactStatementRewriter.ACTION_RELATED_OBJECT.equalsIgnoreCase(action)) {
                if (value != null && !value.isBlank() && !sameReference(subjectName, value)) {
                    held.add(value.trim());
                    addRelation(relations, relationKeys, value, "ma przy sobie", statement);
                }
                continue;
            }
            if (FactStatementRewriter.ACTION_NEAR_OBJECT.equalsIgnoreCase(action)) {
                if (value != null && !value.isBlank() && !sameReference(subjectName, value)) {
                    nearby.add(value.trim());
                    addRelation(relations, relationKeys, value, "obok", statement);
                }
                continue;
            }
            if (FactStatementRewriter.ACTION_NEAR_TEXT.equalsIgnoreCase(action)) {
                if (value != null && !value.isBlank()) {
                    nearbyText.add(value.trim());
                }
                continue;
            }

            // Person–person or free spatial relation
            if (fact.getTargetMention() != null) {
                String neighborName = value;
                if (sameReference(subjectName, neighborName) || isGeneric(neighborName)) {
                    continue;
                }
                if (isPersonMention(fact.getTargetMention())) {
                    neighbors.add(neighborName);
                }
                addRelation(relations, relationKeys, neighborName, action, statement);
            } else if (value != null && !value.isBlank() && !action.isBlank()) {
                // Free action with object (e.g. trzyma nóż)
                if (!sameReference(subjectName, value) && !isGeneric(value)) {
                    // A free relation is not evidence that its object is being held.
                    addRelation(relations, relationKeys, value, action, statement);
                }
            } else if (!action.isBlank()) {
                actions.add(action);
            }
        }

        // Remaining context objects not already classified as held via facts.
        for (String obj : contextObjects) {
            String resolved = canonicalizeReference(obj, canonicalLabels);
            if (resolved != null && !resolved.isBlank() && !isGeneric(resolved)
                    && !sameReference(subjectName, resolved)
                    && !held.contains(resolved) && !nearby.contains(resolved)) {
                nearby.add(resolved);
            }
        }

        putStringArray(person, "appearance", appearance);
        putStringArray(person, "actions", actions);
        putStringArray(person, "held_objects", held);
        putStringArray(person, "nearby_objects", nearby);
        putStringArray(person, "nearby_text", nearbyText);
        putStringArray(person, "neighbors", neighbors);
        person.set("relations", relations);
        return person;
    }

    private static void addRelation(
            ArrayNode relations, Set<String> keys, String to, String relation, String statement) {
        if (to == null || to.isBlank() || relation == null || relation.isBlank()) {
            return;
        }
        String key = (relation + "|" + to).toLowerCase(Locale.ROOT);
        if (!keys.add(key)) {
            return;
        }
        ObjectNode rel = MAPPER.createObjectNode();
        rel.put("to", clean(to));
        rel.put("relation", clean(FactStatementRewriter.readableAction(relation)));
        if (statement != null && !statement.isBlank()) {
            rel.put("statement", clean(statement));
        }
        relations.add(rel);
    }

    private static String resolveFactValue(Fact fact, Map<UUID, EntityMention> peopleById) {
        if (fact.getTargetMention() != null) {
            EntityMention target = fact.getTargetMention();
            if (target.getId() != null && peopleById.containsKey(target.getId())) {
                return displayName(peopleById.get(target.getId()));
            }
            String name = displayName(target);
            if (isGeneric(name)) {
                return "nierozpoznana osoba";
            }
            return name;
        }
        if (fact.getObject() != null && !fact.getObject().isBlank()) {
            return clean(fact.getObject());
        }
        return "";
    }

    private static Map<String, String> canonicalLabels(List<EntityMention> people) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (EntityMention person : people) {
            String canonical = clean(displayName(person));
            putCanonicalLabel(labels, person.getVisionLabel(), canonical);
            putCanonicalLabel(labels, person.getLabel(), canonical);
            putCanonicalLabel(labels, canonical, canonical);
        }
        return labels;
    }

    private static void putCanonicalLabel(Map<String, String> labels, String source, String canonical) {
        if (source == null || source.isBlank() || canonical == null || canonical.isBlank()) {
            return;
        }
        labels.putIfAbsent(clean(source).toLowerCase(Locale.ROOT), canonical);
    }

    private static String canonicalizeReference(String value, Map<String, String> labels) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String cleaned = clean(value);
        return labels.getOrDefault(cleaned.toLowerCase(Locale.ROOT), cleaned);
    }

    private static boolean sameReference(String left, String right) {
        return left != null && right != null && clean(left).equalsIgnoreCase(clean(right));
    }

    /** Preserve raw vision labels and add the resolved name beside them for semantic retrieval. */
    private static JsonNode enrichVisionWithCanonicalNames(JsonNode vision, Map<String, String> labels) {
        if (vision == null || !vision.isObject() || labels.isEmpty()) {
            return vision;
        }
        ObjectNode enriched = ((ObjectNode) vision).deepCopy();
        JsonNode entities = enriched.get("entities");
        if (entities != null && entities.isArray()) {
            for (JsonNode entity : entities) {
                if (entity != null && entity.isObject()) {
                    String canonical = canonicalizeReference(entity.path("label").asText(""), labels);
                    if (!canonical.isBlank() && !sameReference(canonical, entity.path("label").asText(""))) {
                        ((ObjectNode) entity).put("canonical_name", canonical);
                    }
                }
            }
        }
        JsonNode relations = enriched.get("relations");
        if (relations != null && relations.isArray()) {
            for (JsonNode relation : relations) {
                if (relation == null || !relation.isObject()) {
                    continue;
                }
                ObjectNode object = (ObjectNode) relation;
                addCanonicalRelationName(object, "subject_label", "subject_name", labels);
                addCanonicalRelationName(object, "object_label", "object_name", labels);
            }
        }
        return enriched;
    }

    private static void addCanonicalRelationName(
            ObjectNode relation, String labelField, String nameField, Map<String, String> labels) {
        String original = relation.path(labelField).asText("");
        String canonical = canonicalizeReference(original, labels);
        if (!canonical.isBlank() && !sameReference(original, canonical)) {
            relation.put(nameField, canonical);
        }
    }

    private static List<EntityMention> filterPeople(List<EntityMention> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        List<EntityMention> out = new ArrayList<>();
        for (EntityMention m : mentions) {
            if (m == null || m.getStatus() != MentionStatus.CONFIRMED || !isPersonMention(m)) {
                continue;
            }
            out.add(m);
        }
        return out;
    }

    private static boolean isPersonMention(EntityMention mention) {
        if (mention == null) {
            return false;
        }
        if (mention.getEntity() != null && mention.getEntity().getType() != null) {
            return "PERSON".equalsIgnoreCase(mention.getEntity().getType());
        }
        return mention.getEntityType() == null || "PERSON".equalsIgnoreCase(mention.getEntityType());
    }

    private static boolean isGeneric(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("person ")
                || lower.startsWith("osoba ")
                || lower.equals("uczestnik")
                || lower.equals("unknown")
                || lower.equals("nierozpoznana osoba");
    }

    private static String displayName(EntityMention mention) {
        if (mention == null) {
            return "uczestnik";
        }
        if (mention.getEntity() != null && mention.getEntity().getDisplayName() != null
                && !mention.getEntity().getDisplayName().isBlank()) {
            return clean(mention.getEntity().getDisplayName());
        }
        if (mention.getLabel() != null && !mention.getLabel().isBlank()) {
            return clean(mention.getLabel());
        }
        return "uczestnik";
    }

    private static void putStringArray(ObjectNode node, String field, Set<String> values) {
        ArrayNode arr = MAPPER.createArrayNode();
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.isBlank()) {
                    arr.add(clean(v));
                }
            }
        }
        node.set(field, arr);
    }

    private static List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = clean(raw);
        try {
            if (trimmed.startsWith("[")) {
                JsonNode arr = MAPPER.readTree(trimmed);
                if (!arr.isArray()) {
                    return List.of();
                }
                List<String> out = new ArrayList<>();
                arr.forEach(n -> {
                    String v = clean(n.asText(""));
                    if (!v.isBlank()) {
                        out.add(v);
                    }
                });
                return out;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return List.of(trimmed);
    }

    private static ArrayNode parseStringArrayNode(String raw) {
        List<String> list = parseStringList(raw);
        if (list.isEmpty()) {
            return null;
        }
        ArrayNode arr = MAPPER.createArrayNode();
        list.forEach(arr::add);
        return arr;
    }

    private static JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(clean(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonNode recoverVisionPayload(FileEntity file) {
        JsonNode structured = parseVisionPayload(file.getStructuredVisionContext());
        return structured != null ? structured : parseVisionPayload(file.getImageSummary());
    }

    private static JsonNode parseVisionPayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = clean(raw);
        String json = StructuredVisionExtractor.extractJsonObject(normalized);
        if (!looksLikeJson(json)) {
            return null;
        }
        JsonNode parsed = parseJson(json);
        if (parsed == null) {
            parsed = parseJson(StructuredVisionExtractor.repairCommonVisionJsonErrors(json));
        }
        return parsed != null && parsed.isObject() ? parsed : null;
    }

    private static ArrayNode normalizeVisibleTexts(JsonNode values) {
        ArrayNode normalized = MAPPER.createArrayNode();
        for (JsonNode value : values) {
            if (value.isTextual()) {
                normalized.add(clean(value.asText()));
            } else if (value.isObject()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static String textValue(JsonNode object, String field) {
        if (object == null || !object.hasNonNull(field)) {
            return null;
        }
        String value = clean(object.get(field).asText(""));
        return value.isBlank() ? null : value;
    }

    private static boolean looksLikeJson(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static String clean(String value) {
        return value == null ? null : Utf8MojibakeRepair.repair(value).trim();
    }

}
