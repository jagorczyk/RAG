package com.rag.rag.folder.dto;

import java.util.List;
import java.util.UUID;

public record FileMoveDto(List<String> filePaths, UUID targetFolderId) {}
