package com.rag.rag.knowledge.graph;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Certain identity-to-face geometry used by pixel verification. */
public record EntityVisualAnchor(
        UUID mentionId,
        String entityName,
        String faceAnchorId,
        List<Float> bbox,
        BigDecimal identityConfidence
) {
    public EntityVisualAnchor {
        bbox = bbox == null ? List.of() : List.copyOf(bbox);
        identityConfidence = identityConfidence == null ? BigDecimal.ZERO : identityConfidence;
    }
}
