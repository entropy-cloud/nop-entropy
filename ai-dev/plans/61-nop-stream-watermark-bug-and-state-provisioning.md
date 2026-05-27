# Plan 61: nop-stream Watermark Bug Fix and State Backend Provisioning

> Plan Status: **completed**
> Created: 2026-05-27
> Module: `nop-stream`
> Source: Phase B adversarial review

## Purpose

Two related correctness issues found during adversarial review: (1) a watermark initialization bug that silently corrupts state after checkpoint restore, and (2) missing state backend provisioning in the checkpoint executor that causes operators to silently fall back to broken in-memory state.

## Goals

1. **Fix currentWatermark == 0 bug**: `WindowAggregationOperator.open()` overwrites a legitimately restored watermark value of 0 to `Long.MIN_VALUE` because it can't distinguish "unset" from "legitimately zero". Use a boolean `watermarkInitialized` flag instead.
2. **Provision state backend in GraphModelCheckpointExecutor**: When checkpointing is enabled, create and set a `MemoryStateBackend` on operators that support it, so `CepOperator` and future operators get proper keyed state backends instead of falling back to `SimpleKeyedStateStore`.

## Non-Goals

- Wiring runtime `WindowOperator` into DataStream API (needs WindowOperatorFactory pattern, separate plan)
- Fixing `CheckpointCoordinator` finishCommit failure handling (needs design review)
- Converting remaining `IllegalStateException` to `ErrorCode` (follow-up from Plan 60)

## Current Baseline

- Build and tests pass: 306 tests, 0 failures ✅
- `WindowAggregationOperator.open()` line 78: `if (this.currentWatermark == 0) this.currentWatermark = Long.MIN_VALUE;`
- `GraphModelCheckpointExecutor` never calls `setStateBackend()` or `setKeyedStateBackend()` on operators
- `CepOperator` falls back to `SimpleKeyedStateStore` when no backend available — no key scoping

## Implementation Notes

### Watermark Fix

Replace `if (this.currentWatermark == 0)` with a `boolean watermarkInitialized` field:
- Field defaults to `false` (set to `true` in `open()` after init, and set to `true` in `restoreState()` after restore)
- `open()` checks `if (!watermarkInitialized)` instead of `if (this.currentWatermark == 0)`

### State Backend Provisioning

In `GraphModelCheckpointExecutor`, after creating invokables but before running tasks:
- Create a `MemoryStateBackend` (the only available implementation)
- For each operator chain in each invokable, if the operator has `stateBackend == null`, set it
- This ensures `CepOperator.open()` finds `this.stateBackend != null` and creates a proper `IKeyedStateBackend`

## Execution Slices

### Slice 1: Watermark bug fix
> Status: completed

- [x] Add `watermarkInitialized` boolean to `WindowAggregationOperator`
- [x] Replace `== 0` check with `!watermarkInitialized` in `open()`
- [x] Set `watermarkInitialized = true` in `open()` and `restoreState()`
- [x] Add test: restore from snapshot with `currentWatermark == 0`, verify it's preserved after `open()`
- [x] Verify: `./mvnw test -pl nop-stream -am -T 1C`

### Slice 2: State backend provisioning
> Status: completed

- [x] Add state backend provisioning in `GraphModelCheckpointExecutor`
- [x] Verify CepOperator gets keyed state backend in checkpoint-enabled execution
- [x] Add test: CepOperator with multiple keys through checkpoint path
- [x] Verify: `./mvnw test -pl nop-stream -am -T 1C`

## Exit Criteria

- [x] `currentWatermark == 0` no longer overwritten after checkpoint restore
- [x] `watermarkInitialized` flag used instead of value check
- [x] `GraphModelCheckpointExecutor` provisions state backends to operator chains
- [x] CepOperator receives `IKeyedStateBackend` in checkpoint-enabled execution
- [x] New tests added for both fixes
- [x] `./mvnw test -pl nop-stream -am -T 1C` passes (306+ tests, 0 failures)

## Closure Gates

- [x] All Exit Criteria items checked
- [x] Independent closure audit confirms fixes

**Evidence**: watermarkInitialized field exists and works. State backend pre-provisioning verified. Commit 089e79849.

Closure Audit Evidence (retroactive):

- Reviewer / Agent: Retrospective code audit via git history
- Evidence: All checklist items confirmed complete. Plan status verified consistent with codebase state.
