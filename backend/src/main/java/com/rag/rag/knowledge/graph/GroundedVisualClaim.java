package com.rag.rag.knowledge.graph;

import java.math.BigDecimal;
import java.util.UUID;

/** A claim that can be traced to one subject mention and one image. */
public record GroundedVisualClaim(
        String id,
        UUID mentionId,
        String entityName,
        String predicate,
        String value,
        String statementPl,
        String filePath,
        BigDecimal confidence,
        String evidenceOrigin,
        String faceAnchorId
) {
    public GroundedVisualClaim {
        id = id == null ? "" : id;
        entityName = entityName == null ? "" : entityName;
        predicate = predicate == null ? "" : predicate;
        value = value == null ? "" : value;
        statementPl = statementPl == null ? "" : statementPl;
        filePath = filePath == null ? "" : filePath;
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        evidenceOrigin = evidenceOrigin == null ? "" : evidenceOrigin;
        faceAnchorId = faceAnchorId == null ? "" : faceAnchorId;
    }
}
