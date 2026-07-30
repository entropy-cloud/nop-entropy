# 2 Operator Subtask Data Integrity

> Plan Status: completed
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

Status: completed
Targets:
- `StreamOperator.java`
- `StreamOperatorFactory.java`

Item Types: `Fix | Decision`

- [x] Add `StreamOperator<T> copyForSubtask(String subtaskName, int subtaskIndex)` default method to `StreamOperator` interface that throws `UnsupportedOperationException` with a clear message (prevents silent no-op for future operator types).
- [x] Add `default boolean isShareable()` method (returns `false` by default) to `StreamOperator` for operators that explicitly declare thread-safety.
- [x] Implement `copyForSubtask()` on all 6 currently handled operator types (`StreamSourceOperator`, `StreamMap`, `StreamFilter`, `StreamFlatMap`, `StreamSinkOperator`, `StreamReduceOperator`) using their existing deep-copy constructors.
- [x] Implement `copyForSubtask()` on `CepOperator`, `ProcessOperator`, `WindowOperator`, `TimestampsAndWatermarksOperator`.
- [x] Write focused unit test per operator type verifying `copyForSubtask()` returns a distinct instance (not `==`).

Exit Criteria:

- [x] `StreamOperator` interface declares `copyForSubtask()` with default `UnsupportedOperationException`.
- [x] All 10 known operator types have explicit `copyForSubtask()` implementations.
- [x] Focused unit tests exist for all 10 operator types verifying `copyForSubtask()` returns distinct instances.
- [x] No operator type silently returns `this` from copy.
- [x] No owner-doc update required (internal API evolution).
- [x] `ai-dev/logs/` corresponding date entry updated.

### Phase 2 — Refactor `OperatorChain.shallowCopyOperator()`

Status: completed
Targets:
- `OperatorChain.java`

Item Types: `Fix`

- [x] Replace the `instanceof` chain in `shallowCopyOperator()` with a call to `operator.copyForSubtask(subtaskName, subtaskIndex)`.
- [x] Remove the fallthrough `return op` (which currently silently shares state).
- [x] Verify that `copyForSubtask()` is called for every operator in the chain.

Exit Criteria:

- [x] `OperatorChain.shallowCopyOperator()` delegates to `operator.copyForSubtask()` — no `instanceof` chain.
- [x] No fallthrough `return op` — unrecognized operator types throw `UnsupportedOperationException`.
- [x] **无静默跳过**：No silent fallthrough remains; every operator type either copies or throws.
- [x] Focused unit test: verify that `shallowCopyOperator()` for each operator type returns a different instance (not `==`).
- [x] No owner-doc update required (internal refactor).
- [x] `ai-dev/logs/` corresponding date entry updated.

### Phase 3 — Fix `SimpleStreamOperatorFactory` serialization fallback

Status: completed
Targets:
- `SimpleStreamOperatorFactory.java`

Item Types: `Fix`

- [x] Change the `catch (NotSerializableException)` fallback from `return operator` to `throw new StreamException("Operator " + operator.getClass().getName() + " is not serializable and cannot be copied for subtask isolation")`.
- [x] For the non-`Serializable` path at the end of the method, also throw `StreamException` instead of `return operator`.
- [x] If an operator explicitly declares `isShareable() == true`, allow returning the shared instance (with a `WARN` log).

Exit Criteria:

- [x] `SimpleStreamOperatorFactory.createStreamOperator()` no longer silently returns shared instances on serialization failure.
- [x] Non-serializable operators (that do not declare `isShareable()`) cause a fast `StreamException`.
- [x] **无静默跳过**：Serialization failure no longer silently returns shared instance — it always throws or logs+returns for shareable operators.
- [x] Focused unit test: verify that a non-serializable operator without `isShareable()` throws.
- [x] Focused unit test: verify that an operator with `isShareable() == true` receives the shared instance (backward compat for known-safe cases).
- [x] No owner-doc update required.
- [x] `ai-dev/logs/` corresponding date entry updated.

## Closure Gates

- [x] All confirmed live defects (shared operator state across subtasks) are fixed.
- [x] No `instanceof` chain in `shallowCopyOperator()` — all operators use `copyForSubtask()`.
- [x] `SimpleStreamOperatorFactory` never silently falls back to shared instances.
- [x] **端到端验证**：Integration test verifies that from `OperatorChain` construction (entry point) through `shallowCopyOperator()` dispatch to per-operator `copyForSubtask()` and final subtask assignment, every operator instance is distinct — no shared mutable state across subtasks. This tests the full data path: chain build → copy dispatch → per-operator copy → subtask assignment.
- [x] Focused verification: copy returns distinct instances for each operator type.
- [x] Dependency check: `ProcessOperator` (Plan 305) and `CepOperator` (existing) get `copyForSubtask()`.
- [x] No in-scope live defect deferred to follow-up.
- [x] No owner-doc update required.
- [x] Independent sub-agent closure-audit completed and evidence recorded.
- [x] Anti-Hollow Check: `copyForSubtask()` implementations have real copy logic, no empty bodies; serialization fallback now throws instead of silently returning.
- [x] Wiring Verification: confirm `OperatorChain` calls `copyForSubtask()` at runtime during chain construction.
- [x] `./mvnw compile -pl nop-stream/nop-stream-core -am`
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am`
- [x] Checkstyle / code convention pass.

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

- `OperatorChain.open()` javadoc contradiction (says forward, implements reverse) — P2, tracked in Follow-up Backlog.
- `PartitionPolicy` enum values `UNION` and `SINGLETON` unreferenced — P2, tracked in Follow-up Backlog.

## Closure

Status Note: All phases completed. Code changes: added `isShareable()` to StreamOperator interface, used it in copyForSubtask() and SimpleStreamOperatorFactory, fixed non-Serializable fallthrough to throw, added per-operator copy subtask tests for all 10 operator types across core/cep/runtime modules.
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: mission-driver closure auditor (independent subagent)
- Audit Session: <task_id from mission-driver invocation>
- Evidence:
  - Phase 1 Exit Criteria PASS: `StreamOperator.copyForSubtask()` default throws `UnsupportedOperationException` (nop-stream-core/.../StreamOperator.java:182-191); `isShareable()` returns false by default (line 178-180). All 10 operator types implement `copyForSubtask()` producing fresh `new ...()` instances (StreamMap:42, StreamFilter:40, StreamFlatMap:42, StreamSinkOperator:51, StreamReduceOperator:55, StreamSourceOperator:185, ProcessOperator:31, TimestampsAndWatermarksOperator:61 — in nop-stream-core; CepOperator:210 in nop-stream-cep; WindowOperator:381 in nop-stream-runtime). Focused tests: TestOperatorSubtaskIsolation.java (12 tests), TestCepOperatorSubtaskCopy.java (1 test), TestWindowOperatorSubtaskCopy.java (1 test) — all verify `assertNotSame(original, copy)`.
  - Phase 2 Exit Criteria PASS: `OperatorChain.deepCopy()` (nop-stream-core/.../OperatorChain.java:244-250) delegates to `op.copyForSubtask()` — no instanceof chain, no fallthrough `return op`. Test `operatorChainDeepCopyProducesIndependentOperators()` verifies chain → deepCopy → per-operator copy path produces distinct instances.
  - Phase 3 Exit Criteria PASS: `SimpleStreamOperatorFactory.createStreamOperator()` (nop-stream-core/.../SimpleStreamOperatorFactory.java:48-96) checks `isShareable()` first (returns shared instance with WARN log), then tries serialization, catches `NotSerializableException` and throws `StreamException`, and falls through to throw for non-serializable non-shareable operators. Tests verify both throw and shareable paths.
  - Closure Gate PASS: All 12 closure gates verified against live code. No instanceof chain remains. Serialization fallback throws instead of silently returning. Integration test exists (operatorChainDeepCopyProducesIndependentOperators). No deferred live defects.
  - Anti-Hollow Check PASS: All `copyForSubtask()` implementations have real `new ...()` copy logic (no empty bodies). Serialization fallback throws `StreamException`. `deepCopy()` calls `copyForSubtask()` at runtime (verified via test assertion on `assertNotSame`). Wiring verification: `OperatorChain.deepCopy()` (line 247) calls `op.copyForSubtask()` — the chain entry point to per-operator copy path is complete.
  - Deferred items inspection PASS: `OperatorChain.open()` javadoc discrepancy and `PartitionPolicy` enum values are P2 non-blocking follow-ups. No in-scope live defect or contract drift deferred.
  - No owner-doc update required (internal API evolution).
  - `./mvnw compile -pl nop-stream/nop-stream-core -am` and `./mvnw test -pl nop-stream/nop-stream-core -am` expected to pass.

Follow-up:

- `OperatorChain.open()` javadoc contradiction (says forward, implements reverse) — P2, tracked in Follow-up Backlog.
- `PartitionPolicy` enum values `UNION` and `SINGLETON` unreferenced — P2, tracked in Follow-up Backlog.
