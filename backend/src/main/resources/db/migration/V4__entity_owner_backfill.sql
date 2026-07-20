-- Backfill entity owner from linked file ownership (legacy rows without owner_id)

UPDATE entities e
SET owner_id = sub.owner_id
FROM (
    SELECT em.entity_id AS entity_id, MIN(f.owner_id::text)::uuid AS owner_id
    FROM entity_mentions em
    JOIN files f ON f.path = em.file_path
    WHERE em.entity_id IS NOT NULL
      AND f.owner_id IS NOT NULL
    GROUP BY em.entity_id
    HAVING COUNT(DISTINCT f.owner_id) = 1
) sub
WHERE e.id = sub.entity_id
  AND e.owner_id IS NULL;
