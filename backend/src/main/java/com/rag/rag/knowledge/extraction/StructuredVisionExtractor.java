package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class StructuredVisionExtractor {

    @Value("${vision.structured.prompt}")
    private String structuredPrompt;

    private final ChatLanguageModel visionModel;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public StructuredVisionExtractor(@Qualifier("visionModel") ChatLanguageModel visionModel) {
        this.visionModel = visionModel;
    }

    public ExtractionResult extract(String base64Image, String mimeType) {
        return extract(base64Image, mimeType, Set.of());
    }

    public ExtractionResult extract(String base64Image, String mimeType, Set<String> faceAnchors) {
        String anchorInstruction = faceAnchors == null || faceAnchors.isEmpty() ? "" : """

                The image contains magenta face boxes labelled with these technical ids: %s.
                For every PERSON whose face is inside a labelled box, copy that exact id to face_anchor_id.
                Never attach an id to another participant and never invent an id. Keep labels such as person 1;
                face_anchor_id is a separate technical field. Write all natural-language observations in Polish.
                """.formatted(faceAnchors);
        UserMessage message = UserMessage.from(
                TextContent.from(structuredPrompt + anchorInstruction),
                ImageContent.from(base64Image, normalizeImageMimeType(mimeType))
        );

        String responseText = visionModel.generate(message).content().text();
        log.debug("Vision model response length={}", responseText == null ? 0 : responseText.length());

        ExtractionResult parsed = parseResponse(responseText);
        if (parsed.isStructured()) {
            return parsed;
        }
        // One shorter retry — models sometimes wrap prose around JSON on the first attempt.
        log.warn("Vision JSON parse failed; retrying with compact instruction");
        UserMessage retry = UserMessage.from(
                TextContent.from("""
                        Return ONLY one JSON object (no markdown, no commentary) matching this shape:
                        {"entities":[{"label":"person 1","type":"PERSON","actions":[],"objects":[],"nearby_objects":[],
                        "visual_cues":[],"bbox":[]}],"relations":[],"scene":"","scene_summary":"",
                        "background":[],"setting":"","lighting":"","visible_texts":[]}
                        Describe the image exhaustively in Polish field values. Face anchors: %s
                        """.formatted(faceAnchors == null ? Set.of() : faceAnchors)),
                ImageContent.from(base64Image, normalizeImageMimeType(mimeType))
        );
        String retryText = visionModel.generate(retry).content().text();
        ExtractionResult retryParsed = parseResponse(retryText);
        if (retryParsed.isStructured()) {
            return retryParsed;
        }
        // Prefer the longer raw text for embedding fallback.
        String raw = responseText != null && responseText.length() >= (retryText == null ? 0 : retryText.length())
                ? responseText : retryText;
        return new ExtractionResult(null, raw, false);
    }

    private ExtractionResult parseResponse(String text) {
        if (text == null || text.isBlank()) {
            return new ExtractionResult(null, text, false);
        }
        try {
            String jsonContent = extractJsonObject(text);
            VisionResultDto dto = objectMapper.readValue(jsonContent, VisionResultDto.class);
            if (dto == null) {
                return new ExtractionResult(null, text, false);
            }
            // Accept even if entities empty but scene_summary present — still useful graph/embed text.
            return new ExtractionResult(dto, text, true);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse vision response to JSON: {}", e.getMessage());
            return new ExtractionResult(null, text, false);
        }
    }

    /** Prefer fenced ```json, else first balanced {...} object. */
    static String extractJsonObject(String text) {
        if (text == null) {
            return "{}";
        }
        String jsonContent = text.trim();
        if (jsonContent.contains("```json")) {
            int start = jsonContent.indexOf("```json") + 7;
            int end = jsonContent.indexOf("```", start);
            if (end > start) {
                return jsonContent.substring(start, end).trim();
            }
        }
        if (jsonContent.contains("```")) {
            int start = jsonContent.indexOf("```") + 3;
            int end = jsonContent.indexOf("```", start);
            if (end > start) {
                String inner = jsonContent.substring(start, end).trim();
                if (inner.startsWith("{")) {
                    return inner;
                }
            }
        }
        int start = jsonContent.indexOf('{');
        int end = jsonContent.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return jsonContent.substring(start, end + 1);
        }
        return jsonContent;
    }

    private String normalizeImageMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return "image/jpeg";
        String normalized = mimeType.toLowerCase(Locale.ROOT).trim();
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    public record ExtractionResult(VisionResultDto resultDto, String rawText, boolean isStructured) {}
}
