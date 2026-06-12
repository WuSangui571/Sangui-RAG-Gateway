-- Normalize legacy CHAT_EMBEDDING rows into CHAT or EMBEDDING.
-- Rows with embedding_model are converted to EMBEDDING and chat_model cleared.
-- Rows without embedding_model but with chat_model are normalized to CHAT.
-- Rows with insufficient fields default to CHAT (disabled-safe) and chat_model cleared.

UPDATE rag_model_config
SET capability = 'EMBEDDING',
    chat_model = NULL,
    updated_at = NOW()
WHERE capability = 'CHAT_EMBEDDING'
  AND embedding_model IS NOT NULL
  AND embedding_model <> '';

UPDATE rag_model_config
SET capability = 'CHAT',
    updated_at = NOW()
WHERE capability = 'CHAT_EMBEDDING'
  AND chat_model IS NOT NULL
  AND chat_model <> ''
  AND (embedding_model IS NULL OR embedding_model = '');

UPDATE rag_model_config
SET capability = 'CHAT',
    chat_model = NULL,
    status = 'DISABLED',
    updated_at = NOW()
WHERE capability = 'CHAT_EMBEDDING';
