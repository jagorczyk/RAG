package com.rag.rag.Dto;

import java.util.List;
import java.util.UUID;

public record FileMoveDto(List<String> filePaths, UUID targetFolderId) {}
