# 生产/默认密钥安全基线收敛

## 背景

当前 `rag.gateway.secret-key` 是系统核心安全资产之一，既参与 upstream provider API key 的加密可信度，也参与 admin JWT 签名。已有生产配置 guard 主要约束 `prod` / `production` profile 下的弱配置，但本任务需要进一步收敛默认密钥、profile guard 和用途边界，避免公开弱密钥被误用于真实 provider key 加密或 admin JWT 签名。

本轮是 Codex / DeepSeek 双端协作任务：Codex 只负责范围判断、PRD、Trellis task/context、spec 读取、代码研究和测试计划；业务实现由 DeepSeek 执行。

## 任务分类

Complex Task。

原因：
- 涉及 backend configuration、deployment env、README/spec、startup guard、admin JWT、upstream key encryption 和测试合同。
- 触达安全资产和运行环境边界，不应作为单点 hotfix。
- 需要兼顾 dev/test 可启动性、生产误部署阻断、以及已有 encrypted provider key 的兼容性。

## 目标

- 收敛 `rag.gateway.secret-key` 的默认值和 profile guard，阻止公开弱主密钥在非测试运行环境中被误用。
- 明确 `rag.gateway.secret-key` 当前同时承担 upstream API key encryption key 与 admin JWT signing key 的事实边界。
- 在不破坏已有加密数据的前提下，形成“当前修复 + 后续分离迁移方案”的安全合同。
- 同步 `.env.example`、`deploy/docker-compose.yml`、`README.md` 和 `.trellis/spec/`，让配置 guard、文档和部署文件保持一致。

## 非目标

- 不重做 Spring Security、CORS、安全响应头、CSRF、cookie 策略等全套安全整改。
- 不做 Docker root 用户、CI 镜像扫描、Trellis gitlink 或大规模 CI/Docker 重构。
- 不在本任务中强制轮换已有 provider key 加密密钥或迁移历史密文。
- 不一次性拆分 JWT signing key 与 upstream encryption key 的运行配置，除非可以证明完全向后兼容且不需要数据迁移。
- 不修改前端 UI，除非实现过程中发现文档或类型合同必须同步。
- 不改变 `/v1/*` app API key auth 和 `/api/admin/**` admin JWT 的职责边界。

## 安全不变量

- 任何启动失败信息、日志、测试断言和文档示例都不得回显真实 secret、placeholder secret、provider key、admin JWT 或 encrypted key 的值。
- `test` profile 必须能继续使用测试安全默认值运行单元测试。
- `dev` profile 是否允许公开 placeholder 需要显式决策：若继续允许，必须确保它不会用于 production-like profile；若禁止，则必须给出可执行的本地开发配置路径。
- 非 `test` profile 下公开弱主密钥不得静默通过。若需要保留 `dev` 便利性，应通过显式 dev-only 或 local-only 合同表达，不允许无 profile / compose / staging 误启动。
- Guard 必须使用 Spring `Environment.getActiveProfiles()` 或等价的真实 active profile 来源，不得只解析 raw `spring.profiles.active` 字符串。

## API / Command / Payload Contract

本任务预期不新增外部 HTTP API、Admin API DTO、frontend type、DB schema 或 migration。

可能新增或调整的配置 contract：

| Env var | Spring property | Contract |
|---|---|---|
| `RAG_GATEWAY_SECRET_KEY` | `rag.gateway.secret-key` | 非 `test` 运行环境不得使用公开弱值；production-like profile 下必须非空、长度足够、非 placeholder。 |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `prod` / `production` 仍触发生产 guard；`prod,dev` / `production,test` 等冲突组合必须失败。 |
| 可能新增 `RAG_GATEWAY_ALLOW_WEAK_LOCAL_SECRET` | 可能新增 `rag.gateway.allow-weak-local-secret` | 仅当实现方判断需要保留本地 dev placeholder 时才可新增；必须默认 `false`，不得在 Compose 生产示例中默认放开。 |

现有命令合同需要保持：

```bash
cd backend
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
mvn -q "-Dtest=UpstreamApiKeyEncryptorTest,AdminJwtServiceTest" test
mvn -q -DskipTests compile
cd ..
docker compose --env-file .env.example -f deploy/docker-compose.yml config
git diff --check
```

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| `test` profile with test/default secret | Context starts; tests remain deterministic. | config binding / smoke test |
| no active profile with public placeholder secret | Startup fails unless explicitly documented as local-only allowed. Preferred outcome: fail visibly. | `ProductionConfigGuardTest` or new config guard test |
| `dev` profile with public placeholder secret | Must be explicitly decided and tested. If allowed, only `dev` alone can pass and README must warn it is not deployment-safe. If disallowed, local dev docs must show override. | guard test |
| `prod` profile with blank secret | Startup fails with safe message naming `rag.gateway.secret-key`; no value echo. | `ProductionConfigGuardTest` |
| `prod` profile with short secret | Startup fails with safe message. | `ProductionConfigGuardTest` |
| `prod` profile with known placeholder (`local-dev-change-me` or documented weak default) | Startup fails with safe message. | `ProductionConfigGuardTest` |
| `prod` profile with strong secret but unsafe DB/Redis/storage/output-capture settings | Existing production guard behavior still fails at the relevant property. | regression in existing guard tests |
| `prod` profile with strong secret and safe required env | Context starts. | `ProductionConfigGuardTest` / `ProductionContextSmokeTest` |
| `prod,dev` or `production,test` | Startup fails because production-like profile cannot combine with local/test profiles. | existing or expanded guard test |
| `AdminJwtService` signs with weak/default key outside test | Must be prevented by startup guard before service is operational. | smoke / guard test |
| `UpstreamApiKeyEncryptor` receives blank/null secret | Fails fast; no mock success fallback. | existing encryptor tests |
| Error/log output includes secret value | Test should assert failure messages name property only and do not contain the configured secret value. | guard/encryptor/admin JWT tests |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Non-test deployment requires strong `RAG_GATEWAY_SECRET_KEY`; production-like profile also keeps existing DB/Redis/storage/output-capture guardrails; Compose/env/docs/spec agree; tests prove weak placeholder cannot sign JWT or encrypt provider keys in real runtime. |
| Base | `test` profile and focused unit tests remain simple and deterministic; local development has a clear documented override path without weakening production/staging defaults. |
| Bad | Public placeholder stays accepted in no-profile or Compose deployment; guard only checks `prod` while staging/default runtime can encrypt provider keys with weak public key; failures echo secrets; implementation adds a silent fallback key; JWT and encryption key are split without migration/compatibility plan. |

## Required Tests And Assertion Points

- `ProductionConfigGuardTest`
  - Covers `test`, `dev`, no profile, `prod`, `production`, conflicting profiles, placeholder secret, blank secret, short secret, and strong secret.
  - Asserts failure messages include property names but not configured secret values.
- `ProductionContextSmokeTest`
  - Confirms startup-visible config failures/success paths with real Spring configuration wiring.
- `UpstreamApiKeyEncryptorTest`
  - Keeps AES-GCM round trip, random IV, blank/null secret failure, malformed payload failure, and no secret leakage.
- `AdminJwtServiceTest` or equivalent
  - Confirms admin JWT signing/validation still works with strong key and rejects invalid/expired token paths.
  - Does not assert or log raw signing key.
- Config binding tests if new properties are introduced
  - Validate defaults, boolean acknowledgement behavior, and no silent fallback.
- Deployment contract check
  - `docker compose --env-file .env.example -f deploy/docker-compose.yml config`
  - Confirms backend receives the intended env vars and placeholders are documented as unsafe for deployment.
- Static hygiene
  - `git diff --check`
  - Optional focused secret scan over changed docs/config for accidental real key examples.

All backend unit-test commands should be run with a hard timeout of 60 seconds when feasible.

## Expected Files To Review / Modify

Initial expected files before focused research:

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionConfigGuard.java`
- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionGuardProperties.java`
- `backend/src/main/java/com/sangui/raggateway/common/config/EncryptionProperties.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptor.java`
- `backend/src/main/java/com/sangui/raggateway/auth/AdminJwtService.java`
- `backend/src/test/java/com/sangui/raggateway/ProductionConfigGuardTest.java`
- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptorTest.java`
- `backend/src/test/java/com/sangui/raggateway/auth/AdminJwtServiceTest.java`
- `deploy/docker-compose.yml`
- `.env.example`
- `README.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/backend/error-handling.md`

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project source of truth for deployment env, production config guardrails, secret rules, admin JWT, upstream key encryption, and Compose contract.
- `.trellis/spec/backend/database-guidelines.md`: upstream API key storage/encryption baseline; `RAG_GATEWAY_SECRET_KEY` is the current encryption master key and must come from env outside local development.
- `.trellis/spec/backend/error-handling.md`: admin/gateway error shapes and secret-safe error-response rules.
- `.trellis/spec/backend/logging-guidelines.md`: forbids logging app API keys, upstream keys, encrypted keys, auth headers, passwords, prompts, provider bodies, and stack traces in client responses.
- `.trellis/spec/backend/quality-guidelines.md`: backend review checklist requires upstream keys encrypted at rest with AES-256-GCM using `RAG_GATEWAY_SECRET_KEY`; backend unit tests should run with 60-second timeout when feasible.
- `.trellis/spec/gateway/resilience.md`: upstream provider failures and request logging must stay bounded and secret-safe; no silent fallback.
- `.trellis/spec/security/rag-security.md`: API key/upstream key handling, admin JWT boundary, error/log secret safety.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: this task touches Docker Compose/env vars and security keys, so flow/contracts/tests must be explicit before implementation.

### Code Patterns Found

- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionConfigGuard.java`: current startup guard centralizes production-like checks and already uses `Environment.getActiveProfiles()`. It only runs when active profile contains `prod` or `production`.
- `backend/src/test/java/com/sangui/raggateway/ProductionConfigGuardTest.java`: existing guard tests cover prod good/bad configs, profile combinations, datasource/Redis/storage/output-capture failures, and safe secret error messages. It currently asserts `dev` and no-profile do not trigger guard.
- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`: existing ApplicationContextRunner pattern verifies startup-visible config wiring for `EncryptionConfig`, `AdminAuthConfig`, `GatewayAuthConfig`, and validated properties.
- `backend/src/main/java/com/sangui/raggateway/common/config/EncryptionConfig.java`: `@Profile("!test")` creates `UpstreamApiKeyEncryptor` from `rag.gateway.secret-key`.
- `backend/src/main/java/com/sangui/raggateway/common/config/AdminAuthConfig.java`: `@Profile("!test")` injects the same `rag.gateway.secret-key` into `AdminJwtService`, confirming the current shared-key boundary.
- `backend/src/main/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptor.java`: blank master key fails fast; key derivation is SHA-256 of the configured secret; encryption format is `v1:<iv>:<ciphertext>`.
- `backend/src/main/java/com/sangui/raggateway/common/security/AdminJwtService.java`: blank JWT secret and non-positive expiry fail fast; JJWT enforces HMAC key suitability. Token validation currently logs JWT exception messages, so implementation should review whether messages can expose token fragments.

### Current Risk Found

- `backend/src/main/resources/application.yml` defaults `spring.profiles.active` to `dev`.
- `backend/src/main/resources/application-dev.yml` defaults `rag.gateway.secret-key` to `local-dev-change-me`.
- `deploy/docker-compose.yml` also defaults backend `SPRING_PROFILES_ACTIVE` to `dev` and `RAG_GATEWAY_SECRET_KEY` to `local-dev-change-me`.
- `.env.example` and `README.md` document `RAG_GATEWAY_SECRET_KEY=local-dev-change-me` as a local placeholder.
- Because `ProductionConfigGuard` exits unless profile is `prod`/`production`, default `dev` or no-profile runtime can create `UpstreamApiKeyEncryptor` and `AdminJwtService` with the public placeholder.

### Files Likely To Modify

- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionConfigGuard.java`: extend or refactor guard invariant so non-test weak placeholder behavior is explicit and tested; keep existing production checks.
- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionGuardProperties.java`: only if a deliberate local weak-secret acknowledgement property is introduced.
- `backend/src/main/resources/application.yml`: review default active profile and blank secret behavior.
- `backend/src/main/resources/application-dev.yml`: review whether dev placeholder remains, is removed, or becomes explicitly local-only acknowledged.
- `deploy/docker-compose.yml`: remove weak secret fallback or require explicit env; ensure backend service receives any new guard env var if introduced.
- `.env.example`: replace weak secret line with a non-secret placeholder / instruction, or clearly mark dev-only with explicit override behavior.
- `README.md`: update env table and secret/provider key handling section to state strong-secret requirement and shared-use boundary.
- `.trellis/spec/sangui-rag-gateway.md`: update production guard contract and validation matrix.
- `.trellis/spec/backend/database-guidelines.md`: update encryption master-key local/default boundary and any future key-splitting migration notes.
- `.trellis/spec/backend/quality-guidelines.md`: update required tests for secret baseline changes.
- `.trellis/spec/security/rag-security.md`: document shared-key risk boundary and future split-key migration plan.
- `backend/src/test/java/com/sangui/raggateway/ProductionConfigGuardTest.java`: add no-profile/dev/test/placeholder/strong-secret matrix and ensure messages do not echo secret values.
- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`: add startup-visible coverage for weak default rejection in non-test contexts if behavior changes.
- `backend/src/test/java/com/sangui/raggateway/common/security/AdminJwtServiceTest.java`: add direct service-level checks only if service-level weak-key rejection is added; otherwise keep guard-level enforcement.
- `backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptorTest.java`: add direct service-level checks only if encryptor-level weak-key rejection is added; otherwise keep guard-level enforcement.

### Risk / Boundary Notes

- Preferred structural fix: one startup guard expresses runtime weak-secret policy; do not add a second independent validator that can drift.
- Be careful with `@Profile("!test")`: test profile currently avoids creating encryption/admin auth beans in broad contexts. Guard tests should use `ApplicationContextRunner` patterns already present.
- If weak-key rejection is added inside `UpstreamApiKeyEncryptor` or `AdminJwtService`, existing tests that instantiate them with shorter test secrets may need deliberate updates. That is higher blast radius than guard-only enforcement.
- Splitting JWT signing key and upstream encryption key is likely a follow-up migration, not the default implementation here, because changing encryption key derivation can make existing `api_key_encrypted` values undecryptable.
- Do not change DB schema or encryption payload format in this task unless a migration and compatibility plan is explicitly added.
- Do not weaken existing prod guard checks for datasource, Redis, storage type, local storage acknowledgement, or output capture acknowledgement.

## Implementation Approach

1. Inventory all references to `rag.gateway.secret-key`, `RAG_GATEWAY_SECRET_KEY`, known placeholder values, admin JWT signing, and upstream API key encryption.
2. Define the invariant in one place: which runtime profiles may accept weak/local secrets, and which must fail.
3. Prefer updating existing `ProductionConfigGuard` / properties over adding parallel guard implementations.
4. Keep `UpstreamApiKeyEncryptor` and `AdminJwtService` fail-fast behavior visible; do not add generated fallback secrets.
5. If key separation is introduced only as a future migration plan, document it in spec/README without changing runtime encryption format.
6. Update tests first around guard behavior, then implementation, then config/docs/spec.

## Handoff Notes For Implementer

- Treat this as a security baseline convergence, not a broad auth hardening task.
- Do not store, print, or document real secrets. Use placeholders such as `<set-a-strong-32-char-secret>` in docs.
- Do not make startup succeed by inventing a default secret at runtime.
- Do not weaken existing production guard checks for datasource, Redis, local storage, or output capture.
- If implementation requires a new property, document default, env var, allowed profiles, and tests in the same change.
