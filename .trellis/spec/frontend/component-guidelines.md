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

## API Key Table Detect Action

The API key management page includes a row-level detect action for each key:

- Detect button calls `POST /api/admin/api-keys/{id}/detect` with admin user ID header.
- `DISABLED` rows show the restore action, which calls `POST /api/admin/api-keys/{id}/enable`.
- Row-level loading state is shown only on the detect button for the row being detected (no global loading spinner).
- Detection result (`usable`/`unusable`) is shown as an inline Tag per row and is not persisted globally or in local storage.
- `REVOKED` rows display a terminal "Revoked" tag directly; no detect button is rendered.
- Detection result is cleared on table refresh (`fetchKeys`) and when the active app selection changes.
- No plaintext key, key hash, or secret fields are stored in detection UI state.
- TypeScript types (`ApiKeyDetectionVO`) match backend snake_case fields: `key_id`, `app_id`, `usable`, `status`, `app_enabled`, `expires_at`, `checked_at`.
- Actions column is widened to accommodate the extra detect button and inline result tag.

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
