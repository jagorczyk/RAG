package com.rag.rag.chat.dto;

import com.rag.rag.ingestion.dto.SourceDto;

import java.util.List;

public record MessageResponse(String response, List<SourceDto> sources, boolean uncertain,
                              List<QueryEvidenceDto> evidence, String answerKind) {
    public MessageResponse(String response, List<SourceDto> sources) {
        this(response, sources, false, List.of(), "DOCUMENT");
    }

    public MessageResponse(String response, List<SourceDto> sources, boolean uncertain) {
        this(response, sources, uncertain, List.of(), "DOCUMENT");
    }

    public MessageResponse(String response, List<SourceDto> sources, boolean uncertain,
                           List<QueryEvidenceDto> evidence) {
        this(response, sources, uncertain, evidence, "DOCUMENT");
    }
}
