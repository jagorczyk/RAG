-- Knowledge entities (Osoby) scoped per user account

ALTER TABLE entities ADD COLUMN IF NOT EXISTS owner_id UUID;
CREATE INDEX IF NOT EXISTS idx_entities_owner_id ON entities (owner_id);
