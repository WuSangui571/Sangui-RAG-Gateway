CREATE TABLE IF NOT EXISTS rag_knowledge_base (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    name                VARCHAR(255) NOT NULL,
    embedding_model     VARCHAR(255) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_base_user_status
    ON rag_knowledge_base(user_id, status);
CREATE INDEX IF NOT EXISTS idx_rag_knowledge_base_user_created_at
    ON rag_knowledge_base(user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_knowledge_base_user_name
    ON rag_knowledge_base(user_id, name);

CREATE TABLE IF NOT EXISTS rag_document (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    knowledge_base_id   BIGINT NOT NULL REFERENCES rag_knowledge_base(id),
    original_filename   VARCHAR(512) NOT NULL,
    content_type        VARCHAR(255),
    file_size           BIGINT NOT NULL,
    storage_path        VARCHAR(1024) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    chunk_count         INTEGER NOT NULL DEFAULT 0,
    error_message       VARCHAR(512),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_document_user_status
    ON rag_document(user_id, status);
CREATE INDEX IF NOT EXISTS idx_rag_document_kb_status
    ON rag_document(knowledge_base_id, status);
CREATE INDEX IF NOT EXISTS idx_rag_document_user_kb_created_at
    ON rag_document(user_id, knowledge_base_id, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_document_chunk (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    knowledge_base_id   BIGINT NOT NULL REFERENCES rag_knowledge_base(id),
    document_id         BIGINT NOT NULL REFERENCES rag_document(id),
    chunk_index         INTEGER NOT NULL,
    content             TEXT NOT NULL,
    token_count         INTEGER,
    metadata            JSONB,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_document_chunk_user_kb
    ON rag_document_chunk(user_id, knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_rag_document_chunk_document
    ON rag_document_chunk(document_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_document_chunk_document_index
    ON rag_document_chunk(document_id, chunk_index);
