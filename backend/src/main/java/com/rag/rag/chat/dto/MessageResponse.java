package com.rag.rag.chat.dto;

import com.rag.rag.ingestion.dto.SourceDto;

import java.util.List;

public record MessageResponse(String response, List<SourceDto> sources, boolean uncertain) {
    public MessageResponse(String response, List<SourceDto> sources) {
        this(response, sources, false);
    }
}
