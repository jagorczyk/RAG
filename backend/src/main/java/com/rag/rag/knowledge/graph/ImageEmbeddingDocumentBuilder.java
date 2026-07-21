package com.rag.rag.knowledge.graph;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.MentionStatus;
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
 *   "file": "...",
 *   "path": "...",
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
        String trimmed = jsonOrText.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return jsonOrText;
        }
        try {
            JsonNode node = MAPPER.readTree(trimmed);
            return PRETTY.writeValueAsString(node);
        } catch (Exception e) {
            return jsonOrText;
        }
    }

    public static String build(FileEntity file, List<EntityMention> mentions, List<Fact> facts) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "image_knowledge");
        if (file != null) {
            String fileName = file.getFileName() != null ? file.getFileName() : fileNameFromPath(file.getPath());
            if (fileName != null && !fileName.isBlank()) {
                root.put("file", fileName);
            }
            if (file.getPath() != null && !file.getPath().isBlank()) {
                root.put("path", file.getPath());
            }
            root.set("scene", buildScene(file));
            ArrayNode texts = parseStringArrayNode(file.getVisibleTexts());
            if (texts != null && !texts.isEmpty()) {
                root.set("visible_texts", texts);
            }
        } else {
            root.set("scene", MAPPER.createObjectNode());
        }

        List<EntityMention> people = filterPeople(mentions);
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
            participants.add(buildParticipant(mention, factsByMention.getOrDefault(mention.getId(), List.of()), byId));
        }
        root.set("participants", participants);

        try {
            return PRETTY.writeValueAsString(root);
        } catch (Exception e) {
            // last resort: still try single-line JSON rather than Java object dump
            try {
                return MAPPER.writeValueAsString(root);
            } catch (Exception ignored) {
                return root.toString();
            }
        }
    }

    private static ObjectNode buildScene(FileEntity file) {
        ObjectNode scene = MAPPER.createObjectNode();
        if (file.getImageScene() != null && !file.getImageScene().isBlank()) {
            scene.put("label", file.getImageScene().trim());
        }
        if (file.getImageSummary() != null && !file.getImageSummary().isBlank()) {
            scene.put("summary", file.getImageSummary().trim());
        }
        JsonNode attrs = parseJson(file.getSceneAttributes());
        if (attrs != null && attrs.isObject()) {
            if (attrs.hasNonNull("setting") && !attrs.get("setting").asText("").isBlank()) {
                scene.put("setting", attrs.get("setting").asText().trim());
            }
            if (attrs.hasNonNull("lighting") && !attrs.get("lighting").asText("").isBlank()) {
                scene.put("lighting", attrs.get("lighting").asText().trim());
            }
            if (attrs.has("background") && attrs.get("background").isArray()) {
                scene.set("background", attrs.get("background"));
            }
        }
        return scene;
    }

    private static ObjectNode buildParticipant(
            EntityMention mention, List<Fact> mentionFacts, Map<UUID, EntityMention> peopleById) {
        ObjectNode person = MAPPER.createObjectNode();
        person.put("name", displayName(mention));
        String type = mention.getEntityType() != null ? mention.getEntityType() : "PERSON";
        person.put("type", type);

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
            String action = fact.getAction() == null ? "" : fact.getAction().trim();
            String actionUpper = action.toUpperCase(Locale.ROOT);
            String value = resolveFactValue(fact, peopleById);
            String statement = fact.getStatementPl();
            if (statement == null || statement.isBlank()) {
                statement = FactStatementRewriter.buildStatement(displayName(mention), action, value);
            }

            if (FactStatementRewriter.ACTION_APPEARANCE.equalsIgnoreCase(action)) {
                if (value != null && !value.isBlank()) {
                    appearance.add(value.trim());
                }
                continue;
            }
            if (FactStatementRewriter.ACTION_RELATED_OBJECT.equalsIgnoreCase(action)) {
                if (value != null && !value.isBlank()) {
                    held.add(value.trim());
                }
                addRelation(relations, relationKeys, value, "ma przy sobie", statement);
                continue;
            }
            if (FactStatementRewriter.ACTION_NEAR_OBJECT.equalsIgnoreCase(action)) {
                if (value != null && !value.isBlank()) {
                    nearby.add(value.trim());
                }
                addRelation(relations, relationKeys, value, "obok", statement);
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
                String neighborName = displayName(fact.getTargetMention());
                if (isPersonMention(fact.getTargetMention()) && !isGeneric(neighborName)) {
                    neighbors.add(neighborName);
                }
                addRelation(relations, relationKeys, neighborName, action, statement);
            } else if (value != null && !value.isBlank() && !action.isBlank()) {
                // Free action with object (e.g. trzyma nóż)
                if (looksLikeObjectValue(value)) {
                    held.add(value.trim());
                } else {
                    actions.add((action + " " + value).trim());
                }
                addRelation(relations, relationKeys, value, action, statement);
            } else if (!action.isBlank()) {
                actions.add(action);
            }
        }

        // Remaining context objects not already classified as held via facts.
        for (String obj : contextObjects) {
            if (!held.contains(obj) && !nearby.contains(obj)) {
                nearby.add(obj);
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
        rel.put("to", to.trim());
        rel.put("relation", FactStatementRewriter.readableAction(relation));
        if (statement != null && !statement.isBlank()) {
            rel.put("statement", statement.trim());
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
            return fact.getObject().trim();
        }
        return "";
    }

    private static List<EntityMention> filterPeople(List<EntityMention> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        List<EntityMention> out = new ArrayList<>();
        for (EntityMention m : mentions) {
            if (m == null || m.getStatus() == MentionStatus.REJECTED) {
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

    private static boolean looksLikeObjectValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        // Heuristic: multi-word person names are rare as fact.object without targetMention.
        return !Character.isUpperCase(value.trim().charAt(0)) || value.trim().contains(" ");
    }

    private static String displayName(EntityMention mention) {
        if (mention == null) {
            return "uczestnik";
        }
        if (mention.getEntity() != null && mention.getEntity().getDisplayName() != null
                && !mention.getEntity().getDisplayName().isBlank()) {
            return mention.getEntity().getDisplayName().trim();
        }
        if (mention.getLabel() != null && !mention.getLabel().isBlank()) {
            return mention.getLabel().trim();
        }
        return "uczestnik";
    }

    private static void putStringArray(ObjectNode node, String field, Set<String> values) {
        ArrayNode arr = MAPPER.createArrayNode();
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.isBlank()) {
                    arr.add(v.trim());
                }
            }
        }
        node.set(field, arr);
    }

    private static List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        try {
            if (trimmed.startsWith("[")) {
                JsonNode arr = MAPPER.readTree(trimmed);
                if (!arr.isArray()) {
                    return List.of();
                }
                List<String> out = new ArrayList<>();
                arr.forEach(n -> {
                    String v = n.asText("").trim();
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
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String fileNameFromPath(String path) {
        if (path == null) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
