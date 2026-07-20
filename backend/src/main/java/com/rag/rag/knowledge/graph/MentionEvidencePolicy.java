package com.rag.rag.knowledge.graph;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.IdentityEvidenceSource;
import com.rag.rag.knowledge.entity.MentionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Single source of truth for deciding whether an identity mention is certain.
 * Observation confidence and identity confidence intentionally remain separate.
 */
@Component
public class MentionEvidencePolicy {

    @Value("${rag.graph.min-mention-confidence:0.75}")
    private double minMentionConfidence = 0.75;

    @Value("${face.match.threshold:0.50}")
    private double faceMatchThreshold = 0.50;

    @Value("${face.match.min-margin:0.08}")
    private double faceMatchMinMargin = 0.08;

    @Value("${face.match.min-det-score:0.50}")
    private double faceMinDetectionScore = 0.50;

    @Value("${identity.description-auto-confirm-threshold:0.85}")
    private double descriptionAutoConfirmThreshold = 0.85;

    public boolean isCertain(EntityMention mention) {
        if (mention == null || mention.getStatus() != MentionStatus.CONFIRMED || mention.getEntity() == null) {
            return false;
        }
        // Vision placeholders (person 1 / animal 1) are never certain identity sources.
        if (isVisionPlaceholderLabel(mention.getLabel())
                || isVisionPlaceholderLabel(mention.getEntity().getDisplayName())) {
            return false;
        }

        IdentityEvidenceSource source = mention.getIdentitySource();
        if (source == null) {
            // Backwards compatibility only. Existing data keeps the old semantics.
            return atLeast(mention.getConfidence(), minMentionConfidence);
        }

        return switch (source) {
            case USER, USER_TAG -> atLeast(mention.getIdentityConfidence(), 1.0);
            case FACE_MATCH -> atLeast(mention.getConfidence(), faceMinDetectionScore)
                    && atLeast(mention.getIdentityConfidence(), faceMatchThreshold)
                    && atLeast(mention.getIdentityMargin(), faceMatchMinMargin);
            case DESCRIPTION_MATCH -> !"PERSON".equalsIgnoreCase(mention.getEntityType())
                    && atLeast(mention.getConfidence(), minMentionConfidence)
                    && atLeast(mention.getIdentityConfidence(), descriptionAutoConfirmThreshold);
            case FACE_CLUSTER -> false;
        };
    }

    /**
     * Mirrors identity placeholder rules without depending on IdentityResolutionService
     * (avoids a graph↔identity cycle). Keep in sync with {@code isGenericLabel}.
     */
    static boolean isVisionPlaceholderLabel(String label) {
        if (label == null || label.isBlank()) {
            return true;
        }
        String lower = label.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("nieznana")
                || lower.startsWith("nieznany")
                || lower.startsWith("unknown")
                || lower.matches("osoba\\s*\\d*")
                || lower.matches("person\\s*\\d*")
                || lower.matches("people\\s*\\d*")
                || lower.matches("man\\s*\\d*")
                || lower.matches("woman\\s*\\d*")
                || lower.matches("animal\\s*\\d*")
                || lower.matches("zwierzę\\s*\\d*")
                || lower.matches("zwierze\\s*\\d*")
                || lower.matches("pet\\s*\\d*")
                || lower.matches("dog\\s*\\d*")
                || lower.matches("cat\\s*\\d*")
                || lower.equals("osoba")
                || lower.equals("person")
                || lower.equals("people")
                || lower.equals("man")
                || lower.equals("woman")
                || lower.equals("boy")
                || lower.equals("girl")
                || lower.equals("animal")
                || lower.equals("zwierzę")
                || lower.equals("zwierze")
                || lower.equals("pet")
                || lower.equals("dog")
                || lower.equals("cat")
                || lower.equals("postać")
                || lower.equals("postac")
                || lower.equals("figure")
                || lower.equals("individual");
    }

    public BigDecimal evidenceConfidence(EntityMention mention) {
        if (!isCertain(mention)) {
            return BigDecimal.ZERO;
        }
        if (mention.getIdentitySource() == IdentityEvidenceSource.USER
                || mention.getIdentitySource() == IdentityEvidenceSource.USER_TAG) {
            return BigDecimal.ONE;
        }
        return mention.getIdentityConfidence() != null
                ? mention.getIdentityConfidence()
                : mention.getConfidence();
    }

    private boolean atLeast(BigDecimal value, double threshold) {
        return value != null && value.compareTo(BigDecimal.valueOf(threshold)) >= 0;
    }
}
