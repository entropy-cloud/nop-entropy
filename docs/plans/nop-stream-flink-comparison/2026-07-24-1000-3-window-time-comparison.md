# 窗口机制 & 时间模型源码级对比分析

> Plan Status: active
> Plan Type: analysis
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 5
> Last Reviewed: 2026-07-24
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 5
> Related: `ai-dev/plans/316-flink-source-audit.md`, `ai-dev/plans/317-nopstream-live-audit.md`, `ai-dev/plans/303-nop-stream-flink-inspired-improvements.md`

## Purpose

对比 nop-stream 窗口/时间模型与 Flink 的实现层面差异，识别缺口、接口不一致、空壳和静默跳过，产出比对文档供 item 8（综合缺口分析）使用。

## Current Baseline

- Plans 316 (Flink 源码审计) 和 317 (nop-stream 实现审计) 为 **active**，本计划在它们关闭后方可执行
- Flink baseline 将产出至 `ai-dev/analysis/nop-stream/01-flink-source-audit.md`
- nop-stream baseline 将产出至 `ai-dev/analysis/nop-stream/02-nopstream-live-audit.md`
- Plan 303 已完成 WindowAggregationOperator 退役和 TimerService 的 processing time timer 实现；但其 Closure 记录 4 个 session window 测试被禁用（`TestSessionWindowAdvancedMerge` x3 + `TestSessionWindowWithPeriodicWatermark.testMultiKeyIndependentSessions` x1），暴露了 WindowOperator session window merge bug（`Failed to set merged accumulator`）。本计划负责评估此 bug 在对比分析中的分类和影响
- 已知缺口：TimestampsAndWatermarksOperator 未自动插入图管线、watermarkInterval 硬编码为 0 使周期性发射不生效、多输入 watermark 合并可能未接线
- 本计划 6 个对比维度需与 Plans 316+317 的交付物 subsection 结构成对对齐；如果 Plans 316/317 交付物粒度不足，本计划将直接从源码补充

## Goals

- 产出窗口/时间模型源码级对比文档，覆盖 WindowOperator 执行路径、合并窗口、Pane 语义、InternalTimerService、Watermark 生成与传播、Watermark 自动插入 6 个方面
- 每个发现附带精确的 Flink 类/方法和 nop-stream 类/方法引用
- 为 item 8 提供可直接消费的差距列表和修复建议
- 对 Plan 303 遗留的 4 个禁用 session window 测试，评估并归类其在 gap 表中的位置（Bug/P1 级别 — 因导致实际功能退化）

## Non-Goals

- 不进行代码修复（属于 item 10 及后续）
- 不覆盖 checkpoint/barrier（item 3）、状态管理（item 4）、CEP（item 6）、分布式执行（item 7）
- 不涉及 Flink Table/SQL API 或 PyFlink

## Scope

### In Scope

- WindowOperator 执行路径对比：窗口分配、状态操作、trigger 触发、emit、purging。对比 Flink WindowOperator 完整生命周期 vs nop-stream WindowOperator 实现
- MergingWindowSet/合并窗口对比：SessionWindow、MergingWindowAssigner 路径。Flink 的 MergingWindowSet 如何管理窗口合并状态 vs nop-stream 的等价实现。需覆盖 Plan 303 遗留的 session window merge bug
- Pane 语义对比：early/on-time/late firing、PaneState。对比 Flink 的 Pane 管理 vs nop-stream 的 pane/late 处理
- InternalTimerService 对比：timer 注册/触发/checkpoint/恢复。对比 Flink 的 TimerHeapInternalTimer + InternalTimerService 两阶段 vs nop-stream HeapInternalTimerService（已知 Plan 303 已修复 processing time timer 空方法体）
- Watermark 生成与传播对比：Strategy 接口（WatermarkStrategy/SourceFunction 内建 vs TimestampsAndWatermarksOperator）、单输入/多输入对齐（StatusWatermarkValve vs nop-stream multi-input）、空闲检测（withIdleness）
- Watermark 自动插入机制对比：Flink 的 autoWatermarkInterval + onPeriodicEmit vs nop-stream 未生效的 watermarkInterval
- 结论：差距列表、优先级（P0-P3）、修复建议

### Out Of Scope

- Checkpoint/barrier comparison (item 3)
- State management comparison (item 4)
- CEP comparison (item 6)
- Distributed execution comparison (item 7)
- Code fixes or refactoring
- Parallel source watermark alignment (WatermarkAlignment group + coordinator support)

### Analysis Depth Guardrails

- Per comparison dimension: identify **Bug** (incorrect behavior), **Gap** (feature missing or unconnected), **Improvement** (enhancement opportunity), **Hollow** (interface exists but body no-op), **No-Op** (silent skip), and **Doc** (documentation/contract drift) — consistent with the gap table taxonomy used in Execution Plan item 9
- Each finding must cite exact class:method or file:line for both Flink and nop-stream
- Stop when additional dimensions add no new gap categories. Target 5-15 pages.
- If Plans 316/317 deliverable subsections lack sufficient detail for a given dimension, supplement with direct source reading (`/Users/abc/sources/flink/flink-streaming-java/...` and `nop-stream/...`), documenting the supplementation

## Execution Plan

### Phase 1 - Window/Time Comparison Deliverable

Status: planned
Targets: Flink at `/Users/abc/sources/flink/flink-streaming-java/src/main/java/org/apache/flink/streaming/api/windowing/` and `/Users/abc/sources/flink/flink-streaming-java/src/main/java/org/apache/flink/streaming/api/operators/`; nop-stream at `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/`

- Item Types: `Proof | Decision | Follow-up`

- [ ] `Proof` Verify Plans 316 and 317 have Plan Status: completed (all phases closed, closure audit passed). Confirm their deliverables exist at `ai-dev/analysis/nop-stream/01-flink-source-audit.md` and `02-nopstream-live-audit.md`.
- [ ] `Decision` Verify schema compatibility between `01-flink-source-audit.md` and `02-nopstream-live-audit.md` — confirm their subsection structure allows pairwise comparison for all 6 window/time dimensions; if mismatch exists, document reconciliation before proceeding.
- [ ] `Proof` Compare WindowOperator execution path: Flink's WindowOperator.processElement() → WindowAssigner.assignWindows() → trigger.onElement() → WindowFunction/ProcessWindowFunction → emit → pane management vs nop-stream's WindowOperator execution. Check state operations (windowState, windowStateDescriptor), trigger context implementation, evictor integration.
- [ ] `Decision` Compare MergingWindowSet/session window support: Flink's MergingWindowSet (merge session windows, merge results callback, persisted merge state) vs nop-stream's equivalent. Assess the 4 disabled session window tests from Plan 303 follow-up — classify as Bug/P1 (functional regression, `Failed to set merged accumulator`). Record in gap table as Bug with priority P1.
- [ ] `Proof` Compare pane semantics: Flink's allowedLateness + pane management (PaneState, early/on-time/late firing, onTimer triggers) vs nop-stream's pane implementation. Check late data handling, triggerResult (PURGE/CONTINUE/FIRE_AND_PURGE), and allowedLateness contract.
- [ ] `Proof` Compare InternalTimerService: Flink's TimerHeapInternalTimer + InternalTimerService (two-phase: timer registration at operator level, deletion/firing at service level) vs nop-stream's HeapInternalTimerService. Verify Plan 303's processing time timer fix aligns with Flink's timer semantics. Check timer checkpoint/restore integration.
- [ ] `Proof` Compare watermark generation and propagation: Flink's WatermarkStrategy (assignTimestampsAndWatermarks) vs nop-stream's TimestampsAndWatermarksOperator. Check whether the operator is auto-inserted in the transformation pipeline. Compare multi-input watermark alignment (Flink StatusWatermarkValve vs nop-stream multi-input merge). Check idle detection (withIdleness).
- [ ] `Proof` Compare watermark auto-insertion mechanism: Flink's autoWatermarkInterval → SourceFunction.SourceContext.emitWatermark(periodic) cycle vs nop-stream's watermarkInterval (hardcoded 0). Identify the gap in periodic watermark emission.
- [ ] `Follow-up` Synthesize findings into a gap table (Bug/Gap/Improvement/Hollow/No-Op/Doc) with priority (P0-P3) and repair recommendations
- [ ] `Follow-up` Write deliverable at `ai-dev/analysis/nop-stream/05-window-comparison.md`

Exit Criteria:

> Each Exit Criterion must be `[x]` before Phase Status becomes `completed`.

- [ ] Deliverable `ai-dev/analysis/nop-stream/05-window-comparison.md` exists, covering all 6 comparison dimensions with Flink and nop-stream class:method references
- [ ] Each finding includes gap classification (Bug/Gap/Improvement/Hollow/No-Op/Doc), severity (P0-P3), and specific file:line evidence
- [ ] Plan 303's 4 disabled session window tests assessed and classified in the gap table (expected: Bug/P1 — session window merge functional regression)
- [ ] Deliverable passes independent sub-agent review (different task_id, no Blocker remaining)
- [ ] No owner-doc update required (analysis-only, no live baseline change)
- [ ] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> All items below and all Phase Exit Criteria must be `[x]` before `Plan Status` can be `completed`.

- [ ] Deliverable at `ai-dev/analysis/nop-stream/05-window-comparison.md` with actionable gap table consumable by item 8
- [ ] Deliverable has passed independent sub-agent review with no Blocker
- [ ] `ai-dev/logs/` entry recorded
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0

## Deferred But Adjudicated

(No deferred items at review time)

## Non-Blocking Follow-ups

(No non-blocking follow-ups at draft time)

## Closure

Status Note: (to be filled on completion)
Completed: (to be filled on completion)

Closure Audit Evidence:

(to be filled by independent sub-agent on closure)

Follow-up:

- (to be filled on closure)
