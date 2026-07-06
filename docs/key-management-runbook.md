# Key Management Runbook

> API key rotation, revocation, and leak recovery procedures for Sangui-RAG-Gateway.

## API Key Lifecycle

App API keys (`sk-sangui-*`) are credentials used by external systems to call `/v1/*` gateway endpoints. The full plaintext key is shown **only once** at creation time and stored as a hash in the database - it is never recoverable.

Key statuses: `ACTIVE`, `DISABLED`, `REVOKED`, `EXPIRED`.

## PowerShell Variables

The examples below assume these variables are set before running the commands:

```powershell
$BackendBaseUrl = "http://localhost:8080"
$AdminToken = "<admin-jwt-from-login>"
$utf8 = New-Object System.Text.UTF8Encoding($false)
```

## Revoke an API Key

```
POST /api/admin/api-keys/{id}/revoke
Authorization: Bearer <admin-jwt>
```

```powershell
curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
  -H "Authorization: Bearer $AdminToken"
```

After revocation, the key must fail public `/v1/*` calls with HTTP 401 `invalid_api_key`.

## Disable an API Key

```
POST /api/admin/api-keys/{id}/disable
Authorization: Bearer <admin-jwt>
```

```powershell
curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/disable" `
  -H "Authorization: Bearer $AdminToken"
```

Disabling is idempotent. Disabling a revoked key returns `400 INVALID_REQUEST`.

## Create a New API Key

```
POST /api/admin/apps/{appId}/api-keys
Authorization: Bearer <admin-jwt>
Content-Type: application/json
{"name":"key-name","expires_at":null}
```

```powershell
$utf8 = New-Object System.Text.UTF8Encoding($false)
$createBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($createBodyPath, '{"name":"replacement-key","expires_at":null}', $utf8)
curl.exe -s -X POST "$BackendBaseUrl/api/admin/apps/<app-id>/api-keys" `
  -H "Authorization: Bearer $AdminToken" `
  -H "Content-Type: application/json" `
  --data-binary "@$createBodyPath"
Remove-Item -LiteralPath $createBodyPath -Force
```

Copy the full `key` field immediately - it will never be shown again. Never commit the plaintext key.

## If the Plaintext Key Is Lost

The full plaintext key is shown only once and cannot be recovered. If lost:

1. **Create a new API key** for the affected app.
2. **Copy the new key immediately.**
3. **Update all clients** to use the new key.
4. **Optionally revoke the old key** as a precaution.

## If the Plaintext Key Is Leaked

If you suspect the key has been exposed (committed to a repo, shared in logs, visible in a screenshot):

1. **Revoke the leaked key immediately:**
   ```powershell
   curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
     -H "Authorization: Bearer $AdminToken"
   ```
2. **Verify the revoked key is rejected:**
   ```powershell
   curl.exe -s -X POST "$BackendBaseUrl/v1/chat/completions" `
     -H "Content-Type: application/json" `
     -H "Authorization: Bearer <leaked-key>" `
     -d '{"messages":[{"role":"user","content":"test"}]}'
   ```
   Expected: HTTP 401 with `error.code=invalid_api_key`.
3. **Create a fresh API key** and copy it immediately.
4. **Update all clients** to use the new key.
5. **Remove all plaintext artifacts**: clear clipboard, delete scratch files, remove from terminal history, scrub repositories or logs.

## After Demo - Revocation Checklist

1. **Revoke the demo key:**
   ```powershell
   curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
     -H "Authorization: Bearer $AdminToken"
   ```
2. **Verify rejection:** check HTTP 401 with `invalid_api_key`.
3. **Delete local copy-paste artifacts** from clipboard, scratch files, notes.
4. **Remove uploaded knowledge files** from `backend/data/` if they contain proprietary content.

## Model Config Key Rotation

Upstream provider API keys are configured through model configs and encrypted at rest with AES-256-GCM. To rotate:

```
PUT /api/admin/model-configs/{id}
Authorization: Bearer <admin-jwt>
Content-Type: application/json
{"name":"config-name","provider_name":"openai-compatible","base_url":"https://api.example.com","api_key":"<new-provider-key>","chat_model":"model-name"}
```

Rules:
- Omitting `api_key` from the PUT body preserves the existing encrypted key.
- Blank `api_key` (empty string) is rejected with `400 INVALID_REQUEST`.
- Non-blank `api_key` rotates the encrypted value.
- Model config disable/enable never rotates or clears the upstream key.
- The upstream key is never returned in admin responses.

After rotation, run a non-streaming chat to confirm the new upstream key is active.

## Important Notes

- Use `curl.exe` (Windows system curl), not the PowerShell `curl` alias.
- For formal verification, write JSON bodies to temp files with UTF-8 no-BOM encoding and submit with `curl.exe --data-binary "@<path>"`.
- Never commit plaintext API keys, upstream provider keys, or `backend/data/` to git.
