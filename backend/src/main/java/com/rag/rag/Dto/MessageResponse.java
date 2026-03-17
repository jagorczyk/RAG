package com.rag.rag.Dto;

import java.util.List;

public record MessageResponse(String response, List<SourceDto> sources) {}
