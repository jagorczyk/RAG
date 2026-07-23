package com.rag.rag.core.retrieval;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Creates lexical/ownership indexes after LangChain4j has ensured the embeddings table. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalIndexInitializer implements InitializingBean {
    private final JdbcTemplate jdbcTemplate;
    @SuppressWarnings("unused")
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public void afterPropertiesSet() {
        try {
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS embeddings_owner_idx
                    ON embeddings ((metadata->>'owner_id'))
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS embeddings_path_idx
                    ON embeddings ((metadata->>'path'))
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS embeddings_fts_idx
                    ON embeddings USING GIN (
                      to_tsvector('simple', LOWER(COALESCE(text, '') || ' ' ||
                        COALESCE(metadata->>'filename', '') || ' ' || COALESCE(metadata->>'path', '')))
                    )
                    """);
        } catch (Exception exception) {
            log.warn("Could not initialize retrieval indexes: {}", exception.getMessage());
        }
    }
}
