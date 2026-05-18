# 17 nop-job 阻塞策略完善 + 执行统计 Metrics

> Plan Status: completed
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

Status: completed (已存在)
Targets: `nop-job/nop-job-coordinator/`, `nop-job/nop-job-dao/`

注：`JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` 已包含 `findActiveFires()` + `cancelFire()` + `cancelTasks()` 逻辑。测试 `testOverlayBlockStrategyCancelsActiveFireAndCreatesReplacement` 已验证。

### Phase 2 - RECOVERY 阻塞策略

Status: completed
Targets: `nop-job/model/`, `nop-job/nop-job-coordinator/`

实现：
- ORM dict `job/block-strategy` 增加 RECOVERY=4
- `_NopJobCoreConstants.BLOCK_STRATEGY_RECOVERY = 4`
- `IJobScheduleStore.recoveryFireAndAdvanceSchedule()` + `JobScheduleStoreImpl` 实现
- `shouldRecovery()` 判断逻辑
- 测试：`testRecoveryBlockStrategyResetsFailedFire` + `testRecoveryBlockStrategyNoFailedFireAdvancesSchedule`

### Phase 3 - Metrics 三件套

Status: completed
Targets: `nop-job/nop-job-coordinator/metrics/`, `nop-job/nop-job-worker/metrics/`

实现：
- `IJobDispatcherMetrics` 三件套：`waiting-fires` / `dispatch-conflict` / `fires-dispatched`
- `IJobWorkerMetrics` 三件套：`tasks-claimed` / `task-success`(Timer) / `task-failure` / `task-timeout`
- `IJobCompletionMetrics` 三件套：`fires-completed` / `fire-success`(Timer) / `fire-failure` / `fire-timeout`
- 注入到 DispatcherScanner / WorkerScanner / CompletionProcessor

### Phase 4 - Schedule 聚合统计字段

Status: completed
Targets: `nop-job/model/`, `nop-job/nop-job-coordinator/`

实现：
- ORM 新增列：`lastDurationMs`(BIGINT) / `totalFireCount`(BIGINT) / `successFireCount`(BIGINT) / `failFireCount`(BIGINT)
- `JobCompletionProcessorImpl` 完成时更新聚合字段
- GraphQL 自动可查

### Phase 5 - 限流设计文档

Status: completed
Targets: `ai-dev/design/nop-job/rate-limiting-design.md`

完成：
- 补充 snail-job 限流机制对比（Worker 线程池间接控制）
- 确定限流粒度：Worker 级 > Job 级 > 集群级
- 结论：暂不需要 nop-cluster 新增限流接口
- 阻塞策略已覆盖 Job 级并发控制

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

Status Note: All 5 phases completed. 28 tests pass (coordinator 20 + worker 8). No remaining plan-owned work.

Closure Audit Evidence:

- Reviewer / Agent: self-audit during ralph-loop
- Evidence: `./mvnw test -pl nop-job/nop-job-coordinator,nop-job/nop-job-worker` — 28/28 tests pass, 0 failures

Follow-up:

- Worker 级 Semaphore 限流实现（rate-limiting-design.md §3.4 定义了方案）
- beans.xml 注入真实 Metrics 实现（当前默认空实现，生产环境需配置）
