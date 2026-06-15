ALTER TABLE rag_request_log
    ADD COLUMN IF NOT EXISTS completion_length INTEGER,
    ADD COLUMN IF NOT EXISTS output_capture_status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    ADD COLUMN IF NOT EXISTS output_preview TEXT,
    ADD COLUMN IF NOT EXISTS output_preview_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS output_redacted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS output_retention_expires_at TIMESTAMP;

ALTER TABLE rag_app
    ADD COLUMN IF NOT EXISTS request_log_output_capture_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS rag_request_log_output_access_audit (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL,
    app_id               BIGINT NOT NULL,
    request_log_id       BIGINT,
    request_id           VARCHAR(64) NOT NULL,
    access_result        VARCHAR(32) NOT NULL,
    reason               VARCHAR(256),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_request_log_output_expiry
    ON rag_request_log(output_retention_expires_at);

CREATE INDEX IF NOT EXISTS idx_rag_request_log_output_audit_user_created_at
    ON rag_request_log_output_access_audit(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_request_log_output_audit_app_created_at
    ON rag_request_log_output_access_audit(app_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_request_log_output_audit_request_id
    ON rag_request_log_output_access_audit(request_id);
