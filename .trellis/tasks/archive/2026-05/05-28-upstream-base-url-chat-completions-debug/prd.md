# Upstream Base URL Compatibility and Chat Completions Debuggability

## Goal

Make upstream OpenAI-compatible `base_url` handling tolerant of common provider/client configuration styles so authenticated `POST /v1/chat/completions` calls do not accidentally target `/v1/v1/chat/completions`.

This task comes from manual integration feedback after the non-streaming Chat Completions baseline: operators commonly configure upstream providers as either `https://host` or `https://host/v1`. The gateway must normalize both formats predictably and document the contract.

## Classification

Simple Task with API/upstream-forwarding contract impact.

This is not a RAG, streaming, frontend, database, Redis, storage, or admin auth task.

## Scope

In scope:

- Normalize upstream model config `base_url` in `OpenAiCompatibleUpstreamClient`.
- Accept both provider root URLs and OpenAI API root URLs:
  - `https://api.example.com`
  - `https://api.example.com/`
  - `https://api.example.com/v1`
  - `https://api.example.com/v1/`
- Keep final Chat Completions path exactly:
  - `https://api.example.com/v1/chat/completions`
- Add focused unit tests for URL normalization and request path construction.
- Update project/backend specs with the accepted `base_url` formats, path joining rule, and safe upstream error/logging behavior.

Out of scope:

- No streaming implementation.
- No RAG retrieval or prompt augmentation.
- No frontend changes.
- No database migration or schema change.
- No admin model-config API contract change beyond documentation of existing `base_url` field semantics.
- No provider-specific routing rules beyond the generic OpenAI-compatible `/v1/chat/completions` path.
- No pass-through of upstream provider error bodies to public callers.
- No changes to app API key auth, upstream key encryption, or model selection.

## Current Problem

The implemented baseline documents upstream forwarding as:

```text
Target URL is {normalized_base_url}/v1/chat/completions.
```

If `normalized_base_url` is only trimmed for trailing slash, then a configured value like:

```text
https://api.example.com/v1
```

can produce:

```text
https://api.example.com/v1/v1/chat/completions
```

That causes provider failures that are surfaced as generic `502 upstream_error`, making manual integration difficult to diagnose.

## Required Contract

### Stored Field

The existing model config field remains:

```text
rag_model_config.base_url
```

No new API field, DTO field, table column, enum, or environment variable is introduced.

### Normalization Rule

Before appending the chat completions path:

1. Trim surrounding whitespace if the current code path already allows it, or leave existing validation ownership unchanged.
2. Remove trailing slash characters from `base_url`.
3. If the resulting base URL ends with `/v1`, treat it as the OpenAI-compatible API root and append only `/chat/completions`.
4. Otherwise, treat it as the provider host/root and append `/v1/chat/completions`.

Equivalent rule:

```text
normalize(base_url):
  base = base_url without trailing slash
  if base ends with "/v1":
      final_url = base + "/chat/completions"
  else:
      final_url = base + "/v1/chat/completions"
```

### API / Command / Payload Fields

No public gateway request payload changes.

Existing public API remains:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Existing admin model config payload field remains:

```json
{
  "base_url": "https://api.example.com/v1"
}
```

Allowed admin/operator values for `base_url` after this task:

| Input `base_url` | Final upstream request URL |
|---|---|
| `https://api.example.com` | `https://api.example.com/v1/chat/completions` |
| `https://api.example.com/` | `https://api.example.com/v1/chat/completions` |
| `https://api.example.com/v1` | `https://api.example.com/v1/chat/completions` |
| `https://api.example.com/v1/` | `https://api.example.com/v1/chat/completions` |

## Validation / Error Matrix

| Scenario | Expected behavior | Public response |
|---|---|---|
| Valid root base URL, upstream 200 | Calls `{host}/v1/chat/completions` once and returns upstream success body mapped to OpenAI-compatible response | 200 chat completion JSON |
| Valid root base URL with trailing slash, upstream 200 | Calls `{host}/v1/chat/completions` without double slash | 200 chat completion JSON |
| Valid `/v1` base URL, upstream 200 | Calls `{host}/v1/chat/completions`, not `{host}/v1/v1/chat/completions` | 200 chat completion JSON |
| Valid `/v1/` base URL, upstream 200 | Calls `{host}/v1/chat/completions`, not `{host}/v1/v1/chat/completions` | 200 chat completion JSON |
| Upstream non-2xx | Do not return provider body; normalize as upstream failure | 502 `upstream_error` |
| Upstream network failure | Do not expose host internals beyond generic message | 502 `upstream_error` |
| Upstream timeout | Preserve existing timeout mapping | 504 `upstream_timeout` |
| Missing/invalid app API key | Unchanged gateway auth behavior | 401 `invalid_api_key` |
| Missing/undecryptable upstream key | Unchanged model-config readiness behavior | 409 `model_config_not_ready` |

## Good / Base / Bad Cases

Good cases:

- `baseUrl=https://api.example.com` targets `/v1/chat/completions`.
- `baseUrl=https://api.example.com/` targets `/v1/chat/completions`.
- `baseUrl=https://api.example.com/v1` targets `/v1/chat/completions`.
- `baseUrl=https://api.example.com/v1/` targets `/v1/chat/completions`.

Base cases:

- Existing non-streaming request mapping, app default model selection, Authorization header, `stream=false`, and timeout behavior remain unchanged.
- Existing `/v1/models` behavior remains unchanged.
- Existing admin model-config create/update validation remains unchanged except docs now clarify accepted URL formats.

Bad cases:

- A configured `/v1` URL must never generate `/v1/v1/chat/completions`.
- A configured trailing slash must never generate `//v1/chat/completions` or `//chat/completions`.
- Upstream error body must not be passed through to the public caller.
- Logs must not contain upstream API key, app API key, request body, response body, or full prompt/messages.

## Logging Requirements

Optional implementation enhancement:

- On upstream failure, internal logs may include safe operational fields:
  - upstream HTTP status
  - normalized final upstream URL or URL path
  - provider/model name if already available and non-secret
  - exception class or bounded safe message

Forbidden in logs:

- plaintext upstream API key
- encrypted upstream API key
- Authorization header
- public app API key
- full request body/messages
- provider response body

## Expected Modified Files

Likely implementation files:

- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/error-handling.md`

Potentially unchanged but relevant for context:

- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigEntity.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`

## Required Tests and Assertion Points

Required focused test:

```bash
cd backend
mvn -q "-Dtest=OpenAiCompatibleUpstreamClientTest" test
```

Assertions:

- A base URL without trailing slash sends exactly one request to `/v1/chat/completions`.
- A base URL with trailing slash sends exactly one request to `/v1/chat/completions`.
- A base URL ending in `/v1` sends exactly one request to `/v1/chat/completions`.
- A base URL ending in `/v1/` sends exactly one request to `/v1/chat/completions`.
- The outbound Authorization header still uses the decrypted upstream key.
- Upstream non-2xx still maps to `502 upstream_error` without provider body pass-through.
- Timeout behavior still maps to `504 upstream_timeout` if existing test coverage allows.

Recommended regression tests:

```bash
cd backend
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=OpenAiModelsControllerTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn -q -DskipTests compile
```

Full backend verification before final merge:

```bash
cd backend
mvn test
```

## Acceptance Criteria

- [ ] `OpenAiCompatibleUpstreamClient` correctly targets `/v1/chat/completions` for all four required base URL forms.
- [ ] No generated URL contains `/v1/v1/chat/completions` for a base URL ending in `/v1` or `/v1/`.
- [ ] Existing app auth, default model selection, upstream Authorization header, and non-streaming forwarding behavior remain unchanged.
- [ ] Upstream provider error body is still not exposed to public gateway callers.
- [ ] Specs document accepted `base_url` formats and path joining rules.
- [ ] Specs clarify that `502 upstream_error` remains generic, with only safe internal upstream status/url logging allowed.
- [ ] Required focused tests pass.

## DeepSeek Implementation Notes

- Keep the change local to upstream URL construction and its tests.
- Prefer a small private helper method if it improves testability/readability, but do not introduce a broad URL utility unless existing code already has one.
- Search existing references before changing constants or path strings.
- Do not modify unrelated gateway validation, DTO shapes, database schema, or frontend code.
