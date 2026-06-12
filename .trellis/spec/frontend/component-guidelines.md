# Component Guidelines

> Components should make admin workflows clear, compact, and safe. The UI should help users configure the gateway, not sell the product.

## Component Principles

- Prefer predictable admin UI patterns: tables, forms, detail sections, status tags, drawers, modals, and tabs.
- Use the chosen UI library's components before custom-building.
- Keep components focused on one job.
- Use domain names when the component encodes business meaning.
- Make loading, empty, error, disabled, and permission states explicit.

## Expected Domain Components

Useful reusable components include:

```text
AppStatusTag
KnowledgeBaseStatusTag
DocumentStatusTag
ApiKeyPrefix
ApiKeyOneTimeSecret
ModelProviderForm
RetrievalConfigForm
ChunkStrategyForm
RequestLogStatusTag
SourceCitationList
```

Document status should be visually clear:

```text
uploaded
parsing
embedding
ready
failed
```

## Props and Events

Props should be typed and explicit.

Prefer:

```text
app
knowledgeBase
document
loading
disabled
onRefresh
onSubmit
onRevoke
```

Avoid:

```text
data
item
info
callback
config
```

unless the component is truly generic.

Components that mutate server state should expose callbacks or use feature-level hooks/composables; they should not hide important API side effects.

## Forms

Form components must:

- Validate required fields before submit.
- Show server validation errors near the affected field when possible.
- Preserve entered values on recoverable errors.
- Mask secret fields by default.
- Never display full upstream API keys returned from the backend; the backend should not return them.

API key creation should use a one-time display component. After the user leaves the success state, the full key must not be recoverable from frontend state.

## Tables

Tables should support:

- Loading state.
- Empty state.
- Error state or retry action.
- Pagination when the backend API supports it.
- Clear status tags.
- Action buttons grouped consistently.

Request log tables should show safe summaries only, not full prompts.

### Implemented API Key Lifecycle Actions

The API key table in `frontend/src/pages/api-keys/ApiKeyPage.tsx` mirrors the backend lifecycle contract for display only. Backend services remain the source of truth for allowed transitions.

| Status | Visible row actions | Modal semantics |
|---|---|---|
| `ACTIVE` | Disable, Revoke | Disable is reversible warning-level copy; Revoke is danger/terminal copy. |
| `DISABLED` | Enable, Revoke | Enable is non-danger copy and states the key can authenticate again only if the app is enabled and the key is not expired. |
| `EXPIRED` | Revoke | Do not show Enable; expired keys must not be silently revived. |
| `REVOKED` | none | Revoked keys are terminal. |

After a successful lifecycle mutation, refresh the API key list. Normal lifecycle responses must use `ApiKeyVO` and must not be typed or rendered as if they include the one-time plaintext `key`.

## Layout

Use an app shell with navigation for the admin console. Keep pages task-oriented:

```text
list -> detail -> configure/upload/manage
```

Avoid:

- Marketing landing pages as the first screen.
- Oversized hero copy.
- Decorative nested cards.
- UI text that explains obvious controls instead of presenting the workflow.

## Styling

- Keep cards to real repeated items, modals, or framed tools.
- Do not put UI cards inside other cards.
- Use restrained color and status semantics from the UI library.
- Ensure table, form, and button text fits on both desktop and mobile widths.
- Use icons for common actions when available in the chosen icon library.

## Accessibility

- All interactive controls need accessible labels.
- Icon-only buttons need tooltips.
- Error messages must be visible, not only color-coded.
- Do not rely on color alone for document or app status.
