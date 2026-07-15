package com.rag.rag.knowledge.face;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DetectedFaceDto(
        @JsonProperty("embedding") List<Float> embedding,
        @JsonProperty("bbox") List<Float> bbox,
        @JsonProperty("det_score") double detScore,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) Integer imageWidth,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) Integer imageHeight
) {
    public float[] embeddingArray() {
        if (embedding == null) {
            return new float[0];
        }
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i);
        }
        return result;
    }

    public DetectedFaceDto withImageDimensions(int width, int height) {
        return new DetectedFaceDto(embedding, bbox, detScore, width, height);
    }
}
