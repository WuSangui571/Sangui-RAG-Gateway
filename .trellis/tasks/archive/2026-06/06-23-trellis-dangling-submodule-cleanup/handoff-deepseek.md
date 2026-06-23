# DeepSeek Execution Handoff

## PRD Path

`.trellis/tasks/06-23-trellis-dangling-submodule-cleanup/prd.md`

## Current Task Path

`.trellis/tasks/06-23-trellis-dangling-submodule-cleanup`

The task is active via:

```powershell
python .\.trellis\scripts\task.py start .trellis\tasks\06-23-trellis-dangling-submodule-cleanup
```

## Must Read Before Editing

- `.trellis/tasks/06-23-trellis-dangling-submodule-cleanup/prd.md`
- `.trellis/tasks/06-23-trellis-dangling-submodule-cleanup/research.md`
- `.trellis/tasks/06-23-trellis-dangling-submodule-cleanup/implement.jsonl`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
- `.trellis/config.yaml`
- `.trellis/scripts/common/config.py`
- `.trellis/scripts/common/packages_context.py`
- `.trellis/scripts/multi_agent/start.py`
- `.trellis/scripts/multi_agent/create_pr.py`

## Expected Direction

Preferred route: remove the root repository's unconfigured `Trellis` gitlink, because current evidence shows:

- Root `.gitmodules` is absent.
- Root git config has no `submodule.*` entries.
- Root `.git/modules` is absent.
- `.trellis/config.yaml` has no active package/submodule config.
- `get_context.py --mode packages` reports single-repo mode.
- The active workflow source is the checked-in `.trellis/` directory, not root `Trellis/`.

Alternative route: only if the user explicitly says `Trellis` must remain a submodule, restore the full submodule contract instead of removing the gitlink.

## Expected Modified Files

Preferred no-submodule route:

- Root Git index entry for `Trellis`: remove the gitlink.
- `.gitignore`: only if needed to keep the local nested `Trellis/` checkout out of accidental staging after gitlink removal.
- README or `.trellis/spec/sangui-rag-gateway.md`: only if documentation must clarify that `Trellis/` is not a required submodule.
- `.trellis/tasks/06-23-trellis-dangling-submodule-cleanup/*`: implementation notes/evidence/context only.

Do not modify backend/frontend implementation files unless the user explicitly expands scope.

## Strictly Forbidden Scope

- No backend Java implementation changes.
- No frontend TypeScript/React implementation changes.
- No database migrations.
- No `/v1/*` gateway behavior changes.
- No admin API, DTO, VO, frontend type, or payload changes.
- No RAG retrieval/prompt/ingestion/storage/request-log/JWT/AES/rate-limit/production-guard fixes.
- No vendoring of nested `Trellis/` repo contents into Sangui-RAG-Gateway.
- No deleting the local nested `Trellis/` working tree unless the user confirms it is disposable.
- No auto-commit, push, PR creation, or record-session.

## Required Validation Commands

Run after implementation:

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

Optional:

```powershell
git submodule status
```

Do not treat optional `git submodule status` as a failure of the cleanup if it still fails with the known local Git helper issue:

```text
basename: command not found
sed: command not found
git-sh-setup: file not found
```

Record it as an environment/tooling limitation and use the metadata checks instead.

## Planning Self-Check

- Acceptance criteria are defined in `prd.md`.
- Forbidden scope is explicit in both PRD and this handoff.
- Expected modified files are listed.
- Required validation commands are listed.
- Specific guideline files were read, not only spec indexes.
- No API/DB/frontend DTO/type fields are changed by the planned route.
- No blocking requirement question remains; current evidence supports the preferred no-submodule cleanup branch.
