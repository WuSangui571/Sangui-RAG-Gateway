# Type Safety

> TypeScript should protect API contracts, status enums, and secret-handling flows. Avoid `any` around backend payloads.

## TypeScript Baseline

Use strict TypeScript settings where practical.

Avoid:

```text
any
unknown without narrowing
stringly-typed statuses
untyped API responses
```

Use explicit types for API requests and responses.

## Type Organization

Recommended `src/types` groups:

```text
auth.ts
app.ts
knowledge.ts
document.ts
model-config.ts
api-key.ts
request-log.ts
openai.ts
common.ts
```

API clients should import these shared types instead of redefining payloads inline.

## Admin Auth Types

Admin login/session contracts live in:

```text
frontend/src/types/auth.ts
frontend/src/api/auth.ts
frontend/src/api/http.ts
```

Required DTO/VO shapes:

```ts
export interface AdminLoginDTO {
  username: string;
  password: string;
}

export interface AdminUserVO {
  id: number;
  username: string;
  status: 'ACTIVE' | 'DISABLED' | string;
}

export interface AdminLoginVO {
  access_token: string;
  token_type: 'Bearer' | string;
  expires_at: string;
  user: AdminUserVO;
}
```

Admin API clients must not accept `adminUserId` as a parameter. `frontend/src/api/http.ts` owns the single admin credential injection point and sends `Authorization: Bearer <token>` for Admin APIs after login. `/admin/auth/login` may be called without a token. A `401` from Admin APIs must clear or invalidate current auth state in the shell so the user can log in again.

## Domain Types

Define explicit status unions:

```ts
export type DocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'PARSED'
  | 'EMBEDDING'
  | 'READY'
  | 'FAILED';

export type ApiKeyStatus =
  | 'ACTIVE'
  | 'DISABLED'
  | 'EXPIRED'
  | 'REVOKED';

export type AppStatus = 'ENABLED' | 'DISABLED';

export type ModelConfigCapability = 'CHAT' | 'EMBEDDING';

export type CheckStatus = 'SUCCESS' | 'FAILED' | 'PARTIAL';
```

Keep frontend enum values aligned with backend enum values.

## API Key Types

Separate one-time secret responses from normal API key records:

```ts
export interface ApiKeyVO {
  id: string;
  appId: string;
  keyPrefix: string;
  status: ApiKeyStatus;
  expiresAt?: string;
  createdAt: string;
  lastUsedAt?: string;
}

export interface CreateApiKeyResponse {
  apiKey: ApiKeyVO;
  plaintextKey: string;
}
```

Only creation responses may contain `plaintextKey`. Normal list/detail APIs must not.

## Request Log Types

Request logs should use summaries and IDs:

```ts
export interface ApiRequestLogVO {
  id: string;
  requestId: string;
  appId: string;
  apiKeyId: string;
  model: string;
  questionSummary?: string;
  hitChunkIds: string[];
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  latencyMs: number;
  status: string;
  errorCode?: string;
  createdAt: string;
}
```

Do not type request logs as if they include full prompts or document content.

## Runtime Validation

For security-sensitive forms, validate before submitting:

```text
base URL format
required model names
positive embedding dimension
positive topK
valid similarity threshold
valid chunk size/overlap
```

Frontend validation improves UX but does not replace backend validation.

## Anti-Patterns

- `Record<string, any>` for API responses.
- Reusing create DTOs as update DTOs when semantics differ.
- Modeling secrets as optional fields on normal list/detail VOs.
- Accepting arbitrary string statuses in components without a fallback display.

## Knowledge Base and Document Types

Backend contracts for future frontend admin pages:

```ts
export type KnowledgeBaseStatus =
  | 'EMPTY'
  | 'PROCESSING'
  | 'READY'
  | 'FAILED';

export type DocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'PARSED'
  | 'EMBEDDING'
  | 'READY'
  | 'FAILED';

export interface DocumentVO {
  id: number;
  user_id: number;
  knowledge_base_id: number;
  original_filename: string;
  content_type: string | null;
  file_size: number;
  status: DocumentStatus;
  chunk_count: number;
  error_message: string | null;
  created_at: string;
  updated_at: string;
}

export interface KnowledgeBaseVO {
  id: number;
  user_id: number;
  name: string;
  embedding_model: string;
  embedding_dimension: number;
  status: KnowledgeBaseStatus;
  created_at: string;
  updated_at: string;
}
```

Note: `DocumentVO` does not expose `storage_path`. All fields use snake_case as produced by backend `@JsonProperty`.

## AppVO with Default Knowledge Base

`AppVO` now exposes `default_knowledge_base_id` (mirroring `default_model_config_id`):

```ts
export interface AppVO {
  id: number;
  user_id: number;
  name: string;
  status: AppStatus;
  default_model_config_id: number | null;
  default_knowledge_base_id: number | null;
  request_log_output_capture_enabled: boolean;
  created_at: string;
  updated_at: string;
}
```

KB binding admin API uses:

```ts
export interface BindAppDefaultKnowledgeBaseDTO {
  knowledge_base_id: number;
}

export interface BindAppDefaultKnowledgeBaseVO {
  app_id: number;
  user_id: number;
  default_knowledge_base_id: number;
}
```

App output capture switch management uses a focused DTO and API client:

```ts
export interface UpdateAppOutputCaptureDTO {
  request_log_output_capture_enabled: boolean;
}

updateAppOutputCapture(
  appId: number,
  dto: UpdateAppOutputCaptureDTO,
): Promise<ApiResponse<AppVO>>
```

The frontend endpoint path is `/admin/apps/{appId}/request-log-output-capture`, which maps to backend `/api/admin/apps/{appId}/request-log-output-capture`. The App management page may render this as an Ant Design `Switch`, but enabling from `false` to `true` must show an explicit risk confirmation before the API call. Disabling may call the API directly. The page must not render or persist `output_preview`, raw answers, prompts, request messages, provider bodies, raw SSE payloads, chunk content, embeddings, keys, hashes, encrypted upstream keys, stack traces, or environment values.

## Request Log Observability Types

Future frontend contracts for request log observability:

```ts
export interface RequestLogUsageVO {
  prompt_tokens: number | null;
  completion_tokens: number | null;
  total_tokens: number | null;
}

export interface ApiRequestLogVO {
  id: number;
  request_id: string;
  app_id: number;
  api_key_id: number;
  model: string | null;
  provider_name: string | null;
  status: 'success' | 'failure';
  error_code: string | null;
  latency_ms: number | null;
  upstream_latency_ms: number | null;
  usage: RequestLogUsageVO | null;
  messages_count: number | null;
  question_summary: string | null;
  hit_chunk_ids: number[];
  created_at: string;
}

export interface ApiRequestLogDetailVO extends ApiRequestLogVO {
  user_id: number;
  updated_at: string;
}

export interface ApiRequestLogPageVO<T> {
  items: T[];
  page: number;
  page_size: number;
  total: number;
}

export interface HitChunkSummaryVO {
  chunk_id: number;
  document_id: number;
  knowledge_base_id: number;
  source_filename: string | null;
  chunk_index: number;
  summary: string;
}
```

Forbidden fields never present in responses (do not type them):
```text
prompt, messages, full_messages, augmented_prompt, api_key, key_hash, authorization,
upstream_api_key, api_key_encrypted, chunk_content, embedding, provider_response_body,
stack_trace
```

### Request Log Output Preview Types

Normal request-log detail may include output metadata only:

```ts
export type OutputCaptureStatus =
  | 'DISABLED'
  | 'CAPTURED'
  | 'EMPTY'
  | 'TRUNCATED_ONLY'
  | 'REDACTED'
  | 'REDACTION_BLOCKED'
  | 'STREAMING_UNSUPPORTED'
  | 'FAILED'
  | 'EXPIRED';

export interface ApiRequestLogDetailVO extends ApiRequestLogVO {
  user_id: number;
  updated_at: string;
  output_capture_status: OutputCaptureStatus;
  completion_length: number | null;
  output_preview_available: boolean;
  output_preview_truncated: boolean;
  output_redacted: boolean;
  output_retention_expires_at: string | null;
}
```

Explicit preview access uses a separate DTO and VO:

```ts
export interface RequestLogOutputAccessDTO {
  confirm_access: boolean;
  reason?: string;
}

export interface RequestLogOutputPreviewVO {
  request_id: string;
  output_capture_status: OutputCaptureStatus;
  completion_length: number | null;
  output_preview: string | null;
  output_preview_truncated: boolean;
  output_redacted: boolean;
  output_retention_expires_at: string | null;
}
```

Frontend API client:

```ts
accessOutputPreview(appId, requestId, {
  confirm_access: true,
  reason,
})
```

UI rules:

- Detail drawer renders only output metadata by default.
- Preview content is fetched only from the explicit access modal after confirmation.
- Preview text must not be copied by default unless a future task adds an explicit approved copy flow.
- Do not persist preview content in localStorage, sessionStorage, global stores, or URL state.
- Keep shared DTO types UI-agnostic; labels and i18n keys stay in component/i18n files.
