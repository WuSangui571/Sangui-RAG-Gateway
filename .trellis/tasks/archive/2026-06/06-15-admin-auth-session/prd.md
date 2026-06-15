# Admin 登录与会话认证

## Goal

把 Admin Console 从临时 `X-Admin-User-Id` 身份入口迁移到最小可用的登录与会话认证机制：后端提供用户账号、密码哈希、登录接口、JWT 签发与校验；Admin API 统一从认证上下文读取 `userId`；前端用登录态替代手工输入 user id，API client 自动携带认证凭证。

本任务只做最小可用 Admin auth，不引入复杂 RBAC、团队空间、权限角色矩阵、注册邀请、刷新令牌、多租户组织模型、第三方登录或完整 Spring Security 改造。

## Scope Classification

Complex Task.

原因：该任务跨数据库迁移、后端认证过滤器/上下文、多个 Admin Controller、前端 shell/API client/types、错误合同、测试矩阵和 spec 更新。它同时影响权限边界与现有 App/model/key/KB/document/request-log 全流程，不能作为局部热修。

## Current Project State

- 当前分支：`feature/admin-auth-session`，非 `main` 专属任务分支，工作区干净。
- 当前 Trellis 状态：没有 active/current task；本任务创建在 `.trellis/tasks/06-15-admin-auth-session`。
- 最近 journal 已记录完成：
  - Session 54 `Request log output observability policy`：完成 request-log 输出预览元数据、显式预览访问、审计与 spec。
  - Session 55 `App Output Capture Switch Management`：完成 app 级 output capture 开关 API/UI、测试、spec，并记录完整回归测试通过。
- 当前前后端 Admin API 仍依赖临时 `X-Admin-User-Id` header；本任务就是移除对该 header 的直接信任。

## Requirements

### Backend Auth / User Model

- 新增最小 Admin 用户表 `sys_user` 或与 spec 一致的账号表，至少包含：
  - `id BIGSERIAL PRIMARY KEY`
  - `username VARCHAR(...) NOT NULL UNIQUE`
  - `password_hash VARCHAR(...) NOT NULL`
  - `status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'`
  - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- 用户状态最小集合：`ACTIVE`, `DISABLED`。不实现角色/RBAC。
- 新增密码哈希服务：
  - 不存储明文密码。
  - 登录校验必须使用哈希验证。
  - 哈希算法必须可测试、可迁移；若使用依赖，需要在 `backend/pom.xml` 中明确引入并测试。
- 新增最小登录接口：
  - `POST /api/admin/auth/login`
  - Request JSON:
    ```json
    {
      "username": "admin",
      "password": "plaintext password"
    }
    ```
  - Success `200 ApiResponse<AdminLoginVO>`:
    ```json
    {
      "code": "OK",
      "message": "success",
      "data": {
        "access_token": "<jwt>",
        "token_type": "Bearer",
        "expires_at": "2026-06-15T12:00:00",
        "user": {
          "id": 100,
          "username": "admin"
        }
      }
    }
    ```
  - Failure keeps admin `ApiResponse` envelope, not OpenAI `/v1/*` error shape.
- 新增当前用户接口：
  - `GET /api/admin/auth/me`
  - Header: `Authorization: Bearer <jwt>`
  - Success `200 ApiResponse<AdminUserVO>` with `id`, `username`, `status`.
- 新增 Admin JWT 签发与校验：
  - JWT 只用于 `/api/admin/**`，不得影响 `/v1/*` app API key auth。
  - 使用 `rag.gateway.secret-key` 或明确新增 `rag.admin-auth.jwt-secret` 作为签名密钥；禁止硬编码 secret。
  - JWT payload 至少包含 user id、username、issued-at、expires-at。
  - 过期、缺失、格式错误、签名错误、用户不存在或禁用均返回 401。

### Backend Admin API Context

- 新增 Admin 认证上下文，例如 `AdminAuthContext` / `AdminAuthContextHolder`，只保存安全身份字段：
  - `userId`
  - `username`
  - optional `requestId`
- 新增 `/api/admin/**` servlet filter 或 interceptor：
  - 对 `/api/admin/auth/login` 放行。
  - 对 `/api/admin/auth/me` 及其他 `/api/admin/**` 校验 `Authorization: Bearer <jwt>`。
  - 成功后设置 Admin auth context，finally 清理。
  - 失败直接返回 `401 ApiResponse.error("UNAUTHORIZED", "Authentication required")` 或等价清晰 401 代码。
- 替换 Admin controller 对 `X-Admin-User-Id` 的直接信任：
  - 不再通过 `@RequestHeader("X-Admin-User-Id") Long userId` 获取身份。
  - controller 从统一 Admin auth context 获取 userId，再沿用现有 service 的 owner-scoped 方法。
  - 保留 service 层 `findByIdAndUserId`、`listByUserId` 等租户边界，不把权限逻辑只放在 filter。
- 保持 401/403 边界：
  - 未登录、token 缺失/无效/过期/用户禁用：401。
  - 已登录但访问其他用户资源：403 `FORBIDDEN`，通用 `Access denied`。
  - 已登录但资源不存在：404 `NOT_FOUND`。
- `/v1/*` 的 `GatewayAuthFilter`、app API key 认证和 OpenAI-compatible error shape 不得改变。

### Frontend Auth State / API Client

- `AdminShell` 当前输入 user id 的临时入口改为登录页：
  - 输入 username/password。
  - 登录成功后保存 access token 和当前用户。
  - UI 显示当前 username 或 user id。
  - logout 清除 token、用户、选中 app 等敏感/会话状态。
- 全局认证状态只保存：
  - access token/session marker
  - current user safe metadata
  - 不保存密码。
- `frontend/src/api/http.ts` 改为统一携带 `Authorization: Bearer <token>`。
  - Admin API client 函数不再要求传 `adminUserId` 参数。
  - `/api/admin/auth/login` 不携带 token。
  - 401 应触发清晰的登录态失效处理，至少在页面展示错误并允许重新登录。
- 新增/更新 frontend types：
  - `types/auth.ts` with login DTO/VO/current user types。
  - 现有 API payload 继续使用后端 snake_case 字段，不做契约层翻译。
- 保持现有页面工作流：
  - model configs
  - apps
  - API keys
  - knowledge bases/documents
  - request logs/output preview
  - smoke page

### Spec Updates

本任务编码完成时必须更新 executable contracts：

- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md` if new reusable cross-layer rule is introduced

Spec 必须明确：

- Admin auth API signatures / payload fields.
- `Authorization: Bearer <admin-jwt>` replaces `X-Admin-User-Id` for Admin APIs.
- 401 vs 403 vs 404 matrix.
- DB migration and seed/admin bootstrap boundary.
- Frontend token storage and clearing boundary.
- Existing `/v1/*` API key auth remains separate.

## API / Payload Contract

### Login

```http
POST /api/admin/auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "plaintext password"
}
```

Success:

```http
HTTP 200
```

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "access_token": "<jwt>",
    "token_type": "Bearer",
    "expires_at": "2026-06-15T12:00:00",
    "user": {
      "id": 100,
      "username": "admin"
    }
  }
}
```

### Current User

```http
GET /api/admin/auth/me
Authorization: Bearer <jwt>
```

Success:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 100,
    "username": "admin",
    "status": "ACTIVE"
  }
}
```

### Existing Admin APIs

All existing `/api/admin/**` endpoints keep their paths and request/response bodies, but identity source changes:

```http
Authorization: Bearer <admin-jwt>
```

`X-Admin-User-Id` must no longer be trusted or required for Admin APIs after this task.

### Public Gateway APIs

No change:

```http
GET /v1/models
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
```

## Validation / Error Matrix

| Scenario | HTTP | Code | Response shape | Required behavior |
|---|---:|---|---|---|
| Login missing username/password | 400 | `INVALID_REQUEST` | `ApiResponse` | No token issued. |
| Login malformed JSON | 400 | `INVALID_REQUEST` | `ApiResponse` | Body content not echoed. |
| Login unknown username | 401 | `UNAUTHORIZED` or `INVALID_CREDENTIALS` | `ApiResponse` | Generic login failure; do not reveal whether username exists. |
| Login wrong password | 401 | `UNAUTHORIZED` or `INVALID_CREDENTIALS` | `ApiResponse` | Generic login failure. |
| Login disabled user | 401 | `UNAUTHORIZED` | `ApiResponse` | No token issued. |
| Valid login | 200 | `OK` | `ApiResponse<AdminLoginVO>` | Token issued with expiry and safe user metadata. |
| Admin API missing Authorization | 401 | `UNAUTHORIZED` | `ApiResponse` | No controller business method should trust fallback identity. |
| Admin API non-Bearer Authorization | 401 | `UNAUTHORIZED` | `ApiResponse` | No token content echoed. |
| Admin API invalid signature/token | 401 | `UNAUTHORIZED` | `ApiResponse` | No stack trace or token echoed. |
| Admin API expired token | 401 | `UNAUTHORIZED` | `ApiResponse` | User can re-login. |
| Admin API token user missing/disabled | 401 | `UNAUTHORIZED` | `ApiResponse` | Context not set. |
| Logged-in user accesses own app/model/KB/key/log | 200 | `OK` | Existing VO | Existing behavior preserved. |
| Logged-in user guesses another user's app/model/KB/key/log | 403 | `FORBIDDEN` | `ApiResponse` | Generic `Access denied`; no sensitive data returned. |
| Logged-in user accesses missing resource | 404 | `NOT_FOUND` | `ApiResponse` | Existing 404 boundary preserved. |
| `/v1/*` missing app API key | 401 | `invalid_api_key` | `OpenAiErrorResponse` | Existing GatewayAuthFilter behavior unchanged. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Admin user logs in, frontend stores token/current user, App/model/key/KB/document/request-log pages load without user-id input, all Admin calls carry `Authorization`, and owner-scoped resources behave as before. |
| Good | Cross-user resource access with a valid token returns 403 and does not execute unsafe data-returning queries. |
| Good | `/v1/models` and `/v1/chat/completions` still authenticate only app API keys and still return OpenAI-compatible error shapes. |
| Base | Fresh local dev can create/use a minimal admin user through documented migration/seed/bootstrap boundary without hardcoded production secrets. |
| Base | Frontend reload with token either restores current user via `/api/admin/auth/me` or returns to login on 401. |
| Bad | Any Admin API still accepts `X-Admin-User-Id` as an authority source, frontend keeps requiring admin user id, JWT secret is hardcoded, password is persisted/logged, or cross-user access downgrades from 403 to 401/404 inconsistently. |
| Bad | Public `/v1/*` starts accepting admin JWT or Admin `/api/admin/**` starts accepting `sk-sangui-*` app keys. |

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary includes `User` as admin console owner and requires tenant isolation.
- `.trellis/spec/backend/directory-structure.md`: `auth` and `user` modules are intended for admin console login and identity; `common.security` is for filters/context/helpers.
- `.trellis/spec/backend/database-guidelines.md`: `sys_user` is listed as core table; tenant-sensitive tables already carry `user_id`.
- `.trellis/spec/backend/error-handling.md`: Admin APIs use `ApiResponse`; public `/v1/*` uses OpenAI-compatible error shape. Existing specs still document `X-Admin-User-Id`, so this task must update them.
- `.trellis/spec/backend/logging-guidelines.md`: auth logs must not log Authorization header, tokens, passwords, or future production admin identity headers.
- `.trellis/spec/backend/quality-guidelines.md`: tenant isolation, secret handling, admin API regressions, and tests are completion gates.
- `.trellis/spec/frontend/directory-structure.md`: frontend already expects `api/auth.ts`, login page/shell, typed API clients.
- `.trellis/spec/frontend/state-management.md`: global state is allowed only for authenticated user and access token/session marker; secrets must not be stored broadly.
- `.trellis/spec/frontend/type-safety.md`: add explicit auth DTO/VO types; keep backend enum and snake_case fields aligned.
- `.trellis/spec/frontend/quality-guidelines.md`: visual smoke covers unauthenticated login wrapper and must continue passing.
- `.trellis/spec/security/rag-security.md`: tenant boundaries, secret-safe errors, and separate gateway API key boundary must be preserved.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: this task triggers API, DB, frontend and auth boundary mapping.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: replace repeated header parsing with shared auth context instead of duplicating validation in every controller.

### Code Patterns Found

- Public gateway auth filter pattern:
  - `backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java`
  - `backend/src/main/java/com/sangui/raggateway/common/config/GatewayAuthConfig.java`
  - `backend/src/main/java/com/sangui/raggateway/common/security/GatewayRequestContextHolder.java`
  - Reusable pattern: servlet filter validates credential, sets ThreadLocal context, clears in finally, writes auth error directly when filter-level exceptions bypass MVC advice.
- Existing owner-scoped service methods:
  - `AppService.findByIdAndUserId`, `listByUserId`, `bindDefaultModelConfig`, `bindDefaultKnowledgeBase`, `updateOutputCapture`
  - `ModelConfigService.findByIdAndUserId`, `listAdminConfigs`
  - `KnowledgeBaseService.findByIdAndUserId`, `listByUserId`
  - `DocumentService.findByIdAndUserId`, `listByKnowledgeBase`
  - `ApiRequestLogService.listRequestLogs/getRequestLogDetail/getHitChunkSummaries`
  - Keep these service boundaries; only identity source should change.
- Repeated controller pattern to replace:
  - `@RequestHeader("X-Admin-User-Id") Long userId`
  - local `validateUserId`
  - `findByIdAndUserId(..., userId)` plus `findById(...)` for 403/404 distinction.
- Frontend API injection pattern:
  - `frontend/src/api/http.ts` currently builds `X-Admin-User-Id` in one place.
  - This is the right central point to switch to Authorization token.
- Frontend shell pattern:
  - `frontend/src/components/layout/AdminShell.tsx` owns the current fake login state.
  - This is the right place to replace user-id input with real login/current user/logout behavior.

### Files Likely To Modify

Backend likely new:

- `backend/src/main/resources/db/migration/V12__create_admin_user_table.sql`
- `backend/src/main/java/com/sangui/raggateway/user/UserEntity.java`
- `backend/src/main/java/com/sangui/raggateway/user/UserStatus.java`
- `backend/src/main/java/com/sangui/raggateway/user/UserMapper.java`
- `backend/src/main/java/com/sangui/raggateway/user/UserService.java`
- `backend/src/main/java/com/sangui/raggateway/auth/AdminAuthController.java`
- `backend/src/main/java/com/sangui/raggateway/auth/AdminAuthService.java`
- `backend/src/main/java/com/sangui/raggateway/auth/dto/AdminLoginDTO.java`
- `backend/src/main/java/com/sangui/raggateway/auth/vo/AdminLoginVO.java`
- `backend/src/main/java/com/sangui/raggateway/auth/vo/AdminUserVO.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/AdminAuthFilter.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/AdminAuthContext.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/AdminAuthContextHolder.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/AdminJwtService.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/PasswordHasher.java`
- `backend/src/main/java/com/sangui/raggateway/common/config/AdminAuthConfig.java`

Backend likely changed:

- `backend/pom.xml` if a password/JWT dependency is chosen.
- `backend/src/main/resources/application.yml` and `application-dev.yml` for admin JWT expiry/secret if needed.
- Admin controllers currently using `X-Admin-User-Id`:
  - `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java` only if auth exception mapping is not handled directly in filter.
- Existing controller tests for all Admin APIs, replacing header setup with auth context/filter/token helper:
  - `AppAdminControllerTest`
  - `ApiKeyAdminControllerTest`
  - `ModelConfigAdminControllerTest`
  - `KnowledgeBaseAdminControllerTest`
  - `DocumentAdminControllerTest`
  - `ApiRequestLogAdminControllerTest`
- New auth/user tests:
  - `AdminAuthServiceTest`
  - `AdminAuthControllerTest`
  - `AdminAuthFilterTest`
  - `AdminJwtServiceTest`
  - `PasswordHasherTest`
  - `UserServiceTest`

Frontend likely new:

- `frontend/src/types/auth.ts`
- `frontend/src/api/auth.ts`
- Optional auth provider/hook if keeping `AdminShell` lean:
  - `frontend/src/app/providers/AuthProvider.tsx` or auth logic inside `AdminShell` for V1 minimal scope.

Frontend likely changed:

- `frontend/src/api/http.ts`
- All typed API files removing `adminUserId` params:
  - `frontend/src/api/apps.ts`
  - `frontend/src/api/api-keys.ts`
  - `frontend/src/api/model-configs.ts`
  - `frontend/src/api/knowledge.ts`
  - `frontend/src/api/documents.ts`
  - `frontend/src/api/request-logs.ts`
- Pages/components currently consuming `adminUserId`:
  - `frontend/src/components/layout/AdminShell.tsx`
  - `frontend/src/App.tsx`
  - `frontend/src/pages/apps/AppConfigPage.tsx`
  - `frontend/src/pages/api-keys/ApiKeyPage.tsx`
  - `frontend/src/pages/model-configs/ModelConfigPage.tsx`
  - `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
  - `frontend/src/pages/request-logs/RequestLogListPage.tsx`
  - `frontend/src/pages/smoke/SmokeTestPage.tsx`
  - `frontend/src/components/domain/RequestLogDetailDrawer.tsx`
  - `frontend/src/components/domain/OutputPreviewModal.tsx`
  - `frontend/src/app/i18n/dict.ts`
- Visual smoke may need updates if login form selectors/text change but `data-testid="login-wrapper"` should remain.

Spec likely changed:

- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md` if needed.

## Risk / Boundary Notes

- Do not let Admin JWT and public app API key auth merge. `/v1/*` remains app API key only; `/api/admin/**` remains admin JWT only.
- Do not add RBAC, roles, teams, invitations, refresh tokens, signup, password reset, or OAuth in this task.
- Do not weaken 403 owner checks when removing header parsing; service owner-scoped methods should remain the enforcement boundary after authentication.
- Do not make frontend token storage a cache for domain data. Store only token/current user and keep app/model/KB/log data as server state.
- Do not silently fallback to a default admin user on missing token. Missing auth must fail as 401.
- Do not log passwords, tokens, Authorization headers, JWT claims containing sensitive data, or raw request bodies.
- If bootstrapping a first admin user is needed, keep it explicit and documented. Do not hardcode a production default password. A local-dev seed is acceptable only if clearly dev-scoped and spec-documented.
- Existing controller tests may currently run with `@Profile("!test")` exclusions or standalone MockMvc patterns; implementation should preserve testability without requiring a full server.

## Required Tests And Assertion Points

Backend targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=AdminAuthServiceTest,AdminAuthControllerTest,AdminAuthFilterTest,AdminJwtServiceTest,PasswordHasherTest,UserServiceTest" test
mvn -q "-Dtest=AppAdminControllerTest,ApiKeyAdminControllerTest,ModelConfigAdminControllerTest,KnowledgeBaseAdminControllerTest,DocumentAdminControllerTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn -q "-Dtest=AppServiceTest,ModelConfigServiceTest,ApiKeyServiceTest,KnowledgeBaseServiceTest,DocumentServiceTest,ApiRequestLogServiceTest" test
mvn test
```

Backend assertion points:

- Login succeeds with active user and correct password; token and expiry returned; password hash not returned.
- Login fails for missing body fields, unknown username, wrong password, disabled user.
- Admin filter returns 401 for missing/non-Bearer/invalid/expired token and does not call controller.
- Admin context is set during request and cleared after request.
- Existing Admin controller actions use context userId and no longer require `X-Admin-User-Id`.
- Cross-user app/model/KB/document/API-key/request-log access still returns 403.
- Missing resources still return 404.
- `/v1/*` gateway auth tests remain unchanged and still return OpenAI-compatible `invalid_api_key`.
- No password/token/Authorization value appears in responses or logs asserted by available tests.

Frontend checks:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual
```

Frontend assertion points:

- Login page replaces user-id input and still satisfies `data-testid="login-wrapper"` visual smoke baseline.
- API client sets `Authorization: Bearer <token>` for Admin APIs.
- API clients no longer require `adminUserId` parameters.
- Logout clears auth state and selected app state.
- 401 from Admin API is surfaced and allows re-login.
- Existing pages still compile with typed DTO/VO contracts.

## Acceptance Criteria

- [ ] PRD and Trellis context are ready for DeepSeek implementation before any business-code changes.
- [ ] A minimal admin user table/model/service exists with password hashes and active/disabled status.
- [ ] `POST /api/admin/auth/login` issues a signed expiring token on valid credentials and returns 401 on invalid credentials.
- [ ] `GET /api/admin/auth/me` returns current safe user metadata for a valid token.
- [ ] `/api/admin/**` except login requires `Authorization: Bearer <admin-jwt>`.
- [ ] Existing Admin APIs derive `userId` from Admin auth context, not `X-Admin-User-Id`.
- [ ] Cross-user access remains 403; missing resource remains 404; unauthenticated access is 401.
- [ ] `/v1/*` app API key authentication and OpenAI-compatible error shape are not changed.
- [ ] Frontend no longer asks for admin user id; it has login/logout state and automatically sends Authorization.
- [ ] Existing app/model/key/KB/document/request-log/output-preview flows work under logged-in admin state.
- [ ] Backend/frontend/security/guides specs document the new Admin auth contract and migration boundary.
- [ ] Required backend tests, frontend typecheck/build/visual smoke pass.

## Explicit Non-Goals

- No RBAC/roles/permissions matrix.
- No organizations/teams/workspaces.
- No OAuth/OIDC/LDAP.
- No registration/invitation/password reset flow.
- No refresh token/session table unless implementation proves it is necessary for the minimal contract.
- No change to public `/v1/*` API key auth.
- No change to RAG retrieval, prompt, streaming, output preview policy beyond identity source.
- No broad UI redesign beyond replacing fake user-id login with real login/logout.

## Planning Self-Check

- 验收标准：已明确，见 Acceptance Criteria 和 Required Tests。
- 禁止修改范围：已明确，见 Explicit Non-Goals 和 Risk / Boundary Notes。
- 预计修改文件：已列出 backend/frontend/spec likely files。
- 必读 guideline：已读取具体 backend/frontend/security/guides guideline，不只读 index。
- 必跑测试：已列出 backend targeted、backend full、frontend typecheck/build/visual smoke。
- 需求不清点：唯一需要实现端自行决策的是 JWT/密码哈希依赖选择；原则是最小、可测试、无硬编码 secret。如引入新依赖，必须在 PRD 范围内更新 `pom.xml` 并覆盖测试。
- API/DB/frontend DTO 对齐：已定义 login/me/Admin Authorization contract、`sys_user` 表、frontend `auth.ts` 类型与 Admin API 参数迁移边界。
