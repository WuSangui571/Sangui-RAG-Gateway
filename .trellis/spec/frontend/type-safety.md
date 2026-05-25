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
