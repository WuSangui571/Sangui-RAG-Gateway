# Runtime Evidence Record

> Generated: 2026-06-01T20:29 UTC+8
> Environment: local development (Windows, PowerShell 5.1)
> Backend: http://localhost:8080
> Frontend: http://localhost:3000
> App ID: 5 (`demo-acceptance-app`)
> Admin User ID: 1

## Split-Provider Runtime Config

| Role | Provider | Base URL | Model | Dimension | Config ID | Status |
|---|---|---|---|---|---|---|
| Chat | sanguicode | `https://api.sanguicode.com` | `deepseek-v4-pro` | N/A | 5 | ENABLED |
| Embedding | dashscope | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `text-embedding-v4` | 1024 | 6 | ENABLED |

Knowledge Base: id=4, name=`demo-acceptance-kb`, status=READY, embedding_model=`text-embedding-v4`, dimension=1024.

App binding: default_model_config_id=5, default_knowledge_base_id=4.

## Smoke Test Run 1: Basic Mode (no request-log, no revoked-key)

**Command:**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<redacted>" `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?"
```

| # | Step | Result | Evidence |
|---|---|---|---|
| 1 | Backend health | PASS | HTTP 200, `code=OK`, `data.status=UP` |
| 2 | Frontend proxy health | PASS | HTTP 200, `code=OK` (JSON, not HTML) |
| 3 | Non-streaming chat | PASS | HTTP 200, `choices[0].message.content` present (bounded preview: "Sangui RAG Gateway provides an **OpenAI-compatible gateway integration style**...") |
| 4 | Streaming chat | PASS | SSE stream received, 134 data chunk(s), `[DONE]` present |
| 5 | Request-log validation | SKIP | `-AppId` and `-AdminUserId` not supplied |
| 6 | Revoked-key 401 | SKIP | `-VerifyRevokedKey` not supplied |

**Exit code: 0**

## Smoke Test Run 2: Full Mode (request-log + revoked-key)

**Command:**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<redacted>" `
  -AppId 5 `
  -AdminUserId 1 `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?" `
  -RevokedApiKey "<redacted>" `
  -VerifyRevokedKey
```

| # | Step | Result | Evidence |
|---|---|---|---|
| 1 | Backend health | PASS | HTTP 200, `code=OK`, `data.status=UP` |
| 2 | Frontend proxy health | PASS | HTTP 200, `code=OK` (JSON, not HTML) |
| 3 | Non-streaming chat | PASS | HTTP 200, `choices[0].message.content` present (bounded preview: "The Sangui RAG Gateway provides an OpenAI-compatible gateway integration style.") |
| 4 | Streaming chat | PASS | SSE stream received, 117 data chunk(s), `[DONE]` present |
| 5 | Request-log validation | PASS | See detail below |
| 6 | Revoked-key 401 | PASS | HTTP 401, `error.code=invalid_api_key` |

**Exit code: 0**

### Request-Log Evidence (Step 5)

| Field | Value |
|---|---|
| `request_id` | `9f111131-712a-41c0-8df8-50dd4cba1dd5` |
| `model` | `deepseek-v4-pro` |
| `provider_name` | `sanguicode` |
| `latency_ms` | 3845 |
| `hit_chunk_ids` | `[5]` (count=1) |

### Hit-Chunk Evidence (Step 5e)

| Field | Value |
|---|---|
| `chunk_id` | 5 |
| `document_id` | 5 |
| `knowledge_base_id` | 4 |
| `source_filename` | `sangui-demo-acceptance.md` |
| `chunk_index` | 0 |

Hit-chunk summaries count: 1. No full chunk content, embeddings, or provider bodies returned.

## Static Validation

| Check | Result | Notes |
|---|---|---|
| PSParser syntax check | PASS | `[System.Management.Automation.PSParser]::Tokenize` completed without errors |
| `git diff --check` | PASS | Only CRLF warnings on task-local `.jsonl`/`.md` files (not code) |
| Secret scan: `sk-sangui-` real keys | PASS | No real keys found in README.md, scripts/, or .trellis/spec/ |
| Secret scan: `api_key_encrypted`/`key_hash` in scripts | PASS | No matches |
| Secret scan: `Authorization: Bearer sk-sangui-` with real key | PASS | All matches use placeholders (`xxxx`, `...`, `<your-key>`) |
| Secret scan: `Invoke-RestMethod`/`Invoke-WebRequest` | PASS | Only in explanatory text about what NOT to use |
| Secret scan: `curl.exe -d $variable` | PASS | Only in explanatory text about what NOT to do for formal acceptance |

## Targeted Backend Tests

| Test Batch | Command | Result |
|---|---|---|
| Request-log | `mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test` | PASS |
| Gateway/Auth | `mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test` | PASS |
| RAG/Admin | `mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest,OpenAiCompatibleEmbeddingClientTest,DocumentAdminControllerTest,ModelConfigServiceTest,AppAdminControllerTest" test` | PASS |

## Frontend Checks

| Check | Command | Result |
|---|---|---|
| Typecheck | `npm run typecheck` | PASS |
| Build | `npm run build` | PASS (3018 modules, built in 21s) |

## Demo Acceptance Evidence Checklist

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | Backend health | PASS | HTTP 200, `code=OK`, `data.status=UP` |
| 2 | Frontend `/api` proxy health | PASS | JSON response, `code=OK` |
| 3 | Model config presence | PASS | App 5 has ENABLED Sanguicode chat config (id=5) bound as default |
| 4 | KB status `READY` | PASS | App 5 has bound KB (id=4), status=READY |
| 5 | Non-streaming chat success | PASS | HTTP 200, `choices[0].message.content` present |
| 6 | Streaming SSE success | PASS | `data:` chunks received, `data: [DONE]` present (117 chunks) |
| 7 | Request-log list/detail | PASS | `status=success`, model=`deepseek-v4-pro`, provider=`sanguicode`, latency_ms=3845, hit_chunk_ids=[5] |
| 8 | Hit-chunks safe metadata | PASS | chunk_id=5, document_id=5, kb_id=4, source_filename present, chunk_index=0 |
| 9 | Revoked-key 401 | PASS | HTTP 401, `error.code=invalid_api_key` |
| 10 | No secrets in output | PASS | No API keys, key hashes, encrypted keys, provider bodies, stack traces, or embedding vectors in script output |

## README/Script/Spec Consistency

| Document | Status | Notes |
|---|---|---|
| `README.md` | Aligned | Split-Provider Runtime Setup, Evidence Checklist (10 items), smoke parameter table, failure boundary classification all match script and spec |
| `scripts/demo-smoke.ps1` | Aligned | 6 steps, curl.exe, UTF-8 no-BOM, revoked-key opt-in, safe output only |
| `.trellis/spec/sangui-rag-gateway.md` | Aligned | "Implemented Demo Acceptance Automation Rule" matches script behavior and README |

No changes required to README, script, or spec.

## Key Cleanup

| Key ID | Name | Final Status |
|---|---|---|
| 9 | `smoke-runtime-evidence-20260601` | REVOKED |
| 10 | `smoke-revoke-test-20260601` | REVOKED |

Both temporary keys created for this evidence session have been revoked. No plaintext keys remain in any committed file or terminal artifact.
