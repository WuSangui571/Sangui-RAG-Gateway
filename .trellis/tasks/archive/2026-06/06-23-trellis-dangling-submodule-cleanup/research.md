# Focused Code Research

## Relevant Specs

- `.trellis/workflow.md`: task workflow, context injection, no AI auto-commit rule, and current-task mechanism.
- `.trellis/spec/sangui-rag-gateway.md`: project boundary and Trellis workflow rules; complex tasks require goal, affected modules, data/API risks, test approach, and step-by-step plan before coding.
- `.trellis/spec/guides/index.md`: shared guide index; triggers deeper thinking when a task touches repo/deployment-like boundaries.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: defines contract mapping, validation matrix, and Good/Base/Bad case discipline for infrastructure/config changes.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: requires search-first behavior before adding parallel mechanisms; relevant because Trellis already has submodule helpers and package config readers.
- `.trellis/spec/backend/quality-guidelines.md`: backend tests are required only if backend implementation/build paths are touched; otherwise record skipped with reason.
- `.trellis/spec/frontend/quality-guidelines.md`: frontend checks are required only if frontend implementation/build paths are touched; otherwise record skipped with reason.
- `.trellis/config.yaml`: current project package/submodule config source; packages/submodule section is commented out, so `get_context.py --mode packages` reports single-repo mode.

## Code Patterns Found

- Trellis package/submodule config source of truth:
  - `.trellis/scripts/common/config.py`
  - `get_submodule_packages()` returns packages whose config has `type: submodule`.
  - Current `.trellis/config.yaml` has no active `packages:` block, so Trellis should not treat this repo as a submodule monorepo.
- Trellis package reporting:
  - `.trellis/scripts/common/packages_context.py`
  - `get_package_info()` marks `isSubmodule` from config type and `isGitRepo` from explicit config.
  - Current session output confirms single-repo mode with spec layers `backend, frontend, gateway, rag, security`.
- Multi-agent submodule init path:
  - `.trellis/scripts/multi_agent/start.py`
  - `_init_submodules_for_task()` calls `git submodule status <path>` and `git submodule update --init <path>` only for configured submodule packages.
  - Because no package is configured, a root `Trellis` gitlink is not reachable through Trellis package config and is therefore stale/inconsistent.
- Multi-agent submodule PR path:
  - `.trellis/scripts/multi_agent/create_pr.py`
  - `_process_submodule_changes()` uses `get_submodule_packages()` and stages changed submodule paths only for configured submodule packages.
  - An unconfigured root gitlink can confuse git status/clone behavior without being managed by Trellis automation.
- Ignore rules:
  - `.gitignore` ignores `.trellis/.developer` but does not ignore a root `Trellis/` checkout.
  - If the gitlink is removed while local `Trellis/` remains, the executor must ensure nested contents are not accidentally staged.

## Repository Audit Findings

| Audit | Finding |
|---|---|
| Root `.gitmodules` | absent |
| Root git index | contains `160000 7a469c... Trellis` |
| Root `.git/config` submodule entries | none |
| Root `.git/modules` | absent |
| Local `Trellis/` directory | exists and is a nested Git repo |
| `Trellis/.git/config` | origin is `git@github.com:mindfold-ai/Trellis.git` |
| `git -C Trellis ...` | blocked by dubious ownership for the sandbox user |
| `git submodule status` | failed on this Windows Git install because helper commands `basename`, `sed`, and `git-sh-setup` were not found |
| `.trellis/config.yaml` | no active package/submodule configuration |

## Files Likely To Modify

Preferred no-submodule cleanup:

- Root Git index entry for `Trellis`: remove the gitlink from tracked state.
- `.gitignore`: add `Trellis/` only if removing the gitlink leaves a local nested checkout as untracked noise that should remain local-only.
- README or `.trellis/spec/sangui-rag-gateway.md`: update only if current docs need an explicit note that `.trellis/` is checked in and `Trellis/` is not a required submodule.
- `.trellis/tasks/06-23-trellis-dangling-submodule-cleanup/*`: task notes/context/handoff metadata.

Alternative retain-submodule cleanup:

- `.gitmodules`: restore valid `Trellis` submodule path and URL.
- `.trellis/config.yaml`: add active submodule package config if Trellis automation should initialize it.
- README/spec: document `git submodule update --init` and expected clone behavior.

## Risk / Boundary Notes

- The current evidence favors removing the root gitlink rather than restoring submodule config because Sangui-RAG-Gateway already contains its active `.trellis/` workflow files and no active Trellis package config points at `Trellis/`.
- Do not delete or vendor the local nested `Trellis/` checkout without explicit confirmation; removing a gitlink from the index is different from removing local files.
- If the executor uses `git rm --cached Trellis`, immediately inspect `git status --short --untracked-files=all` to ensure the nested checkout is not queued as a massive untracked addition.
- The local `git submodule status` command is not reliable evidence on this machine because the Git helper shell environment is broken. Prefer Git index/tree/config checks unless the environment is repaired.
- Backend/frontend/RAG/security tests are not required for a metadata-only cleanup. If implementation touches their files, run the corresponding targeted checks before handoff back to Codex.

## Required Tests

Minimum required checks after implementation:

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

Optional stronger checks:

```powershell
git submodule status
```

Only count this as passed if the local Git helper issue is fixed. Otherwise record the exact failure and rely on the minimum metadata checks.

Backend/frontend checks are skipped unless implementation touches backend/frontend files.
