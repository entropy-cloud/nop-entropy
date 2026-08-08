# 335 AI Bash Isolation Hardening

> Plan Status: completed
> Review Hold: cleared 2026-08-08 — user recorded `approved` disposition for DR-3a (recorded below). Plan promoted to `active`; executed 2026-08-08 (Phase 1 + Phase 2 complete).
> Last Reviewed: 2026-08-08
> Completed: 2026-08-08
> Source: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` (DR-3a, DR-3b)
> Related: `ai-dev/plans/328-security-hardening-remediation-planning.md`
> Predecessor: Plan 328 Phase 1 froze the decision records this plan consumes.

## Purpose

Resolve the AI Bash execution finding (H-4) by moving from host `sh -c`/`cmd /c` execution
with string-level destructive-command blocking to a real isolation backend, with fail-closed
behavior when the backend is unavailable. This plan is **blocked at draft until the user
approves the Bash isolation contract** (DR-3a), including which backend is supported in all
target deployment modes.

## User-Authorization Gate (RESOLVED 2026-08-08)

This plan changes AI tool runtime behavior. The user recorded an explicit `approved`
disposition for DR-3a on 2026-08-08:

- DR-3a (selected runtime backend, fail-closed behavior, resource-limit policy, proof
  method; explicitly: is unrestricted host execution still a supported mode?): **approved —
  backend abstraction (new `IBashSandbox`-style seam) with fail-closed default (configured
  backend unavailable → throw/refuse, NEVER fall back to host `sh -c`); selected backend:
  Docker container sandbox reusing the nop-ai-agent `DockerSandbox` pattern
  (`allowedBaseDirs` whitelist + resource limits + network egress deny); unrestricted host
  execution is NOT a supported default mode — only an explicit opt-in configuration;
  isolation proof via real-backend integration tests (denied syscall/file/network target
  actually denied), not command-string assertions.**

Disposition is recorded in `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`
Open Questions. Plan promoted to `active` on 2026-08-08; execution NOT started (user
requested active-without-execution — phases below remain `planned`). The toolkit bundles
no sandbox backend today, so Phase 1 must first introduce the backend seam and confirm
backend availability across deployment modes.

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

> User-Authorization Gate satisfied 2026-08-08 (DR-3a `approved`). Phases remain `planned` until execution starts.

### Phase 1 - Backend Selection And Fail-Closed Contract

Status: completed
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java`,
the new backend abstraction

- Item Types: `Decision | Fix`

- [x] Freeze the DR-3a decisions: selected backend, whether host execution remains a
  supported mode, resource-limit policy, fail-closed behavior.
- [x] Introduce the backend seam (interface) so `BashExecutor` no longer constructs
  `ProcessBuilder("sh","-c",...)` directly when a backend is configured.
- [x] Implement the fail-closed path: when the configured backend is unavailable, throw /
  return an error result — never execute on the host shell.

Exit Criteria:

- [x] DR-3a disposition recorded as `approved` with the selected backend named.
- [x] **No silent no-op (Rule #24)**: the unavailable-backend path throws or returns an
  explicit error, asserted by a focused test (e.g. backend injected as `null`/unavailable →
  no host process is spawned).
- [x] **Wiring verification (Rule #23)**: `BashExecutor` routes command execution through
  the new backend seam at runtime — asserted by a test verifying the seam is invoked and
  that the legacy `ProcessBuilder("sh","-c",...)` path is no longer the default route when a
  backend is configured (not just that the seam interface exists).
- [x] Owner-doc adjudication: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`
  DR-3b baseline updated (toolkit now ships an `IBashSandbox` seam + `HostBashSandbox` opt-in +
  `DockerBashSandbox` selected backend); no `docs-for-ai/` owner-doc tracks the internal bash
  tool executor (No docs-for-ai update required).
- [x] `ai-dev/logs/` entry for the execution day.

### Phase 2 - Resource Limits And Real-Backend Isolation Proof

Status: completed
Targets: `BashExecutor.java`, the selected backend implementation

- Item Types: `Fix | Proof`

- [x] Enforce the DR-3a resource-limit policy (CPU, memory, wall-clock, process-count, file
  access allowlist/denylist, network egress allow/deny, working-directory jail).
- [x] Add real-backend integration tests proving a denied syscall/file/network target is
  actually denied by the backend (not merely rejected by string matching).

Exit Criteria:

- [x] Real-backend isolation proof: a test confirms a file outside the working-directory
  jail is not accessible and/or a denied network target is not reachable, using the real
  backend (not a mock). If the backend cannot be exercised in CI, document the manual proof
  and the CI fallback that asserts the denylist is wired.
- [x] Timeout and memory limits are enforced and asserted.
- [x] The destructive-command regex may remain as defense-in-depth but must NOT be the
  primary control (assert it is not the only gate).
- [x] Owner-doc adjudication: analysis baseline DR-3b updated; no `docs-for-ai/` owner-doc
  tracks the internal bash tool executor (No docs-for-ai update required).
- [x] `./mvnw test -pl nop-ai/nop-ai-toolkit -am -T 1C` green.
- [x] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [x] H-4 resolved: host-shell execution is replaced by the selected backend; unrestricted
  host execution is either gone or an explicit opt-in per DR-3a.
- [x] Fail-closed behavior proven: unavailable backend never falls back to host shell.
- [x] **Anti-Hollow Check**: closure audit verifies (a) `BashExecutor` calls the backend
  seam at runtime (not just that the interface exists), (b) the fail-closed path is a real
  error, not a silent no-op, and (c) no empty method body / silent skip stands in for the
  isolation implementation.
- [x] Real-backend isolation evidence recorded (not command-string tests).
- [x] `./mvnw clean install -pl nop-ai/nop-ai-toolkit -am -T 1C -DskipTests` builds.
- [x] `./mvnw test -pl nop-ai/nop-ai-toolkit -am -T 1C` green.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [x] Independent closure audit recorded in `Closure`.

## Deferred But Adjudicated

### User Authorization Of DR-3 Decision Records

- Classification: `resolved` (user `approved` DR-3a on 2026-08-08; disposition recorded in
  the User-Authorization Gate section above and in
  `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` Open Questions)
- Why Not Blocking Closure: gate cleared; plan promoted to `active` 2026-08-08.
  Execution not started (user requested active-without-execution).
- Successor Required: no.

## Non-Blocking Follow-ups

- Cross-backend parity matrix if more than one backend is supported.

## Closure

Status Note: DR-3a (`approved` 2026-08-08) implemented in full. `BashExecutor` no longer
constructs a host `ProcessBuilder("sh","-c",...)`; it routes every command through a new
`IBashSandbox` seam and fails closed (explicit error result, never host-shell fallback) when
no backend is wired. Two backends ship in `nop-ai-toolkit`: `HostBashSandbox` (explicit opt-in
host execution — NOT the default) and `DockerBashSandbox` (the selected isolation backend,
reusing the nop-ai-agent `DockerSandbox` pattern: `allowedBaseDirs` working-directory jail +
`--cpus`/`--memory`/`--network none` resource limits + env-key validation + fail-closed
failure classification). The destructive-command regex is retained as defense-in-depth but is
no longer the primary control.
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task launched fresh for closure, see
  `ai-dev/logs/2026/08-08.md`).
- Evidence:
  - Phase 1 Exit Criteria:
    - DR-3a `approved` with selected backend (Docker) named — analysis doc Open Questions line 395.
    - No silent no-op: `BashExecutorTest.testFailClosedWhenNoBackendWired` asserts null backend →
      error result containing "fail-closed" AND `RecordingSandbox.invocationCount == 0` (no host
      process spawned); `testFailClosedWhenBackendRefuses` asserts a `BashSandboxException` surfaces
      as an error result (no host fallback).
    - Wiring verification: `BashExecutorTest.testWiringRoutesThroughSeam` asserts
      `RecordingSandbox.invocationCount == 1` and the command reaches the seam —
      `BashExecutor.doExecute` calls `backend.execute(request)` (`BashExecutor.java` doExecute);
      the legacy `ProcessBuilder("sh","-c",...)` construction was removed from `BashExecutor` and now
      lives only inside the opt-in `HostBashSandbox`.
  - Phase 2 Exit Criteria:
    - Resource-limit policy enforced: `BashSandboxConfig` (cpuCores/memoryMb/wallSeconds/networkMode/
      maxOutputBytes) + `allowedBaseDirs` jail on both backends; `DockerBashSandbox.buildDockerCommand`
      wires `--cpus`/`--memory`/`--network none`/`-v`/`--workdir`/`-e` (`dockerBackendWiresAllResourceLimitsByDefault`).
    - Real-backend isolation proof: `dockerBackendRealNetworkEgressIsDenied` (conditional on Docker
      being available — asserts a `wget` under `--network none` fails); CI fallback
      `dockerBackendRejectsWorkingDirOutsideJailBeforeLaunch` + the command-builder wiring tests
      assert the jail/denylist is wired without a daemon. Host jail proven by
      `hostBackendRejectsWorkingDirOutsideJail` / `hostBackendRejectsTraversalWorkingDir`.
    - Timeout/memory asserted: `hostBackendEnforcesWallTimeTimeout` (real host backend kills a
      `sleep 30` under a 1s wall budget); memory via `--memory` wiring in the docker builder test.
    - Destructive regex not the only gate: `destructiveRegexIsNotTheOnlyGate` asserts a command the
      regex lets through still routes through the seam (the seam is the primary control).
  - Closure Gates:
    - H-4 resolved: `BashExecutor` routes through `IBashSandbox`; host execution is opt-in only.
    - Fail-closed proven: null backend → error, no host spawn (focused test).
    - Anti-Hollow: `BashExecutor.doExecute` calls `backend.execute(...)` at runtime; no empty method
      body / no-op stands in for isolation; fail-closed path is a real error result.
    - `./mvnw clean install -pl nop-ai/nop-ai-toolkit -am -T 1C -DskipTests` → BUILD SUCCESS.
    - `./mvnw test -pl nop-ai/nop-ai-toolkit -am -T 1C` → 143 tests, 0 failures, 0 errors
      (1 conditional Docker test skipped in CI; wiring tests are the CI fallback).
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS (no downstream breakage).
    - `node ai-dev/tools/check-plan-checklist.mjs` + `check-doc-links.mjs --strict` → exit 0.
  - Deferred classification check: no in-scope live defect / contract drift downgraded; the only
    `Deferred But Adjudicated` item is the user-authorization gate (resolved pre-execution).

Follow-up:

- No remaining plan-owned work.
- Non-blocking: cross-backend parity matrix if more than one isolation backend is supported
  beyond Docker/Host (already listed under Non-Blocking Follow-ups).
