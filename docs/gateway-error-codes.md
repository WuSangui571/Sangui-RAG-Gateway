# Gateway Error Codes

Public `/v1/*` APIs return OpenAI-compatible error responses, not the Admin `ApiResponse<T>` envelope.

Shape:

```json
{
  "error": {
    "message": "Safe error message",
    "type": "invalid_request_error",
    "code": "invalid_request"
  }
}
```

## Public Gateway Errors

| HTTP | Code | Type | Meaning |
|---:|---|---|---|
| 400 | `invalid_request` | `invalid_request_error` | Malformed JSON or unsupported/invalid chat payload. |
| 401 | `invalid_api_key` | `invalid_request_error` | Missing, malformed, unknown, disabled, revoked, expired app API key, or disabled app. |
| 409 | `model_config_not_ready` | `invalid_request_error` | Authenticated app has no enabled default chat model config or no usable upstream key. |
| 409 | `knowledge_base_not_ready` | `invalid_request_error` | Authenticated app has no bound ready knowledge base. |
| 429 | `rate_limit_exceeded` | `rate_limit_error` | Authenticated app API key exceeded request or token limits. |
| 500 | `internal_error` | `server_error` | Internal failure such as Redis limiter unavailability. |
| 502 | `embedding_failed` | `server_error` | Query embedding provider failed or timed out before retrieval. |
| 502 | `upstream_error` | `server_error` | Upstream chat provider returned an error, network failure, or malformed success body. |
| 504 | `upstream_timeout` | `server_error` | Upstream chat provider timed out. |

## Safety Rules

- Public gateway errors never include Admin envelope fields such as `code`, `message`, and `data` at the top level.
- Admin APIs under `/api/admin/*` never return the OpenAI-compatible `error` object.
- Error responses must not reveal app key details, upstream keys, raw provider bodies, prompts, private document text, chunk content, storage paths, or stack traces.
- Rate-limit rejection happens before retrieval, embedding, and upstream chat calls.

## Common Validation Checks

| Scenario | Expected result |
|---|---|
| Missing `Authorization` header on `/v1/models` or `/v1/chat/completions` | HTTP 401 with `error.code=invalid_api_key`. |
| Valid app key but missing model config | HTTP 409 with `error.code=model_config_not_ready`. |
| Valid app key but no ready bound knowledge base for chat | HTTP 409 with `error.code=knowledge_base_not_ready`. |
| Rate limit exceeded | HTTP 429 with `error.code=rate_limit_exceeded`. |
| Upstream provider timeout | HTTP 504 with `error.code=upstream_timeout`. |
