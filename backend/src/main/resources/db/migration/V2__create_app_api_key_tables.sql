CREATE TABLE IF NOT EXISTS rag_app (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_app_user_status ON rag_app(user_id, status);

CREATE TABLE IF NOT EXISTS rag_api_key (
    id              BIGSERIAL PRIMARY KEY,
    app_id          BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    name            VARCHAR(255) NOT NULL,
    key_hash        VARCHAR(128) NOT NULL,
    key_prefix      VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at      TIMESTAMP NULL DEFAULT NULL,
    last_used_at    TIMESTAMP NULL DEFAULT NULL,
    revoked_at      TIMESTAMP NULL DEFAULT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rag_api_key_app FOREIGN KEY (app_id) REFERENCES rag_app(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_api_key_hash ON rag_api_key(key_hash);
CREATE INDEX IF NOT EXISTS idx_rag_api_key_app ON rag_api_key(app_id);
CREATE INDEX IF NOT EXISTS idx_rag_api_key_app_status ON rag_api_key(app_id, status);
