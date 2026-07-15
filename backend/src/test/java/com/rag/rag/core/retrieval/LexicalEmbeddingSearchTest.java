package com.rag.rag.core.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LexicalEmbeddingSearchTest {

    @Test
    void tokenizesQueryWithoutClosedDomainVocabulary() {
        List<String> tokens = LexicalEmbeddingSearch.tokenize("Czy @\"michal.jpg\" ma blond włosy?");
        assertTrue(tokens.contains("michal.jpg") || tokens.stream().anyMatch(t -> t.contains("michal")));
        assertTrue(tokens.contains("blond") || tokens.contains("włosy") || tokens.contains("wlosy")
                || tokens.stream().anyMatch(t -> t.startsWith("wł") || t.startsWith("wl")));
        assertFalse(tokens.isEmpty());
    }

    @Test
    void hybridMergerPrefersItemsPresentInBothLists() {
        var vectorOnly = List.of(content("dir://a.jpg", "vector only text", 0.9));
        var both = List.of(
                content("dir://b.jpg", "shared hybrid text", 0.7),
                content("dir://a.jpg", "vector only text", 0.6)
        );
        var lexical = List.of(
                new LexicalEmbeddingSearch.LexicalHit("shared hybrid text",
                        Map.of("path", "dir://b.jpg", "filename", "b.jpg"), 0.95)
        );

        var merged = HybridRetrievalMerger.merge(both, lexical, 5);
        assertFalse(merged.isEmpty());
        assertEquals("dir://b.jpg", merged.get(0).textSegment().metadata().getString("path"));
    }

    private static dev.langchain4j.rag.content.Content content(String path, String text, double score) {
        return dev.langchain4j.rag.content.Content.from(
                dev.langchain4j.data.segment.TextSegment.from(
                        text,
                        dev.langchain4j.data.document.Metadata.from(Map.of(
                                "path", path,
                                "filename", path.substring(path.lastIndexOf('/') + 1),
                                "score", String.valueOf(score)
                        ))
                )
        );
    }
}
