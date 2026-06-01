# RAG Pipeline Specifications

> RAG-specific implementation contracts for Sangui-RAG-Gateway. These specs keep retrieval, prompt augmentation, document ingestion, observability, and security aligned with the lightweight OpenAI-compatible gateway boundary.

## Available Specs

| Spec | Purpose | Status |
|------|---------|--------|
| [Retrieval Quality](./retrieval-quality.md) | Tenant-safe vector retrieval, no-hit behavior, recall limits, and future retrieval strategy boundaries | Active |
| [Prompt Context Policy](./prompt-context-policy.md) | RAG context construction, prompt augmentation, no-fabrication rules, and structured-output boundaries | Active |
| [Document Ingestion](./document-ingestion.md) | Chunking, parser limits, document status, large-ingestion boundaries, and future parser roadmap | Active |

## Pre-Development Checklist

- [ ] Confirm whether the task changes retrieval, prompt augmentation, document ingestion, request logs, or no-hit behavior.
- [ ] Read [Project Specification](../sangui-rag-gateway.md) for product boundary and MVP scope.
- [ ] Read [Retrieval Quality](./retrieval-quality.md) before changing retrieval SQL, similarity thresholds, topK, no-hit policy, or hit chunk logging.
- [ ] Read [Prompt Context Policy](./prompt-context-policy.md) before changing prompt messages, context assembly, structured output handling, or no-hit instructions.
- [ ] Read [Document Ingestion](./document-ingestion.md) before changing parsing, chunking, embedding, document status transitions, or large-document handling.
- [ ] Read [RAG Security](../security/rag-security.md) before exposing context, chunks, logs, prompts, document content, or tenant-scoped data.
- [ ] For cross-layer changes, read [Cross-Layer Thinking Guide](../guides/cross-layer-thinking-guide.md).

## Non-Goal Reminder

RAG work must not turn this project into a Dify/FastGPT-style workflow platform, agent platform, table agent, or low-code AI product. The goal is still a small, explainable, OpenAI-compatible RAG gateway.
