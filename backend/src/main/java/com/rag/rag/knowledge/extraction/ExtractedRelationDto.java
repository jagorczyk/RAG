package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExtractedRelationDto {

    @JsonProperty("subject_label")
    private String subjectLabel;

    private String relation;

    @JsonProperty("object_label")
    private String objectLabel;
}
