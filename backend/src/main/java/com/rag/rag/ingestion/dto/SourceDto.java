package com.rag.rag.ingestion.dto;

public record SourceDto(
    String path,
    String fileName,
    Double score,
    String base64, 
    String type
) {}
