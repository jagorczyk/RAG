package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractedEntityDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldIgnoreLegacyObjectFieldsFromVisionResponse() throws Exception {
        String json = """
                {
                  "label": "person 1",
                  "type": "PERSON",
                  "actions": ["walking"],
                  "objects": ["phone"],
                  "visual_cues": ["blue shirt"]
                }
                """;

        ExtractedEntityDto entity = objectMapper.readValue(json, ExtractedEntityDto.class);

        assertEquals("PERSON", entity.getType());
        assertEquals("person 1", entity.getLabel());
        assertEquals(1, entity.getActions().size());
    }

    @Test
    void acceptsBareLabelStringAsEntity() throws Exception {
        ExtractedEntityDto entity = objectMapper.readValue("\"person 2\"", ExtractedEntityDto.class);

        assertEquals("person 2", entity.getLabel());
        assertEquals("PERSON", entity.getType());
    }

    @Test
    void visionResultAcceptsMixedEntityShapes() throws Exception {
        String json = """
                {
                  "entities": [
                    {"label":"person 1","type":"PERSON","actions":["stoi"],"objects":[],"visual_cues":[]},
                    "person 2",
                    "animal 1"
                  ],
                  "relations": [],
                  "scene": "park",
                  "scene_summary": "dwie osoby i pies",
                  "visible_texts": []
                }
                """;

        VisionResultDto result = objectMapper.readValue(json, VisionResultDto.class);

        assertEquals(3, result.getEntities().size());
        assertEquals("person 1", result.getEntities().get(0).getLabel());
        assertEquals("person 2", result.getEntities().get(1).getLabel());
        assertEquals("PERSON", result.getEntities().get(1).getType());
        assertEquals("animal 1", result.getEntities().get(2).getLabel());
        assertEquals("ANIMAL", result.getEntities().get(2).getType());
    }
}
