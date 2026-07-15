package com.rag.rag.knowledge.query;

import com.rag.rag.core.retrieval.LexicalEmbeddingSearch;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hybrid (semantic + lexical) recall for image candidates.
 * Intentionally bypasses answer reranking.
 */
@Service
@RequiredArgsConstructor
public class ImageCandidateRetriever {
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final LexicalEmbeddingSearch lexicalEmbeddingSearch;

    @Value("${rag.visual-retrieval.max-vector-candidates:40}")
    private int maxCandidates;

    @Value("${rag.visual-retrieval.min-score:0.15}")
    private double minScore;

    @Value("${rag.retrieval.lexical-enabled:true}")
    private boolean lexicalEnabled;

    public Map<String, BigDecimal> recall(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }

        Map<String, BigDecimal> scores = new LinkedHashMap<>();

        var embedding = embeddingModel.embed(query).content();
        var result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(Math.max(1, maxCandidates))
                .minScore(minScore).build());
        result.matches().forEach(match -> {
            String path = match.embedded().metadata().getString("path");
            if (path != null && !path.isBlank()) {
                scores.merge(path, BigDecimal.valueOf(match.score()), BigDecimal::max);
            }
        });

        if (lexicalEnabled && lexicalEmbeddingSearch != null) {
            lexicalEmbeddingSearch.recallPaths(query, maxCandidates).forEach((path, score) ->
                    scores.merge(path, BigDecimal.valueOf(score), BigDecimal::max));
        }

        return scores;
    }
}
