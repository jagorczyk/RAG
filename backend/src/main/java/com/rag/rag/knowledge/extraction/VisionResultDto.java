package com.rag.rag.knowledge.extraction;

import lombok.Data;
import java.util.List;

@Data
public class VisionResultDto {
    private List<ExtractedEntityDto> entities;
    private List<ExtractedRelationDto> relations;
    private String scene;
}
