package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StructuredVisionExtractor {

    @Value("${vision.structured.prompt}")
    private String structuredPrompt;

    private final ChatLanguageModel visionModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StructuredVisionExtractor(@Qualifier("visionModel") ChatLanguageModel visionModel) {
        this.visionModel = visionModel;
    }

    public ExtractionResult extract(String base64Image, String mimeType) {
        UserMessage message = UserMessage.from(
                TextContent.from(structuredPrompt),
                ImageContent.from(base64Image, mimeType)
        );

        String responseText = visionModel.generate(message).content().text();
        log.debug("Vision model response: {}", responseText);

        return parseResponse(responseText);
    }

    private ExtractionResult parseResponse(String text) {
        try {
            String jsonContent = text;
            if (jsonContent.contains("```json")) {
                int start = jsonContent.indexOf("```json") + 7;
                int end = jsonContent.lastIndexOf("```");
                if (start < end) {
                    jsonContent = jsonContent.substring(start, end).trim();
                }
            } else if (jsonContent.contains("```")) {
                int start = jsonContent.indexOf("```") + 3;
                int end = jsonContent.lastIndexOf("```");
                if (start < end) {
                    jsonContent = jsonContent.substring(start, end).trim();
                }
            }

            VisionResultDto dto = objectMapper.readValue(jsonContent, VisionResultDto.class);
            return new ExtractionResult(dto, text, true);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse vision response to JSON: {}", e.getMessage());
            return new ExtractionResult(null, text, false);
        }
    }

    public record ExtractionResult(VisionResultDto resultDto, String rawText, boolean isStructured) {}
}
