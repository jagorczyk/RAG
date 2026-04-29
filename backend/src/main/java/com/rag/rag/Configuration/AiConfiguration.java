package com.rag.rag.Configuration;
import com.rag.rag.Service.ChatMemoryService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Configuration
public class AiConfiguration {

    private final JdbcTemplate jdbcTemplate;

    private static final int MAX_SEGMENT_CHARS = 400;

    public AiConfiguration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Value("${ollama.base.url}")
    private String BASE_URL;
    @Value("${chat.language.model}")
    private String TEXT_MODEL;
    @Value("${vision.language.model}")
    private String VISION_MODEL;
    @Value("${embedding.model}")
    private String EMBEDDING_MODEL;
    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    @Bean
    Tokenizer tokenizer() {
        return new OpenAiTokenizer("gpt-4o-mini");
    }

    @Bean
    @Primary
    ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .apiKey(OPENAI_API_KEY)
                .modelName("llama-3.3-70b-versatile")
                .temperature(0.1)
                .maxTokens(512)
                .timeout(Duration.ofMinutes(2))
                .build();
    }

    @Bean("visionModel")
    ChatLanguageModel visionLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(VISION_MODEL)
                .timeout(Duration.ofMinutes(10))
                .temperature(0.0)
                .build();
    }

    @Bean
    ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {

        return query -> {
            String queryText = query.text();
            System.out.println("RAG QUERY: " + queryText);

            if (queryText == null || queryText.trim().isEmpty()) {
                return Collections.emptyList();
            }

            String cleanQuery = queryText.replaceAll("@[^\\s,\\]]+", "").trim();
            if (cleanQuery.isEmpty()) cleanQuery = queryText;
            System.out.println("CLEAN QUERY: " + cleanQuery);

            var queryEmbedding = embeddingModel.embed(cleanQuery).content();
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder searchRequestBuilder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(5)      // zmniejszono z 10 - oszczędność tokenów
                    .minScore(0.70);

            List<String> mentions = new ArrayList<>();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@\"([^\"]+)\"|@([^\\s,\\]\\!\\?]+)").matcher(queryText);
            while (matcher.find()) {
                String mention = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(2).trim();
                mentions.add(mention);
                mentions.add(mention.toLowerCase());
            }

            if (!mentions.isEmpty()) {
                System.out.println("DETECTED MENTIONS: " + mentions);
                Filter pathFilter = metadataKey("path").isIn(mentions);
                Filter filenameFilter = metadataKey("filename").isIn(mentions);
                Filter folderFilter = metadataKey("document_id").isIn(mentions);
                searchRequestBuilder.filter(pathFilter.or(filenameFilter).or(folderFilter));
                searchRequestBuilder.minScore(0.01);
                searchRequestBuilder.maxResults(10); // zmniejszono z 30
            }

            EmbeddingSearchResult<TextSegment> vectorResults = embeddingStore.search(searchRequestBuilder.build());
            System.out.println("VECTOR RESULTS COUNT: " + vectorResults.matches().size());

            List<Content> keywordContents = new ArrayList<>();
            if (!mentions.isEmpty()) {
                try {
                    StringBuilder sql = new StringBuilder(
                            "SELECT text, metadata->>'path' as path, metadata->>'filename' as filename, metadata->>'document_id' as document_id " +
                                    "FROM embeddings WHERE 1=0"
                    );
                    List<Object> params = new ArrayList<>();

                    for (String mention : mentions) {
                        sql.append(" OR metadata->>'document_id' = ? OR metadata->>'filename' = ? OR metadata->>'path' ILIKE ?");
                        params.add(mention);
                        params.add(mention);
                        params.add("%" + mention + "%");
                    }
                    sql.append(" LIMIT 5"); // zmniejszono z 15

                    keywordContents.addAll(jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
                        String text = rs.getString("text");
                        String path = rs.getString("path");
                        String filename = rs.getString("filename");
                        String documentId = rs.getString("document_id");

                        Map<String, Object> map = new HashMap<>();
                        map.put("path", path != null ? path : "nieznana");
                        map.put("filename", filename != null ? filename : "Wynik tekstowy");
                        if (documentId != null) map.put("document_id", documentId);
                        map.put("score", "0.5");

                        return Content.from(TextSegment.from(text, Metadata.from(map)));
                    }));
                } catch (Exception e) {
                    System.err.println("Keyword search failed: " + e.getMessage());
                }
            }
            System.out.println("KEYWORD RESULTS COUNT: " + keywordContents.size());

            List<Content> combinedResults = new ArrayList<>(keywordContents);
            vectorResults.matches().stream()
                    .map(match -> {
                        TextSegment segment = match.embedded();
                        Map<String, Object> metadata = new HashMap<>(segment.metadata().toMap());
                        metadata.put("score", String.valueOf(match.score()));
                        System.out.println("Match: " + metadata.get("filename") + " Score: " + match.score());
                        return Content.from(TextSegment.from(segment.text(), Metadata.from(metadata)));
                    })
                    .forEach(combinedResults::add);

            return combinedResults.stream().limit(5).collect(Collectors.toList());
        };
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever, ChatLanguageModel chatLanguageModel) {
        ContentInjector contentInjector = (contents, query) -> {
            System.out.println("CONTENT INJECTOR RECEIVED CONTENTS COUNT: " + contents.size());
            String userQuestion = query.singleText();
            if (userQuestion == null || userQuestion.trim().isEmpty()) userQuestion = "Pytanie";

            if (contents.isEmpty()) return UserMessage.from(userQuestion);

            String contextJoined = contents.stream()
                    .map(content -> {
                        TextSegment segment = content.textSegment();
                        String filename = segment.metadata().getString("filename");
                        String folderName = segment.metadata().getString("document_id");

                        // Ucinamy długie teksty segmentów - główna przyczyna przekraczania limitu tokenów
                        String text = segment.text();
                        if (text.length() > MAX_SEGMENT_CHARS) {
                            text = text.substring(0, MAX_SEGMENT_CHARS) + "...";
                        }

                        // Skrócony format (bez PATH) - oszczędność tokenów
                        return String.format("DOC: %s (folder: %s)\n%s",
                                filename != null ? filename : "unknown",
                                folderName != null ? folderName : "none",
                                text);
                    })
                    .collect(Collectors.joining("\n---\n"));

            // Skrócony prompt - poprzedni miał ~500 tokenów samych instrukcji
            String finalPrompt =
                    "Answer in Polish based ONLY on these documents. " +
                            "Include passwords/IPs/credentials if present. " +
                            "Cite sources as @filename (no quotes). " +
                            "If nothing relevant: 'Nie znaleziono informacji w dokumentach.'\n\n" +
                            "Q: " + userQuestion + "\n\n" +
                            "DOCS:\n" + contextJoined;

            System.out.println("FINAL PROMPT LENGTH: " + finalPrompt.length());

            return UserMessage.from(finalPrompt);
        };

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .contentInjector(contentInjector)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryService chatMemoryService, Tokenizer tokenizer) {
        return memoryId -> TokenWindowChatMemory.builder()
                .id(memoryId)
                .maxTokens(4000, tokenizer) // zmniejszono z 14000 - Groq limit TPM
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
                .useIndex(true)
                .indexListSize(100)
                .createTable(true)
                .build();
    }
}