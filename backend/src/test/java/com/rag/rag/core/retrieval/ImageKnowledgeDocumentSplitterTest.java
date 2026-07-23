package com.rag.rag.core.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageKnowledgeDocumentSplitterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ImageKnowledgeDocumentSplitter splitter = new ImageKnowledgeDocumentSplitter(
            document -> List.of(document.toTextSegment()));

    @Test
    void splitsImageJsonOnlyAtCompleteSemanticItems() throws Exception {
        String json = """
                {
                  "type":"image_knowledge",
                  "scene":{"summary":"Cztery osoby stoją przed budynkiem."},
                  "participants":[
                    {"name":"Igor","appearance":["biała koszula"],"relations":[]},
                    {"name":"Mario","appearance":["czarna koszula"],"relations":[]}
                  ],
                  "vision_observations":{
                    "scene_summary":"Cztery osoby stoją przed budynkiem.",
                    "entities":[
                      {"label":"person 1","canonical_name":"Igor"},
                      {"label":"person 2","canonical_name":"Mario"}
                    ],
                    "relations":[
                      {"subject_label":"person 1","relation":"obok","object_label":"person 2"}
                    ]
                  }
                }
                """;
        Document document = Document.from(json, Metadata.from("path", "dir://photo.jpeg"));

        List<TextSegment> segments = splitter.split(document);

        assertEquals(6, segments.size());
        int participants = 0;
        int entities = 0;
        int relations = 0;
        for (int index = 0; index < segments.size(); index++) {
            TextSegment segment = segments.get(index);
            JsonNode parsed = mapper.readTree(segment.text());
            assertEquals("image_knowledge", parsed.path("type").asText());
            assertTrue(parsed.has("scene"));
            assertEquals(index, segment.metadata().getInteger("index"));
            assertEquals("dir://photo.jpeg", segment.metadata().getString("path"));
            assertFalse(segment.text().contains("dir://photo.jpeg"));
            participants += parsed.path("participants").size();
            entities += parsed.path("vision_observations").path("entities").size();
            relations += parsed.path("vision_observations").path("relations").size();
        }
        assertEquals(2, participants);
        assertEquals(2, entities);
        assertEquals(1, relations);
    }

    @Test
    void delegatesNonImageDocumentsToConfiguredFallback() {
        Document text = Document.from("zwykły dokument");

        List<TextSegment> segments = splitter.split(text);

        assertEquals(1, segments.size());
        assertEquals("zwykły dokument", segments.get(0).text());
    }
}
