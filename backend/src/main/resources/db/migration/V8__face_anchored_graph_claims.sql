ALTER TABLE entity_mentions
    ADD COLUMN IF NOT EXISTS vision_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS face_anchor_id VARCHAR(64);

ALTER TABLE facts
    ADD COLUMN IF NOT EXISTS statement_pl TEXT,
    ADD COLUMN IF NOT EXISTS evidence_origin VARCHAR(64);

ALTER TABLE files
    ADD COLUMN IF NOT EXISTS graph_projection_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS graph_projection_status VARCHAR(32);

UPDATE files
SET graph_projection_status = 'STALE'
WHERE file_type LIKE 'image/%'
  AND graph_projection_version IS NULL;

CREATE INDEX IF NOT EXISTS idx_entity_mentions_face_anchor
    ON entity_mentions(file_path, face_anchor_id);
CREATE INDEX IF NOT EXISTS idx_files_graph_projection
    ON files(graph_projection_status, graph_projection_version);
