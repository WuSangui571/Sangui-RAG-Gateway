# Frontend Directory Structure

> Target organization for the admin console. Keep the UI grouped by workflow and avoid spreading one feature across unrelated directories.

## Recommended Structure

If using Vue:

```text
src
  ├── app
  │   ├── router
  │   ├── stores
  │   └── providers
  ├── api
  │   ├── http.ts
  │   ├── auth.ts
  │   ├── apps.ts
  │   ├── knowledge.ts
  │   ├── documents.ts
  │   ├── model-configs.ts
  │   ├── api-keys.ts
  │   └── request-logs.ts
  ├── pages
  │   ├── LoginPage.vue
  │   ├── apps
  │   ├── knowledge
  │   ├── model-configs
  │   ├── api-keys
  │   └── request-logs
  ├── components
  │   ├── common
  │   ├── layout
  │   └── domain
  ├── composables
  ├── types
  ├── utils
  └── styles
```

If using React:

```text
src
  ├── app
  │   ├── router
  │   ├── stores
  │   └── providers
  ├── api
  ├── pages
  ├── components
  │   ├── common
  │   ├── layout
  │   └── domain
  ├── hooks
  ├── types
  ├── utils
  └── styles
```

Pick one framework and keep the equivalent structure consistent.

## MVP Pages

The admin console MVP should include:

```text
Login page
App list
App detail
Knowledge base list
Knowledge base detail
Document upload page
Model configuration page
API key management page
Request log page
```

## Feature Boundaries

Use page folders for route-level composition:

```text
pages/apps
pages/knowledge
pages/model-configs
pages/api-keys
pages/request-logs
```

Use domain components for reusable feature UI:

```text
components/domain/AppStatusTag
components/domain/DocumentStatusTag
components/domain/ApiKeyOneTimeSecret
components/domain/ModelProviderForm
components/domain/RetrievalConfigForm
```

Use common components only when they are generic and reused across multiple domains.

## API Client Organization

The `api/` directory owns HTTP calls and request/response mapping.

Do not call `fetch`/`axios` directly from pages or components. Use typed API functions such as:

```text
createApp
listApps
uploadDocument
createApiKey
listRequestLogs
updateModelConfig
```

## Naming

Use clear domain names:

```text
AppListPage
AppDetailPage
KnowledgeBaseDetailPage
DocumentUploadPanel
ApiKeyTable
RequestLogTable
ModelConfigForm
```

Avoid generic names like `DataPage`, `InfoCard`, or `ConfigPanel` when a domain-specific name is clearer.

## Import Rules

- Pages may import API clients, domain components, common components, hooks/composables, and types.
- Domain components should receive data and callbacks through props rather than importing page-specific stores.
- API clients may import shared HTTP utilities and types only.
- Global stores should not import page components.
- Avoid circular feature imports.

## Styles

Use the selected UI library's design tokens and components first. Add custom CSS only where needed for layout, density, or clear status presentation.

The admin console should feel quiet, dense, and operational. Avoid landing-page layouts, oversized hero sections, decorative card-heavy screens, and unnecessary animation.
