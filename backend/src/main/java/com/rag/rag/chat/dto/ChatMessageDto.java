package com.rag.rag.chat.dto;

import com.rag.rag.ingestion.dto.SourceDto;

import java.util.List;

public record ChatMessageDto(String text, String type, List<SourceDto> sources,
                             List<QueryEvidenceDto> evidence, Boolean uncertain, String answerKind) {
    public ChatMessageDto(String text, String type, List<SourceDto> sources) {
        this(text, type, sources, List.of(), false, "DOCUMENT");
    }

    public ChatMessageDto(String text, String type, List<SourceDto> sources,
                          List<QueryEvidenceDto> evidence) {
        this(text, type, sources, evidence, false, "DOCUMENT");
    }
}
