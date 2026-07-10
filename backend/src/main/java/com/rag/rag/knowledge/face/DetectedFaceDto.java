package com.rag.rag.knowledge.face;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DetectedFaceDto(
        List<Float> embedding,
        List<Float> bbox,
        @JsonProperty("det_score") double detScore
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
}
