package com.rag.rag.knowledge.dto;

import java.util.List;
import java.util.UUID;

public record PersonGraphDto(
        List<PersonGraphNodeDto> nodes,
        List<PersonGraphEdgeDto> edges
) {
    public record PersonGraphNodeDto(
            UUID id,
            String displayName,
            int photoCount
    ) {}

    public record PersonGraphEdgeDto(
            UUID sourceId,
            UUID targetId,
            String relation,
            int weight,
            String kind
    ) {}
}
