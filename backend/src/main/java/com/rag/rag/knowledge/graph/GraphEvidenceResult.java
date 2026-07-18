package com.rag.rag.knowledge.graph;

import java.util.List;

public record GraphEvidenceResult(String context, List<String> certainPaths) {
    public GraphEvidenceResult {
        context = context == null ? "" : context;
        certainPaths = certainPaths == null ? List.of() : List.copyOf(certainPaths);
    }

    public boolean hasEvidence() {
        return !context.isBlank() || !certainPaths.isEmpty();
    }
}
