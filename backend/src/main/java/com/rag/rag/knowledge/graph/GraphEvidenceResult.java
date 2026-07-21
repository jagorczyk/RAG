package com.rag.rag.knowledge.graph;

import java.util.List;

public record GraphEvidenceResult(String context, List<String> certainPaths, List<GroundedVisualClaim> claims) {
    public GraphEvidenceResult(String context, List<String> certainPaths) {
        this(context, certainPaths, List.of());
    }

    public GraphEvidenceResult {
        context = context == null ? "" : context;
        certainPaths = certainPaths == null ? List.of() : List.copyOf(certainPaths);
        claims = claims == null ? List.of() : List.copyOf(claims);
    }

    public boolean hasEvidence() {
        return !context.isBlank() || !certainPaths.isEmpty() || !claims.isEmpty();
    }
}
