package com.rag.rag.knowledge.dto;

import java.util.UUID;

public record SuggestionMentionViewDto(
        UUID id,
        String label,
        String filePath,
        String fileName,
        String faceCropBase64
) {}
