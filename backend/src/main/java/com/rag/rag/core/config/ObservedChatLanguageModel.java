package com.rag.rag.core.config;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Measures real provider calls without changing prompts, evidence or model responses.
 * The fixed role/model tags make control-vs-answer A/B comparisons visible through Actuator.
 */
public final class ObservedChatLanguageModel implements ChatLanguageModel {

    public static final String METRIC_NAME = "rag.llm.inference";

    private final ChatLanguageModel delegate;
    private final MeterRegistry meterRegistry;
    private final String provider;
    private final String role;
    private final String modelName;

    public ObservedChatLanguageModel(ChatLanguageModel delegate,
                                     MeterRegistry meterRegistry,
                                     String provider,
                                     String role,
                                     String modelName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.provider = normalized(provider);
        this.role = normalized(role);
        this.modelName = normalized(modelName);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return observe(() -> delegate.generate(messages));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages,
                                        List<ToolSpecification> toolSpecifications) {
        return observe(() -> delegate.generate(messages, toolSpecifications));
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return observe(() -> delegate.chat(request));
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private <T> T observe(Supplier<T> inference) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return inference.get();
        } catch (RuntimeException | Error failure) {
            outcome = "error";
            throw failure;
        } finally {
            Timer timer = Timer.builder(METRIC_NAME)
                    .description("Latency of a single language-model provider call")
                    .tag("provider", provider)
                    .tag("role", role)
                    .tag("model", modelName)
                    .tag("outcome", outcome)
                    .register(meterRegistry);
            sample.stop(timer);
        }
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
