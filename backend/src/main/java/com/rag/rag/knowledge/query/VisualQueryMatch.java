package com.rag.rag.knowledge.query;

import java.math.BigDecimal;
import java.util.List;

public record VisualQueryMatch(
        String filePath,
        BigDecimal confidence,
        List<String> reasons,
        VisualMatchDecision.Decision decision,
        List<String> missingEvidence,
        BigDecimal retrievalScore,
        BigDecimal entityConfidence
) {
    public VisualQueryMatch(String filePath, BigDecimal confidence, List<String> reasons) {
        this(filePath, confidence, reasons, VisualMatchDecision.Decision.MATCH,
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public VisualQueryMatch {
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        decision = decision == null ? VisualMatchDecision.Decision.UNCERTAIN : decision;
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        retrievalScore = retrievalScore == null ? BigDecimal.ZERO : retrievalScore;
        entityConfidence = entityConfidence == null ? BigDecimal.ZERO : entityConfidence;
    }
}
