ALTER TABLE rag_model_config
    ADD COLUMN IF NOT EXISTS capability VARCHAR(32);

ALTER TABLE rag_model_config
    ALTER COLUMN chat_model DROP NOT NULL;

UPDATE rag_model_config
SET capability = 'CHAT_EMBEDDING'
WHERE chat_model IS NOT NULL
  AND embedding_model IS NOT NULL
  AND capability IS NULL;

UPDATE rag_model_config
SET capability = 'CHAT'
WHERE chat_model IS NOT NULL
  AND (embedding_model IS NULL OR embedding_model = '')
  AND capability IS NULL;

UPDATE rag_model_config
SET capability = 'EMBEDDING'
WHERE (chat_model IS NULL OR chat_model = '')
  AND embedding_model IS NOT NULL
  AND capability IS NULL;

UPDATE rag_model_config
SET capability = 'CHAT'
WHERE capability IS NULL;
