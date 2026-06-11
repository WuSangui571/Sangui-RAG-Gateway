# V0.2 RC Tag Creation and Release Verification

## Goal

Create and verify the official annotated release-candidate tag `v0.2.0-rc.1` for the already validated V0.2 release state, then record safe release-tag evidence in Trellis metadata.

This task is release engineering only. It must not change backend, frontend, API, database, Docker, CI, smoke-script, or runtime behavior.

## Scope Classification

Simple Task with release-boundary risk.

The operational flow is clear and limited to git/tag/Trellis metadata, but the tag target must be explicit because the previous runbook originally targeted `ea55a1c5`, while later metadata commits exist:

- `ea55a1c5`: original runbook target; fresh demo key cleanup session record.
- `5aec32fe`: V0.2 RC reproducible smoke/tag runbook commit.
- `1efaa8a9`: Trellis metadata archive commit after the runbook.

## Current Project State Summary

- Branch at planning time: `main`.
- Working directory at `$start`: clean.
- Current task before creation: none.
- Active tasks before creation: none.
- Latest commits at planning time:
  - `1efaa8a9 chore:归档v0.2 rc runbook会话`
  - `5aec32fe docs:补充v0.2 rc复现smoke与tag runbook`
  - `ea55a1c5 chore:记录v0.2 fresh demo key清理会话`
  - `3be0282e docs:确认v0.2 fresh demo key清理`
  - `8a10655c docs:完善v0.2发布就绪收尾记录`
- Existing `v0.2.0-rc.*` tags at planning time: none.
- Previous journal says V0.2 RC runbook/smoke/key cleanup are completed and no release blockers remain.

## Requirements

- Confirm Trellis metadata archive commit is complete and the working directory is clean before tag creation.
- Decide and record the tag target before creating the tag:
  - Preferred target for this task: `5aec32fe`, because it includes the reproducible RC smoke/tag runbook as committed release metadata while excluding later pure journal archive metadata unless explicitly chosen.
  - Original runbook target: `ea55a1c5`, valid only if the operator wants the tag to exclude the runbook commit itself.
  - Do not silently tag `HEAD` unless the selected target is explicitly recorded.
- Confirm `git tag --list "v0.2.0-rc.*"` has no conflict before creating `v0.2.0-rc.1`.
- Create annotated tag `v0.2.0-rc.1` only after all preconditions pass.
- Verify the tag with `git show --stat --oneline v0.2.0-rc.1`.
- Push the tag only after local verification passes.
- Record safe release tag evidence in this Trellis task and/or workspace journal.
- Keep committed evidence safe: no plaintext app API keys, revoked keys, upstream keys, Authorization header values, provider raw bodies, full prompts/messages, full answer text, chunk content, embedding vectors, stack traces, `.env`, uploaded files, `dist`, `target`, or `node_modules`.

## Explicit Non-Goals / Forbidden Changes

- Do not modify backend implementation files under `backend/src/**`.
- Do not modify frontend implementation files under `frontend/src/**`.
- Do not modify database migrations, entities, DTO/VO contracts, OpenAI-compatible API shapes, admin API payloads, Docker Compose service contracts, CI behavior, or smoke-script logic.
- Do not edit `deploy/docker-compose.yml`, `.github/workflows/**`, `scripts/demo-smoke.ps1`, or `frontend/nginx.conf`.
- Do not rerun smoke with plaintext keys unless the operator provides runtime-only secrets outside tracked files.
- Do not commit or record secrets, raw runtime responses, raw answers, raw SSE, provider bodies, prompts/messages, or chunk content.
- Do not overwrite an existing tag. If `v0.2.0-rc.1` exists, stop and ask whether to use the next RC tag.

## Command Contract

### Preflight

```powershell
git status --short
git log --oneline -8
git rev-parse 5aec32fe
git rev-parse ea55a1c5
git tag --list "v0.2.0-rc.*"
git show --stat --oneline --decorate 5aec32fe
git show --stat --oneline --decorate ea55a1c5
```

### Secret / Forbidden-Field Scan

```powershell
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
```

Expected: hits may appear only as placeholders, rule text, scanner arrays, task criteria, or historical metadata. Real keys or concrete Authorization headers block release.

### Tag Creation

Use the selected target explicitly:

```powershell
git tag -a v0.2.0-rc.1 <selected-target-commit> -m "v0.2.0-rc.1"
```

Preferred target if no contrary operator decision is made:

```powershell
git tag -a v0.2.0-rc.1 5aec32fe -m "v0.2.0-rc.1"
```

### Tag Verification

```powershell
git show --stat --oneline v0.2.0-rc.1
git rev-list -n 1 v0.2.0-rc.1
git status --short
```

### Push

```powershell
git push origin v0.2.0-rc.1
```

Push must not be attempted if local tag verification fails.

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Working tree clean before tag | Proceed | `git status --short` has no output except intentional current Trellis planning files before execution; final executor must resolve/commit/avoid dirty metadata before tagging |
| Working tree dirty with uncommitted metadata | Do not tag until decision is recorded | `git status --short` lists files |
| Target `5aec32fe` exists | Preferred target available | `git rev-parse 5aec32fe` succeeds |
| Target `ea55a1c5` exists | Original runbook target available | `git rev-parse ea55a1c5` succeeds |
| `v0.2.0-rc.1` absent | Tag name available | `git tag --list "v0.2.0-rc.*"` has no output |
| `v0.2.0-rc.1` already exists | Stop; do not overwrite | tag list shows existing tag |
| Forbidden-field scan finds real key/header/body | Stop release | scan hit analysis fails |
| Tag created | Annotated tag points to selected commit | `git show --stat --oneline v0.2.0-rc.1` and `git rev-list -n 1 v0.2.0-rc.1` |
| Tag points to wrong commit | Delete only if not pushed; otherwise ask operator | verification hash mismatch |
| Push succeeds | Remote tag published | `git push origin v0.2.0-rc.1` exits 0 |
| Push fails due remote conflict/network/auth | Do not claim release published | record failure boundary and leave local tag evidence |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | Clean tree, no existing RC tag, selected target explicitly recorded, forbidden scan reviewed clean, annotated `v0.2.0-rc.1` created on the selected target, `git show` verifies stats, tag pushed, and Trellis evidence records target hash, tag object summary, push result, and safe validation output. |
| Base | If push credentials/network are unavailable, local annotated tag is verified and evidence records push as pending/failed without claiming remote release completion. |
| Bad | Tag is created from an implicit or dirty `HEAD`, tag target decision is not recorded, existing tag is overwritten, release evidence includes secrets/raw runtime content, or implementation files are changed during release tagging. |

## Required Tests and Assertion Points

Because this task is release metadata/tagging only, backend/frontend tests are not required unless implementation files unexpectedly change.

Required static/release checks:

```powershell
git status --short
git log --oneline -8
git rev-parse 5aec32fe
git rev-parse ea55a1c5
git tag --list "v0.2.0-rc.*"
git diff --check
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
git show --stat --oneline v0.2.0-rc.1
git rev-list -n 1 v0.2.0-rc.1
```

Required push check:

```powershell
git push origin v0.2.0-rc.1
```

If any backend/frontend/infra/smoke implementation file changes unexpectedly, stop and reroute to normal validation:

```powershell
cd backend
mvn -q -DskipTests compile
mvn test
cd ..\frontend
cmd /c npm run typecheck
cmd /c npm run build
```

## Expected Files Likely To Modify

- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/prd.md`
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/research.md`
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/implement.jsonl`
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/check.jsonl`
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/debug.jsonl`
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/task.json`
- `.trellis/workspace/sangui/journal-2.md` only after release evidence is recorded.

No business implementation files are expected to change.

## Open Questions / Required Operator Confirmation

The only material decision is tag target:

- Recommended for this task: tag `5aec32fe` because it includes the committed RC runbook.
- Alternative: tag `ea55a1c5` to follow the original runbook exactly and exclude the runbook commit.

Do not create or push the tag until this choice is explicitly recorded by the executor/operator.
