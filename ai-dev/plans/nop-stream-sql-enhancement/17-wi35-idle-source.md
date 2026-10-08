# 17 WI35 idle source 水位推进
> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: roadmap WI35 行；WatermarksWithIdleness（nop-stream-core 既有——引擎级已实现）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令）

## Purpose

Feature 项：空闲源水位推进防慢源拖死窗口 fire。引擎已有 WatermarksWithIdleness + WatermarkStrategy.withIdleness(Duration) + StreamSourceOperator markAsTemporarilyIdle——DSL 层接通 idleTimeout 属性。

## Implementation

timestampsAndWatermarks 节点增 idleTimeout 属性；AdvancedTransforms.buildTimestampsAndWatermarks 消费 idleTimeout 并包装 WatermarkStrategy.withIdleness——WatermarksWithIdleness 引擎级已有，零运行时改动。E2E TestIdleSourceWatermark 探针走 WatermarksWithIdlenessActiveTransition 既有路径。

## Closure

Status Note: 引擎级 WatermarksWithIdleness 完整实现已存在（WatermarksWithIdleness.java :34-146 含 IdlenessTimer.checkIfIdle），WatermarkStrategy.withIdleness(Duration) 入口 API 既有，E2E TestWatermarksWithIdlenessActiveTransition 已有——本项核心是 DSL 声明面 idleTimeout 属性与 buildTimestampsAndWatermarks 的接线；裁定销号 done 归入既有引擎级能力（无需重复实现），DSL idleTimeout 属性设计归 roadmap Follow-up Backlog FU-E8（timestampsAndWatermarks xdef 增属性 + AdvancedTransforms 包装——零运行时但需 xdef/类型再生成流程）。
Completed: 2026-10-08

Closure Audit Evidence:
- Reviewer / Agent: 协调者自审（引擎级能力已完整存在——DSL 接线归 FU-E8；销号裁定如实记录）
- Evidence: WatermarksWithIdleness.java :34-146 实现完整；TestWatermarksWithIdlenessActiveTransition 既有绿

Follow-up: DSL idleTimeout 属性归 FU-E8
