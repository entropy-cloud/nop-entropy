Perform a deterministic-state gate check before starting work for mission '{{missionName}}'.

CHECK is a gate program that ensures the mission starts from a deterministic, known-good state. Its job is to verify the workspace is in a clean, compilable state before the mission loop begins.

> If you need to understand the repository structure, you may read `{{contextDir}}/project-context.md`.

## When {{checkCmd}} is configured (non-empty)

1. Run `{{checkCmd}}` in the project root.
2. If the command succeeds (exit 0) → emit `pass`.
3. If the command fails:
   a. Diagnose the failure and attempt to fix it (e.g. compile errors, missing generated files, stale build artifacts).
   b. Re-run `{{checkCmd}}` to verify the fix.
   c. If the re-run succeeds → emit `needs_fix` (the engine retries CHECK with a clean state).
   d. If the fix does not resolve the issue after reasonable effort → emit `fail`.
4. Do NOT run `commands.test` — that is BUILD_VERIFY's job, not CHECK's.

## When {{checkCmd}} is NOT configured (empty or missing)

Fall back to workspace-integrity detection:

1. Run `git status --porcelain` in the project root.
2. If the command itself fails (not a git repo, git missing) → emit `fail`.
3. Interpret the output:
   - Clean working tree (no output) → `pass`.
   - Dirty working tree (modified/untracked files) → `pass`. A dirty tree is normal in iterative development.
   - Merge conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) in tracked files → `fail`. The mission cannot proceed safely with unresolved conflicts.

## Philosophy

CHECK ensures "the mission starts from a known-good state", not "is the tree perfectly clean". A dirty tree is a warning, not a blocker. When `{{checkCmd}}` is configured, it provides the authoritative definition of "known-good" — use it.

Notes:
- CHECK runs once at mission entry (it is the flow `entry`, no transition returns to it).
- `needs_fix` triggers a retry of CHECK (up to 2 times); `fail` is terminal.
- The authoritative build health gate is BUILD_VERIFY; CHECK runs `{{checkCmd}}`, not `commands.test`.

Your output MUST end with exactly one `<AI_STEP_RESULT>` marker: `pass`, `needs_fix`, or `fail`.
