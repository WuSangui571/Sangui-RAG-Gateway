# Security Specifications

> Security contracts for RAG retrieval, prompt context, request logs, document evidence, and tenant-scoped gateway behavior.

## Available Specs

| Spec | Purpose | Status |
|------|---------|--------|
| [RAG Security](./rag-security.md) | Tenant boundaries, prompt/context secrecy, safe evidence exposure, request-log limits, and future safety-model roadmap | Active |

## Pre-Development Checklist

- [ ] Confirm whether the task touches API keys, upstream keys, retrieval SQL, knowledge-base data, prompt context, logs, error responses, or hit chunk evidence.
- [ ] Read [Project Specification](../sangui-rag-gateway.md) for security and product boundaries.
- [ ] Read [RAG Security](./rag-security.md) before changing RAG context, retrieval, document evidence, request logs, or prompt behavior.
- [ ] Read [Database Guidelines](../backend/database-guidelines.md) before changing tenant-scoped queries.
- [ ] Read [Logging Guidelines](../backend/logging-guidelines.md) before changing logs or persisted observability fields.
- [ ] Read [Error Handling](../backend/error-handling.md) before changing public errors.

