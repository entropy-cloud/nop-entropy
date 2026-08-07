# 335 AI Bash Isolation Hardening

> Plan Status: draft
> Review Hold: blocked on user authorization of DR-3a (isolation backend selection, host-execution mode). Cannot be resolved at review time — held as `draft` until the user records an approved disposition. Review passed (source anchors verified against live BashExecutor.java; format compliant; exit criteria testable; scope clear).
> Last Reviewed: 2026-08-08
> Source: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` (DR-3a, DR-3b)
> Related: `ai-dev/plans/328-security-hardening-remediation-planning.md`
> Predecessor: Plan 328 Phase 1 froze the decision records this plan consumes.

## Purpose

Resolve the AI Bash execution finding (H-4) by moving from host `sh -c`/`cmd /c` execution
with string-level destructive-command blocking to a real isolation backend, with fail-closed
behavior when the backend is unavailable. This plan is **blocked at draft until the user
approves the Bash isolation contract** (DR-3a), including which backend is supported in all
target deployment modes.

## User-Authorization Gate (BLOCKER)

This plan changes AI tool runtime behavior. It MUST remain `Plan Status: draft` with a
blocked implementation slice until the user records an explicit disposition for:

- DR-3a (selected runtime backend, fail-closed behavior, resource-limit policy, proof
  method; explicitly: is unrestricted host execution still a supported mode?).

The toolkit currently bundles **no** sandbox backend (see DR-3b runtime facts), so a
backend must be selected and its availability across deployment modes confirmed before
implementation.

## Current Baseline

See Plan 328 analysis DR-3a/DR-3b for verified source anchors. Summary:

- `BashExecutor.doExecute` builds `sh -c <command>` (or `cmd /c`) via `ProcessBuilder` and
  runs it on the host process. There is no process isolation backend.
- The only pre-execution control is `validateCommand` — a destructive-command regex that is
  trivially bypassable and provides no filesystem/network/process confinement.
- `parseEnv` strips a `DANGEROUS_ENV_VARS` set (defense-in-depth hint, not isolation).
- Timeout is enforced; CPU/memory/file/network limits are not.
- `IToolExecutor.executeAsync` runs on `context.getExecutor()`; there is no per-tool
  isolation seam beyond the `IToolExecutor` interface itself.

## Goals

- Replace host-shell execution with a real isolation backend chosen in DR-3a.
- Fail closed (refuse the call) when the selected backend is unavailable — never fall back
  to host `sh -c`.
- Enforce resource limits (CPU, memory, wall-clock, process-count, file access, network
  egress) per the DR-3a policy.
- Prove isolation with real-backend integration evidence, not command-string assertions.

## Non-Goals

- Sandboxing trusted developer-controlled XLang DSL (out of scope per Plan 328).
- Removing the `bash` tool entirely (the AI toolkit keeps a shell tool; it becomes isolated).
- HTTP egress enforcement (Plan 336).

## Scope

### In Scope

- `BashExecutor` (executor selection, command construction, resource limits, fail-closed
  backend-unavailable path).
- The backend abstraction/seam if a new one is introduced (today there is none).
- Real-backend integration tests proving isolation.

### Out Of Scope

- Authentication/JWT (Plan 333) and AES encrypted values (Plan 334).
- HTTP SSRF egress (Plan 336).
- Generated (`_`-prefixed) files.

## Execution Plan

> All Phases are `blocked` until the User-Authorization Gate is satisfied.

### Phase 1 - Backend Selection And Fail-Closed Contract

Status: blocked (pending DR-3a user approval)
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java`,
the new backend abstraction

- Item Types: `Decision | Fix`

- [ ] Freeze the DR-3a decisions: selected backend, whether host execution remains a
  supported mode, resource-limit policy, fail-closed behavior.
- [ ] Introduce the backend seam (interface) so `BashExecutor` no longer constructs
  `ProcessBuilder("sh","-c",...)` directly when a backend is configured.
- [ ] Implement the fail-closed path: when the configured backend is unavailable, throw /
  return an error result — never execute on the host shell.

Exit Criteria:

- [ ] DR-3a disposition recorded as `approved` with the selected backend named.
- [ ] **No silent no-op (Rule #24)**: the unavailable-backend path throws or returns an
  explicit error, asserted by a focused test (e.g. backend injected as `null`/unavailable →
  no host process is spawned).
- [ ] **Wiring verification (Rule #23)**: `BashExecutor` routes command execution through
  the new backend seam at runtime — asserted by a test verifying the seam is invoked and
  that the legacy `ProcessBuilder("sh","-c",...)` path is no longer the default route when a
  backend is configured (not just that the seam interface exists).
- [ ] Owner-doc adjudication: if this Phase changes live baseline/owner behavior, relevant
  `ai-dev/design/` / `docs-for-ai/` updated; otherwise explicit `No owner-doc update
  required` recorded.
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 2 - Resource Limits And Real-Backend Isolation Proof

Status: blocked (pending Phase 1)
Targets: `BashExecutor.java`, the selected backend implementation

- Item Types: `Fix | Proof`

- [ ] Enforce the DR-3a resource-limit policy (CPU, memory, wall-clock, process-count, file
  access allowlist/denylist, network egress allow/deny, working-directory jail).
- [ ] Add real-backend integration tests proving a denied syscall/file/network target is
  actually denied by the backend (not merely rejected by string matching).

Exit Criteria:

- [ ] Real-backend isolation proof: a test confirms a file outside the working-directory
  jail is not accessible and/or a denied network target is not reachable, using the real
  backend (not a mock). If the backend cannot be exercised in CI, document the manual proof
  and the CI fallback that asserts the denylist is wired.
- [ ] Timeout and memory limits are enforced and asserted.
- [ ] The destructive-command regex may remain as defense-in-depth but must NOT be the
  primary control (assert it is not the only gate).
- [ ] Owner-doc adjudication: if this Phase changes live baseline/owner behavior, relevant
  `ai-dev/design/` / `docs-for-ai/` updated; otherwise explicit `No owner-doc update
  required` recorded.
- [ ] `./mvnw test -pl nop-ai/nop-ai-toolkit -am -T 1C` green.
- [ ] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [ ] H-4 resolved: host-shell execution is replaced by the selected backend; unrestricted
  host execution is either gone or an explicit opt-in per DR-3a.
- [ ] Fail-closed behavior proven: unavailable backend never falls back to host shell.
- [ ] **Anti-Hollow Check**: closure audit verifies (a) `BashExecutor` calls the backend
  seam at runtime (not just that the interface exists), (b) the fail-closed path is a real
  error, not a silent no-op, and (c) no empty method body / silent skip stands in for the
  isolation implementation.
- [ ] Real-backend isolation evidence recorded (not command-string tests).
- [ ] `./mvnw clean install -pl nop-ai/nop-ai-toolkit -am -T 1C -DskipTests` builds.
- [ ] `./mvnw test -pl nop-ai/nop-ai-toolkit -am -T 1C` green.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [ ] Independent closure audit recorded in `Closure`.

## Deferred But Adjudicated

### User Authorization Of DR-3 Decision Records

- Classification: `blocked (not a residual — a hard prerequisite)`
- Why Not Blocking Closure Of Plan 328: this is a successor plan; Plan 328 closed on
  having created this draft.
- Successor Required: this plan IS the successor; it activates only after user `approved`.

## Non-Blocking Follow-ups

- Cross-backend parity matrix if more than one backend is supported.

## Closure

Status Note: Draft created by Plan 328 Phase 2. Blocked at draft pending user authorization
of DR-3a. No implementation has begun.
Completed: N/A

Closure Audit Evidence:

- Reviewer / Agent: N/A (draft, not yet completed)
- Evidence: N/A

Follow-up:

- User must record `approved`/`rejected`/`deferred` for DR-3a before this plan can be
  promoted to `active`.
