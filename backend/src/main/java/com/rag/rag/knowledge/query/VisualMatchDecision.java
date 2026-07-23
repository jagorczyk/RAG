package com.rag.rag.knowledge.query;

import java.math.BigDecimal;
import java.util.List;
import com.rag.rag.knowledge.graph.GroundedVisualClaim;

/** Result returned by a semantic visual matcher for one image. */
public record VisualMatchDecision(
        Decision decision,
        BigDecimal confidence,
        List<String> reasons,
        List<String> missingEvidence,
        List<GroundedVisualClaim> claims
) {
    public enum Decision { MATCH, NO_MATCH, UNCERTAIN }

    public VisualMatchDecision(Decision decision, BigDecimal confidence, List<String> reasons,
                               List<String> missingEvidence) {
        this(decision, confidence, reasons, missingEvidence, List.of());
    }

    public VisualMatchDecision {
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        claims = claims == null ? List.of() : List.copyOf(claims);
    }
}
