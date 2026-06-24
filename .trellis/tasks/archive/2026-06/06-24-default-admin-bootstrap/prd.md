# Default Admin Bootstrap

## Goal

Make a fresh Sangui-RAG-Gateway deployment usable immediately by creating the first admin user only when it is safe and explicitly allowed by the active runtime profile/configuration.

This task is backend-only and focused on the first-use admin login path. It must not change gateway chat behavior, RAG retrieval, document ingestion, frontend UX, database schema shape, or API compatibility beyond documenting the new runtime configuration contract.

## Classification

Complex Task.

Reason: the implementation is small to medium in code size, but it crosses startup lifecycle, profile detection, production guardrails, password hashing, database idempotency, logging secrecy, and isolated tests.

## Scope

### In Scope

- Add backend configuration properties under `rag.admin-auth`:
  - `default-admin-username`
  - `default-admin-password`
  - `allow-default-admin`
- Add startup bootstrap logic that checks whether `sys_user` is empty.
- Create exactly one default admin user only when:
  - `sys_user` is empty, and
  - the runtime is `dev` or no-profile local mode, or `rag.admin-auth.allow-default-admin=true` is explicitly configured, and
  - a valid default admin password is configured.
- Use existing `PasswordHasher` to produce BCrypt `password_hash`.
- Skip creation when `sys_user` is non-empty.
- Keep logs secret-safe: username may be logged; plaintext password and hash must never be logged.
- Add tests for dev/no-profile, prod, idempotency, password hashing, and login compatibility.
- Update spec/docs only if needed to make the configuration contract durable.

### Out of Scope

- No frontend changes.
- No new public REST endpoint.
- No schema migration unless code research proves the existing `sys_user` schema is insufficient.
- No default app, knowledge base, model config, app API key, or sample data seeding.
- No hardcoded production password.
- No fallback login path or hidden mock success.
- No automatic admin creation when existing users are present.
- No password or hash exposure in logs, API responses, README examples, tests, or committed evidence.

## Runtime Configuration Contract

### Spring Properties

| Property | Type | Default / expectation | Notes |
|---|---|---|---|
| `rag.admin-auth.default-admin-username` | string | local/dev default may be `admin`; production should configure explicitly if used | Trim/validate non-blank before insertion. |
| `rag.admin-auth.default-admin-password` | string | local/dev may have a documented safe local placeholder; production must provide a strong explicit value when bootstrap is allowed | Must never be logged or exposed. |
| `rag.admin-auth.allow-default-admin` | boolean | `false` | Explicit acknowledgement required outside dev/no-profile mode. |

### Environment Variables

| Environment variable | Spring property |
|---|---|
| `RAG_ADMIN_AUTH_DEFAULT_ADMIN_USERNAME` | `rag.admin-auth.default-admin-username` |
| `RAG_ADMIN_AUTH_DEFAULT_ADMIN_PASSWORD` | `rag.admin-auth.default-admin-password` |
| `RAG_ADMIN_AUTH_ALLOW_DEFAULT_ADMIN` | `rag.admin-auth.allow-default-admin` |

### Commands / Deployment Surface

No new CLI command is introduced.

Docker/Compose and `.env.example` may be updated only to document safe local defaults and explicit production acknowledgement. Production examples must not contain a real reusable password.

### API / Payload Fields

No REST API request or response shape changes are expected.

Login compatibility must remain:

```http
POST /api/admin/auth/login
Content-Type: application/json

{
  "username": "<default-admin-username>",
  "password": "<default-admin-password>"
}
```

Successful login must still return the existing admin login envelope/VO and signed JWT. Do not add bootstrap-specific fields to login responses.

## Validation / Error Matrix

| Scenario | Expected behavior | Failure boundary |
|---|---|---|
| `sys_user` empty, active `dev`, username/password configured | Insert one `sys_user` row with username, BCrypt password hash, active status, timestamps as existing entity conventions require | Startup/bootstrap |
| `sys_user` empty, no active profile, local config considered dev-like by existing guard conventions | Same as dev if existing production guard allows no-profile local mode | Startup/bootstrap |
| `sys_user` non-empty | Skip bootstrap; do not modify existing rows | Idempotency |
| `prod` active, `allow-default-admin=false` or absent | Do not create default admin | Production safety |
| `prod` active, `allow-default-admin=true`, password missing/blank | Fail fast or reject bootstrap visibly with safe exception naming property only | Production safety |
| `prod` active, `allow-default-admin=true`, password is local/dev placeholder | Fail fast or reject bootstrap visibly with safe exception naming property only | Production safety |
| `prod` active, `allow-default-admin=true`, strong password configured | Create one admin only if table empty | Explicit production bootstrap |
| Username blank when bootstrap would run | Fail fast or reject bootstrap visibly with safe exception naming property only | Config validation |
| Password hash produced | Stored value is not equal to plaintext and verifies with `PasswordHasher.matches` / login path | Password security |
| Logging during create/skip/reject | Logs may include username and action; never include password/hash | Secret safety |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Fresh dev deployment starts, `sys_user` is empty, one active admin user is inserted with BCrypt hash, login with configured credentials succeeds, logs print username only. Re-running startup skips because the table is non-empty. |
| Base | Existing deployment already has at least one `sys_user`; startup leaves all users unchanged regardless of default admin config. |
| Bad | Production startup silently creates an admin from an implicit/local password; password/hash appears in logs; bootstrap creates duplicate users; bootstrap bypasses `PasswordHasher`; login succeeds through a second hidden fallback instead of the persisted `sys_user` row. |

## Acceptance Criteria

- [ ] Fresh dev/no-profile startup can create the first admin when `sys_user` is empty and valid default credentials are configured.
- [ ] Existing `sys_user` rows prevent bootstrap from inserting or mutating users.
- [ ] Production profile does not create an implicit default admin when `allow-default-admin` is false/missing.
- [ ] Production profile with `allow-default-admin=true` rejects missing, blank, or unsafe local/default passwords.
- [ ] Production profile with `allow-default-admin=true` and a strong configured password creates one admin only when the table is empty.
- [ ] Stored password hash is BCrypt, not equal to plaintext, and verifies through the existing `PasswordHasher` and admin login service.
- [ ] Logs and exceptions never echo plaintext password or password hash.
- [ ] No frontend, RAG, gateway chat, request-log, storage, or schema behavior changes are introduced.

## Expected Files To Modify

Implementation owner may adjust this list after code research, but should keep the change bounded:

- `backend/src/main/java/com/sangui/raggateway/common/config/AdminAuthConfig.java`
- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionConfigGuard.java`
- `backend/src/main/java/com/sangui/raggateway/auth/...` or a new narrowly scoped admin bootstrap package/class
- `backend/src/main/java/com/sangui/raggateway/user/UserService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `.env.example`
- `deploy/docker-compose.yml` if env passthrough is needed
- `README.md` only for deployment/default-admin instructions if needed
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md` or `.trellis/spec/security/rag-security.md` only if the durable bootstrap contract changes those specs
- Targeted backend tests under `backend/src/test/java/com/sangui/raggateway/**`

## Required Tests And Assertion Points

### Targeted Unit / Context Tests

Run from `backend/` with the project 60 second unit-test timeout rule:

```bash
mvn -q "-Dtest=DefaultAdminBootstrapServiceTest,AdminAuthServiceTest,PasswordHasherTest" test
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
mvn -q "-Dtest=UserServiceTest,AdminAuthFilterTest,AdminJwtServiceTest" test
mvn -q -DskipTests compile
```

If the implementation chooses different test class names, replace `DefaultAdminBootstrapServiceTest` with the actual targeted bootstrap test class and keep the same assertion coverage.

### Assertions

- Empty-table dev path calls insert exactly once with expected username/status and a hash that differs from plaintext.
- Existing-user path does not insert or update.
- Production disallowed path does not insert.
- Production explicit-allow plus missing/blank/local password fails visibly and secret-safely.
- Production explicit-allow plus strong password inserts exactly once.
- `PasswordHasher.matches(plaintext, storedHash)` succeeds.
- `AdminAuthService.login(username, plaintext)` succeeds using the created row.
- Logs/exceptions in tests do not contain plaintext password or hash if log capture is practical.

### Broader Verification

```bash
mvn -q test
git diff --check
```

Full `mvn test` is preferred after implementation because startup/config changes can affect unrelated Spring context tests. If it exceeds the required 60 second backend unit-test timeout, record the timeout and keep targeted evidence.

## Planning Notes

- Prefer a single explicit bootstrap component/service with injected `UserMapper`/`UserService`, `PasswordHasher`, config properties, and `Environment`.
- Keep production profile detection aligned with `ProductionConfigGuard`: use `Environment.getActiveProfiles()` and existing profile semantics rather than parsing raw `spring.profiles.active`.
- Treat `ProductionConfigGuard` as the existing runtime policy hub if startup failure rules need to be centralized.
- Do not add silent fallback behavior. If config is unsafe in a path where bootstrap is requested, fail visibly with property names only.
- If using a count query for `sys_user`, keep it simple and database-backed; avoid relying on cached application state.
