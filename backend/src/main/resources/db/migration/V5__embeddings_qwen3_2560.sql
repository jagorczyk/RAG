-- Text embeddings switched from BGE-M3 (1024-dim) to Qwen3-Embedding-4B (2560-dim).
-- pgvector rejects inserts when the column typmod does not match the vector length.
-- Old vectors are not comparable across models — drop the table; LangChain4j
-- PgVectorEmbeddingStore (createTable=true) recreates it with the model dimension.

DROP TABLE IF EXISTS embeddings CASCADE;
