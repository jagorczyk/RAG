package com.rag.rag.knowledge.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EntityMentionViewDto(
        UUID id,
        String filePath,
        String label,
        String entityType,
        BigDecimal confidence,
        String status,
        String visualCues,
        UUID entityId,
        String entityDisplayName,
        List<Float> bbox
) {}
