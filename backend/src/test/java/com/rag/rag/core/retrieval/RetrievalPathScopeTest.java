package com.rag.rag.core.retrieval;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalPathScopeTest {

    @AfterEach
    void tearDown() {
        RetrievalPathScope.clear();
    }

    @Test
    void emptyScopeAllowsAnyPath() {
        assertTrue(RetrievalPathScope.pathInScope("dir://any.jpg", List.of()));
        assertTrue(RetrievalPathScope.pathInScope("dir://any.jpg"));
    }

    @Test
    void exactPathMatch() {
        List<String> scope = List.of("dir://wakacje/a.jpg", "dir://wakacje/b.jpg");
        assertTrue(RetrievalPathScope.pathInScope("dir://wakacje/a.jpg", scope));
        assertFalse(RetrievalPathScope.pathInScope("dir://other/x.jpg", scope));
    }

    @Test
    void folderPrefixMatch() {
        assertEquals("dir://wakacje/", RetrievalPathScope.folderPrefix("dir://wakacje"));
        assertEquals("dir://wakacje/", RetrievalPathScope.folderPrefix("dir://wakacje/"));
        assertEquals("", RetrievalPathScope.folderPrefix("dir://wakacje/a.jpg"));

        List<String> scope = List.of("dir://wakacje");
        assertTrue(RetrievalPathScope.pathInScope("dir://wakacje/a.jpg", scope));
        assertTrue(RetrievalPathScope.pathInScope("dir://wakacje/sub/b.jpg", scope));
        assertFalse(RetrievalPathScope.pathInScope("dir://inne/a.jpg", scope));
    }

    @Test
    void threadLocalSetAndClear() {
        RetrievalPathScope.set(List.of("dir://only/me.jpg"));
        assertEquals(List.of("dir://only/me.jpg"), RetrievalPathScope.get());
        assertTrue(RetrievalPathScope.pathInScope("dir://only/me.jpg"));
        assertFalse(RetrievalPathScope.pathInScope("dir://other.jpg"));
        RetrievalPathScope.clear();
        assertTrue(RetrievalPathScope.get().isEmpty());
        assertTrue(RetrievalPathScope.pathInScope("dir://other.jpg"));
    }
}
