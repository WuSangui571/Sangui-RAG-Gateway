CREATE TABLE IF NOT EXISTS rag_document_processing_task (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    knowledge_base_id   BIGINT NOT NULL REFERENCES rag_knowledge_base(id),
    document_id         BIGINT NOT NULL REFERENCES rag_document(id),
    status              VARCHAR(32) NOT NULL,
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    max_attempts        INTEGER NOT NULL DEFAULT 3,
    last_error_message  VARCHAR(512),
    locked_by           VARCHAR(128),
    locked_at           TIMESTAMP,
    next_attempt_at     TIMESTAMP,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_doc_proc_task_document
    ON rag_document_processing_task(document_id);

CREATE INDEX IF NOT EXISTS idx_rag_doc_proc_task_status_next
    ON rag_document_processing_task(status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_rag_doc_proc_task_user_kb_status
    ON rag_document_processing_task(user_id, knowledge_base_id, status);
