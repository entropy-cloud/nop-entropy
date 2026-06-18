# nop-ai-agent Multi-Member Per-Task Routing

> Status: final design (post-implementation)
> Last Reviewed: 2026-06-18
> Source: plan 244 (`L4-multi-member-per-task-routing`)

## Purpose

This document records the architectural decisions behind nop-ai-agent's
**multi-member per-task routing** primitive (plan 244): the ability for a
single team-task graph node to fan out to **N** member agents (bound
and/or spawned) concurrently and reduce their results into a single task
outcome. It captures the 8 pre-adjudicated design decisions and the
rejected alternatives, in their final landed state.

This is a design doc, not a plan or code reference. For execution steps
and exit criteria see plan 244. For class signatures and method lists see
the source code.

## Context

Prior to plan 244, the team-task DAG execution pipeline (plans 233/237/238/
241/243) hardcoded a "one task = one member" assumption: each graph node
delegated to a single bound member (`MemberAgentTaskStep`) or spawned a
single member (`SpawnMemberAgentTaskStep`). A task could not be distributed
across multiple members. Plan 244 introduces a pluggable **per-task member
router** that lifts this to "one task = N members + reduction", with the
shipped default reproducing the pre-244 single-member behaviour line-for-
line (zero regression).

## Design Decisions

### 1. Semantic model = fan-out (replicate) + reduction, not partitioning / pipeline

The same task is replicated and dispatched to N members concurrently; N
results are reduced to a single task outcome. **Rejected**: partitioning
(splitting a task into N sub-tasks — requires task-splittability semantics
and sub-result joining contracts, an independent result surface) and
pipeline (N members process a task sequentially — requires member ordering
primitives, an independent result surface). Fan-out + reduction is the
minimal, universally-applicable model: any task can be replicated.

### 2. Single per-task routing extension point covering both halves (bound + spawn)

A **single** `ITaskMemberRouter` extension point decides, per task, which N
targets to dispatch to. Each `DispatchTarget` in the returned
`MemberDispatchPlan` is tagged BOUND (delegate to an already-bound session
via `IAgentEngine.execute`) or SPAWN (spawn a fresh member at node run time
via `IMemberSpawner.spawnMember`). **Rejected**: a dual router (one per
half) — doubles wiring complexity for no benefit. **Rejected**: changing
`resolveMember` / `resolveSpawnTarget` to return Lists — breaks the bound-
priority single-member regression invariant (plans 233/238 tests assert the
single-member resolution path line-for-line). The shipped
`NoOpTaskMemberRouter` default reproduces the pre-244 single-member
resolution exactly (bound priority + spawn fallback), wrapped as a
singleton plan, so single-member teams behave identically to plans
233/238/241/243.

The router runs at **graph build time**, non-executing: it never calls the
engine nor the spawner. Its job is a build-time selection over public team
data (bound roster + declarative memberSpecs). Actual execution is deferred
to node run time inside the fan-out steps, preserving DAG dependency order
(plan 238 decision 1, unchanged).

### 3. Reduction shipped default = all-must-succeed (strictest, most honest)

The `IReductionStrategy` extension point is pluggable; the shipped
`AllMustSucceedReduction` requires all N members to reach
`AgentExecStatus.completed`. Any failure (engine exception, non-completed
status, NO_SPAWN, SPAWN_FAILED, spawner null/throws, complete-CAS loss)
fast-fails the node and leaves the task CLAIMED. **Rejected** (as shipped
defaults): quorum / majority / first-wins — these are strictness-lowering
optimizations, opt-in extension points for successor plans. The strictest
default avoids the "partial failure silently succeeds" hollow risk
(No Silent No-Op #24).

The task's CLAIMED → COMPLETED transition happens **once** for the whole
node, after all N members complete successfully — not per member. The
`completeTask` CAS is owned by the fan-out step (not the reduction
strategy), so the strategy's job is purely a yes/no decision over already-
collected outcomes.

### 4. Claim synchronous, single; complete after all N members succeed, single

The CREATED → CLAIMED transition happens synchronously at node-trigger time
(preserving DAG dependency order + claim CAS loss synchronous fast-fail +
already-COMPLETED idempotent synchronous success). The N member executions
run concurrently (bound via engine futures, spawn via supplyAsync). The
single CLAIMED → COMPLETED transition fires only after all N members
complete. **Rationale**: claim is a task-slot acquisition semantic (not
per-member); single claim/complete keeps the `ITeamTaskStore` state machine
unchanged and reuses the existing CAS semantics.

### 5. Fan-out first-failure fast-fail; in-flight members not cancelled (run-to-completion, results discarded)

Any member failure completes the reduced future exceptionally
(`CompletableFuture` composition's natural semantics). Other in-flight
members continue to completion; their results are discarded. **Rejected**
(for v1): in-flight cancellation — requires `IAgentEngine.cancelSession`
integration + cancellation propagation, an independent result surface
(plan 244 Non-Goal). Discarded results do not break correctness (the task
has honestly failed; the claim/complete state machine is correct);
resource waste is a known v1 limitation (a reduction-extension successor
may introduce cancellation).

### 6. Spawn fan-out = build-time target selection + N `spawnMember` calls carrying each target + supplyAsync composition; `IMemberSpawner` interface signature unchanged; `SpawnMemberRequest` gains an optional additive target field

Spawn target **selection** (build-time, non-executing) is done by the
router from public `Team.getSpec().getMemberSpecs()`. Spawn **execution**
(run-time) is done by the spawn fan-out step: for each selected target, a
`SpawnMemberRequest` carrying that specific target is constructed and
`spawnMember(request)` is called (each call single-target, single-result),
offloaded via `supplyAsync(spawnExecutor)`. The `IMemberSpawner.spawnMember
(SpawnMemberRequest)` **interface signature is unchanged** (daemon plans
236/237 call sites line-for-line zero-regression). `SpawnMemberRequest`
gains an **optional additive target field** (backward compatible: the
existing 3-arg constructor defaults target=null = spawner self-resolves =
daemon path unchanged; a new 4-arg constructor accepts the pre-resolved
target). `DefaultMemberSpawner.spawnMember` uses `request.target` when
non-null, otherwise falls back to `resolveSpawnTarget` (the daemon path
line-for-line zero-regression).

**Rejected alternatives**:
- Changing `IMemberSpawner.spawnMember` to return multi-results / futures —
  breaks daemon plans 236/237 cross-module regression.
- Adding a new method to `IMemberSpawner` — breaks NoOp/Default swap-ability
  (single-method abstraction).
- Bypassing the spawner and calling the engine directly — breaks three-state
  honest interpretation + the extension-point abstraction (plan 243
  decision 5 already rejected this).

### 7. Bound fan-out = N `IAgentEngine.execute(request)` futures composed directly

For N bound members, N `engine.execute` calls (each on its own session,
returning the engine's existing `CompletableFuture`) are composed via
`CompletableFuture.allOf` + reduction. No `supplyAsync` offload is needed
(the engine is already async). Each bound member has an independent
session; no session conflict.

### 8. Router and reduction are wire-at-consumer extension points (null-safe → NoOp shipped defaults)

Both `ITaskMemberRouter` and `IReductionStrategy` follow the established
Layer 4 wire-at-consumer convention (`IMemberSpawner`, `ITeamAclChecker`,
`IResourceGuard`, `IFencingTokenService`, `IDaemonCoordinator`). They are
injected into `TeamTaskFlowOrchestrator` (the consumer) via setters /
constructors, null-safe → NoOp shipped defaults. An orchestrator
constructed without an explicit router / reduction uses the shipped
defaults and behaves line-for-line identically to plans 233/238/241/243.

## Tenant Propagation

The fan-out steps re-apply the caller's captured tenant
(plan 243 design decision 2, explicit-propagation mechanism) around the
single `completeTask` CAS. This is necessary because the reduction +
complete chain runs on whatever thread completed the last member future,
which may have cleared its tenant context (a spawn worker's `finally`).
The shared `FanOutReduceComplete` helper centralizes this: it sets the
tenant before `completeTask` and clears it in `finally`, so DB stores
filter by the caller's tenant inside the CAS.

## Failure Semantics (No Silent No-Op #24)

Every failure path is honest:
- Empty plan (no dispatchable member) → orchestrator throws at build time,
  task stays CREATED, DAG build aborts before any node runs.
- Claim CAS loss → synchronous throw at node-trigger time, task stays in
  its prior state.
- Member engine exception / non-completed status → reduction fast-fails,
  node future completes exceptionally, task stays CLAIMED.
- Spawner NO_SPAWN / SPAWN_FAILED / null / throws → mapped to a failure
  outcome, reduction fast-fails, task stays CLAIMED.
- `completeTask` CAS loss → throw, task stays CLAIMED.
- Already-COMPLETED task → honest idempotent synchronous success (no
  engine invocation, task reported in completed set).

No `continue`, no empty method body, no swallowed exceptions, no TODO-as-
done. The `markFailed` recording happens via a `whenComplete` on the
stepped future so it fires for any exception path (reduction failure, CAS
loss, etc.).

## Non-Goals (Successor Surfaces)

- **Task partitioning** (split a task into N sub-tasks): requires task-
  splittability semantics + sub-result joining. Successor plan required.
- **Member pipeline** (N members process a task sequentially): requires
  member ordering primitives. Successor plan required.
- **Quorum / majority / first-wins reduction**: the extension point is
  reserved; concrete strategies are opt-in successors.
- **In-flight cancellation on first failure**: requires
  `IAgentEngine.cancelSession` + cancellation propagation. Successor plan
  required.
- **Per-task fan-out degree quota dimension**: v1 fan-out degree is
  naturally bounded by existing `TEAM_MEMBERS` /
  `TEAM_PARALLEL_BOUND_MEMBERS` team-member quotas (plan 234). Optimization
  candidate.
- **`TeamTaskSchedulerDaemon` per-task multi-member dispatch**: the daemon
  (plan 236) dispatch path remains single-member; this plan only extends
  the programmatic orchestrator. Successor plan required.
- **Modifying nop-task core / `GraphTaskStep`**: nop-task already provides
  complete async + CompletableFuture scheduling; this plan consumes it.
- **Spawn session reuse / pooling** (plan 239 carry-over): each spawn
  creates a fresh session. Optimization candidate.

## References

- Plan 244: `ai-dev/plans/244-nop-ai-agent-multi-member-per-task-routing.md`
- Plan 233: nop-task DAG integration (bound-member single delegation)
- Plan 237: `IMemberSpawner` auto-spawn extension point
- Plan 238: orchestrator auto-spawn integration
- Plan 241: async team-task orchestration (bound half async)
- Plan 243: spawn step async + dedicated spawn executor + tenant propagation
- `nop-ai-agent-async-team-task-orchestration.md` (async team-task design)
- `nop-ai-agent-orchestrator-auto-spawn.md` (orchestrator auto-spawn design)
