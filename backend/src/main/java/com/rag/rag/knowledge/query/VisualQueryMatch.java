package com.rag.rag.knowledge.query;

import java.math.BigDecimal;
import java.util.List;
import com.rag.rag.knowledge.graph.GroundedVisualClaim;

public record VisualQueryMatch(
        String filePath,
        BigDecimal confidence,
        List<String> reasons,
        VisualMatchDecision.Decision decision,
        List<String> missingEvidence,
        BigDecimal retrievalScore,
        BigDecimal entityConfidence,
        List<GroundedVisualClaim> claims
) {
    public VisualQueryMatch(String filePath, BigDecimal confidence, List<String> reasons,
                            VisualMatchDecision.Decision decision, List<String> missingEvidence,
                            BigDecimal retrievalScore, BigDecimal entityConfidence) {
        this(filePath, confidence, reasons, decision, missingEvidence, retrievalScore, entityConfidence, List.of());
    }

    public VisualQueryMatch(String filePath, BigDecimal confidence, List<String> reasons) {
        this(filePath, confidence, reasons, VisualMatchDecision.Decision.MATCH,
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    public VisualQueryMatch {
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        decision = decision == null ? VisualMatchDecision.Decision.UNCERTAIN : decision;
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        retrievalScore = retrievalScore == null ? BigDecimal.ZERO : retrievalScore;
        entityConfidence = entityConfidence == null ? BigDecimal.ZERO : entityConfidence;
        claims = claims == null ? List.of() : List.copyOf(claims);
    }
}
