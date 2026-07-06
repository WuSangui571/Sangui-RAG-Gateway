# README Documentation Simplification

## Task Classification

Simple Task: documentation-only README restructuring.

Reasoning:
- Primary expected production edit is `README.md`.
- No runtime behavior, API contract, database schema, frontend type, DTO, storage, auth, or permission logic should change.
- The task still needs cross-module research because README must accurately describe product scope, deployment, admin workflows, RAG behavior, and safe evidence boundaries for first-time users.

## Goal

Rewrite the project README into a concise first-use entry document for people seeing Sangui-RAG-Gateway for the first time.

The README should quickly answer:
- What is this project?
- What can it do now?
- How do I deploy it locally with Docker Compose?
- How do I configure the first working app?
- How do existing systems call it?
- What real modules/pages/files exist in the repository?

Everything else should be shortened, moved behind links, or removed from README if it is not essential for first-time use.

## Audience

First-time users and developers evaluating or deploying the project.

They should not need to read long operational runbooks before understanding the project.

## Current Problem

Current README is too long and mixes:
- Product overview.
- Full deployment instructions.
- Detailed Admin API reference.
- Long demo acceptance scripts and evidence rules.
- Key rotation and incident runbooks.
- CI boundary classification.
- Developer command reference.
- Project structure.

This makes the first-use path hard to scan and increases stale documentation risk.

## Requirements

1. Keep README concise and first-use oriented.
   - Target structure should be scannable in one pass.
   - Prefer short sections, compact tables, and links to detailed docs.
   - Do not keep long runbooks inline unless they are required for first successful deployment.

2. Preserve accurate product positioning.
   - Sangui-RAG-Gateway is a lightweight OpenAI-compatible RAG enhancement gateway.
   - It is API-first middleware, not a Dify/FastGPT clone, workflow platform, agent platform, or full OpenAI API replacement.
   - README must state: `This project supports a compatible subset of OpenAI Chat Completions API.`

3. Preserve the implemented capability list, but compress it.
   - Backend: Spring Boot 3.4, Java 21, Flyway, PostgreSQL/pgvector, Redis.
   - Frontend: React 18, TypeScript, Vite, Ant Design admin console.
   - Gateway: `GET /v1/models`, `POST /v1/chat/completions`, non-streaming and streaming.
   - Admin: login, model configs, knowledge bases, document upload/status/retry/delete, apps, API keys, smoke/test chat, request logs.
   - RAG: app-bound KB retrieval, prompt augmentation, request-log safe evidence.
   - Deployment: full-stack Docker Compose.

4. Keep the deployment path accurate and compact.
   - Required local setup:
     - copy `.env.example` to `.env`
     - run `docker compose --env-file .env -f deploy/docker-compose.yml up -d --build`
     - check `http://localhost:8080/api/health`
     - open `http://localhost:3000`
   - Mention PG/Redis host ports are internal by default and require `deploy/docker-compose.host-ports.yml` only when local host access is needed.
   - Mention production-like runs require replacing local secrets.

5. Keep the first admin setup flow.
   - Login/admin bootstrap note.
   - Create model config.
   - Create knowledge base and upload a supported document.
   - Create app and bind model config + knowledge base.
   - Create API key and copy it immediately.
   - Call `/v1/chat/completions`.
   - Check request logs.

6. Do not overpromise document support.
   - Current README should not claim reliable complex PDF/DOCX/table QA unless code and tests prove it.
   - State conservative supported/primary document path based on current code/spec: text-like docs, txt/md/markdown, with PDF/DOCX only if verified from current implementation before documenting.
   - If unsure, document only verified file support from code and leave PDF/DOCX as roadmap/limited future work.

7. Reduce or relocate long detailed sections.
   - Candidate sections to shorten or move out of README:
     - Admin API endpoint reference.
     - Demo acceptance flow.
     - Runtime evidence checklist.
     - Key rotation/revocation runbooks.
     - Model config key rotation.
     - Lost/leaked API key runbook.
     - CI failure boundary classification.
   - Existing `docs/runtime-evidence-checklist.md` should be linked rather than duplicated.
   - If new detailed docs are needed, create docs under `docs/` only if necessary; otherwise keep a compact link/reference.

8. Screenshots.
   - No screenshot assets currently exist in the repository.
   - README may include a short `Screenshots` section with insertion guidance comments or a small placeholder list, but should not reference non-existent image files as if they exist.
   - Recommended future screenshot insertion points:
     - After "What it does": Admin console overview/sidebar.
     - After first setup flow: Model config or App detail page showing bindings.
     - Near request logs: Request log list/detail showing safe metadata only.
   - Any screenshot must redact API keys, upstream keys, prompts, full answers, chunk content, provider bodies, stack traces, storage paths, and real `.env` values.

9. Keep security and safe evidence boundaries visible but brief.
   - Full app API key is shown once only.
   - App keys are hashed; upstream keys are encrypted.
   - Admin APIs use JWT; public `/v1/*` uses app API keys.
   - Request logs expose safe metadata only, not full prompts, raw answers, chunk content, provider bodies, stack traces, keys, or storage paths.

10. Keep project structure practical.
    - Include a compact tree of the real top-level modules:
      - `backend/`
      - `frontend/`
      - `deploy/`
      - `scripts/`
      - `docs/`
      - `.trellis/`
    - Do not include a long exhaustive package tree unless it helps first-time navigation.

11. Keep development/test commands compact.
    - Backend compile/test examples.
    - Frontend lint/test/typecheck/build.
    - Compose config sanity for deployment doc changes.
    - `git diff --check`.

## Non-Goals / Forbidden Scope

Do not:
- Change backend, frontend, API, DB, migration, Docker runtime behavior, auth, permissions, storage, RAG, retrieval, prompt, rate-limit, or request-log implementation.
- Add new product features.
- Add screenshots that do not exist.
- Invent deployment commands not backed by current repo files.
- Advertise unsupported OpenAI APIs such as `/v1/responses`, `/v1/embeddings`, images, tools/function calling, vision, audio, or full OpenAI compatibility.
- Expose real secrets or encourage committing `.env`, API keys, provider keys, uploads, `node_modules`, `dist`, or Maven/target output.
- Keep README as a full operational manual.

## API / Command / Payload Contracts

No API or payload contract changes are expected.

README must accurately document these existing commands and payload examples:

### Main Compose command

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

### Optional host DB/Redis ports

```bash
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml up -d --build
```

### Health check

```bash
curl http://localhost:8080/api/health
```

Expected safe response fields:

```json
{
  "code": "OK",
  "data": {
    "status": "UP",
    "service": "sangui-rag-gateway"
  }
}
```

### Gateway chat example

```bash
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-sangui-<your-key>" \
  -d '{"model":"ignored","messages":[{"role":"user","content":"Summarize the uploaded document."}]}'
```

Payload fields to mention as supported subset:
- `model`
- `messages`
- `temperature`
- `max_tokens`
- `top_p`
- `stream`

Public auth:

```http
Authorization: Bearer sk-sangui-...
```

Admin auth:

```http
Authorization: Bearer <admin-jwt>
```

## Validation / Error Matrix

Documentation validation should check:

| Scenario | Expected README behavior | Assertion point |
|---|---|---|
| First-time user wants to know what the project is | Top section explains lightweight OpenAI-compatible RAG gateway in 1-2 short paragraphs | Manual review |
| First-time user wants to run it | Docker Compose path is visible before deep details | README scan |
| User needs DB/Redis host access | Host-port override is documented as opt-in only | Compose docs match `deploy/docker-compose.host-ports.yml` |
| User integrates an existing system | README shows `base_url`/`api_key` replacement idea and `/v1/chat/completions` example | Gateway spec match |
| User expects full OpenAI API | README explicitly says compatible subset and lists only supported public endpoints | Spec match |
| User needs detailed smoke/evidence | README links to `scripts/demo-smoke.ps1` and `docs/runtime-evidence-checklist.md` instead of duplicating long content | README scan |
| User needs screenshots | README identifies insertion positions or safe screenshot guidance without broken image links | Link check/manual review |
| Sensitive examples | No real `sk-sangui-*`, provider keys, admin JWTs, prompts, raw answers, chunk content, provider bodies, stack traces, or `.env` secrets are committed | `rg` scan |

## Good / Base / Bad Cases

Good:
- README is short, accurate, and first-use oriented.
- A new user can understand the product and run Docker Compose without reading long runbooks.
- Detailed runbooks are linked or relocated.
- API support is described as a subset, not full OpenAI compatibility.
- Screenshot guidance is safe and does not create broken references.

Base:
- README keeps some compact troubleshooting notes and links to detailed files.
- No screenshots are committed yet, but insertion points are clearly noted.
- Documentation-only validation passes without starting the full runtime.

Bad:
- README still exceeds first-use scope with hundreds of lines of runbooks.
- README claims unsupported APIs, unsupported file types, or full platform capabilities.
- README references screenshots that do not exist.
- README includes secret-looking examples, full keys, raw prompts, answer text, provider bodies, stack traces, chunk content, or real local `.env` values.
- Documentation edits accidentally change runtime config or implementation files.

## Expected Files To Modify

Likely:
- `README.md`: primary rewrite/simplification.

Optional, only if needed:
- `docs/demo-acceptance.md`: if long demo acceptance content must be preserved outside README.
- `docs/key-management-runbook.md`: if key rotation/leak runbooks need relocation.
- `docs/runtime-evidence-checklist.md`: only update links/wording if README references require small alignment.

Do not modify implementation files.

## Required Tests / Validation

Minimum after documentation changes:

```bash
git diff --check
docker compose --env-file .env.example -f deploy/docker-compose.yml config
rg -n "sk-sangui-[A-Za-z0-9_-]{8,}|Authorization: Bearer sk-sangui-|provider_response_body|api_key_encrypted|key_hash|augmented_prompt|chunk_content|stack_trace" README.md docs
```

If README changes frontend or backend command claims, also run the relevant static checks:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

```bash
cd backend
mvn -q -DskipTests compile
```

For a documentation-only README simplification, full backend/frontend tests are not required unless implementation files are changed.

## Acceptance Criteria

- [ ] README starts with a concise product explanation and current status.
- [ ] README clearly says the project supports a compatible subset of OpenAI Chat Completions API.
- [ ] README includes a compact "what it does now" capability summary grounded in current code/spec.
- [ ] README includes one-command Docker Compose deployment and health verification.
- [ ] README includes first admin setup flow.
- [ ] README includes a minimal `/v1/chat/completions` usage example.
- [ ] README identifies supported public endpoints without overclaiming unsupported OpenAI APIs.
- [ ] README trims or moves long runbook/evidence/API reference content behind links.
- [ ] README includes safe screenshot insertion guidance or a no-screenshot note without broken image links.
- [ ] README includes a compact project structure and test/development command section.
- [ ] README does not expose sensitive examples or forbidden fields.
- [ ] No implementation/runtime files are modified.

## Handoff Notes

Implementation should be a docs-only pass. Keep the final diff easy to review:
- Prefer rewriting README into a shorter document over incremental small edits to the existing 1200-line structure.
- Verify every capability claim against current code/spec before keeping it.
- If relocating runbooks, preserve important safety content in `docs/` and link from README.
- Keep README as the entry point, not the archive for every operational detail.
