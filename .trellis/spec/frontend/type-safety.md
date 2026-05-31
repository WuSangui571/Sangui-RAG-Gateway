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
