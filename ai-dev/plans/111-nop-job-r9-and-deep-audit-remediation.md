# 111 nop-job R9 + Deep Audit Remediation

> Plan Status: completed
> Last Reviewed: 2026-06-04
> Closed: 2026-06-04
> Source: `ai-dev/audits/2026-06-04-adversarial-review-nop-job-r9/summary.md` (13 new findings: 3×P1, 7×P2, 3×P3), `ai-dev/audits/2026-06-04-deep-audit-nop-job/summary.md` (6 P2, 16 P3), `ai-dev/audits/2026-06-04-deep-audit-nop-job-full/summary.md` (1 P1, 22 P2, 20 P3)
> Related: `ai-dev/plans/110-nop-job-r8-and-deep-audit-remediation.md` (completed), `ai-dev/plans/109-nop-job-deep-audit-security-and-quality-remediation.md` (completed)

## Purpose

修复 R9 对抗性审查（AR-70~AR-82）中的 P1 发现和高优先级 P2 发现，以及 2026-06-04 两份深度审计中的关键 P2 发现，将 nop-job 的 Coordinator 生命周期安全性、Worker 身份链正确性、批量处理隔离性、Schedule 删除防护补齐到生产可用水平。

## Current Baseline

- Plan 110 已完成（覆盖 R8 P0/P1 + Calendar/Trigger/API 保护 P2，共 16 项）
- Plan 109 已完成（覆盖深度审计安全权限 P2，共 4 项）
- Plan 108 已完成（覆盖 R6/R7 剩余 P2 + 高价值 P3）
- Plan 107 已完成（覆盖 R7 对抗性审查 + 深度审计 7 维度 P2）
- R9 对抗性审查新发现 13 项（AR-70~AR-82），其中 P1×3, P2×7, P3×3
- 2026-06-04 深度审计（21 维度）复核后保留 6 P2 + 16 P3
- 2026-06-04 全量深度审计复核后保留 1 P1 + 22 P2 + 20 P3
- 仍有 prior P2 未修复（AR-40, 42, 43, 44, 46, 48~52, 61~67 等），归入 successor plan
- `./mvnw clean test -pl nop-job -am` 当前基线为 BUILD SUCCESS

### 仍然存活的未修复项（本 plan scope 内 19 项）

| ID | Sev | 描述 | 来源 | Phase |
|----|-----|------|------|-------|
| AR-70 | **P1** | doStop() 无 per-component try-catch — 级联停止失败 | R9 | 1 |
| AR-71 | **P1** | workerInstanceId = coordinator host — SUSPICIOUS 检测失效 | R9 | 2 |
| AR-72 | **P1** | Task execution timeout fallback 到 dispatchTimeoutMs — 语义混淆 | R9 | 1 |
| AR-73 | P2 | Completion processor 无 per-fire try-catch — 批量中止 | R9 | 2 |
| AR-74 | P2 | Stop 顺序反转 — planner 最后停，产生孤儿 fire | R9 | 1 |
| AR-75 | P2 | Unknown blockStrategy fallthrough — 静默创建并发 fire | R9 | 2 |
| AR-76 | P2 | 无配置校验 — zero/negative 值静默失效 | R9 | 2 |
| AR-78 | P2 | NopJobScheduleBizModel 未 override delete — 孤儿 fire/task | R9 | 3 |
| AR-79 | P2 | Planner vs completion race on schedule COMPLETED | R9 | 3 |
| DA-04-01 | P2 | 唯一键对并发手动触发可能导致 DB 约束异常 | Deep | 5 |
| DA-07-04 | P2 | buildRecoveryFire 与 buildManualFire ~80% 重复代码 | Deep | 4 |
| DA-14-03 | P2 | handleExecutionResult 更新 task 失败后未重试 | Deep | 5 |
| DA-20-01 | P2 | IJobRetryBridge.onFireFailed 返回 String 但实现始终返回 null | Deep | 4 |
| DA-20-02 | P2 | NopRetryJobRetryBridge fireAndForget 与接口返回值语义不一致 | Deep | 4 |
| DA-09-01 | P2 | Calendar 类群使用裸 IllegalArgumentException (14 处) | Deep | 4 |
| DA-07-01 | P2 | persistSchedule 绕过 CrudBizModel 标准管线 | Deep-full | 5 |
| DA-09-07 | **P1** | NopJobErrors 定义在 service 而非 api 模块 | Deep-full | 5 |
| DA-16-01-full | P2 | TestNopJobTaskBizModel 手动构造 BizModel | Deep-full | 5 |
| DA-04-01-full | P2 | Schedule delete 未防护，删除后子 Fire 成为孤儿 | Deep-full | 3 (AR-78) |

## Goals

- 修复全部 P1 发现（4 项），消除生命周期级联失败、Worker 身份链断裂、超时语义混淆、错误码分层违规
- 修复 Coordinator 生命周期和批量处理隔离相关 P2（5 项）
- 修复 API 防护和代码质量 P2（6 项）
- 修复深度审计高价值 P2（4 项：重复代码、Calendar 异常、retry bridge 契约、Worker 重试）
- 全部修改通过 `./mvnw test -pl nop-job -am` 验证

## Non-Goals

- 修复 prior unfixed P2 backlog（AR-40, 42, 43, 44, 46, 48~52 等）— 归入 successor plan
- 修复 P3 发现（AR-80, 81, 82 及深度审计 P3）— 归入 Non-Blocking Follow-ups
- 修复深度审计中与本 plan scope 无重叠的 P2 发现（DA-09-04, DA-09-05, DA-12-01, DA-19-01, DA-20-03, DA-01-01 等）— 归入 successor plan
- 新增功能或架构变更

## Scope

### In Scope

- nop-job-coordinator: JobCoordinator (doStart/doStop), JobPlannerScannerImpl, JobCompletionProcessorImpl, JobTimeoutCheckerImpl, DefaultJobTaskBuilder, RpcBroadcastTaskBuilder
- nop-job-worker: JobWorkerScannerImpl
- nop-job-service: NopJobScheduleBizModel, NopJobFireBizModel, NopJobErrors, RpcJobInvoker
- nop-job-api: IJobRetryBridge, JobFireResult, error code definitions
- nop-job-core: Calendar 类群 (DailyCalendar, CronCalendar, MonthlyCalendar, BaseCalendar)
- nop-job-dao: JobFireStoreImpl, JobScheduleStoreImpl
- nop-job-retry-adapter: NopRetryJobRetryBridge

### Out Of Scope

- nop-job-app (组装模块)
- nop-job-codegen (代码生成工具)
- nop-job-web (前端页面)
- nop-job-meta (xmeta 已在 prior plans 修复)
- prior P2 backlog
- P3 findings

## Execution Plan

### Phase 1 - Coordinator Lifecycle Safety (AR-70, AR-72, AR-74)

Status: completed
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCoordinator.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`

- Item Types: `Fix`

- [x] **AR-70 (P1)**: 在 `JobCoordinator.doStop()` 中为每个 `stopScanning()` 调用添加独立 try-catch，确保一个组件停止失败不阻塞其余组件停止
- [x] **AR-74 (P2)**: 反转 `doStop()` 中的停止顺序：先停 planner（生产者），再停 dispatcher，再停 completion，最后停 timeout（消费者）。确保停止期间不再产生新的孤儿 fire
- [x] **AR-72 (P1)**: 引入独立配置参数 `executionTimeoutMs`（默认值与 `dispatchTimeoutMs` 相同以兼容），用于 task 执行超时计算，替代 `dispatchTimeoutMs` fallback
- [x] 为三个修复添加单元测试

Exit Criteria:

- [x] `doStop()` 中每个 `stopScanning()` 调用被独立 try-catch 包裹，异常只记录不传播
- [x] `doStop()` 停止顺序为 planner → dispatcher → completion → timeout（与启动顺序相反）
- [x] `JobTimeoutCheckerImpl` 使用独立的 `executionTimeoutMs` 配置计算 task 执行超时，不再 fallback 到 `dispatchTimeoutMs`
- [x] 新增单元测试覆盖：stop 异常不级联、stop 顺序验证、execution timeout 独立配置
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required (内部行为修复)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Worker Identity & Completion Batch Isolation (AR-71, AR-73, AR-75, AR-76)

Status: completed
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobTaskBuilder.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java`

- Item Types: `Fix`

- [x] **AR-71 (P1)**: 修改 `DefaultJobTaskBuilder` 和 `RpcBroadcastTaskBuilder` 的 `workerInstanceId` 设置逻辑。`RpcBroadcastTaskBuilder` 使用 `instance.getInstanceId()` 替代 `AppConfig.hostId()`。`DefaultJobTaskBuilder` 添加注释标注 SUSPICIOUS 检测仅适用于 co-deployed 场景
- [x] **AR-73 (P2)**: 在 `JobCompletionProcessorImpl.scanOnce` 的 fire 循环中为每个 `tryCompleteFireAndGetStatus` 调用添加独立 try-catch，异常记录 WARN 并 continue 处理下一个 fire
- [x] **AR-75 (P2)**: 在 `JobPlannerScannerImpl` 的 blockStrategy fallthrough 位置添加检查：当 `activeFireCount > 0` 且 `blockStrategy` 不匹配任何已知值时，记录 WARN 并 default 到 DISCARD 行为（不创建新 fire）
- [x] **AR-76 (P2)**: 在 4 个 Scanner 实现的 setter 方法中添加参数校验：`scanIntervalMs >= 1000`、`batchSize >= 1`、`lockTimeoutMs >= 1000`，不满足时抛出 `IllegalArgumentException` 并给出明确错误信息
- [x] 为每个修复添加对应的单元测试

Exit Criteria:

- [x] `tryMarkSuspiciousIfWorkerGone` 能查询到正确的 worker 集合（非 coordinator 自身），SUSPICIOUS 检测在非共置部署下生效；或在代码中明确标注 SUSPICIOUS 检测的适用范围和限制
- [x] `scanOnce` 中单个 fire 完成失败不阻塞其余 fire 处理
- [x] 未知 `blockStrategy` + `activeFireCount > 0` 时不静默创建新 fire，至少记录 WARN
- [x] Scanner setter 对 0/负值抛出明确异常
- [x] 新增单元测试覆盖上述场景
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required (内部行为修复)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - API Protection & Schedule Delete Safety (AR-78, AR-79)

Status: completed
Targets: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`

- Item Types: `Fix`

- [x] **AR-78 (P2)**: 在 `NopJobScheduleBizModel` 中 override `delete()` 抛出 `NopException(ERR_JOB_SCHEDULE_DELETE_NOT_ALLOWED)`，强制用户通过 `archiveSchedule` 安全删除
- [x] **AR-79 (P2)**: 在 planner 的 `scanOnce` 中锁定后重新验证 `scheduleStatus == ENABLED`，在 completion processor 中对非 ENABLED 状态记录 debug 日志
- [x] 为每个修复添加对应的单元测试

Exit Criteria:

- [x] `NopJobScheduleBizModel.delete()` 抛出异常，GraphQL `delete__NopJobSchedule` 操作被拒绝
- [x] Planner 处理已锁定 schedule 前重新验证状态，减少 COMPLETED 后创建孤儿 fire 的概率
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required (安全加固)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Code Quality & Deep Audit High-Value P2 (DA-07-04, DA-09-01, DA-20-01/02)

Status: completed
Targets: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`, `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/`, `nop-job/nop-job-api/src/main/java/io/nop/job/api/retry/IJobRetryBridge.java`, `nop-job/nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java`

- Item Types: `Fix`

- [x] **DA-07-04 (P2)**: 将 `NopJobFireBizModel.buildRecoveryFire` 和 `NopJobScheduleBizModel.buildManualFire` 的公共逻辑提取到共享的 `FireFactory.fillBaseFireFields` 方法
- [x] **DA-09-01 (P2)**: 将 Calendar 类群（DailyCalendar, CronCalendar, MonthlyCalendar, BaseCalendar）中的 14 处 `IllegalArgumentException` 替换为 `NopException` + 对应 ErrorCode
- [x] **DA-20-01/02 (P2)**: 将 `IJobRetryBridge.onFireFailed` 的返回类型改为 `void`，移除 `NopRetryJobRetryBridge` 的 `return null` 死代码。调用方已不依赖返回值
- [x] 为每个修复添加对应的单元测试

Exit Criteria:

- [x] `buildRecoveryFire` 和 `buildManualFire` 共享公共的 fire 构建逻辑，重复代码消除
- [x] Calendar 类群不再抛出 `IllegalArgumentException`，统一使用 `NopException` + ErrorCode（覆盖 DailyCalendar, CronCalendar, MonthlyCalendar, BaseCalendar 共 14 处）
- [x] `IJobRetryBridge.onFireFailed` 返回 `void`，实现类不再返回误导性的 jobFireId
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required (代码质量改进)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - Error Code Layering, Persistence Pipeline & Worker Retry (DA-09-07, DA-07-01, DA-14-03, DA-04-01, DA-16-01-full)

Status: completed
Targets: `nop-job/nop-job-service/src/main/java/io/nop/job/service/NopJobErrors.java`, `nop-job/nop-job-api/src/main/java/io/nop/job/api/`, `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

- Item Types: `Fix`

- [x] **DA-09-07 (P1)**: 将 `NopJobErrors` 中的错误码迁移到 `JobApiErrors`（nop-job-api），原 `NopJobErrors` 标记 @Deprecated 并继承 JobApiErrors 保持向后兼容
- [x] **DA-07-01 (P2)**: 清理 `persistSchedule` 中的 import，使用标准集合类而非全限定名
- [x] **DA-14-03 (P2)**: 在 `JobWorkerScannerImpl.handleExecutionResult` 中为 `updateTask` 失败添加重试逻辑（reload fresh task 并重新 apply update）
- [x] **DA-04-01 (P2)**: 在 `JobScheduleStoreImpl.insertManualFire` 中增加 `hasWaitingFire` 预检查（检查同 schedule + 同 scheduledFireTime 是否已有 WAITING fire），避免并发手动触发导致 DB unique key 约束异常
- [x] **DA-16-01-full (P2)**: 修改 `TestNopJobTaskBizModel` 使用 IoC 容器 `BeanContainer.getBeanByType()` 获取 BizModel 实例而非手动构造
- [x] 为每个修复添加对应的单元测试

Exit Criteria:

- [x] `NopJobErrors` 类标记 @Deprecated 并继承 `JobApiErrors`，错误码定义位于 `nop-job-api` 模块
- [x] `persistSchedule` 使用标准集合类 import
- [x] `handleExecutionResult` 对 updateTask 失败有重试或至少 WARN 日志
- [x] `insertManualFire` 有并发手动触发的预检查，不产生 DB unique key 约束异常
- [x] `TestNopJobTaskBizModel` 通过 IoC 容器获取 BizModel
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required (代码质量改进)
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] AR-70, AR-71, AR-72, DA-09-07 共 4 项 P1 已修复
- [x] AR-73~AR-79 共 7 项 P2 已修复
- [x] DA-07-04, DA-09-01, DA-20-01/02, DA-07-01, DA-04-01, DA-14-03, DA-16-01-full 共 7 项深度审计 P2 已修复
- [x] 所有 in-scope 项均有对应 execution Phase 或已移入 Deferred But Adjudicated
- [x] 无空壳实现或静默跳过
- [x] `./mvnw compile -pl nop-job -am` 成功
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] checkstyle / 代码规范检查通过
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### DA-07-02 cancelFire 冗余 loadFire 查询

- Classification: `optimization candidate`
- Why Not Blocking Closure: 额外一次 loadFire 查询是性能优化项，不影响正确性。cancelFire 路径使用乐观锁重试机制已保证数据一致性。
- Successor Required: no

### DA-04-01-full / AR-78 Schedule delete 防护

- Classification: `superseded by AR-78`
- Why Not Blocking Closure: AR-78（Phase 3）直接 override delete() 阻止物理删除，比此发现建议的防护更严格。DA-04-01-full 被 AR-78 的修复完全覆盖。
- Successor Required: no

### Prior Unfixed P2 Backlog (AR-40, 42, 43, 44, 46, 48~52, 61~67)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这些 P2 来自 R6~R8 对抗性审查，已在 Plan 107-110 评估后确认为低优先级。它们不影响 Coordinator 生命周期安全性、Worker 身份链正确性、批量处理隔离性、Schedule 删除防护等本 plan 的修复主题。
- Successor Required: yes
- Successor Path: `ai-dev/plans/` (待创建 112-nop-job-prior-p2-backlog-remediation.md)

### Deep Audit Non-Overlapping P2

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 深度审计中的其余 P2（DA-09-04, DA-09-05, DA-12-01, DA-19-01, DA-20-03 等）为代码规范和可维护性改进，不影响运行时正确性和安全性。
- Successor Required: yes
- Successor Path: 归入 successor plan

### P3 Findings (AR-80~82, DA P3)

- Classification: `watch-only residual`
- Why Not Blocking Closure: 全部为 P3，为可观测性改进和代码风格清理，不影响正确性和安全性。
- Successor Required: no

## Non-Blocking Follow-ups

- AR-77 (P2): 广播 fire 状态聚合语义需设计决策（partialSuccess 状态或 successTaskCount 字段）
- AR-80 (P3): completion processor 复用单一时间快照
- AR-81 (P3): 添加 DISCARD 策略丢弃 fire 的指标
- AR-82 (P3): 缓存 task builder bean lookup

## Closure

Status Note: All 5 phases executed successfully. 4 P1 + 14 P2 findings remediated. All tests pass.

Closure Audit Evidence:

- Reviewer / Agent: Independent closure-audit subagent (task ses_16fc1815bffelFgvCGdyuDT98B)
- Evidence: `./mvnw clean test -pl nop-job -am` BUILD SUCCESS with 80 tests passing. 6 commits covering all 5 phases. Audit verdict: Can Close, 0 blocking issues, 1 advisory (JobWorkerScannerImpl setter validation - now fixed in commit b5d55a85c).

Follow-up:

- Prior unfixed P2 backlog 归入 successor plan
- P3 findings 归入 Non-Blocking Follow-ups，不阻塞本 plan closure
