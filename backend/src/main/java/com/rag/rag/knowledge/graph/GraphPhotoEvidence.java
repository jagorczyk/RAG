package com.rag.rag.knowledge.graph;

import java.util.List;

/** Complete certain graph snapshot for one photo. */
public record GraphPhotoEvidence(
        String id,
        String sourcePath,
        List<GraphEvidenceItem> items,
        String heading
) {
    public GraphPhotoEvidence {
        id = id == null ? "" : id.trim();
        sourcePath = sourcePath == null ? "" : sourcePath.trim();
        items = items == null ? List.of() : List.copyOf(items);
        heading = heading == null ? "" : heading.trim();
    }

    public GraphPhotoEvidence(String id, String sourcePath, List<GraphEvidenceItem> items) {
        this(id, sourcePath, items, "");
    }

    public String render() {
        String body = items.stream()
                .map(GraphEvidenceItem::render)
                .filter(line -> !line.isBlank())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        if (body.isBlank()) {
            return "";
        }
        String header = !heading.isBlank()
                ? "=== " + heading + " " + id + " ==="
                : sourcePath.isBlank() ? "=== Podsumowanie grafu ===" : "=== Zdjęcie " + id + " ===";
        return header + "\n" + body;
    }
}
