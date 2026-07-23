-- Hibernate created this check constraint before FAILED was added to
-- IngestionStatus. ddl-auto=update does not evolve existing enum checks.
ALTER TABLE files
    DROP CONSTRAINT IF EXISTS files_ingestion_status_check;

ALTER TABLE files
    ADD CONSTRAINT files_ingestion_status_check
        CHECK (ingestion_status IN (
            'PENDING',
            'EXTRACTED',
            'NEEDS_REVIEW',
            'READY',
            'FAILED'
        ));
