package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vision entity. Models sometimes emit a bare label string instead of an object;
 * {@link TolerantDeserializer} accepts both shapes so ingest does not fail.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ExtractedEntityDto.TolerantDeserializer.class)
public class ExtractedEntityDto {
    private String label;
    private String type; // PERSON or ANIMAL
    private List<String> actions;

    private List<String> objects;

    @JsonProperty("nearby_objects")
    private List<String> nearbyObjects;

    @JsonProperty("nearby_text")
    private List<String> nearbyText;

    private List<Float> bbox;

    /** Technical face anchor rendered on the analysis copy, e.g. face_1. */
    @JsonProperty("face_anchor_id")
    private String faceAnchorId;

    @JsonProperty("visual_cues")
    private List<String> visualCues;

    static final class TolerantDeserializer extends JsonDeserializer<ExtractedEntityDto> {
        @Override
        public ExtractedEntityDto deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();
            JsonNode node = mapper.readTree(parser);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return fromLabel(node.asText("").trim());
            }
            if (!node.isObject()) {
                return null;
            }
            ExtractedEntityDto dto = new ExtractedEntityDto();
            dto.setLabel(text(node, "label"));
            dto.setType(text(node, "type"));
            dto.setFaceAnchorId(text(node, "face_anchor_id"));
            dto.setActions(stringList(node.get("actions")));
            dto.setObjects(stringList(node.get("objects")));
            dto.setNearbyObjects(stringList(node.get("nearby_objects")));
            dto.setNearbyText(stringList(node.get("nearby_text")));
            dto.setVisualCues(stringList(node.get("visual_cues")));
            dto.setBbox(floatList(node.get("bbox")));
            if ((dto.getType() == null || dto.getType().isBlank()) && dto.getLabel() != null) {
                dto.setType(inferType(dto.getLabel()));
            }
            return dto;
        }

        private static ExtractedEntityDto fromLabel(String label) {
            if (label == null || label.isBlank()) {
                return null;
            }
            ExtractedEntityDto dto = new ExtractedEntityDto();
            dto.setLabel(label);
            dto.setType(inferType(label));
            dto.setActions(List.of());
            dto.setObjects(List.of());
            dto.setVisualCues(List.of());
            return dto;
        }

        private static String inferType(String label) {
            String lower = label.toLowerCase(Locale.ROOT);
            if (lower.startsWith("animal") || lower.startsWith("zwierzę") || lower.startsWith("zwierze")) {
                return "ANIMAL";
            }
            return "PERSON";
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            String text = value.asText("").trim();
            return text.isEmpty() ? null : text;
        }

        private static List<String> stringList(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                String value = node.asText("").trim();
                return value.isEmpty() ? List.of() : List.of(value);
            }
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                if (item != null && item.isTextual()) {
                    String value = item.asText("").trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            });
            return values;
        }

        private static List<Float> floatList(JsonNode node) {
            if (node == null || !node.isArray()) {
                return null;
            }
            List<Float> values = new ArrayList<>();
            node.forEach(item -> {
                if (item != null && item.isNumber()) {
                    values.add(item.floatValue());
                }
            });
            return values;
        }
    }
}
