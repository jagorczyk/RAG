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

    @Value("${llm.deepinfra.base-url}")
    private String deepInfraBaseUrl;

    @Value("${llm.deepinfra.api-key}")
    private String deepInfraApiKey;

    @Value("${llm.deepinfra.chat-model}")
    private String deepInfraChatModel;

    @Value("${llm.deepinfra.vision-model}")
    private String deepInfraVisionModel;

    @Value("${llm.chat.max-tokens:512}")
    private Integer chatMaxTokens;

    @Value("${llm.vision.max-tokens:2048}")
    private Integer visionMaxTokens;

    @Value("${llm.log-requests:false}")
    private boolean logRequests;

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
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepinfra")
    public ChatLanguageModel deepInfraChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraChatModel)
                .temperature(TEMPERATURE)
                .maxTokens(chatMaxTokens)
                .timeout(Duration.ofMinutes(TIMEOUT_MINUTES))
                .logRequests(logRequests)
                .build();
    }

    @Bean("visionModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepinfra")
    public ChatLanguageModel deepInfraVisionModel() {
        return OpenAiChatModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraVisionModel)
                .timeout(Duration.ofMinutes(TIMEOUT_MINUTES))
                .temperature(0.0)
                .maxTokens(visionMaxTokens)
                .logRequests(logRequests)
                .build();
    }
}
