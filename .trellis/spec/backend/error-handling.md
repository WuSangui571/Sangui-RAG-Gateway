# Error Handling

> Public gateway errors should be OpenAI-compatible. Admin console errors may use a normal application response envelope, but must still avoid leaking secrets or stack traces.

## Error Families

Common gateway error codes:

```text
invalid_api_key
rate_limit_exceeded
app_not_found
knowledge_base_not_ready
embedding_failed
upstream_timeout
upstream_error
internal_error
```

OpenAI-compatible error shape:

```json
{
  "error": {
    "message": "Specific error message",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

Rate-limit example:

```json
{
  "error": {
    "message": "Rate limit exceeded for this API key.",
    "type": "rate_limit_error",
    "code": "rate_limit_exceeded"
  }
}
```

## Gateway HTTP Status Mapping

Use conventional statuses where possible:

```text
401 invalid_api_key
403 app disabled, key revoked, forbidden tenant access
404 app_not_found when it is safe to reveal
409 knowledge_base_not_ready
429 rate_limit_exceeded
502 upstream_error
504 upstream_timeout
500 internal_error
```

Do not expose internal implementation details in `message`.

## Exception Boundaries

Use explicit domain exceptions for expected business failures:

```text
InvalidApiKeyException
RateLimitExceededException
AppNotAvailableException
KnowledgeBaseNotReadyException
EmbeddingException
UpstreamModelException
```

The global exception handler should:

- Convert gateway exceptions to OpenAI-compatible errors.
- Convert admin API exceptions to the admin response envelope.
- Log request IDs and safe context.
- Hide stack traces from clients.
- Preserve upstream status/code only when safe and useful.

## Streaming Errors

For `stream=true`:

- If authentication, app loading, or retrieval fails before streaming starts, return a normal error response.
- If upstream fails after streaming begins, emit the most compatible error event possible and close the stream.
- If the client disconnects, cancel upstream forwarding and avoid logging it as an internal server error.

Usage data may be missing in MVP streaming responses; document this limitation.

## Upstream Error Handling

Upstream provider failures should be normalized:

```text
connection failure -> upstream_error
timeout -> upstream_timeout
provider 4xx -> upstream_error unless it maps to a safe config issue
provider 5xx -> upstream_error
invalid upstream API key -> upstream_error for public gateway callers; admin APIs may show a masked configuration error
```

Do not pass through upstream response bodies blindly. They may include provider details or sensitive request fragments.

## Document Pipeline Errors

Document processing must update status and error reason:

```text
UPLOADED -> PARSING -> PARSED -> EMBEDDING -> READY
UPLOADED/PARSING/PARSED/EMBEDDING -> FAILED
```

Store a bounded error message suitable for admin display. Full stack traces belong in server logs only.

## Forbidden Patterns

- Returning Java stack traces to clients.
- Logging complete API keys, upstream API keys, full private documents, or full augmented prompts.
- Treating tenant access failures as retriable internal errors.
- Swallowing embedding failures and marking documents as ready.
- Wrapping every exception as `500 internal_error` when the client should receive `401`, `429`, `409`, or upstream error classes.
