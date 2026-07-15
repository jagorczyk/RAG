package com.rag.rag.knowledge.extraction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class PersonDto {
    private String label;
    private List<String> actions;
    private List<String> objects;
    
    @JsonProperty("visual_cues")
    private List<String> visualCues;
}
