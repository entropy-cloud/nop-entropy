# Evictor / Watermark Valve / Pane 集成（G46, G47, G48, P2）

> Plan Status: completed
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 21；`ai-dev/analysis/nop-stream/08-gap-analysis.md` G46/G47/G48（05-window: G7/G8/G9）
> Mission: nop-stream-production
> Work Item: 21
> Related: `ai-dev/design/nop-stream/window-design.md` §3.3/§6/§8.7/§11/§13；`ai-dev/design/nop-stream/time-model-design.md` §5.3/§6.4/§8/§9；Plan `2026-07-25-0800-1-session-window-merge`（窗口算子前置，已完成）

## Purpose

收口窗口/时间模型的三个 P2 集成缺口，并同步纠正 `window-design.md` / `time-model-design.md` 的过时描述。**经 live 核对，三个 gap 的现状与 roadmap 假设有偏差**：G46 的 evictBefore/evictAfter **已接线且语义与 Flink 的 transient-per-fire 淘汰一致**（持久化裁剪反而是回归，非修复）；G47 的合并数学**已存在且 N 输入可用**，但 nop-stream 无两输入算子消费故 dormant；G48 的 pane 计算**已实现并暴露**，真实缺口在持久化与 `isLast`/retract 语义。本 plan 据此把每个 gap 收口为"核对 + 补真实 gap + 文档"，避免建造无消费者的空壳。

## Current Baseline

> 所有引用均为 live repo 核对结果，路径相对 `nop-stream` 模块根。

### G46 — Evictor 调用现状
- `Evictor` 接口 at `nop-stream-core/.../windowing/evictors/Evictor.java:40`：`evictBefore`(50-54)、`evictAfter`(64-68)、`EvictorContext`(73-84)。实现 `CountEvictor`/`DeltaEvictor`/`TimeEvictor` 均含两者。
- **evictBefore 与 evictAfter 均已被调用**：`WindowOperator.emitWindowContents()` at `nop-stream-runtime/.../operators/windowing/WindowOperator.java:761-811`：line 795 `evictBefore` → line 800-801 `userFunction.process` → line 802 `evictAfter`。从 `onEventTime`(672)/`onProcessingTime`(733)/`processElement` 早触发路径均可到达。
- **关键事实（决定 G46 性质）**：eviction 作用于**局部瞬态副本** `wrapped`（`ArrayList`，771 行从 state 读取构造），**不写回 state**。在 `DISCARDING` 模式 `clearWindowContents`(808) 清空；在 `ACCUMULATING` 模式跳过 clear，故**全量元素跨 firing 持久化，eviction 每次 firing 重新计算**。这与 Flink `EvictingWindowOperator` 的 transient-per-fire eviction 语义**一致**（Flink 同样从 `ListState` 读副本、eviction 不写回、每次重算）。因此 **G46 的正确收口是"核对 + 文档"，而非"持久化裁剪"**——后者会永久丢弃 Flink 会重新淘汰的元素，构成回归。
- 构造接线：`WindowOperatorBuilder` 传 `evictor`/`accumulationMode`(201-202)；evictor≠null 时切 `ListStateDescriptor` + `BufferingAggregateProcessWindowFunction`(115-122)。`WindowOperator` ctor 306-307。
- 测试覆盖：`TestPaneInfoAndAccumulationMode.testEvictorEvictAfterCalled`(167-197) 断言 `evictAfterCalled=true`；仅覆盖 MapState 测试路径（`windowStateDescriptor=null`），生产 `InternalListState` 路径未单独测。
- **无 `EvictingWindowOperator`**：统一由 `WindowOperator`(122-124) 承担（与 `window-design.md` §15:469 决策一致）。

### G47 — 多输入 watermark 合并现状
- **无 `StatusWatermarkValve` 类**：唯一命中是 `nop-stream-core/.../streamrecord/watermark/WatermarkStatus.java:61` javadoc 中**悬空 `@link StatusWatermarkValve`**（指向从未创建的类）。
- 等价物已存在且**已 N 输入可用**：`IndexedCombinedWatermarkStatus.forInputsCount(int)` at `.../common/eventtime/IndexedCombinedWatermarkStatus.java:41-52`（参数本就是 int，非硬编码 2）；`CombinedWatermarkStatus` min-combine at `.../common/eventtime/CombinedWatermarkStatus.java:77-78`（`Math.min`），idleness 74-83，advance 85-88。
- **真正硬编码 2 的位置**：`AbstractStreamOperator.processWatermark(mark,index)`(324-331) 与 status 路径(346-358) 中 `IndexedCombinedWatermarkStatus.forInputsCount(2)`（line 326 与 349）。即 valve 本身 N-capable，只是 `AbstractStreamOperator` 的**输入数来源未定义**。
- **dormant 现实**：nop-stream **零 `implements TwoInputStreamOperator`**，零 `processWatermark1/2`(333-339) 调用者。合并路径在任何 live 执行中都不触发。source 侧 `WatermarkOutputMultiplexer`(`.../common/eventtime/WatermarkOutputMultiplexer.java:50`) 仅被自身单测引用。

### G48 — pane 跟踪现状
- `PaneInfo`(`.../windowing/PaneInfo.java:15-45`，字段 `index`/`isFirst`/`isLast`/`timing`，`PaneTiming` EARLY/ON_TIME/LATE 40-44)；`AccumulationMode`(`.../windowing/AccumulationMode.java:10-15`，值为 `DISCARDING`/`ACCUMULATING`/`ACCUMULATING_AND_RETRACTING`)；`PaneState` 存在但 `WindowOperator` 未用。
- **early/on-time/late 已实现并暴露**：`WindowOperator.computePaneInfo(K,W)`(813-839)：watermark(821)、ON_TIME 首跨(825-827)、LATE 后续(828-829)、EARLY 否则(830-831)、isFirst(834)、index(835-836)。`emitWindowContents` 766-767 写 `processContext.currentPaneInfo`；`WindowContext.getPaneInfo()`(1690-1692) 暴露。
- **真实 gap（正确性级）**：
  1. `isLast` 在 line 838 **硬编码 `false`**。且 `computePaneInfo`(813-838) 在 `emitWindowContents` 内构造并**立即随 emit 输出**（766-767），`PaneInfo` 不可变——清理时（`clearWindowContents` 1115）已无法回填已输出的 `isLast`。故"清理时回填 isLast=true"在当前架构下**时间上不可行**。
  2. `paneTracking` Map（`WindowOperator.java:193`）位于"// State that is not checkpointed"横幅下(195-197)；`snapshotState`(435-456) 只持久化 `trigger-accumulators` + `internal-timers`。**重启后 pane index/onTimeEmitted 丢失** → 恢复后窗口首次触发又被误判为 ON_TIME/isFirst。`paneTracking` 键为 `paneKey`（841-843）= `key + "\u0000" + windowNamespace(window)`；`PaneTrackingInfo`(845-848)={paneIndex,onTimeEmitted} 无序列化器。`windowNamespace`(1330) 的泛型分支用 `window.getClass().getName()+"#"+window.toString()` **不可逆**（非 `TimeWindow` 无法在 restore 重建 `W`）。
  3. `ACCUMULATING_AND_RETRACTING` 是死枚举：`WindowOperator` 只在 808 分支 DISCARDING，ctor 归一为 ACCUMULATING(325)；全仓无 retract 逻辑。
- 测试覆盖：`TestPaneInfoAndAccumulationMode`（early 76-88、onTime 90-108、late 110-133、DISCARDING 135-149、ACCUMULATING 151-165、anti-hollow 199-210）。

### 过时 owner docs（本 plan 须同步）
- `time-model-design.md` §6.4(196) 声称 `watermarkInterval=0L` 硬编码 —— **STALE**（实为 `TimestampsAndWatermarksOperator.DEFAULT_WATERMARK_INTERVAL_MS=200`，line 30，可配置）；§8(224) 未提 `IndexedCombinedWatermarkStatus`、`StatusWatermarkValve` 为悬空名；§9 item 1-2(229-230) STALE。
- `window-design.md` §3 交互流程图(51) 与 §8.7(265-277) 只画单次 `evictor.evict(...)` —— 未反映 before/after 两次调用现实；§11(370-382) 未提 evictAfter 的瞬态语义；§13(407 声称 `emitWindowContents` 不更新 PaneState) STALE；§6(117-121) AccumulationMode 表用 `RETRACTING` 与代码枚举 `ACCUMULATING_AND_RETRACTING` 不符且未标注 spec-only。

## Goals

- **G46**：核对 evictBefore/evictAfter 调用接线（反空壳，含生产 `InternalListState` 路径），**确认 transient-per-fire 淘汰与 Flink 一致**，并文档化该语义；**不引入裁剪持久化**（避免回归）。
- **G47**：修正 `WatermarkStatus.java:61` 悬空 `@link`；裁定 `AbstractStreamOperator` 输入数来源（因无两输入算子，裁定为"dormant、保留 2 输入路径但记录 N-capable 现状 + successor"）；显式 Anti-Hollow 豁免（valve 单测级验证，e2e 归两输入算子 successor）。**不在无消费者前提下建造两输入算子或新 valve**。
- **G48**：使 `paneTracking` 参与 checkpoint/restore（限定 `TimeWindow` 可逆键 + 序列化 DTO + 在 open 阶段恢复，与 timer 恢复序对齐）；`isLast` 裁定为"清理前不可知"并文档化（不采用时间上不可行的回填方案）；`ACCUMULATING_AND_RETRACTING` 标 spec-only 并在未实现分支抛异常、doc 同步为代码枚举名。
- 同步纠正 `window-design.md` 与 `time-model-design.md` 的过时段落。

## Non-Goals

- 实现 evictAfter 裁剪持久化（经核对为回归，非修复）。
- 实现第一个两输入算子（G47 完整价值需它，属 successor；本 plan 只文档化 + 修正悬空引用）。
- 实现 `ACCUMULATING_AND_RETRACTING` 的 retract 逻辑（spec-only，deferred）。
- 用更丰富 `PaneState` 模型替换 live `PaneTrackingInfo`。
- 将 evictor 改为独立算子（§15 已拒绝）。
- source 侧 `WatermarkOutputMultiplexer` 接线。

## Scope

### In Scope

- G46：调用接线反空壳核对（含生产路径）+ transient 语义文档化 + 聚焦测试覆盖生产路径。
- G47：修正悬空 `@link` + 输入数来源裁定 + Anti-Hollow 豁免 + `time-model-design.md` 同步。
- G48：`paneTracking` snapshot/restore（限定 `TimeWindow` + DTO + open 阶段恢复）+ `isLast` 裁定 + RETRACTING spec-only + 聚焦测试 + `window-design.md` 同步。

### Out Of Scope

- evictAfter 持久化。
- 两输入算子本体。
- retract 逻辑实现。
- `PaneState` 模型替换。
- source 侧 multiplexer 接线。

## Execution Plan

### Phase 1 — G46 Evictor 接线核对 + transient 语义文档化

Status: completed
Targets:
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`（`emitWindowContents` 761-811，只读核对）
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/`（生产路径 evictor 测试）
- `ai-dev/design/nop-stream/window-design.md` §3/§8.7/§11

- Item Types: `Proof | Decision`

- [x] `Proof` 反空壳核对：追踪确认 `evictBefore`(795) 与 `evictAfter`(802) 在 firing 路径被调用，且 eviction 作用于局部瞬态 `wrapped`(771) 不写回 state；ACCUMULATING 模式跳过 clear(808) 故元素跨 firing 持久化、eviction 每次 firing 重算。
- [x] `Proof` 新增聚焦测试覆盖**生产 `InternalListState` 路径**（非仅现有 MapState 测试路径）：ACCUMULATING + evictor 场景下，跨两次 firing 验证 eviction 为瞬态重算（第二次 firing 看到完整元素集 + eviction 重新应用），与 Flink transient 语义一致。
- [x] `Decision` 裁定 G46 收口为"核对 + 文档"：明确**不引入裁剪持久化**（持久化会丢弃 Flink 会重淘汰的元素，构成回归）。决策写入 `window-design.md`。

Exit Criteria:

- [x] evictBefore/evictAfter 调用接线经反空壳核对（生产路径追踪）
- [x] 生产 `InternalListState` 路径有聚焦测试证明瞬态重算语义
- [x] G46 不做裁剪持久化的裁定已写入 `window-design.md`（含 Flink transient 语义对照理由）
- [x] `window-design.md` §3/§8.7 更新为 before/after 两次调用现实 + 瞬态语义；§11 注明 evictAfter 不持久化
- [x] **接线验证**：测试断言 evictBefore/evictAfter 被实际调用 + eviction 不写回 state（读取持久化元素集验证瞬态）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — G47 悬空引用修正 + 输入数来源裁定 + Anti-Hollow 豁免

Status: completed
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/streamrecord/watermark/WatermarkStatus.java`（line 61 悬空 `@link`）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java`（`processWatermark(mark,index)` 324-331 输入数来源）
- `nop-stream/nop-stream-core/src/test/.../common/eventtime/`（valve 数学单测，如缺）
- `ai-dev/design/nop-stream/time-model-design.md` §5.3/§6.4/§8/§9

- Item Types: `Fix | Decision | Proof`

- [x] `Fix` 修正 `WatermarkStatus.java:61` 悬空 `@link StatusWatermarkValve` → 指向 live 的 `IndexedCombinedWatermarkStatus`/`CombinedWatermarkStatus`（或改为准确文字，不含悬空 `@link`）。
- [x] `Decision` 裁定 `AbstractStreamOperator` 输入数来源：因无两输入算子，valve 为 dormant。裁定为——保留现有 2 输入路径但记录现状（输入数当前为字面量 2，待两输入算子 successor 提供真实输入数），**不在本 plan 建造两输入算子或新 valve**（避免空壳）。决策写入 `time-model-design.md`。
- [x] `Proof` 确认 valve 数学（`CombinedWatermarkStatus` min-combine + idleness）有单测覆盖；若缺则补 N 输入 min-combine/idleness 单测（valve 本身 N-capable，单测级验证即可）。

Exit Criteria:

- [x] `WatermarkStatus.java:61` 不再含悬空 `@link`
- [x] 输入数来源裁定写入 `time-model-design.md`（dormant 现状 + successor 路径 + 不建造无消费者算子的理由）
- [x] valve 数学有单测覆盖（min-combine + idleness）
- [x] `time-model-design.md` §6.4/§9 item 1-2 修正（interval 默认 200、已接入执行路径），§5.3 补 `IndexedCombinedWatermarkStatus`，§8 注明 valve N-capable 但 dormant + Anti-Hollow 豁免
- [x] **Anti-Hollow 豁免（显式）**：G47 valve 为单测级验证 by design；e2e/wiring 验证（运行时生效）显式 defer 至两输入算子 successor——本 phase 不要求运行时生效，因无消费者（建造消费者即空壳）。该豁免写入 plan 与 design doc。
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — G48 paneTracking 持久化 + isLast 裁定 + RETRACTING spec-only

Status: completed
Targets:
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`（`paneTracking` 193、`snapshotState` 435-456、restore 路径、`computePaneInfo` 813-839、`AccumulationMode` 325）
- `nop-stream/nop-stream-runtime/src/test/.../operators/windowing/TestPaneInfoAndAccumulationMode.java`
- `ai-dev/design/nop-stream/window-design.md` §6/§13

- Item Types: `Fix | Decision | Proof`

- [x] `Fix` 使 `paneTracking`(193) 参与 `snapshotState`(435-456) 与 restore：新增 `PaneTrackingInfo` 的序列化 DTO；**键限定为可逆的 `TimeWindow`**（`paneKey` 用可重建的 TimeWindow 表示；非 TimeWindow 窗口的 paneTracking 不参与持久化并在文档注明限制，避免 `windowNamespace` 不可逆导致 restore 错配）；restore 在 `open` 阶段、state backend 初始化后应用（与 timer deferred-restore 序对齐）。
- [x] `Decision` 裁定 `isLast`：`PaneInfo.isLast`(838) 清理前不可知，且 `computePaneInfo` 在 emit 前构造不可变 PaneInfo，清理时回填在当前架构时间上不可行——裁定为"清理前不可知，恒为 false"并文档化为已知契约限制（**不采用回填方案**）。若未来需要，须改为清理时独立 emit 一次 cleanup pane（属未来增强，本 plan 不做）。
- [x] `Fix` `ACCUMULATING_AND_RETRACTING` 未实现分支抛 `UnsupportedOperationException`（非静默当作 ACCUMULATING）；`window-design.md` §6 标 spec-only 且**将 `RETRACTING` 改为代码枚举名 `ACCUMULATING_AND_RETRACTING`**。
- [x] `Proof` 新增聚焦测试：注册 pane → snapshot → restore → 验证 pane index/onTimeEmitted 续接（恢复后窗口触发不再被误判为 ON_TIME/isFirst），限定 `TimeWindow`。

Exit Criteria:

- [x] `paneTracking` 在 snapshot/restore 中持久化与恢复（TimeWindow 限定，DTO + open 阶段恢复）
- [x] 聚焦测试证明恢复后 pane 状态续接（非首次触发误判）
- [x] `isLast` 裁定为"清理前不可知"并文档化（含回填不可行的理由）
- [x] `ACCUMULATING_AND_RETRACTING` 未实现分支抛异常（非静默），spec-only 文档化且 doc 用代码枚举名
- [x] `window-design.md` §6（RETRACTING→`ACCUMULATING_AND_RETRACTING` spec-only）、§13（live pane 现状 + isLast 裁定 + checkpoint 事实 + TimeWindow 限制）更新
- [x] **端到端验证**：至少一条测试覆盖 注册 pane → window 触发 emit → snapshot → restore → 再次触发，pane info 连续正确
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] G46 收敛：evictBefore/evictAfter 接线经反空壳核对（含生产路径），transient 语义与 Flink 一致并文档化；未引入持久化回归
- [x] G47 收敛：悬空 `@link` 修正 + 输入数来源裁定 + dormant/Anti-Hollow 豁免文档化；未建造无消费者算子
- [x] G48 收敛：paneTracking 持久化（TimeWindow 限定）、isLast 裁定、RETRACTING spec-only
- [x] `window-design.md` 与 `time-model-design.md` 列出的过时段落已同步至 live baseline
- [x] 无 in-scope live defect 被静默降级（RETRACTING、两输入算子、evictAfter 持久化为明确 out-of-scope，非 in-scope defect 降级）
- [x] `./mvnw compile -pl nop-stream -am`
- [x] `./mvnw test -pl nop-stream -am -T 1C`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` 退出码 0
- [x] 独立子 agent closure-audit 完成并写入证据

## Deferred But Adjudicated

### 第一个两输入算子（connect/union/join）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: G47 valve 已 N-capable 且有数学单测；但无两输入算子消费者时建造它即空壳（违反 Anti-Hollow）。valve 的运行时生效（e2e/wiring）显式 defer 至两输入算子 successor。本 plan 的 G47 Anti-Hollow 豁免即基于此。
- Successor Required: `yes`
- Successor Path: 未来两输入算子 plan

### ACCUMULATING_AND_RETRACTING retract 逻辑

- Classification: `optimization candidate`
- Why Not Blocking Closure: retract 属上层语义扩展，nop-stream 无 retract 下游消费者；本 plan 标 spec-only 并让未实现分支快速失败。
- Successor Required: `no`

## Non-Blocking Follow-ups

- source 侧 `WatermarkOutputMultiplexer` 仍未接线；若 Stage 49 source split 需要，再接入。
- `isLast` 若未来需要真实语义，可改为清理时独立 emit cleanup pane（本 plan 不做）。
- 非 `TimeWindow` 窗口的 paneTracking 持久化受 `windowNamespace` 不可逆限制；未来可扩展可逆 namespace 编码。

## Closure

Status Note: G46/G47/G48 三个 P2 集成缺口收口。经 live 核对，每个 gap 的现状与 roadmap 假设有偏差，本 plan 据此把每个 gap 收口为"核对 + 补真实 gap + 文档"，避免建造无消费者的空壳。475 tests pass。
Completed: 2026-07-25

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (general, fresh session ses_0668bdb90ffeOEKvBSyZda7yZp)
- Audit Session: read-only verification of all 10 checks against live code/test/docs
- Evidence:
  - G46 gate: PASS — `WindowOperator.emitWindowContents()` evictBefore(843)→process(848)→evictAfter(850), eviction on local transient `wrapped`(819) not written back; DISCARDING-only clear(856-858). Test `testEvictionIsTransientPerFireOnProductionListStatePath` (TestEvictorIntegration.java:131) uses ListStateDescriptor production path + ACCUMULATING, asserts sizesSeen=[1,2,3,4]. Docs §3/§8.7/§11 updated.
  - G47 gate: PASS — `StatusWatermarkValve` grep across nop-stream = ZERO matches; `WatermarkStatus.java:61` now links `IndexedCombinedWatermarkStatus`. `TestIndexedCombinedWatermarkStatus` (6 tests: N-input min-combine + idleness). `time-model-design.md` §6.4 (interval=200), §5.4 (input-count decision + Anti-Hollow exemption), §9 updated.
  - G48 gate: PASS — `snapshotState` writes `"pane-tracking"`(495-497), `restoreState` captures(528-531), `open()` applies(366-374); `PaneTrackingSnapshot` DTO(928) + `isTimeWindowPaneKey` filter(899-901). `open()` throws on ACCUMULATING_AND_RETRACTING(347-353). `window-design.md` §6 (ACCUMULATING_AND_RETRACTING spec-only) + §13 (isLast decision, checkpoint facts, TimeWindow limit). Tests `testPaneTrackingSurvivesCheckpointRestore`(225) + `testRetractingModeFailsFastOnOpen`(290).
  - All exit criteria met; no in-scope defect silently downgraded (RETRACTING/two-input/evictAfter-persist are explicit out-of-scope).
  - `./mvnw test -pl nop-stream -am -T 1C` → 475 pass, 0 failures. `scan-hollow --severity high` exit 0. `check-plan-checklist --strict` exit 0.
