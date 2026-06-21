# Dev Secret HS256 Local Contract

## Goal

Converge the local development secret contract so `rag.gateway.secret-key` has one consistent meaning across startup guard, Admin JWT HS256 signing, upstream API key AES encryption, local config, README, and Trellis specs.

The immediate problem is that the previous production baseline allows `local-dev-change-me` in `dev` or no-profile runtime when `rag.production-guard.allow-weak-local-secret=true`, but `AdminJwtService` uses JJWT `Keys.hmacShaKeyFor(...)`, which requires an HMAC key strong enough for HS256. A weak placeholder can therefore pass the guard while the Admin JWT bean still fails during a full non-test Spring context startup.

This task must produce a clear local development runtime contract: guard success and complete Spring context startup must agree.

## Classification

Complex Task.

Reason: this is a cross-layer security/configuration contract involving backend startup config, Admin JWT signing, upstream key encryption, docs/spec, `.env.example`, Compose env wiring, and focused tests. It is not a DB/API/frontend feature, but it crosses enough runtime layers that a PRD and Trellis context are required before implementation.

## Scope

In scope:

- `rag.gateway.secret-key` validation behavior for `dev`, no active profile, `test`, and production-like profiles.
- `ProductionConfigGuard` contract and tests around weak placeholders, minimum HS256-compatible length, and safe error messages.
- `AdminJwtService` startup behavior and tests for weak/short secret handling.
- `UpstreamApiKeyEncryptor` tests only as needed to preserve AES derivation behavior and blank-secret failure.
- `application-dev.yml`, `.env.example`, README, and Trellis spec updates for the chosen local-dev strategy.
- `deploy/docker-compose.yml` config verification if env documentation or defaults are touched.

Out of scope:

- Do not split JWT signing key and AES encryption key in this task.
- Do not migrate or re-encrypt existing `api_key_encrypted` provider keys.
- Do not add or change database schema/migrations.
- Do not change Admin API DTO/VO fields, frontend types, or frontend pages.
- Do not change gateway `/v1/*` behavior, app API key auth, retrieval, prompt construction, or request-log output capture behavior.
- Do not introduce a silent fallback key, generated ephemeral secret, or mock-success startup path.

## Decision

Use a local development placeholder that is non-production, explicit, and HS256-compatible.

Required direction:

- Replace the default dev placeholder value `local-dev-change-me` with a safe non-production placeholder of at least 32 UTF-8 characters, for example `local-dev-hs256-secret-change-me-32chars`.
- Keep production-like profiles strict: `prod`/`production` require a non-blank, non-placeholder secret of at least 32 characters and must reject known local/documentation placeholders.
- Remove the effective local-dev path where `rag.production-guard.allow-weak-local-secret=true` lets `local-dev-change-me` pass startup. Acknowledgement must not bypass HS256 minimum strength.
- Preserve the future split-key note: `rag.gateway.secret-key` still has dual roles for now, and splitting JWT/AES keys is deferred to a separate migration task.

If implementation finds that exact placeholder length/name should differ, the replacement value may change, but it must remain:

- at least 32 UTF-8 characters,
- obviously non-production,
- documented as a placeholder to replace for real deployments,
- listed in guard tests as a known local placeholder rejected in production-like profiles.

## Current Code Findings

- `backend/src/main/resources/application.yml` sets `spring.profiles.active` default to `dev` and binds `rag.gateway.secret-key` from `RAG_GATEWAY_SECRET_KEY`.
- `backend/src/main/resources/application-dev.yml` currently defaults `rag.gateway.secret-key` to `local-dev-change-me`.
- `ProductionConfigGuard` rejects blank and documented placeholder in all non-test profiles, but only requires length `>= 32` in production-like profiles.
- `ProductionConfigGuard` currently allows `local-dev-change-me` in `dev` or no-profile when `rag.production-guard.allow-weak-local-secret=true`.
- `AdminJwtService` constructs `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`; JJWT enforces HS256-compatible key strength and can fail on short secrets.
- `UpstreamApiKeyEncryptor` derives AES-256 key material by SHA-256 hashing `rag.gateway.secret-key`; it only rejects blank secret and can technically work with short placeholders, so JWT is the stricter runtime consumer.
- `ProductionContextSmokeTest` currently proves guard-only behavior separately from Admin JWT bean wiring, but does not assert that weak-local acknowledgement still fails full Admin JWT context startup.

## API / Command / Payload Fields

No HTTP API, DTO, VO, or database payload changes are planned.

Runtime configuration contract:

| Environment variable | Spring property | Contract |
|---|---|---|
| `RAG_GATEWAY_SECRET_KEY` | `rag.gateway.secret-key` | Required for non-test runtime. Must be non-blank. Must be at least 32 UTF-8 characters for any dev/no-profile/prod runtime that creates Admin JWT beans. Must not be known documentation/local placeholders in production-like profiles. |
| `RAG_PRODUCTION_ALLOW_WEAK_LOCAL_SECRET` | `rag.production-guard.allow-weak-local-secret` | Legacy acknowledgement must not permit a secret that fails Admin JWT HS256 strength. Prefer documenting it as deprecated/ineffective for `local-dev-change-me`, or remove references if implementation chooses to fully retire it. Never enable in production-like profiles. |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `test` skips guard. `dev` is local default. `prod`/`production` activate production guard and must not be combined with `dev` or `test`. |

Command contracts:

```bash
cd backend
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
mvn -q "-Dtest=AdminJwtServiceTest,UpstreamApiKeyEncryptorTest" test
mvn -q -DskipTests compile
cd ..
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| `test` profile with weak/blank placeholder | Guard remains inactive; tests that intentionally instantiate Admin JWT still own their own validation | `ProductionConfigGuardTest`, `ProductionContextSmokeTest` |
| `dev` profile with new HS256-compatible local placeholder | Guard passes and Admin JWT bean can be created | `ProductionConfigGuardTest`, `ProductionContextSmokeTest` |
| No active profile with new HS256-compatible local placeholder | Guard passes and Admin JWT bean can be created when AdminAuthConfig is loaded | `ProductionConfigGuardTest`, `ProductionContextSmokeTest` |
| `dev` or no-profile with blank secret | Startup fails visibly; message names `rag.gateway.secret-key` or JWT boundary but does not echo the value | `ProductionConfigGuardTest`, `ProductionContextSmokeTest`, `AdminJwtServiceTest` |
| `dev` or no-profile with `local-dev-change-me` and `allow-weak-local-secret=true` | Must not be accepted as a complete runtime startup. Prefer guard failure with a safe message naming HS256/minimum length; at minimum Admin JWT context smoke must fail visibly without echoing the secret. | `ProductionConfigGuardTest`, `ProductionContextSmokeTest` |
| `dev` or no-profile with documented placeholder `<set-a-strong-32-char-secret>` | Startup fails; ack does not bypass it; placeholder value is not echoed | `ProductionConfigGuardTest`, `ProductionContextSmokeTest` |
| `prod` with new local placeholder | Startup fails; production-like profiles reject known local placeholders even if length is valid | `ProductionConfigGuardTest` |
| `prod` with strong non-placeholder secret and safe DB/Redis/storage/output config | Startup passes | `ProductionConfigGuardTest` |
| `AdminJwtService` constructed with short secret | Throws a safe `IllegalArgumentException` before/around JJWT weak-key failure; message does not echo secret | `AdminJwtServiceTest` |
| `UpstreamApiKeyEncryptor` with strong local placeholder | AES encrypt/decrypt behavior unchanged | `UpstreamApiKeyEncryptorTest` |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Local dev default secret is HS256-compatible, clearly marked non-production, and lets guard plus Admin JWT bean wiring succeed. Production-like startup still rejects placeholders and weak/short secrets. README/spec/.env.example all describe the same contract. |
| Base | Developer sets a real 32+ character local `RAG_GATEWAY_SECRET_KEY`; dev/no-profile startup succeeds, AES encryption and Admin JWT signing both work, and no secret value is printed in startup errors. |
| Bad | `local-dev-change-me` with weak-local acknowledgement passes guard but full Spring context fails later in Admin JWT; docs claim one contract while tests enforce another; errors echo the secret value; implementation silently generates a fallback key; implementation starts JWT/AES key split or provider-key migration. |

## Files Likely To Modify

Expected implementation files:

- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionConfigGuard.java`
- `backend/src/test/java/com/sangui/raggateway/ProductionConfigGuardTest.java`
- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/AdminJwtServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptorTest.java` if needed for strong placeholder coverage
- `.env.example`
- `README.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/security/rag-security.md` if wording about dual-use or local-dev secret changes

Files that should usually remain unchanged:

- DB migrations under `backend/src/main/resources/db/migration`
- Frontend source and types
- Gateway `/v1/*` controllers/filters
- Model config encryption payload format
- `ModelConfigService` unless tests reveal an existing direct secret assumption requiring only a narrowly scoped adjustment

## Required Tests

Run targeted tests first:

```bash
cd backend
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
mvn -q "-Dtest=AdminJwtServiceTest,UpstreamApiKeyEncryptorTest" test
mvn -q -DskipTests compile
```

Run deploy/config contract check if docs/env/Compose are touched:

```bash
cd ..
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

Optional but useful if implementation touches auth filter/config wiring beyond tests:

```bash
cd backend
mvn -q "-Dtest=AdminAuthFilterTest,AdminAuthServiceTest,AdminAuthControllerTest" test
```

## Assertion Points

- Guard failure messages name the boundary/property but do not echo `local-dev-change-me`, the documented placeholder, or user-provided secret values.
- `ProductionContextSmokeTest` must cover Admin JWT bean creation with the chosen dev/no-profile secret path, not guard-only startup.
- Tests must distinguish guard slice success from complete relevant Spring context success.
- Docs must state that a value may pass AES derivation while still being invalid for Admin JWT HS256; the effective shared-secret requirement is therefore the stricter HS256-compatible minimum.
- No test should depend on real provider keys, Docker services, PostgreSQL, Redis, or external network calls.

## Planning Self-Check

- Acceptance criteria: guard and complete Spring context startup agree for local dev secret behavior.
- Forbidden scope: no JWT/AES key split, no provider-key migration, no DB/API/frontend changes.
- Estimated files: listed above.
- Required tests: listed above.
- Guidelines read: backend, security, gateway, cross-layer, logging/error/quality/database/project spec.
- Open questions: none; use the HS256-compatible local placeholder strategy unless implementation reveals a blocking issue.
