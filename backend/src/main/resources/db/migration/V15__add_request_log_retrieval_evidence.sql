ALTER TABLE rag_request_log
    ADD COLUMN IF NOT EXISTS retrieval_evidence JSONB;
