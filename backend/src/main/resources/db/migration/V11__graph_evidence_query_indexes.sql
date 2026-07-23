CREATE INDEX IF NOT EXISTS idx_entity_mentions_entity_status_path
    ON entity_mentions (entity_id, status, file_path);

CREATE INDEX IF NOT EXISTS idx_entity_mentions_path_status_confidence
    ON entity_mentions (file_path, status, confidence);

CREATE INDEX IF NOT EXISTS idx_facts_path_confidence
    ON facts (file_path, confidence);
