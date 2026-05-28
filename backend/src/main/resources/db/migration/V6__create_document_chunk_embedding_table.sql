CREATE TABLE IF NOT EXISTS rag_document_chunk_embedding (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    knowledge_base_id   BIGINT NOT NULL REFERENCES rag_knowledge_base(id),
    document_id         BIGINT NOT NULL REFERENCES rag_document(id),
    chunk_id            BIGINT NOT NULL REFERENCES rag_document_chunk(id),
    embedding_model     VARCHAR(255) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    embedding           VECTOR NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_doc_chunk_emb_chunk_id
    ON rag_document_chunk_embedding(chunk_id);
CREATE INDEX IF NOT EXISTS idx_rag_doc_chunk_emb_user_kb
    ON rag_document_chunk_embedding(user_id, knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_rag_doc_chunk_emb_document
    ON rag_document_chunk_embedding(document_id);
