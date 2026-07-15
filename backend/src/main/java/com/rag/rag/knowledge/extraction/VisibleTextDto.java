package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class VisibleTextDto {
    private String text;
    private List<Float> bbox;

    @JsonProperty("near_entity_label")
    private String nearEntityLabel;
}
