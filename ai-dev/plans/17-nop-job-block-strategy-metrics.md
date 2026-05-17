# 17 nop-job 阻塞策略完善 + 执行统计 Metrics

> Plan Status: draft
> Last Reviewed: 2026-05-17
> Source: `ai-dev/analysis/2026-05-17-snail-job-vs-nop-job-comparison.md` §2.4, §2.5, §4 P0/P1
> Related: 15-nop-job-invoker-implementation-plan.md, 16-nop-job-core-redesign.md

## Purpose

收口 nop-job 阻塞策略的缺失项（OVERLAY cancel 逻辑、RECOVERY 策略）和执行统计 metrics 体系，使 nop-job 的调度能力达到可替代 snail-job 的水平。

## Current Baseline

- `JobPlannerScannerImpl` 已实现 DISCARD（跳过）和 OVERLAY（新建 fire 覆盖旧的）两种阻塞策略
- OVERLAY 路径没有 cancel 旧的 active fire — `shouldOverlay` 只调用 `overlayFireAndAdvanceSchedule`，未通过 `IJobCancelHandler` 取消正在执行的旧 fire
- ORM dict `job/block-strategy` 有 DISCARD=1, OVERLAY=2, PARALLEL=3，缺少 RECOVERY=4
- 已有 `IJobPlannerMetrics` 三件套（planner 层），Dispatcher/Worker/Completion 层没有 metrics
- `NopJobFire` 已有 `fireStatus`, `startTime`, `endTime`, `durationMs` — 基础执行记录完整
- `NopJobSchedule` 没有聚合统计字段（总执行次数、成功率等）

## Goals

- OVERLAY 时正确 cancel 当前正在执行的 fire
- 新增 RECOVERY 阻塞策略
- 实现 Planner/Dispatcher/Worker/Completion 四层 Metrics 体系
- 在 `NopJobSchedule` 上增加聚合统计字段，可通过 GraphQL 查询

## Non-Goals

- 告警通知（用户明确暂不处理）
- 限流实现（先完成 `ai-dev/design/nop-job/rate-limiting-design.md` 设计，实现放后续 plan）
- Map/MapReduce（后续通过 nop-task 实现）
- 独立的 `job_summary` 表（先在 schedule 上聚合，够用后再考虑）

## Scope

### In Scope

- OVERLAY cancel 逻辑
- RECOVERY 阻塞策略（ORM dict + 判断逻辑）
- `IJobDispatcherMetrics` / `IJobWorkerMetrics` / `IJobCompletionMetrics` 三件套
- `NopJobSchedule` 聚合统计字段（ORM + CompletionProcessor 更新 + GraphQL 可查）
- 限流设计文档（仅文档，不实现代码）

### Out Of Scope

- 告警通知
- Map/MapReduce
- 限流实现
- Dashboard UI
- job_summary 独立表

## Execution Plan

### Phase 1 - OVERLAY Cancel 逻辑

Status: planned
Targets: `nop-job/nop-job-coordinator/`, `nop-job/nop-job-dao/`

- Item Types: `Fix`

- [ ] 在 `shouldOverlay` 路径中，overlay 前先查找并 cancel 当前 active fire
- [ ] `IJobScheduleStore` 增加 `findActiveFires(String scheduleId)` 方法

Exit Criteria:

- [ ] OVERLAY 时能正确取消正在执行的 fire
- [ ] `TestJobCoordinatorScanner` 新增 overlay cancel 测试用例且通过
- [ ] `ai-dev/design/nop-job/block-strategy-design.md` 已更新为最终状态

### Phase 2 - RECOVERY 阻塞策略

Status: planned
Targets: `nop-job/model/`, `nop-job/nop-job-coordinator/`

- Item Types: `Fix`

- [ ] ORM dict `job/block-strategy` 增加 RECOVERY=4
- [ ] `JobPlannerScannerImpl` 新增 `shouldRecovery()` 判断逻辑
- [ ] 重置失败 fire 状态为 WAITING（而非创建新 fire）

Exit Criteria:

- [ ] ORM dict 有 RECOVERY=4，`./mvnw install` 从 `nop-job/` 执行通过
- [ ] 测试覆盖 RECOVERY 场景（失败 fire → 自动重触发）
- [ ] `ai-dev/design/nop-job/block-strategy-design.md` 已包含 RECOVERY 决策

### Phase 3 - Metrics 三件套

Status: planned
Targets: `nop-job/nop-job-coordinator/metrics/`, `nop-job/nop-job-worker/metrics/`

- Item Types: `Fix`

- [ ] `IJobDispatcherMetrics` 三件套：接口 + 真实实现 + 空实现
- [ ] `IJobWorkerMetrics` 三件套
- [ ] `IJobCompletionMetrics` 三件套
- [ ] 注入到对应组件（DispatcherScanner / Invoker / CompletionProcessor）

Exit Criteria:

- [ ] 每个 metrics 接口都有三件套（接口 + Impl + EmptyImpl）
- [ ] 真实实现使用 `GlobalMeterRegistry.instance()`，构造函数预创建 Counter/Timer
- [ ] 业务组件默认持有空实现，通过 beans.xml 注入真实实现
- [ ] 符合 `ai-dev/lessons/02-metrics-design-convention.md` 规范
- [ ] `./mvnw test` 通过

### Phase 4 - Schedule 聚合统计字段

Status: planned
Targets: `nop-job/model/`, `nop-job/nop-job-coordinator/`

- Item Types: `Fix`

- [ ] ORM 变更：`NopJobSchedule` 增加 `lastFireTime`, `lastFireStatus`, `lastDurationMs`, `totalFireCount`, `successFireCount`, `failFireCount`
- [ ] `JobCompletionProcessorImpl` 完成时更新聚合字段

Exit Criteria:

- [ ] `./mvnw install` 从 `nop-job/` 执行通过（含 codegen 链）
- [ ] 聚合字段可通过 GraphQL `NopJobSchedule__findPage` 查询
- [ ] 测试验证聚合字段递增逻辑

### Phase 5 - 限流设计文档

Status: planned
Targets: `ai-dev/design/nop-job/rate-limiting-design.md`

- Item Types: `Decision`

- [ ] 补充 snail-job 限流机制对比
- [ ] 确定限流粒度（Worker 级 / Job 级 / 集群级）
- [ ] 确定是否需要 nop-cluster 新增限流接口

Exit Criteria:

- [ ] `ai-dev/design/nop-job/rate-limiting-design.md` 包含与 snail-job 的对比分析
- [ ] 包含明确的限流粒度决策和理由
- [ ] 包含是否需要 nop-cluster 新增接口的结论

## Closure Gates

- [ ] 所有 ORM 变更通过 `./mvnw install` 验证
- [ ] 阻塞策略测试覆盖 DISCARD / OVERLAY / PARALLEL / RECOVERY 四种场景
- [ ] 所有 metrics 接口符合三件套规范
- [ ] Schedule 聚合字段可被 GraphQL 查询
- [ ] 限流设计文档完成（代码不实现）
- [ ] 受影响的 `ai-dev/design/nop-job/` 设计文档已更新到最终状态
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] `./mvnw compile` (或 `-pl` 指定模块)
- [ ] `./mvnw test` (或 `-pl` 指定模块)
- [ ] checkstyle / 代码规范检查通过
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### 告警通知

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户明确暂不处理，不影响调度核心能力
- Successor Required: `yes`
- Successor Path: 后续 plan

### 限流实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 只产出设计文档，实现依赖设计结论
- Successor Required: `yes`
- Successor Path: 后续 plan

### Map/MapReduce

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 通过 nop-task 编排实现，独立于调度核心
- Successor Required: `yes`
- Successor Path: 后续 plan

## Non-Blocking Follow-ups

- Dashboard UI（聚合字段已可 GraphQL 查询，UI 后续迭代）
- job_summary 独立表（当前 schedule 聚合够用，统计维度超出时再考虑）

## Closure

Status Note: <<完成时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<完成时填写：no remaining plan-owned work 或列出 non-blocking follow-up>>
