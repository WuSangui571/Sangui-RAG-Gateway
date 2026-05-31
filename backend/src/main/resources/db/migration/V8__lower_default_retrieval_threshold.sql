ALTER TABLE rag_app
    ALTER COLUMN retrieval_similarity_threshold SET DEFAULT 0.300;

UPDATE rag_app
SET retrieval_similarity_threshold = 0.300,
    updated_at = CURRENT_TIMESTAMP
WHERE retrieval_similarity_threshold = 0.700;
