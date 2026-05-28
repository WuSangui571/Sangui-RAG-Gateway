# Logging Guidelines

> Logs must make the gateway observable without exposing private data. Treat prompts, documents, API keys, and upstream keys as sensitive by default.

## Log Levels

Use levels consistently:

```text
DEBUG: local troubleshooting details, disabled in production by default
INFO: lifecycle events and successful high-level operations
WARN: expected but abnormal situations, rate limits, validation failures, upstream retries
ERROR: unexpected failures requiring investigation
```

Do not log expected authentication failures as `ERROR`; use `WARN` or structured request logs.

## Required Request Context

Use a request ID for every gateway request and propagate it through logs.

Recommended safe fields:

```text
request_id
user_id
app_id
api_key_id
model
provider_name
knowledge_base_id
document_id
latency_ms
status
error_code
hit_chunk_ids
prompt_tokens
completion_tokens
total_tokens
```

The request log table should persist the same safe operational data.

## Sensitive Data Rules

Never log:

```text
complete app API key
complete upstream API key
encrypted upstream API key (api_key_encrypted)
complete private document content
complete augmented prompt
large user messages
embedding vectors
authorization headers (including X-Admin-User-Id in production when it becomes real auth)
raw uploaded file contents
upstream admin API key plaintext from create/update DTOs
```

Allowed with limits:

```text
key prefix
masked upstream key
question summary or bounded prefix
chunk IDs
document filename
provider name
model name
```

Question text should be summarized or truncated to a configured maximum length before persistence.

## Gateway Logs

For `/v1/chat/completions`, log stage timing where practical:

```text
auth latency
app config load latency
embedding latency
retrieval latency
prompt build latency
upstream latency
total latency
```

This can start as database request logs plus application logs, then later move to metrics.

### Implemented Structured Gateway Log Contract

Non-streaming `POST /v1/chat/completions` emits these structured log events:

```text
gateway.chat.auth_completed
gateway.chat.config_resolved
gateway.chat.upstream_started
gateway.chat.upstream_succeeded
gateway.chat.upstream_failed
gateway.chat.response_parse_succeeded
gateway.chat.response_parse_failed
gateway.chat.validation_failed
gateway.chat.completed
```

Each event includes only safe key/value fields:

| Field | Event(s) | Description |
|---|---|---|
| `request_id` | all when available | UUID generated per request by the controller |
| `app_id` | auth_completed, config_resolved, completed | App ID resolved during authentication |
| `api_key_id` | auth_completed, config_resolved, completed | API key ID resolved during authentication |
| `user_id` | auth_completed, completed | User ID resolved during authentication |
| `provider_name` | config_resolved | Provider name from default model config |
| `model` | config_resolved, upstream_*, response_parse_* | Model name from default model config |
| `upstream_url` | upstream_* | Sanitized host+path only, no query or userinfo |
| `status` | completed | `success` or `failure` |
| `error_code` | completed, upstream_failed, validation_failed | Stable error code |
| `latency_ms` | completed | Total request latency |
| `upstream_latency_ms` | upstream_*, response_parse_succeeded | Upstream round-trip latency |
| `messages_count` | upstream_started, completed | Number of messages (count only, no content) |
| `error_class` | upstream_failed, response_parse_failed | Exception class simple name |
| `reason` | validation_failed | Safe failure reason key |

`request_id` is generated in `OpenAiChatCompletionsController` and propagated via `GatewayRequestContext`.

### Sensitive Data Rules (Gateway Chat Completions)

Never log in gateway Chat Completions logs:

```text
complete app API key
complete upstream API key
encrypted upstream API key (api_key_encrypted)
Authorization header value
full request body
messages content
provider raw error body
full prompt content
```

The upstream client sanitizes URLs to host+path only via `ChatCompletionLogHelper.sanitizeUpstreamUrl()`.

Implementation files:
- `backend/src/main/java/com/sangui/raggateway/log/ChatCompletionLogHelper.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/GatewayRequestContext.java` (added `requestId`)
- `backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`

### Implemented Request Log Persistence Contract

Non-streaming `POST /v1/chat/completions` persists one safe row to `rag_request_log` per authenticated request via `ApiRequestLogService.record(CreateRequestLogCommand)`.

Persisted fields:

| Field | Source | Safety |
|---|---|---|
| `request_id` | UUID generated by controller | Safe UUID |
| `user_id` | `GatewayRequestContext.userId` | Safe ID |
| `app_id` | `GatewayRequestContext.appId` | Safe ID |
| `api_key_id` | `GatewayRequestContext.apiKeyId` | Safe ID |
| `model` | `ModelConfigEntity.chatModel` from success path only | Safe bounded string |
| `provider_name` | `ModelConfigEntity.providerName` from success path only | Safe bounded string |
| `status` | `success` or `failure` | Safe enum |
| `error_code` | Gateway error code from GatewayException | Safe string |
| `latency_ms` | Total controller elapsed time | Safe numeric |
| `upstream_latency_ms` | Service-measured upstream latency from success path | Safe numeric |
| `prompt_tokens` | Upstream response usage from success path | Safe numeric |
| `completion_tokens` | Upstream response usage from success path | Safe numeric |
| `total_tokens` | Upstream response usage from success path | Safe numeric |
| `messages_count` | Request message array count only | Safe numeric |
| `question_summary` | null in this baseline | Safe null |
| `hit_chunk_ids` | null in this baseline | Safe null |

Never persisted:

```text
app API key plaintext
app API key hash
upstream API key plaintext
encrypted upstream API key (api_key_encrypted)
Authorization header value
full request body
messages content
provider raw error body
full prompt content
stack traces
```

Insert failure safety: `ApiRequestLogService.record()` catches all exceptions internally. When insert fails, the error is logged at ERROR with request_id and exception class name only. The gateway response is never affected by log persistence failure.

Known unpersisted scenarios (documented limitation):

- Malformed JSON (400): request body cannot be deserialized, so the request ID is not generated before `HttpMessageNotReadableException` handling.
- Gateway auth failure (401): `GatewayAuthFilter` writes the response directly without reaching the controller's persistence boundary.

Implementation files:

```text
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogEntity.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionResult.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
```

## Document Processing Logs

Document ingestion should log:

```text
document_id
knowledge_base_id
filename
parser selected
chunk count
embedding batch count
status transition
failure reason code
```

Do not log parsed document text.

## Rate Limit Logs

Rate-limit hits should include:

```text
api_key_id
app_id
limit_type
window
request_id
```

Do not log the raw key.

## Metrics

MVP observability can be implemented with request logs and Spring Boot Actuator.

Track at minimum:

```text
request count
failed request count
average latency
upstream model latency
embedding latency
retrieval latency
token usage
API key call count
app call count
```

Later extensions may add Prometheus, Grafana, tracing, and slow request analysis.
