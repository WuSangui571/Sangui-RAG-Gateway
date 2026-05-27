# OpenAI Chat Completions 非流式转发基线

## Goal

实现 `POST /v1/chat/completions` 的非流式 OpenAI-compatible pass-through 基线，让已认证的业务系统可以通过 Sangui-RAG-Gateway 调用应用默认上游模型配置。

本任务只打通 gateway 主干调用链：

```text
Gateway API key auth
  -> GatewayRequestContextHolder
  -> App default model config
  -> decrypt upstream API key
  -> OpenAI-compatible upstream non-streaming HTTP call
  -> OpenAI-compatible response passthrough/adaptation
```

## Scope

### In Scope

- 新增 OpenAI Chat Completions request/response DTO，支持非流式 baseline 所需字段。
- 新增 `POST /v1/chat/completions` public gateway endpoint。
- 复用 `GatewayRequestContextHolder` 中的 `appId/userId/apiKeyId/apiKeyPrefix`，不重复认证。
- 通过 `AppService.resolveDefaultModelConfig(app)` 获取同用户、启用的 app 默认模型配置。
- 使用 `ModelConfigEntity.baseUrl/chatModel/apiKeyEncrypted` 组装上游 OpenAI-compatible chat completions 请求。
- 使用 `UpstreamApiKeyEncryptor.decrypt` 解密 upstream API key，仅用于 outbound `Authorization: Bearer ...`。
- 新增 OpenAI-compatible upstream client，支持非流式请求转发。
- 规范 timeout 与 upstream error mapping：
  - timeout -> `504 upstream_timeout`
  - upstream/network/provider failure -> `502 upstream_error`
- 对 public gateway caller 保持 OpenAI-compatible error shape。
- 添加 controller/service/client focused tests。

### Out of Scope

- 不实现 `stream=true` 的 SSE 转发。
- 不实现 RAG retrieval、embedding、prompt augmentation、knowledge base readiness。
- 不实现 request log / metrics / tracing 持久化。
- 不新增数据库表、字段、migration。
- 不实现前端 UI 或 frontend types。
- 不新增 admin API。
- 不新增 rate limit、quota、Redis 行为。
- 不支持 tools/function_call/vision/audio/response_format/parallel_tool_calls。
- 不把上游 response body 原样透传为错误响应。

## Task Classification

Complex Task.

理由：涉及 public OpenAI-compatible API、上游 HTTP client、密钥解密、错误映射、超时、secret-safe logging、已有 gateway auth/model config 合同以及多组测试。

## API Contract

### Endpoint

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Authentication remains owned by `GatewayAuthFilter` for `/v1/*`.

### Supported Request Fields

Baseline request DTO should accept these OpenAI-compatible snake_case fields:

| Field | Type | Required | Behavior |
|---|---|---:|---|
| `model` | string | no | Accepted for compatibility. Baseline should forward using app default `modelConfig.chatModel`, not trust caller-selected model. |
| `messages` | array | yes | Must be non-empty. Each message requires `role` and `content`. |
| `messages[].role` | string | yes | Accept baseline roles `system`, `user`, `assistant`. |
| `messages[].content` | string | yes | Baseline supports string content only. |
| `temperature` | number | no | Forward when present. |
| `max_tokens` | integer | no | Forward when present. |
| `top_p` | number | no | Forward when present. |
| `stream` | boolean | no | If `true`, reject for this baseline with OpenAI-compatible `invalid_request`. If absent/false, non-streaming flow. |

Implementation may accept extra unknown JSON fields only if they are ignored safely and not forwarded unless explicitly modeled. Do not advertise unsupported features.

### Upstream Request

The upstream request is sent to:

```text
{normalized_base_url}/v1/chat/completions
```

Rules:

- Normalize `baseUrl` to avoid duplicate slash.
- Outbound header: `Authorization: Bearer <decrypted-upstream-api-key>`.
- Outbound header: `Content-Type: application/json`.
- Outbound body:
  - `model`: always `modelConfig.chatModel`.
  - `messages`: original request messages unchanged for baseline.
  - `temperature`, `max_tokens`, `top_p`: forward when present.
  - `stream`: force false or omit when absent/false.
- Never log outbound authorization header, decrypted key, encrypted key, or full messages.

### Success Response

On upstream success, return HTTP 200 with OpenAI-compatible chat completion response shape.

Baseline can either:

- return a typed `OpenAiChatCompletionResponse` DTO containing common fields, or
- return a JSON object/tree after basic upstream success validation.

Expected response fields for tests:

```json
{
  "id": "chatcmpl-test",
  "object": "chat.completion",
  "created": 1710000000,
  "model": "gpt-4o-mini",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 1,
    "completion_tokens": 1,
    "total_tokens": 2
  }
}
```

The gateway must not add admin envelope fields (`code`, `message`, `data`) to gateway success or gateway error responses.

## Validation And Error Matrix

| Scenario | HTTP | Error type | Error code | Assertion points |
|---|---:|---|---|---|
| Missing/invalid/disabled app API key | 401 | `invalid_request_error` | `invalid_api_key` | Still handled by `GatewayAuthFilter`; no controller/service re-auth. |
| Authenticated app missing or disabled default model config | 409 | `invalid_request_error` | `model_config_not_ready` | Same semantics as `/v1/models`. |
| Enabled model config has missing/blank `api_key_encrypted` | 409 | `invalid_request_error` | `model_config_not_ready` | Do not attempt upstream call; do not reveal config internals. |
| Encrypted upstream key cannot decrypt | 409 or 502 | `invalid_request_error` or `server_error` | Prefer `model_config_not_ready` if config is unusable before calling upstream | Do not leak encrypted payload or exception detail. |
| Null/malformed JSON body | 400 | `invalid_request_error` | `invalid_request` | Gateway endpoint should return OpenAI-compatible shape, not admin `ApiResponse`. |
| Missing/empty `messages` | 400 | `invalid_request_error` | `invalid_request` | No upstream call. |
| Message missing `role` or `content` | 400 | `invalid_request_error` | `invalid_request` | No upstream call. |
| `stream=true` | 400 | `invalid_request_error` | `invalid_request` | Explicitly rejected until streaming task. |
| Upstream network error / connection refused | 502 | `server_error` | `upstream_error` | No upstream key/header/body leakage. |
| Upstream non-2xx response | 502 | `server_error` | `upstream_error` | Do not pass through upstream body blindly. |
| Upstream timeout | 504 | `server_error` | `upstream_timeout` | Client-facing message is generic. |
| Upstream success | 200 | n/a | n/a | OpenAI-compatible chat completion shape, no admin envelope. |

## Good / Base / Bad Cases

### Good Cases

- Valid app API key, enabled app, enabled default model config with decryptable upstream key, non-streaming request, upstream returns valid chat completion JSON -> gateway returns 200 OpenAI-compatible chat completion JSON.
- Request includes `model` different from default -> gateway still forwards configured `modelConfig.chatModel` to preserve app-level control.
- Request includes supported optional generation fields -> gateway forwards those fields.

### Base Cases

- Gateway auth failures remain covered by `GatewayAuthFilterTest`; chat controller does not duplicate auth logic.
- `/v1/models` behavior remains unchanged.
- No database migration is created.
- Existing test profile behavior may need adjustment: once the route exists, stale tests asserting `/v1/chat/completions` 404 must be updated or scoped to a test-only route.

### Bad Cases

- Missing model config -> `409 model_config_not_ready`.
- Missing upstream encrypted API key -> `409 model_config_not_ready`.
- `stream=true` -> `400 invalid_request`.
- Empty messages -> `400 invalid_request`.
- Upstream timeout -> `504 upstream_timeout`.
- Upstream 401/403/500 or malformed provider failure -> `502 upstream_error`, without leaking provider response body, outbound headers, or upstream key.

## Likely Architecture

Recommended package layout:

```text
com.sangui.raggateway.gateway.openai
  OpenAiChatCompletionsController
  OpenAiChatCompletionRequest
  OpenAiChatCompletionResponse or equivalent response DTO/tree
  OpenAiChatMessage

com.sangui.raggateway.gateway.completion
  ChatCompletionGatewayService

com.sangui.raggateway.gateway.upstream
  OpenAiCompatibleUpstreamClient
  UpstreamChatCompletionRequest
  UpstreamChatCompletionException or typed exception/result helpers
```

Keep controller thin:

```text
Controller: HTTP DTO and endpoint only
Service: context/app/model-config resolution, validation, secret decrypt orchestration
Client: outbound HTTP call, timeout, provider status mapping
```

## Files Likely To Modify

Expected new files:

- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionRequest.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionResponse.java` or JSON response wrapper
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatMessage.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/*` helper classes/exceptions if needed
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java`

Expected existing files to update:

- `backend/pom.xml` if an HTTP client dependency is required. Prefer existing Spring Boot web facilities first; avoid unnecessary dependencies.
- `backend/src/main/resources/application.yml` only if a gateway/upstream timeout property is introduced.
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java` if gateway JSON parse/validation currently maps to admin envelope and must be made OpenAI-compatible for `/v1/*`.
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerIntegrationTest.java`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `README.md` only if behavior documentation is updated in this task.

## Required Tests

Run from `backend/`:

```bash
mvn -q -DskipTests compile
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=OpenAiModelsControllerTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

Test assertion points:

- Controller/service resolves context via `GatewayRequestContextHolder`.
- Missing default model config returns `409 model_config_not_ready`.
- `stream=true` returns `400 invalid_request`.
- Empty or invalid messages return `400 invalid_request`.
- Upstream success returns `object=chat.completion`, choices, usage, no admin envelope.
- Upstream call uses configured `chatModel`, not caller-selected model.
- Upstream call includes decrypted Authorization header internally but tests must assert response/errors never include plaintext upstream key, `api_key_encrypted`, or `Authorization`.
- Upstream timeout maps to `504 upstream_timeout`.
- Upstream non-2xx/network error maps to `502 upstream_error`.
- Existing auth failure tests still pass and remain filter-owned.

## Acceptance Criteria

- [ ] `POST /v1/chat/completions` exists for non-streaming requests.
- [ ] Valid authenticated request with configured app default model forwards to upstream and returns OpenAI-compatible chat completion shape.
- [ ] Upstream API key is decrypted only in memory for outbound call and is never returned or logged.
- [ ] App default model config is the source of `baseUrl`, `chatModel`, and upstream API key.
- [ ] Missing/unready model config returns `409 model_config_not_ready`.
- [ ] Upstream timeout returns `504 upstream_timeout`.
- [ ] Upstream/provider/network errors return `502 upstream_error`.
- [ ] `stream=true` is explicitly rejected with compatible `400 invalid_request`.
- [ ] No RAG retrieval, prompt augmentation, request log, streaming, frontend, DB migration, or admin API work is introduced.
- [ ] Required focused tests and full `mvn test` pass.

## Planning Self-Check

- Acceptance criteria are defined.
- Forbidden scope is explicit.
- Expected modified files are listed.
- Required test commands are listed.
- Backend guideline files, not only indexes, were read before planning.
- No user clarification is required before implementation; scope is intentionally bounded to non-streaming pass-through.
- API/DTO fields are defined; no DB/frontend type changes are expected.
