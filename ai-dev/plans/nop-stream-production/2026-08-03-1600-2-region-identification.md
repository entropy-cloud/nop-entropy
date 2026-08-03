# 44-B Region Concept and Identification（Region Failover 前置 #2）

> Plan Status: active
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Item 44（successor plan 2/5）; `ai-dev/design/nop-stream/failover-design.md` §五.2（region 概念需求，术语已更新）+ §9.2（blast radius）+ §9.5（scope — plan 级，硬前置为 successor 1）
> Mission: nop-stream-production
> Work Item: 44. Region-based failover — successor plan 2/5（region concept and identification）
> Related: **successor 1** `2026-08-03-1600-1-blocking-edge-materialization-point.md`（物化 marker — 本 plan 硬前置，本 plan 消费其 `JobEdge` 物化标记）; **successor 3** `2026-08-03-1600-3-supervision-loop-execution-model.md`（supervision loop — 硬依赖本 plan 的 region ID）; **决策 plan** `2026-08-03-1403-1-region-based-failover.md`（go confirmed）

## Purpose

在 runtime 引入 region 抽象，并基于 successor 1 的物化 marker（`JobEdge` 上的物化启用标记），将 JobGraph 切分为多个 pipelined connected component（region）。本 plan 交付 region **概念与识别**（抽象 + 分解算法 + region ID 全链路传播到 task），不交付 supervision loop（successor 3）或 drain/reconnect（successor 4）。这是 region-based failover 的"知道重启谁"基础。

## Current Baseline

经 live 仓库核对（2026-08-03）：

- **生产代码零 region 概念**：全仓库仅 `JobCoordinator.java:220,891` 两处 forward-looking 注释提及 "per-region"，无 Region 类、无 region ID、无 region 分解逻辑。
- **JobGraph 持有平铺 vertices/edges**：`JobGraph`（`JobGraph.java:57`）持有 `Map<String,JobVertex> vertices`（`:70`）+ `List<JobEdge> edges`（`:76`）+ `StreamModel`（`:78`），无 region 字段。
- **所有 edge 均 pipelined → 单 connected component → 单 region**（`failover-design.md` §2.1 已证明）：当前任意 JobGraph = 单 region，vertex 级重启 = global。
- **successor 1（draft）将引入 `JobEdge` 物化 marker**：本 plan 在其上构建 region 切分——物化启用 edge 跨 region，未启用 edge 连通同一 region。本 plan 消费 successor 1 的"物化 marker（给 successor 2）"契约。
- **task 构造路径**：`GraphModelCheckpointExecutor.buildTasks()`（`:703-715`）从 `GraphExecutionPlan` 创建 `SubtaskTask`；region ID 需经此路径传播（JobGraph 分解 → region 赋值 → GraphExecutionPlan 携带 → SubtaskTask 可查询）。
- **决策已落**：`failover-design.md` §9.5 item 2 = plan 级；§五.2（术语已更新）定义 region 概念需求。

### 真正剩余的 gap

- Region 抽象 + 分解算法 + ID 全链路传播完全缺失——本 plan 交付对象。
- 依赖 successor 1 的 `JobEdge` 物化 marker 作为切分依据（successor 1 land 后本 plan 可执行）。

## Goals

- **Region 抽象**（Region + region ID 类型）。
- **JobGraph → region 分解**：物化 marker edge 切分 pipelined connected component；无物化 marker 的图 = 单 region（零回归）。
- **region ID 全链路传播**：JobGraph 分解 → region 赋值 → `GraphExecutionPlan` 携带 → `SubtaskTask`/task 可查询自身 region。
- **region 分解正确性**：线性 / 菱形 / 多源汇合等拓扑切分正确，可被 focused test 验证。

## Non-Goals

- supervision loop / mid-execution 重启（successor 3）。
- drain/reconnect（successor 4）。
- region-aware scheduling（G55）——调度优化，非 failover 正确性前置。
- per-region restart 计数器（successor 5）。
- region 边界动态调整（与 Stage 35/37 rescale 交互，后续增强）。
- 跨 JVM region 调度。

## Scope

### In Scope

- Region 抽象（Region class + region ID 类型）。
- JobGraph region 分解算法（connected-component 切分：读取 successor 1 的 `JobEdge` 物化 marker；marker edge 跨 region，无 marker edge 连通）。
- region ID 全链路传播：JobGraph 分解 → 赋值 → `GraphExecutionPlan` 携带 → `SubtaskTask` 可查询。
- region 分解正确性测试（单 region / 双 region 线性 / 菱形 / 多源汇合）。
- 零回归：无物化 marker 的既有作业 = 单 region，既有行为不变。

### Out Of Scope

- successor 3/4/5 项（supervision loop / drain/reconnect / per-region counter）。
- region-aware scheduling（G55）。
- 跨 JVM region 调度。
- region 边界动态调整。

## Execution Plan

### Phase 1 — Region 抽象 + 分解 + 全链路传播

Status: planned
Targets: `nop-stream-core/jobgraph/`（Region 抽象）; `JobGraph.java`; `JobVertex.java`; `GraphExecutionPlan`; `GraphModelCheckpointExecutor.buildTasks()`（region ID 传播）; owner-docs

- Item Types: `Fix | Decision | Proof`

- [ ] Region 抽象（Region class + region ID 类型）——`Fix`
- [ ] JobGraph region 分解算法（connected-component on 无物化 marker 的 edge；物化 marker edge 跨 region；消费 successor 1 的 `JobEdge` 物化标记）——`Fix`
- [ ] region ID 赋值与不可变约束处理——`Decision`：`JobVertex` 类文档（`JobVertex.java`）声明构造后不可变。region ID 赋值方案二选一并记录于 owner-doc：(a) region 分解结果存于独立映射表 `Map<String, RegionId>`（JobVertex 不变）；(b) JobVertex 增设可变 region 字段（需更新不可变契约文档）。推荐 (a) 以最小侵入，最终方案在执行时据 `JobVertex` 实际约束裁定。
- [ ] region ID 全链路传播：region 赋值结果 → `GraphExecutionPlan` 携带 → `GraphModelCheckpointExecutor.buildTasks()`（`:703-715`）写入 `SubtaskTask`——`Fix`
- [ ] region 分解正确性测试：单 region（无物化 marker）/ 双 region（1 物化 marker edge 线性）/ 菱形 / 多源汇合——`Proof`
- [ ] 零回归验证：无物化 marker 的既有作业 = 单 region，既有行为不变——`Proof`

Exit Criteria:

- [ ] Region 抽象存在，`JobGraph` 可分解出 region 集合（region 集合可观测）
- [ ] region ID 全链路传播：`SubtaskTask` 可查询自身 region ID（**接线验证** #23：从 JobGraph 分解到 SubtaskTask 查询的路径连通，断言可观测——不只是 JobVertex 层有值，SubtaskTask 实际拿到）
- [ ] **端到端验证（graph-level，#22 适用性说明）**：含物化 marker edge 的测试 JobGraph 被正确切分为多 region（region 数量 + 成员断言）；每个 vertex/subtask 携带正确 region ID；无物化 marker 的图 = 单 region（断言 region 数 = 1）。本 plan 的"端到端"是**图级结构验证**（region 分解是图级 feature，非数据管线）；数据管线的端到端重启 E2E 属 successor 3。
- [ ] **无静默跳过**（#24）：region 分解遇到无法分类的 edge（如未知 partition type）时 fail-fast（非静默归入默认 region）
- [ ] `JobVertex` 不可变约束的处理方案已裁定并记录（不违反既有契约）
- [ ] 零回归：既有作业（无物化 marker）行为不变，既有测试全绿
- [ ] owner-doc: `failover-design.md` region 识别落地状态（§五.2 implementation status）; `01-architecture-baseline.md` 如引入 region 概念则同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] region 分解正确（单/双/菱形/多源拓扑测试通过）
- [ ] region ID 全链路传播（JobGraph → GraphExecutionPlan → SubtaskTask 可查询）
- [ ] `JobVertex` 不可变约束未被违反（方案已裁定并记录）
- [ ] 零回归（既有作业 = 单 region，既有测试全绿）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] 不存在被静默降级到 deferred 的 in-scope gap
- [ ] 受影响 owner docs 已同步
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：JobGraph 分解→region ID 赋值→GraphExecutionPlan→SubtaskTask 查询 调用链运行时连通；无空方法体/静默跳过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### region-aware scheduling（G55）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: region-aware scheduling 是调度优化（按 region 调度顺序/资源），非 failover 正确性前置；本 plan 只交付 region 识别，调度策略属后续。
- Successor Required: no

## Non-Blocking Follow-ups

- region 边界动态调整（与 Stage 35/37 rescale 交互）——后续增强

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<每条 Exit Criterion / Closure Gate 验证结果 + check-plan-checklist 退出码 + Anti-Hollow 结果>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
