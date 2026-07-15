package com.rag.rag.knowledge.face;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FaceAnalyzeResponse(
        @JsonProperty("faces") List<DetectedFaceDto> faces,
        @JsonProperty("count") int count,
        @JsonProperty("image_width") Integer imageWidth,
        @JsonProperty("image_height") Integer imageHeight
) {}
