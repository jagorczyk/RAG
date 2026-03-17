package com.rag.rag.Dto;

import java.util.List;

public record ChatMessageDto(String text, String type, List<SourceDto> sources) {}
