ALTER TABLE rag_app
    ADD COLUMN IF NOT EXISTS default_knowledge_base_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS retrieval_top_k INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS retrieval_similarity_threshold NUMERIC(4,3) NOT NULL DEFAULT 0.700,
    ADD COLUMN IF NOT EXISTS retrieval_max_context_chunks INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS retrieval_max_context_chars INTEGER NOT NULL DEFAULT 12000,
    ADD COLUMN IF NOT EXISTS retrieval_max_single_chunk_chars INTEGER NOT NULL DEFAULT 3000,
    ADD COLUMN IF NOT EXISTS no_hit_policy VARCHAR(32) NOT NULL DEFAULT 'STRICT_RAG';

ALTER TABLE rag_app
    ADD CONSTRAINT fk_rag_app_default_knowledge_base
        FOREIGN KEY (default_knowledge_base_id) REFERENCES rag_knowledge_base(id);

CREATE INDEX IF NOT EXISTS idx_rag_app_default_knowledge_base ON rag_app(default_knowledge_base_id);
