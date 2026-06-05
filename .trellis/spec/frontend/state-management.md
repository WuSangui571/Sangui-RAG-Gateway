# State Management

> Keep state as close to the workflow as possible. The admin console should not accumulate broad global state for data that can be fetched from the server.

## State Categories

Use local state for:

```text
form fields
modal/drawer visibility
active tabs
table filters
upload progress for the current page
one-time API key display state
```

Use server state for:

```text
apps
knowledge bases
documents
model configs
API keys
request logs
document processing status
```

Use global state only for:

```text
authenticated user
access token/session marker
current layout/navigation preferences
rare cross-page UI flags
```

## UI Preference Baseline

The admin console has a frontend-only UI preference provider:

```text
frontend/src/app/providers/UIPreferenceProvider.tsx
frontend/src/app/i18n/dict.ts
frontend/src/app/i18n/useI18n.ts
```

Allowed persisted keys:

```text
sangui-admin-theme  -> "dark" | "light"
sangui-admin-locale -> "zh-CN" | "en-US"
```

Rules:

- Default theme is `dark`; default locale is `zh-CN`.
- Invalid stored values must reset to defaults explicitly.
- Ant Design theme must be applied through `ConfigProvider`.
- Frontend-owned display text should use the typed dictionary and `useI18n()`.
- Backend enum values, backend error codes, API payload fields, and DTO names must not be translated or remapped at the contract layer.
- The UI preference provider must never store server data, generated app API keys, upstream keys, prompts, request bodies, request logs, hit chunk data, admin API responses, or runtime evidence.
- Full generated API keys remain page/modal memory state only and must be cleared when the one-time display lifecycle ends.

## Global Store Rules

If using Vue, Pinia is preferred. If using React, use the selected lightweight store or context sparingly.

Global stores must not become a cache of every backend entity. Prefer per-page fetching and server-state hooks/composables.

Store authentication state separately from domain data.

## Server State Refresh

For mutation flows:

```text
submit mutation
show success/error
refresh affected list/detail
clear transient form state when appropriate
```

For document upload:

```text
upload file
create document record
show processing state
poll until READY or FAILED
allow retry or re-upload when supported
```

## Secret State

Full API keys must never enter persistent frontend storage.

Rules:

- Full generated app API keys can exist only in memory for one-time display.
- Clear full keys when the dialog/drawer/page closes.
- Upstream API keys should be sent to the backend when entered and then discarded from form state after save.
- Do not store secrets in global stores unless there is a hard requirement; there should not be one for MVP.

## Derived State

Compute derived display values near the UI:

```text
status label
status color
masked key
formatted latency
formatted token count
```

Keep business rules that affect backend behavior on the backend. The frontend may mirror simple display rules but should not be the source of truth for authorization, quotas, or tenant isolation.

## Anti-Patterns

- Global store containing all apps, documents, model configs, logs, and API keys together.
- Persisting generated API keys in local storage.
- Component-level duplicated API loading logic across pages.
- Optimistic UI for security-sensitive changes such as revoking API keys unless rollback behavior is explicit.
