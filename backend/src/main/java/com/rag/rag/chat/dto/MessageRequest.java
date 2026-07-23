package com.rag.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record MessageRequest(
        @NotBlank(message = "Treść wiadomości nie może być pusta")
        String message,
        List<UUID> folderIds
) {
    public MessageRequest {
        folderIds = folderIds == null ? List.of() : List.copyOf(folderIds);
    }

    public MessageRequest(String message) {
        this(message, List.of());
    }
}
