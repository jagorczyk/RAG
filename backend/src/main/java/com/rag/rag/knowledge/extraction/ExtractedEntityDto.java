package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedEntityDto {
    private String label;
    private String type; // PERSON or ANIMAL
    private List<String> actions;

    private List<String> objects;

    @JsonProperty("nearby_objects")
    private List<String> nearbyObjects;

    @JsonProperty("nearby_text")
    private List<String> nearbyText;

    private List<Float> bbox;
    
    @JsonProperty("visual_cues")
    private List<String> visualCues;
}
