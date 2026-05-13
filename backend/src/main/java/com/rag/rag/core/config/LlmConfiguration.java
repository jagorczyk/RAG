package com.rag.rag.core.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class LlmConfiguration {

    @Value("${ollama.base.url}")
    private String BASE_URL;

    @Value("${chat.language.model}")
    private String TEXT_MODEL;

    @Value("${vision.language.model}")
    private String VISION_MODEL;

    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    @Value("${llm.openai.base-url}")
    private String OPENAI_BASE_URL;

    @Value("${llm.openai.chat-model}")
    private String OPENAI_CHAT_MODEL;

    @Value("${llm.openai.vision-base-url}")
    private String OPENAI_VISION_BASE_URL;

    @Value("${llm.openai.vision-model}")
    private String OPENAI_VISION_MODEL;

    @Value("${llm.timeout-minutes:2}")
    private int TIMEOUT_MINUTES;

    @Value("${llm.temperature:0.1}")
    private double TEMPERATURE;


    @Bean("chatLanguageModel")
    @Primary
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
    public ChatLanguageModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(TEXT_MODEL)
                .timeout(Duration.ofMinutes(10))
                .temperature(TEMPERATURE)
                .build();
    }

    @Bean("visionModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
    public ChatLanguageModel ollamaVisionModel() {
        return OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(VISION_MODEL)
                .timeout(Duration.ofMinutes(10))
                .temperature(0.0)
                .build();
    }


    @Bean("chatLanguageModel")
    @Primary
    @ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
    public ChatLanguageModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(OPENAI_BASE_URL)
                .apiKey(OPENAI_API_KEY)
                .modelName(OPENAI_CHAT_MODEL)
                .temperature(TEMPERATURE)
                .maxTokens(512)
                .timeout(Duration.ofMinutes(TIMEOUT_MINUTES))
                .build();
    }

    @Bean("visionModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
    public ChatLanguageModel openAiVisionModel() {
        return OpenAiChatModel.builder()
                .baseUrl(OPENAI_VISION_BASE_URL)
                .apiKey(OPENAI_API_KEY)
                .modelName(OPENAI_VISION_MODEL)
                .timeout(Duration.ofMinutes(TIMEOUT_MINUTES))
                .temperature(0.0)
                .build();
    }
}
