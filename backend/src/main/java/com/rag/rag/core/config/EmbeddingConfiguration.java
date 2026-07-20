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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class EmbeddingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfiguration.class);
    private static final Pattern VECTOR_DIM = Pattern.compile("vector\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final String EMBEDDINGS_TABLE = "embeddings";

    @Value("${ollama.base.url:http://localhost:11434}")
    private String baseUrl;

    @Value("${embedding.model:bge-m3}")
    private String embeddingModel;

    @Value("${llm.deepinfra.base-url}")
    private String deepInfraBaseUrl;

    @Value("${llm.deepinfra.api-key}")
    private String deepInfraApiKey;

    @Value("${llm.deepinfra.embedding-model:Qwen/Qwen3-Embedding-4B}")
    private String deepInfraEmbeddingModel;

    /**
     * Output size via Matryoshka (Qwen3-4B max 2560). Keep ≤2000 so pgvector IVFFlat/HNSW
     * can index the column (extension limit). Must match embeddings.embedding typmod.
     */
    @Value("${llm.deepinfra.embedding-dimensions:1024}")
    private int deepInfraEmbeddingDimensions;

    /** IVFFlat needs enough rows and dims ≤2000; off by default after empty rebuilds. */
    @Value("${rag.embeddings.use-index:false}")
    private boolean embeddingsUseIndex;

    @Value("${rag.embeddings.index-list-size:100}")
    private int embeddingsIndexListSize;

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
    @ConditionalOnProperty(name = "llm.embedding.provider", havingValue = "ollama")
    EmbeddingModel ollamaEmbeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(embeddingModel)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "llm.embedding.provider", havingValue = "deepinfra", matchIfMissing = true)
    EmbeddingModel deepInfraEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(deepInfraBaseUrl)
                .apiKey(deepInfraApiKey)
                .modelName(deepInfraEmbeddingModel)
                .dimensions(deepInfraEmbeddingDimensions)
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
            DataSource dataSource,
            @Value("${pgvector.host:localhost}") String host,
            @Value("${pgvector.port:5433}") int port,
            @Value("${pgvector.database:vector_db}") String database,
            EmbeddingModel embeddingModel
    ) {
        int dimension = embeddingModel.dimension();
        ensureEmbeddingsTableDimension(dataSource, dimension);

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(dbUser)
                .password(dbPassword)
                .table(EMBEDDINGS_TABLE)
                .dimension(dimension)
                .useIndex(embeddingsUseIndex)
                .indexListSize(embeddingsIndexListSize)
                .createTable(true)
                .build();
    }

    /**
     * LangChain4j only creates the table when missing — it never alters vector typmod.
     * After a model switch (e.g. 1024 → 2560) drop the incompatible table so createTable rebuilds it.
     */
    static void ensureEmbeddingsTableDimension(DataSource dataSource, int expectedDimension) {
        if (expectedDimension <= 0) {
            log.warn("Embedding model reported dimension {}; skipping schema check", expectedDimension);
            return;
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            Integer existing = readVectorDimension(statement, EMBEDDINGS_TABLE, "embedding");
            if (existing == null) {
                return;
            }
            if (existing == expectedDimension) {
                return;
            }
            log.warn(
                    "Dropping {} table: vector column is {}-dim but embedding model is {}-dim (re-ingest required)",
                    EMBEDDINGS_TABLE,
                    existing,
                    expectedDimension
            );
            statement.execute("DROP TABLE IF EXISTS " + EMBEDDINGS_TABLE + " CASCADE");
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to align embeddings table with model dimension " + expectedDimension, e);
        }
    }

    /** @return vector typmod dimension, or null if table/column missing */
    static Integer readVectorDimension(Statement statement, String table, String column) throws SQLException {
        String sql = """
                SELECT format_type(a.atttypid, a.atttypmod)
                FROM pg_attribute a
                JOIN pg_class c ON a.attrelid = c.oid
                JOIN pg_namespace n ON c.relnamespace = n.oid
                WHERE n.nspname = 'public'
                  AND c.relname = '%s'
                  AND a.attname = '%s'
                  AND NOT a.attisdropped
                """.formatted(table, column);
        try (ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            String type = rs.getString(1);
            if (type == null) {
                return null;
            }
            Matcher matcher = VECTOR_DIM.matcher(type);
            if (!matcher.find()) {
                return null;
            }
            return Integer.parseInt(matcher.group(1));
        }
    }
}
