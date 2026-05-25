# Frontend Quality Guidelines

> The admin console should be reliable, clear, and safe for configuration workflows. Visual polish matters, but operational clarity and secret safety matter more.

## Testing Expectations

When frontend implementation starts, cover:

```text
login flow
app create/edit flow
knowledge base create flow
document upload and status display
model config form validation
API key one-time display and clearing
API key revoke/disable flow
request log list filters
```

Component-level tests should focus on:

```text
status tags
forms
API key one-time display
document upload panel
request log table
```

End-to-end tests should cover the MVP admin path:

```text
login -> create knowledge base -> upload document -> create app -> create API key
```

## UX Requirements

- Users must always know whether a document is uploaded, parsing, embedding, ready, or failed.
- API key creation must clearly show that the full key is visible only once.
- Disabled/revoked/expired API keys must be visually distinct.
- Model configuration errors must be actionable.
- Retrieval settings should show defaults and bounds.
- Request logs should be filterable enough to debug app usage without exposing sensitive data.

## Security Requirements

- Do not log secrets to console.
- Do not persist full generated API keys.
- Do not show upstream API key plaintext after save.
- Do not render full private document content in logs unless the feature explicitly requires a document preview and the backend authorizes it.
- Mask or omit sensitive fields by default.

## Accessibility

- Form fields need labels.
- Icon-only controls need tooltips/accessible labels.
- Error messages must be visible and associated with relevant fields.
- Status cannot rely on color alone.
- Keyboard navigation should work for dialogs and forms.

## Performance

- Paginate large lists.
- Avoid loading full request logs or full document contents into list pages.
- Poll document status only while needed.
- Avoid repeated API calls caused by unstable dependencies in hooks/composables.
- Keep admin pages responsive during uploads and long-running processing.

## Visual Design

- Build the actual admin experience as the first screen after login.
- Avoid landing-page or marketing-page composition.
- Use dense but organized tables and forms.
- Use cards only for repeated items, modals, and genuinely framed tools.
- Do not nest cards inside cards.
- Keep text within containers on desktop and mobile.

## Completion Checklist

- [ ] Loading, empty, error, and success states are handled.
- [ ] Forms validate before submit and show backend errors.
- [ ] Secret fields are masked or cleared correctly.
- [ ] Status values have explicit UI mappings and fallbacks.
- [ ] API calls use typed client functions.
- [ ] Page behavior matches the lightweight RAG gateway product boundary.

## Forbidden Patterns

- Storing generated full API keys in local storage/session storage.
- Using the admin console as a chat playground-first product instead of a configuration console.
- Building workflow/agent/plugin-marketplace UI for MVP.
- Copying provider-specific assumptions into generic model config UI.
- Rendering complete augmented prompts in request log pages by default.
