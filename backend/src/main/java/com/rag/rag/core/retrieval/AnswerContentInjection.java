package com.rag.rag.core.retrieval;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the final answer {@link UserMessage} from graph scaffolding (prefix of the
 * chat prompt) plus retrieved embedding segments. Pure string assembly — no Spring,
 * no phrase routing over user questions.
 */
public final class AnswerContentInjection {

    public static final String DOCUMENTS_HEADER = "Dokumenty:";
    public static final String USER_QUESTION_MARKER = "Pytanie użytkownika:";
    public static final String SHORT_QUESTION_MARKER = "Pytanie:";

    private AnswerContentInjection() {
    }

    /**
     * Injects retrieved segment texts into the answer turn.
     *
     * @param contents        hybrid/vector hits for the current scope (may be empty)
     * @param userQuestion    full answer prompt (graph + instructions + question marker)
     * @param maxSegmentChars truncate each segment body (technical size limit)
     */
    public static UserMessage inject(List<Content> contents, String userQuestion, int maxSegmentChars) {
        String safeQuestion = userQuestion == null ? "" : userQuestion;
        if (safeQuestion.isBlank()) {
            safeQuestion = "Pytanie";
        }
        int segmentLimit = maxSegmentChars <= 0 ? 1500 : maxSegmentChars;

        String graphContext = extractGraphContext(safeQuestion);
        String actualQuestion = extractUserQuestion(safeQuestion);
        List<Content> safeContents = contents == null ? List.of() : contents;

        if (safeContents.isEmpty()) {
            if (!graphContext.isEmpty()) {
                return UserMessage.from(graphContext + "\n\n" + USER_QUESTION_MARKER + " " + actualQuestion);
            }
            return UserMessage.from("""
                    Brak fragmentów dokumentów w indeksie dla tego pytania.
                    Odpowiedz dokładnie jednym zdaniem: Nie znaleziono informacji w dokumentach.
                    Nie zgaduj i nie używaj wiedzy spoza systemu.

                    %s %s
                    """.formatted(USER_QUESTION_MARKER, actualQuestion));
        }

        String contextJoined = safeContents.stream()
                .map(content -> formatSegment(content, segmentLimit))
                .collect(Collectors.joining("\n---\n"));

        StringBuilder promptBuilder = new StringBuilder();
        if (!graphContext.isEmpty()) {
            promptBuilder.append(graphContext).append("\n\n");
        }
        // Minimal inject cue only — system message already sets freeform style (no second rulebook).
        promptBuilder.append("Używaj wyłącznie dostarczonych dowodów; odpowiedź naturalna po polsku. ")
                .append("Nie wypisuj ścieżek ani list źródeł.\n\n")
                .append(SHORT_QUESTION_MARKER).append(" ").append(actualQuestion).append("\n\n")
                .append(DOCUMENTS_HEADER).append("\n").append(contextJoined);

        return UserMessage.from(promptBuilder.toString());
    }

    /** True when the assembled user message contains non-empty document segment bodies. */
    public static boolean containsDocumentSegments(String injectedUserMessage) {
        if (injectedUserMessage == null || injectedUserMessage.isBlank()) {
            return false;
        }
        int idx = injectedUserMessage.indexOf(DOCUMENTS_HEADER);
        if (idx < 0) {
            return false;
        }
        String after = injectedUserMessage.substring(idx + DOCUMENTS_HEADER.length()).trim();
        return !after.isBlank();
    }

    public static String extractUserQuestion(String queryText) {
        if (queryText == null) {
            return "";
        }
        // Prefer the last marker so nested instruction blocks cannot hide the real question.
        int marker = queryText.lastIndexOf(USER_QUESTION_MARKER);
        if (marker >= 0) {
            String after = queryText.substring(marker + USER_QUESTION_MARKER.length()).trim();
            after = cutDocuments(after);
            if (!after.isBlank()) {
                return after;
            }
        }
        int shortMarker = queryText.lastIndexOf(SHORT_QUESTION_MARKER);
        if (shortMarker >= 0) {
            String after = queryText.substring(shortMarker + SHORT_QUESTION_MARKER.length()).trim();
            after = cutDocuments(after);
            if (!after.isBlank()
                    && !after.contains("Jesteś asystentem dokumentów")
                    && !after.contains("[Styl odpowiedzi]")) {
                return after;
            }
        }
        return queryText.trim();
    }

    public static String extractGraphContext(String queryText) {
        if (queryText == null || !queryText.contains(USER_QUESTION_MARKER)) {
            return "";
        }
        int marker = queryText.lastIndexOf(USER_QUESTION_MARKER);
        return queryText.substring(0, marker).trim();
    }

    public static String extractRetrievalQuery(String queryText) {
        String question = extractUserQuestion(queryText);
        return question.replaceAll("@[^\\s,\\]]+", "").trim();
    }

    private static String formatSegment(Content content, int maxSegmentChars) {
        TextSegment segment = content.textSegment();
        String filename = segment.metadata().getString("filename");
        String folderName = segment.metadata().getString("document_id");
        String text = segment.text() == null ? "" : segment.text();
        if (text.length() > maxSegmentChars) {
            text = text.substring(0, maxSegmentChars) + "...";
        }
        return String.format("[Folder: %s, Plik: %s]\n%s",
                folderName != null ? folderName : "nieznany",
                filename != null ? filename : "nieznany",
                text);
    }

    private static String cutDocuments(String after) {
        int docs = after.indexOf("\n" + DOCUMENTS_HEADER);
        if (docs < 0) {
            docs = after.indexOf("\n\n" + DOCUMENTS_HEADER);
        }
        if (docs >= 0) {
            return after.substring(0, docs).trim();
        }
        return after;
    }
}
