package com.rag.rag.core.config;

import com.rag.rag.chat.service.ChatMemoryService;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfiguration {

    @Value("${rag.chat.memory.max-tokens:5000}")
    private int maxMemoryTokens;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryService chatMemoryService, Tokenizer tokenizer) {
        return memoryId -> TokenWindowChatMemory.builder()
                .id(memoryId)
                .maxTokens(maxMemoryTokens, tokenizer)
                .chatMemoryStore(chatMemoryService)
                .build();
    }
}
