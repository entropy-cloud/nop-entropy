# 103 nop-job Adversarial Review Remediation (2026-06-03)

> Plan Status: planned
> Last Reviewed: 2026-06-03
> Source: `ai-dev/audits/2026-06-03-adversarial-review-nop-job/` (Round 1: 7, Round 2: 9, Round 3: 2, total 18 new findings)
> Related: `ai-dev/plans/20-nop-job-audit-remediation.md` (completed), `ai-dev/plans/21-nop-job-adversarial-review-remediation.md` (completed)

## Purpose

修复 2026-06-03 对抗性审查发现的所有 P1 和高价值 P2 问题。该审查在 5 月审计修复的基础上，发现 5 项 P1（修复残留语义错误、静默丢弃、并发更新丢失、RPC 取消路由断裂）和 8 项 P2（事务边界不一致、任务孤儿、过时参数等）。

## Current Baseline

- Plan 20（系统审计修复）和 Plan 21（对抗性审查修复）均已完成
- 5 月审计的 15 项主要发现中，11 项已修复，2 项仍存在（F9 retryRecordId 错误值、F14 API 命名冲突），1 项部分修复（F4 maxFailedCount 硬编码）
- 2026-06-03 对抗性审查发现 18 项新问题：5×P1、8×P2、5×P3
- 代码基线验证结果：7/8 项关键发现已在 live repo 中确认（AR-17 的 targetHost 路由部分已通过 attribute 机制而非 jobParams 注入）
- `./mvnw clean test -pl nop-job` 当前基线应为 BUILD SUCCESS

## Goals

- 修复全部 5 项 P1：AR-1（startTime 语义错误）、AR-2（recovery fire 缺字段）、AR-8（rerunFire 静默丢弃）、AR-9（schedule 并发更新丢失）、AR-17（RPC 取消 targetHost 路由）
- 修复 5 项高影响 P2：AR-3（insertManualFire overlay 无 try-catch）、AR-5（CLAIMED 任务孤儿）、AR-11（dispatch timeout 不取消 tasks）、AR-12（recovery 过时参数）、AR-13（SUSPICIOUS 同周期转换 TIMEOUT）
- 修复 3 项中影响 P2：AR-4（retryRecordId 死写）、AR-10（Worker updateTask 无版本检查）、AR-18（insertManualFire overlay 统计缺失）
- 所有修复有对应测试覆盖

## Non-Goals

- 不重新设计调度器架构
- 不修复 P3 项（AR-6 死代码、AR-7 maxFailedCount、AR-14 copyMap 命名、AR-15 错误优先级、AR-16 广播 taskPayload）——进入 Non-Blocking Follow-ups
- 不修复已知遗留项 F9（retryRecordId 返回 jobFireId）和 F14（CONTINUE 命名冲突）——属于 API 设计限制
- 不处理 DAO→Core 反向依赖重构——已 defer 到独立计划
- 不处理 deep-audit 的 154 项发现中未在本次对抗性审查中重新出现的项

## Scope

### In Scope

- AR-1: `JobFireStoreImpl` startTime 语义修正
- AR-2: `JobScheduleStoreImpl` recovery fire 缺失字段补全
- AR-3: `JobScheduleStoreImpl` insertManualFire overlay 路径 try-catch
- AR-4: `JobCompletionProcessorImpl` retryRecordId 死写修正
- AR-5: `JobTimeoutCheckerImpl` / `JobTaskStoreImpl` CLAIMED 任务超时回收
- AR-8: `NopJobFireBizModel` rerunFire 返回值检查
- AR-9: `JobFireStoreImpl` completeFireAndUpdateSchedule schedule 版本检查
- AR-10: `JobTaskStoreImpl` updateTask 版本检查
- AR-11: `JobTimeoutCheckerImpl` tryMarkDispatchTimeout 取消关联 tasks
- AR-12: `JobScheduleStoreImpl` recovery 路径刷新 jobParamsSnapshot
- AR-13: `JobTimeoutCheckerImpl` SUSPICIOUS 宽限期
- AR-17: `DefaultJobCancelHandler` resolveJobParams 注入 targetHost header
- AR-18: `JobScheduleStoreImpl` insertManualFire overlay 统计更新
- 每项修复的测试覆盖

### Out Of Scope

- AR-6 (P3): parallel path setActiveFireCount(0) 死代码
- AR-7 (P3): maxFailedCount 硬编码为 0
- AR-14 (P3): copyMap 返回原始引用（方法名误导）
- AR-15 (P3): findFirstErrorTask 错误优先级不一致
- AR-16 (P3): RpcBroadcastTaskBuilder 不设置 taskPayload
- F9 (P1): retryRecordId 返回 jobFireId（异步设计限制）
- F14 (P3): JobFireResult.CONTINUE 命名冲突
- DAO→Core 依赖重构（独立计划）

## Execution Plan

### Phase 1 - P1 用户可感知 Bug 修复（AR-8, AR-17）

Status: completed
Targets: `nop-job/nop-job-service/`, `nop-job/nop-job-coordinator/`

- Item Types: `Fix`

- [x] AR-8: `NopJobFireBizModel.rerunFire` 检查 `insertManualFire` 返回值，`false` 时抛出异常，使用错误码 `ERR_JOB_FIRE_RERUN_DISCARDED`（参考 `triggerNow` 的 `ERR_JOB_SCHEDULE_MANUAL_TRIGGER_DISCARDED`）
- [x] AR-17: `DefaultJobCancelHandler` 的 `resolveJobParams` 方法签名改为 `resolveJobParams(NopJobSchedule schedule, NopJobFire fire, NopJobTask task)`（新增 `task` 参数），从 `task.getTargetHost()` 注入 `ApiConstants.HEADER_SVC_TARGET_HOST` header；同时修正 `CancelJobExecutionContext` 构造函数第 106 行 `setAttribute("targetHost", task.getWorkerAddress())` 改为 `task.getTargetHost()`（与执行路径 `DefaultJobExecutionContextBuilder.resolveJobParams` 保持一致）
- [x] 为 AR-8 添加测试：验证 DISCARD 策略下 `rerunFire` 抛出 `ERR_JOB_FIRE_RERUN_DISCARDED`
- [x] 为 AR-17 添加测试：验证取消上下文中 jobParams 包含 targetHost header（值为 `task.getTargetHost()` 非 `task.getWorkerAddress()`）

Exit Criteria:

- [x] `NopJobFireBizModel.rerunFire` 在 `insertManualFire` 返回 `false` 时抛出异常，不再静默成功
- [x] `DefaultJobCancelHandler.resolveJobParams` 接受 `NopJobTask task` 参数，返回的 Map 中包含 `headers.SVC_TARGET_HOST`，值为 `task.getTargetHost()`（非 `task.getWorkerAddress()`）；构造函数中 `setAttribute("targetHost", ...)` 也改用 `task.getTargetHost()`
- [x] 新增测试覆盖上述两项行为，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required（API 行为从"静默错误"修正为"正确行为"，不影响公共契约定义）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P1 修复残留语义错误（AR-1, AR-2）

Status: completed
Targets: `nop-job/nop-job-dao/`, `nop-job/nop-job-coordinator/`

- Item Types: `Fix`

- [x] AR-1: `JobFireStoreImpl.tryLockFiresForDispatch` 中 `fire.setStartTime(new Timestamp(now))`（使用实际当前时间），lock expiry 信号由 `updateTime` 保留
- [x] AR-2: `JobScheduleStoreImpl.recoveryFireAndAdvanceSchedule` 的 no-failed-fires 分支补充 `triggerSource=TRIGGER_SOURCE_RECOVERY`、`retryPolicyId`、`plannerInstanceId`、`jobParamsSnapshot`（从 schedule 刷新）
- [x] 为 AR-1 添加测试：验证 dispatch 后 `startTime` 在过去（`now` 附近），`updateTime` 在未来（`now + lockTimeoutMs`）
- [x] 为 AR-2 添加测试：验证 recovery 创建的 fire 包含完整字段

Exit Criteria:

- [x] `fire.startTime` 等于 dispatch 时刻的 `now`（不是 `now + lockTimeoutMs`），`cancelFire` 的 duration 计算不再始终为 0
- [x] dispatch timeout deadline 不再额外增加 `lockTimeoutMs`
- [x] recovery fire 的 no-failed-fires 路径创建的 fire 包含 `triggerSource`、`retryPolicyId`、`plannerInstanceId`、`jobParamsSnapshot`，与 `buildFire` 等价
- [x] 新增测试覆盖上述行为，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P1 并发安全修复（AR-9, AR-10）

Status: planned
Targets: `nop-job/nop-job-dao/`, `nop-job/nop-job-worker/`

- Item Types: `Fix`

- [ ] AR-9: `JobFireStoreImpl.completeFireAndUpdateSchedule` 中 `scheduleDao().updateEntityDirectly(schedule)` 改为 `scheduleDao().tryUpdateManyWithVersionCheck(Collections.singletonList(schedule))`，版本冲突时重试最多 3 次（加载最新 schedule、重新计算计数器差值、再次尝试更新），fire 已有幂等保护
- [ ] AR-10: `JobTaskStoreImpl.updateTask` 改为 `taskDao().tryUpdateManyWithVersionCheck(Collections.singletonList(task))`
- [ ] `JobWorkerScannerImpl.handleExecutionResult` 在版本冲突时（`updateTask` 返回空列表）重新加载 task 状态，若已被 TIMEOUT/CANCELED 则跳过，否则重试一次
- [ ] 为 AR-9 添加测试：模拟并发 fire completion，验证 schedule 计数器不丢失
- [ ] 为 AR-10 添加测试：模拟 worker 回调与 timeout checker 竞争，验证 TIMEOUT 状态不被覆盖

Exit Criteria:

- [ ] `completeFireAndUpdateSchedule` 对 schedule 使用乐观锁，版本冲突时重试，并发完成不丢失计数器更新
- [ ] `updateTask` 使用乐观锁，worker 回调版本冲突时重新检查状态，不覆盖 TIMEOUT/CANCELED
- [ ] 新增测试覆盖并发场景，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - P2 任务生命周期与事务边界修复（AR-3, AR-5, AR-11, AR-12, AR-13, AR-18）

Status: planned
Targets: `nop-job/nop-job-dao/`, `nop-job/nop-job-coordinator/`

- Item Types: `Fix`

- [ ] AR-3: `JobScheduleStoreImpl.insertManualFire` overlay 取消循环添加 try-catch（与 `overlayFireAndAdvanceSchedule` 一致）
- [ ] AR-5: `JobTimeoutCheckerImpl` 扩展 `fetchRunningTasks` 查询范围包含 CLAIMED 状态，或新增 `fetchClaimedTasks` 查询 + 超时回收逻辑
- [ ] AR-11: `JobTimeoutCheckerImpl.tryMarkDispatchTimeout` 在完成 fire 后查询并取消关联的未完成 tasks（与 `cancelFire` 一致）
- [ ] AR-12: `JobScheduleStoreImpl.recoveryFireAndAdvanceSchedule` 的有-failedFires 路径从 schedule 刷新 `jobParamsSnapshot` 和 `retryPolicyId`
- [ ] AR-13: `JobTimeoutCheckerImpl.scanTaskTimeouts` 中 `tryMarkSuspiciousIfWorkerGone` 成功后 `continue`，跳过当前周期的 timeout 检查
- [ ] AR-18: `JobScheduleStoreImpl.insertManualFire` overlay 分支添加 `totalFireCount` 和 `failFireCount` 统计更新
- [ ] 为 AR-5 添加测试：验证 CLAIMED 超时任务被正确回收
- [ ] 为 AR-11 添加测试：验证 dispatch timeout 时关联 tasks 被取消
- [ ] 为 AR-13 添加测试：验证 SUSPICIOUS 任务保留至少一个 scan 周期

Exit Criteria:

- [ ] `insertManualFire` overlay 路径在取消 fire 失败时不回滚整个事务
- [ ] CLAIMED 任务在超时后可被回收，不再产生永久孤儿
- [ ] dispatch timeout 时关联 tasks 被取消
- [ ] recovery 路径（有/无 failedFires）均使用 schedule 最新参数
- [ ] SUSPICIOUS 任务保留至少一个 scan 周期后才被转为 TIMEOUT
- [ ] `insertManualFire` overlay 路径正确更新 `totalFireCount`/`failFireCount`
- [ ] 新增测试覆盖，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - P2 retryRecordId 死写修复（AR-4）

Status: planned
Targets: `nop-job/nop-job-coordinator/`

- Item Types: `Fix`

- [ ] AR-4: 在 `handleRetryAndAlarm` 设置 `retryRecordId` 之后，增加 `fireStore.updateFireRecordId(fire)` 或在当前事务中刷新 fire 实体，确保 `retryRecordId` 写入 DB。注意：不将 `handleRetryAndAlarm` 移到 `completeFireAndUpdateSchedule` 之前（因为这会改变 retry bridge 在持久化前被调用的语义）
- [ ] 为 AR-4 添加测试：验证 retryRecordId 被正确持久化到 DB

Exit Criteria:

- [ ] `retryRecordId` 在 fire 持久化后不再为 null（如果 retry bridge 返回了有效 ID）
- [ ] 新增测试覆盖，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 5 项 P1 已修复（AR-1, AR-2, AR-8, AR-9, AR-17）
- [ ] 全部 8 项 P2 已修复（AR-3, AR-4, AR-5, AR-10, AR-11, AR-12, AR-13, AR-18）
- [ ] 每项修复有对应测试覆盖
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 已验证组件间调用链在运行时确实连通，无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] `./mvnw test -pl nop-job -am` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### AR-6: Planner parallel path setActiveFireCount(0) 死代码

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅误导后续开发者，不影响运行时行为。in-memory 值不会被重新读取，DB 值正确。
- Successor Required: no
- Successor Path: —

### AR-7: maxFailedCount 硬编码为 0

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 没有用户可见配置被静默忽略（该功能从未提供配置入口）。需要 ORM 模型变更，属于 feature enhancement。
- Successor Required: yes
- Successor Path: 待定（需先确认产品需求）

### AR-14: copyMap 返回原始引用而非副本

- Classification: `optimization candidate`
- Why Not Blocking Closure: 实际影响极低——fire 创建后立即持久化（ORM flush 做序列化），理论上的数据污染窗口在当前使用模式下不会触发。
- Successor Required: no
- Successor Path: —

### AR-15: findFirstErrorTask 错误优先级不一致

- Classification: `optimization candidate`
- Why Not Blocking Closure: 仅影响广播场景下 fire 的 errorCode 准确性，不影响执行结果。fire 的终态由 `resolveFinalFireStatus` 正确确定。
- Successor Required: no
- Successor Path: —

### AR-16: RpcBroadcastTaskBuilder 不设置 taskPayload

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前运行时 `DefaultJobExecutionContextBuilder.resolveJobParams` 从 fire 的 `jobParamsSnapshot` 读取参数（不依赖 taskPayload），数据层面不影响执行。
- Successor Required: no
- Successor Path: —

### F9: retryRecordId 返回 jobFireId 而非实际重试记录 ID

- Classification: `watch-only residual`
- Why Not Blocking Closure: 异步 retry API 设计限制——`onFireFailed` 回调在 retry record 创建前执行，无法获取真实 ID。需要 nop-retry 接口重新设计。
- Successor Required: yes
- Successor Path: 待定（需 nop-retry 接口演进）

### F14: JobFireResult.CONTINUE 命名冲突

- Classification: `optimization candidate`
- Why Not Blocking Closure: 编译和运行时无歧义（字段 vs 方法，类型不同），仅影响可读性。
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- 考虑将 `copyMap` 重命名为 `getOrEmpty` 或改为 `new HashMap<>(map)`（AR-14）
- 考虑为 `findFirstErrorTask` 添加优先级排序逻辑与 `resolveFinalFireStatus` 一致（AR-15）
- 考虑在广播 task 中也设置 taskPayload（AR-16）
- 考虑为 `maxFailedCount` 添加 ORM 列和配置入口（AR-7）
- 考虑重新设计 nop-retry 回调接口以支持同步获取 retryRecordId（F9）
- 清理 parallel path 死代码 `setActiveFireCount(0)`（AR-6）

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<no remaining plan-owned work 或 non-blocking follow-up 列表>>
