package com.rag.rag.folder.dto;

public record FileDto(
        String path,
        String name,
        String imageBase64,
        String fileType,
        String extractedText
) {}
