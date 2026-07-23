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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(root.has("file"));
        assertFalse(root.has("path"));
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

    @Test
    void recoversMalformedVisionPayloadAndRepairsPolishWithoutLosingConfirmedPerson() throws Exception {
        String brokenVision = "{\"entities\":[{\"label\":\"person 1\",\"type\":\"PERSON\","
                + "\"actions\":[\"u\u00C5\u009Bmiecha si\u00C4\u0099\"],"
                + "\"visual_cues\":[\"kr\u00C3\u00B3tkie blond w\u00C5\u0082osy\"]},"
                + "\"label\":\"kurtka\",\"type\":\"CLOTHING\"}],"
                + "\"relations\":[],\"scene\":\"pok\u00C3\u00B3j z szafkami\","
                + "\"scene_summary\":\"M\u00C4\u0099\u00C5\u00BCczyzna podnosi r\u00C4\u0099k\u00C4\u0099.\","
                + "\"background\":[\"bia\u0142a pokrywka\"]}";
        KnowledgeEntity igorEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Igor").type("PERSON").build();
        EntityMention igor = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(igorEntity)
                .label("Igor")
                .entityType("PERSON")
                .status(MentionStatus.CONFIRMED)
                .filePath("dir://dupek/20230417_115245.jpg")
                .build();
        FileEntity file = FileEntity.builder()
                .path("dir://dupek/20230417_115245.jpg")
                .fileName("20230417_115245.jpg")
                .imageSummary(brokenVision)
                .build();

        String json = ImageEmbeddingDocumentBuilder.build(file, List.of(igor), List.of());
        JsonNode root = mapper.readTree(json);

        assertEquals("pok\u00F3j z szafkami", root.path("scene").path("label").asText());
        assertEquals("M\u0119\u017Cczyzna podnosi r\u0119k\u0119.", root.path("scene").path("summary").asText());
        assertEquals("Igor", root.path("participants").get(0).path("name").asText());
        assertEquals(2, root.path("vision_observations").path("entities").size());
        assertTrue(json.contains("kr\u00F3tkie blond w\u0142osy"));
        assertTrue(json.contains("bia\u0142a pokrywka"));
        assertTrue(!json.contains("\u00C3") && !json.contains("\u00C4") && !json.contains("\u00C5"));
    }

    @Test
    void removesTechnicalAnchorsAndDoesNotTurnSpatialRelationsIntoHeldObjectsOrSelfRelations()
            throws Exception {
        KnowledgeEntity igorEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Igor").type("PERSON").build();
        KnowledgeEntity marioEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Mario").type("PERSON").build();
        EntityMention igor = EntityMention.builder()
                .id(UUID.randomUUID()).entity(igorEntity).label("Igor").visionLabel("person 1")
                .entityType("PERSON").status(MentionStatus.CONFIRMED)
                .contextObjects("[\"budynek\"]").filePath("dir://photo.jpeg").build();
        EntityMention mario = EntityMention.builder()
                .id(UUID.randomUUID()).entity(marioEntity).label("Mario").visionLabel("person 2")
                .entityType("PERSON").status(MentionStatus.CONFIRMED)
                .filePath("dir://photo.jpeg").build();
        Fact buildingRelation = Fact.builder()
                .id(UUID.randomUUID()).mention(igor).action("obok").object("budynek")
                .statementPl("Igor obok budynek.").filePath("dir://photo.jpeg").build();
        Fact brokenSelfRelation = Fact.builder()
                .id(UUID.randomUUID()).mention(mario).action("obok").object("Mario")
                .statementPl("Mario obok Mario.").filePath("dir://photo.jpeg").build();
        FileEntity file = FileEntity.builder()
                .path("dir://photo.jpeg").fileName("photo.jpeg")
                .imageSummary("Grupa stoi przed budynkiem. Etykieta face_1 jest nad twarzą.")
                .visibleTexts("[{\"text\":\"face_1\"}]")
                .structuredVisionContext("""
                        {"entities":[
                          {"label":"person 1","type":"PERSON","face_anchor_id":"face_1",
                           "visual_cues":["szara marynarka"],"nearby_text":["face_1"]},
                          {"label":"person 2","type":"PERSON","face_anchor_id":"face_2"}
                        ],"relations":[],"scene_summary":"Grupa stoi przed budynkiem.",
                        "visible_texts":[{"text":"face_1"}]}
                        """)
                .build();

        JsonNode root = mapper.readTree(ImageEmbeddingDocumentBuilder.build(
                file, List.of(igor, mario), List.of(buildingRelation, brokenSelfRelation)));

        String json = root.toString();
        assertFalse(json.contains("face_1"));
        assertFalse(json.contains("face_2"));
        assertFalse(root.has("file"));
        assertFalse(root.has("path"));
        assertEquals("Igor", root.path("vision_observations").path("entities")
                .get(0).path("canonical_name").asText());

        JsonNode igorNode = participant(root, "Igor");
        JsonNode marioNode = participant(root, "Mario");
        assertTrue(igorNode.path("held_objects").isEmpty());
        assertTrue(igorNode.path("nearby_objects").toString().contains("budynek"));
        assertTrue(igorNode.path("relations").toString().contains("budynek"));
        assertTrue(marioNode.path("relations").isEmpty());
        assertFalse(json.contains("Mario obok Mario"));
    }

    private static JsonNode participant(JsonNode root, String name) {
        for (JsonNode participant : root.path("participants")) {
            if (name.equals(participant.path("name").asText())) {
                return participant;
            }
        }
        throw new AssertionError("Missing participant " + name);
    }
}
