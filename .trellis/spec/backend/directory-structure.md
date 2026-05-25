# Backend Directory Structure

> Target package structure for the Spring Boot backend. Keep modules organized by business capability and keep cross-cutting code in `common`.

## Root Package

Use this root package:

```text
com.sangui.raggateway
```

Recommended structure:

```text
com.sangui.raggateway
  ├── common
  │   ├── config
  │   ├── exception
  │   ├── response
  │   ├── security
  │   └── util
  ├── auth
  ├── user
  ├── app
  ├── apikey
  ├── model
  ├── knowledge
  ├── document
  ├── embedding
  ├── retrieval
  ├── gateway
  │   ├── openai
  │   ├── completion
  │   └── stream
  ├── rag
  │   ├── prompt
  │   ├── context
  │   └── pipeline
  └── log
```

## Layering

Use these layer responsibilities:

```text
Controller: HTTP input/output only
Service: business logic and transaction boundaries
Repository/Mapper: database access
Client: external HTTP calls
Converter: DO/DTO/VO conversion
Handler/Strategy: pluggable implementations
```

Do not put complex business logic in controllers.

## Module Responsibilities

`common` contains reusable infrastructure only:

- `config`: Spring configuration, properties binding, beans.
- `exception`: domain exceptions, global exception handler, error codes.
- `response`: admin response envelopes and OpenAI-compatible error helpers.
- `security`: auth filters, key hashing, encryption helpers, tenant context.
- `util`: small stateless utilities only.

`auth` and `user` manage admin console login and user identity.

`app` manages externally exposed RAG apps and app-level settings.

`apikey` manages app API keys, hashing, prefixes, expiration, revocation, rate-limit metadata, and quota metadata.

`model` manages upstream provider config, encrypted upstream API keys, base URLs, chat models, embedding models, and embedding dimensions.

`knowledge` manages knowledge bases, chunk strategy, embedding model binding, and readiness status.

`document` manages upload, storage abstraction, parsing, cleaning, chunking, document status, and parser implementations.

`embedding` manages embedding client calls and embedding result validation.

`retrieval` manages vector search, threshold filtering, deduplication, and context token limits.

`rag` manages prompt/context construction and the RAG orchestration pipeline.

`gateway` owns OpenAI-compatible public endpoints, request/response models, upstream forwarding, and streaming behavior.

`log` records API request logs and observability data.

## Naming Conventions

Use clear suffixes:

```text
Entity/DO: database entity
DTO: HTTP/admin request object
VO: HTTP/admin response object
Command: command-style service input
Query: query condition object
Client: external service client
Handler: strategy handler
Properties: configuration properties
```

Examples:

```text
CreateAppDTO
AppVO
UploadDocumentCommand
OpenAiChatCompletionRequest
OpenAiChatCompletionResponse
DocumentParser
PdfDocumentParser
EmbeddingClient
RetrievalService
RagPromptBuilder
```

## Dependency Rules

- Controllers may depend on services and converters, not mappers directly.
- Services may depend on mappers/repositories, clients, converters, and domain helpers.
- Mappers/repositories must not call services or external clients.
- `gateway` may orchestrate `apikey`, `app`, `retrieval`, `rag`, `model`, and `log`, but detailed logic belongs in those modules.
- `rag.prompt` should not call the database or upstream HTTP directly.
- `retrieval` should not construct final OpenAI messages.
- `document` ingestion should not depend on gateway request models.
- `common` must not depend on business modules.

## Resource Layout

```text
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
src/main/resources/db/migration
src/main/resources/mapper
```

Use migration files for schema changes. Do not rely on ad hoc manual SQL in production setup.

## Tests

Mirror package structure under `src/test/java`.

Prioritize unit tests for:

```text
ApiKeyHasher
RagPromptBuilder
TextChunker
RetrievalService
OpenAiResponseAdapter
DocumentParser implementations
```

Use integration tests for:

```text
Upload document -> parse/chunk/embed -> chat completions -> augmented response
```
