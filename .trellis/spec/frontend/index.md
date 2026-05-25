# Frontend Development Guidelines

> Admin console conventions for Sangui-RAG-Gateway. The UI exists to complete configuration workflows clearly; it should not become a general AI platform interface.

## Overview

The frontend is a practical admin console for:

```text
login
apps
knowledge bases
document upload and processing status
model configuration
API key management
request logs
```

The project is currently a blank implementation, so these files define target conventions for future work rather than extracted code patterns.

## Must-Read Before Frontend Work

Always read:

- [Project Specification](../sangui-rag-gateway.md)

Then read the relevant frontend guideline files:

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | App, route, API, component, and feature organization | Active |
| [Component Guidelines](./component-guidelines.md) | Admin component composition and UI behavior | Active |
| [Hook Guidelines](./hook-guidelines.md) | Data fetching and reusable UI logic | Active |
| [State Management](./state-management.md) | Local, server, and global state boundaries | Active |
| [Type Safety](./type-safety.md) | TypeScript contracts and API model rules | Active |
| [Quality Guidelines](./quality-guidelines.md) | UX, accessibility, security, and testing expectations | Active |

## Pre-Development Checklist

- [ ] Confirm the page or workflow being changed.
- [ ] Read [Project Specification](../sangui-rag-gateway.md) to verify the feature belongs in a lightweight RAG gateway.
- [ ] Read [Directory Structure](./directory-structure.md) before adding files.
- [ ] Read [Type Safety](./type-safety.md) before defining API payloads or response models.
- [ ] Read [State Management](./state-management.md) before adding global state.
- [ ] Read [Quality Guidelines](./quality-guidelines.md) before completing the task.
- [ ] For API contract changes, read [Cross-Layer Thinking Guide](../guides/cross-layer-thinking-guide.md).

## Frontend Stack Target

Recommended stack:

```text
Vue 3 or React
TypeScript
Vite
Element Plus, Ant Design Vue, Arco Design, or another practical admin UI library
```

Once the framework is chosen, keep the whole admin console consistent. Do not mix Vue and React.

## Product Direction

The admin UI should be clear, workflow-driven, and operational:

- Prefer tables, forms, status tags, drawers, and detail pages over marketing-style pages.
- Show document processing status clearly.
- Make API key one-time display behavior obvious.
- Mask secrets everywhere after creation.
- Explain compatibility limitations in docs/help text, not through decorative UI.
