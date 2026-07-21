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

    /** Open free-text background elements (no domain dictionary). */
    private List<String> background;

    /** Open free-text setting / environment (e.g. wnętrze samochodu). */
    private String setting;

    /** Open free-text lighting description. */
    private String lighting;

    @JsonProperty("visible_texts")
    private List<VisibleTextDto> visibleTexts;
}
