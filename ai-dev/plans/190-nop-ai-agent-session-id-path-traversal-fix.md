# 190 nop-ai-agent Session-Id Path Traversal Fix (P0)

> Plan Status: completed
> Last Reviewed: 2026-06-15
> Module: nop-ai-agent
> Work Item: AUDIT-13-15
> Source: ai-dev/audits/2026-06-15-deep-audit-nop-ai-agent/13-security-permission.md (finding [维度13-15], severity P0)
> Related: ai-dev/plans/185-nop-ai-agent-db-backed-session-store.md, ai-dev/plans/186-nop-ai-agent-db-backed-checkpoint-persistence.md

## Purpose

Close the P0 path-traversal vulnerability [13-15] in `nop-ai-agent`: a caller-controlled `sessionId` (sourced from the public API `AgentMessageRequest.sessionId`) flows unvalidated into `Path.resolve(sessionId)` calls in `FileBackedSessionStore` and `FileBackedCheckpointManager`, enabling arbitrary file write and delete outside the session root directory. This plan brings the library to a fail-closed state: any `sessionId` that is not a safe identifier is rejected before it reaches the filesystem.

## Current Baseline

Verified against live repo (2026-06-15):

- `DefaultAgentEngine.resolveSessionId(String)` at `engine/DefaultAgentEngine.java:1173-1178` only checks null/empty and returns the raw value unchanged — no character set or path-shape validation.
- `FileBackedSessionStore.sessionFilePath(String)` at `session/FileBackedSessionStore.java:289-291` returns `rootDirectory.resolve(sessionId).resolve(SESSION_FILE_NAME)` with no boundary check. `remove(String)` at `:135` calls `rootDirectory.resolve(sessionId)` then `Files.deleteIfExists` / `Files.delete` — arbitrary delete possible.
- `FileBackedCheckpointManager.sessionDirPath(String)` at `reliability/FileBackedCheckpointManager.java:296-298` returns `rootDirectory.resolve(sessionId)`; `loadSessionFromDisk` at `:240` uses the same pattern.
- `IAgentEngine.execute` / `sendMessage` / `resumeSession` / `restoreSession` / `cancelSession` are public API; `AgentMessageRequest` stores the caller-provided `sessionId` verbatim with no sanitization. Only `execute` (`DefaultAgentEngine.java:603`) and `sendMessage` (`:591`) route through `resolveSessionId`; `resumeSession` (`:709`→`sessionStore.get` at `:710`), `restoreSession` (`:783`→`:801`), and `cancelSession` (`:497`→`:505`/`:513`) call `sessionStore.get(sessionId)` directly with the raw caller value and therefore bypass `resolveSessionId` entirely.
- No existing plan addresses this finding. Plans 141/170/174 cover `IPathAccessChecker` (tool-execution path deny-lists), which is a distinct surface from sessionId resolution in the session/checkpoint stores.
- `FileBackedSessionStore` is the persistent store selected when crash/restart durability is needed (the shipped default is `InMemorySessionStore`), so the vulnerability activates the moment a deployment switches to persistent sessions.

Attack chain (confirmed by audit, statically): caller passes `sessionId="../../../etc/cron.d/exploit"` → `save()` overwrites `/etc/cron.d/exploit/session.json` (or a sibling path the caller can shape), and `removeSession()` deletes an arbitrary file/dir the process can access. Combined with cron or `~/.ssh/authorized_keys` this enables RCE / privilege escalation.

## Goals

- Make every `sessionId` that reaches a filesystem `Path.resolve` in `nop-ai-agent` pass a strict allow-list validation: only `[A-Za-z0-9_-]` characters, non-empty, and the resolved/normalized path must remain inside the configured root directory.
- Reject invalid sessionIds with a fail-closed exception (no silent fall-back, no truncation).
- Cover all confirmed resolve sites: engine-layer identifier validation at `DefaultAgentEngine.resolveSessionId` (covers `execute`/`sendMessage` only), and store/checkpoint-layer containment validation at every `rootDirectory.resolve(sessionId)` site in `FileBackedSessionStore` and `FileBackedCheckpointManager` — this store/checkpoint layer is also the defense-in-depth that catches the raw caller sessionIds reaching `resumeSession`/`restoreSession`/`cancelSession` (which bypass `resolveSessionId`) — plus any other `rootDirectory.resolve(sessionId)` discovered during execution.
- Add regression tests that prove a traversal-shaped sessionId is rejected at the entry point and at each store.

## Non-Goals

- Fixing the sibling finding [13-16] (`agentName` path injection in `loadAgentModel` / `CallAgentExecutor` agentId whitelist) — that is a separate P2 finding with its own surface.
- Hardening the other P1/P2 audit findings (13-01 default AllowAll, 14-01 concurrency, etc.) — each is its own work item.
- Changing the `sessionId` generation strategy for internally-created sessions (UUID remains the default; validation only guards externally-supplied ids).
- Adding a session-id allow-list at the `AgentMessageRequest` DTO level (identifier validation at `resolveSessionId` for `execute`/`sendMessage`, plus the store/checkpoint-layer containment validation that also guards the `resumeSession`/`restoreSession`/`cancelSession` paths, is sufficient and stays close to the filesystem use).

## Scope

### In Scope

- A single shared sessionId validation routine with two layers, reusable by the engine, session store, and checkpoint manager: an identifier-level check (null/empty + strict regex `^[A-Za-z0-9_-]+$`) runnable at the engine layer where no `rootDirectory` is in scope, and a containment check (`rootDirectory.resolve(id).normalize()` must `startsWith(rootDirectory.normalize())`) runnable at the store/checkpoint layer where `rootDirectory` is in scope.
- Wiring the identifier-level layer into `DefaultAgentEngine.resolveSessionId` (covers `execute`/`sendMessage`) and applying the full routine (identifier + containment) as defense-in-depth at each `rootDirectory.resolve(sessionId)` call site — which is also the layer that validates the raw sessionIds reaching `resumeSession`/`restoreSession`/`cancelSession`.
- Regression + boundary unit tests proving traversal is blocked and legitimate UUIDs still pass.

### Out Of Scope

- `agentName` validation (13-16).
- `toolName` / path-parameter validation hardening (13-09, 13-10, 13-23).
- Concurrency, atomic-write, and audit-logger fixes (14-xx, 13-02).
- Any change to `InMemorySessionStore` (it never resolves paths).

## Execution Plan

### Phase 1 - Add sessionId validation and harden resolve sites

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/FileBackedSessionStore.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/FileBackedCheckpointManager.java`, plus the new validation helper location chosen during execution.

- Item Types: `Fix`

- [x] Introduce a shared sessionId validation helper with two layers (placed where the engine, session, and reliability packages can all reach it without a circular dependency): (1) an identifier-level check that rejects null/empty and enforces the strict allow-list regex `^[A-Za-z0-9_-]+$` — this is the only layer runnable at the engine (`resolveSessionId`), where no `rootDirectory` is in scope; (2) a containment check that, after `rootDirectory.resolve(id).normalize()`, asserts the result `startsWith(rootDirectory.normalize())` so even allow-list edge cases cannot escape the root — this layer runs at the store/checkpoint layer (`FileBackedSessionStore`, `FileBackedCheckpointManager`), where `rootDirectory` is in scope. Both layers throw a fail-closed `NopAiAgentException` with a descriptive message on any violation.
- [x] Wire the identifier-level layer of the helper into `DefaultAgentEngine.resolveSessionId` (`engine/DefaultAgentEngine.java:1173-1178`) so the sessionIds entering `execute` (`:603`) and `sendMessage` (`:591`) pass strict identifier validation before use (the UUID fallback path for empty ids remains unchanged). This does NOT cover `resumeSession` (`:709`), `restoreSession` (`:783`), or `cancelSession` (`:497`), which accept raw caller sessionIds and bypass `resolveSessionId`; those paths are caught by the store/checkpoint-layer containment check in the next item.
- [x] Apply the full routine (identifier-level + containment) at each confirmed resolve site as defense-in-depth: `FileBackedSessionStore.sessionFilePath` (`:289-291`), `FileBackedSessionStore.remove` (`:135`), `FileBackedCheckpointManager.sessionDirPath`/`loadSessionFromDisk` (`:240, 296-298`). This store/checkpoint layer is what validates the raw caller sessionIds reaching `resumeSession` (`:709`), `restoreSession` (`:783`), and `cancelSession` (`:497`) via `sessionStore.get(sessionId)`. Grep `nop-ai-agent/src/main/java` for any other `rootDirectory.resolve(sessionId)` and harden those too.
- [x] Ensure all rejection paths throw (no `continue`, no null return, no silent sanitization) per Minimum Rule #24.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] A single validation routine exists and is invoked by `DefaultAgentEngine.resolveSessionId` and by every `rootDirectory.resolve(sessionId)` site in the module (verifiable by grep: no `resolve(sessionId` call site lacks a preceding validation call).
- [x] `sessionId` values containing `/`, `\`, `..`, NUL, or any char outside `[A-Za-z0-9_-]` cause a `NopAiAgentException` (fail-closed), verifiable by reading the validation routine.
- [x] A normalized-path containment check guarantees `rootDirectory.resolve(id).normalize()` stays within `rootDirectory.normalize()`.
- [x] **无静默跳过**：invalid sessionIds throw, never return a sanitized value or fall through to filesystem access (Minimum Rule #24).
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes.
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Regression and boundary tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/...` (new `TestSessionIdValidation` plus additions to the existing `FileBackedSessionStore` / `FileBackedCheckpointManager` test classes).

- Item Types: `Proof`

- [x] Add a focused `TestSessionIdValidation` covering: valid UUID passes; valid `[A-Za-z0-9_-]` id passes; each rejection case throws — `../`, absolute `/etc/x`, backslash `..\x`, NUL byte, empty string, whitespace, dot `.`/`..` literal, and a Unicode char.
- [x] Add regression tests to the `FileBackedSessionStore` test class proving: `save`/`get`/`remove` with a traversal-shaped sessionId throw and never touch a file outside the test root (assert no file is created at the traversal target using a temp-dir probe).
- [x] Add regression tests to the `FileBackedCheckpointManager` test class proving the same for `saveCheckpoint`/`getCheckpoint`/`loadSessionFromDisk` paths.
- [x] Add one end-to-end-style test at the `DefaultAgentEngine.execute` level proving that an `AgentMessageRequest` carrying a traversal sessionId is rejected before any store interaction (e.g. assert the engine throws and no file is written under the root).

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestSessionIdValidation` exists and asserts each valid/invalid case described above.
- [x] `FileBackedSessionStore` and `FileBackedCheckpointManager` tests assert traversal ids throw AND that no out-of-root file artifact is produced (temp-dir probe).
- [x] **端到端验证** (Minimum Rule #22): a `DefaultAgentEngine.execute`-level test confirms a traversal sessionId is rejected at the public API boundary before reaching the stores.
- [x] **接线验证** (Minimum Rule #23): the test suite asserts the validation helper is actually invoked on the engine path (e.g. via the thrown exception type/message, or a focused test calling `resolveSessionId`'s caller).
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` passes (all new and existing tests green).
- [x] No owner-doc update required (this is a security defect fix internal to the library; no public contract change beyond "invalid sessionIds now throw"). Document the new fail-closed behavior in the class javadoc of the affected stores as part of Phase 1.
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] P0 finding [13-15] is resolved: no `rootDirectory.resolve(sessionId)` site in `nop-ai-agent` can be driven outside the root by any caller-supplied sessionId.
- [x] Fail-closed behavior is the only behavior for invalid sessionIds (no silent sanitization, no fall-back).
- [x] Regression tests exist and pass for every rejection vector listed in Phase 2.
- [x] End-to-end test at `DefaultAgentEngine.execute` proves the public API rejects traversal sessionIds.
- [x] No in-scope live defect or contract drift remains in this plan's scope.
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes.
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` passes.
- [x] checkstyle / 代码规范检查通过.
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据.
- [x] **Anti-Hollow Check**: closure audit verifies the validation routine is actually invoked on the engine → store path (runtime call-chain, not just type presence) and that rejection throws rather than silently returns.

## Deferred But Adjudicated

### Sibling finding [13-16] — agentName path injection + CallAgentExecutor agentId whitelist

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: distinct attack surface (VFS resource path + LLM-controlled sub-agent id), different files (`loadAgentModel`, `CallAgentExecutor`), and a different severity (P2). Fixing 13-15 does not depend on it and vice versa. Will be addressed by its own work item.
- Successor Required: yes
- Successor Path: to be created (next available plan number when picked up)

## Non-Blocking Follow-ups

- Consider centralizing all caller-supplied identifier validation (sessionId / agentName / toolName) behind a single `AgentIdentifiers` validator in a future hardening pass; out of scope for this single P0 fix.

## Closure

Status Note: P0 path-traversal finding [13-15] is genuinely remediated. A single fail-closed helper (`SessionIds`, two layers: identifier regex + filesystem containment) is wired into both layers — the engine entry point (`DefaultAgentEngine.resolveSessionId`, covering `execute`/`sendMessage`) and every filesystem-touching resolve site in `FileBackedSessionStore` and `FileBackedCheckpointManager` (`sessionDirPath` → `requireContainedPath`, which also guards the raw-caller paths `resumeSession`/`restoreSession`/`cancelSession` that bypass `resolveSessionId`). The fix is verified live (not hollow) by a synchronous-throw end-to-end test. The deferred [13-16] (agentName injection) is a genuinely separate surface and remains honestly unfixed. No in-scope defect or contract drift remains.
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (explore, fresh session `ses_13740664fffeeHM3KnS3JtRHeH`, distinct from the implementer session) + implementer self-verification of tool exit codes.
- Audit Session: `ses_13740664fffeeHM3KnS3JtRHeH`
- Evidence:
  - **Phase 1 Exit Criteria** — all PASS per independent audit:
    - Single validation routine exists & invoked at every resolve site: PASS — `SessionIds.java` (engine pkg); grep confirms the only `rootDirectory.resolve(sessionId)` in main code is inside `SessionIds.requireContainedPath` (`SessionIds.java:94`); the 4 original sites now route through `sessionDirPath` → `requireContainedPath` (`FileBackedSessionStore.java:300-311`, `FileBackedCheckpointManager.java:305-317`).
    - Invalid chars (`/`,`\`,`..`,NUL,non-allow-list) throw `NopAiAgentException`: PASS — `SessionIds.requireValidIdentifier` (`:60-71`) enforces `^[A-Za-z0-9_-]+$`.
    - Containment check `resolve(id).normalize()` stays within `rootDirectory.normalize()`: PASS — `SessionIds.requireContainedPath` (`:86-102`).
    - No silent skip (Rule #24): PASS — every invalid branch throws; no `continue`/null return/sanitization.
    - `./mvnw compile -pl nop-ai/nop-ai-agent -am`: PASS (BUILD SUCCESS).
    - `ai-dev/logs/2026/06-15.md` updated: PASS.
  - **Phase 2 Exit Criteria** — all PASS per independent audit:
    - `TestSessionIdValidation` (15 tests): PASS — valid UUID/allow-list pass; each rejection vector (`../`, absolute, backslash, NUL, empty, whitespace, `.`,`..`, unicode) throws.
    - Store tests assert throw AND no out-of-root artifact: PASS — `TestFileBackedSessionStore` (+5) & `TestFileBackedCheckpointManager` (+5) use temp-dir probes (planted outside-target file survives `remove`).
    - End-to-end (Rule #22): PASS — `TestFileBackedCheckpointDispatchPath.executeRejectsTraversalSessionIdBeforeStoreInteraction` drives `engine.execute` with `../../../etc/cron.d/exploit`, asserts `NopAiAgentException` escapes synchronously before the future is constructed, and asserts no file under/ outside the root.
    - Wiring (Rule #23): PASS — the e2e test asserts the thrown message contains the `path-traversal guard`/`[A-Za-z0-9_-]` identifier emitted only by `SessionIds`, proving `resolveSessionId` → `requireValidIdentifier` is the live path; positive case `executeAcceptsValidSessionIdAfterGuard` confirms not over-blocking.
    - `./mvnw test -pl nop-ai/nop-ai-agent -am`: PASS (BUILD SUCCESS). Surefire: TestSessionIdValidation 15/0/0/0, TestFileBackedSessionStore 29/0/0/0, TestFileBackedCheckpointManager 17/0/0/0, TestFileBackedCheckpointDispatchPath 7/0/0/0 (Tests/Failures/Errors/Skipped).
    - No owner-doc update required (internal security fix) + fail-closed behavior documented in class javadocs of both stores + `SessionIds`: PASS.
    - `ai-dev/logs/2026/06-15.md` updated: PASS.
  - **Closure Gates** — all 10 MET (independent audit Step 6 + tooling).
  - **Anti-Hollow check**: PASS — runtime call chain traced: `execute` → `resolveSessionId` (first statement, `:603`) → `SessionIds.requireValidIdentifier` (`:1183`); traversal input throws synchronously, proven by the e2e `assertThrows` on `engine.execute(req)` directly (not on a future). Store write path `save`→`sessionFilePath`→`sessionDirPath`→`requireContainedPath` is in-line before any `Files.write`/`delete`.
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`**: exit code **0** (1 plan checked, 1 passed, 0 failed).
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high`**: exit code **0** (15 reported `UnsupportedOperationException` findings are all pre-existing intentional "Phase 2 not yet implemented" stubs in `IAgentEngine`/`ISessionStore`/`IAiMemoryStore`/`NoOpHookRegistry`/`DefaultAgentEngine:1168` — none introduced or touched by this plan; this plan's code has full real logic with no hollow/no-op).
  - **Doc-link checker**: `check-doc-links.mjs --strict` reports 76 pre-existing broken links across files from prior sessions (plans 128/140/141/150/157/159/161/162/165/166/179/180/181/183/185/186/189, `nop-ai-agent-react-engine.md`, `nop-ai-agent-reliability.md`, `opencode-goal-driver/*`). Verified via grep + git status that **none** of plan 190's edited files (this plan file, `nop-ai-agent-roadmap.md`, `06-15.md` log, or any `nop-ai-agent/src/**` source) appear in the broken-links list — this task introduced **0** new broken links. The pre-existing cross-file doc debt is out of scope for a single P0 security fix.
  - **Deferred honesty**: finding [13-16] (`agentName` injection in `DefaultAgentEngine.loadAgentModel:1189` — still `"/" + agentName + ".agent.xml"` unchanged) is a genuinely distinct surface (VFS resource path, P2), correctly tracked with `Successor Required: yes`, NOT silently swallowed.

Follow-up:

- No remaining plan-owned work for [13-15].
- [13-16] (agentName path injection + `CallAgentExecutor` agentId whitelist) tracked in Deferred But Adjudicated — successor plan to be opened at the next available number.
- Pre-existing doc-link debt (76 broken links across prior-session files) is a separate doc-hygiene item, not plan-190-owned.
- `scan-hollow-implementations.mjs` 15 high-severity `UnsupportedOperationException` findings are pre-existing intentional stubs; consider a module-wide pass to convert them to `NopAiAgentException` (out of scope here).
