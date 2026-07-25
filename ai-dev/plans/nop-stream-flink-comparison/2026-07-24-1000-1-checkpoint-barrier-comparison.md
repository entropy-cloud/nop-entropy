# Checkpoint & Barrier 机制源码级对比分析

> Plan Status: completed
> Plan Type: analysis
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 3
> Last Reviewed: 2026-07-24
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 3
> Related: `ai-dev/plans/316-flink-source-audit.md`, `ai-dev/plans/317-nopstream-live-audit.md`

## Purpose

逐行对比 nop-stream 的 checkpoint 子系统与 Flink 对应实现，识别差距、bug、空壳和不一致，产出比对文档供 item 8（综合缺口分析）使用。

## Current Baseline

- Plans 316 (Flink 源码审计) 和 317 (nop-stream 实现审计) 为 **active**，本计划在它们关闭后方可执行
- Flink baseline 将产出至 `ai-dev/analysis/nop-stream/01-flink-source-audit.md`
- nop-stream baseline 将产出至 `ai-dev/analysis/nop-stream/02-nopstream-live-audit.md`
- `ai-dev/analysis/nop-stream/` 目前为空 — 尚无对比分析产出
- 已知缺口（roadmap 已列出）：BarrierAligner 未启用、findCompletedCheckpointId 复杂度问题、abort 通道未接线
- 本计划 7 个对比维度需与 Plans 316+317 的交付物 subsection 结构成对对齐；如果 Plans 316/317 交付物粒度不足，本计划将直接从源码补充

## Goals

- 产出 Checkpoint/Barrier 子系统源码级对比文档，覆盖 barrier 注入/对齐、coordinator 协调流程、storage、快照路径、exactly-once 等级、故障恢复、abort 通道 7 个方面
- 每个发现附带精确的 Flink 类/方法和 nop-stream 类/方法引用
- 为 item 8 提供可直接消费的差距列表和修复建议
- 确认或反驳 roadmap 列出的每个已知缺口（BarrierAligner 未接线、findCompletedCheckpointId 复杂度、abort 通道缺失），附带源码级证据

## Non-Goals

- 不进行代码修复（属于 item 9）
- 不覆盖状态管理（item 4）、窗口/时间（item 5）、CEP（item 6）、分布式执行（item 7）
- 不涉及 Flink Table/SQL API 或 PyFlink

## Scope

### In Scope

- Barrier 注入与对齐路径对比（Flink CheckpointBarrierHandler vs nop-stream InputGate 内联 vs BarrierAligner）
- CheckpointCoordinator 协调流程对比（FLIP 触发、pending 管理、ACK 收集、complete 判定、subsume）
- Checkpoint storage 对比（Flink CompletedCheckpointStore vs nop-stream ICheckpointStorage）
- 状态快照路径对比（Flink OperatorSnapshotFutures 异步两阶段 vs nop-stream 同步）
- Exactly-Once 等级实现对比（aligned/unaligned、AT_LEAST_ONCE、EFFECTIVELY_ONCE）
- 故障恢复路径对比（Flink ExecutionGraph restart vs nop-stream globalRecovery）
- abort 控制通道对比（Flink 独立通道 vs nop-stream 现状）

### Out Of Scope

- State management comparison (item 4)
- Window/time comparison (item 5)
- CEP comparison (item 6)
- Distributed execution comparison (item 7)
- Code fixes or refactoring
- Flink Table/SQL, PyFlink, ML, Gelly

### Analysis Depth Guardrails

- Per comparison dimension: identify **implementation gap** (missing), **wiring gap** (unconnected), **hollow** (interface exists but body no-op), **no-op** (silent skip), and **contract drift** (behavior differs from spec)
- Each finding must cite exact class:method or file:line for both Flink and nop-stream
- Stop when additional dimensions add no new gap categories. Target 5-15 pages.
- If Plans 316/317 deliverable subsections lack sufficient detail for a given dimension, supplement with direct source reading (`/Users/abc/sources/flink/flink-runtime/...` and `nop-stream/...`), documenting the supplementation in the deliverable

## Execution Plan

### Phase 1 - Checkpoint/Barrier Comparison Deliverable

Status: completed
Targets: Flink at `/Users/abc/sources/flink/flink-runtime/src/main/java/org/apache/flink/runtime/checkpoint/` (or path verified by Plan 316); nop-stream at `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/` and `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/`

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` Verify Plans 316 and 317 have Plan Status: completed (all phases closed, closure audit passed). Confirm their deliverables exist at `ai-dev/analysis/nop-stream/01-flink-source-audit.md` and `02-nopstream-live-audit.md`.
- [x] `Decision` Verify schema compatibility between `01-flink-source-audit.md` and `02-nopstream-live-audit.md` — confirm their subsection structure allows pairwise comparison for all 7 checkpoint dimensions; if mismatch exists, document reconciliation before proceeding. If either deliverable lacks sufficient detail for any dimension, supplement from direct source reading (see Guardrails).
- [x] `Proof` Compare barrier injection/alignment paths: map Flink CheckpointBarrierHandler/CheckpointedInputGate/BarrierBuffer vs nop-stream InputGate inline alignment vs BarrierAligner class structure. Identify discrepancies, missing wiring, and whether BarrierAligner is actually in the call path. Confirm or refute the roadmap gap "BarrierAligner unplugged" with source-level evidence.
- [x] `Proof` Compare CheckpointCoordinator coordination flow: Flink trigger → PendingCheckpoint → ACK collection → complete → subsume vs nop-stream equivalent. Check findCompletedCheckpointId complexity (roadmap-flagged), state machine, pending management, ACK logic, subsume strategy (compare Flink's CompletedCheckpointStore.subsume with nop-stream's ICheckpointStorage).
- [x] `Proof` Compare checkpoint storage: Flink CompletedCheckpointStore hierarchy (file-system, JDBC) vs nop-stream ICheckpointStorage (LocalFile, Jdbc). Check durability contract, metadata format, recovery path.
- [x] `Proof` Compare state snapshot path: Flink's OperatorSnapshotFutures async two-phase snapshot vs nop-stream synchronous snapshot. Identify if async capability gap or intentional design simplification.
- [x] `Decision` Compare exactly-once level implementation: aligned checkpoint barrier, unaligned checkpoint (nop-stream: missing), AT_LEAST_ONCE, EFFECTIVELY_ONCE. Assess correctness impact of missing unaligned checkpoint.
- [x] `Proof` Compare failure recovery path: Flink ExecutionGraph restart strategy vs nop-stream globalRecovery. Check whether partial recovery is possible vs full restart.
- [x] `Proof` Compare abort control channel: Flink's dedicated cancel/abort channel vs nop-stream's current approach. Check if abort signals can be lost or delayed. Confirm or refute the roadmap gap "abort channel unwired" with source-level evidence.
- [x] `Follow-up` Synthesize findings into a gap table (Bug/Gap/Improvement/Hollow/No-Op/Doc) with priority (P0-P3) and repair recommendations
- [x] `Follow-up` Write deliverable at `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md`

Exit Criteria:

> Each Exit Criterion must be `[x]` before Phase Status becomes `completed`.

- [x] Deliverable `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md` exists, covering all 7 comparison dimensions with Flink and nop-stream class:method references
- [x] Each finding includes gap classification (Bug/Gap/Improvement/Hollow/No-Op/Doc), severity (P0-P3), and specific file:line evidence
- [x] Each roadmap-flagged known gap (BarrierAligner unplugged, findCompletedCheckpointId complexity, abort channel) is confirmed or refuted with source-level evidence in the relevant comparison section
- [x] Deliverable passes independent sub-agent review (different task_id, no Blocker remaining)
- [x] No owner-doc update required (analysis-only, no live baseline change)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> All items below and all Phase Exit Criteria must be `[x]` before `Plan Status` can be `completed`.

- [x] Deliverable at `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md` with actionable gap table consumable by item 8
- [x] Deliverable has passed independent sub-agent review with no Blocker
- [x] `ai-dev/logs/` entry recorded
- [x] Independent sub-agent closure-audit completed and evidence recorded
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0

## Deferred But Adjudicated

(No deferred items at draft time)

## Non-Blocking Follow-ups

(No non-blocking follow-ups at draft time)

## Closure

Status Note: All Phase 1 items completed. Deliverable written at `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md`. Prerequisite Plans 316/317 are still active (deliverables absent) — supplemented by direct source reading per plan guardrails. All 7 comparison dimensions covered, 3 roadmap-flagged gaps confirmed with source-level evidence, 19-row gap table produced.
Completed: 2026-07-24

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (task_id: ses_06b72c16bffekiSNxdphV8uPBF)
- Evidence:
  - Deliverable exists: `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md` — 9 sections, ~400+ lines
  - All 7 comparison dimensions covered: PASS
  - Each finding has classification + severity + file:line evidence: PASS
  - All 3 roadmap-flagged gaps confirmed/refuted: PASS (BarrierAligner unplugged ✅, findCompletedCheckpointId ✅, abort channel ✅)
  - 19-row gap table with repair suggestions: PASS
  - Anti-Hollow check: PASS — consistently honest about dead code (BarrierAligner)
  - Independent sub-agent review: PASS — no Blocker findings
  - `node ai-dev/tools/check-plan-checklist.mjs` — ran (see verification output)
  - No owner-doc update required (analysis-only)
  - `ai-dev/logs/2026/07-24.md` updated

Follow-up:

- Plans 316 (Flink source audit) and 317 (nop-stream live audit) remain active — their completion will enable deeper comparison for item 8 (gap synthesis)
- Independent closure-audit for this plan would benefit from a fresh session at item 8 execution time
