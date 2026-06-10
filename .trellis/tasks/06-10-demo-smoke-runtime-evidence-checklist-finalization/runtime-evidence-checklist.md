# Runtime Evidence Checklist

> Template for recording demo smoke acceptance evidence. Record metadata only.
> Never commit this file with real values in placeholder fields.
> Task-local copy of the durable template at `docs/runtime-evidence-checklist.md`.

## Recording Rules

- **Allowed**: safe metadata fields as listed in README.md Safe Evidence Fields. `knowledge_base_id` may appear as the `kb_id` label in smoke script output.
- **Forbidden**: raw assistant answers, bounded answer previews, raw SSE payloads, API keys, key hashes, encrypted keys, prompts, messages, augmented prompts, chunk content, chunk summary text, provider raw bodies, embedding vectors, stack traces, uploaded file artifacts, Playwright report contents, real `.env` secrets.
- **Placeholders**: use `<redacted>` for all secrets and API key values. Use safe IDs such as `<app-id>`, `<request-id>`, `<kb-id>`, `<admin-user-id>`.

---

## Smoke Run Metadata

| Field | Value |
|---|---|
| Date | `<YYYY-MM-DD HH:MM UTC+8>` |
| Environment | `<local / CI / staging>` |
| Backend URL | `<http://localhost:8080>` |
| Frontend URL | `<http://localhost:3000>` |
| App ID | `<app-id>` |
| Admin User ID | `<admin-user-id>` |
| Smoke Message | length=`<N>`, label=`<known demo prompt>` |

## Split-Provider Config (Safe Metadata Only)

| Role | Provider Name | Base URL | Chat Model / Embedding Model | Dimension | Config Status |
|---|---|---|---|---|---|
| Chat | `<provider-name>` | `<base-url>` | `<chat-model>` | N/A | ENABLED |
| Embedding | `<provider-name>` | `<base-url>` | `<embedding-model>` | `<dimension>` | ENABLED |

Knowledge Base: id=`<kb-id>`, status=READY, embedding_model=`<model>`, dimension=`<dim>`.

App binding: default_model_config_id=`<config-id>`, default_knowledge_base_id=`<kb-id>`.

---

## Command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<redacted>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "<known-demo-message>" `
  -RevokedApiKey "<redacted>" `
  -VerifyRevokedKey
```

---

## Smoke Steps

| # | Step | Result | Evidence (metadata only) |
|---|---|---|---|
| 1 | Backend health | `<PASS / FAIL>` | `<record HTTP status, code, data.status>` |
| 2 | Frontend proxy health | `<PASS / FAIL>` | `<record JSON vs HTML, code=OK>` |
| 3 | App readiness | `<PASS / FAIL / SKIP>` | `<record overall_status, check count, required check presence; no readiness JSON if unreviewed>` |
| 4 | Non-streaming chat | `<PASS / FAIL>` | `<record HTTP 200, content length only - never answer text>` |
| 5 | Streaming chat | `<PASS / FAIL>` | `<record SSE data line count, [DONE] present/absent>` |
| 6 | Request-log validation | `<PASS / FAIL / SKIP>` | `<see Request-Log Evidence below>` |
| 7 | Revoked-key 401 | `<PASS / FAIL / SKIP>` | `<record HTTP 401, error.code=invalid_api_key - never record revoked key>` |

**Exit code**: `<0 or non-zero>`

---

## Request-Log Evidence (Step 6)

| Field | Value |
|---|---|
| `request_id` | `<request-id>` |
| `model` | `<model>` |
| `provider_name` | `<provider-name>` |
| `latency_ms` | `<N>` |
| `messages_count` | `<N>` |
| `hit_chunk_ids` | `[<ids>]` (count=`<N>`) |

### Detail Validation

| Field | Value |
|---|---|
| `request_id` | `<request-id>` (matches list row) |
| `user_id` | `<N>` |
| `messages_count` | `<N>` |
| Safe fields present | `<PASS / FAIL>` (list missing field names only) |
| Forbidden-field scan | `<PASS / FAIL>` (list offending field names only) |

### Hit-Chunk Evidence (Step 6e)

| Field | Value |
|---|---|
| `chunk_id` | `<N>` |
| `document_id` | `<N>` |
| `knowledge_base_id` | `<N>` (script output label may be `kb_id`) |
| `source_filename` | `<filename>` |
| `chunk_index` | `<N>` |

Hit-chunk summaries count: `<N>`. Forbidden-field scan: `<PASS / FAIL>`. No chunk content, chunk summary text, embeddings, or provider bodies recorded.

---

## Good / Base / Bad Recording Examples

### Good: Complete Passing Smoke

Record only safe metadata:

| # | Step | Result | Evidence |
|---|---|---|---|
| 1 | Backend health | PASS | HTTP 200, `code=OK`, `data.status=UP` |
| 2 | Frontend proxy health | PASS | HTTP 200, `code=OK` (JSON, not HTML) |
| 3 | App readiness | PASS | `overall_status=READY`, 6 checks present, no forbidden fields |
| 4 | Non-streaming chat | PASS | HTTP 200, content length=`<N>` |
| 5 | Streaming chat | PASS | SSE `<N>` data chunk(s), `[DONE]` present |
| 6 | Request-log validation | PASS | `request_id=<request-id>`, `model=<model>`, `provider_name=<name>`, `latency_ms=<N>`, `hit_chunk_ids=[<ids>]`; detail safe fields present; hit-chunks metadata present; no forbidden fields |
| 7 | Revoked-key 401 | PASS | HTTP 401, `error.code=invalid_api_key` |

**Exit code: 0**. Do not record answer body, raw SSE, keys, prompt, messages array, chunk content, or chunk summary text.

### Base: Readiness Not READY

Record only boundary metadata:

| # | Step | Result | Evidence |
|---|---|---|---|
| 1 | Backend health | PASS | HTTP 200, `code=OK` |
| 2 | Frontend proxy health | PASS | JSON, `code=OK` |
| 3 | App readiness | FAIL | `overall_status=<status>`, boundary `<readiness/retrieval/auth/embedding>`, failing check(s): `<key>=<status>` |
| 4-7 | (skipped or partial) | SKIP | (readiness failure stops or subsequent steps may fail independently) |

**Exit code: non-zero**. Do not paste raw readiness JSON if it contains unreviewed values.

### Base: Request-Log No Matching Row

Record only query and failure boundary:

| # | Step | Result | Evidence |
|---|---|---|---|
| 1–5 | Health, chat, stream | PASS | (as above, metadata only) |
| 6 | Request-log validation | FAIL | List query: `page=1`, `page_size=5`, `status=success`. No recent success log matched smoke `Message` prefix. Boundary: `request-log`. |

**Exit code: non-zero**. Do not accept stale request rows as substitute evidence.

### Bad: Revoked-Key Verification Fails

Record only safe error metadata:

| # | Step | Result | Evidence |
|---|---|---|---|
| 1–6 | Health through request-log | PASS/FAIL | (as applicable) |
| 7 | Revoked-key 401 | FAIL | `-VerifyRevokedKey` enabled. Observed HTTP `<N>`, error code `<code or missing>`. Expected HTTP 401, `error.code=invalid_api_key`. Boundary: `auth`. |

**Exit code: non-zero**. Never record the revoked key value.

---

## Static Validation

| Check | Result | Notes |
|---|---|---|
| `git diff --check` | `<PASS / FAIL>` | |
| `rg` forbidden-field scan on committed files | `<PASS / FAIL>` | No `sk-sangui-*`, `api_key_encrypted`, `key_hash`, `provider_response_body`, `stack_trace`, `augmented_prompt`, `chunk_content`, `Authorization: Bearer sk-sangui-` |
| PSParser syntax check (if script changed) | `<PASS / SKIP>` | |

## Backend Tests (if backend changed - otherwise SKIP)

| Batch | Command | Result |
|---|---|---|
| Request-log | `mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test` | `<PASS / FAIL / SKIP>` |
| Gateway/Auth | `mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test` | `<PASS / FAIL / SKIP>` |
| RAG/Admin | `mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest,AppAdminControllerTest" test` | `<PASS / FAIL / SKIP>` |

## Frontend Checks (if frontend changed - otherwise SKIP)

| Check | Command | Result |
|---|---|---|
| Typecheck | `npm run typecheck` | `<PASS / FAIL / SKIP>` |
| Build | `npm run build` | `<PASS / FAIL / SKIP>` |

## Key Cleanup

| Key ID | Name | Final Status |
|---|---|---|
| `<key-id>` | `<name>` | `<REVOKED / ACTIVE>` |

All temporary keys created for this evidence session must be revoked. No plaintext keys may remain in any committed file or terminal artifact.
