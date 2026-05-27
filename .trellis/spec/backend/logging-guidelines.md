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
