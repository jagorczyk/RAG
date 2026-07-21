-- Hibernate @Lob on PostgreSQL stored payload as large-object OIDs (numeric text),
-- so vision/face cache reads returned "162191" instead of JSON. Wipe corrupted rows
-- and keep payload as plain text (entity no longer uses @Lob).
DELETE FROM image_analysis_cache;

-- Best-effort: drop orphan large objects left by previous @Lob writes (ignore errors).
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT oid FROM pg_largeobject_metadata
        WHERE oid >= 100000
    LOOP
        BEGIN
            PERFORM lo_unlink(r.oid);
        EXCEPTION WHEN OTHERS THEN
            -- ignore missing / permission issues
            NULL;
        END;
    END LOOP;
END $$;
