package com.rag.rag.knowledge.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EntityAppearanceDto(
        UUID mentionId,
        String filePath,
        String fileName,
        String status,
        BigDecimal confidence,
        List<Float> bbox
) {}
