package com.rag.rag.folder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FolderDto(
        @NotBlank(message = "Nazwa folderu jest wymagana")
        @Size(max = 255, message = "Nazwa folderu jest za długa")
        String name
) {}
