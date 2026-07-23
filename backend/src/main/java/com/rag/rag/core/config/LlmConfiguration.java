package com.rag.rag.core.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class LlmConfiguration {

    @Value("${ollama.base.url:http://localhost:11434}")
    private String BASE_URL;

    @Value("${chat.language.model:llama3.1}")
    private String TEXT_MODEL;

    @Value("${vision.language.model:minicpm-v}")
    private String VISION_MODEL;

    @Value("${llm.deepinfra.base-url}")
    private String deepInfraBaseUrl;

    @Value("${llm.deepinfra.api-key}")
    private String deepInfraApiKey;

    @Value("${llm.deepinfra.control-model:${llm.deepinfra.chat-model}}")
    private String deepInfraControlModel;

    @Value("${llm.deepinfra.answer-model:${llm.deepinfra.chat-model}}")
    private String deepInfraAnswerModel;

    @Value("${llm.deepinfra.attribution-model:${llm.deepinfra.answer-model:${llm.deepinfra.chat-model}}}")
    private String deepInfraAttributionModel;

    @Value("${llm.deepinfra.vision-model}")
    private String deepInfraVisionModel;

    @Value("${llm.control.max-tokens:${llm.chat.max-tokens:512}}")
    private Integer chatMaxTokens;

    @Value("${llm.vision.max-tokens:2048}")
    private Integer visionMaxTokens;

    @Value("${llm.log-requests:false}")
    private boolean logRequests;

    @Value("${llm.timeout-seconds:30}")
    private int chatTimeoutSeconds;

    @Value("${llm.vision.timeout-seconds:120}")
    private int visionTimeoutSeconds;

    @Value("${llm.vision.max-retries:0}")
    private int visionMaxRetries;

    @Value("${llm.control.temperature:${llm.temperature:0.0}}")
    private double TEMPERATURE;

    @Value("${llm.answer.temperature:0.0}")
    private double answerTemperature;

    @Value("${llm.answer.max-tokens:768}")
    private Integer answerMaxTokens;

    @Value("${llm.attribution.max-tokens:512}")
    private Integer attributionMaxTokens;


    @Bean("chatLanguageModel")
    @Primary
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public ChatLanguageModel ollamaChatModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(TEXT_MODEL)
                .timeout(Duration.ofMinutes(10))
                .temperature(TEMPERATURE)
                .build();
        return observed(model, meterRegistry, "ollama", "control", TEXT_MODEL);
    }

    @Bean("structuredControlLanguageModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public ChatLanguageModel ollamaStructuredControlModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(TEXT_MODEL)
                .timeout(Duration.ofMinutes(10))
                .temperature(0.0)
                .build();
        return observed(model, meterRegistry, "ollama", "control_json", TEXT_MODEL);
    }

    @Bean("answerLanguageModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public ChatLanguageModel ollamaAnswerModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(TEXT_MODEL)
                .timeout(Duration.ofMinutes(10))
                .temperature(answerTemperature)
                .build();
        return observed(model, meterRegistry, "ollama", "answer", TEXT_MODEL);
    }

    @Bean("attributionLanguageModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public ChatLanguageModel ollamaAttributionModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(TEXT_MODEL)
                .timeout(Duration.ofMinutes(10))
                .temperature(0.0)
                .build();
        return observed(model, meterRegistry, "ollama", "attribution", TEXT_MODEL);
    }

    @Bean("visionModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public ChatLanguageModel ollamaVisionModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(VISION_MODEL)
                .timeout(Duration.ofMinutes(10))
                .temperature(0.0)
                .build();
        return observed(model, meterRegistry, "ollama", "vision", VISION_MODEL);
    }


    @Bean("chatLanguageModel")
    @Primary
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepinfra", matchIfMissing = true)
    public ChatLanguageModel deepInfraChatModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraControlModel)
                .temperature(TEMPERATURE)
                .maxTokens(chatMaxTokens)
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .logRequests(logRequests)
                .build();
        return observed(model, meterRegistry, "deepinfra", "control", deepInfraControlModel);
    }

    @Bean("structuredControlLanguageModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepinfra", matchIfMissing = true)
    public ChatLanguageModel deepInfraStructuredControlModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraControlModel)
                .temperature(0.0)
                .maxTokens(chatMaxTokens)
                .responseFormat("json_object")
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .logRequests(logRequests)
                .build();
        return observed(model, meterRegistry, "deepinfra", "control_json", deepInfraControlModel);
    }

    @Bean("answerLanguageModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepinfra", matchIfMissing = true)
    public ChatLanguageModel deepInfraAnswerModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraAnswerModel)
                .temperature(answerTemperature)
                .maxTokens(answerMaxTokens)
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .logRequests(logRequests)
                .build();
        return observed(model, meterRegistry, "deepinfra", "answer", deepInfraAnswerModel);
    }

    @Bean("attributionLanguageModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepinfra", matchIfMissing = true)
    public ChatLanguageModel deepInfraAttributionModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraAttributionModel)
                .temperature(0.0)
                .maxTokens(attributionMaxTokens)
                .responseFormat("json_object")
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .logRequests(logRequests)
                .build();
        return observed(model, meterRegistry, "deepinfra", "attribution", deepInfraAttributionModel);
    }

    @Bean("visionModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepinfra", matchIfMissing = true)
    public ChatLanguageModel deepInfraVisionModel(MeterRegistry meterRegistry) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraVisionModel)
                // Detailed structured image extraction is asynchronous and routinely
                // takes longer than an interactive chat response.
                .timeout(Duration.ofSeconds(visionTimeoutSeconds))
                .maxRetries(visionMaxRetries)
                .temperature(0.0)
                .maxTokens(visionMaxTokens)
                .logRequests(logRequests)
                .build();
        return observed(model, meterRegistry, "deepinfra", "vision", deepInfraVisionModel);
    }

    private static ChatLanguageModel observed(ChatLanguageModel delegate,
                                              MeterRegistry meterRegistry,
                                              String provider,
                                              String role,
                                              String modelName) {
        return new ObservedChatLanguageModel(delegate, meterRegistry, provider, role, modelName);
    }
}
