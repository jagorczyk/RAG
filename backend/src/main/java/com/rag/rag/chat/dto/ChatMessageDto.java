package com.rag.rag.chat.dto;

import com.rag.rag.ingestion.dto.SourceDto;

import java.util.List;

public record ChatMessageDto(String text, String type, List<SourceDto> sources) {}
