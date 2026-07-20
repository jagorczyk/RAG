package com.rag.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record MessageRequest(
        @NotBlank(message = "Treść wiadomości nie może być pusta")
        String message
) {}
