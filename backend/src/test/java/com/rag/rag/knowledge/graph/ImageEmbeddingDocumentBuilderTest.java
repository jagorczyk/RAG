package com.rag.rag.knowledge.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.fact.FactStatementRewriter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageEmbeddingDocumentBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsStructuredJsonWithNeighborsAndRelations() throws Exception {
        KnowledgeEntity olekEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Olek").type("PERSON").build();
        KnowledgeEntity bartekEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Bartek").type("PERSON").build();
        EntityMention olek = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(olekEntity)
                .label("Olek")
                .entityType("PERSON")
                .status(MentionStatus.CONFIRMED)
                .visualCues("[\"czarna kurtka\"]")
                .filePath("dir://a.jpg")
                .build();
        EntityMention bartek = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(bartekEntity)
                .label("Bartek")
                .entityType("PERSON")
                .status(MentionStatus.CONFIRMED)
                .filePath("dir://a.jpg")
                .build();
        Fact holds = Fact.builder()
                .id(UUID.randomUUID())
                .mention(olek)
                .action("trzyma")
                .object("nóż")
                .statementPl("Olek trzyma nóż.")
                .filePath("dir://a.jpg")
                .confidence(new BigDecimal("0.9"))
                .build();
        Fact nextTo = Fact.builder()
                .id(UUID.randomUUID())
                .mention(olek)
                .targetMention(bartek)
                .action("z lewej od")
                .object("Bartek")
                .statementPl("Olek z lewej od Bartek.")
                .filePath("dir://a.jpg")
                .confidence(new BigDecimal("0.9"))
                .build();
        Fact appearance = Fact.builder()
                .id(UUID.randomUUID())
                .mention(olek)
                .action(FactStatementRewriter.ACTION_APPEARANCE)
                .object("krótkie włosy")
                .statementPl("Olek ma krótkie włosy.")
                .filePath("dir://a.jpg")
                .confidence(new BigDecimal("0.85"))
                .build();
        FileEntity file = FileEntity.builder()
                .path("dir://a.jpg")
                .fileName("a.jpg")
                .imageScene("samochód")
                .imageSummary("dwie osoby w aucie")
                .sceneAttributes("{\"setting\":\"wnętrze auta\",\"background\":[\"trawa\"]}")
                .build();

        String json = ImageEmbeddingDocumentBuilder.build(file, List.of(olek, bartek),
                List.of(holds, nextTo, appearance));
        JsonNode root = mapper.readTree(json);

        assertEquals("image_knowledge", root.path("type").asText());
        assertEquals("a.jpg", root.path("file").asText());
        assertEquals("samochód", root.path("scene").path("label").asText());
        assertEquals("wnętrze auta", root.path("scene").path("setting").asText());
        assertTrue(root.path("participants").isArray());
        assertEquals(2, root.path("participants").size());

        JsonNode olekNode = null;
        for (JsonNode p : root.path("participants")) {
            if ("Olek".equals(p.path("name").asText())) {
                olekNode = p;
            }
        }
        assertTrue(olekNode != null);
        assertTrue(olekNode.path("appearance").toString().contains("czarna kurtka")
                || olekNode.path("appearance").toString().contains("krótkie"));
        assertTrue(olekNode.path("neighbors").toString().contains("Bartek"));
        assertTrue(olekNode.path("relations").isArray());
        assertTrue(olekNode.path("relations").size() >= 1);
        String relDump = olekNode.path("relations").toString();
        assertTrue(relDump.contains("Bartek") || relDump.contains("nóż"));
        // Pretty-printed: multi-line, indented — human readable in the embeddings UI.
        assertTrue(json.contains("\n"), "JSON should contain newlines");
        assertTrue(json.contains("  "), "JSON should be indented");
    }

    @Test
    void formatReadablePrettyPrintsCompactJson() {
        String compact = "{\"type\":\"image_knowledge\",\"file\":\"x.jpg\",\"participants\":[]}";
        String pretty = ImageEmbeddingDocumentBuilder.formatReadable(compact);
        assertTrue(pretty.contains("\n"));
        assertTrue(pretty.contains("\"type\""));
        assertTrue(pretty.contains("image_knowledge"));
    }
}
