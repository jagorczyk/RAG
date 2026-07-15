package com.rag.rag.core.config;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfiguration {

    @Value("${ollama.base.url}")
    private String baseUrl;

    @Value("${embedding.model}")
    private String embeddingModel;

    @Value("${llm.deepinfra.base-url}")
    private String deepInfraBaseUrl;

    @Value("${llm.deepinfra.api-key}")
    private String deepInfraApiKey;

    @Value("${llm.deepinfra.embedding-model:Qwen/Qwen3-Embedding-8B}")
    private String deepInfraEmbeddingModel;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${rag.ingestion.max-segment-size:600}")
    private int ingestionSegmentSize;

    @Value("${rag.ingestion.overlap:100}")
    private int ingestionOverlap;

    @Bean
    @ConditionalOnProperty(name = "llm.embedding.provider", havingValue = "local")
    EmbeddingModel localEmbeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    @ConditionalOnProperty(name = "llm.embedding.provider", havingValue = "ollama", matchIfMissing = true)
    EmbeddingModel ollamaEmbeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(embeddingModel)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "llm.embedding.provider", havingValue = "deepinfra")
    EmbeddingModel deepInfraEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraEmbeddingModel)
                .build();
    }

    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            Tokenizer tokenizer
    ) {
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(ingestionSegmentSize, ingestionOverlap, tokenizer))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(
            @Value("${pgvector.host:localhost}") String host,
            @Value("${pgvector.port:5433}") int port,
            @Value("${pgvector.database:vector_db}") String database,
            EmbeddingModel embeddingModel
    ) {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(dbUser)
                .password(dbPassword)
                .table("embeddings")
                .dimension(embeddingModel.dimension())
                .useIndex(true)
                .indexListSize(100)
                .createTable(true)
                .build();
    }
}
