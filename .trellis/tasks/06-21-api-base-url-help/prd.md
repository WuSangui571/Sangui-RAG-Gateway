# API Base URL Help for API Key Creation

## Goal

Improve the developer onboarding guidance shown after an app API key is created, so users can copy the correct OpenAI-compatible SDK `base_url` and the full Chat Completions endpoint without guessing the gateway path.

This task is scoped to frontend guidance around API key creation and one-time key display. It must not change backend APIs, database schema, gateway routing, deployment config, or API key security semantics.

## Background

Sangui-RAG-Gateway is a lightweight OpenAI-compatible RAG gateway. External systems should integrate by replacing their upstream LLM `api_key` and OpenAI-compatible `base_url` with the Sangui gateway values.

The current API key creation flow is adjacent to the recently completed request-log/API key usability work. After a user creates an API key, the next practical need is knowing what address to configure in an SDK or direct HTTP client.

## Scope Classification

- Type: Simple Task
- Area: frontend
- Expected blast radius: API key creation success UI, i18n dictionary, unit/component tests
- Hotfix vs structural: targeted frontend UX fix; no structural backend or API contract change

## Requirements

- Locate the API key creation success / one-time secret display component or page.
- Display runtime-derived integration addresses, based on the current browser origin instead of a hardcoded host:
  - OpenAI-compatible SDK `base_url`: `<window.location.origin>/v1`
  - Full Chat Completions endpoint: `<window.location.origin>/v1/chat/completions`
- Include examples using `http://localhost:3000` and `http://localhost:3000/v1/chat/completions` only as examples, not as fixed production values.
- Make the text clear that the full generated API key is still visible only once and must be copied before leaving the success state.
- Add or update Chinese and English i18n copy through the existing typed dictionary flow.
- Add focused tests covering:
  - default/local origin rendering;
  - non-localhost origin rendering;
  - no hardcoded `localhost:3000` being used as the actual generated production address;
  - one-time API key secret handling remains in transient UI state.

## Explicit Non-Goals

- Do not modify backend API endpoints, DTOs, controllers, services, entities, migrations, or gateway auth behavior.
- Do not modify Docker, Nginx, deployment env vars, or reverse proxy routing.
- Do not add new runtime config for frontend base URL unless existing code already has an approved single source of truth that should be reused.
- Do not persist plaintext generated API keys in localStorage, sessionStorage, global stores, URL state, logs, or test snapshots.
- Do not add a chat playground, smoke-test shortcut, request-log feature, or broader onboarding wizard.
- Do not change API key list/detail behavior or normal masked key display after creation.

## API / Command / Payload Contract

No API, command, payload, DTO, DB, or environment contract changes are intended.

Existing public gateway contract remains:

```text
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
```

Frontend display contract:

```text
sdkBaseUrl = `${currentOrigin}/v1`
chatCompletionsEndpoint = `${currentOrigin}/v1/chat/completions`
```

`currentOrigin` should come from the browser runtime origin (`window.location.origin`) or an existing local helper that already represents it. If tests run in jsdom, inject/mock the origin at the browser-location boundary rather than hardcoding production values.

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| User creates an API key while frontend runs at `http://localhost:3000` | Success UI shows SDK `base_url` as `http://localhost:3000/v1` and full endpoint as `http://localhost:3000/v1/chat/completions` | API key page/component test |
| User creates an API key while frontend runs at `https://rag.example.com` | Success UI shows `https://rag.example.com/v1` and `https://rag.example.com/v1/chat/completions` | API key page/component test with mocked origin |
| UI includes localhost examples | Examples are visually/textually examples only and are not used as generated runtime values | i18n/component test |
| API key creation succeeds | Plaintext key appears only in the one-time success state | existing or updated API key test |
| Success dialog closes or creation state resets | Plaintext key is cleared from component/page memory state | existing or updated API key test |
| User switches locale | Chinese and English labels/help text render through dictionary keys | i18n-aware component test |
| Backend/API unavailable during creation | Existing error path remains visible; no fake endpoint success is fabricated | existing create failure test, if present |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | A user creates an API key on a non-local production origin and can copy both `https://host/v1` for SDK `base_url` and `https://host/v1/chat/completions` for raw HTTP calls. The generated key is still one-time only and cleared after closing the success UI. |
| Base | A local developer runs the frontend at `http://localhost:3000`; the UI shows local values derived from the current origin and may include local examples, but no value is baked into implementation as the only valid host. |
| Bad | The UI hardcodes `localhost:3000` as the generated address, suggests SDK `base_url` without `/v1`, persists the plaintext key beyond the one-time state, or changes backend/gateway contracts to satisfy a frontend copy issue. |

## Required Tests And Assertion Points

- Targeted Vitest/React Testing Library tests for the API key page or one-time secret component.
- Assertions should verify generated address text under at least two origins:
  - `http://localhost:3000`
  - `https://rag.example.com`
- Assertions should verify the full endpoint contains `/v1/chat/completions`.
- Assertions should verify the SDK base URL contains `/v1` but not `/chat/completions`.
- Assertions should verify plaintext API key behavior remains one-time/transient.
- Existing create/list/revoke/disable behavior must not regress.

## Validation Commands

Run from `frontend/` after implementation:

```bash
cmd /c npx vitest run src/__tests__/pages/ApiKeyPage.test.tsx
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

If the test file path differs after code research, use the actual API key page/component test file path.

## Planning Self-Check

- Acceptance criteria: generated SDK base URL, full endpoint, i18n, non-local origin tests, one-time secret lifecycle.
- Prohibited scope: backend, DB, API, deployment, gateway routing, persistent secret storage.
- Expected files: API key page/component, i18n dictionary, API key page/component tests, optionally a small frontend utility if an existing pattern supports it.
- Required guideline reads: frontend directory/component/state/type/quality/hook guidelines, project spec, shared guides, security spec for secret boundaries.

