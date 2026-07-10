package com.rag.rag.knowledge.dto;

import java.util.List;
import java.util.UUID;

public record EntitySummaryDto(
        UUID id,
        String displayName,
        String type,
        List<EntityPhotoDto> photos
) {}
