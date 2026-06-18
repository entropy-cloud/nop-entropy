# Daemon Dispatch Parity（TeamTaskSchedulerDaemon 多成员 fan-out + async 派发对齐 orchestrator）

> Status: landed
> Last Reviewed: 2026-06-18
> Owner plan: `ai-dev/plans/245-nop-ai-agent-daemon-multi-member-async-dispatch.md`（Work Item: `L4-daemon-multi-member-async-dispatch`）
> Related: `nop-ai-agent-task-scheduler-daemon.md`（plan 236 交付 daemon 主干——本设计升级其 dispatch 路径）、`nop-ai-agent-multi-member-routing.md`（plan 244 交付 `ITaskMemberRouter` + fan-out + reduction——本设计复用）、`nop-ai-agent-async-team-task-orchestration.md`（plan 241/243 交付 async + dedicated spawn executor + tenant 传播——本设计复用）、`nop-ai-agent-team-task-reclaim.md`（plan 240 交付 reclaim 恢复模型——本设计失败语义对齐）

## Purpose

This document records the architectural decisions behind nop-ai-agent's
**daemon dispatch parity** (plan 245): aligning the unattended dispatch
path (`TeamTaskSchedulerDaemon`, plan 236) with the programmatic
orchestrator (`TeamTaskFlowOrchestrator`, plans 233/241/243/244) at the
per-task dispatch layer — multi-member fan-out + reduction + async +
honest failure + tenant isolation. It captures the pre-adjudicated design
decisions and the rejected alternatives, in their final landed state.

This is a design doc, not a plan or code reference. For execution steps
and exit criteria see plan 245. For class signatures and method lists see
the source code.

## Context

Prior to plan 245, the daemon's per-task dispatch was asymmetric with the
orchestrator:

- **Single-member hardcoded**: the daemon resolved **one** bound member per
  team (`resolveBoundMember`) and delegated each ready task to it. There
  was no per-task routing, no fan-out, no reduction.
- **Synchronous blocking**: the daemon called
  `engine.execute(request).join()`, blocking the scan thread on each task
  until that single member completed.
- **Three-arg spawn constructor**: the daemon used the original
  `SpawnMemberRequest(team, task, daemonSessionId)` (target=null =
  spawner self-resolves a single target).

Meanwhile the orchestrator (plan 244) had already gained multi-member
per-task fan-out + reduction, async per-node dispatch (plan 241/243), a
dedicated spawn executor, and explicit-propagation tenant isolation. The
daemon — the module's "unattended automation" entry — lagged behind on
exactly the dimensions that define modern orchestration parity.

Plan 245 closes this asymmetry by having the daemon **reuse** the
orchestrator's already-landed fan-out / reduction / async / executor /
tenant infrastructure, rather than reimplementing a parallel multi-member
/ async path.

## Design Decisions

### 1. Reuse, do not rewrite — the daemon consumes the same fan-out + reduce + complete chain as the orchestrator

The daemon's per-task dispatch calls a single shared
`MemberFanOutDispatcher` that builds the per-target member futures
(bound → `IAgentEngine.execute` async; spawn → `supplyAsync(spawnMember)`
on the dedicated executor with tenant re-application), reduces them under
the plan's `IReductionStrategy`, and on success performs the single
`completeTask` CAS under the caller's captured tenant. This is the same
chain the orchestrator's fan-out step variants
(`BoundMemberFanOutStep` / `SpawnMemberFanOutStep` / `MixedMemberFanOutStep`)
delegate to.

**Rejected**: a dual-parallel dispatch path (the daemon reimplementing
fan-out / reduction / async alongside the orchestrator's). Dual paths
inevitably drift — the orchestrator would gain a feature and the daemon
would lag, which is exactly the asymmetry plan 245 was created to
eliminate. Sharing one implementation is the structural guarantee that
both paths stay aligned (Anti-Hollow #8/#22).

The three nop-task fan-out step variants were refactored to delegate to
`MemberFanOutDispatcher` (eliminating the prior triplication of the
per-target future builders), so the dispatcher is the single canonical
implementation. Plan 244's orchestrator tests (89 of them) stayed green
through the refactor, confirming behavioral equivalence.

### 2. Async per-cycle dispatch = the scan thread does not block on any single task's completion

The daemon's `scanOnce` fires a task's fan-out future via
`MemberFanOutDispatcher.dispatch(...)` and does **not** call `.join()` on
it. When every underlying member future was already complete at
construction time (a fast test engine returning completed futures, or a
bound-member plan whose engine returned synchronously), the dispatcher's
whole chain — including the single `completeTask` CAS — runs
synchronously, so the outcome is observed and counted inside the scan
(zero regression for the pre-245 synchronous happy path). When any
underlying future is genuinely async (real engine, or a spawn-target
`supplyAsync`), the outcome is not observed at scan-return; the future is
tracked in an in-flight queue (`awaitInFlightDispatches`) for tests and
graceful shutdown to await, and the store transition happens inside the
dispatcher's chain regardless of timing.

**Rejected**: a fire-and-forget model with no in-flight tracking (would
make tests non-deterministic and graceful shutdown lossy). **Rejected**:
re-introducing a synchronous `.join()` for "simplicity" (re-introduces the
blocking the plan was created to remove; plan 241 carry-over).

### 3. Honest failure retains CLAIMED (not abandoned) — aligned with the orchestrator + the plan 240 reclaim model

A fan-out / reduction failure (empty plan / member exception /
non-completed status / spawner NO_SPAWN / SPAWN_FAILED / throws / null /
`completeTask` CAS loss) leaves the task **CLAIMED** (not ABANDONED). The
recovery model is plan 240's reclaim (`CLAIMED → CREATED`), which
re-queues the task for another member to pick up — the same model the
orchestrator already uses (the orchestrator leaves failed nodes CLAIMED,
never abandons them).

This is an **intentional failure-semantics change** from the pre-245
daemon, which abandoned failed tasks (`CLAIMED → ABANDONED`). The change
aligns the daemon with the orchestrator line-for-line and makes failed
tasks recoverable by the existing reclaim daemon rather than terminal.
Existing daemon tests that asserted `ABANDONED` on dispatch failure were
updated to assert `CLAIMED` + the new `failedTasks` / `failedTaskIds`
counters; the happy-path single-member completion behavior is unchanged
(zero regression for success). The `SchedulerScanResult` gained a
`failedTasks` counter (retained-CLAIMED failures); `abandonedTasks` is
kept for backward compatibility and is zero under the fan-out path.

**Rejected**: keeping abandon for the single-member path and retain-
CLAIMED only for multi-member (a dual failure model — hollow, drift-prone,
the exact anti-pattern plan 245 rejects). The retain-CLAIMED model is
uniform across all fan-out failures.

### 4. Claim synchronous, single; complete after reduction success, single

The CREATED → CLAIMED transition happens synchronously at dispatch time
(the daemon claims before firing the fan-out, preserving the existing
per-task claim semantics + CAS-loss-as-claimLost + already-COMPLETED-
idempotent contract). The single CLAIMED → COMPLETED transition fires
only after the reduction succeeds, inside the dispatcher's chain. This
mirrors the orchestrator's claim/complete model (plan 244 decision 4) and
keeps the `ITeamTaskStore` state machine unchanged.

### 5. The NoOp shipped router default reproduces the pre-245 single-member behavior

`NoOpTaskMemberRouter` (the shipped default, wired null-safe into the
daemon) produces a singleton plan: bound priority + spawn fallback, the
same member the pre-245 daemon's `resolveBoundMember` /
`DefaultMemberSpawner.resolveSpawnTarget` pair would have selected. A
single-member plan consumed by the shared dispatcher degenerates to
single-target execution — line-for-line zero regression for the pre-245
single-member happy path. The bound path with an already-complete engine
future completes synchronously within the scan; the spawn path
(`supplyAsync`) is async but the durable store state (CLAIMED on failure,
COMPLETED on success) is identical.

### 6. Dedicated spawn executor + explicit-propagation tenant — reused verbatim from plan 243

The daemon lazily creates / owns a dedicated daemon-thread pool
(`ai-agent-daemon-spawn-worker-N`) independent of the `commonPool`, used
to offload SPAWN targets' synchronous `spawnMember` calls via
`supplyAsync`. This is the same isolation constraint as plan 243
(decision 3): spawn workers synchronously join an engine future inside
`DefaultMemberSpawner.spawnMember`, so sharing the `commonPool` would
stall when concurrent spawn targets ≥ commonPool parallelism. The caller's
tenant is captured once at `scanOnce` entry and re-applied inside each
spawn worker (`set` / `clear` in `finally`) — the explicit-propagation
mechanism from plan 243 (decision 2), which is robust to all dispatch
topologies. The owned pool is released by `stop()`.

### 7. `IMemberSpawner.spawnMember` interface signature is unchanged

The daemon consumes the additive `SpawnMemberRequest` target field (plan
244 decision 6) when a router selects a specific spawn target. The
interface signature `spawnMember(SpawnMemberRequest) → SpawnMemberResult`
is unchanged (plan 244 locked it). The NoOp / Default spawner
implementations are unchanged. Single-member daemon spawn calls
(target=null) are line-for-line zero-regression.

## Tenant Propagation

The fan-out chain re-applies the caller's captured tenant around the
single `completeTask` CAS (inside `MemberFanOutDispatcher`, reusing the
plan 243 mechanism). The daemon captures the tenant once on the scan
thread and passes it to the dispatcher; spawn workers re-apply + clear it
per-worker. Cross-tenant visibility is enforced by the underlying DB
stores' tenant WHERE clauses; the dispatcher does not bypass them.

## Failure Semantics (No Silent No-Op #24)

Every failure path is honest and leaves the task CLAIMED (recoverable via
plan 240 reclaim):
- Empty plan (no dispatchable member) → synchronous honest failure, no
  fan-out fired, task stays CLAIMED.
- Router throws / returns null → synchronous honest failure (contract
  violation), task stays CLAIMED.
- Member engine exception / non-completed status → reduction fast-fails,
  task stays CLAIMED.
- Spawner NO_SPAWN / SPAWN_FAILED / null / throws → mapped to a failure
  outcome, reduction fast-fails, task stays CLAIMED.
- `completeTask` CAS loss → throw, task stays CLAIMED.
- Already-COMPLETED task → honest idempotent synchronous success (no
  engine invocation, task reported in the completed set).

No `continue`, no empty method body, no swallowed exceptions, no TODO-as-
done.

## Non-Goals (Successor Surfaces)

- **Cross-process daemon coordination / nop-job `IJobScheduler`
  integration** (plan 242/240 carry-over): plan 245 delivers single-
  process async dispatch; cluster election + scheduling-replacement is an
  independent successor.
- **team-task claimer-liveness cross-check / `team-task-reclaim` LLM
  tool** (plan 240 carry-over): claim stays time-based; the reclaim tool
  is independent.
- **nop-task decorator (retry/timeout/rate-limit) integration** (plan 236
  carry-over): plan 245 aligns dispatch semantics only.
- **fan-out in-flight cancellation / partitioning / pipeline / quorum-
  majority reduction** (plan 244 carry-over): plan 245 reuses the shipped
  `AllMustSucceedReduction` (first-failure fast-fail + run-to-completion
  results discarded); these remain orchestrator-layer successors the
  daemon inherits naturally once they land.
- **daemon scheduling-policy changes** (per-cycle task selection /
  concurrency model): plan 245 upgrades single-task dispatch semantics
  only; the per-cycle scheduling loop is unchanged.
- **spawn session pooling / runtime graph mutation** (plan 239 carry-over).

## References

- Plan 245: `ai-dev/plans/245-nop-ai-agent-daemon-multi-member-async-dispatch.md`
- Plan 236: `TeamTaskSchedulerDaemon` unattended dispatch backbone
- Plan 244: `ITaskMemberRouter` + fan-out + reduction (`MemberFanOutDispatcher` origin)
- Plan 241: async team-task orchestration
- Plan 243: dedicated spawn executor + explicit-propagation tenant
- Plan 240: team-task reclaim recovery model
- `nop-ai-agent-task-scheduler-daemon.md` (daemon design)
- `nop-ai-agent-multi-member-routing.md` (multi-member routing design)
