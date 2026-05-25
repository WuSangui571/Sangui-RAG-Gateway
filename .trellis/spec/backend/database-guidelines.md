# Database Guidelines

> PostgreSQL and pgvector rules for Sangui-RAG-Gateway. The highest-risk area is tenant-safe vector retrieval; every query must preserve user/app/knowledge-base boundaries.

## Database Stack

Use PostgreSQL as the main database and pgvector for embeddings.

Recommended persistence stack:

```text
PostgreSQL + pgvector
MyBatis-Plus
Migration files under src/main/resources/db/migration
```

Redis is used for rate limits, quota counters, cache, and task state support. Redis is not the source of truth for business records.

## Core Tables

Recommended tables:

```text
sys_user
rag_app
rag_api_key
rag_model_config
rag_knowledge_base
rag_document
rag_document_chunk
rag_request_log
```

## Table Rules

- Every business table must have primary key `id`.
- Core tables must include `created_at` and `updated_at`.
- Soft deletion may use `deleted` or `deleted_at`; choose one and keep it consistent once implemented.
- Tenant-related tables must include `user_id` or have a mandatory relation that resolves to a user.
- Status fields must use explicit enum values.
- Large text may use `text`.
- Flexible metadata should use `jsonb`.
- Embeddings should use pgvector column types with fixed dimensions.

## Tenant Isolation

Core data must not be queryable across user boundaries.

Tenant-sensitive objects:

```text
App
ApiKey
KnowledgeBase
Document
DocumentChunk
ModelConfig
ApiRequestLog
```

If a query is used by a user-facing admin API, it must be scoped by the current user unless the endpoint is explicitly system-admin only.

If a query is used by `/v1/chat/completions`, it must be scoped through the API key's app and knowledge base.

## Vector Retrieval Rules

Never run global vector search.

Forbidden:

```sql
SELECT *
FROM rag_document_chunk
ORDER BY embedding <=> ?
LIMIT 5;
```

Minimum acceptable scope:

```sql
SELECT *
FROM rag_document_chunk
WHERE knowledge_base_id = ?
ORDER BY embedding <=> ?
LIMIT ?;
```

Preferred scope when `user_id` exists:

```sql
SELECT *
FROM rag_document_chunk
WHERE user_id = ?
  AND knowledge_base_id = ?
ORDER BY embedding <=> ?
LIMIT ?;
```

When an app can bind multiple knowledge bases, use an app-authorized list:

```sql
SELECT *
FROM rag_document_chunk
WHERE user_id = ?
  AND knowledge_base_id = ANY(?)
ORDER BY embedding <=> ?
LIMIT ?;
```

Do not filter tenant boundaries in Java after vector search. The SQL query itself must enforce the boundary.

## Embedding Dimension Rules

- A knowledge base has exactly one embedding model and vector dimension.
- The embedding dimension is fixed at knowledge-base creation time.
- Reject attempts to insert chunks with a mismatched vector dimension.
- Changing a knowledge base's embedding model requires re-embedding all documents or creating a new knowledge base.

## Status Enums

Document status:

```text
UPLOADED
PARSING
PARSED
EMBEDDING
READY
FAILED
```

App status:

```text
ENABLED
DISABLED
```

API key status:

```text
ACTIVE
DISABLED
EXPIRED
REVOKED
```

Knowledge base status should distinguish "created but empty", "processing", "ready", and "failed" when implemented.

## API Key Storage

Never store plaintext app API keys.

Store:

```text
key_hash
key_prefix
status
expires_at
last_used_at
rate_limit_config
quota_config
```

The full key value is only returned once on creation.

## Upstream API Key Storage

Never store plaintext upstream API keys.

Store encrypted values:

```text
api_key_encrypted
api_key_masked or prefix for display
encryption_version if key rotation is introduced
```

The encryption master key must come from environment variables, not source code.

## Migrations

- Every schema change must be represented by a migration file.
- Migration file names should be ordered and descriptive.
- Migration SQL must create required indexes with tables.
- Include pgvector extension setup in initial migrations.
- Do not rely on ORM auto-DDL outside local experiments.

Recommended indexes:

```text
rag_api_key(key_hash)
rag_app(user_id, status)
rag_knowledge_base(user_id, status)
rag_document(knowledge_base_id, status)
rag_document_chunk(knowledge_base_id)
rag_request_log(app_id, created_at)
```

Add vector indexes only after confirming pgvector operator class and distance metric choices.

## Transaction Boundaries

Use service methods as transaction boundaries.

Typical transactions:

- Create app and default config records.
- Create API key hash/prefix metadata.
- Create document metadata and mark upload state.
- Update document state during parsing/embedding transitions.
- Persist chunks and update document/knowledge-base readiness.

Do not keep a database transaction open while calling upstream model or embedding APIs. Persist an intermediate state, call the external service, then persist the result or failure.
