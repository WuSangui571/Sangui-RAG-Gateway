# V0.2 Demo Acceptance Evidence Pack

Metadata-only formal acceptance evidence recorded from `scripts/demo-smoke.ps1`. No real keys, raw answers, raw SSE, prompts, messages, chunk content, chunk summary text, provider bodies, stack traces, or .env values are committed.

## Smoke Run Metadata

| Field | Value |
|---|---|
| Date | 2026-06-10 17:57 UTC+8 |
| Environment | local |
| Backend URL | http://localhost:8080 |
| Frontend URL | http://localhost:3000 |
| App ID | 5 |
| Admin User ID | 1 |
| Smoke Message | length=55, label=`What integration style does Sangui RAG Gateway provide?` |

## Command

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

## Smoke Steps

| # | Step | Result | Evidence (metadata only) |
|---|---|---|---|
| 1 | Backend health | PASS | HTTP 200, `code=OK`, `data.status=UP` |
| 2 | Frontend proxy health | PASS | HTTP 200, `code=OK` (JSON, not HTML) |
| 3 | App readiness | PASS | `overall_status=READY`, 6 checks present: `app`, `default_model_config`, `default_knowledge_base`, `knowledge_base_status`, `active_api_key`, `embedding_config`; no forbidden fields |
| 4 | Non-streaming chat | PASS | HTTP 200, content length=196 |
| 5 | Streaming chat | PASS | SSE 135 data chunk(s), `[DONE]` present |
| 6 | Request-log validation | PASS | request_id=`6a67c4a6-8a89-49eb-a678-5a37285d46e7`, model=`deepseek-v4-pro`, provider_name=`sanguicode`, latency_ms=4120, hit_chunk_ids=[5] (count=1); detail safe fields present; hit-chunks metadata present; no forbidden fields |
| 7 | Revoked-key 401 | PASS | HTTP 401, `error.code=invalid_api_key` |

Exit code: `0`

## Request-Log Evidence

| Field | Value |
|---|---|
| `request_id` | `6a67c4a6-8a89-49eb-a678-5a37285d46e7` |
| `model` | `deepseek-v4-pro` |
| `provider_name` | `sanguicode` |
| `latency_ms` | 4120 |
| `messages_count` | 1 |
| `hit_chunk_ids` | `[5]` (count=1) |

### Detail Validation

| Field | Value |
|---|---|
| `request_id` | `6a67c4a6-8a89-49eb-a678-5a37285d46e7` (matches list row) |
| `user_id` | 1 |
| `messages_count` | 1 |
| Safe fields present | PASS |
| Forbidden-field scan | PASS |

### Hit-Chunk Evidence

| Field | Value |
|---|---|
| `chunk_id` | 5 |
| `document_id` | 5 |
| `knowledge_base_id` | 4 (script output label `kb_id`) |
| `source_filename` | `sangui-demo-acceptance.md` |
| `chunk_index` | 0 |

Hit-chunk summaries count: 1. Forbidden-field scan: PASS. No chunk content, chunk summary text, embeddings, or provider bodies recorded.

## Static Validation

| Check | Result | Notes |
|---|---|---|
| `PSParser` syntax check (`demo-smoke.ps1`) | PASS | Valid PowerShell 5.1 syntax |
| `git diff --check HEAD` | PASS | CRLF warning for `.trellis/workspace/sangui/journal-2.md` only; no whitespace errors |
| Trailing-whitespace scan on changed Trellis files | PASS | No trailing whitespace hits |
| Real generated key regex scan | PASS | No concrete generated app key values found |
| Forbidden-field scan (`Select-String`) on task dir, docs, README.md, scripts | PASS | All hits are rule text/placeholders only (PRD prohibited-field lists, script's `$forbidden` array, README placeholder `sk-sangui-<...>`, docs template rules). No real secrets, keys, raw answers, raw SSE, chunk content, provider bodies, or stack traces found. |

## Test Recording

| Area | Command | Result |
|---|---|---|
| Backend tests | SKIP | No backend implementation changes made |
| Frontend typecheck | SKIP | No frontend implementation changes made |
| Frontend build | SKIP | No frontend implementation changes made |

## Key Cleanup

| Key ID | Name | Final Status |
|---|---|---|
| `<redacted>` | fresh demo key | PENDING MANUAL CONFIRMATION: revoke unless intentionally retained for follow-up manual testing |
| `<redacted>` | revoked demo key | Already REVOKED (verified 401) |

All temporary keys created for this evidence session must be revoked before final release evidence is treated as closed. Codex could not verify the fresh demo key ID or server-side final status from committed metadata; the operator must confirm revocation or explicitly document intentional retention for follow-up manual testing. Static repository scans found no plaintext keys in the reviewed files; terminal/runtime artifacts outside the repository were not reviewed and must not be committed.
