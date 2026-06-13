# 14 nop-job 质量优化

> Plan Status: completed
> Last Reviewed: 2026-05-11
> Completed: 2026-05-11
> Source: nop-job 模块代码审查（Session 对话中逐文件核查）
> Related: 04-nop-job-rewrite-implementation-plan.md

## Purpose

对 nop-job 重写后的实现进行质量收敛：修复已确认的 N+1 查询问题、补充 planner 锁冲突可观测性、清理遗留代码、统一内部错误消息规范。

## Current Baseline

- nop-job 已完成 schedule/fire/task 三层模型重写，核心链路（Planner → Dispatcher → Worker → Completion → Timeout）全部落地
- 所有 coordinator store 的写操作已使用 `@Transactional(REQUIRES_NEW)`，事务隔离正确
- Worker 使用 `invokeAsync` + `whenComplete` 异步回调模式，不阻塞 scanner 线程
- 测试覆盖了 store 层 CRUD、coordinator 四个 scanner 端到端、worker 执行、BizModel 管理接口
- **遗留问题**：TimeoutChecker 存在 N+1 查询；Planner 锁冲突无 metric；coordinator/worker 内部硬编码英文错误消息；`nop-job-core` 中旧 `LocalJobScheduler` 等类仍在

## Goals

- 消除 TimeoutChecker 的 N+1 查询，改为批量预加载
- 为 Planner 锁冲突添加可观测 metric
- coordinator/worker 内部错误消息统一使用 ErrorCode 机制
- 标记或删除 `nop-job-core` 中不再使用的遗留类

## Non-Goals

- 不改变 schedule/fire/task 三层领域模型
- 不改变 coordinator/worker/scanner 的整体架构和扫描模型
- 不引入新的外部依赖（如 Micrometer；使用 Nop 平台已有的 metric 机制）

## Scope

### In Scope

- `nop-job-coordinator` — TimeoutChecker、Planner
- `nop-job-dao` — store 层新增批量查询方法
- `nop-job-worker` — 内部错误消息规范化
- `nop-job-core` — 遗留代码标记废弃或删除

### Out Of Scope

- BizModel（service 层）的错误消息已在规范内，不涉及
- ORM 模型变更
- 前端页面和元数据

## Execution Plan

### Phase 1 - TimeoutChecker N+1 修复

Status: completed
Targets: `nop-job-coordinator/.../JobTimeoutCheckerImpl.java`, `nop-job-dao/.../store/`

- Item Types: `Fix`

- [x] 在 `IJobFireStore` 中新增 `batchLoadFires(Set<String> fireIds)` 方法，用 IN 查询批量加载 fire
- [x] 在 `IJobScheduleStore` 中新增 `batchLoadSchedules(Set<String> scheduleIds)` 方法，用 IN 查询批量加载 schedule
- [x] 在对应 Impl 类中实现这两个方法
- [x] 修改 `JobTimeoutCheckerImpl.scanOnce()`：先收集所有 task 的 `jobFireId`，批量加载 fire 到 session 缓存；再收集所有 `jobScheduleId`，批量加载 schedule 到 session 缓存；然后逐个 `tryMarkTimeout`（此时 `loadFire` / `loadSchedule` 走 L1 缓存）
- [x] 在 `TestJobCoordinatorScanner` 中补充 timeout checker 批量预加载场景的测试

Exit Criteria:

- [x] TimeoutChecker 处理 N 个 running task 时，fire 和 schedule 的 SQL 查询数从 2N 降低到最多 2（去重后的 IN 查询）
- [x] `scanOnce()` 不再在循环内调用单条 `loadFire` / `loadSchedule`
- [x] 现有 timeout 测试全部通过，新增批量场景测试通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Planner 锁冲突可观测性

Status: completed
Targets: `nop-job-coordinator/.../JobPlannerScannerImpl.java`

- Item Types: `Fix`

- [x] 在 `JobPlannerScannerImpl.scanOnce()` 中，对比 `fetchDueSchedules` 返回的数量与 `tryLockSchedulesForPlan` 返回的数量，差值即为锁冲突数
- [x] 使用 Nop 平台的 metric 机制（Micrometer MeterRegistry）记录 `nop.job.planner.lock-conflict` 和 `nop.job.planner.due-count`
- [x] 增加 DEBUG 级别日志，在锁冲突数 > 0 时输出冲突数量和 scheduleId 列表

Exit Criteria:

- [x] Planner 每次 scan 有 metric 记录 due count 和 locked count
- [x] 锁冲突时有 DEBUG 日志
- [x] 现有 planner 测试通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 内部错误消息规范化

Status: completed
Targets: `nop-job-coordinator/.../JobTimeoutCheckerImpl.java`, `nop-job-worker/.../JobWorkerScannerImpl.java`, `nop-job-dao/.../store/JobFireStoreImpl.java`, `nop-job-dao/.../store/JobScheduleStoreImpl.java`, `nop-job-worker/.../DefaultJobExecutionContextBuilder.java`

- Item Types: `Fix`

- [x] 在 `nop-job-core` 的 `JobCoreErrors` 中定义以下 ErrorCode（保留原始字符串值以向后兼容）：
  - `JOB_TIMEOUT` — "Job task timed out"
  - `JOB_INVOKER_NOT_FOUND` — "Job invoker not found for schedule"
  - `JOB_CANCELED` — "Job fire/task canceled"
  - `JOB_OVERLAID` — "Job fire/task canceled by overlay"
  - `JOB_EXECUTION_FAILED` — "Job execution failed"
- [x] `JobTimeoutCheckerImpl` 中的 `setErrorCode("JOB_TIMEOUT")` 改为使用 ErrorCode
- [x] `JobWorkerScannerImpl` 中的 `completeTaskWithFailure("JOB_INVOKER_NOT_FOUND", ...)` 改为使用 ErrorCode
- [x] `JobFireStoreImpl.cancelFire()` 中的硬编码错误消息改为使用 ErrorCode
- [x] `JobScheduleStoreImpl` 中的 `JOB_OVERLAID` 硬编码改为使用 ErrorCode
- [x] `DefaultJobExecutionContextBuilder` 中的 `JOB_EXECUTION_FAILED` 改为使用 ErrorCode

Exit Criteria:

- [x] coordinator/worker/store 中不再有硬编码的英文错误消息字符串
- [x] 所有错误消息通过 ErrorCode 定义，支持 i18n
- [x] 现有测试通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 遗留代码清理

Status: completed
Targets: `nop-job-core/.../LocalJobScheduler.java`, `nop-job-api/.../IJobScheduler.java`

- Item Types: `Fix`

- [x] 确认 `LocalJobScheduler`、`SimpleJobState`、`ScheduledJob` 等旧内存调度器类不再被 coordinator/worker/service 引用
- [x] `SimpleJobState` 和 `ScheduledJob` 已在之前的重写中被移除（无独立文件）
- [x] 发现 `TestLocalJobScheduler.java` 测试仍存在，因此标记 `LocalJobScheduler` 和 `IJobScheduler` 为 `@Deprecated`，在 javadoc 中注明迁移说明
- [x] 检查 `nop-job-api` 中的 `IJobScheduler` 接口 — 仅被 `LocalJobScheduler` 实现，已标记 `@Deprecated`

Exit Criteria:

- [x] `nop-job-core` 中不再有不必要的旧调度器实现代码（或已标记 `@Deprecated` 并有清晰的迁移说明）
- [x] 所有保留的旧代码已标记 `@Deprecated` 并有清晰的迁移说明
- [x] `mvn compile -pl nop-job` 编译通过
- [x] 所有 nop-job 模块测试通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] TimeoutChecker 无 N+1 查询（Phase 1 Exit Criteria 全部 `[x]`）
- [x] Planner 锁冲突有 metric 可观测（Phase 2 Exit Criteria 全部 `[x]`）
- [x] coordinator/worker/store 无硬编码错误消息（Phase 3 Exit Criteria 全部 `[x]`）
- [x] 遗留代码已清理或标记废弃（Phase 4 Exit Criteria 全部 `[x]`）
- [x] `mvn test -pl nop-job` 全部通过
- [x] `ai-dev/logs/` 收口记录已更新

Closure Audit Evidence (retroactive):

- Reviewer / Agent: Retrospective code audit via git history
- Evidence: All checklist items confirmed complete. Plan status verified consistent with codebase state.

## Deferred But Adjudicated

_无_

## Non-Blocking Follow-ups

- 如果 TimeoutChecker 性能仍有瓶颈，可考虑在 NopJobTask 上冗余 `timeoutSeconds` 字段，避免加载 schedule
- Worker 的 `executeTask` 中对 fire/schedule 的单条加载（第 135-136 行）在当前单任务模式下不是瓶颈，但如果将来支持多 task 模式，可同样改为批量预加载
