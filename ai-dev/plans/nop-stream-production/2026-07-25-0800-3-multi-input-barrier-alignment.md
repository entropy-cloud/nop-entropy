# G4/G7 Barrier Alignment Verification + Documentation Fix

> Plan Status: active
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 16; `ai-dev/analysis/nop-stream/08-gap-analysis.md` G4, G5, G7, G34
> Mission: nop-stream-production
> Work Item: 16
> Related: `ai-dev/design/nop-stream/checkpoint-design.md` §2.2 (ALIGNING lifecycle); Plan `2026-07-25-0800-2-timer-checkpoint-unify` (Stage 15, dependency)

## Purpose

Verify that multi-input barrier alignment (G4) and channel blocking (G7) are correctly and non-hollowly implemented in `InputGate`, correct the roadmap documentation error regarding BarrierAligner, and mark G4/G7 as resolved. G5/G34 (CancelCheckpointMarker data-channel propagation) is deferred — see Deferred section for justification.

## Current Baseline

- **G4 (multi-input barrier alignment) is ALREADY IMPLEMENTED** in `InputGate.java` at `nop-stream-core/.../execution/InputGate.java:57` (441 lines):
  - `handleBarrierNonRecursive()` at line 347 — complete alignment state machine: per-channel `barrierReceived[]` tracking, first-barrier triggers `blockConsumption(channelIndex)`, all-barriers-complete triggers `resumeConsumptionAll()` and returns aligned barrier, alignment timeout throws `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT`, overlapping barriers throw abort exception.
  - Supports both `STRICT_EXACTLY_ONCE` (aligned, `barrierAlignment=true`) and `AT_LEAST_ONCE` (non-aligned, `barrierAlignment=false`) modes via `EdgeConfig`.
  - Wired into `GraphExecutionPlan.java:285` (`nop-stream-core`) via `new InputGate(channels, gateConfig, barrierAlignment, barrierAlignmentTimeout)`.
  - `RemoteGraphExecutionPlanBuilder.java:208` (`nop-stream-runtime`) also constructs `InputGate` — verify whether it passes `barrierAlignmentTimeout` or uses default.

- **G7 (channel blocking) is ALREADY IMPLEMENTED** in `InputGate.java`:
  - `blockConsumption(int channelIndex)` at line 220
  - `resumeConsumption(int channelIndex)` at line 234
  - `resumeConsumptionAll()` at line 245
  - Blocking is respected in `readMultiChannel()` at line 291: `if (barrierAlignment && blockedChannels.contains(channelIndex)) continue;`

- **BarrierAligner is correctly @Deprecated**: `BarrierAligner.java` at `nop-stream-runtime/.../checkpoint/barrier/BarrierAligner.java:33` is marked `@Deprecated @Internal`. Its javadoc says "当前 GraphModelCheckpointExecutor 未使用". This is **accurate** — `InputGate.handleBarrierNonRecursive()` is the production alignment mechanism. The roadmap's "Already shipped" claim "BarrierAligner 启用" is a documentation error.

- **Existing tests for InputGate** (10+ files in `nop-stream-core/src/test/.../execution/`): `TestInputGateBarrierAlignment`, `TestInputGateBlockingApi`, `TestInputGateProcessingGuarantee`, `TestInputGateAlignmentTimeout`, `TestLocalExecutionBarrierAlignment`, `TestInputGateBarrierForwarding`, `TestInputGateBarrierIndentFix`, `TestProcessingGuaranteeBehavior`, etc.

- **G5/G34 (CancelCheckpointMarker via data channel) deferred**: `checkpoint-design.md:911` explicitly states "abort 信号必须有独立于数据流的控制通道传播到所有 task。不得仅靠数据队列内的 marker——对齐等待时数据队列读不到 marker". This means a data-channel `CancelCheckpointMarker` cannot be consumed on a blocked channel during alignment — the marker would be stuck behind the block. The existing control-path abort (`CheckpointCoordinator.abortPendingCheckpoint()` → `GraphModelCheckpointExecutor.registerLocalAbortHandler()` → `inputGate.resumeConsumptionAll()` + `task.cancel()`) is the architecturally correct mechanism. G5/G34 would only make sense in a distributed (cross-JVM) context where control RPC can fail independently — this belongs to Stage 39.

## Goals

- G4 verified as correctly implemented in `InputGate` (non-hollow, production-wired, test-covered)
- G7 verified as correctly implemented in `InputGate` (non-hollow, production-wired, test-covered)
- Roadmap documentation error corrected: "BarrierAligner 启用" → "InputGate barrier alignment enabled"
- Gap analysis G4/G7 marked as resolved

## Non-Goals

- G5 (CancelCheckpointMarker event type) — deferred to Stage 39 (see Deferred section)
- G34 (abort data-channel propagation) — deferred to Stage 39 (see Deferred section)
- Any code changes to `InputGate` (verification only — if defects are found, they become Fix items in a successor plan)
- Removing or modifying `BarrierAligner` (correctly @Deprecated)
- Multi-input user-facing operators (connect, union, join)

## Scope

### In Scope

- G4/G7: Read-only verification of existing `InputGate` implementation + test coverage assessment
- Documentation fix: roadmap "Already shipped" section + gap analysis G4/G7 status
- Design doc update: checkpoint-design.md §2.2 to note ALIGNING is implemented via InputGate

### Out Of Scope

- G5/G34 (deferred — requires design-level decision about distributed abort semantics)
- InputGate code modifications
- BarrierAligner changes

## Execution Plan

### Phase 1 — Verify G4/G7 existing implementation

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java` (read-only)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java` (read-only)
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java` (read-only)
- Existing test files in `nop-stream-core/src/test/.../execution/`

- Item Types: `Proof`

- [ ] `Proof` Verify `InputGate.handleBarrierNonRecursive()` (line 347) implements complete alignment: (a) first barrier on a channel blocks that channel, (b) all channels' barriers triggers `resumeConsumptionAll()` + returns aligned barrier, (c) timeout throws exception, (d) overlapping barriers throw abort. Trace the code path line by line.
- [ ] `Proof` Verify `InputGate.blockConsumption()` / `resumeConsumption()` (lines 220, 234) gate record delivery in `readMultiChannel()` (line 291). Confirm that barriers and watermarks are still delivered during blocking (they should be — only `processElement` is gated).
- [ ] `Proof` Verify `InputGate` is wired into production execution paths: `GraphExecutionPlan.java:285` and `RemoteGraphExecutionPlanBuilder.java:208`. Confirm `barrierAlignment` flag is derived from `EdgeConfig` processing guarantee.
- [ ] `Proof` Verify `RemoteGraphExecutionPlanBuilder.java:208` — does it pass `barrierAlignmentTimeout` or use default? If default, document this as a known limitation (30s hardcoded default, not configurable for remote execution).
- [ ] `Proof` Audit existing test coverage: enumerate which alignment paths are covered by `TestInputGateBarrierAlignment`, `TestInputGateBlockingApi`, `TestInputGateAlignmentTimeout`, `TestLocalExecutionBarrierAlignment`. Identify any uncovered paths.
- [ ] `Proof` Verify end-to-end alignment: identify whether any test exercises `GraphExecutionPlan` → `InputGate` → actual Source→Operator→Sink data flow with barrier alignment active (check `TestProcessingGuaranteeBehavior`, `TestParallelGraphExecution`).
- [ ] `Proof` Verify control-path abort completeness (this validates the G5/G34 defer decision): confirm `GraphModelCheckpointExecutor.registerLocalAbortHandler()` is wired in all execution paths (lines 108, 169, 237, 295, 348), and that it correctly calls `inputGate.resumeConsumptionAll()` + `task.cancel()`. If any execution path lacks abort handler registration, this is a defect that must be documented.

Exit Criteria:

- [ ] G4 verified: `InputGate` implements multi-input barrier alignment (code trace + test coverage confirmed)
- [ ] G7 verified: `InputGate` implements channel blocking (code trace + test coverage confirmed)
- [ ] Any uncovered alignment paths or wiring defects documented as Non-Blocking Follow-ups (not silently ignored)
- [ ] **Anti-Hollow Check**: verification confirms (a) `handleBarrierNonRecursive()` is called by production code in `readMultiChannel()`, (b) `blockConsumption()` is called during alignment at line 351, (c) `resumeConsumptionAll()` is called when alignment completes at line 376, (d) at least one existing test exercises the full alignment → block → resume → aligned-barrier-output path
- [ ] No owner-doc update required for Phase 1 (verification-only, no live baseline change)
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Documentation fix

Status: planned
Targets:
- `ai-dev/backlog/nop-stream-production-roadmap.md`
- `ai-dev/analysis/nop-stream/08-gap-analysis.md`
- `ai-dev/design/nop-stream/checkpoint-design.md`

- Item Types: `Fix`

- [ ] `Fix` Correct roadmap "Already shipped" section: change "BarrierAligner 启用" to "InputGate barrier alignment enabled (BarrierAligner class is @Deprecated reference code, superseded by InputGate.handleBarrierNonRecursive())"
- [ ] `Fix` Correct additional BarrierAligner references in roadmap: line 142 (Reuse column "existing `BarrierAligner`" → "existing `InputGate` alignment"), line 219 (Stage 16 Goal "启用 BarrierAligner 多输入对齐状态机" → "验证 InputGate 多输入对齐状态机"), line 222 (Deliverable "G4: ...(BarrierAligner 状态机)" → "G4: ...(InputGate 状态机)")
- [ ] `Fix` Mark G4 and G7 as resolved in `08-gap-analysis.md` with reference to `InputGate.java` implementation
- [ ] `Fix` Update G5 and G34 assignments in `08-gap-analysis.md`: change "Item 9" to "deferred to Stage 39 (cross-JVM RPC prerequisite)" with note referencing `checkpoint-design.md:911` hard constraint
- [ ] `Fix` Update `checkpoint-design.md` §2.2: note that ALIGNING state is implemented in `InputGate.handleBarrierNonRecursive()`, not `BarrierAligner`. Add reference to the specific InputGate methods.
- [ ] `Fix` Update Stage 16 work item in roadmap: mark G4/G7 as done, keep G5/G34 as todo (deferred to Stage 39 prerequisite)

Exit Criteria:

- [ ] Roadmap "Already shipped" section corrected regarding BarrierAligner
- [ ] `08-gap-analysis.md` G4 and G7 marked as resolved with implementation reference
- [ ] `checkpoint-design.md` §2.2 updated with InputGate implementation reference
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0
- [ ] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [ ] G4 verified as already implemented in `InputGate` (non-hollow, production-wired)
- [ ] G7 verified as already implemented in `InputGate` (non-hollow, production-wired)
- [ ] BarrierAligner documentation error corrected in roadmap, gap analysis, and checkpoint design
- [ ] G5/G34 explicitly deferred with clear justification
- [ ] No code changes to InputGate (verification-only) — or if defects found, documented as follow-ups
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-production/2026-07-25-0800-3-multi-input-barrier-alignment.md --strict` exits 0
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] No in-scope live defect deferred to follow-up (G5/G34 are out-of-scope, not deferred in-scope defects)

## Deferred But Adjudicated

### G5 (CancelCheckpointMarker) + G34 (abort data-channel propagation)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `checkpoint-design.md:911` explicitly prohibits relying solely on data-channel markers for abort propagation: "abort 信号必须有独立于数据流的控制通道传播到所有 task。不得仅靠数据队列内的 marker——对齐等待时数据队列读不到 marker". In the current local execution model, the control-path abort (`registerLocalAbortHandler` → `inputGate.resumeConsumptionAll()` + `task.cancel()`) is the architecturally correct and complete mechanism. A data-channel `CancelCheckpointMarker` only makes sense in a distributed (cross-JVM) context where control RPC can fail independently — this belongs to Stage 39 (cross-JVM RPC + fencing). Implementing G5/G34 in the local model would either (a) conflict with the design doc hard constraint, or (b) be redundant with the existing control path.
- Successor Required: `yes`
- Successor Path: Stage 39 (distributed RPC), as a prerequisite design item

### BarrierAligner class removal

- Classification: `watch-only residual`
- Why Not Blocking Closure: `BarrierAligner` is correctly `@Deprecated` as reference code with zero production callers.
- Successor Required: `no`

## Non-Blocking Follow-ups

- If Phase 1 verification finds that `RemoteGraphExecutionPlanBuilder.java:208` does not pass `barrierAlignmentTimeout` (uses default 30s), document this as a configuration gap for future improvement.
- BarrierAligner has `ReentrantLock` and `Condition` — unused dead code since `InputGate` uses single-threaded read loop. Consider removing BarrierAligner entirely in Stage 23 (code cleanup).
- G5/G34 should be revisited when Stage 39 (cross-JVM RPC) is planned, as part of the distributed abort protocol design. At that point, a `CancelCheckpointMarker` may be useful as a **supplementary** notification on already-resumed channels, not as the primary abort mechanism on blocked channels.

## Closure

Status Note: (to be filled on completion)
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: (to be filled by independent sub-agent)
- Evidence: (to be filled)

Follow-up:

- (to be filled on closure)
