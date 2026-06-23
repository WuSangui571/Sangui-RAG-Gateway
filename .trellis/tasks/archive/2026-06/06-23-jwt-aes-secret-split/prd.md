# JWT/AES Secret Split

## Task Classification

Complex Task.

Reason: this task changes the security/configuration contract across backend startup config,
Admin JWT signing, upstream provider API key encryption, deployment environment variables,
production guard validation, README/spec documentation, and regression tests. It is a
structural configuration fix, not a local constant change.

## Goal

Split the current shared `rag.gateway.secret-key` responsibility into two explicit secrets:

- Admin JWT signing secret: used only by `AdminJwtService`.
- Upstream API key AES encryption secret: used only by `UpstreamApiKeyEncryptor`.

After the change, each secret must have one clear meaning, startup validation must name the
specific missing/invalid property, and existing encrypted upstream provider keys must remain
migratable without changing the encrypted payload format.

## Current Problem

Current implementation uses one property for two unrelated purposes:

```text
RAG_GATEWAY_SECRET_KEY
-> rag.gateway.secret-key
   -> AdminAuthConfig -> AdminJwtService HS256 signing
   -> EncryptionProperties -> UpstreamApiKeyEncryptor AES-256-GCM key derivation
```

This creates a security and operations ambiguity:

- Rotating the admin JWT signing secret also breaks upstream API key decryption.
- Keeping the AES encryption secret stable also keeps old JWT signing material alive.
- Docs and production guard can only describe one generic "gateway secret", not the actual
  cryptographic boundary.

## Required Contract

### New Environment Variables and Spring Properties

| Purpose | Environment variable | Spring property | Required behavior |
|---|---|---|---|
| Admin JWT signing | `RAG_ADMIN_AUTH_JWT_SECRET` | `rag.admin-auth.jwt-secret` | Required in all non-test profiles. At least 32 UTF-8 characters. Must not be documented or local placeholders. Production-like profiles must not reuse the AES secret. |
| Upstream key AES encryption | `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` | `rag.gateway.encryption.secret-key` | Required in all non-test profiles. At least 32 UTF-8 characters. Must not be documented or local placeholders. Production-like profiles must not reuse the JWT secret. |
| Legacy shared secret | `RAG_GATEWAY_SECRET_KEY` | `rag.gateway.secret-key` | Deprecated compatibility input only. It must no longer be the primary source of truth for either new secret after this task. |

### Development Defaults

`application-dev.yml` may provide safe local-only placeholders, but they must be distinct:

```yaml
rag:
  admin-auth:
    jwt-secret: ${RAG_ADMIN_AUTH_JWT_SECRET:local-dev-admin-jwt-secret-change-me-32chars}
  gateway:
    encryption:
      secret-key: ${RAG_GATEWAY_ENCRYPTION_SECRET_KEY:local-dev-aes-key-secret-change-me-32chars}
```

The exact placeholder strings may be adjusted during implementation, but they must be:

- At least 32 UTF-8 characters.
- Clearly local-only.
- Different from each other.
- Rejected in production-like profiles.

### Production Requirements

In `prod` or `production` profiles:

- `rag.admin-auth.jwt-secret` must be non-blank, 32+ characters, non-placeholder.
- `rag.gateway.encryption.secret-key` must be non-blank, 32+ characters, non-placeholder.
- The two effective secrets must not be equal.
- Failure messages must name only the property and rule; they must not echo secret values.
- Production-like profiles must still reject `prod,dev` and `production,test`.

### Migration Strategy

Do not change the existing encrypted upstream API key payload format:

```text
v1:<base64url-iv>:<base64url-ciphertext>
```

Do not add a hidden fallback decrypt path that tries multiple secrets. That would create a
second source of truth and could mask bad deployment state.

For existing deployments with encrypted provider keys:

1. Copy the current production value of `RAG_GATEWAY_SECRET_KEY` into
   `RAG_GATEWAY_ENCRYPTION_SECRET_KEY`.
2. Generate a new independent value for `RAG_ADMIN_AUTH_JWT_SECRET`.
3. Remove or ignore the deprecated `RAG_GATEWAY_SECRET_KEY`.
4. Restart. Existing encrypted provider keys should decrypt because the AES secret is preserved.
5. Existing admin JWTs should become invalid because the signing secret changed. This is acceptable;
   admins must log in again.

If the AES secret is intentionally changed, old encrypted provider keys will fail to decrypt until
the model configs are re-entered or rotated. This must fail visibly as the existing
`model_config_not_ready` / decrypt failure path, not through silent fallback.

## API / Command / Payload Fields

No public `/v1/*` API request or response shape changes.

No Admin API DTO/VO changes.

No database schema or encrypted payload migration.

Changed runtime/config commands and payloads:

```text
RAG_ADMIN_AUTH_JWT_SECRET=<strong-jwt-secret-32-plus-chars>
RAG_GATEWAY_ENCRYPTION_SECRET_KEY=<existing-or-new-aes-secret-32-plus-chars>
RAG_GATEWAY_SECRET_KEY=<deprecated-do-not-use>
```

Spring property contract:

```yaml
rag:
  admin-auth:
    jwt-secret: ${RAG_ADMIN_AUTH_JWT_SECRET:}
    jwt-expiration-seconds: ${RAG_ADMIN_AUTH_JWT_EXPIRATION_SECONDS:86400}
  gateway:
    encryption:
      secret-key: ${RAG_GATEWAY_ENCRYPTION_SECRET_KEY:}
```

README/deployment command that must still work after updating `.env.example`:

```bash
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| `test` profile with missing JWT/AES secrets | Guard skipped; test context still starts where configs are profile-disabled. | Existing test profile behavior. |
| `dev` profile with dev defaults | Context starts; `AdminJwtService` and `UpstreamApiKeyEncryptor` beans can be created. | `ProductionContextSmokeTest`. |
| Non-test profile with blank JWT secret | Startup fails naming `rag.admin-auth.jwt-secret`; value not echoed. | `ProductionConfigGuardTest`, `ProductionContextSmokeTest`. |
| Non-test profile with blank AES secret | Startup fails naming `rag.gateway.encryption.secret-key`; value not echoed. | `ProductionConfigGuardTest`, `ProductionContextSmokeTest`. |
| Non-test profile with short JWT secret | Startup fails naming `rag.admin-auth.jwt-secret` and `32`; value not echoed. | `ProductionConfigGuardTest`, `AdminJwtServiceTest`. |
| Non-test profile with short AES secret | Startup fails naming `rag.gateway.encryption.secret-key` and `32`; value not echoed. | `ProductionConfigGuardTest`, `UpstreamApiKeyEncryptorTest`. |
| Production profile with either local placeholder | Startup fails naming the specific property and placeholder rule; value not echoed. | `ProductionConfigGuardTest`. |
| Production profile with equal JWT and AES secrets | Startup fails with a safe message explaining the two secrets must be distinct. | `ProductionConfigGuardTest`. |
| Existing encrypted provider key with AES secret preserved | `decrypt(encrypt(value))` or stored sample encrypted with old AES secret still decrypts after JWT secret changes. | `UpstreamApiKeyEncryptorTest` or config smoke test. |
| Existing encrypted provider key with AES secret changed | Decrypt fails visibly; no fallback secret attempt hides the failure. | `UpstreamApiKeyEncryptorTest`, gateway/model config tests if needed. |
| Admin JWT secret changed, AES secret unchanged | Old JWT validation fails; upstream key decrypt still works. | `AdminJwtServiceTest`, `UpstreamApiKeyEncryptorTest`. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Production deployment sets distinct strong `RAG_ADMIN_AUTH_JWT_SECRET` and `RAG_GATEWAY_ENCRYPTION_SECRET_KEY`; guard passes; admin JWT signing works; upstream key encrypt/decrypt works; compose config includes both env vars; docs explain migration from old shared secret. |
| Base | Local dev uses distinct safe placeholders from `application-dev.yml` / `.env.example`; startup and focused tests pass; no production placeholder is accepted in prod. |
| Bad | One generic `RAG_GATEWAY_SECRET_KEY` remains the primary documented secret; rotating JWT breaks provider key decrypt unexpectedly; production accepts equal secrets; decrypt silently tries both old and new secrets; docs imply encrypted payloads are automatically migrated. |

## Expected Files To Modify

Backend implementation:

- `backend/src/main/java/com/sangui/raggateway/common/config/AdminAuthConfig.java`
  - Inject `rag.admin-auth.jwt-secret` instead of `rag.gateway.secret-key`.
- `backend/src/main/java/com/sangui/raggateway/common/config/EncryptionProperties.java`
  - Rebind encryption secret to `rag.gateway.encryption.secret-key` or a narrowly named equivalent.
- `backend/src/main/java/com/sangui/raggateway/common/config/EncryptionConfig.java`
  - Keep bean creation pattern; consume the updated properties object.
- `backend/src/main/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptor.java`
  - Error messages should name `rag.gateway.encryption.secret-key`, not the old shared property.
- `backend/src/main/java/com/sangui/raggateway/common/security/AdminJwtService.java`
  - Constructor validation can remain local, but error messages may need property-specific wording via caller/config tests.
- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionConfigGuard.java`
  - Validate both effective properties and production distinctness.
- `backend/src/main/resources/application.yml`
  - Add new properties and env mappings.
- `backend/src/main/resources/application-dev.yml`
  - Add distinct local placeholders.

Deployment/docs/spec:

- `.env.example`
- `deploy/docker-compose.yml`
- `README.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/security/rag-security.md`

Tests:

- `backend/src/test/java/com/sangui/raggateway/ProductionConfigGuardTest.java`
- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/AdminJwtServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptorTest.java`
- Potentially `backend/src/test/java/com/sangui/raggateway/auth/AdminAuthServiceTest.java` if constructor fixture names change.

## Explicit Non-Goals

- Do not change `/v1/*` OpenAI-compatible API contracts.
- Do not change Admin API DTO/VO fields.
- Do not add a database migration.
- Do not change the encrypted upstream key payload format.
- Do not implement bulk re-encryption.
- Do not add hidden dual-secret decrypt fallback.
- Do not change app API key hashing, gateway API key auth, retrieval SQL, prompt construction,
  document ingestion, object storage, or frontend UI.
- Do not mix this task with the already-fixed CI streaming test race except keeping that diff as a
  separate pending change if it has not been committed yet.

## Required Tests And Assertion Points

Run from `backend/` with a 60 second timeout per command when feasible:

```bash
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
mvn -q "-Dtest=AdminJwtServiceTest,UpstreamApiKeyEncryptorTest" test
mvn -q "-Dtest=AdminAuthFilterTest,AdminAuthServiceTest,AdminAuthControllerTest" test
mvn -q "-Dtest=ModelConfigServiceTest,ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q -DskipTests compile
```

Repository-level checks:

```bash
docker compose --env-file .env.example -f deploy/docker-compose.yml config
git diff --check
rg "RAG_GATEWAY_SECRET_KEY|rag.gateway.secret-key" backend .env.example deploy README.md .trellis/spec
```

The final `rg` is expected to find only documented/deprecated compatibility references and no
primary wiring from Admin JWT or AES encryption to the old shared property.

## Planning Self-Check

- Acceptance criteria are explicit: two separate secrets, production validation, migration path,
  docs/spec sync, and tests.
- Forbidden scope is explicit: no API/DB/frontend/payload/encryption-format migration.
- Expected files are listed.
- Required tests are listed with assertion points.
- Specific guideline files have been read before writing this PRD:
  backend directory/database/error/logging/quality, gateway resilience, security rag-security,
  cross-layer thinking, and code-reuse thinking.
- No unresolved API/DB/frontend DTO alignment exists because this is a runtime config contract only.

