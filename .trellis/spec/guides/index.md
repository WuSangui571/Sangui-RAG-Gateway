# Thinking Guides

> Use these guides before work that can cross module, API, database, security, or RAG pipeline boundaries.

## Available Guides

| Guide | Purpose | When to Use |
|-------|---------|-------------|
| [Project Specification](../sangui-rag-gateway.md) | Product boundary, architecture, API scope, RAG/security rules, roadmap | Always before new backend/frontend feature work |
| [Code Reuse Thinking Guide](./code-reuse-thinking-guide.md) | Identify patterns and reduce duplication | When repeated patterns or shared helpers appear |
| [Cross-Layer Thinking Guide](./cross-layer-thinking-guide.md) | Think through data flow across layers | Features spanning API, service, DB, frontend, deployment, or RAG pipeline |
| [RAG Retrieval Quality](../rag/retrieval-quality.md) | Retrieval contracts, no-hit behavior, recall limits, and future retrieval strategy boundaries | Before retrieval or request-log hit chunk changes |
| [RAG Prompt Context Policy](../rag/prompt-context-policy.md) | Prompt augmentation, context budgets, no-fabrication rules, and structured-output boundaries | Before prompt or context changes |
| [RAG Document Ingestion](../rag/document-ingestion.md) | Chunking, parser limits, document status, and async ingestion boundaries | Before ingestion, parser, chunking, or embedding changes |
| [Gateway Resilience](../gateway/resilience.md) | Upstream timeout, error normalization, safe logging, and fallback roadmap | Before upstream call, streaming, retry, or fallback changes |
| [RAG Security](../security/rag-security.md) | Tenant boundaries, prompt secrecy, safe evidence exposure, and request-log limits | Before RAG security or observability changes |

## Sangui-RAG-Gateway Thinking Triggers

Read the project specification and cross-layer guide when a task touches any of:

- OpenAI-compatible request/response shape.
- API key authentication, hashing, revocation, rate limits, or quotas.
- Upstream model provider configuration.
- Document upload, parsing, chunking, embedding, or status transitions.
- Vector retrieval, similarity thresholds, or context token limits.
- RAG prompt construction or no-hit policy.
- Streaming responses and client disconnect behavior.
- Database schema or pgvector queries.
- Request logs, metrics, or sensitive data handling.
- Frontend workflows that reveal secrets or display processing status.

When a trigger matches, read the matching detailed spec in `rag/`, `gateway/`, or `security/` in addition to this thinking guide. Those files define executable contracts and validation matrices; this guide only defines what to think about.

## Product Boundary Questions

Before adding a feature, answer:

- Does it serve an OpenAI-compatible RAG gateway?
- Does it reduce integration cost for existing systems?
- Does it improve private knowledge enhancement, reliability, security, or observability?
- Does it keep the system small and explainable?
- Does it avoid drifting into a full low-code AI platform?

If the answer is no, defer the feature unless the user explicitly redefines the project scope.

## Pre-Modification Rule

Before changing any constant, status, API field, environment variable, or database column, search for existing references first.

Prefer `rg`:

```bash
rg "value_to_change"
```

This prevents mismatched contracts across backend, frontend, docs, migrations, and tests.

## How to Use This Directory

1. Before coding, read the relevant thinking guide.
2. During implementation, return to the guides when logic crosses layers or starts repeating.
3. After bugs or design changes, update the relevant spec so future work has better context.

Core principle:

```text
Think through API, tenant, RAG, secret, and deployment boundaries before writing code.
```
