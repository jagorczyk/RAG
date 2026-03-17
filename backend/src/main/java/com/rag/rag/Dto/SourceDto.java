package com.rag.rag.Dto;

public record SourceDto(
    String path,
    String fileName,
    Double score,
    String base64, 
    String type
) {}
