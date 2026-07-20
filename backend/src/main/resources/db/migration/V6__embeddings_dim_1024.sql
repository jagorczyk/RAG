-- Qwen3-Embedding-4B full dim (2560) cannot use pgvector IVFFlat/HNSW (max 2000 dims).
-- Switch to MRL 1024; recreate empty table. App ensureEmbeddingsTableDimension also
-- drops on typmod mismatch at startup.

DROP TABLE IF EXISTS embeddings CASCADE;
