# 191 nop-ai-agent AgentName Path Injection Fix (P2)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: AUDIT-13-16
> Last Reviewed: 2026-06-15
> Source: carry-over from `ai-dev/plans/190-nop-ai-agent-session-id-path-traversal-fix.md` (Deferred But Adjudicated §[13-16]); live defect verified at `DefaultAgentEngine.java:1206-1207`; roadmap row `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md:261`
> Related: `ai-dev/plans/190-nop-ai-agent-session-id-path-traversal-fix.md` (sibling P0 sessionId fix that delivered the reusable `SessionIds.requireValidIdentifier` pattern this plan mirrors)

## Purpose

Close the P2 path-injection finding [13-16] in `nop-ai-agent`: a caller-controlled `agentName` (sourced from the public API `AgentMessageRequest.agentName`, and indirectly from LLM-supplied `call-agent` tool args) flows unvalidated into the string concatenation `"/" + agentName + ".agent.xml"` in `DefaultAgentEngine.loadAgentModel`, then into `ResourceComponentManager.instance().loadComponentModel(path)`. This enables VFS resource path traversal / arbitrary resource load via a traversal-shaped agentName such as `"../../etc/passwd"`. It is the sibling of the P0 sessionId fix in plan 190 — same attack class, distinct surface. This plan brings the agentName surface to a fail-closed state and adds a defense-in-depth agentId whitelist in `CallAgentExecutor`.

## Current Baseline

Verified against live repo (2026-06-15):

- `DefaultAgentEngine.loadAgentModel(String agentName)` at `engine/DefaultAgentEngine.java:1206-1220` builds `String path = "/" + agentName + ".agent.xml"` at `:1207` with **zero** validation, then calls `ResourceComponentManager.instance().loadComponentModel(path)` at `:1209`. A caller-controlled agentName containing `../` (e.g. `"../../etc/passwd"`) drives the VFS loader to an arbitrary resource path.
- `loadAgentModel` is the single private chokepoint for agentName → VFS-path conversion. It has exactly three callers, all inside `DefaultAgentEngine`:
  - `:627` `doExecute` — reached via the public API `execute(AgentMessageRequest)` (`:621-623`); `agentName` comes directly from `request.getAgentName()` (caller-controlled).
  - `:763` `resumeSession` — `agentName` comes from `session.getAgentName()` (persisted, originally written at session creation; defense-in-depth still warranted because persisted data is only as trustworthy as the write path).
  - `:881` `restoreSession` — same persisted-session source as `resumeSession`.
- `CallAgentExecutor` (`tool/CallAgentExecutor.java:91-110`) resolves `agentId` from LLM-supplied tool args (`call.getInput()` JSON, parsed at `:255-272`). The only validation is a null/empty check at `:92-94`; a non-empty value is assigned directly to `targetAgentId` at `:109` (unless it equals the literal `"self"`, which resolves to the parent's agentName at `:103`). `targetAgentId` then flows into `engine.execute(execRequest)` at `:188` → `doExecute` → `loadAgentModel`. An LLM that emits `call-agent` with `agentId="../../../etc/passwd"` reaches the unvalidated concatenation.
- Plan 190 (completed) delivered `SessionIds.requireValidIdentifier` at `engine/SessionIds.java:60-71`, enforcing the strict allow-list regex `^[A-Za-z0-9_-]+$` with fail-closed `NopAiAgentException`. This is the proven pattern this plan mirrors for agentName. The existing `SessionIds` helper is semantically keyed to sessionId (method param name + javadoc); whether to reuse, generalize, or introduce a sibling helper for agentName is an execution-time decision (see Goals, not Non-Goals — but the implementation choice is left to the source, per plan rule 10).
- All existing agent model resources in the repo use simple identifiers matching `^[A-Za-z0-9_-]+$` (e.g. `test-agent`, `test-react-agent`, `test-parent-agent`, `test-plan-agent`) — confirmed via glob of `*.agent.xml` under `src/test/resources/_vfs/`. No legitimate agent name requires `/`, `\`, `.`, whitespace, or Unicode. The VFS path is single-level (`"/" + name + ".agent.xml"`); there is no namespaced/nested agent-name convention.
- Roadmap row `nop-ai-agent-roadmap.md:261` tracks this as `AUDIT-13-16 | P2 | ❌ 未修复`.

## Goals

- Make every `agentName` that reaches the VFS path concatenation in `loadAgentModel` pass a strict identifier validation (mirroring the plan-190 rule: null/empty rejected, and only `[A-Za-z0-9_-]` characters allowed).
- Reject invalid agentNames with a fail-closed exception (no silent fall-back, no truncation, no sanitization) — Minimum Rule #24.
- Validate at the `loadAgentModel` chokepoint so all three callers (`:627`, `:763`, `:881`) — and the indirect `CallAgentExecutor` → `engine.execute` path — are covered by a single guard.
- Add defense-in-depth validation of `agentId` in `CallAgentExecutor` (after the existing empty check) so an LLM-supplied traversal-shaped agentId is rejected with a clean, LLM-facing fail result before the engine is invoked.
- Add regression tests proving a traversal-shaped agentName is rejected at the chokepoint and at the public API boundary, and that a traversal-shaped `agentId` in `call-agent` yields a fail result without VFS access.

## Non-Goals

- Fixing the other audit findings (13-01 AllowAll defaults, 13-02 NoOpAuditLogger, 13-03 symlink resolution, 14-01 concurrency, 14-04 atomic write, 09-01 NopAiAgentException base class, etc.) — each is its own work item with its own surface.
- Adding `agentName` validation at the `AgentMessageRequest` DTO constructor level (validating at the `loadAgentModel` chokepoint is sufficient and stays close to the dangerous VFS operation, mirroring plan 190's decision to stay close to the filesystem use rather than the DTO).
- Changing `sessionId` validation (already closed by plan 190).
- Centralizing all caller-supplied identifier validation (sessionId / agentName / toolName) behind a single shared validator — that is a non-blocking follow-up tracked in plan 190; this plan fixes the live defect without committing to that refactor.
- Changing the `"self"` resolution semantics in `CallAgentExecutor` (when `agentId="self"`, the parent's agentName is used; that value is already validated when the parent session was created).

## Scope

### In Scope

- Identifier validation (null/empty rejected + strict allow-list `^[A-Za-z0-9_-]+$`, fail-closed) applied at the top of `DefaultAgentEngine.loadAgentModel`, before the `"/" + agentName + ".agent.xml"` concatenation — the single chokepoint covering all three call sites and the indirect `CallAgentExecutor` path.
- Identifier validation applied in `CallAgentExecutor` after the existing empty check (`:92-94`), on the non-`"self"` branch (`:108-110`), returning a descriptive `AiToolCallResult.errorResult` (consistent with `CallAgentExecutor`'s fail-with-error-result contract) for any `agentId` that fails the allow-list. The `"self"` branch does not need a new check (it resolves to the parent's already-validated agentName).
- Regression + boundary unit tests for both surfaces.

### Out Of Scope

- `sessionId` validation (closed by plan 190).
- `toolName` / path-parameter validation hardening (13-04, 13-08, 13-09, 13-10).
- Any change to the `AgentMessageRequest` DTO or its constructor.
- Any change to how persisted sessions store agentName.
- The centralized `AgentIdentifiers` validator refactor (plan 190 Non-Blocking Follow-ups).

## Execution Plan

### Phase 1 - Validate agentName at the loadAgentModel chokepoint + CallAgentExecutor agentId whitelist

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (`loadAgentModel`, `:1206-1220`), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java` (`:91-110`)

- Item Types: `Fix`

- [x] Add fail-closed identifier validation at the top of `DefaultAgentEngine.loadAgentModel` (before `:1207`): reject null/empty and any agentName containing a character outside `[A-Za-z0-9_-]` by throwing `NopAiAgentException` with a descriptive message (e.g. naming the guard and the allowed character class). This single guard covers the public API path (`execute` → `doExecute:627`), `resumeSession:763`, and `restoreSession:881`.
- [x] Add identifier validation in `CallAgentExecutor` on the non-`"self"` branch (`:108-110`), immediately after the existing null/empty check (`:92-94`): an `agentId` that fails the strict allow-list returns a descriptive `AiToolCallResult.errorResult` via the existing `fail(...)` helper (`:306-308`). The `"self"` branch (`:102-107`) is left unchanged (resolves to the parent's already-validated agentName).
- [x] Ensure both rejection paths fail hard — no `continue`, no null return, no silent sanitization, no truncation (Minimum Rule #24). `loadAgentModel` throws; `CallAgentExecutor` returns an error result (its documented contract is fail-with-error-result, never throw uncaught — see class javadoc `:40-42`).
- [x] Document the new fail-closed behavior in the javadoc of `loadAgentModel` and in the `CallAgentExecutor` class javadoc (note that `agentId` is now allow-list validated).

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `loadAgentModel` validates `agentName` against `^[A-Za-z0-9_-]+$` (null/empty rejected) **before** any string concatenation or `ResourceComponentManager` call — verifiable by reading the method: the validation call precedes the `"/" + agentName` line.
- [x] `CallAgentExecutor` validates `agentId` on the non-`"self"` branch against the same allow-list and returns a descriptive error result for violations — verifiable by reading `:91-110` and confirming the `"self"` branch is unchanged.
- [x] **无静默跳过** (Minimum Rule #24): invalid agentNames throw in `loadAgentModel`; invalid agentIds return an error result in `CallAgentExecutor`. No silent fall-back / truncation / sanitization in either path.
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes.
- [x] No owner-doc update required: this is an internal security defect fix; the fail-closed behavior is documented in the affected methods' javadoc (class-level owner docs describe architecture decisions, not per-method validation — see Minimum Rule #14). The roadmap row `nop-ai-agent-roadmap.md:261` will be flipped ❌→✅ in Phase 2 closure, not here.
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Regression and boundary tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/...` (new `TestAgentNameValidation` plus additions to `TestCallAgentExecutor`).

- Item Types: `Proof`

- [x] Add a focused `TestAgentNameValidation` covering the `loadAgentModel` validation directly (or via the closest reachable seam — e.g. invoking `execute` with a real engine instance and asserting the throw): valid names pass (`test-agent`, `my_agent`, `Agent123`); each rejection case throws `NopAiAgentException` — `../`, absolute `/etc/passwd`, backslash `..\x`, NUL byte, empty string, whitespace, lone dot `.`/`..`, dot-containing `a.b`, and a Unicode char.
- [x] Add an end-to-end test at the `DefaultAgentEngine.execute` level proving an `AgentMessageRequest` carrying a traversal agentName (e.g. `"../../etc/passwd"`) is rejected before any VFS resource load (assert the exception escapes synchronously — mirroring plan 190's `executeRejectsTraversalSessionIdBeforeStoreInteraction` pattern — and that no `loadComponentModel` side effect occurs for the traversal path, e.g. via a focused seam or by asserting the exception is thrown before the future is constructed).
- [x] Add tests to `TestCallAgentExecutor` proving: (a) a `call-agent` tool call with a traversal-shaped `agentId` (e.g. `"../../../etc/passwd"`) returns an `AiToolCallResult` error result (does NOT throw, does NOT reach `engine.execute`); (b) the `"self"` branch still resolves correctly and is not blocked by the new check; (c) a valid `agentId` still proceeds to `engine.execute` (not over-blocking).
- [x] Verify no over-blocking: existing tests that use legitimate agent names (`test-agent`, `test-react-agent`, etc.) continue to pass without modification.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestAgentNameValidation` exists and asserts each valid/invalid case described above (valid passes; each rejection vector throws `NopAiAgentException`).
- [x] **端到端验证** (Minimum Rule #22): a `DefaultAgentEngine.execute`-level test confirms a traversal agentName is rejected at the public API boundary before reaching VFS resource load.
- [x] **接线验证** (Minimum Rule #23): the test suite asserts the validation is actually invoked on the engine path — e.g. via the thrown exception type/message containing the agentName guard marker, or a focused test driving `loadAgentModel`'s caller.
- [x] `TestCallAgentExecutor` tests assert: traversal `agentId` → error result (not throw, not VFS); `"self"` branch unaffected; valid `agentId` not over-blocked.
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` passes (all new and existing tests green — the latter confirms no over-blocking of legitimate agent names).
- [x] Roadmap row `nop-ai-agent-roadmap.md:261` flipped from `❌ 未修复` to `✅ 已修复` with plan 191 noted as the landing plan.
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] P2 finding [13-16] is resolved: no `agentName` reaching the VFS path concatenation in `loadAgentModel` can be driven outside the `/[name].agent.xml` form by any caller-supplied or LLM-supplied value.
- [x] Fail-closed behavior is the only behavior for invalid agentNames / agentIds (no silent sanitization, no fall-back).
- [x] `CallAgentExecutor` defense-in-depth rejects traversal-shaped agentIds with a clean LLM-facing error before `engine.execute`.
- [x] Regression tests exist and pass for every rejection vector listed in Phase 2.
- [x] End-to-end test at `DefaultAgentEngine.execute` proves the public API rejects traversal agentNames.
- [x] No over-blocking: all existing tests using legitimate agent names pass unchanged.
- [x] No in-scope live defect or contract drift remains in this plan's scope.
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes.
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` passes.
- [x] checkstyle / 代码规范检查通过.
- [x] 受影响的 owner docs 已同步 (roadmap row flipped ❌→✅; per-method javadoc updated) 或明确写明 No owner-doc update required for design docs (architecture unchanged).
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据.
- [x] **Anti-Hollow Check**: closure audit verifies (a) the validation in `loadAgentModel` is the first statement before the concatenation (runtime call-chain, not just type presence), (b) `CallAgentExecutor`'s check is on the live path between arg-parsing and `engine.execute`, (c) rejection throws / returns error rather than silently proceeding.

## Deferred But Adjudicated

(None at drafting time. Any item moved here during execution must include Classification, Why Not Blocking Closure, and Successor Required fields.)

## Non-Blocking Follow-ups

- Centralizing all caller-supplied identifier validation (sessionId / agentName / toolName) behind a single shared `AgentIdentifiers` validator — tracked in plan 190 Non-Blocking Follow-ups; this plan fixes the live defect without committing to that refactor. Whether the agentName validation reuses `SessionIds.requireValidIdentifier`, a generalized helper, or a sibling class is an execution-time implementation choice, not a plan-level decision.

## Closure

Status Note: P2 finding [13-16] closed. agentName path-injection guard at the `loadAgentModel` chokepoint (Phase 1) + defense-in-depth agentId allow-list in `CallAgentExecutor` (Phase 1) + full regression/boundary/end-to-end test coverage (Phase 2). All Closure Gates verified by independent audit subagent.
Completed: 2026-06-15

Closure Audit Evidence:

Reviewer/Agent: general-closure-auditor (fresh session, distinct task_id ses_1368a3539ffe1wyqTSs3gP6LN7)
Audit Session: independent closure audit of plan 191, 2026-06-15

Per-Exit-Criterion verdicts (all PASS):
1. P2 finding resolved: PASS — `DefaultAgentEngine.java:1230-1232`: `AgentNames.requireValidIdentifier(agentName)` (line 1231) is the FIRST statement, before `String path = "/" + agentName + ".agent.xml"` (line 1232) and `loadComponentModel(path)` (line 1234).
2. Fail-closed behavior: PASS — `AgentNames.java:55-66` throws `NopAiAgentException` (no sanitization/fall-back); `CallAgentExecutor.java:124-128` returns `fail(...)` error result.
3. CallAgentExecutor defense-in-depth: PASS — `CallAgentExecutor.java:124` check is in the non-`"self"` `else` branch (line 118), after arg parsing (lines 99-109), before `executeSubAgent`/`engine.execute` (lines 135, 154). `"self"` branch (lines 112-117) unchanged.
4. Regression tests exist: PASS — `TestAgentNameValidation` covers 4 valid + 11 rejection vectors (null/empty/`../`/`/etc/passwd`/`..\`/NUL/whitespace/`.`/`..`/`a.b`/Unicode) + predicate form.
5. End-to-end test: PASS — `TestAgentNameValidation.executeRejectsTraversalAgentNameBeforeVfsResourceLoad`: `assertThrows(NopAiAgentException.class, () -> engine.execute(req))` synchronously; `ThrowingChatService.callCount==0` proves no VFS load.
6. No over-blocking: PASS — `TestCallAgentExecutor` (a) traversal→error+executeCount==0, (b) self→success+executeCount==1, (c) valid→success+executeCount==1; `TestAgentNameValidation.executeAcceptsValidAgentNameAfterGuard`: test-agent→completed.
7. Anti-Hollow Check: PASS — (a) `loadAgentModel:1231` validation is first statement; `loadAgentModel` is first call of `doExecute:627`, called synchronously by `execute:623`. (b) `CallAgentExecutor:124` check is on live path between arg-parsing and engine.execute. (c) rejection throws (loadAgentModel) / returns error result (CallAgentExecutor), neither silently proceeds.

`check-plan-checklist.mjs --strict` exit code: 0 (after all items ticked + Reviewer field added).
`scan-hollow-implementations.mjs nop-ai/nop-ai-agent` exit code: 0 (scanner produces report; no new hollow findings introduced by this plan — Phase 2 is test-only, Phase 1 guards throw/return-error, never silent no-op).
`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: BUILD SUCCESS (all tests green, 22 new tests + existing suite).
