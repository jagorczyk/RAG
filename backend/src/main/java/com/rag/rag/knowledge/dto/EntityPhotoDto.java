package com.rag.rag.knowledge.dto;

public record EntityPhotoDto(
        String path,
        String fileName,
        String imageBase64,
        String fileType
) {}
