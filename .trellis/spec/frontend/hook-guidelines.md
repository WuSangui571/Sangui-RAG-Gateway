# Hook and Composable Guidelines

> Use hooks/composables to keep pages readable, centralize repeated UI logic, and isolate server-state behavior.

## Naming

If using Vue, use composable names:

```text
useApps
useAppDetail
useKnowledgeBases
useDocumentUpload
useApiKeys
useRequestLogs
```

If using React, use hook names:

```text
useApps
useAppDetail
useKnowledgeBases
useDocumentUpload
useApiKeys
useRequestLogs
```

Names should describe the domain workflow, not the HTTP method.

## Responsibilities

Hooks/composables may own:

- Data loading state.
- Mutations and refresh behavior.
- Polling document processing status.
- Upload progress state.
- Local table filters and pagination parameters.
- Mapping API errors into displayable messages.

They should not own:

- Global authentication token storage unless auth-specific.
- Cross-page business state that belongs in a store.
- Large presentation logic that belongs in components.

## Data Fetching

All network calls should go through typed API clients under `src/api`.

Recommended pattern:

```text
page -> hook/composable -> api client -> backend
```

Avoid:

```text
page -> raw fetch/axios
component -> raw fetch/axios
```

## Polling

Document processing status may need polling.

Polling rules:

- Poll only while documents are in non-terminal states.
- Stop polling when all visible documents are `READY` or `FAILED`.
- Stop polling when the page unmounts.
- Keep polling intervals reasonable and configurable.

Terminal statuses:

```text
READY
FAILED
```

## Error Handling

Hooks/composables should expose:

```text
loading
error
data
refresh
mutating state where needed
```

Do not swallow API errors. Convert them into displayable messages while preserving safe error codes for troubleshooting.

## Side Effects

Side effects should be easy to trace:

- Upload hooks manage upload progress and refresh document status.
- API key hooks clear one-time key plaintext when the creation dialog closes.
- Auth hooks manage login/logout and token clearing.
- Avoid hidden navigation side effects from generic hooks.

## Secret Handling

API key creation hooks must treat full keys as short-lived UI state:

- Store only in memory.
- Clear when modal/drawer closes.
- Do not persist to local storage/session storage.
- Do not log to console.
