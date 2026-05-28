CREATE TABLE IF NOT EXISTS rag_request_log (
    id                   BIGSERIAL PRIMARY KEY,
    request_id           VARCHAR(64) NOT NULL,
    user_id              BIGINT NOT NULL,
    app_id               BIGINT NOT NULL,
    api_key_id           BIGINT NOT NULL,
    model                VARCHAR(255),
    provider_name        VARCHAR(128),
    status               VARCHAR(32) NOT NULL,
    error_code           VARCHAR(64),
    latency_ms           BIGINT,
    upstream_latency_ms  BIGINT,
    prompt_tokens        INTEGER,
    completion_tokens    INTEGER,
    total_tokens         INTEGER,
    messages_count       INTEGER,
    question_summary     VARCHAR(512),
    hit_chunk_ids        JSONB,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_request_log_app_created_at ON rag_request_log(app_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_request_log_user_created_at ON rag_request_log(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_request_log_api_key_created_at ON rag_request_log(api_key_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_request_log_request_id ON rag_request_log(request_id);
