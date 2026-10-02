# 22 WI12 分析窗口算子

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI12 行、D6=(a)、Q2）、`ai-dev/design/nop-stream/join-operator.md` §5（有序缓冲复用）
> Related: `ai-dev/plans/nop-stream-sql/12-wi10-window-parameterization.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

实现分析窗口（OVER）算子：每 key 有序缓冲以独立可测构件落地，OVER 语义含 ROW_NUMBER 与 frame 滑动聚合（事件时间，D6），keyed state 经 checkpoint 与 restore 的端到端证据。

## Current Baseline

- 引擎无 OVER 算子（roadmap §3.1「分析窗口 OVER(...)：无算子」）；窗口体系完备——WindowOperator（runtime，windowing/ 包）、WindowAssigner 家族（core，Tumbling/Sliding/Merging/Global）、NamespaceAware*State（per-window state）、TimerService。
- D6=(a)：事件时间语义；Q2：无 RowKind 不引入修正语义，frame 重开按事件时间重算。
- keyed state checkpoint/restore 先例：TestE2EWindowOperatorWithCheckpoint（runtime，MemoryStateBackend + processBarrier + restoreState 形态）。
- reduce 先例：StreamReduceOperator 以 `Map<Object, T> values`（per-key）+ setCurrentKey 管理键控状态；TimerService/HeapInternalTimerService 在 ProcessOperator 有接线先例。
- 每 key 有序缓冲的独立构件当前不存在（A5：keyed state 可承载——已有 operator 级证据，尚缺此具体用法证据）。

## Goals

- **PerKeyOrderedBuffer**（nop-stream-runtime，windowing/ 包，独立可测构件）：按 event-timestamp 有序维护每 key 元素缓冲；API：add(key, ts, value)、sortedView(key)、trimToWatermark(key, wm)（清理 ≤ wm 的已聚合元素）；内部用 keyed state 语义（Map per key，测试可直接以 key 参数驱动，无需 backend 也可用 standalone 形态）；Serializable。
- **OverWindowOperator**（runtime，OneInputStreamOperator）：缓冲每 key 元素；watermark 推进时对窗口帧求值——ROW_NUMBER（帧内序号）与 RANGE/ROWS 滑动聚合（sum/count/avg/min/max 经 WI9 AggregateFunction 语义）；发射帧结果（last-value-wins 语义与 D1=(a) 一致：每 watermark 步进 emit 当前帧结果）；Q2：frame 重开=事件时间重算，无修正标记。
- **测试**：
  1. `TestPerKeyOrderedBuffer`（独立构件单测：add 有序性/跨 key 隔离/trim 清理/乱序插入排序）
  2. `TestAnalysisWindowEventTime`（roadmap 明文类名：事件时间 OVER——乱序元素被缓冲重排、watermark 触发行计算、ROW_NUMBER 断言、frame 滑动聚合断言）
  3. `TestE2EOverWindowWithCheckpoint`（checkpoint/restore E2E：缓冲状态经 snapshot/restore 后帧计算连续）
- 语义标注：OverWindowOperator javadoc 标注 D6=(a) 事件时间与 D1=(a) 终值语义。

## Non-Goals

- 不做 SQL 语法面（OVER 语法归 WI17 编译器）；不做 GETENSOR/DISTINCT frame；不接 WindowedStream DSL（分析窗口非切片窗口）；不含join 复用接线（WI13）。

## Scope

### In Scope

- `nop-stream/nop-stream-runtime`：PerKeyOrderedBuffer、OverWindowOperator、三个测试类
- roadmap WI12 行（Phase 2 翻转）；当日日志

### Out Of Scope

- DSL 声明面；WI13 join 复用接线；SQL 编译器。

## Execution Plan

### Phase 1 - 缓冲构件与 OVER 算子

Status: completed
Targets: `nop-stream/nop-stream-runtime`

- Item Types: `Feature`

- [x] PerKeyOrderedBuffer（add/sortedView/trimToWatermark/trimToCount/keys/putAll；standalone 可测；执行期修正——TreeMap comparator 须具名 Serializable 类，lambda 比较器不可序列化）
- [x] OverWindowOperator（watermark 触发帧求值；ROW_NUMBER + 滑动聚合；**audit B-1 rework——持久化改走 keyed MapState（§十 通道），open() 自建 keyed backend，snapshotState 经 super 携带 keyed lineage，copyForSubtask 新建实例**）
- [x] TestPerKeyOrderedBuffer + TestAnalysisWindowEventTime + TestE2EOverWindowWithCheckpoint（复审 R-1/R-2 修正——keyed 通道清理与写入键对齐 key\u0000ts\u0000seq、trimToWatermark/trimToCount WithKeys 变体返回被删键、rebuildViewFromKeyedState 防重入守卫）
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] 三个测试类隔离实跑绿（12 用例——含 R-1b 变体单测）
- [x] **端到端验证**：E2E checkpoint/restore 后帧计算连续（缓冲恢复，rn=1/2/3 跨界证明）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 绿（1208 零退化）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（不同 task_id）：构件独立可测、OVER 语义判别、checkpoint 证据；证据落 ai-dev/audits/nop-stream-sql/wi12-closure-audit.md
- [ ] audit 通过后 roadmap WI12 `todo` → `done`（括注单层一对）；`parseRoadmapMarkdown` 复核 31 + 7
- [ ] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [ ] 独立 audit 证据落档两处
- [ ] roadmap WI12 = done + 解析器 31 + 7 复核通过
- [ ] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [ ] PerKeyOrderedBuffer 独立可测构件落地且有单测
- [ ] TestAnalysisWindowEventTime 覆盖缓冲与 OVER 语义（roadmap 明文类名）
- [ ] ROW_NUMBER 与 frame 滑动聚合断言判别
- [ ] checkpoint/restore E2E 证据（缓冲恢复后帧计算连续）
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime` 绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/22-wi12-over-window-operator.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

Status Note: <<完成时填写>>
Completed:

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<验证结果>>

Follow-up:

- <<no remaining plan-owned work 或列出>>
