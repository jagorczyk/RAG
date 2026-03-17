package com.rag.rag.Configuration;
import com.rag.rag.Service.ChatMemoryService;
import com.rag.rag.Service.ChatService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class AiConfiguration {

    @Value("${ollama.base.url}")
    private String BASE_URL;
    @Value("${chat.language.model}")
    private String TEXT_MODEL;
    @Value("${vision.language.model}")
    private String VISION_MODEL;
    @Value("${embedding.model}")
    private String EMBEDDING_MODEL;

    @Bean
    Tokenizer tokenizer() {
        return new OpenAiTokenizer("gpt-3.5-turbo");
    }

    @Bean
    @Primary
    ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(TEXT_MODEL)
                .timeout(Duration.ofMinutes(10))
                .numCtx(4096)
                .numPredict(1024)
                .temperature(0.1)
                .build();
    }

    @Bean("visionModel")
    ChatLanguageModel visionLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(VISION_MODEL)
                .timeout(Duration.ofMinutes(10))
                .numCtx(4096)
                .numPredict(512)
                .temperature(0.3)
                .build();
    }

    @Bean
    ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {

        return query -> {
            var queryEmbedding = embeddingModel.embed(query.text()).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.7)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

            return searchResult.matches().stream()
                    .map(match -> {
                        TextSegment segment = match.embedded();
                        Metadata metadata = segment.metadata().copy();
                        metadata.put("score", match.score());
                        return Content.from(TextSegment.from(segment.text(), metadata));
                    })
                    .collect(Collectors.toList());
        };
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever) {

        ContentInjector contentInjector = DefaultContentInjector.builder()
                .metadataKeysToInclude(List.of("path", "score", "filename", "document_id"))
                .build();

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .contentInjector(contentInjector)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryService chatMemoryService, Tokenizer tokenizer) {
        return memoryId -> TokenWindowChatMemory.builder()
                .id(memoryId)
                .maxTokens(2500, tokenizer)
                .chatMemoryStore(chatMemoryService)
                .build();
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(BASE_URL)
                .modelName(EMBEDDING_MODEL)
                .build();
    }

    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            Tokenizer tokenizer
    ) {
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(600, 100, tokenizer))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5433)
                .database("vector_db")
                .user("user")
                .password("password")
                .table("embeddings")
                .dimension(embeddingModel().dimension())
//larger datasets
                .useIndex(true)
                .indexListSize(100)
//
                .createTable(true)
//                .dropTableFirst(true)
                .build();
    }
}
