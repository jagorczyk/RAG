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
}
