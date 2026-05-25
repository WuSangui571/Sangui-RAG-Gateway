# Backend Development Guidelines

> Backend conventions for Sangui-RAG-Gateway, a lightweight OpenAI-compatible RAG gateway built around Spring Boot, PostgreSQL/pgvector, Redis, and upstream OpenAI-compatible providers.

## Overview

The backend must stay API-first, security-conscious, and lightweight. It should expose a compatible subset of OpenAI APIs while hiding document ingestion, retrieval, prompt augmentation, upstream forwarding, observability, and tenant isolation.

The project is currently a blank implementation, so these files define target conventions for future work rather than extracted code patterns.

## Must-Read Before Backend Work

Always read the project overview first:

- [Project Specification](../sangui-rag-gateway.md)

Then read the relevant backend guideline files:

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Spring Boot module and package organization | Active |
| [Database Guidelines](./database-guidelines.md) | PostgreSQL, pgvector, MyBatis-Plus, tenant-safe queries, migrations | Active |
| [Error Handling](./error-handling.md) | OpenAI-compatible errors and internal exception rules | Active |
| [Logging Guidelines](./logging-guidelines.md) | Safe structured logging and request observability | Active |
| [Quality Guidelines](./quality-guidelines.md) | Testing, security, RAG pipeline, streaming, and code review standards | Active |

## Pre-Development Checklist

- [ ] Confirm whether the task affects the gateway API, admin APIs, ingestion pipeline, retrieval, prompt building, upstream forwarding, database schema, or deployment.
- [ ] Read [Project Specification](../sangui-rag-gateway.md) for product boundary and MVP scope.
- [ ] Read [Directory Structure](./directory-structure.md) before adding packages or modules.
- [ ] Read [Database Guidelines](./database-guidelines.md) before adding tables, queries, migrations, or vector retrieval.
- [ ] Read [Error Handling](./error-handling.md) before adding public API errors or upstream error mapping.
- [ ] Read [Logging Guidelines](./logging-guidelines.md) before logging request, prompt, key, provider, or document data.
- [ ] Read [Quality Guidelines](./quality-guidelines.md) before declaring work complete.
- [ ] For cross-layer changes, read [Cross-Layer Thinking Guide](../guides/cross-layer-thinking-guide.md).

## Backend Stack Target

```text
Java 21
Spring Boot 3.x
Spring Security or Sa-Token
MyBatis-Plus
PostgreSQL + pgvector
Redis
MinIO or local file storage
WebClient/WebFlux
Docker Compose
```

Avoid introducing new infrastructure unless it directly improves the RAG gateway goal.
