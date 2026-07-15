package com.rag.rag.knowledge.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record IdentitySuggestionViewDto(
        UUID id,
        SuggestionMentionViewDto mentionA,
        SuggestionMentionViewDto mentionB,
        BigDecimal similarityScore,
        String status
) {}
