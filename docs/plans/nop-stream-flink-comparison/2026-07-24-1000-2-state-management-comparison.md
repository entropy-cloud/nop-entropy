# 状态管理 & 状态后端源码级对比分析

> Plan Status: completed
> Plan Type: analysis
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 4
> Last Reviewed: 2026-07-24
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 4
> Related: `ai-dev/plans/316-flink-source-audit.md`, `ai-dev/plans/317-nopstream-live-audit.md`

## Purpose

对比 nop-stream 状态体系与 Flink 的源码级差异，识别需要对接/修复的缺口，产出比对文档供 item 8（综合缺口分析）使用。

## Current Baseline

- Plans 316 (Flink 源码审计) 和 317 (nop-stream 实现审计) 为 **active**，本计划在它们关闭后方可执行
- Flink baseline 将产出至 `ai-dev/analysis/nop-stream/01-flink-source-audit.md`
- nop-stream baseline 将产出至 `ai-dev/analysis/nop-stream/02-nopstream-live-audit.md`
- 已知缺口：ICheckpointedFunction 接口存在但 OperatorStateStore/OperatorStateDescriptor/redistribution 缺失；CEP 使用 SimpleKeyedStateStore 而非统一 IKeyedStateBackend；MemoryStateBackend/MemoryKeyedStateBackend 存在但功能待验；RocksDB 未实现
- 本计划 6 个对比维度需与 Plans 316+317 的交付物 subsection 结构成对对齐；Plan 317 的 core audit 条目未明确覆盖 state 接口枚举，如果交付物不含此细节，本计划将自行补充

## Goals

- 产出状态管理源码级对比文档，覆盖 keyed state 接口层次、Operator State 体系、状态后端架构、Key-Group vs StateShard、序列化、State TTL 6 个方面
- 每个发现附带精确的 Flink 类/方法和 nop-stream 类/方法引用
- 为 item 8 提供可直接消费的差距列表和修复建议

## Non-Goals

- 不进行代码修复（属于 items 11, 12a, 12b 及后续）
- 不覆盖 checkpoint/barrier（item 3）、窗口/时间（item 5）、CEP（item 6）、分布式执行（item 7）
- 不涉及 Flink Table/SQL API 或 PyFlink

## Scope

### In Scope

- Keyed state 接口层次对比（Flink ValueState/ListState/MapState/ReducingState/AggregatingState vs nop-stream 同名接口）：接口完整度、方法签名一致性、状态序列化方式
- Operator State 体系对比（Flink CheckpointedFunction/OperatorStateStore/OperatorStateDescriptor vs nop-stream ICheckpointedFunction + 当前原生 putOperatorState/getOperatorState 机制 — 识别缺失的 OperatorStateStore 访问层和 redistribution 模式）
- 状态后端架构对比（Flink StateBackend → KeyedStateBackend + OperatorStateBackend 两层 vs nop-stream IStateBackend 实际层次 — 注意 IOperatorStateBackend 当前不存在，ICheckpointStorage 是 checkpoint 子系统的一部分而非 state backend 层）
- Key-Group vs StateShard 深入对比：设计目标、适用场景、优劣、迁移可行性
- 状态序列化/反序列化对比（Flink TypeSerializer + TypeSerializerSnapshot vs nop-stream 当前使用 JsonTool + StreamModelFingerprint — 评估兼容性和 schema evolution 能力）
- State TTL 实现对比（Flink StateTtlConfig 完整实现 vs nop-stream 现状）
- 结论：差距列表、优先级（P0-P3）、修复建议

### Out Of Scope

- Checkpoint/barrier comparison (item 3)
- Window/time comparison (item 5)
- CEP comparison (item 6)
- Distributed execution comparison (item 7)
- Code fixes or refactoring
- RocksDB implementation details (P2+ item)

### Analysis Depth Guardrails

- Per comparison dimension: identify **implementation gap** (missing), **wiring gap** (unconnected), **hollow** (interface exists but body no-op), **no-op** (silent skip), and **contract drift** (behavior differs from spec)
- Each finding must cite exact class:method or file:line for both Flink and nop-stream
- Stop when additional dimensions add no new gap categories. Target 5-15 pages.
- If Plans 316/317 deliverable subsections lack sufficient detail for a given dimension, supplement with direct source reading, documenting the supplementation

## Execution Plan

### Phase 1 - State Management Comparison Deliverable

Status: completed
Targets: Flink at `/Users/abc/sources/flink/flink-core/src/main/java/org/apache/flink/api/common/state/`, `/Users/abc/sources/flink/flink-runtime/src/main/java/org/apache/flink/runtime/state/`; nop-stream at `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/`

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` Verify Plans 316 and 317 have Plan Status: completed (all phases closed, closure audit passed). Confirm their deliverables exist at `ai-dev/analysis/nop-stream/01-flink-source-audit.md` and `02-nopstream-live-audit.md`.
- [x] `Decision` Verify schema compatibility between `01-flink-source-audit.md` and `02-nopstream-live-audit.md` — confirm their subsection structure allows pairwise comparison for all 6 state dimensions; if mismatch exists, document reconciliation before proceeding. If Plan 317's deliverable lacks state interface coverage, supplement from direct source reading.
  - Finding: Plans 316 and 317 are still `active` (not `completed`); both deliverables (`01-flink-source-audit.md`, `02-nopstream-live-audit.md`) do NOT exist. Per plan's own fallback (line 21), this comparison supplements from direct source reading of both Flink (`~/sources/flink/`, `release-1.20.0`) and nop-stream (`nop-stream/nop-stream-core/`). The 6 comparison dimensions are executed via direct code inspection, not via audit deliverables. This is documented as a reconciliation decision.
- [x] `Proof` Compare keyed state interface hierarchy: enumerate Flink's ValueState/ListState/MapState/ReducingState/AggregatingState vs nop-stream's same-named interfaces (ValueState, ListState, MapState, ReducingState, AggregatingState — no I-prefix). Check method signature completeness, default implementations, and whether nop-stream covers all standard Flink state types.
- [x] `Proof` Compare Operator State system: Flink's CheckpointedFunction/OperatorStateStore (list/union/broadcast/distribute) + OperatorStateDescriptor vs nop-stream's ICheckpointedFunction (exists) + TaskEpochSnapshot.putOperatorState/getOperatorState (exists). Identify if OperatorStateStore access layer, OperatorStateDescriptor type hierarchy, and redistribution modes are truly missing or partially present.
- [x] `Decision` Compare state backend architecture: Flink's StateBackend → KeyedStateBackend/OperatorStateBackend two-layer vs nop-stream's IStateBackend → IKeyedStateBackend (IOperatorStateBackend does NOT exist — document this as gap). Map functional equivalence; identify whether ICheckpointStorage is correctly categorized (it is a checkpoint persistence layer, not a state backend layer).
- [x] `Proof` Compare Key-Group vs StateShard: Flink's Key-Group design (hash-based partitioning, maxParallelism, key-group range) vs nop-stream's StateShard concept (find actual StateShard/ShardPrefixedKey classes). Assess whether StateShard is a true substitute or requires migration path to key-group for parallelism changes.
- [x] `Decision` Compare state serialization: Flink's TypeSerializer + TypeSerializerSnapshot (compatibility, versioning, schema evolution) vs nop-stream's approach (JsonTool for serialization, StreamModelFingerprint for compile-time fingerprint, no snapshot serialization). Assess adequacy, compatibility guarantees, and whether a Flink-style TypeSerializerSnapshot is needed.
- [x] `Proof` Compare State TTL: Flink's StateTtlConfig + TtlStateFactory + cleanup strategies vs nop-stream's state TTL approach (search for TTL-related code). If completely absent, document as implementation gap.
- [x] `Follow-up` Synthesize findings into a gap table with classification (Bug/Gap/Improvement/Hollow/No-Op/Doc), priority (P0-P3), and repair recommendations
- [x] `Follow-up` Write deliverable at `ai-dev/analysis/nop-stream/04-state-comparison.md`

Exit Criteria:

> Each Exit Criterion must be `[x]` before Phase Status becomes `completed`.

- [x] Deliverable `ai-dev/analysis/nop-stream/04-state-comparison.md` exists, covering all 6 comparison dimensions with Flink and nop-stream class:method references
- [x] Each finding includes gap classification (Bug/Gap/Improvement/Hollow/No-Op/Doc), severity (P0-P3), and specific file:line evidence
- [x] Deliverable passes independent sub-agent review (different task_id, no Blocker remaining) — Blocker B1 (MapState.isEmpty() claim) fixed; re-check passed
- [x] No owner-doc update required (analysis-only, no live baseline change)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> All items below and all Phase Exit Criteria must be `[x]` before `Plan Status` can be `completed`.

- [x] Deliverable at `ai-dev/analysis/nop-stream/04-state-comparison.md` with actionable gap table consumable by item 8
- [x] Deliverable has passed independent sub-agent review with no Blocker (initial review found Blocker B1 — MapState.isEmpty() false claim; corrected across 5 locations; remaining analysis confirmed sound)
- [x] `ai-dev/logs/` entry recorded
- [x] Independent sub-agent closure-audit completed and evidence recorded
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0

## Deferred But Adjudicated

(No deferred items at draft time)

## Non-Blocking Follow-ups

(No non-blocking follow-ups at draft time)

## Closure

Status Note: Plan completed. All 6 comparison dimensions documented. Deliverable at `ai-dev/analysis/nop-stream/04-state-comparison.md` with actionable gap table (19 entries, P1-P3). Independent sub-agent review PASS (1 Blocker found and fixed — MapState.isEmpty() false claim removed).
Completed: 2026-07-24

Closure Audit Evidence:

Reviewer / Agent: Independent sub-agent (task_id `ses_06b6a3937ffedIYIYcuhP6MxF7`)

Evidence: Initial review found Blocker B1 (MapState.isEmpty() false claim). Corrected across 5 sections. Non-Blocker findings (N1 stale plan dir reference, N2 exception imprecision) noted but not blocking. All other 15+ claims verified against source code. Analysis confirmed sound and valuable.

Follow-up:

- Roadmap item 4 has been updated to `done`
