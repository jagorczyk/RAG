package com.rag.rag.knowledge.face;

import java.util.List;

public record FaceAnalyzeResponse(List<DetectedFaceDto> faces, int count) {}
