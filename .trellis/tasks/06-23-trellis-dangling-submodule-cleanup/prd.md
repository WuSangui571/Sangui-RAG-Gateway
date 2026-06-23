# Critical Trellis Dangling Submodule Cleanup

## Collaboration Boundary

This task uses Codex / DeepSeek split execution.

- Codex scope for the current round: scope judgment, PRD, implementation plan, Trellis task/context setup, spec reading, focused code research, and test plan.
- DeepSeek scope for the next round: perform the actual Git/Trellis metadata cleanup and any narrowly required documentation/spec updates.
- Codex must not modify backend, frontend, gateway, RAG, security, database, deployment, or other business implementation files in the planning round.

## Current Project State

- Branch at task start: `feature/trellis-dangling-submodule-cleanup`.
- Working tree before task creation: clean.
- Active Trellis task before task creation: none.
- Recent journal state: the previous recorded task closed the dev secret HS256 local contract and explicitly did not implement JWT/AES key split, DB/API/frontend/RAG changes, or request-log behavior changes.

## Task Classification

Complex Task.

Reason: the implementation surface is mostly Git metadata and Trellis infrastructure, but the decision affects clone behavior, submodule commands, Trellis multi-agent helpers, task context setup, and future CI/status interpretation. It requires a deliberate plan before execution even though no business logic should change.

## Goal

Resolve the repository's dangling `Trellis` gitlink/submodule inconsistency so future Trellis task creation, context injection, clone/CI setup, and git status checks operate from one clear source of truth.

The target invariant is:

```text
The root Sangui-RAG-Gateway repository must not contain an unconfigured 160000 gitlink.
If a submodule is intentionally kept, it must have a matching .gitmodules entry,
local git config entry, clone/init instructions, and Trellis package config.
If it is not intentionally kept, the root gitlink and stale submodule metadata must be removed,
and the project must rely on the checked-in .trellis workflow files instead.
```

## Initial Evidence

Read-only audit from 2026-06-23 found:

| Check | Result | Interpretation |
|---|---|---|
| `git status --short --untracked-files=all` | no output | root working tree was clean before task creation |
| `Test-Path .gitmodules` | false | root repository has no `.gitmodules` |
| `git ls-files -s | Select-String "^160000"` | `160000 ... Trellis` | root index still tracks `Trellis` as a gitlink |
| `git ls-tree HEAD Trellis` | `160000 commit 7a469... Trellis` | HEAD contains gitlink metadata |
| `git config --get-regexp "^submodule\."` | no output | root local config has no submodule entry |
| `.git/modules` | absent | no root `.git/modules/Trellis` backing dir |
| `Test-Path Trellis` | true | local nested `Trellis/` checkout exists |
| `Trellis/.git/config` | origin is `git@github.com:mindfold-ai/Trellis.git` | nested repo appears to be the Trellis source checkout |
| `git -C Trellis ...` | fails with dubious ownership | nested checkout is not safe for current sandbox user and may break automation |
| `.trellis/config.yaml` | packages/submodule entries are commented examples only | current project is configured as single-repo, not a Trellis submodule package |
| `git submodule status` | failed because Git shell helper could not find `basename`, `sed`, and `git-sh-setup` | local Git submodule command is currently not reliable evidence on this machine |

## Requirements

- Audit root Git metadata for submodule/gitlink state:
  - `.gitmodules`
  - `git submodule status` or documented fallback if local Git submodule helper is broken
  - `git ls-files -s` entries with mode `160000`
  - `git ls-tree HEAD Trellis`
  - root `.git/config` submodule entries
  - `.git/modules`
  - Trellis references in `.trellis/config.yaml`, `.trellis/scripts`, README, AGENTS, and task/workspace docs
- Decide explicitly between two branches:
  - Preferred default if no real submodule dependency exists: remove the root `Trellis` gitlink and stale submodule expectations.
  - Alternative only if a submodule is intentionally required: restore `.gitmodules`, URL/path, `.trellis/config.yaml` package typing, and clone/init documentation.
- Keep the chosen implementation scoped to repository/Trellis infrastructure.
- Preserve project-local `.trellis/` as the active workflow source of truth.
- Do not migrate or copy the nested `Trellis/` repository contents into Sangui-RAG-Gateway.
- Do not alter backend/frontend/gateway/RAG/security behavior.
- Do not fold in follow-up business tasks such as JWT/AES key split, retrieval threshold single-source cleanup, upload rollback/orphan-file handling, or any request-log/RAG fixes.

## Command / Metadata Contract

This task changes Git/Trellis repository metadata, not HTTP APIs.

### Commands to Audit

```powershell
git status --short --untracked-files=all
if (Test-Path .gitmodules) { Get-Content -Raw .gitmodules } else { "NO_GITMODULES" }
git ls-files -s | Select-String "^160000"
git ls-tree HEAD Trellis
git config --get-regexp "^submodule\."
if (Test-Path .git\modules) { Get-ChildItem -Force .git\modules } else { "NO_GIT_MODULES_DIR" }
git submodule status
rg -n "submodule|gitmodules|gitlink|160000|Trellis" .trellis README.md AGENTS.md .github deploy backend frontend scripts
```

### Commands to Validate

```powershell
git status --short --untracked-files=all
if (Test-Path .gitmodules) { Get-Content -Raw .gitmodules } else { "NO_GITMODULES" }
git ls-files -s | Select-String "^160000"
git config --get-regexp "^submodule\."
git diff --check
python .\.trellis\scripts\get_context.py
python .\.trellis\scripts\get_context.py --mode packages
python .\.trellis\scripts\task.py validate .trellis\tasks\06-23-trellis-dangling-submodule-cleanup
```

### Payload / File Contract

No API payloads or DTOs are changed. Expected metadata/file payloads are:

| Artifact | Expected contract |
|---|---|
| root git index | no unconfigured `160000 Trellis` gitlink after cleanup unless `.gitmodules` and config are restored intentionally |
| `.gitmodules` | either absent because no submodules exist, or present with a valid `Trellis` entry and documented URL/path |
| `.trellis/config.yaml` | no active submodule package config unless the repo intentionally keeps a submodule package |
| README / Trellis docs | describe the chosen repo layout only if existing docs imply a different layout |
| task context jsonl | entries point to existing files only; no stale `.claude/commands/trellis/*.md` references |

## Validation / Error Matrix

| Scenario | Expected result | Required assertion |
|---|---|---|
| No `.gitmodules`, but `git ls-files -s` reports `160000 Trellis` | fail before cleanup; this is the current bug | audit records the mismatch |
| No submodules are intended | cleanup removes the root gitlink and stale submodule metadata | `git ls-files -s | Select-String "^160000"` returns no `Trellis` entry |
| A `Trellis` submodule is intentionally retained | `.gitmodules`, URL/path, git config, docs, and Trellis package config are restored consistently | `git submodule status Trellis` or equivalent succeeds after environment limitation is addressed |
| Local `git submodule status` fails due Git shell helper issue | record as environment/tooling limitation, then use `git ls-files -s`, `.gitmodules`, config, and tree checks as the minimum equivalent evidence | failure text is documented; task does not rely on a false pass |
| Nested `Trellis/` checkout remains locally after gitlink removal | root repo must not track its contents accidentally | `git status --short --untracked-files=all` must be reviewed; executor must decide whether `.gitignore` or local removal is needed, without committing nested contents |
| Trellis context initialization injects missing files | fix task context to existing repo-local files before handoff/implementation completion | `task.py validate` passes |
| Business files are modified | out of scope unless directly required for README/spec documentation | final diff review flags and reverts/isolates unrelated business changes |

## Good / Base / Bad Cases

| Case | Expected outcome |
|---|---|
| Good | Root repo has no dangling `160000 Trellis` gitlink, no inconsistent `.gitmodules`/config state, Trellis package discovery still reports single-repo mode, task context validates, and documentation/specs mention only the chosen current layout. |
| Base | If the local `git submodule` command is broken on this Windows Git install, the executor documents the exact failure and proves the invariant with `git ls-files -s`, `.gitmodules`, root git config, `.git/modules`, and `git diff --check`. |
| Bad | The task removes or rewrites `.trellis/` workflow content, vendors the nested `Trellis/` repo into Sangui-RAG-Gateway, silently ignores a remaining gitlink, adds a fake fallback around Trellis scripts, changes backend/frontend behavior, or mixes in unrelated critical backlog items. |

## Likely Implementation Plan

1. Re-run the audit commands before editing to confirm the state has not changed.
2. Inspect whether any active repo config intentionally references root path `Trellis`.
3. Choose the branch:
   - If no intended dependency exists, remove the root gitlink with `git rm --cached Trellis` or an equivalent safe Git metadata operation, then ensure local nested contents are not staged or accidentally committed.
   - If an intended dependency exists, restore `.gitmodules` and Trellis package config instead, then document clone/init behavior.
4. Update only the minimum docs/specs needed to make the selected layout clear.
5. Validate Git metadata, Trellis context, and diff hygiene.
6. Leave final business-code checks skipped with reason if no backend/frontend files changed.

## Files Likely to Modify

Preferred no-submodule cleanup branch:

- root Git index entry for `Trellis`: remove gitlink.
- `.gitmodules`: likely stays absent; add only if keeping submodule.
- `.gitignore`: only if needed to prevent a local nested `Trellis/` checkout from appearing as accidental untracked content after gitlink removal.
- `.trellis/config.yaml`: only if active config must explicitly document no submodule packages; avoid changing commented examples unless they cause confusion.
- `README.md` or `.trellis/spec/sangui-rag-gateway.md`: only if current documentation implies `Trellis` is a required submodule.
- `.trellis/tasks/06-23-trellis-dangling-submodule-cleanup/*`: task metadata, context, research, and handoff notes.

Alternative retain-submodule branch:

- `.gitmodules`: add valid `Trellis` path and URL.
- `.trellis/config.yaml`: add active package entry if Trellis helpers should initialize it.
- README / spec: add clone and `git submodule update --init` instructions.

## Forbidden Scope

- No backend Java implementation changes.
- No frontend TypeScript/React implementation changes.
- No database migrations.
- No gateway `/v1/*` API changes.
- No admin API, DTO, VO, frontend type, or payload changes.
- No RAG retrieval, prompt, ingestion, storage, request-log, JWT/AES, rate-limit, or production guard behavior changes.
- No deleting the local nested `Trellis/` working tree unless the executor has explicit confirmation that it is disposable; prefer removing only the root gitlink from the index.
- No auto-commit, push, PR creation, or session recording in the implementation handoff unless the user explicitly requests it later.

## Required Tests and Assertion Points

Because this task is repository/Trellis infrastructure only, backend/frontend test suites are not required unless implementation unexpectedly touches those files.

Required local checks:

```powershell
git status --short --untracked-files=all
git ls-files -s | Select-String "^160000"
if (Test-Path .gitmodules) { Get-Content -Raw .gitmodules } else { "NO_GITMODULES" }
git config --get-regexp "^submodule\."
git diff --check
python .\.trellis\scripts\get_context.py
python .\.trellis\scripts\get_context.py --mode packages
python .\.trellis\scripts\task.py validate .trellis\tasks\06-23-trellis-dangling-submodule-cleanup
```

Optional stronger checks if the environment supports them:

```powershell
git submodule status
git clone --no-checkout <repo-url> <temp-dir>
```

If a fresh clone simulation is not feasible locally, record the reason and use the metadata-equivalent checks above.

## Acceptance Criteria

- [ ] Root repository no longer has an unconfigured dangling `Trellis` gitlink.
- [ ] The chosen layout is explicit: either no submodule, or a fully configured submodule.
- [ ] `.trellis/` remains the workflow source of truth and is not replaced by nested `Trellis/` contents.
- [ ] Trellis package discovery still matches the intended project mode.
- [ ] Task context validates and contains only existing repo-local context files.
- [ ] Final diff is limited to Git/Trellis metadata and narrowly necessary documentation/spec updates.
- [ ] Business-code files remain untouched unless the user explicitly expands scope.
- [ ] Required checks and their pass/fail/skip reasons are recorded.

## Open Questions

None blocking for planning. Current evidence supports the preferred no-submodule cleanup branch unless the user explicitly says the root `Trellis` submodule must be retained.
