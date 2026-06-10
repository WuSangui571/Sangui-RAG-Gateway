# Focused Research - V0.2 Fresh Demo Key Cleanup Confirmation

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: Project source of truth for app API key lifecycle, OpenAI-compatible `/v1/*` auth errors, demo smoke automation, safe evidence fields, and V0.2 boundaries.
- `.trellis/spec/backend/error-handling.md`: Defines Admin API key revoke behavior and public gateway `401 invalid_api_key` response shape for disabled/revoked/expired keys.
- `.trellis/spec/backend/logging-guidelines.md`: Defines safe operational fields and forbids complete app keys, Authorization headers, upstream keys, raw prompts/messages, provider bodies, and stack traces in logs/evidence.
- `.trellis/spec/backend/quality-guidelines.md`: Requires API key auth and secret safety coverage; implementation tests are only needed if implementation files change.
- `.trellis/spec/security/rag-security.md`: Defines secret/evidence boundaries and forbidden response/log fields.
- `.trellis/spec/gateway/resilience.md`: Confirms public gateway errors must be normalized and safe; no provider/raw internal details should be exposed.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: Applies because the task touches API key lifecycle, public `/v1/*` auth, release evidence, and secret boundaries.
- `README.md`: User-facing runbook for API key create/list/disable/revoke, demo cleanup, and revoked-key `401 invalid_api_key` verification.
- `docs/runtime-evidence-checklist.md`: Durable metadata-only evidence template; key cleanup section requires temporary evidence-session keys to be revoked.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md`: Current release readiness note. It records `READY WITH OPERATOR-ACTION REQUIRED` and fresh demo key final state `UNCONFIRMED`.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`: Latest formal demo evidence pack. It records the fresh demo key as `PENDING MANUAL CONFIRMATION` and the separate revoked-key fixture as already verified.

## Code Patterns Found

- Admin list/create API keys: `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
  - `GET /api/admin/apps/{appId}/api-keys` returns safe `ApiKeyVO` metadata scoped by `X-Admin-User-Id`.
  - `POST /api/admin/apps/{appId}/api-keys` is the only endpoint that returns plaintext once via `ApiKeyCreateVO`; this task must not call create unless explicitly instructed.
- Admin revoke API key: `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`
  - `POST /api/admin/api-keys/{id}/revoke` returns `ApiResponse<ApiKeyVO>`, omitting plaintext `key` and `key_hash`.
  - Same-user ownership is enforced by `X-Admin-User-Id`; missing key returns 404, cross-user returns 403.
- Revoke service behavior: `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java`
  - `ACTIVE|DISABLED -> REVOKED` sets `revoked_at`.
  - `REVOKED -> REVOKED` is idempotent and returns the existing key.
  - Valid gateway keys must be `ACTIVE` and unexpired.
- Public gateway auth behavior: `backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java`
  - Applies to `/v1/*`.
  - Hashes the plaintext Bearer token, looks up the key, requires `ApiKeyService.isValid`.
  - Disabled/revoked/expired/missing/malformed keys return HTTP `401` with OpenAI-compatible `{"error":{"code":"invalid_api_key"}}`.
  - Logs auth failure reason only; does not echo the token.
- README cleanup runbook:
  - Revoke demo key with `POST /api/admin/api-keys/{id}/revoke`.
  - Verify the revoked key with `POST /v1/chat/completions`.
  - Record HTTP `401` and `invalid_api_key` only.

## Files Likely To Modify

Preferred Trellis-only changes:

- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md`: task-local safe confirmation note to be created by the executor.
- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/task.json`: status/phase updates via Trellis scripts.
- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/implement.jsonl`: implement context.
- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/check.jsonl`: check context.
- `.trellis/workspace/sangui/index.md` and `.trellis/workspace/sangui/journal-2.md`: session recording after completion.

Optional:

- `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md`: update only if choosing to replace the prior `UNCONFIRMED` release-readiness note directly. A task-local confirmation note is safer and sufficient unless the operator wants the archived closeout updated.

Files that should not be modified:

- `backend/src/**`
- `frontend/src/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/**`
- `.github/**`
- `scripts/demo-smoke.ps1`

## Risk / Boundary Notes

- The plaintext fresh demo key is needed for the 401 verification but must remain runtime-only and outside tracked files.
- The key id/name/app id are safe metadata; the key prefix is generally allowed but not necessary for release confirmation. Avoid recording prefix unless needed to disambiguate.
- A successful Admin revoke alone is not enough; the release blocker closes only after public `/v1/chat/completions` returns `401 invalid_api_key` with the revoked key.
- A `401` from missing/malformed Authorization would not prove the specific fresh demo key was revoked. The operator must ensure the request uses the actual fresh demo key at runtime while not committing it.
- Do not copy raw terminal output into the repository. Summarize status/error code and timestamps.
- Do not broaden the task into new key creation, rate limiting, Admin auth hardening, frontend UX changes, or smoke script changes.

## Required Tests

Runtime/manual:

- `GET /api/admin/apps/{appId}/api-keys` to identify the key by safe metadata.
- `POST /api/admin/api-keys/{id}/revoke` to revoke or confirm already revoked.
- `POST /v1/chat/completions` with the revoked plaintext key to confirm HTTP `401`.
- Inspect only `error.code` and confirm it is `invalid_api_key`.

Repository safety:

```powershell
git diff --check
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
rg -n "READY WITH OPERATOR-ACTION REQUIRED|READY FOR V0.2 RELEASE CANDIDATE|UNCONFIRMED|PENDING MANUAL CONFIRMATION|REVOKED|fresh demo key" README.md docs .trellis/spec .trellis/tasks
git status --short
```

Implementation tests:

- Not required if no implementation files change.
- If implementation files are touched unexpectedly, run:

```powershell
cd backend
mvn -q "-Dtest=ApiKeyAdminControllerTest,ApiKeyServiceTest,GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest" test
mvn -q -DskipTests compile
```

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```
