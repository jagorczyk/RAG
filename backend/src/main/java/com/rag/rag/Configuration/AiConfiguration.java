package com.rag.rag.Configuration;
import com.rag.rag.Service.ChatMemoryService;
import com.rag.rag.Service.ChatService;
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
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;

@Configuration
public class AiConfiguration {

    static String OLLAMA_URL = "http://localhost:11434";
    static String OLLAMA_TEXT_MODEL = "llama3";
    static String OLLAMA_IMAGES_MODEL = "llava";

    @Bean
    Tokenizer tokenizer() {
        return new OpenAiTokenizer("gpt-3.5-turbo");
    }

    @Bean
    @Primary
    ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(OLLAMA_TEXT_MODEL)
                .timeout(Duration.ofMinutes(10))
                .numCtx(4096)
                .numPredict(1024)
                .temperature(0.1)
                .build();
    }

    @Bean("visionModel")
    ChatLanguageModel visionLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(OLLAMA_IMAGES_MODEL)
                .timeout(Duration.ofMinutes(10))
                .numCtx(4096)
                .numPredict(512)
                .temperature(0.3)
                .build();
    }

    @Bean
    ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever) {

        ContentInjector contentInjector = DefaultContentInjector.builder()
                .metadataKeysToInclude(List.of("path", "filename", "document_id"))
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
        //return new AllMiniLmL6V2EmbeddingModel();
        return OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("bge-m3")
                .build();
    }

    @Bean
    public String yoloModelPath() {
        return "C:/Users/mrigo/springboot/RAG/backend/models/yolo11x.onnx";
    }

    @Bean
    public String textModelPath() {
        return "C:/Users/mrigo/springboot/RAG/backend/models/DB_TD500_resnet50.onnx";
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
