package com.rag.rag.core.config;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenizerConfiguration {

    @Bean
    Tokenizer tokenizer() {
        return new OpenAiTokenizer("gpt-4o-mini");
    }
}
