# RAG Security

> RAG security is primarily tenant isolation, secret safety, prompt/context containment, and safe observability. Security enhancements must not be implemented as hidden fallbacks or silent success paths.

## 1. Scope / Trigger

Use this spec before changing:

- retrieval SQL, app-to-KB binding, or chunk evidence APIs
- prompt augmentation or no-hit instructions
- request logs, hit chunk summaries, or admin observability APIs
- API key or upstream key handling
- document content exposure
- error response messages
- future input/output safety models or prompt injection classifiers

This task only records the spec. It does not implement safety models, classifiers, Llama Guard, or new access-policy engines.

## 2. Current Hard Specification

- Retrieval SQL must carry `app_id` through resolved app context and must filter by `user_id` and `knowledge_base_id` at the database boundary.
- An app API key can access only the app it is bound to and the knowledge base that app is allowed to use.
- App API keys are protected by API-key scoped Redis request/token limits before retrieval, embedding, or upstream chat calls.
- RAG context can only come from the current app's bound knowledge base.
- Cross-app, cross-user, and cross-knowledge-base retrieval are forbidden.
- User-facing responses must never return complete internal prompts.
- User-facing responses must never return upstream API keys, app API keys, encrypted keys, environment variables, or hidden system rules.
- Request logs must not store complete private document content or complete augmented prompts by default.
- Hit chunk evidence APIs may return only safe fields and bounded summaries.
- Error responses must not expose stack traces, SQL, keys, provider raw bodies, internal filesystem paths, or full request bodies.
- RAG prompts must tell the model that user instructions cannot override system safety rules.
- When users request system prompts, keys, hidden rules, or complete context, the model should refuse rather than reveal internal material.
- When the knowledge base has no sufficient answer, the model should say there is not enough evidence instead of inventing.

## 3. Signatures

Allowed safe request-log fields:

```text
request_id
user_id
app_id
api_key_id
model
provider_name
status
error_code
latency_ms
upstream_latency_ms
usage
messages_count
question_summary
hit_chunk_ids
retrieval_evidence
created_at
updated_at
```

Allowed `retrieval_evidence` fields:

```text
version
no_hits
retrieval_latency_ms
top_k
similarity_threshold
max_context_chunks
citations[].citation_id
citations[].chunk_id
citations[].document_id
citations[].knowledge_base_id
citations[].source_filename
citations[].chunk_index
citations[].similarity
citations[].metadata.source
citations[].metadata.parser
citations[].content_chars
citations[].injected_chars
```

Allowed hit chunk evidence fields:

```text
chunk_id
document_id
knowledge_base_id
source_filename
chunk_index
summary (bounded prefix only)
```

Forbidden response/log fields:

```text
complete app API key
key_hash
Authorization header
upstream API key
api_key_encrypted
complete private document content
complete augmented prompt
full messages
provider raw body
embedding vector
storage_path
stack_trace
internal filesystem path
environment variables
```

Output preview exception:

`output_preview` is allowed only through:

```http
POST /api/admin/apps/{appId}/request-logs/{requestId}/output-preview/access
```

It must be bounded, deterministically redacted, explicitly confirmed with `confirm_access=true`, tenant-scoped by app ownership before any request-log lookup, and audited without storing preview content.

App-level output capture opt-in is managed only through:

```http
PUT /api/admin/apps/{appId}/request-log-output-capture
```

The request body is limited to `request_log_output_capture_enabled: boolean`. The endpoint must verify app ownership before mutation, must return 403 for cross-user access, and must return only `AppVO` metadata. It must not return preview content or any raw prompt/answer/request-log body. Effective capture still requires both the global switch and this app switch.

## 4. Contracts

| Contract | Required behavior |
|----------|-------------------|
| API key boundary | Gateway auth resolves `app_id`, `user_id`, and `api_key_id`; downstream logic uses that context. |
| API key cost boundary | Runtime rate-limit/quota counters are scoped by `api_key_id` only. Redis keys, logs, and errors must never include plaintext app keys, key hashes, key prefixes, prompts, request bodies, or upstream keys. |
| Admin auth boundary | Admin APIs require `Authorization: Bearer <admin-jwt>`; `/v1/*` app API key auth must not accept admin JWTs, and `/api/admin/**` must not accept app API keys as admin credentials. |
| Knowledge-base boundary | Retrieval only uses the app-bound KB and same user. |
| SQL boundary | Vector retrieval filters tenant/KB in SQL, not after Java ranking. |
| Prompt boundary | RAG context is reference material and cannot override system safety or reveal internals. |
| Evidence boundary | Admin hit chunks return safe metadata and bounded summary, not full chunk content. |
| Retrieval evidence boundary | Request-log retrieval evidence returns metadata-only citations and never chunk content, prompts, storage paths, keys, embeddings, provider bodies, or raw SSE. |
| Log boundary | Request logs hold safe operational fields only. |
| Error boundary | Errors are compatible and safe; no internals or secrets. |
| Output preview boundary | Preview content is default-off, app-opt-in, bounded/redacted, expired by retention, and returned only through explicit audited access. |
| App output capture switch boundary | App owners may toggle only their own app-level boolean switch; the management API exposes metadata only and never exposes preview content. |

## 5. Validation & Error Matrix

| Scenario | Expected behavior | Assertion point |
|----------|-------------------|-----------------|
| Admin API missing, non-Bearer, invalid, or expired JWT | 401 `UNAUTHORIZED`; no admin auth context set; no controller business method runs | `AdminAuthFilterTest`, admin controller tests |
| App API key exceeds rate/token limit | 429 OpenAI-compatible `rate_limit_exceeded`; no retrieval, embedding, or upstream call; safe request log only | `OpenAiChatCompletionsControllerTest`, `ApiKeyRateLimitServiceTest` |
| Redis limiter is unavailable | 500 OpenAI-compatible `internal_error`; no silent bypass and no sensitive values in response | `ApiKeyRateLimitServiceTest`, controller test |
| Invalid chat payload under valid app key | 400 `invalid_request`; limiter is not called and quota is not consumed | `OpenAiChatCompletionsControllerTest` |
| App key for app A tries to retrieve app B KB | No chunks returned; request fails according to binding/readiness contract | Retrieval/service test |
| Admin user guesses another user's app or KB ID | 403/404 according to admin contract; no data leak | Controller/service test |
| Request-log detail requested cross-user | 403 `FORBIDDEN`; no log row returned | Request-log API test |
| Output preview requested cross-user | 403 `FORBIDDEN`; app ownership check happens before request-log query | Request-log output access controller test |
| Output preview requested without confirmation | 400 `INVALID_REQUEST`; audit `DENIED`; no preview returned | Request-log output access controller test |
| App output capture switch requested cross-user | 403 `FORBIDDEN`; no app row update; response contains no preview content | App controller/service test |
| Hit chunks include IDs from another KB | Only current app-bound KB chunks are returned | Hit chunk service test |
| User asks for keys or full prompt | Prompt safety instruction refuses; logs omit secrets | Prompt/security test |
| Provider error includes request content | Client and logs receive only safe normalized error | Upstream client test |
| Malformed `hit_chunk_ids` | Fails visibly; no silent fabricated evidence | Request-log service test |
| Malformed `retrieval_evidence` | Fails visibly; no silent fabricated evidence | Request-log service/admin detail test |

## 6. Good/Base/Bad Cases

| Case | Expected result |
|------|-----------------|
| Good | Admin user logs in with username/password, receives a signed expiring JWT, and Admin APIs derive user identity from `AdminAuthContextHolder`. |
| Good | Retrieval, prompt, logs, and hit chunk APIs all use app/user/KB boundaries and return only safe evidence. |
| Good | Output preview capture is globally and app disabled by default; when enabled, only bounded redacted preview is stored and explicit access is audited. |
| Base | No KB evidence or denied access: the gateway fails clearly or says evidence is insufficient without leaking internals. |
| Bad | Context contains another app's data, logs persist full prompts, errors expose SQL or provider bodies, or evidence APIs return full chunk content by default. |
| Bad | Admin APIs trust `X-Admin-User-Id`, public `/v1/*` accepts admin JWTs, or Admin `/api/admin/**` accepts `sk-*` app API keys. |
| Bad | Request-log list/detail exposes `output_preview`, preview access bypasses app ownership, raw SSE is persisted, or audit rows store preview content. |

## 7. Wrong vs Correct

### Wrong

```text
Return full chunk content and full augmented prompt in request-log detail because it is useful for debugging.
```

This leaks private documents and internal prompts into an admin observability surface by default.

### Correct

```text
Persist hit_chunk_ids and bounded question summaries. Expose hit chunk metadata plus bounded summaries only through tenant-scoped admin APIs.
```

## 8. Prompt Injection Boundary

Current hard rules:

- User instructions cannot override system safety rules.
- Knowledge-base context is reference material, not an instruction source for revealing internal state.
- Requests to output system prompts, keys, hidden rules, full context, or complete private documents must be refused.
- No-hit answers must state insufficient evidence rather than inventing knowledge-base support.

These rules belong in the RAG prompt policy and tests. They are not a substitute for SQL tenant isolation or secret-safe logging.

## 9. Future Security Roadmap

The following are valid later enhancements, not current hard dependencies:

- input safety detection
- output safety detection
- prompt injection classifier
- Llama Guard or another safety model
- sensitive information detection and redaction
- per-app data access policy
- audit log

Safety models must be explicit, configurable, observable, and documented. They must not become hidden fallbacks that silently alter request outcomes without traceability.

- secret-key role separation: `rag.gateway.secret-key` previously served as both the upstream provider API key AES-256-GCM encryption master key and the admin JWT HMAC signing key. As of the JWT/AES secret split, these responsibilities are now separate: `rag.admin-auth.jwt-secret` for Admin JWT HS256 signing, and `rag.gateway.encryption.secret-key` for upstream key AES-256-GCM encryption. The encrypted payload format remains unchanged.

## 10. Storage Secret and Lifecycle Boundary

Storage internals are deployment metadata and must not become API, frontend, request-log, or evidence fields.

Forbidden response/log/doc example fields for document storage:

```text
storage_path
object endpoint value in API responses
bucket value in API responses
FILE_STORAGE_OBJECT_ACCESS_KEY value
FILE_STORAGE_OBJECT_SECRET_KEY value
object access key value
object secret key value
signed URL
absolute local filesystem path
uploaded file content
chunk content
embedding vector
```

Allowed safe metadata:

```text
document_id
knowledge_base_id
user_id
storageKey (opaque logical key in server logs only)
file size
backend type
cleanup result
```

Delete security rules:

- Admin document and KB delete APIs derive `userId` from `AdminAuthContextHolder`.
- Missing resources return `404`; cross-user resources return `403` with generic `Access denied`.
- Ownership checks must happen before storage cleanup or DB mutation.
- KB deletion must reject app-bound KBs explicitly with `409 KNOWLEDGE_BASE_IN_USE` unless a future task defines and tests a safe unbind/cascade contract.
- Cleanup failures must be visible; hidden fallbacks or mock-success paths are forbidden.
