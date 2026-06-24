# Backend data tracking cleanup

## Task Classification

Simple Task.

Reason: the goal is clear and the expected code surface is small, but it touches storage/runtime-data repository hygiene, so the handoff must call out command boundaries, validation, and data-loss risks explicitly.

## Goal

Stop tracking local runtime upload data under `backend/data/**` while preserving existing local files on disk. The repository should ignore future local upload/storage output so generated knowledge files do not leak into Git history or distort Docker/CI/review checks.

## Background

`git ls-files backend/data backend/data/**` currently lists tracked upload files, including paths like `backend/data/uploads/knowledge/.../manual-test.md`. These files are runtime artifacts, not source files.

## In Scope

- Confirm the full tracked-file list under `backend/data` before changing the index.
- Update `.gitignore` so local backend runtime data under `backend/data/**` is ignored going forward.
- Remove already tracked `backend/data/**` paths from the Git index only, preserving local working-tree files.
- Check whether tests, README, Docker configuration, or local storage defaults rely on committed files under `backend/data/**`.
- Validate that `git ls-files backend/data backend/data/**` returns no tracked files after implementation.

## Out of Scope

- Do not delete local files under `backend/data/**`.
- Do not change local storage service behavior, upload APIs, document parsing, database schema, migrations, Docker volumes, or runtime default paths unless research proves a direct dependency and the user approves.
- Do not add fallback storage behavior or mock success paths.
- Do not rewrite unrelated `.gitignore` sections.
- Do not change frontend, DTOs, OpenAI-compatible APIs, retrieval behavior, prompt behavior, or embedding behavior.

## Expected Implementation

Likely changes:

- `.gitignore`: add/adjust ignore rule for `backend/data/` or `backend/data/**`.
- Git index only: untrack currently committed `backend/data/**` files, typically with `git rm --cached -r -- backend/data`.

The exact untrack command may be adjusted after inspecting the tracked file list. The implementation must avoid commands that remove the working-tree files.

## Command / Payload / Contract

This task does not introduce or change public API, Admin API, DTO, request payload, response payload, database column, migration, environment variable, or application command signature.

Operational command contract:

- Inspect tracked data:
  - `git ls-files backend/data backend/data/**`
- Untrack data while preserving local files:
  - `git rm --cached -r -- backend/data`
- Verify index cleanup:
  - `git ls-files backend/data backend/data/**`
- Review status:
  - `git status --short`

Expected post-change state:

- `git ls-files backend/data backend/data/**` prints nothing.
- `git status --short` shows only expected `.gitignore` changes and staged/unstaged deletions for previously tracked `backend/data/**` paths.
- Local files under `backend/data/**` still exist in the working tree if they existed before the task.

## Validation / Error Matrix

| Case | Input / Condition | Expected Result | Assertion Point |
| --- | --- | --- | --- |
| Good | Tracked files exist under `backend/data/**` | Files are removed from Git index only and ignored going forward | `git ls-files backend/data backend/data/**` is empty; local files remain on disk |
| Base | Tests or docs reference `backend/data` as a runtime default path | References remain valid; no behavior change | `rg "backend/data|data/uploads|uploads/knowledge|local storage"` review finds no required source fixture removed |
| Bad | Untrack command deletes local data | Reject the implementation | Confirm local `backend/data` contents still exist after index cleanup |
| Bad | `.gitignore` rule hides required source/test fixtures outside runtime data | Reject or narrow the rule | `git status --short` and `rg` review show no unrelated files hidden |
| Bad | Implementation changes storage service, API, DB, Docker, or tests unnecessarily | Reject scope creep | Diff review shows only `.gitignore` plus index removals unless a directly justified doc/config adjustment is approved |

## Good / Base / Bad Cases

- Good case: committed runtime uploads are untracked, future uploads remain local ignored data, and existing local files are preserved.
- Base case: local storage tests keep using temporary directories or existing configured runtime paths without needing committed upload fixtures.
- Bad case: implementation deletes `backend/data`, changes local storage defaults, rewrites Docker volume behavior, or makes tests pass through hidden fallbacks.

## Required Research

- Capture tracked `backend/data/**` file list before cleanup.
- Search for storage path references in backend code, tests, README, Docker, and config.
- Confirm `.gitignore` currently lacks an adequate backend runtime data rule or contains a rule that needs tightening.
- Identify whether `LocalFileStorageServiceTest` or `DocumentServiceTest` relies on committed runtime data.

## Required Tests / Checks

Run after implementation:

- `git ls-files backend/data backend/data/**`
- `git status --short`
- `git diff --check`

Run if backend storage/document tests are affected or if research finds relevant references:

- From `backend/`: `mvn -q "-Dtest=LocalFileStorageServiceTest,DocumentServiceTest" test`

Optional if implementation only changes ignore/index metadata and no backend code/config is modified:

- Skip Maven tests with explicit explanation that source/runtime behavior was unchanged.

## Acceptance Criteria

- [ ] Tracked `backend/data/**` files were listed and reviewed before cleanup.
- [ ] `.gitignore` explicitly ignores local backend runtime data.
- [ ] `backend/data/**` files are removed from the Git index only.
- [ ] Existing local files under `backend/data/**` are not deleted.
- [ ] No business implementation, API, DB, Docker, retrieval, prompt, or frontend behavior is changed without approval.
- [ ] `git ls-files backend/data backend/data/**` returns empty after cleanup.
- [ ] `git status --short` shows only expected `.gitignore` and index-removal changes.
- [ ] Required checks are run or explicitly marked not applicable with reason.

## Planning Notes for DeepSeek

Keep the diff minimal. Treat this as repository hygiene plus runtime data boundary enforcement, not a storage-feature refactor.
