package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class VisionResultDto {
    private List<ExtractedEntityDto> entities;
    private List<ExtractedRelationDto> relations;
    private String scene;

    @JsonProperty("scene_summary")
    private String sceneSummary;

    @JsonProperty("visible_texts")
    private List<VisibleTextDto> visibleTexts;
}
