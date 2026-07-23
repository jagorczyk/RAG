package com.rag.rag.knowledge.graph;

import java.util.List;

public record GraphEvidenceResult(
        String context,
        List<String> certainPaths,
        List<GroundedVisualClaim> claims,
        List<GraphPhotoEvidence> photos
) {
    public GraphEvidenceResult(String context, List<String> certainPaths) {
        this(context, certainPaths, List.of(), List.of());
    }

    public GraphEvidenceResult(String context, List<String> certainPaths, List<GroundedVisualClaim> claims) {
        this(context, certainPaths, claims, List.of());
    }

    public GraphEvidenceResult {
        context = context == null ? "" : context;
        certainPaths = certainPaths == null ? List.of() : List.copyOf(certainPaths);
        claims = claims == null ? List.of() : List.copyOf(claims);
        photos = photos == null ? List.of() : List.copyOf(photos);
    }

    public boolean hasEvidence() {
        return !context.isBlank() || !certainPaths.isEmpty() || !claims.isEmpty();
    }
}
