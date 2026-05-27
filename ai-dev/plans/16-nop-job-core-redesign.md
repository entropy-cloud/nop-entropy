# 16 nop-job-core 内存调度器重构

> Plan Status: completed
> Created: 2026-05-17 (从 `docs/plans/job-core-redesign.md` 迁移)
> Completed: 2026-05-17
> Related: 04-nop-job-rewrite-implementation-plan.md, 14-nop-job-quality-fixes.md, 15-nop-job-invoker-implementation-plan.md

## Closure Audit (2026-05-17)

迁移时验证仓库当前状态，确认本计划的所有目标**已在之前的开发中全部落地**：

| Phase | 目标 | 验证结果 |
|-------|------|----------|
| Phase 1: ITrigger 签名改为 ITriggerEvalContext | `ITriggerContext` 接口已不存在，所有 Trigger 实现已使用 `ITriggerEvalContext` | ✅ |
| Phase 2: 删除旧的内存调度器 | `scheduler/` 目录不存在；`ITriggerExecutor`/`ITriggerExecution`/`ITriggerAction`/`ITriggerHook`/`ITriggerContext`/`TriggerContextImpl` 全部已删除 | ✅ |
| Phase 3: 实现 LocalJobScheduler | `LocalJobScheduler.java`（442行）+ `TestLocalJobScheduler.java` 均已存在 | ✅ |
| Phase 4: IJobScheduler 接口精简 | `getTriggerStatus` 已不存在，`JobState` 枚举已存在于 `nop-job-api` | ✅ |
| Phase 5: 验证收尾 | `nop-job-core` 只保留 Trigger/Calendar/Constants/LocalJobScheduler，结构干净 | ✅ |

## 原始计划内容（归档）

### 背景

`nop-job-core` 中的内存调度器（`DefaultJobScheduler` + `TriggerExecutorImpl`）是过度抽象的死代码，
已全面转向 Coordinator-Worker 分布式架构。计划删除 ~1500 行无用代码，用 ~200 行极简 `LocalJobScheduler` 替代。

### 已删除的代码

- `scheduler/` 包：`DefaultJobScheduler`, `JobExecution`, `ResolvedJobSpec`
- 接口：`ITriggerExecutor`, `ITriggerExecution`, `ITriggerAction`, `ITriggerHook`, `ITriggerContext`
- 实现：`TriggerContextImpl`（301行状态机）
- 测试：`TestJobScheduler`, `TestTriggerExecutor`

### 已新增的代码

- `LocalJobScheduler`（442行）— 极简内存调度器，支持 add/remove/suspend/resume/cancel/fireNow
- `TestLocalJobScheduler` — 测试覆盖
- `JobState` 枚举（`nop-job-api`）— 替代旧的 `JOB_INSTANCE_STATUS_*` 整型常量

### 保留的代码

- `ITrigger` + 所有 Trigger 实现（Cron/Periodic/LimitCount/LimitTime/CheckActive/PauseCalendar/HandleMisfire/Once）
- `ITriggerEvalContext` + `TriggerBuilder` + `JobTriggerCalculator`
- `ICalendar` + 所有 Calendar 实现
- `_NopJobCoreConstants` + `NopJobCoreConstants` + `JobCoreErrors`
- `utils/ICronExpression` + `utils/CronExpression`

Closure Audit Evidence (retroactive):

- Reviewer / Agent: Retrospective code audit via git history
- Evidence: All checklist items confirmed complete. Plan status verified consistent with codebase state.
