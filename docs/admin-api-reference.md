# Admin API Reference

Admin APIs return the `ApiResponse<T>` envelope (`code`, `message`, `data`). Except for login, they require `Authorization: Bearer <admin-jwt>`.

## Auth

| Operation | Method | Route |
|---|---|---|
| Login | `POST` | `/api/admin/auth/login` |
| Current admin user | `GET` | `/api/admin/auth/me` |

## Apps

| Operation | Method | Route |
|---|---|---|
| Create app | `POST` | `/api/admin/apps` |
| List apps | `GET` | `/api/admin/apps` |
| Get app detail | `GET` | `/api/admin/apps/{id}` |
| Disable app | `POST` | `/api/admin/apps/{id}/disable` |
| Enable app | `POST` | `/api/admin/apps/{id}/enable` |
| App readiness | `GET` | `/api/admin/apps/{appId}/readiness` |
| Bind default model config | `PUT` | `/api/admin/apps/{appId}/default-model-config` |
| Bind default knowledge base | `PUT` | `/api/admin/apps/{appId}/knowledge-base` |
| Set request-log output capture | `PUT` | `/api/admin/apps/{appId}/request-log-output-capture` |

## Model Configs

| Operation | Method | Route |
|---|---|---|
| Create model config | `POST` | `/api/admin/model-configs` |
| Update model config | `PUT` | `/api/admin/model-configs/{id}` |
| Get model config detail | `GET` | `/api/admin/model-configs/{id}` |
| List model configs | `GET` | `/api/admin/model-configs` |
| Disable model config | `POST` | `/api/admin/model-configs/{id}/disable` |
| Enable model config | `POST` | `/api/admin/model-configs/{id}/enable` |
| Check model config payload | `POST` | `/api/admin/model-configs/check` |
| Check saved model config | `POST` | `/api/admin/model-configs/{id}/check` |
| List chat-capable configs | `GET` | `/api/admin/model-configs/chat-capable` |

## API Keys

| Operation | Method | Route |
|---|---|---|
| Create API key | `POST` | `/api/admin/apps/{appId}/api-keys` |
| List API keys | `GET` | `/api/admin/apps/{appId}/api-keys` |
| Disable API key | `POST` | `/api/admin/api-keys/{id}/disable` |
| Enable API key | `POST` | `/api/admin/api-keys/{id}/enable` |
| Revoke API key | `POST` | `/api/admin/api-keys/{id}/revoke` |

## Knowledge Bases & Documents

| Operation | Method | Route |
|---|---|---|
| Create knowledge base | `POST` | `/api/admin/knowledge-bases` |
| List knowledge bases | `GET` | `/api/admin/knowledge-bases` |
| Get knowledge base detail | `GET` | `/api/admin/knowledge-bases/{id}` |
| Delete knowledge base | `DELETE` | `/api/admin/knowledge-bases/{id}` |
| Upload document | `POST` | `/api/admin/knowledge-bases/{knowledgeBaseId}/documents` |
| List documents in knowledge base | `GET` | `/api/admin/knowledge-bases/{knowledgeBaseId}/documents` |
| Get document detail | `GET` | `/api/admin/documents/{documentId}` |
| Delete document | `DELETE` | `/api/admin/documents/{documentId}` |
| Retry document processing | `POST` | `/api/admin/documents/{documentId}/processing-task/retry` |

## Request Logs

| Operation | Method | Route |
|---|---|---|
| List request logs | `GET` | `/api/admin/apps/{appId}/request-logs` |
| Get request log detail | `GET` | `/api/admin/apps/{appId}/request-logs/{requestId}` |
| Get hit chunk summaries | `GET` | `/api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks` |
| Access output preview | `POST` | `/api/admin/apps/{appId}/request-logs/{requestId}/output-preview/access` |

## Retrieval Evaluation

| Operation | Method | Route |
|---|---|---|
| Run retrieval evaluation | `POST` | `/api/admin/apps/{appId}/retrieval-evaluations/runs` |

## Response Envelope

All admin APIs return:

```json
{
  "code": "OK",
  "message": "success",
  "data": { ... }
}
```

Primary error codes: `INVALID_REQUEST` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `KNOWLEDGE_BASE_IN_USE` (409).

## Disable Impact on Gateway

| Disabled resource | Gateway effect | Error |
|---|---|---|
| App | All API keys under the app fail `/v1/*` auth | 401 `invalid_api_key` |
| Model Config | App key authenticates but model resolution fails | 409 `model_config_not_ready` |
| API Key | Only that specific key fails `/v1/*` auth | 401 `invalid_api_key` |

Disabling is idempotent. Re-enabling restores normal behavior without modifying bindings or keys.
