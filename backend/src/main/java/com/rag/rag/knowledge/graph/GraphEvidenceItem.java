package com.rag.rag.knowledge.graph;

/**
 * One immutable, prompt-safe graph observation. The source path is retained for
 * attribution, but is deliberately not rendered into the model context.
 */
public record GraphEvidenceItem(
        String id,
        Kind kind,
        String statementPl,
        String sourcePath
) {
    public enum Kind { PARTICIPANTS, MENTION, FACT, SCENE, TEXT, AGGREGATE, INVENTORY, DOCUMENT }

    public GraphEvidenceItem {
        id = id == null ? "" : id.trim();
        kind = kind == null ? Kind.FACT : kind;
        statementPl = statementPl == null ? "" : statementPl.trim();
        sourcePath = sourcePath == null ? "" : sourcePath.trim();
    }

    public String render() {
        return statementPl.isBlank() ? "" : "[" + id + "] " + statementPl;
    }
}
