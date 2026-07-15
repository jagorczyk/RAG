package com.rag.rag.knowledge.query;

import java.math.BigDecimal;
import java.util.List;

/** Result returned by a semantic visual matcher for one image. */
public record VisualMatchDecision(
        Decision decision,
        BigDecimal confidence,
        List<String> reasons,
        List<String> missingEvidence
) {
    public enum Decision { MATCH, NO_MATCH, UNCERTAIN }

    public VisualMatchDecision {
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
    }
}
