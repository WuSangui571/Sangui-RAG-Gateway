# V0.2 Fresh Demo Key Cleanup Confirmation

Date: 2026-06-10 19:03 UTC+8
Task: V0.2 Fresh Demo Key Cleanup Confirmation
Executor: DeepSeek (runtime via operator-provided plaintext key held outside repo)

---

## 1. Fresh Demo Key Safe Metadata

| Field | Value |
|---|---|
| Key ID | 28 |
| Name | `demo-acceptance-20260610` |
| App ID | 5 |
| Admin User ID | 1 |
| Key Prefix | `sk-sangui-yuE2Roo9` |
| Created At | 2026-06-10T11:01:56 UTC+8 |
| Status Before | `ACTIVE` |
| Status After | `REVOKED` |
| Revoked At | 2026-06-10T11:03:19 UTC+8 |

No plaintext key, Authorization header, key hash, or encrypted key is recorded.

## 2. Revocation Result

| Assertion | Result |
|---|---|
| Admin API endpoint | `POST /api/admin/api-keys/28/revoke` with `X-Admin-User-Id: 1` |
| HTTP status | 200 |
| Response envelope | `code=OK`, `message=success` |
| `data.status` | `REVOKED` |
| `data.revoked_at` present | Yes — `2026-06-10T11:03:19.073384949` |
| `data.key` absent | Confirmed — API response omits `key` and `key_hash` (ApiKeyVO contract) |
| `data.key_hash` absent | Confirmed |

Revocation was idempotent. The key was `ACTIVE` before this call and transitioned to `REVOKED`.

## 3. Revoked-Key 401 Verification

| Assertion | Result |
|---|---|
| Public endpoint | `POST /v1/chat/completions` via frontend proxy (localhost:3000) |
| HTTP status | **401** |
| `error.code` | `invalid_api_key` |
| `error.message` | `Invalid API key.` |
| `error.type` | `invalid_request_error` |
| Boundary | `auth` |

The revoked key was provided at runtime by the operator and was never committed to any tracked file. The verification used a temporary UTF-8 no-BOM request body file created with `[System.IO.Path]::GetTempFileName()` and deleted immediately after the call.

Response body shape matches `GatewayAuthFilter.writeAuthError()` contract — OpenAI-compatible `{"error":{"message":"...","type":"invalid_request_error","code":"invalid_api_key"}}`.

## 4. Release Decision

**READY FOR V0.2 RELEASE CANDIDATE**

Rationale:
- Fresh demo key `demo-acceptance-20260610` (ID 28) has been revoked and carries server-side `REVOKED` status with `revoked_at` timestamp.
- Public gateway correctly rejects the revoked key with HTTP 401 and `error.code=invalid_api_key`.
- This was the sole remaining V0.2 release-candidate blocker (previously documented as `UNCONFIRMED` / `PENDING MANUAL CONFIRMATION` in the release-readiness closeout and evidence pack).
- All other V0.2 release readiness closeout checks passed independently and remain valid (see `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md`).

## 5. Repository Safety

| Check | Result | Notes |
|---|---|---|
| Plaintext key committed | No | Key was held only in runtime memory and shell process arguments; never written to any tracked file. |
| Authorization header committed | No | `Authorization: Bearer <key>` appeared only in runtime curl commands. |
| Raw response body committed | No | Only HTTP status and error code metadata recorded. |
| Terminal transcript committed | No | |
| Provider body / prompt / messages / chunk content / stack trace committed | No | |

Codex re-ran the PRD-required `rg` forbidden-field scan after this file was written. Hits were limited to rule text, placeholders, spec contracts, historical task rules, or scanner arrays; no plaintext key, concrete `Authorization: Bearer sk-sangui-*`, key hash, encrypted key, provider body, raw answer, chunk content, or stack trace was found.
