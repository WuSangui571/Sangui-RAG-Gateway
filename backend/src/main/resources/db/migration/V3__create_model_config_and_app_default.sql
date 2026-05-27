CREATE TABLE IF NOT EXISTS rag_model_config (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    name               VARCHAR(255) NOT NULL,
    provider_name      VARCHAR(128) NOT NULL,
    base_url           VARCHAR(1024) NOT NULL,
    api_key_encrypted  TEXT,
    api_key_masked     VARCHAR(128),
    chat_model         VARCHAR(255) NOT NULL,
    embedding_model    VARCHAR(255),
    embedding_dimension INTEGER,
    status             VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_model_config_user_status ON rag_model_config(user_id, status);
CREATE INDEX IF NOT EXISTS idx_rag_model_config_provider_model ON rag_model_config(provider_name, chat_model);

ALTER TABLE rag_app
    ADD COLUMN IF NOT EXISTS default_model_config_id BIGINT NULL,
    ADD CONSTRAINT fk_rag_app_default_model_config
        FOREIGN KEY (default_model_config_id) REFERENCES rag_model_config(id);

CREATE INDEX IF NOT EXISTS idx_rag_app_default_model_config ON rag_app(default_model_config_id);
