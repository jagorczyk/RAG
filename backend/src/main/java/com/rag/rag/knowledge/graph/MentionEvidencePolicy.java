package com.rag.rag.knowledge.graph;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.IdentityEvidenceSource;
import com.rag.rag.knowledge.entity.MentionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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
            case DESCRIPTION_MATCH -> atLeast(mention.getConfidence(), minMentionConfidence)
                    && atLeast(mention.getIdentityConfidence(), descriptionAutoConfirmThreshold);
            case FACE_CLUSTER -> false;
        };
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
