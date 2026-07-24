# 12a Operator State Basic

> Plan Status: active
> Plan Type: implementation
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 12a
> Last Reviewed: 2026-07-25
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 12a; `ai-dev/analysis/nop-stream/08-gap-analysis.md` gaps G8, G10, G11, G13; live code audit 2026-07-25
> Related: `2026-07-25-1600-2-operator-state-redistribution.md` (successor — depends on this plan's `IOperatorStateBackend` and operator state snapshot/restore pipeline)

## Purpose

Implement the Operator State infrastructure missing in nop-stream: `IOperatorStateStore` interface, `IOperatorStateBackend` with default memory-backed implementation, `IStateBackend.createOperatorStateBackend()`, `AbstractStreamOperator.operatorStateBackend` field, and `FunctionInitializationContext.getOperatorStateStore()`. The `ICheckpointedFunction` interface and `AbstractUdfStreamOperator` wiring already exist — the real gap is the typed operator state store behind the initialization context. This enables non-keyed operators (including sources) to persist state across checkpoints via `ICheckpointedFunction`.

## Current Baseline

- `ICheckpointedFunction` (`io.nop.stream.core.common.functions.ICheckpointedFunction`): **exists** with `snapshotState(FunctionSnapshotContext)` and `initializeState(FunctionInitializationContext)`
- `FunctionSnapshotContext` (`io.nop.stream.core.checkpoint`): **exists** as interface with `getCheckpointId()`, `getCheckpointTimestamp()`
- `FunctionInitializationContext` (`io.nop.stream.core.checkpoint`): **exists** as interface with only `isRestored()` — **NO `getOperatorStateStore()` method**
- `ListStateDescriptor<T>` (`io.nop.stream.core.common.state`): **exists** extending `StateDescriptor<T>` — usable directly for `getListState()` calls
- `AbstractUdfStreamOperator.snapshotState()`: **already wired** — creates anonymous `FunctionSnapshotContext` and calls `ICheckpointedFunction.snapshotState()`
- `AbstractUdfStreamOperator.initializeState()`: **partially wired** — creates anonymous `FunctionInitializationContext` that only returns `isRestored()` based on `TaskStateSnapshot.isEmpty()`. No operator state store provided to the user function.
- `AbstractStreamOperator.snapshotState()`: **already handles operator state** via `CheckpointParticipant.saveState()` path (lines 164-174) — merges participant state into `OperatorSnapshotResult.operatorStates`
- `AbstractStreamOperator`: **has** `protected transient IKeyedStateBackend keyedStateBackend` field (line 41), but **NO** `operatorStateBackend` field
- `IStateBackend`: **exists** with `createKeyedStateBackend()` — **NO `createOperatorStateBackend()`**
- `StreamSourceOperator`: **has its own `snapshotState()`/`restoreState()`** — delegates through `super.snapshotState()` and `super.restoreState()` so `AbstractStreamOperator` changes are inherited, but also adds source-specific handling via `ReplayableSourceFunction`/`CheckpointedSourceFunction`
- `IOperatorStateBackend` / `IOperatorStateStore`: **do not exist**
- `OperatorStateStore` interface: does not exist (G8)
- `IOperatorStateBackend` interface: does not exist (G10)
- `IStateBackend.createOperatorStateBackend()`: does not exist (G11)
- `FunctionInitializationContext.getOperatorStateStore()`: missing (G13)

## Goals

- Implement `IOperatorStateStore` interface with `getListState(ListStateDescriptor)`
- Implement `IOperatorStateBackend` extending `IOperatorStateStore` with `snapshotState()`/`restoreState()`
- Implement `MemoryOperatorStateBackend` — default in-memory backend using `JsonTool` serialization
- Add `protected transient IOperatorStateBackend operatorStateBackend` field to `AbstractStreamOperator`
- Add `createOperatorStateBackend()` to `IStateBackend`, implement in `MemoryStateBackend`
- Add `getOperatorStateStore()` to `FunctionInitializationContext`; wire in `AbstractUdfStreamOperator.initializeState()`
- Wire operator state snapshot/restore into `AbstractStreamOperator` checkpoint pipeline alongside existing `CheckpointParticipant` path (with clear decision on coexistence)
- Focused unit tests for operator state lifecycle

## Non-Goals

- Union/broadcast/SPLIT_DISTRIBUTE redistribution modes (Item 12b)
- `BroadcastState` type (Item 12b)
- Source connector integration (Item 12b — uses `ReplayableSourceFunction`/`CheckpointedSourceFunction`, not this plan's `IOperatorStateStore`)
- RocksDB state backend
- `TypeSerializer` abstraction (Item 13 — `JsonTool` used in this plan)

## Scope

### In Scope

- **IOperatorStateStore**: interface with `getListState(ListStateDescriptor)`. Each named list state stored in insertion order, supports `add()`, `get()`, `clear()`. Single-parallelism only.
- **IOperatorStateBackend**: extends `IOperatorStateStore`, adds `snapshotState()` returning `OperatorSnapshotResult` and `restoreState(OperatorSnapshotResult)`. This is the backend contract for pluggable operator state backends.
- **MemoryOperatorStateBackend**: maps named states to `List<Object>`. `snapshotState()` serializes entries into `OperatorSnapshotResult.operatorStates` via `JsonTool`. `restoreState()` deserializes back. Used by `FunctionInitializationContext.getOperatorStateStore()` as the default store.
- **AbstractStreamOperator.operatorStateBackend field**: `protected transient IOperatorStateBackend operatorStateBackend`. Created in `open()` via `stateBackend.createOperatorStateBackend()`. The `CheckpointParticipant.saveState()` path remains as-is for now (coexistence decision: operator state backend handles `ICheckpointedFunction` user functions; `CheckpointParticipant` handles framework-level operator state).
- **IStateBackend.createOperatorStateBackend()**: default throws `UnsupportedOperationException`. `MemoryStateBackend` returns `MemoryOperatorStateBackend`.
- **FunctionInitializationContext.getOperatorStateStore()**: returns the `IOperatorStateBackend` instance. Wired in `AbstractUdfStreamOperator.initializeState()`.
- **Focused unit tests**:
  - `IOperatorStateStore` list state: add, get, iteration, clear
  - `MemoryOperatorStateBackend.snapshotState()` → `OperatorSnapshotResult` → `restoreState()` round-trip
  - `AbstractUdfStreamOperator` with `ICheckpointedFunction` → `initializeState()` → `getOperatorStateStore()` returns working store

### Out Of Scope

- Redistribution modes (12b)
- Source connector integration (12b — separate interface hierarchy)
- `TypeSerializer` abstraction (13)

## Execution Plan

### Phase 1 — IOperatorStateStore + IOperatorStateBackend + MemoryOperatorStateBackend

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/` (IOperatorStateStore, IOperatorStateBackend)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/` (MemoryOperatorStateBackend)

Item Types: `Fix`

- [ ] `Fix` Create `IOperatorStateStore` interface with `getListState(ListStateDescriptor)`. Signature: `<T> ListState<T> getListState(ListStateDescriptor<T> descriptor)`.
- [ ] `Fix` Create `IOperatorStateBackend` interface extending `IOperatorStateStore`: add `OperatorSnapshotResult snapshotState()` and `void restoreState(OperatorSnapshotResult result)`.
- [ ] `Fix` Create `MemoryOperatorStateBackend` implementing `IOperatorStateBackend`: stores `Map<String, ListState<Object>>`. `snapshotState()` serializes via `JsonTool` to `OperatorSnapshotResult.operatorStates`. `restoreState()` deserializes back. Uses `MemoryArrayListState` (internal) for list state.
- [ ] `Fix` Verify `ListState` interface methods: `add(T)`, `addAll(Iterable<T>)`, `get()` (returns `Iterable<T>`), `update(Iterable<T>)`, `clear()`. Implement all in `MemoryArrayListState`.
- [ ] Add focused test: `MemoryOperatorStateBackend.snapshotState()` → `restoreState()` round-trip preserves all entries and order
- [ ] Add focused test: multiple named list states coexist independently
- [ ] Add focused test: empty state → snapshot → restore → state is empty

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `IOperatorStateStore.getListState(ListStateDescriptor)` compiles and returns typed `ListState<T>`
- [ ] `IOperatorStateBackend` has `snapshotState()` and `restoreState()`
- [ ] `MemoryOperatorStateBackend` round-trip test passes (content equality pre/post checkpoint)
- [ ] `ListState` implementations support `add()`, `addAll()`, `get()`, `update()`, `clear()`
- [ ] **No Silent No-Op**: all methods are implemented — no empty defaults
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — AbstractStreamOperator wiring + FunctionInitializationContext

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractUdfStreamOperator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/IStateBackend.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateBackend.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/FunctionInitializationContext.java`

Item Types: `Fix | Decision | Proof`

- [ ] `Decision` Coexistence with `CheckpointParticipant.saveState()`: the existing `AbstractStreamOperator.snapshotState()` lines 164-174 merge `CheckpointParticipant.saveState()` into `OperatorSnapshotResult`. The new `operatorStateBackend.snapshotState()` also writes operator state. Decision: Keep both paths. `CheckpointParticipant` handles framework-level state (internal operators); `IOperatorStateBackend` handles user-state from `ICheckpointedFunction` functions. Document that each operator uses only one path.
- [ ] `Fix` Add `protected transient IOperatorStateBackend operatorStateBackend` field to `AbstractStreamOperator`.
- [ ] `Fix` In `AbstractStreamOperator.open()`: call `stateBackend.createOperatorStateBackend()` and assign to `operatorStateBackend`. Guard against null `stateBackend`.
- [ ] `Fix` In `AbstractStreamOperator.snapshotState()`: if `operatorStateBackend != null`, call `operatorStateBackend.snapshotState()` and merge result into the existing `OperatorSnapshotResult`.
- [ ] `Fix` In `AbstractStreamOperator.applyPendingRestoreState()` or `restoreState()` equivalent: if `operatorStateBackend != null` and snapshot result has operator states, call `operatorStateBackend.restoreState(result)`.
- [ ] `Fix` Add `IOperatorStateStore getOperatorStateStore()` to `FunctionInitializationContext` interface.
- [ ] `Fix` Update `AbstractUdfStreamOperator.initializeState()`: inject `operatorStateBackend` into the anonymous `FunctionInitializationContext` as the `getOperatorStateStore()` return value. The field is inherited from `AbstractStreamOperator`.
- [ ] `Fix` Add `IOperatorStateBackend createOperatorStateBackend()` to `IStateBackend`. Default: `throw new UnsupportedOperationException("createOperatorStateBackend not implemented")`.
- [ ] `Fix` Implement `MemoryStateBackend.createOperatorStateBackend()` returning `MemoryOperatorStateBackend`.
- [ ] Add focused test: `AbstractUdfStreamOperator` with `ICheckpointedFunction` → `initializeState()` → `getOperatorStateStore()` returns non-null store
- [ ] Add focused test: operator with `ICheckpointedFunction` → write to operator state → snapshot → restore → state contents match

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `AbstractStreamOperator` has `operatorStateBackend` field, created in `open()` from `stateBackend`
- [ ] `AbstractStreamOperator.snapshotState()` includes operator state backend output in result
- [ ] `AbstractStreamOperator.restoreState()` restores operator state from snapshot
- [ ] `FunctionInitializationContext.getOperatorStateStore()` returns the operator state backend
- [ ] `AbstractUdfStreamOperator.initializeState()` provides operator state store via `FunctionInitializationContext`
- [ ] `IStateBackend.createOperatorStateBackend()` exists; throws on unsupported backends
- [ ] `MemoryStateBackend.createOperatorStateBackend()` returns working `MemoryOperatorStateBackend`
- [ ] **接线验证**: `AbstractStreamOperator.open()` → `stateBackend.createOperatorStateBackend()` → `FunctionInitializationContext.getOperatorStateStore()` → `ICheckpointedFunction.initializeState()` receives it
- [ ] Operator state backend created in `AbstractStreamOperator.open()` (not a non-existent `setup()` — `open()` is the lifecycle hook)
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 3 — Integration test + completeness

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/test/`

Item Types: `Proof`

- [ ] Create integration test: operator implementing `ICheckpointedFunction` with `MemoryOperatorStateBackend` → list state accumulates entries across multiple `processElement()` calls → `snapshotState()` via checkpoint pipeline → `restoreState()` recovers all entries
- [ ] Test scenario: operator with keyed state AND operator state → both survive checkpoint/restore
- [ ] Test scenario: `CheckpointParticipant` operator state co-exists with new `IOperatorStateBackend` without conflict

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Integration test: `ICheckpointedFunction` operator with list state → checkpoint → restore → state contents match
- [ ] Integration test: keyed state + operator state both survive checkpoint/restore
- [ ] Integration test: `CheckpointParticipant` operator state and `IOperatorStateBackend` state coexist in `OperatorSnapshotResult`
- [ ] **Anti-Hollow Check**: operator state actually serialized/deserialized through checkpoint pipeline (verified by asserting state content equality post-restore)
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` passes
- [ ] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [ ] All in-scope gaps addressed: G8 (`IOperatorStateStore`), G10 (`IOperatorStateBackend`), G11 (`createOperatorStateBackend()`), G13 (`getOperatorStateStore()`)
- [ ] `ICheckpointedFunction` user functions can persist operator state through checkpoint/restore
- [ ] `MemoryOperatorStateBackend` serializes/deserializes correctly through `OperatorSnapshotResult`
- [ ] `AbstractStreamOperator.snapshotState()` includes operator state backend output (coexists with `CheckpointParticipant` path)
- [ ] No in-scope live defect or contract drift deferred to follow-up
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] **Anti-Hollow Check**: operator state backend produces/consumes data through checkpoint pipeline — E2E test verifies state content equality
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0

## Deferred But Adjudicated

### Union/broadcast/SPLIT_DISTRIBUTE redistribution

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: This plan provides single-parallelism operator state. Redistribution is Item 12b.
- Successor Required: `yes` (Item 12b)

### TypeSerializer bridge

- Classification: `optimization candidate`
- Why Not Blocking Closure: `JsonTool` serialization is consistent with existing pattern. `TypeSerializer` bridge is Item 13.
- Successor Required: `yes` (Item 13)

## Non-Blocking Follow-ups

- (none at draft time)

## Closure

Status Note: <<filled on completion>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<independent reviewer>>
- Evidence: <<PASS/FAIL results for each exit criterion and closure gate>>
