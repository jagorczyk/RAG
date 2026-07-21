-- Open scene attributes from structured vision (background / setting / lighting).
ALTER TABLE files
    ADD COLUMN IF NOT EXISTS scene_attributes jsonb;
