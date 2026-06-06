# Frontend Admin Visual Smoke Failure Artifact Controlled Acceptance

## Goal

Validate the remaining runtime evidence gap for the frontend Admin visual smoke CI contract: when the visual smoke boundary fails in GitHub Actions, the frontend job uploads the `visual-smoke-results` artifact and the artifact contains only approved Playwright output paths.

This is a controlled acceptance task. The temporary failure trigger must not be merged into `main`.

## Task Classification

Complex Task.

Reason: the implementation change is intentionally tiny, but the acceptance path spans GitHub Actions behavior, temporary branch workflow, CI job boundaries, artifact download, artifact content safety, and Trellis evidence recording. The correct outcome depends on runtime evidence rather than local code inspection alone.

## Current Project State

- Recent completed work added the frontend Admin visual smoke CI gate.
- Success-path GitHub Actions evidence has been recorded: frontend job command order, Playwright browser install/cache behavior, `npm run test:visual:ci` execution, and no artifact upload on success.
- The remaining documented gap is failure-path artifact evidence: proving a failed/cancelled visual smoke run uploads `visual-smoke-results` and that the uploaded files are limited to approved report/result directories.
- Current workspace starts from `main` with a clean working directory and no active Trellis task before this task is activated.

## Requirements

- Create a temporary branch for controlled CI failure validation.
- Introduce only a test-only visual smoke failure trigger or minimal visual smoke assertion change.
- Trigger GitHub Actions on the temporary branch through push or pull request.
- Confirm the frontend job fails at the visual smoke/test boundary, not at dependency install, typecheck, build, backend, Docker, or unrelated workflow setup.
- Download the `visual-smoke-results` artifact from the failed/cancelled frontend run.
- Inspect the artifact file list and verify it contains only:
  - `frontend/playwright-report/`
  - `frontend/test-results/`
- Verify the artifact does not contain:
  - `.env`
  - `node_modules`
  - `dist`
  - secrets or secret-like files
  - backend data
  - uploaded knowledge files
  - provider/API keys
  - unrelated repository contents
- Remove or abandon the temporary failure branch. Do not merge the failure change.
- Record Trellis evidence with only safe metadata:
  - run ID
  - frontend job ID
  - artifact name
  - artifact file-list summary
  - safety conclusion

## Non-Goals / Forbidden Scope

- Do not change backend, API, database, migrations, RAG retrieval, prompt construction, request-log APIs, Docker images, or product behavior.
- Do not add new Admin UI coverage in this task.
- Do not add a second visual smoke runner.
- Do not change `test:visual:ci` away from direct delegation to `npm run test:visual` unless a concrete CI-only failure trigger requires a temporary branch-only change.
- Do not upload or cache `frontend/dist/`, `frontend/node_modules/`, `.env`, backend data, local uploads, or any secrets.
- Do not merge the intentional failing assertion into `main`.
- Do not record full artifact HTML/body contents in Trellis evidence; record only file names/paths and a safety summary.
- Do not commit or push from Codex in this planning pass.

## API / Command / Payload Contract

No backend API, frontend API, DTO, database, or public payload contract changes are expected.

Command contracts involved:

```bash
cd frontend
npm ci
npx playwright install chromium
npm run typecheck
npm run build
npm run test:visual:ci
```

GitHub Actions artifact contract:

```yaml
name: visual-smoke-results
path:
  - frontend/playwright-report/
  - frontend/test-results/
condition: failure() || cancelled()
```

`frontend/package.json` contract:

```json
"test:visual:ci": "npm run test:visual"
```

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Success-path CI run | No `visual-smoke-results` artifact uploaded | Existing Session 35 evidence; do not re-open unless needed |
| Controlled visual smoke failure | Frontend job fails at `Visual smoke test` step | GitHub Actions job log step status |
| Controlled visual smoke failure | `visual-smoke-results` artifact exists | GitHub Actions artifact list for the run |
| Artifact download | Artifact extracts successfully | Local artifact directory file list |
| Artifact content allowlist | Only Playwright report/result paths are present | Recursive file-list inspection |
| Artifact content denylist | No `.env`, `node_modules`, `dist`, backend data, uploads, or secret-like files | Recursive file-list inspection and filename/path scan |
| Unrelated CI failure | Task evidence is not accepted | Job failure before visual smoke requires fixing the trigger/branch setup or rerunning |
| Artifact missing on failure | Task evidence is not accepted | Workflow artifact policy or upload path must be investigated |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | A temporary branch intentionally fails only the visual smoke boundary; frontend job uploads `visual-smoke-results`; artifact contains only `frontend/playwright-report/` and/or `frontend/test-results/`; Trellis evidence records safe IDs and file-list summary; temporary failure branch is not merged. |
| Base | GitHub credentials are insufficient for Codex to push/download artifacts; DeepSeek or user follows the same plan manually and records run ID, job ID, artifact name, file-list summary, and safety conclusion without exposing artifact content. |
| Bad | Failure is caused by install/typecheck/build/backend/Docker; artifact is absent; artifact contains `.env`, `node_modules`, `dist`, backend data, uploads, secrets, full provider bodies, keys, or broad repository contents; failing trigger is merged to `main`. |

## Expected Implementation Approach

1. Create a temporary branch from current `main`.
2. Add the smallest branch-only test failure trigger. Preferred options:
   - Change one expected visual smoke CSS assertion in `frontend/tests/visual/admin-login-theme-smoke.spec.ts`.
   - Or add an explicit test-only environment branch in the visual smoke test, only if it is removed before merging.
3. Push the temporary branch or open a temporary PR to trigger GitHub Actions.
4. Wait for the workflow run and inspect the frontend job logs.
5. Download `visual-smoke-results`.
6. Generate a file-list summary and denylist scan result.
7. Delete/close the temporary branch/PR without merging the failing change.
8. Add `acceptance-evidence.md` under this Trellis task with safe metadata only.

## Files Likely To Modify

Temporary branch only:

- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`: minimal failing assertion, preferred.
- Or `frontend/scripts/run-visual-smoke.mjs`: only if a CI-only failure trigger is chosen and documented.

Permanent task evidence:

- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/acceptance-evidence.md`
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/task.json`
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/implement.jsonl`
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/check.jsonl`

No permanent business implementation file changes are expected.

## Required Tests and Assertion Points

Before pushing the temporary branch:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Optional local boundary proof for the temporary failure trigger:

```bash
cd frontend
cmd /c npm run test:visual:ci
```

Expected local result for the temporary trigger: fails at Playwright visual smoke assertion only.

CI acceptance:

- GitHub Actions frontend job reaches and fails `Visual smoke test`.
- Artifact named `visual-smoke-results` exists on the failed/cancelled run.
- Artifact recursive file list is limited to approved directories.
- Denylist scan for `.env`, `node_modules`, `dist`, backend data, uploads, and secret-like filenames returns no matches.

After cleanup:

```bash
git status --short
```

Expected final state before handoff back to Codex check/finish-work: only Trellis evidence/context files should remain as intended changes on `main`, unless the user explicitly chooses another workflow.

## Planning Self-Check

- Acceptance criteria are explicit: failure boundary, artifact existence, artifact allowlist/denylist, safe evidence.
- Forbidden scope is explicit: no backend/API/DB/RAG/Admin UI product changes, no permanent failing test, no merging temporary failure branch.
- Expected files are listed and separated into temporary branch-only vs permanent Trellis evidence.
- Required tests and CI assertion points are listed.
- Concrete frontend/security/guides guideline files were read before this PRD was written.
- No unresolved API, DB, frontend type, or DTO alignment issue exists because this task should not change those contracts.
- Open risk: GitHub credentials and artifact download permissions may require user or DeepSeek-side execution.
