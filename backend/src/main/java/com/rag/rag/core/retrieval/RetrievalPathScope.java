package com.rag.rag.core.retrieval;

import java.util.ArrayList;
import java.util.List;

/**
 * Request-scoped path/folder filter for hybrid (vector + lexical) retrieval.
 * Set by chat plan execution; read by ContentRetriever. No user-language routing.
 */
public final class RetrievalPathScope {

    private static final ThreadLocal<List<String>> SCOPE = new ThreadLocal<>();

    private RetrievalPathScope() {
    }

    public static void set(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            SCOPE.remove();
            return;
        }
        List<String> copy = new ArrayList<>();
        for (String path : paths) {
            if (path != null && !path.isBlank()) {
                copy.add(path.trim());
            }
        }
        if (copy.isEmpty()) {
            SCOPE.remove();
        } else {
            SCOPE.set(List.copyOf(copy));
        }
    }

    public static List<String> get() {
        List<String> scope = SCOPE.get();
        return scope == null ? List.of() : scope;
    }

    public static void clear() {
        SCOPE.remove();
    }

    /**
     * True when unrestricted (empty scope) or {@code path} matches an exact scope entry
     * or lies under a folder-style scope prefix.
     */
    public static boolean pathInScope(String path, List<String> scope) {
        if (scope == null || scope.isEmpty()) {
            return true;
        }
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String entry : scope) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (path.equals(entry)) {
                return true;
            }
            String prefix = folderPrefix(entry);
            if (!prefix.isEmpty() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean pathInScope(String path) {
        return pathInScope(path, get());
    }

    /**
     * Folder prefix for scope matching: trailing slash kept; path without a file
     * extension and without trailing slash is treated as a folder root.
     */
    public static String folderPrefix(String scopeEntry) {
        if (scopeEntry == null || scopeEntry.isBlank()) {
            return "";
        }
        String entry = scopeEntry.trim();
        if (entry.endsWith("/")) {
            return entry;
        }
        int slash = entry.lastIndexOf('/');
        String last = slash >= 0 ? entry.substring(slash + 1) : entry;
        // Heuristic: "dir://folder" or "dir://folder/sub" without a filename extension
        // is a folder scope; "dir://folder/file.jpg" is an exact file only.
        if (!last.contains(".")) {
            return entry + "/";
        }
        return "";
    }
}
