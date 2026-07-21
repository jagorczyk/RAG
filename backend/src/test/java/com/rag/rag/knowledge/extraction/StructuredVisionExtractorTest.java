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
    void extractJsonObjectStripsMarkdownFence() {
        String fenced = "```json\n{\"scene\":\"park\"}\n```";
        assertTrue(StructuredVisionExtractor.extractJsonObject(fenced).contains("\"scene\""));
    }
}
