# Cross-Layer Thinking Guide

> Think through data flow across layers before implementing. Sangui-RAG-Gateway has especially sensitive boundaries around OpenAI-compatible APIs, tenant isolation, documents, embeddings, prompts, streaming, and secret handling.

## When This Guide Is Required

Use this guide when a task touches 3 or more layers, or any of these contracts:

- Public `/v1/*` gateway APIs.
- Admin API payloads consumed by frontend pages.
- Database schema, migration, or enum changes.
- API key, upstream key, tenant, rate-limit, or quota behavior.
- Document ingestion pipeline.
- Embedding model and vector dimension contracts.
- Retrieval SQL, topK, similarity threshold, or context token limits.
- Prompt construction.
- Streaming response behavior.
- Request logging or observability.
- Docker Compose or environment variables.

## Step 1: Map The Flow

Draw the complete path.

Gateway chat flow:

```text
HTTP request
  -> API key auth
  -> app config
  -> model config
  -> last user message extraction
  -> query embedding
  -> vector retrieval
  -> context filtering/truncation
  -> RAG prompt/messages
  -> upstream chat API
  -> OpenAI-compatible response
  -> request log
```

Document ingestion flow:

```text
upload
  -> file storage
  -> document row
  -> parser
  -> text cleaning
  -> chunking
  -> embedding
  -> chunk/vector rows
  -> document/knowledge-base status
  -> frontend status display
```

Admin config flow:

```text
frontend form
  -> admin API DTO
  -> validation
  -> encrypted/hashed persistence
  -> masked response VO
  -> frontend display
```

Admin auth flow:

```text
frontend login form
  -> POST /api/admin/auth/login { username, password }
  -> password hash verification against sys_user
  -> AdminJwtService signs expiring token with rag.gateway.secret-key
  -> frontend stores token/current safe user metadata in shell state
  -> frontend HTTP helper sends Authorization: Bearer <admin-jwt>
  -> AdminAuthFilter validates token and active user
  -> AdminAuthContextHolder exposes userId to Admin controllers
  -> services keep owner-scoped queries and 403/404 distinction
```

For each arrow, define:

- Input format.
- Output format.
- Validation owner.
- Error shape.
- Sensitive fields.
- Tenant boundary.

## Step 2: Define Contracts

Before coding, write down:

- Request fields and optionality.
- Response fields and masking rules.
- Enum values.
- Database columns and indexes.
- Environment variables.
- Error codes and HTTP statuses.
- Test cases for success and failure.
- For Admin auth, record the exact identity source (`Authorization: Bearer <admin-jwt>`), login/me DTO/VO fields, token expiry property, and bootstrap boundary for the first `sys_user`.

For OpenAI-compatible endpoints, distinguish:

```text
Supported and implemented
Accepted but ignored with documentation
Rejected with compatible error
Unsupported and not advertised
```

## Step 3: Check Tenant and Secret Boundaries

Tenant checks:

- Which user owns the app?
- Which app owns the API key?
- Which knowledge base is attached to the app?
- Does vector retrieval filter by `knowledge_base_id` and preferably `user_id` in SQL?
- Can the frontend request another user's resource by guessing an ID?

Secret checks:

- Is the app API key shown only once?
- Is only the API key hash stored?
- Is the upstream key encrypted?
- Do logs and error responses mask keys?
- Does frontend state clear one-time secrets after display?

## Step 4: Check RAG-Specific Boundaries

Ingestion:

- Does the parser support the file type?
- What happens on parse failure?
- Are chunk size and overlap persisted or traceable?
- Is embedding dimension validated before saving vectors?
- Are large jobs asynchronous or bounded?

Retrieval:

- Is SQL scoped before ordering by vector distance?
- Are topK, threshold, context-token limits, and per-chunk token limits enforced?
- Are duplicate or low-signal chunks filtered?
- Is no-hit behavior consistent with the app's policy?

Prompt:

- Are original messages preserved?
- Is the original system prompt preserved?
- Is RAG context clearly separated from user input?
- Are source labels safe and useful?

## Step 5: Check Streaming Boundaries

For `stream=true`:

- What validation occurs before the first byte is sent?
- How are upstream chunks mapped to client chunks?
- How is upstream cancellation handled when the client disconnects?
- What error event is emitted if upstream fails mid-stream?
- Is usage unsupported, approximated, or provided?

Document the behavior in README and tests.

## Step 6: Test The Contract

Good/base cases:

- Valid API key, ready knowledge base, retrieval hits, upstream success.
- Valid upload for a supported file type.
- Valid model config with masked response.

Bad cases:

- Invalid/revoked/expired API key.
- Knowledge base not ready.
- No retrieval hits under `STRICT_RAG`.
- Embedding dimension mismatch.
- Upstream timeout.
- Client disconnect during stream.
- Cross-tenant resource ID access.

Edge cases:

- Empty messages.
- Last message is not from user.
- Very long user message.
- Empty uploaded document.
- Duplicate chunks.
- Provider returns malformed response.

## Completion Checklist

Before implementation:

- [ ] Complete flow is mapped.
- [ ] Boundary formats are defined.
- [ ] Validation owner is clear.
- [ ] Tenant checks are SQL-level where needed.
- [ ] Secret masking/storage rules are defined.
- [ ] Error codes and response shapes are defined.
- [ ] Good/base/bad cases are known.

After implementation:

- [ ] API contract tests or focused unit tests cover the flow.
- [ ] Frontend types match backend DTO/VO shape.
- [ ] Database migrations match entity/model changes.
- [ ] Logs contain safe IDs and omit secrets/content.
- [ ] README/specs are updated if behavior changed.
- [ ] Cross-layer auth changes keep public `/v1/*` app API key auth separate from Admin `/api/admin/**` JWT auth.
