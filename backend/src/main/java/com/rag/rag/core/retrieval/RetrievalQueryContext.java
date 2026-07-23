package com.rag.rag.core.retrieval;

/** Request-local standalone query produced by the planner for hybrid retrieval. */
public final class RetrievalQueryContext {
    private static final ThreadLocal<String> QUERY = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DISABLED = new ThreadLocal<>();

    private RetrievalQueryContext() {
    }

    public static void set(String query) {
        if (query == null || query.isBlank()) QUERY.remove();
        else QUERY.set(query.trim());
    }

    public static String get() {
        String value = QUERY.get();
        return value == null ? "" : value;
    }

    public static void setDisabled(boolean disabled) {
        if (disabled) DISABLED.set(Boolean.TRUE);
        else DISABLED.remove();
    }

    public static boolean isDisabled() {
        return Boolean.TRUE.equals(DISABLED.get());
    }

    public static void clear() {
        QUERY.remove();
        DISABLED.remove();
    }
}
