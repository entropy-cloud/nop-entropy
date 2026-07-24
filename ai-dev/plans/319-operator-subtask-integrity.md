# 2 Operator Subtask Data Integrity

> Plan Status: active
> Last Reviewed: 2026-07-25
> Source: `docs/audits/nop-stream-flink-comparison/2026-07-24-2227-open-audit-nop-stream-flink-comparison.md` — findings AR-1 and AR-4
> Related: `318-distributed-comparison.md`

## Purpose

Eliminate two sources of shared mutable operator state across parallel subtasks: the `OperatorChain.deepCopy()` instanceof chain that silently returns the same instance for unhandled operator types, and the `SimpleStreamOperatorFactory.createStreamOperator()` serialization fallback that returns a shared template instance.

## Current Baseline

- `OperatorChain.shallowCopyOperator()` handles 6 known operator types with proper deep-copy constructors. Any other type (e.g., `CepOperator`, `ProcessOperator`, `WindowOperator`, `TimestampsAndWatermarksOperator`, user-defined operators) falls through to `return op` — sharing the same mutable instance across all subtasks.
- `SimpleStreamOperatorFactory.createStreamOperator()` attempts serialization-based deep copy. When `NotSerializableException` occurs (e.g., for lambdas), it silently returns the shared template instance. Non-`Serializable` operators also return the shared instance.
- Multi-subtask pipelines (parallelism > 1) using unhandled operator types will corrupt state across subtasks: race conditions, double-processing, checkpoint corruption.
- Unit tests commonly use `parallelism=1`, masking these issues.

## Goals

- Every `StreamOperator` instance is independently deep-copied for each subtask — no shared mutable state.
- `SimpleStreamOperatorFactory` does not silently fall back to shared instances on serialization failure.
- All existing operator types (`CepOperator`, `ProcessOperator`, `WindowOperator`, `TimestampsAndWatermarksOperator`) are handled.

## Non-Goals

- Thread-safe operator design (operators are expected to be single-threaded per subtask; the fix prevents cross-subtask sharing).
- Performance optimization of the copy mechanism (e.g., Cloner library evaluation).
- Changes to `ChainingOutput` or other non-operator chain components.

## Scope

### In Scope

- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SimpleStreamOperatorFactory.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamOperator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/StreamOperatorFactory.java`
- Any operator-type file where a `copyForSubtask()` method is added.

### Out Of Scope

- `OperatorChain.open()` javadoc vs implementation discrepancy (tracked in Follow-up Backlog — P2).
- Other operator lifecycle issues.

## Execution Plan

### Phase 1 — Add `copyForSubtask()` interface method

Status: planned
Targets:
- `StreamOperator.java`
- `StreamOperatorFactory.java`

Item Types: `Fix | Decision`

- [ ] Add `StreamOperator<T> copyForSubtask(String subtaskName, int subtaskIndex)` default method to `StreamOperator` interface that throws `UnsupportedOperationException` with a clear message (prevents silent no-op for future operator types).
- [ ] Add `default boolean isShareable()` method (returns `false` by default) to `StreamOperator` for operators that explicitly declare thread-safety.
- [ ] Implement `copyForSubtask()` on all 6 currently handled operator types (`StreamSourceOperator`, `StreamMap`, `StreamFilter`, `StreamFlatMap`, `StreamSinkOperator`, `StreamReduceOperator`) using their existing deep-copy constructors.
- [ ] Implement `copyForSubtask()` on `CepOperator`, `ProcessOperator`, `WindowOperator`, `TimestampsAndWatermarksOperator`.
- [ ] Write focused unit test per operator type verifying `copyForSubtask()` returns a distinct instance (not `==`).

Exit Criteria:

- [ ] `StreamOperator` interface declares `copyForSubtask()` with default `UnsupportedOperationException`.
- [ ] All 10 known operator types have explicit `copyForSubtask()` implementations.
- [ ] Focused unit tests exist for all 10 operator types verifying `copyForSubtask()` returns distinct instances.
- [ ] No operator type silently returns `this` from copy.
- [ ] No owner-doc update required (internal API evolution).
- [ ] `ai-dev/logs/` corresponding date entry updated.

### Phase 2 — Refactor `OperatorChain.shallowCopyOperator()`

Status: planned
Targets:
- `OperatorChain.java`

Item Types: `Fix`

- [ ] Replace the `instanceof` chain in `shallowCopyOperator()` with a call to `operator.copyForSubtask(subtaskName, subtaskIndex)`.
- [ ] Remove the fallthrough `return op` (which currently silently shares state).
- [ ] Verify that `copyForSubtask()` is called for every operator in the chain.

Exit Criteria:

- [ ] `OperatorChain.shallowCopyOperator()` delegates to `operator.copyForSubtask()` — no `instanceof` chain.
- [ ] No fallthrough `return op` — unrecognized operator types throw `UnsupportedOperationException`.
- [ ] **无静默跳过**：No silent fallthrough remains; every operator type either copies or throws.
- [ ] Focused unit test: verify that `shallowCopyOperator()` for each operator type returns a different instance (not `==`).
- [ ] No owner-doc update required (internal refactor).
- [ ] `ai-dev/logs/` corresponding date entry updated.

### Phase 3 — Fix `SimpleStreamOperatorFactory` serialization fallback

Status: planned
Targets:
- `SimpleStreamOperatorFactory.java`

Item Types: `Fix`

- [ ] Change the `catch (NotSerializableException)` fallback from `return operator` to `throw new StreamException("Operator " + operator.getClass().getName() + " is not serializable and cannot be copied for subtask isolation")`.
- [ ] For the non-`Serializable` path at the end of the method, also throw `StreamException` instead of `return operator`.
- [ ] If an operator explicitly declares `isShareable() == true`, allow returning the shared instance (with a `WARN` log).

Exit Criteria:

- [ ] `SimpleStreamOperatorFactory.createStreamOperator()` no longer silently returns shared instances on serialization failure.
- [ ] Non-serializable operators (that do not declare `isShareable()`) cause a fast `StreamException`.
- [ ] **无静默跳过**：Serialization failure no longer silently returns shared instance — it always throws or logs+returns for shareable operators.
- [ ] Focused unit test: verify that a non-serializable operator without `isShareable()` throws.
- [ ] Focused unit test: verify that an operator with `isShareable() == true` receives the shared instance (backward compat for known-safe cases).
- [ ] No owner-doc update required.
- [ ] `ai-dev/logs/` corresponding date entry updated.

## Closure Gates

- [ ] All confirmed live defects (shared operator state across subtasks) are fixed.
- [ ] No `instanceof` chain in `shallowCopyOperator()` — all operators use `copyForSubtask()`.
- [ ] `SimpleStreamOperatorFactory` never silently falls back to shared instances.
- [ ] **端到端验证**：Integration test verifies that from `OperatorChain` construction (entry point) through `shallowCopyOperator()` dispatch to per-operator `copyForSubtask()` and final subtask assignment, every operator instance is distinct — no shared mutable state across subtasks. This tests the full data path: chain build → copy dispatch → per-operator copy → subtask assignment.
- [ ] Focused verification: copy returns distinct instances for each operator type.
- [ ] Dependency check: `ProcessOperator` (Plan 305) and `CepOperator` (existing) get `copyForSubtask()`.
- [ ] No in-scope live defect deferred to follow-up.
- [ ] No owner-doc update required.
- [ ] Independent sub-agent closure-audit completed and evidence recorded.
- [ ] Anti-Hollow Check: `copyForSubtask()` implementations have real copy logic, no empty bodies; serialization fallback now throws instead of silently returning.
- [ ] Wiring Verification: confirm `OperatorChain` calls `copyForSubtask()` at runtime during chain construction.
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am`
- [ ] Checkstyle / code convention pass.

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

- `OperatorChain.open()` javadoc contradiction (says forward, implements reverse) — P2, tracked in Follow-up Backlog.
- `PartitionPolicy` enum values `UNION` and `SINGLETON` unreferenced — P2, tracked in Follow-up Backlog.

## Closure

Status Note: *To be filled on completion.*
Completed:
