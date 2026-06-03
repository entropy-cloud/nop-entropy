# 110 nop-job R8 + Deep Audit Critical Findings Remediation

> Plan Status: in progress
> Last Reviewed: 2026-06-04
> Source: `ai-dev/audits/2026-06-04-adversarial-review-nop-job-r8/summary.md` (16 new findings: 1×P0, 5×P1, 8×P2, 2×P3), `ai-dev/audits/2026-06-04-deep-audit-nop-job/summary.md` (34 findings: 2×P1, 13×P2, 19×P3)
> Related: `ai-dev/plans/109-nop-job-deep-audit-security-and-quality-remediation.md` (completed)

## Purpose

修复 R8 对抗性审查（AR-54~AR-69）和 2026-06-04 深度审计（Dim14-01 等）中的 P0/P1 发现和最高优先级 P2 发现，将 nop-job 的 Calendar 构建正确性、并发计数器安全性、RECOVERY 策略完整性、API 保护补齐到生产可用水平。

## Current Baseline

- Plan 109 已完成（覆盖 Dim13 安全权限 4 项 P2）
- Plan 108 已完成（覆盖 R6/R7 剩余 P2 + 高价值 P3）
- Plan 107 已完成（覆盖 R7 对抗性审查 + 深度审计 7 维度 12 项 P2）
- R8 对抗性审查新发现 16 项（AR-54~AR-69），其中 P0×1, P1×5, P2×8, P3×2
- 深度审计新发现 34 项，其中 P1×2, P2×13, P3×19
- 仍有 10 项 prior P2 未修复（AR-40, 42, 43, 44, 46, 48, 49, 50, 51, 52），归入 successor plan
- `./mvnw clean test -pl nop-job -am` 当前基线为 BUILD SUCCESS

### 仍然存活的未修复项（本 plan scope 内 16 项）

| ID | Sev | 描述 | 来源 |
|----|-----|------|------|
| AR-54 | **P0** | DailyCalendarSpec `LocalTime.of(24,0,0)` 崩溃 | R8 |
| AR-55 | **P1** | WeeklyCalendarSpec ISO→Calendar 映射不一致 — 排除错误星期几 | R8 |
| AR-56 | **P1** | DailyCalendar 排除午夜 — 与 PauseCalendarTrigger 组合导致无限循环 | R8 |
| AR-57 | **P1** | shouldRecovery 缺少 activeFireCount 守卫 | R8 |
| AR-58 | **P1** | resolveCompletionDecision 畸形 JSON 中止整个扫描批次 | R8 |
| AR-59 / Dim14-02 | **P1** | persistSchedule 乐观锁耗尽后 force-update 覆盖并发引擎计数器 | R8 + Deep |
| Dim14-01 | **P1** | completeFireAndUpdateSchedule 并发 delta 在重试循环外计算导致计数器漂移 | Deep |
| AR-65 | P2 | NopJobFireBizModel 未限制 CRUD | R8 |
| AR-67 / Dim11-02 | P2 | NopJobFire.xmeta 缺少引擎字段只读覆盖 | R8 + Deep |
| Dim11-01 | P2 | NopJobTask.xmeta 缺少引擎字段只读覆盖 | Deep |
| AR-61 | P2 | resetFailedTasks 跳过 SUSPICIOUS 任务 | R8 |
| AR-60 | P2 | enableSchedule 保留过时 nextFireTime | R8 |
| AR-62 | P2 | PeriodicTrigger fixed-rate 模式漂移 | R8 |
| AR-63 | P2 | DailyCalendar.getNextIncludedTime 无迭代次数限制 | R8 |
| AR-64 | P2 | CronCalendar MAX_ITERATION 返回被排除时间 | R8 |
| AR-66 | P2 | JobFireResult.ERROR 设置 completed=true — 语义混淆 | R8 |

## Goals

- 修复所有 P0 和 P1 发现（6 项），消除崩溃、静默错误行为、并发数据损坏
- 修复 API 保护相关 P2 发现（4 项），关闭 GraphQL CRUD 绕过引擎生命周期的攻击面
- 修复 Calendar/Trigger 鲁棒性 P2 发现（5 项），消除无限循环、漂移和语义混淆
- 全部修改通过 `./mvnw test -pl nop-job -am` 验证

## Non-Goals

- 修复 prior unfixed P2 backlog（AR-40, 42, 43, 44, 46, 48, 49, 50, 51, 52）— 归入 successor plan
- 修复 P3 发现（AR-68, AR-69, Dim14-05~07, Dim11-03 等 29 项）— 归入 Non-Blocking Follow-ups
- 修复深度审计中与本 plan scope 无重叠的 P2 发现（Dim04-01, Dim07-01, Dim09-01~03, Dim14-03/04, Dim16-01/02）— 部分与 AR-59/AR-61 重叠，其余归入 successor plan
- 新增功能或架构变更

## Scope

### In Scope

- nop-job-core: CalendarBuilder, DailyCalendar, CronCalendar, WeeklyCalendar, PeriodicTrigger, LocalJobScheduler
- nop-job-dao: JobFireStoreImpl, JobScheduleStoreImpl
- nop-job-service: NopJobScheduleBizModel, NopJobFireBizModel
- nop-job-coordinator: JobPlannerScannerImpl, JobCompletionProcessorImpl
- nop-job-meta: NopJobFire.xmeta, NopJobTask.xmeta
- nop-job-api: JobFireResult

### Out Of Scope

- nop-job-app (组装模块，无手写代码)
- nop-job-codegen (代码生成工具)
- nop-job-web (前端页面)
- prior P2 backlog (10 项，successor plan)
- P3 findings (29 项，Non-Blocking Follow-ups)

## Execution Plan

### Phase 1 - Calendar P0+P1 Critical Fixes

Status: in progress
Targets: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CalendarBuilder.java`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/DailyCalendar.java`

- Item Types: `Fix`

- [x] **AR-54 (P0)**: 在 `CalendarBuilder.java:80` 将 `LocalTime.of(24, 0, 0)` 改为 `LocalTime.MAX`（`23:59:59.999999999`），修复 DailyCalendarSpec 无 end time 时崩溃
- [x] **AR-55 (P1)**: 在 `CalendarBuilder.java:63-67` 添加 ISO 8601 到 `java.util.Calendar` 的星期映射转换：`int calendarDay = (isoDay % 7) + 1`，修复 WeeklyCalendarSpec 静默排除错误星期几
- [x] **AR-56 (P1)**: 在 `DailyCalendar.java:209` 将 `timeInMillis > startOfDayInMillis` 改为 `timeInMillis >= startOfDayInMillis`（使用 `>=`），修复非反转模式下午夜被排除导致 PauseCalendarTrigger 无限循环
- [x] 为三个修复各添加单元测试：DailyCalendarSpec 无 end time 不崩溃、WeeklyCalendarSpec 排除正确的星期几、DailyCalendar + PauseCalendarTrigger 午夜 cron 不无限循环

Exit Criteria:

- [x] `CalendarBuilder.buildCalendar` 对 `DailyCalendarSpec{start:"09:00", end:null}` 不抛异常，end 默认为 `LocalTime.MAX`
- [x] `WeeklyCalendarSpec{excludes:[6,7]}` 排除的是周六和周日（Calendar.SATURDAY=7, Calendar.SUNDAY=1），不是周五和周六
- [x] `DailyCalendar.isTimeIncluded(午夜毫秒值)` 在非反转模式下返回 true
- [x] PauseCalendarTrigger 对 cron `"0 0 0 * * *"` + DailyCalendar 排除 08:00-17:00 的场景能在有限步内返回有效触发时间
- [x] 新增 3 组单元测试覆盖上述场景
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required (内部行为修复)
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Engine P1 Critical Fixes

Status: in progress
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`, `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`

- Item Types: `Fix`

- [x] **AR-57 (P1)**: 在 `JobPlannerScannerImpl.shouldRecovery` 中添加 `defaultInt(schedule.getActiveFireCount()) > 0` 守卫，与 `shouldDiscard`/`shouldOverlay` 保持一致；无活跃 fire 时走正常 fire 创建路径
- [x] **AR-58 (P1)**: 在 `JobCompletionProcessorImpl.resolveCompletionDecision` 中对 `JsonTool.parseMap(resultPayload)` 包裹 try-catch，解析失败时视为空 Map 并记录 WARN 日志，防止单个畸形 task 结果中止整个扫描批次
- [x] **AR-59 / Dim14-02 (P1)**: 在 `NopJobScheduleBizModel.persistSchedule` 中移除乐观锁耗尽后的 `updateEntityDirectly` fallback，改为抛出 `NopException`（与 store 层一致）；或保留 fallback 但在 `updateEntityDirectly` 前重新读取最新状态并只更新非引擎字段
- [x] **Dim14-01 (P1)**: 在 `JobFireStoreImpl.completeFireAndUpdateSchedule` 中将 delta 计算移入重试循环内，每次迭代从 fresh entity 重新计算，参照同文件 `cancelFire` 方法的正确模式
- [x] 为每个修复添加对应的单元测试

Exit Criteria:

- [x] `shouldRecovery` 在 `activeFireCount == 0` 时返回 false，RECOVERY 策略 schedule 在无活跃 fire 时走正常 fire 创建路径
- [x] `resolveCompletionDecision` 对畸形 JSON resultPayload 不抛异常，返回默认 completion decision（视为空 payload）
- [x] `persistSchedule` 乐观锁耗尽后抛出异常（或安全地只更新非引擎字段），不会无条件覆盖引擎计数器
- [x] `completeFireAndUpdateSchedule` 的 delta 在每次重试迭代内重新计算，两个并发 fire 完成后 `activeFireCount` 正确归零
- [x] 新增单元测试覆盖上述 4 个场景
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required (内部行为修复)
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - API Protection & Recovery Completeness P2 Fixes

Status: in progress
Targets: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`, `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/NopJobFire.xmeta`, `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/NopJobTask.xmeta`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

- Item Types: `Fix`

- [x] **AR-65 (P2)**: 在 `NopJobFireBizModel` 中 override `delete__` 抛出 `NopException(ERR_JOB_FIRE_DELETE_NOT_ALLOWED)`（参照 NopJobTaskBizModel 限制 delete 的模式）；需先在 `NopJobErrors` 中定义 `ERR_JOB_FIRE_DELETE_NOT_ALLOWED` 错误码；考虑同时限制 `save__` 和 `update__` 或至少限制 `delete__`
- [x] **AR-67 / Dim11-02 (P2)**: 在 `NopJobFire.xmeta` 中为以下字段添加只读覆盖：`triggerSource`→`insertable="false" updatable="false"`，`scheduledFireTime`→`updatable="false"`，`triggeredBy`→`insertable="false" updatable="false"`，`retryPolicyId`→`updatable="false"`，`retryRecordId`→`updatable="false"`，`partitionIndex`→`updatable="false"`，`jobScheduleId`→`updatable="false"`，`jobParamsSnapshot`→`updatable="false"`，`executorKind`→`updatable="false"`
- [x] **Dim11-01 (P2)**: 在 `NopJobTask.xmeta` 中为以下 **4 个尚未限制的字段** 添加限制（注：`jobFireId`、`shardingIndex`、`shardingTotal` 已在 prior plan 中添加了 `updatable="false"`）：`taskPayload`→`updatable="false"`，`taskNo`→`updatable="false"`，`partitionIndex`→`updatable="false"`，`targetHost`→`insertable="false" updatable="false"`
- [x] **AR-61 (P2)**: 在 `JobScheduleStoreImpl.isTaskFailed` 中添加 `taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS`，使 RECOVERY 策略能恢复 worker 崩溃导致的 SUSPICIOUS 任务

Exit Criteria:

- [x] `NopJobFireBizModel.delete__` 抛出异常，GraphQL `delete__NopJobFire` 操作被拒绝
- [x] `NopJobFire.xmeta` 新增 9 个字段的只读覆盖，GraphQL `save__NopJobFire`/`update__NopJobFire` 无法修改引擎管理字段
- [x] `NopJobTask.xmeta` 新增 4 个字段的限制（`taskPayload`、`taskNo`、`partitionIndex`、`targetHost`），GraphQL `save__NopJobTask`/`update__NopJobTask` 无法修改引擎管理字段
- [x] `isTaskFailed` 包含 SUSPICIOUS 状态，RECOVERY 路径能重置 SUSPICIOUS 任务为 WAITING
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required (安全加固)
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Trigger & Calendar Robustness P2 Fixes

Status: planned
Targets: `nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/PeriodicTrigger.java`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/DailyCalendar.java`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CronCalendar.java`, `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`, `nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/JobFireResult.java`

- Item Types: `Fix`

- [ ] **AR-60 (P2)**: 在 `NopJobScheduleBizModel.enableSchedule` 中总是重新计算 `nextFireTime`（移除 `if (schedule.getNextFireTime() == null)` 条件判断），或至少检查 `nextFireTime` 是否在过去并重新计算
- [ ] **AR-62 (P2)**: 在 `LocalJobScheduler.executeJob` 中将 `lastScheduledTime` 设置为计划时间（trigger 返回的 time 值）而非 `currentTime()`，修复 PeriodicTrigger fixed-rate 模式永久漂移
- [ ] **AR-63 (P2)**: 在 `DailyCalendar.getNextIncludedTime` 的 while 循环中添加 `MAX_ITERATION` 保护（与 CronCalendar 一致），超出时抛出异常而非无限循环
- [ ] **AR-64 (P2)**: 在 `CronCalendar.getNextIncludedTime` 中超过 `MAX_ITERATION` 时抛出 `NopException` 而非 `break` 返回被排除时间
- [ ] **AR-66 (P2)**: 在 `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus`（约 line 200）中，当 `completionDecision.completed == true` 时，检查 `finalFireStatus`：如果 fire 最终状态为 SUCCESS，设置 `SCHEDULE_STATUS_COMPLETED`；如果 fire 最终状态为非 SUCCESS（ERROR/FAILED/TIMEOUT），设置 `SCHEDULE_STATUS_ERROR` 而非 `SCHEDULE_STATUS_COMPLETED`。同时需在 `resolveCompletionDecision` 中对 `JsonTool.parseMap` 添加 try-catch（与 AR-58 Phase 2 修复一起完成）

Exit Criteria:

- [ ] `enableSchedule` 后 `nextFireTime` 总是重新计算，与 `resumeSchedule` 行为一致
- [ ] `PeriodicTrigger` fixed-rate 模式下，首次触发延迟后后续触发时间不永久漂移
- [ ] `DailyCalendar.getNextIncludedTime` 有 `MAX_ITERATION` 保护，超出限制时抛出异常
- [ ] `CronCalendar.getNextIncludedTime` 超过 `MAX_ITERATION` 时抛出 `NopException` 而非返回被排除时间
- [ ] `JobFireResult.ERROR` 结果在 `allowResultCompletion=true` 时，`tryCompleteFireAndGetStatus` 根据 `finalFireStatus` 将 schedule 标记为 ERROR 而非 COMPLETED
- [ ] 新增单元测试覆盖上述 5 个场景
- [ ] `./mvnw test -pl nop-job -am` 全过
- [ ] No owner-doc update required (内部行为修复)
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] AR-54 (P0) DailyCalendarSpec 崩溃已修复
- [ ] AR-55~AR-59, Dim14-01 共 6 项 P1 已修复
- [ ] AR-60~AR-67, Dim11-01 共 10 项 P2 已修复（AR-67 与 Dim11-02 合并，AR-59 与 Dim14-02 合并；Dim11-01 实际仅剩 4 个未限制字段）
- [ ] 无空壳实现或静默跳过
- [ ] `./mvnw compile -pl nop-job -am` 成功
- [ ] `./mvnw test -pl nop-job -am` 全过
- [ ] checkstyle / 代码规范检查通过
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### Prior Unfixed P2 Backlog (AR-40~AR-52, 10 items)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这些 P2 来自 R6/R7 对抗性审查，已经在 Plan 106-108 评估后确认为低优先级。它们不影响 Calendar 正确性、并发安全性、API 保护等本 plan 的修复主题。修复它们需要额外的代码分析和测试，应归入独立的 successor plan。
- Successor Required: yes
- Successor Path: `ai-dev/plans/` (待创建 111-nop-job-prior-p2-backlog-remediation.md)

### P3 Findings (AR-68, AR-69, Dim11-03, Dim14-05~07, etc.)

- Classification: `watch-only residual`
- Why Not Blocking Closure: P3 为可观测性改进和 API 契约清理，不影响正确性和安全性。随 P0/P1/P2 修复完成后逐批处理。
- Successor Required: yes
- Successor Path: 随 prior P2 backlog 一起归入 successor plan

### Deep Audit P2 Not In This Scope (Dim04-01, Dim07-01, Dim09-01~03, Dim14-03/04, Dim16-01/02)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这些 P2 来自 2026-06-04 深度审计的 8 维度审查，与本 plan 的 R8 + 核心引擎修复主题无直接关联。部分项（Dim14-03 fire 版本冲突静默返回、Dim14-04 事务原子性）需要更深入的设计决策，应在 successor plan 中处理。
- Successor Required: yes
- Successor Path: 随 prior P2 backlog 一起归入 successor plan

## Non-Blocking Follow-ups

- AR-68 (P3): 指标系统对失败/超时路径记录 durationMs 分布（Timer 替代 Counter）
- AR-69 (P3): CronCalendar 移除 `throws ParseException` 声明
- Dim11-03 (P3): NopJobSchedule.xmeta scheduleStatus 添加 `insertable="false"`
- Dim14-05 (P3): Worker metrics 仅在 task 更新成功时上报
- Dim14-06 (P3): executeTask 检查 updateTask 返回值
- Dim14-07 (P3): tryMarkSuspiciousIfWorkerGone 重新检查 worker 存活状态

## Closure

Status Note:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- Prior unfixed P2 backlog (AR-40~AR-52) 和 remaining deep audit P2s 归入 successor plan
- P3 findings 归入 Non-Blocking Follow-ups，不阻塞本 plan closure
