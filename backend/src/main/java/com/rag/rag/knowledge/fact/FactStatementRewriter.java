package com.rag.rag.knowledge.fact;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.repository.FactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Builds and rewrites Polish fact statements with canonical subject/object names.
 * Vision placeholders ({@code person 1}) must not remain after identity is confirmed.
 */
@Service
@RequiredArgsConstructor
public class FactStatementRewriter {

    public static final String ACTION_APPEARANCE = "HAS_APPEARANCE";
    public static final String ACTION_RELATED_OBJECT = "RELATED_OBJECT";
    /** Spatial proximity (nearby_objects) — not ownership / holding. */
    public static final String ACTION_NEAR_OBJECT = "NEAR_OBJECT";
    public static final String ACTION_NEAR_TEXT = "NEAR_TEXT";

    private final FactRepository factRepository;

    /**
     * Human-readable statement: {@code {subject} {predicate} {value}.}
     * Technical actions are rendered as short Polish glue (e.g. HAS_APPEARANCE → „ma”).
     */
    public static String buildStatement(String subjectName, String action, String value) {
        String subject = subjectName == null || subjectName.isBlank() ? "uczestnik" : subjectName.trim();
        String predicate = readableAction(action);
        StringBuilder statement = new StringBuilder(subject);
        if (predicate != null && !predicate.isBlank()) {
            statement.append(' ').append(predicate.trim());
        }
        if (value != null && !value.isBlank()) {
            statement.append(' ').append(value.trim());
        }
        String text = statement.toString().trim();
        if (text.isEmpty()) {
            return "";
        }
        return text.matches(".*[.!?]$") ? text : text + ".";
    }

    public static String readableAction(String action) {
        if (action == null || action.isBlank()) {
            return "";
        }
        String trimmed = action.trim();
        return switch (trimmed.toUpperCase(Locale.ROOT)) {
            case ACTION_APPEARANCE -> "ma";
            case ACTION_RELATED_OBJECT -> "ma przy sobie";
            case ACTION_NEAR_OBJECT -> "obok";
            case ACTION_NEAR_TEXT -> "ma obok napis";
            case "LEFT_OF" -> "z lewej od";
            case "RIGHT_OF" -> "z prawej od";
            default -> trimmed;
        };
    }

    public static String displayName(EntityMention mention) {
        if (mention == null) {
            return "uczestnik";
        }
        if (mention.getEntity() != null && mention.getEntity().getDisplayName() != null
                && !mention.getEntity().getDisplayName().isBlank()) {
            return mention.getEntity().getDisplayName().trim();
        }
        if (mention.getLabel() != null && !mention.getLabel().isBlank()) {
            return mention.getLabel().trim();
        }
        return "uczestnik";
    }

    /**
     * Rebuild {@code statement_pl} (and target object labels) for every fact on the mention's file
     * that involves this mention as subject or target.
     */
    @Transactional
    public void rewriteFactsForMention(EntityMention mention) {
        if (mention == null || mention.getId() == null || mention.getFilePath() == null
                || mention.getFilePath().isBlank()) {
            return;
        }
        for (Fact fact : factRepository.findByFilePath(mention.getFilePath())) {
            if (fact == null || fact.getMention() == null) {
                continue;
            }
            boolean isSubject = mention.getId().equals(fact.getMention().getId());
            boolean isTarget = fact.getTargetMention() != null
                    && mention.getId().equals(fact.getTargetMention().getId());
            if (!isSubject && !isTarget) {
                continue;
            }
            if (isTarget) {
                String targetName = displayName(mention);
                fact.setObject(targetName);
            }
            String subject = displayName(fact.getMention());
            String value;
            if (fact.getTargetMention() != null) {
                value = displayName(fact.getTargetMention());
                fact.setObject(value);
            } else {
                value = fact.getObject();
            }
            fact.setStatementPl(buildStatement(subject, fact.getAction(), value));
            factRepository.save(fact);
        }
    }
}
