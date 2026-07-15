package com.rag.rag.core.retrieval;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion of semantic (vector) and lexical hit lists.
 */
public final class HybridRetrievalMerger {

    private static final int RRF_K = 60;

    private HybridRetrievalMerger() {
    }

    public static List<Content> merge(
            List<Content> vectorContents,
            List<LexicalEmbeddingSearch.LexicalHit> lexicalHits,
            int maxResults
    ) {
        Map<String, Ranked> ranked = new LinkedHashMap<>();

        for (int i = 0; i < vectorContents.size(); i++) {
            Content content = vectorContents.get(i);
            String key = contentKey(content);
            Ranked entry = ranked.computeIfAbsent(key, ignored -> Ranked.fromVector(content));
            entry.rrf += 1.0 / (RRF_K + i + 1);
            entry.vectorRank = i + 1;
            Double vectorScore = parseScore(content.textSegment().metadata().getString("score"));
            if (vectorScore != null) {
                entry.vectorScore = Math.max(entry.vectorScore, vectorScore);
            }
        }

        for (int i = 0; i < lexicalHits.size(); i++) {
            LexicalEmbeddingSearch.LexicalHit hit = lexicalHits.get(i);
            String key = lexicalKey(hit);
            Ranked entry = ranked.computeIfAbsent(key, ignored -> Ranked.fromLexical(hit));
            entry.rrf += 1.0 / (RRF_K + i + 1);
            entry.lexicalRank = i + 1;
            entry.lexicalScore = Math.max(entry.lexicalScore, hit.score());
            if (entry.content == null) {
                entry.content = toContent(hit);
            }
        }

        List<Ranked> ordered = new ArrayList<>(ranked.values());
        ordered.sort((a, b) -> {
            int byRrf = Double.compare(b.rrf, a.rrf);
            if (byRrf != 0) {
                return byRrf;
            }
            return Double.compare(Math.max(b.vectorScore, b.lexicalScore), Math.max(a.vectorScore, a.lexicalScore));
        });

        List<Content> result = new ArrayList<>();
        for (Ranked entry : ordered) {
            if (entry.content == null) {
                continue;
            }
            result.add(withHybridScores(entry));
            if (result.size() >= maxResults) {
                break;
            }
        }
        return result;
    }

    private static Content withHybridScores(Ranked entry) {
        TextSegment segment = entry.content.textSegment();
        Map<String, Object> metadata = new HashMap<>(segment.metadata().toMap());
        double publicScore = Math.max(entry.vectorScore, entry.lexicalScore);
        if (publicScore <= 0) {
            publicScore = entry.rrf;
        }
        metadata.put("score", String.valueOf(publicScore));
        metadata.put("retrieval_score", String.valueOf(publicScore));
        metadata.put("hybrid_rrf", String.valueOf(entry.rrf));
        if (entry.vectorRank > 0) {
            metadata.put("vector_rank", String.valueOf(entry.vectorRank));
        }
        if (entry.lexicalRank > 0) {
            metadata.put("lexical_rank", String.valueOf(entry.lexicalRank));
        }
        if (entry.lexicalScore > 0) {
            metadata.put("lexical_score", String.valueOf(entry.lexicalScore));
        }
        return Content.from(TextSegment.from(segment.text(), Metadata.from(metadata)));
    }

    private static Content toContent(LexicalEmbeddingSearch.LexicalHit hit) {
        Map<String, Object> metadata = new HashMap<>();
        hit.metadata().forEach(metadata::put);
        metadata.put("score", String.valueOf(hit.score()));
        metadata.put("retrieval_score", String.valueOf(hit.score()));
        metadata.put("lexical_score", String.valueOf(hit.score()));
        return Content.from(TextSegment.from(hit.text(), Metadata.from(metadata)));
    }

    private static String contentKey(Content content) {
        TextSegment segment = content.textSegment();
        String path = segment.metadata().getString("path");
        String text = segment.text() == null ? "" : segment.text();
        String prefix = text.length() > 80 ? text.substring(0, 80) : text;
        return (path == null ? "" : path) + "|" + prefix.hashCode();
    }

    private static String lexicalKey(LexicalEmbeddingSearch.LexicalHit hit) {
        String path = hit.metadata().getOrDefault("path", "");
        String text = hit.text() == null ? "" : hit.text();
        String prefix = text.length() > 80 ? text.substring(0, 80) : text;
        return path + "|" + prefix.hashCode();
    }

    private static Double parseScore(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class Ranked {
        Content content;
        double rrf;
        double vectorScore;
        double lexicalScore;
        int vectorRank;
        int lexicalRank;

        static Ranked fromVector(Content content) {
            Ranked ranked = new Ranked();
            ranked.content = content;
            return ranked;
        }

        static Ranked fromLexical(LexicalEmbeddingSearch.LexicalHit hit) {
            Ranked ranked = new Ranked();
            ranked.content = toContent(hit);
            ranked.lexicalScore = hit.score();
            return ranked;
        }
    }
}
