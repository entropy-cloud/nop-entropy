# 157 Actor Cancel Two-Level Semantics — graceful / forced

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: A5
> **Last Reviewed**: 2026-06-13
> **Source**: Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1, item A5 ("Actor Cancel 两级语义: graceful（完成当前 tool）/ forced（立即中断）")
> **Related**: Plan 134 (`134-nop-ai-agent-engine-actor-entry.md`, L1-1 IAgentEngine + DefaultAgentEngine), Plan 137 (`137-nop-ai-agent-session.md`, L1-10 AgentSession), Plan 149 (`149-nop-ai-agent-session-fields.md`, L1-20 session fields). Unblocks: L3-3 (IGoalTracker — cancel-aware goal tracking), L4 multi-agent orchestration, L4-5 (DeferredAckMailbox — reservation rollback on cancel).

## Purpose

Fill the `UnsupportedOperationException` hole in `IAgentEngine.cancelSession()` and `IAgentEngine.getSessionStatus()` by implementing two-level cancel semantics (graceful = complete current tool then stop; forced = interrupt immediately) in `DefaultAgentEngine`, making the ReAct execution loop cancellation-aware, and wiring the session status lifecycle (running → terminal status) so cancel results are externally observable.

## Current Baseline

Verified against live repo on 2026-06-13:

- `IAgentEngine.cancelSession(sessionId, reason, forced)` is a **default method that throws UOE** (`IAgentEngine.java:21-23`). The `forced` boolean parameter is already in the signature (added by L1-17), signaling two-level semantics is the intended completion.
- `IAgentEngine.getSessionStatus(sessionId)` is a **default method that throws UOE** (`IAgentEngine.java:17-19`).
- `IAgentEngine.forkSession(...)` is also a UOE default (`IAgentEngine.java:13-15`) — **out of scope** for this plan (requires VFS session store).
- `DefaultAgentEngine` **does NOT override** any of these three methods (`DefaultAgentEngine.java` — no cancel/getSessionStatus/forkSession methods present).
- `AgentExecStatus` enum has 4 values: `pending, running, completed, failed` (`AgentExecStatus.java:3-10`) — **no `cancelled` terminal state**.
- `AgentEventType` has 11 values (`AgentEventType.java`) — **no cancellation-related events**.
- `AgentExecutionContext` has status, iteration, tokens, messages, metadata fields (`AgentExecutionContext.java`) — **no cancellation flag or interrupt mechanism**.
- `DefaultAgentEngine.doExecute()` runs the executor inside `CompletableFuture.supplyAsync()` (`DefaultAgentEngine.java:177-192`) but:
  - Does **not track** running executions (no map of sessionId → running future/context/thread).
  - Does **not write** the execution's final status back to `AgentSession` — session status stays at its initial `pending` forever.
- `ReActAgentExecutor.execute()` is a synchronous `while` loop (`ReActAgentExecutor.java:252-479`) with **no cancellation check points** at any iteration boundary.
- `AgentSession.status` is set to `pending` at creation (`AgentSession.java:35`) and has a `setStatus()` method, but **is never updated by the engine** during or after execution.
- `InMemorySessionStore` is a `ConcurrentHashMap`-backed store with 4 CRUD methods (`InMemorySessionStore.java`) — suitable as the backing store for cancel/getSessionStatus.
- `TestIAgentEngineDefaultMethods` verifies all three methods throw UOE (`TestIAgentEngineDefaultMethods.java:28-32`) — **will need updating** for cancel/getSessionStatus.
- The engine runs ReAct executions on the common `ForkJoinPool` via `CompletableFuture.supplyAsync()` — the executing `Thread` reference is not captured, making direct thread interruption impossible without changes.

## Goals

- `cancelSession(sessionId, reason, forced=false)` (graceful): sets a cancel-requested flag on the running execution's context. The ReAct loop detects the flag at the next iteration boundary (after completing the current tool call / LLM call) and exits cleanly with `AgentExecStatus.cancelled`.
- `cancelSession(sessionId, reason, forced=true)` (forced): sets the flag AND interrupts the executing thread, causing immediate exit (unblocking any in-progress LLM/tool I/O) with `AgentExecStatus.cancelled`.
- `getSessionStatus(sessionId)`: returns the current `AgentExecStatus` of the session by querying the session store — no longer throws UOE.
- `AgentExecStatus.cancelled` represents the terminal cancelled state.
- `SESSION_CANCEL_REQUESTED` and `SESSION_CANCELLED` events are published via `AgentEventPublisher`.
- `AgentSession.status` is updated through the execution lifecycle: `running` at start → terminal status (`completed` / `failed` / `cancelled`) at end.

## Non-Goals

- `forkSession()` implementation (separate work item, requires VFS session store — remains UOE).
- Session checkpoint / snapshot / recovery from cancel (that is A4/L3-4, blocked by L3-4 ❌).
- Multi-agent cancellation propagation (Layer 4 concern).
- Cancellation deadlines / auto-timeout triggers (can be added later as a wrapper around `cancelSession`).
- VFS or DB-backed session store cancel behavior (only `InMemorySessionStore` in scope).

## Scope

### In Scope

- `AgentExecStatus` enum: add `cancelled` terminal value.
- `AgentEventType`: add `SESSION_CANCEL_REQUESTED` and `SESSION_CANCELLED` event types.
- `AgentExecutionContext`: add a thread-safe cancellation flag (visible across threads) + cancel reason.
- `DefaultAgentEngine`: implement `cancelSession()` + `getSessionStatus()` overrides; add per-session running execution tracking (cancel handle = context ref + executing thread ref); wire session status lifecycle in `doExecute()`.
- `ReActAgentExecutor.execute()`: add cancellation checks at iteration boundaries; handle forced-cancel thread interruption.
- Update `TestIAgentEngineDefaultMethods`: cancel/getSessionStatus tests verify real behavior; forkSession test stays UOE.
- New tests for graceful cancel, forced cancel, getSessionStatus, no-op cancel, session status lifecycle wiring.
- Owner doc updates: architecture baseline §四 + roadmap A5 status.

### Out of Scope

- `forkSession()` — remains UOE stub.
- VFS/DB session store cancel behavior.
- Checkpoint/recovery on cancel.
- Multi-agent cancel propagation.
- Cancel deadline/timeout auto-trigger.

## Execution Plan

### Phase 1 — Data Model Foundation: Cancelled Status, Events, Context Flag

Status: completed
Targets: `model/AgentExecStatus.java`, `engine/AgentEventType.java`, `engine/AgentExecutionContext.java`

- Item Types: `Fix`

- [x] Add `cancelled` value to `AgentExecStatus` enum (after `failed`)
- [x] Add `SESSION_CANCEL_REQUESTED` and `SESSION_CANCELLED` to `AgentEventType` enum
- [x] Add a thread-safe (volatile) cancellation flag + cancel reason field to `AgentExecutionContext` with getter and setter methods

Exit Criteria:

> Every item must be checked off `[x]` before Phase Status can be set to `completed`.

- [x] `AgentExecStatus.cancelled` exists and is usable in switch statements and equality checks
- [x] `AgentEventType.SESSION_CANCEL_REQUESTED` and `SESSION_CANCELLED` constants exist and can be referenced from `DefaultAgentEngine` and `ReActAgentExecutor`
- [x] `AgentExecutionContext` has a cancellation flag that is readable/writable from a different thread than the execution thread (volatile or equivalent)
- [x] Existing enum consumers (AgentSession, AgentExecutionResult, record-mappings) are not broken by the new enum value — `./mvnw compile -pl nop-ai-agent -am` passes
- [x] **No new test required**: these are additive enum constants and a new field — no new behavior to test yet (existing compilation verifies type safety)
- [x] No owner-doc update required for this phase (internal data model additions, no public contract change visible to users)
- [x] `ai-dev/logs/` entry updated

### Phase 2 — Engine Implementation: Cancel, getSessionStatus, Running Execution Tracking

Status: completed
Targets: `engine/DefaultAgentEngine.java`, `engine/AgentExecutionContext.java` (cancel handle retrieval if needed)

- Item Types: `Fix`

- [x] Add per-session running execution tracking to `DefaultAgentEngine`: a concurrent map from `sessionId` to a cancel handle that holds a reference to the running `AgentExecutionContext` (for setting the cancel flag) and the executing `Thread` (for forced interrupt)
- [x] Register the execution in the tracking map when `doExecute()` starts (inside the `supplyAsync` body, before calling `executor.execute()`), and unregister it after execution completes (in a `finally` block)
- [x] Wire session status lifecycle in `doExecute()`: set `session.setStatus(running)` before execution starts; set `session.setStatus(ctx.getStatus())` after execution completes (writes the terminal status — completed/failed/cancelled — back to the persistent session)
- [x] Implement `getSessionStatus(String sessionId)` override in `DefaultAgentEngine`: query `sessionStore.get(sessionId)`, return `session.getStatus()`; return `null` (or a not-found indicator) if session does not exist
- [x] Implement `cancelSession(String sessionId, String reason, boolean forced)` override in `DefaultAgentEngine`:
  - Look up the running execution's cancel handle for `sessionId`
  - If a running execution exists:
    - Set the cancel flag + reason on the context
    - Publish `SESSION_CANCEL_REQUESTED` event (with reason + forced flag in payload)
    - If `forced=true`: interrupt the executing thread via `Thread.interrupt()`
  - If no running execution exists but the session exists in the store: set `session.setStatus(cancelled)` directly (the session was idle or already terminated)
  - If the session does not exist at all: complete normally (no-op — nothing to cancel)
  - Return `CompletableFuture.completedFuture(null)` (cancel request is registered synchronously)

Exit Criteria:

- [x] `DefaultAgentEngine` overrides `cancelSession()` — calling it no longer throws UOE; it sets the cancel flag on the running context
- [x] `DefaultAgentEngine` overrides `getSessionStatus()` — calling it no longer throws UOE; it returns the session's status from the store
- [x] Running execution tracking is registered at execution start and unregistered at execution end (verified by code inspection: `finally` block removes the entry)
- [x] Session status lifecycle is wired: `doExecute()` sets `running` at start and the terminal status at end (verified by `getSessionStatus()` returning `running` during execution and the terminal status after)
- [x] **No silent no-op**: `cancelSession` on a non-existent session returns a completed future (explicit success, not an error); on an existing-but-idle session, it sets status to `cancelled` explicitly
- [x] **No new test required in this phase** — tests are in Phase 3 which verifies the complete flow including this wiring. However, the code must compile: `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `ai-dev/logs/` entry updated

### Phase 3 — ReAct Loop Cancellation Awareness + End-to-End Tests

Status: completed
Targets: `engine/ReActAgentExecutor.java`, `test/.../engine/TestDefaultAgentEngineCancel.java` (new), `test/.../engine/TestIAgentEngineDefaultMethods.java` (update)

- Item Types: `Fix`, `Proof`

- [x] Add cancellation check at the **top of the while-loop** in `ReActAgentExecutor.execute()` (before the LLM call): if the cancel flag is set, set `ctx.setStatus(cancelled)`, publish `SESSION_CANCELLED` event, and break out of the loop
- [x] Add cancellation check **after tool calls complete** (after the `for (CompletableFuture<ToolCallOutput> f : futuresArray)` block, before `ctx.setCurrentIteration + 1`): if the cancel flag is set, set status to `cancelled`, publish event, break — this is the graceful boundary where "complete current tool then stop" is realized
- [x] Handle forced-cancel interruption: wrap the `chatService.call()` and `toolManager.callTool()` paths so that `InterruptedException` (triggered by `Thread.interrupt()` from forced cancel) is caught and treated as cancellation — set status to `cancelled`, publish `SESSION_CANCELLED`, and break (do NOT rethrow as a generic failure)
- [x] Ensure the cancelled path skips the `POST_CALL` hook / `EXECUTION_COMPLETED` event for cancelled sessions (cancelled is a distinct terminal state, not "completed")
- [x] Update `TestIAgentEngineDefaultMethods`: replace `cancelSessionThrowsUOE` with a test verifying `cancelSession` no longer throws on `DefaultAgentEngine`; replace `getSessionStatusThrowsUOE` with a test verifying it returns a status. Keep `forkSessionThrowsUOE` as-is (still UOE).
- [x] Add test: `getSessionStatus` returns `pending` for a newly created session, returns `null` for a non-existent session
- [x] Add test: `cancelSession(graceful)` on a non-existent session completes normally (no-op)
- [x] Add test: `cancelSession(graceful)` on an existing-but-idle session sets status to `cancelled`
- [x] Add integration test (end-to-end, graceful): construct a `DefaultAgentEngine` with a mock `IChatService` that returns tool calls for multiple iterations; call `execute()` to start the session; from another thread call `cancelSession(sessionId, "test", false)` while execution is in-flight; verify the returned `AgentExecutionResult.getStatus()` is `cancelled`; verify the session status in the store is `cancelled`
- [x] Add integration test (end-to-end, forced): same setup but call `cancelSession(sessionId, "test", true)`; verify `AgentExecutionResult.getStatus()` is `cancelled`; verify execution terminated faster than graceful (or at minimum, terminated)
- [x] Add test: verify `SESSION_CANCEL_REQUESTED` and `SESSION_CANCELLED` events are published during cancel (using `DefaultAgentEventPublisher` listener capture)

Exit Criteria:

- [x] `ReActAgentExecutor.execute()` checks the cancel flag at the top of each iteration and after tool calls complete — when detected, exits with `AgentExecStatus.cancelled` and publishes `SESSION_CANCELLED` event
- [x] Forced cancel (thread interrupt) results in `AgentExecStatus.cancelled`, not `AgentExecStatus.failed` — the `InterruptedException` path is handled as cancellation
- [x] Cancelled executions do NOT emit `EXECUTION_COMPLETED` (they emit `SESSION_CANCELLED` instead) — cancelled is distinct from completed
- [x] **End-to-end verification**: a test exists that starts execution via `execute()` → calls `cancelSession()` from another thread mid-flight → verifies `AgentExecutionResult` has status `cancelled` AND the session in the store has status `cancelled` (full path: `execute()` → ReAct loop running → `cancelSession()` sets flag → loop detects flag → terminates → result returned)
- [x] **Wiring verification**: the end-to-end test proves `cancelSession()` actually sets the flag on the same `AgentExecutionContext` instance that the running `ReActAgentExecutor` is checking — the loop terminates, proving the wiring is connected
- [x] **Test-mandated**: new behavior covered by focused tests — graceful cancel (loop stops after current tool), forced cancel (immediate stop), getSessionStatus (existing + non-existent), no-op cancel (non-existent session), idle session cancel, event publication
- [x] `TestIAgentEngineDefaultMethods` updated: `cancelSession` and `getSessionStatus` tests verify real behavior on `DefaultAgentEngine`; `forkSession` test remains UOE
- [x] `./mvnw test -pl nop-ai-agent -am` passes (all existing + new tests green)
- [x] `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §四 updated: `IAgentEngine` description reflects that `cancelSession`/`getSessionStatus` are implemented in `DefaultAgentEngine` (not UOE stubs); `forkSession` remains Phase 2 stub
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` line 149: A5 status flipped from ❌ to ✅
- [x] `ai-dev/logs/` entry updated

## Closure Gates

- [x] `IAgentEngine.cancelSession()` UOE hole is filled: `DefaultAgentEngine` overrides it with real two-level semantics
- [x] `IAgentEngine.getSessionStatus()` UOE hole is filled: `DefaultAgentEngine` overrides it with a session-store query
- [x] Two-level cancel works: graceful completes current tool then stops at the next iteration boundary; forced interrupts immediately
- [x] `AgentExecStatus.cancelled` represents the terminal cancelled state; cancelled ≠ completed ≠ failed
- [x] Session status lifecycle is wired: `running` at start → terminal status (completed/failed/cancelled) at end; `getSessionStatus()` reflects this
- [x] `forkSession` UOE is expected and remains (out of scope — not a regression)
- [x] `SESSION_CANCEL_REQUESTED` and `SESSION_CANCELLED` events are published during the cancel flow
- [x] Focused tests cover: graceful cancel, forced cancel, getSessionStatus (existing + non-existent), no-op cancel, idle session cancel, session status lifecycle, event publication
- [x] End-to-end test proves cancel works from `cancelSession()` call through ReAct loop termination to observable `cancelled` result
- [x] No remaining UOE holes for cancel/getSessionStatus in `DefaultAgentEngine`
- [x] Owner docs updated: `01-architecture-baseline.md` §四 + roadmap A5 status
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai-agent -am` passes

## Non-Blocking Follow-ups

- `forkSession()` remains a UOE stub — separate work item, requires VFS session store (Layer 4 concern).
- Cancel behavior for VFS/DB-backed session stores — only `InMemorySessionStore` tested in this plan; VFS stores will need their own cancel flow verification when implemented.
- Cancellation deadline / auto-timeout — can be added as a wrapper around `cancelSession` in a future plan (e.g., schedule `cancelSession(true)` after N seconds).
- Cancel propagation to child/forked sessions — when `forkSession` and multi-agent orchestration are implemented, cancel should cascade to child sessions (Layer 4 concern).

## Closure

Status Note: All three Phases landed. `IAgentEngine.cancelSession()` and `getSessionStatus()` UOE holes are filled with real two-level semantics in `DefaultAgentEngine`; the ReAct loop is cancellation-aware (graceful boundary checks + forced-interrupt handling); `AgentExecStatus.cancelled` is a distinct terminal state; session status lifecycle is wired; cancel events are published; `forkSession` remains an expected UOE stub (out of scope). 400 tests pass (10 new/updated). The plan is closeable.

Closure Audit Evidence:

- Reviewer / Agent: Independent closure-audit subagent (fresh session `ses_13f2ca9c0ffey1N68xZe33Si0e`, not the implementer)
- Audit Session: `ses_13f2ca9c0ffey1N68xZe33Si0e`
- Evidence:
  - **Closure Gates** (11/11 PASS against live code): `cancelSession` real override `DefaultAgentEngine.java:133-158`; `getSessionStatus` real override `:127-131`; graceful cancel-flag check at top of loop `ReActAgentExecutor.java:252-256` AND after tool calls `:483-486`; forced cancel `Thread.interrupt()` `DefaultAgentEngine.java:147` + catch-as-cancellation `ReActAgentExecutor.java:505-516`; `AgentExecStatus.cancelled` exists `AgentExecStatus.java:12`; session status lifecycle wired `DefaultAgentEngine.java:235` (running) + `:244-246` (finally terminal); `forkSession` still UOE `IAgentEngine.java:13-15` (not overridden); `SESSION_CANCEL_REQUESTED`+`SESSION_CANCELLED` defined `AgentEventType.java:26-28` and published (`DefaultAgentEngine.java:164`, `ReActAgentExecutor.java:525`)
  - **Anti-Hollow Check**: PASS — traced end-to-end wiring: `doExecute` creates `ctx` (`:211`) → `CancelHandle(ctx, Thread.currentThread())` registered in `runningExecutions` (`:237-238`) → `cancelSession` retrieves same handle and sets `ctx.setCancelRequested(true)` (`:135-139`) → ReAct loop reads `ctx.isCancelRequested()` (`:253`/`:483`) on the SAME instance → `volatile boolean cancelRequested` (`AgentExecutionContext.java:29`) guarantees cross-thread visibility. No empty method bodies, no silent `continue`, no swallowed exceptions, no TODO/placeholder in new code. `handleCancellation` (`:521-526`) has a real body.
  - **End-to-end test**: PASS — `TestDefaultAgentEngineCancel.cancelGracefulEndToEndStopsAfterCurrentTool` starts execution → cancels from test thread while tool blocks on latch → asserts `result.getStatus()==cancelled` AND `getSessionStatus("graceful-e2e")==cancelled`. `cancelForcedEndToEndInterruptsImmediately` asserts forced yields `cancelled` (not `failed`) + `elapsedMs < 30000`.
  - **No Silent No-Op**: PASS — non-existent session `cancelSession` reaches explicit `return CompletableFuture.completedFuture(null)` (`:157`); non-existent `getSessionStatus` returns explicit `null` (`:130`); idle session explicitly `setStatus(cancelled)` (`:150-154`).
  - **Test re-run**: `./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest='TestDefaultAgentEngineCancel,TestIAgentEngineDefaultMethods'` → `Tests run: 13, Failures: 0, Errors: 0` / BUILD SUCCESS. Full module suite: `Tests run: 400, Failures: 0, Errors: 0`.
  - **Plan checklist**: `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/157-nop-ai-agent-actor-cancel.md --strict` exit 0.
  - **Anti-hollow scan**: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` exit 0 (no hollow findings in new code; only pre-existing intentional Phase 2 UOE stubs in `ISessionStore`/`IAiMemoryStore` which are out of scope).
  - **Deferred item classification**: No in-scope live defect deferred. All Non-Blocking Follow-ups (`forkSession`, VFS cancel, deadline/timeout, multi-agent cascade) are out-of-scope future work tied to unimplemented Layer 4 capabilities, not regressions.
  - **Doc-link checker**: owner-doc edits (`01-architecture-baseline.md`, `nop-ai-agent-roadmap.md`) and log entry introduced 0 new broken links. The 6 flagged links in this plan file are pre-existing `Targets:`-line code references (same pattern as sibling plans 140/141/150/152); the remaining broken links are pre-existing in untouched files across the repo.

Follow-up:

- No remaining plan-owned work. All Non-Blocking Follow-ups (`forkSession` UOE, VFS/DB cancel, cancel deadline/timeout, multi-agent cancel cascade) are explicitly out-of-scope Layer 4 concerns documented above.
