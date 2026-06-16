ALTER TABLE rag_api_key
    ADD COLUMN IF NOT EXISTS requests_per_minute INTEGER,
    ADD COLUMN IF NOT EXISTS tokens_per_minute INTEGER,
    ADD COLUMN IF NOT EXISTS daily_request_quota INTEGER,
    ADD COLUMN IF NOT EXISTS daily_token_quota INTEGER;
