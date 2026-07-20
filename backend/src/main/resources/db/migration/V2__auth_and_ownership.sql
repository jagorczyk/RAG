-- Users + ownership columns for multi-user JWT foundation (Sprint 1)
-- V2: V1 was used only as Flyway baseline on existing DBs (migration body not applied).

CREATE TABLE IF NOT EXISTS users (
    id              UUID         NOT NULL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower ON users (LOWER(email));

-- Safe on empty DBs (Hibernate ddl-auto may create domain tables after Flyway)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'folders'
    ) THEN
        ALTER TABLE folders ADD COLUMN IF NOT EXISTS owner_id UUID;
        CREATE INDEX IF NOT EXISTS idx_folders_owner_id ON folders (owner_id);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'conversations'
    ) THEN
        ALTER TABLE conversations ADD COLUMN IF NOT EXISTS owner_id UUID;
        CREATE INDEX IF NOT EXISTS idx_conversations_owner_id ON conversations (owner_id);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'files'
    ) THEN
        ALTER TABLE files ADD COLUMN IF NOT EXISTS owner_id UUID;
        CREATE INDEX IF NOT EXISTS idx_files_owner_id ON files (owner_id);
    END IF;
END $$;
