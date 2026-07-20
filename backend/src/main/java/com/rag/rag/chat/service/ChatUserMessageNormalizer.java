package com.rag.rag.chat.service;

/**
 * Extracts the bare user question from LangChain chat-memory user messages.
 * Answer turns wrap the question in instructions / graph / RAG scaffolding;
 * token-window memory must keep Q&amp;A only so follow-ups see real conversation.
 * Technical extraction only — no phrase/intent routing (AGENTS.md).
 */
public final class ChatUserMessageNormalizer {

    private static final String OLD_QUESTION_MARKER = "PYTANIE UŻYTKOWNIKA: ";
    private static final String OLD_DATA_MARKER = "\n\nDANE Z BAZY DANYCH:\n";
    private static final String QUESTION_MARKER = "Pytanie użytkownika:";
    private static final String SHORT_QUESTION_MARKER = "Pytanie:";

    private ChatUserMessageNormalizer() {
    }

    /**
     * Returns the original user question when {@code text} is a full answer prompt
     * or retrieval-injected blob; otherwise returns the trimmed text.
     */
    public static String extractOriginalQuestion(String text) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return "";
        }

        // Legacy answer format used before structured instruction blocks.
        if (value.contains(OLD_DATA_MARKER) && value.contains(OLD_QUESTION_MARKER)) {
            int start = value.indexOf(OLD_QUESTION_MARKER) + OLD_QUESTION_MARKER.length();
            int end = value.indexOf(OLD_DATA_MARKER);
            if (start >= OLD_QUESTION_MARKER.length() && end > start) {
                String extracted = value.substring(start, end).trim();
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
        }

        String fromUserMarker = afterLastMarker(value, QUESTION_MARKER);
        if (fromUserMarker != null) {
            return truncateScaffold(fromUserMarker);
        }

        // ContentInjector rebuild path uses "Pytanie: " before document blocks.
        String fromShortMarker = afterLastMarker(value, SHORT_QUESTION_MARKER);
        if (fromShortMarker != null && !looksLikeInstructionBlob(fromShortMarker)) {
            return truncateScaffold(fromShortMarker);
        }

        if (looksLikeInstructionBlob(value)) {
            String lastParagraph = lastNonEmptyParagraph(value);
            if (!lastParagraph.isEmpty() && !looksLikeInstructionBlob(lastParagraph)) {
                return lastParagraph;
            }
        }

        return value;
    }

    private static String afterLastMarker(String text, String marker) {
        int index = text.lastIndexOf(marker);
        if (index < 0) {
            return null;
        }
        String after = text.substring(index + marker.length()).trim();
        return after.isEmpty() ? null : after;
    }

    private static String truncateScaffold(String extracted) {
        String value = extracted;
        value = cutAt(value, "\nDowody:");
        value = cutAt(value, "\nDokumenty:");
        value = cutAt(value, "\n\nDokumenty:");
        value = cutAt(value, "\n[Kontekst zweryfikowany]");
        return value.trim();
    }

    private static String cutAt(String value, String marker) {
        int index = value.indexOf(marker);
        return index >= 0 ? value.substring(0, index) : value;
    }

    private static boolean looksLikeInstructionBlob(String text) {
        return text.contains("Jesteś asystentem dokumentów")
                || text.contains("[Styl odpowiedzi]")
                || text.contains("[Instrukcja odpowiedzi]")
                || text.contains("[Kontekst zweryfikowany]")
                || text.contains("Nie znaleziono informacji w dokumentach.")
                || text.contains("Brak fragmentów dokumentów");
    }

    private static String lastNonEmptyParagraph(String text) {
        String[] parts = text.split("\\n\\s*\\n");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (!part.isEmpty()) {
                // Prefer a short final line that looks like a question, not a bullet list of rules.
                String[] lines = part.split("\\n");
                String lastLine = lines[lines.length - 1].trim();
                if (!lastLine.isEmpty() && !lastLine.startsWith("-") && lastLine.length() < 500) {
                    return lastLine;
                }
                return part;
            }
        }
        return "";
    }
}
