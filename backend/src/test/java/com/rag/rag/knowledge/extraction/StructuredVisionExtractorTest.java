package com.rag.rag.knowledge.extraction;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives real {@link StructuredVisionExtractor#extract}: structured JSON success and plain-text fallback.
 */
@ExtendWith(MockitoExtension.class)
class StructuredVisionExtractorTest {

    @Mock
    private ChatLanguageModel visionModel;

    private StructuredVisionExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new StructuredVisionExtractor(visionModel);
        ReflectionTestUtils.setField(extractor, "structuredPrompt", "Describe the image as JSON.");
    }

    private void stubVision(String responseText) {
        when(visionModel.generate(any(UserMessage.class)))
                .thenReturn(Response.from(AiMessage.from(responseText)));
    }

    @Test
    void extractParsesValidJsonIntoStructuredDto() {
        String json = """
                {
                  "entities": [
                    {
                      "label": "person 1",
                      "type": "PERSON",
                      "actions": ["je zupę"],
                      "objects": ["zupa"],
                      "visual_cues": ["czerwona koszulka"]
                    }
                  ],
                  "relations": [
                    {
                      "subject_label": "person 1",
                      "relation": "z lewej od",
                      "object_label": "person 2"
                    }
                  ],
                  "scene": "kuchnia",
                  "scene_summary": "osoba je zupę w kuchni",
                  "visible_texts": []
                }
                """;
        stubVision(json);

        StructuredVisionExtractor.ExtractionResult result = extractor.extract("YmFzZTY0", "image/jpeg");

        assertTrue(result.isStructured());
        assertNotNull(result.resultDto());
        assertEquals("kuchnia", result.resultDto().getScene());
        assertEquals("osoba je zupę w kuchni", result.resultDto().getSceneSummary());
        assertEquals(1, result.resultDto().getEntities().size());
        assertEquals("person 1", result.resultDto().getEntities().get(0).getLabel());
        assertEquals(List.of("je zupę"), result.resultDto().getEntities().get(0).getActions());
        assertEquals(1, result.resultDto().getRelations().size());
        assertEquals("z lewej od", result.resultDto().getRelations().get(0).getRelation());
        assertEquals(json, result.rawText());
    }

    @Test
    void extractStripsMarkdownJsonFence() {
        String inner = """
                {"entities":[{"label":"person 1","type":"PERSON"}],"scene":"park","scene_summary":"spacer"}
                """;
        String fenced = "```json\n" + inner.trim() + "\n```";
        stubVision(fenced);

        StructuredVisionExtractor.ExtractionResult result = extractor.extract("img", "image/png");

        assertTrue(result.isStructured());
        assertEquals("park", result.resultDto().getScene());
        assertEquals("person 1", result.resultDto().getEntities().get(0).getLabel());
    }

    @Test
    void extractFallsBackWhenResponseIsNotJson() {
        String plain = "Na zdjęciu widać mężczyznę w czerwonej koszulce jedzącego zupę.";
        stubVision(plain);

        StructuredVisionExtractor.ExtractionResult result = extractor.extract("img", "image/jpeg");

        assertFalse(result.isStructured());
        assertNull(result.resultDto());
        assertEquals(plain, result.rawText());
    }

    @Test
    void extractFallsBackWhenJsonIsMalformed() {
        String broken = "{ entities: not-valid-json";
        stubVision(broken);

        StructuredVisionExtractor.ExtractionResult result = extractor.extract("img", "image/jpeg");

        assertFalse(result.isStructured());
        assertNull(result.resultDto());
        assertEquals(broken, result.rawText());
    }

    @Test
    void extractNormalizesImageJpgMimeType() {
        stubVision("{\"entities\":[],\"scene\":\"x\",\"scene_summary\":\"y\"}");

        extractor.extract("YmFzZTY0", "image/jpg");

        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(visionModel).generate(captor.capture());
        // image/jpg is normalized to image/jpeg inside ImageContent
        assertTrue(captor.getValue().toString().contains("image/jpeg"));
    }

    @Test
    void extractUsesJpegDefaultWhenMimeBlank() {
        stubVision("{\"entities\":[],\"scene\":\"x\",\"scene_summary\":\"y\"}");

        StructuredVisionExtractor.ExtractionResult result = extractor.extract("YmFzZTY0", "  ");

        assertTrue(result.isStructured());
        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(visionModel).generate(captor.capture());
        assertTrue(captor.getValue().toString().contains("image/jpeg"));
    }

    @Test
    void repairFixesPrematureEntityCloseBeforeBbox() {
        // Live failure: model closes entity object before the bbox field.
        String broken = "{\"entities\":[{\"label\":\"person 1\",\"type\":\"PERSON\","
                + "\"nearby_text\":[\"VW\"]},\"bbox\":[0,190,1000,650]}],"
                + "\"relations\":[],\"scene\":\"auto\",\"scene_summary\":\"osoba w aucie\"}";
        assertTrue(broken.contains("\"VW\"]},\"bbox\":"));

        String fixed = StructuredVisionExtractor.repairCommonVisionJsonErrors(broken);
        assertFalse(fixed.contains("\"VW\"]},\"bbox\":"));
        assertTrue(fixed.contains("\"VW\"],\"bbox\":"));

        stubVision(broken);
        StructuredVisionExtractor.ExtractionResult result = extractor.extract("YmFzZTY0", "image/jpeg");
        assertTrue(result.isStructured(), "repaired vision JSON must project into graph");
        assertNotNull(result.resultDto());
        assertNotNull(result.resultDto().getEntities());
        assertFalse(result.resultDto().getEntities().isEmpty());
    }

    @Test
    void extractRepairsMojibakeAndMissingOpeningEntityObject() {
        String broken = "{\"entities\":[{\"label\":\"person 1\",\"type\":\"PERSON\","
                + "\"actions\":[\"u\u00C5\u009Bmiecha si\u00C4\u0099\"],\"nearby_text\":[\"53\"]},"
                + "\"label\":\"kurtka\",\"type\":\"CLOTHING\",\"visual_cues\":[\"niebieska\"]}],"
                + "\"relations\":[],\"scene\":\"pok\u00C3\u00B3j z szafkami\","
                + "\"scene_summary\":\"M\u00C4\u0099\u00C5\u00BCczyzna ma podniesion\u00C4\u0085 r\u00C4\u0099k\u00C4\u0099.\"}";
        stubVision(broken);

        StructuredVisionExtractor.ExtractionResult result = extractor.extract("img", "image/jpeg");

        assertTrue(result.isStructured());
        assertEquals(2, result.resultDto().getEntities().size());
        assertEquals("u\u015Bmiecha si\u0119", result.resultDto().getEntities().get(0).getActions().get(0));
        assertEquals("pok\u00F3j z szafkami", result.resultDto().getScene());
        assertEquals("M\u0119\u017Cczyzna ma podniesion\u0105 r\u0119k\u0119.", result.resultDto().getSceneSummary());
    }

    @Test
    void keepsFaceAnchorOnlyAsTechnicalJoinField() {
        String json = """
                {
                  "entities": [{
                    "label": "person 1",
                    "type": "PERSON",
                    "face_anchor_id": "face_1",
                    "actions": ["stoi", "face_1 patrzy w kamerę"],
                    "nearby_text": ["face_1", "WYJŚCIE"],
                    "visual_cues": ["czarna marynarka"]
                  }],
                  "relations": [],
                  "scene": "grupa na zewnątrz",
                  "scene_summary": "Na zdjęciu stoi grupa. Nad twarzą widać face_1.",
                  "visible_texts": [
                    {"text":"face_1","near_entity_label":"person 1"},
                    {"text":"WYJŚCIE"}
                  ]
                }
                """;
        stubVision(json);

        StructuredVisionExtractor.ExtractionResult result =
                extractor.extract("img", "image/jpeg", Set.of("face_1"));

        assertTrue(result.isStructured());
        assertEquals("face_1", result.resultDto().getEntities().get(0).getFaceAnchorId());
        assertEquals(List.of("stoi"), result.resultDto().getEntities().get(0).getActions());
        assertEquals(List.of("WYJŚCIE"), result.resultDto().getEntities().get(0).getNearbyText());
        assertEquals("Na zdjęciu stoi grupa.", result.resultDto().getSceneSummary());
        assertEquals(1, result.resultDto().getVisibleTexts().size());
        assertEquals("WYJŚCIE", result.resultDto().getVisibleTexts().get(0).getText());
    }

    @Test
    void removesFaceAnchorsFromUnstructuredSearchableFallback() {
        stubVision("Osoba oznaczona jako face_1 stoi przed budynkiem.");

        StructuredVisionExtractor.ExtractionResult result =
                extractor.extract("img", "image/jpeg", Set.of("face_1"));

        assertFalse(result.isStructured());
        assertFalse(result.rawText().contains("face_1"));
        assertTrue(result.rawText().contains("stoi przed budynkiem"));
    }

    @Test
    void extractJsonObjectStripsMarkdownFence() {
        String fenced = "```json\n{\"scene\":\"park\"}\n```";
        assertTrue(StructuredVisionExtractor.extractJsonObject(fenced).contains("\"scene\""));
    }
}
