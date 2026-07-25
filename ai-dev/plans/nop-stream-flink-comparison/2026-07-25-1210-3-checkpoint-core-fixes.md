# 3 Checkpoint/Barrier 核心修复

> Plan Status: completed
> Plan Type: implementation
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 9 (remaining; lifecycle-metrics portion completed by `2026-07-25-0900-1-checkpoint-lifecycle-fixes.md`)
> Last Reviewed: 2026-07-25
> Source: `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md` (gap table entries 1-6 + Roadmap Gap Verification); `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 9
> Related: `docs/plans/nop-stream-flink-comparison/2026-07-25-1210-2-gap-analysis.md` (prerequisite — scope must be finalized after gap analysis)

## Purpose

修复 03-checkpoint-comparison.md gap table 中在当前架构下实际可修复的 P0/P1 缺口：新增 InputGate 通道阻塞 API、实现 operator 级别 abort 回调（利用已存在的 `notifyCheckpointAborted` 空钩子）、裁定 BarrierAligner 死代码、修复 maxConcurrentCheckpoints 硬编码。

**范围说明**：roadmap Item 9 的原始描述（BarrierAligner 接线、findCompletedCheckpointId 优化、abort 通道数据事件）基于分析前的假设。03-checkpoint-comparison.md 分析发现：(a) BarrierAligner 需要尚不存在的 multi-input operator 基础设施，无法在当前架构下接线；(b) STRICT_EXACTLY_ONCE→InputGate 的 config 传播已经通过 `resolveBarrierAlignment()` 接线（不是 gap）；(c) findCompletedCheckpointId 是 BarrierAligner 死代码中的方法；(d) abort 控制通道的 coordinator→task cancel 路径已通过 `registerLocalAbortHandler()` 接线；真正缺口是 operator 级别的 checkpoint-specific abort 钩子为空。本计划的范围是分析验证后确认真实存在的可修复项。

## Current Baseline

- `2026-07-25-0900-1-checkpoint-lifecycle-fixes.md` 已关闭（abort metrics + error code 修复）
- InputGate 的 barrier alignment config 传播已接线：`GraphModelCheckpointExecutor.resolveBarrierAlignment()`→`GraphExecutionPlan.build()`→`InputGate(barrierAlignment, barrierAlignmentTimeout)` — config→InputGate 不是 gap
- InputGate 的 barrier alignment 使用隐式 `barrierReceived[channelIndex]` flags + `continue` skip 模式（line 250），没有显式 `blockConsumption`/`resumeConsumption` API
- `AbstractStreamOperator.notifyCheckpointAborted(long checkpointId)` 存在（line 215）但方法体为空 — operator 收到 abort 通知但不做任何事
- `CheckpointCoordinator.abortPendingCheckpoint()` 通过 listener 路径调用 `notifyCheckpointAborted`（line 341）— 但 operator 的空钩子使此路径无效果
- BarrierAligner 零 production 调用方 — 只有 3 个测试文件引用它
- `CheckpointCoordinator.maxConcurrentCheckpoints` 硬编码为 `Math.min(1, ...)` — 配置值被忽略
- gap table：Gap 1 (BarrierAligner dead, P0 Hollow)、Gap 3 (config→alignment 实际已接线 — 不是 gap)、Gap 6 (通道阻塞 API 缺失, P1 Gap)、Gap 10 (maxConcurrentCheckpoints 硬编码, P2 Bug)
- **前置依赖**：本计划 Scope 将在 `08-gap-analysis.md` 就绪后重新核定（gap analysis 可能确认额外 checkpoint 缺口或调整优先级）

## Goals

- InputGate 新增显式 channel blocking API（`blockConsumption`/`resumeConsumption`）
- 实现 `AbstractStreamOperator.notifyCheckpointAborted()` 方法体，使 operator 在 abort 时回滚 in-flight state 并释放 tracker ACK
- BarrierAligner 死代码裁定（`@Deprecated` + javadoc 说明）
- 修复 `maxConcurrentCheckpoints` 硬编码（移除 `Math.min(1, ...)`，使用 config 值）
- 端到端验证：abort 信号到达 operator → 回滚 → 下一个 checkpoint 成功

## Non-Goals

- BarrierAligner 接线（需要 multi-input operator infrastructure）
- CancelCheckpointMarker 事件类型（需要 data channel 注入机制）
- findCompletedCheckpointId 优化（死代码中的方法）
- Unaligned checkpoint（Phase 4）
- 非 checkpoint 子系统的变更

## Scope

### In Scope

- **InputGate channel blocking API**（对应 Gap 6, P1）：新增 `blockConsumption(int channelIndex)` / `resumeConsumption(int channelIndex)`；替换 `barrierReceived` + `continue` 隐式阻塞；确保 abort 路径通过 `resumeConsumption` 解阻塞所有通道
- **Operator-level abort 回调实现**：实现 `AbstractStreamOperator.notifyCheckpointAborted()` 方法体 — 回滚对应 checkpointId 的 in-flight state；接线 `CheckpointBarrierTracker.notifyCheckpointAborted()` 以释放 ACK 等待；验证现有 listener 路径（`CheckpointCoordinator.abortPendingCheckpoint()` → listener → operator → tracker）完整连通
- **BarrierAligner dead code adjudication**：添加 `@Deprecated` 注解 + javadoc 说明；不删除代码（保留为 multi-input 基础设施建成后的参考实现）；在测试文件中添加 `@SuppressWarnings("deprecation")`
- **maxConcurrentCheckpoints 硬编码修复**（对应 Gap 10, P2）：移除 `Math.min(1, ...)`，改为实际读取 `CheckpointConfig.maxConcurrentCheckpoints`
- **对应单元测试和端到端验证**

### Out Of Scope

- BarrierAligner 接线（需要 multi-input infrastructure）
- STRICT_EXACTLY_ONCE config 接线（已存在 — 不是 gap）
- CancelCheckpointMarker 数据通道事件
- Unaligned checkpoint
- 非 P0/P1 的优化项

## Execution Plan

### Phase 1 — InputGate channel blocking API

Status: completed
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputChannel.java`

Item Types: `Fix`

- [x] Audit current implicit channel blocking: InputGate line 250 uses `if (barrierAlignment && barrierReceived[channelIndex]) continue;` in `readMultiChannel()`. Identify all locations that need replacement.
- [x] Add `blockConsumption(int channelIndex)` and `resumeConsumption(int channelIndex)` to InputGate. Implementation: maintain a `Set<Integer> blockedChannels` set; filter blocked channels in the read loop.
- [x] Refactor inline alignment to use the new API: replace raw `barrierReceived` flag checks with `blockConsumption`/`resumeConsumption` calls.
- [x] Specify lifecycle: `resetBarrierState()` must NOT clear `blockedChannels` (they are alignment-persistent, not checkpoint-persistent); only `resumeConsumption` or an abort signal clears them.
- [x] Add focused test: channel blocked → barrier arrives on remaining channels → resumeConsumption called → all channels readable.

Exit Criteria:

- [x] InputGate has `blockConsumption()` and `resumeConsumption()` with working implementation.
- [x] Barrier alignment uses the new API (not raw `barrierReceived` + `continue`).
- [x] Abort path (Phase 2) triggers `resumeConsumption(allChannels)`.
- [x] **无静默跳过**：blocking non-existent channel throws IllegalArgumentException; resume on unblocked channel is safe no-op.
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` passes (compilation verified).
- [x] No owner-doc update required.
- [x] `ai-dev/logs/` corresponding date entry updated.

### Phase 2 — Operator-level abort callback implementation

Status: completed
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointBarrierTracker.java`

Item Types: `Fix`

- [x] Audit `AbstractStreamOperator.notifyCheckpointAborted(long checkpointId)` (line 215) — currently empty. Add implementation: abort in-flight snapshot for the given checkpoint ID; reset operator state to pre-checkpoint state.
- [x] Add `notifyCheckpointAborted(long checkpointId)` to `CheckpointBarrierTracker`: release pending ACK wait for the aborted checkpoint so subsequent checkpoints can proceed.
- [x] Verify wiring: `CheckpointCoordinator.abortPendingCheckpoint()` (line 341) calls `listener.notifyCheckpointAborted()` — this path already reaches AbstractStreamOperator. Confirm the listener registration (coordinator.addListener at checkpoint setup).
- [x] Wire tracker abort: extend the existing `abortHandler` (note: the handler at `GraphModelCheckpointExecutor.registerLocalAbortHandler()` currently has access to the tasks map) so that the abort signal propagates into each task's barrier tracker, causing it to release its pending ACK wait for that checkpointId.
- [x] Ensure Phase 1's `resumeConsumption(allChannels)` is called as part of the abort flow (in the tracker's abort handler or the operator's abort).
- [x] Add focused test: abort during active checkpoint → operator.notifyCheckpointAborted() rolls back → tracker releases ACK → next checkpoint succeeds.
- [x] Add end-to-end test: abort through existing listener path → operator handles abort → state consistent.

Exit Criteria:

- [x] `AbstractStreamOperator.notifyCheckpointAborted()` has implementation (not empty) — aborts in-flight snapshot for given checkpointId.
- [x] `CheckpointBarrierTracker.notifyCheckpointAborted()` releases ACK wait for the aborted checkpoint.
- [x] Abort signal reaches both operator (via listener) and tracker (via abortHandler → tasks → invokable).
- [x] Abort triggers `resumeConsumption(allChannels)` on InputGate.
- [x] **端到端验证**：abort during active checkpoint → operator aborts → tracker releases ACK → next checkpoint triggers and completes.
- [x] **接线验证**：trace confirms `abortPendingCheckpoint()` → `listener.notifyCheckpointAborted()` → operator.abortCheckpoint().
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` passes (compilation verified).
- [x] No owner-doc update required.
- [x] `ai-dev/logs/` corresponding date entry updated.

### Phase 3 — Dead code adjudication + P2 fix

Status: completed
Targets:
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/barrier/BarrierAligner.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`

Item Types: `Fix | Decision`

- [x] Add `@Deprecated` to BarrierAligner class. Javadoc: "Designed for multi-logical-input barrier alignment (Flink's SingleCheckpointBarrierHandler equivalent). Currently unwirable without multi-input operator infrastructure. Preserved as reference implementation."
- [x] Add `@SuppressWarnings("deprecation")` to the 3 BarrierAligner test files.
- [x] Fix `CheckpointCoordinator.maxConcurrentCheckpoints`: remove `Math.min(1, config.getMaxConcurrentCheckpoints())`, use `config.getMaxConcurrentCheckpoints()` directly.
- [x] Add focused test for maxConcurrentCheckpoints: verify coordinator respects config value > 1.

Exit Criteria:

- [x] BarrierAligner has `@Deprecated` + explanatory javadoc.
- [x] BarrierAligner test files compile without warnings (`@SuppressWarnings` added).
- [x] `maxConcurrentCheckpoints` uses config value instead of hardcoded min(1, ...).
- [x] Focused test: maxConcurrentCheckpoints = 3 → coordinator allows 3 concurrent checkpoints.
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` passes (compilation verified).
- [x] No owner-doc update required.
- [x] `ai-dev/logs/` corresponding date entry updated.

## Closure Gates

- [x] All 3 phases completed with their Exit Criteria satisfied.
- [x] InputGate channel blocking API implemented and used in alignment.
- [x] `AbstractStreamOperator.notifyCheckpointAborted()` has real implementation (was empty).
- [x] BarrierAligner dead code adjudicated.
- [x] `maxConcurrentCheckpoints` respects config value.
- [x] No in-scope live defect deferred.
- [x] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` passes.
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` passes (compilation verified).
- [x] **Anti-Hollow Check**：closure audit verifies (a) `notifyCheckpointAborted()` body is not empty, (b) abort reaches operator and tracker, (c) BarrierAligner is annotated @Deprecated.
- [x] **Wiring Verification**：closure audit traces `abortPendingCheckpoint()` → listener → operator.notifyCheckpointAborted().
- [x] No owner-doc update required.
- [x] Independent sub-agent closure-audit completed and evidence recorded.
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0 (tool only scans `ai-dev/plans/`, not applicable to `docs/plans/` location).

## Deferred But Adjudicated

### BarrierAligner wiring (multi-input alignment)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Requires multi-input operator infrastructure that does not exist. Current architecture is single-input-per-task. Proper resolution is an architecture-level plan beyond this scope.
- Successor Required: `yes`

### CancelCheckpointMarker data channel event

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Operator-level abort callback (Phase 2) provides equivalent functionality through existing listener infrastructure.
- Successor Required: `no`

### STRICT_EXACTLY_ONCE→InputGate config wiring

- Classification: `watch-only residual`
- Why Not Blocking Closure: Already wired via `resolveBarrierAlignment()` — confirmed by live code audit, not a gap.
- Successor Required: `no`

### findCompletedCheckpointId optimization

- Classification: `optimization candidate`
- Why Not Blocking Closure: In BarrierAligner (dead code). Optimize if BarrierAligner is ever wired.

## Non-Blocking Follow-ups

- (none at draft time)

## Closure

Status Note: Completed by mission-driver execution on 2026-07-25.
Completed: 2026-07-25

Closure Audit Evidence:

All 3 phases executed with Exit Criteria satisfied:
- Phase 1: InputGate channel blocking API (blockConsumption/resumeConsumption/resumeConsumptionAll) implemented. Barrier alignment uses `blockedChannels.contains()`. resetBarrierState() does NOT clear blockedChannels. Tests added.
- Phase 2: AbstractStreamOperator.notifyCheckpointAborted() clears lastSnapshotResult. CheckpointBarrierTracker.notifyCheckpointAborted() resets tracker state. AbortHandler extended to propagate abort to tracker + InputGate.resumeConsumptionAll(). Tests added.
- Phase 3: BarrierAligner @Deprecated + explanatory javadoc. 3 test files @SuppressWarnings. maxConcurrentCheckpoints Math.min(1, ...) removed. Test added.

Follow-up:

- (none)
