# Gateway Specifications

> Gateway-level reliability, upstream error, timeout, streaming, and observability contracts for Sangui-RAG-Gateway.

## Available Specs

| Spec | Purpose | Status |
|------|---------|--------|
| [Resilience](./resilience.md) | Upstream timeout, error normalization, safe logging, retry/fallback boundaries, and request-log failure recording | Active |

## Pre-Development Checklist

- [ ] Confirm whether the task changes upstream chat calls, embedding calls, timeout handling, streaming behavior, request logs, retry behavior, or provider routing.
- [ ] Read [Project Specification](../sangui-rag-gateway.md) for API compatibility and gateway boundary.
- [ ] Read [Resilience](./resilience.md) before changing upstream clients, timeout settings, failure mapping, or fallback behavior.
- [ ] Read [RAG Security](../security/rag-security.md) before logging provider failures, request bodies, prompts, keys, or document content.
- [ ] Read [Error Handling](../backend/error-handling.md) before changing public error codes or response shapes.
- [ ] Read [Logging Guidelines](../backend/logging-guidelines.md) before adding or changing gateway logs.

