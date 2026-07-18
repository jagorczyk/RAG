package com.rag.rag.knowledge.dto;

import java.util.UUID;

public record IdentityConflictDto(String code, String message, UUID existingMentionId) {}
