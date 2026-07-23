package com.rag.rag.core.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservedChatLanguageModelTest {

    @Test
    void recordsSuccessfulInferenceByProviderRoleAndModel() {
        ChatLanguageModel delegate = mock(ChatLanguageModel.class);
        when(delegate.generate(anyList())).thenReturn(Response.from(AiMessage.from("ok")));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChatLanguageModel observed = new ObservedChatLanguageModel(
                delegate, registry, "deepinfra", "control", "candidate/model");

        assertEquals("ok", observed.generate("test"));

        Timer timer = registry.find(ObservedChatLanguageModel.METRIC_NAME)
                .tags("provider", "deepinfra", "role", "control",
                        "model", "candidate/model", "outcome", "success")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(TimeUnit.NANOSECONDS) >= 0);
    }

    @Test
    void recordsFailedInferenceAndRethrowsTheOriginalFailure() {
        ChatLanguageModel delegate = mock(ChatLanguageModel.class);
        when(delegate.generate(anyList())).thenThrow(new IllegalStateException("provider unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChatLanguageModel observed = new ObservedChatLanguageModel(
                delegate, registry, "deepinfra", "answer", "answer/model");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> observed.generate("test"));

        assertEquals("provider unavailable", failure.getMessage());
        Timer timer = registry.find(ObservedChatLanguageModel.METRIC_NAME)
                .tags("provider", "deepinfra", "role", "answer",
                        "model", "answer/model", "outcome", "error")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }
}
