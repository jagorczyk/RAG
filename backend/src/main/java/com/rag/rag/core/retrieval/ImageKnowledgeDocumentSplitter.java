package com.rag.rag.core.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Splits canonical image knowledge at semantic boundaries. Every resulting embedding input is a
 * complete JSON object, so an entity or relation can never be cut in half by a character/token
 * splitter. Non-image documents retain the configured recursive splitting behavior.
 */
public final class ImageKnowledgeDocumentSplitter implements DocumentSplitter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DocumentSplitter fallback;

    public ImageKnowledgeDocumentSplitter(DocumentSplitter fallback) {
        this.fallback = fallback;
    }

    @Override
    public List<TextSegment> split(Document document) {
        ObjectNode root = parseImageKnowledge(document == null ? null : document.text());
        if (root == null) {
            return fallback.split(document);
        }

        List<SegmentPayload> payloads = new ArrayList<>();
        payloads.add(new SegmentPayload("scene", scenePayload(root)));

        JsonNode participants = root.get("participants");
        if (participants != null && participants.isArray()) {
            for (JsonNode participant : participants) {
                if (participant != null && participant.isObject()) {
                    ObjectNode payload = basePayload(root, "participant");
                    ArrayNode values = MAPPER.createArrayNode();
                    values.add(participant.deepCopy());
                    payload.set("participants", values);
                    payloads.add(new SegmentPayload("participant", payload));
                }
            }
        }

        JsonNode vision = root.get("vision_observations");
        if (vision != null && vision.isObject()) {
            addVisionItems(payloads, root, vision.path("entities"), "vision_entity", "entities");
            addVisionItems(payloads, root, vision.path("relations"), "vision_relation", "relations");
        }

        List<TextSegment> segments = new ArrayList<>(payloads.size());
        int index = 0;
        for (SegmentPayload payload : payloads) {
            Metadata metadata = document.metadata().copy();
            metadata.put("index", index++);
            metadata.put("segment_kind", payload.kind());
            segments.add(TextSegment.from(payload.json().toString(), metadata));
        }
        return List.copyOf(segments);
    }

    private static void addVisionItems(
            List<SegmentPayload> payloads, ObjectNode root, JsonNode items, String kind, String field) {
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            if (item == null || item.isNull()) {
                continue;
            }
            ObjectNode payload = basePayload(root, kind);
            ObjectNode observations = MAPPER.createObjectNode();
            ArrayNode values = MAPPER.createArrayNode();
            values.add(item.deepCopy());
            observations.set(field, values);
            payload.set("vision_observations", observations);
            payloads.add(new SegmentPayload(kind, payload));
        }
    }

    private static ObjectNode scenePayload(ObjectNode root) {
        ObjectNode payload = basePayload(root, "scene");
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if ("type".equals(field.getKey()) || "scene".equals(field.getKey())
                    || "participants".equals(field.getKey())
                    || "vision_observations".equals(field.getKey())
                    || "file".equals(field.getKey()) || "path".equals(field.getKey())) {
                continue;
            }
            payload.set(field.getKey(), field.getValue().deepCopy());
        }

        JsonNode vision = root.get("vision_observations");
        if (vision != null && vision.isObject()) {
            ObjectNode globalVision = MAPPER.createObjectNode();
            vision.fields().forEachRemaining(field -> {
                if (!"entities".equals(field.getKey()) && !"relations".equals(field.getKey())) {
                    globalVision.set(field.getKey(), field.getValue().deepCopy());
                }
            });
            if (!globalVision.isEmpty()) {
                payload.set("vision_observations", globalVision);
            }
        }
        return payload;
    }

    private static ObjectNode basePayload(ObjectNode root, String kind) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("type", "image_knowledge");
        payload.put("segment", kind);
        JsonNode scene = root.get("scene");
        if (scene != null) {
            payload.set("scene", scene.deepCopy());
        }
        return payload;
    }

    private static ObjectNode parseImageKnowledge(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = MAPPER.readTree(text);
            if (!parsed.isObject() || !"image_knowledge".equals(parsed.path("type").asText())) {
                return null;
            }
            return (ObjectNode) parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record SegmentPayload(String kind, ObjectNode json) {
    }
}
