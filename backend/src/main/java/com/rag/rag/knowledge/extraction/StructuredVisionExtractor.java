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
        String jsonContent = extractJsonObject(text);
        VisionResultDto dto = tryReadDto(jsonContent);
        if (dto == null) {
            String repaired = repairCommonVisionJsonErrors(jsonContent);
            if (!repaired.equals(jsonContent)) {
                log.info("Applied vision JSON repair (len {} → {})", jsonContent.length(), repaired.length());
                dto = tryReadDto(repaired);
            }
        }
        if (dto == null) {
            log.warn("Failed to parse vision response to JSON (raw len={})", text.length());
            return new ExtractionResult(null, text, false);
        }
        // Accept even if entities empty but scene_summary present — still useful graph/embed text.
        return new ExtractionResult(dto, text, true);
    }

    private VisionResultDto tryReadDto(String jsonContent) {
        try {
            return objectMapper.readValue(jsonContent, VisionResultDto.class);
        } catch (JsonProcessingException e) {
            log.debug("Vision JSON not yet parseable: {}", e.getMessage());
            return null;
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

    /**
     * Models often close the entity object one field too early, e.g.
     * {@code "nearby_text":["VW"]},"bbox":[...]} instead of
     * {@code "nearby_text":["VW"],"bbox":[...]}.
     * That yields invalid JSON and historically marked graph_projection FAILED
     * even though vision_analysis_status stayed COMPLETED.
     */
    static String repairCommonVisionJsonErrors(String json) {
        if (json == null || json.isBlank()) {
            return json == null ? "" : json;
        }
        String fixed = json;
        // `],"bbox":` wrongly written as `]},"bbox":` (object closed before bbox field).
        fixed = fixed.replaceAll("(\\])\\s*\\}\\s*,\\s*\"bbox\"\\s*:", "$1,\"bbox\":");
        // Same pattern for other late fields sometimes emitted after a premature close.
        fixed = fixed.replaceAll(
                "(\\])\\s*\\}\\s*,\\s*\"(face_anchor_id|visual_cues|nearby_objects|nearby_text|actions|objects)\"\\s*:",
                "$1,\"$2\":");
        // Trailing commas before } or ]
        fixed = fixed.replaceAll(",\\s*}", "}");
        fixed = fixed.replaceAll(",\\s*]", "]");
        return fixed;
    }

    private String normalizeImageMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return "image/jpeg";
        String normalized = mimeType.toLowerCase(Locale.ROOT).trim();
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    public record ExtractionResult(VisionResultDto resultDto, String rawText, boolean isStructured) {}
}
