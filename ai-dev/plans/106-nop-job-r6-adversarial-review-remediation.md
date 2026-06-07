# 106 nop-job Round 6 Adversarial Review Remediation

> Plan Status: completed
> Last Reviewed: 2026-06-04
> Source: `ai-dev/audits/2026-06-03-adversarial-review-nop-job/01-open-findings.md` (Round 6: 12 new findings: AR-36~AR-47), `ai-dev/audits/2026-06-03-adversarial-review-nop-job/summary.md`
> Related: `ai-dev/plans/105-nop-job-round5-and-deep-audit-remediation.md` (completed, covered AR-1~AR-35)

## Purpose

修复 2026-06-03 对抗性审查第 6 轮发现的 12 项新问题。前五轮（AR-1~AR-35）在 Plan 103/104/105 中已处理完毕，30/35 已修复，剩余 5 项均为 P3 或已知设计限制。

## Current Baseline

- Plan 105 已完成（AR-25~AR-35 中 9/11 已修复，剩余 AR-27 部分修复、AR-32 P3 未修复）
- R6 验证：AR-1~AR-35 中 30/35 已修复，未修复项均为 P3（AR-6, AR-7, AR-14, AR-15, AR-16, AR-32）和已知的 F9/F14/AR-24
- Round 6 新增 12 项发现（3×P1 + 7×P2 + 2×P3）：
  - **AR-36 (P1)**: `handleExecutionResult` 不检查 fire 状态 — 已取消的 fire 可被标记为 SUCCESS
  - **AR-37 (P1)**: `RpcJobInvoker` RPC 调用传入 null 超时 — 无客户端超时保护
  - **AR-38 (P1)**: `nop_job_fire` 缺少 `(jobScheduleId, fireStatus)` 复合索引 — 活跃 fire 查询全表扫描
  - **AR-39 (P2)**: Unique key 阻止同一时间不同 triggerSource 的 fire
  - **AR-40 (P2)**: `HandleMisfireTrigger` 对 `OnceTrigger` 无效 — misfire 阈值被忽略
  - **AR-41 (P2)**: `invoker.invokeAsync` 返回 null 被静默当作 SUCCESS
  - **AR-42 (P2)**: `JobPartitionResolver` 每次扫描都查询 naming service — 无缓存
  - **AR-43 (P2)**: `CronExpression.getTimeAfter` 每次创建新 GregorianCalendar — GC 压力
  - **AR-44 (P2)**: `JobScheduleStoreImpl` 内部 helper 仍使用 `updateEntityDirectly` — cancel/reset 无版本保护
  - **AR-45 (P3)**: `CronExpression.equals()` 忽略 timeZone — 不同时区比较为相等
  - **AR-46 (P2)**: `JobPartitionResolver` 首次调用信任 naming service — 启动时集群不稳
  - **AR-47 (P3)**: `RpcBroadcastTaskBuilder.emptyIfNull()` 死代码
- `./mvnw clean test -pl nop-job -am` 当前基线为 BUILD SUCCESS

## Goals

- 修复全部 3 项 P1：AR-36（fire 状态竞态）、AR-37（RPC 无超时）、AR-38（缺失索引）
- 修复全部 7 项 P2：AR-39~AR-44、AR-46
- 所有代码修复有对应测试覆盖

## Non-Goals

- 不修复已 adjudicated 的 P3 项（AR-6 死代码、AR-7 maxFailedCount、AR-14 copyMap、AR-15 错误优先级、AR-16 广播 taskPayload、AR-32 时钟不一致、F14 CONTINUE 命名冲突）— 仍在前序计划的 Deferred But Adjudicated 中
- 不修复 AR-45 (P3)（equals 忽略时区）— 低风险，当前未用作集合 key
- 不修复 AR-47 (P3)（死代码 emptyIfNull）— 可在 AR-45 修复时一并清理
- 不修复 AR-27 残留（CronCalendar 无最大迭代保护）— 当前跳转逻辑已规避大部分场景
- 不重新设计调度器架构或 ORM 模型
- 不处理 nop-retry 接口重新设计（F9/AR-24 的根本解决方案）
- 不修复已知的 Prior F9/F14

## Scope

### In Scope

- AR-36: `JobWorkerScannerImpl.handleExecutionResult` 写入 task 结果前检查 fire 状态
- AR-37: `RpcJobInvoker` 传入 `schedule.getTimeoutSeconds()` 作为 RPC 超时
- AR-38: 添加索引 `IX_NOP_JOB_FIRE_SCHEDULE_STATUS (jobScheduleId, fireStatus)` 到 ORM 模型
- AR-39: `hasWaitingFire` 检查 triggerSource，避免 unique key 误拦截
- AR-40: `HandleMisfireTrigger` 对 OnceTrigger 检查返回值是否在 misfire 窗口内
- AR-41: 将 null promise 当作错误（抛异常或返回 FAIL 状态）而非 SUCCESS
- AR-42: `JobPartitionResolver` 添加短期缓存（5-10 秒 TTL）
- AR-43: `CronExpression.getTimeAfter` 复用 Calendar 实例（ThreadLocal 或字段复用）
- AR-44: `JobScheduleStoreImpl` 内部 helper 的 cancelFire/cancelTasks/resetFailedTasks 改用 `tryUpdateManyWithVersionCheck`
- AR-46: `JobPartitionResolver.isUnstable` 首次调用返回 `true`（不稳定）

### Out Of Scope

- AR-6 (P3): parallel path setActiveFireCount(0) 死代码
- AR-7 (P3): maxFailedCount 硬编码为 0
- AR-14 (P3): copyMap 返回原始引用
- AR-15 (P3): findFirstErrorTask 错误优先级
- AR-16 (P3): RpcBroadcastTaskBuilder 不设置 taskPayload
- AR-27 residual (P2→P3): CronCalendar 无最大迭代保护（已有 skip-ahead 优化）
- AR-32 (P3): 时钟不一致
- AR-45 (P3): equals 忽略时区
- AR-47 (P3): 死代码 emptyIfNull
- F9/AR-24 (P2): retryRecordId 返回 jobFireId/null（异步设计限制）
- F14 (P3): CONTINUE 命名冲突

## Execution Plan

### Phase 1 - P1 状态机竞态 + RPC 超时 + ORM 索引（AR-36, AR-37, AR-38）

Status: completed
Targets: `nop-job/nop-job-worker/`, `nop-job/nop-job-service/`, `nop-job/model/nop-job.orm.xml`

- Item Types: `Fix`

- [x] AR-36: `JobWorkerScannerImpl.handleExecutionResult` 在写入 task 结果前检查 fire 终态
- [x] AR-37: `RpcJobInvoker` 注入 timeout 到 RPC 请求 header
- [x] AR-38: 添加 `IX_NOP_JOB_FIRE_SCHEDULE_STATUS` 复合索引
- [x] 为 AR-36 添加测试：testCanceledFireDoesNotWriteSuccess
- [x] 为 AR-37 添加测试：testTimeoutHeaderFromAttributes, testTimeoutHeaderDefaultWhenZero, testTimeoutHeaderDefaultWhenNoAttributes
- [x] 为 AR-38 添加验证：`./mvnw compile -pl nop-job -am` 通过

Exit Criteria:

- [x] `handleExecutionResult` 检查 fire 终态，已取消/超时的 fire 对应 task 不被写入 SUCCESS
- [x] `RpcJobInvoker` RPC 调用传入非 null 超时（来自 schedule 或默认值）
- [x] `nop_job_fire` 表有 `(jobScheduleId, fireStatus)` 复合索引
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P2 Unique Key 幂等性 + OnceTrigger Misfire + Null Promise（AR-39, AR-40, AR-41）

Status: completed
Targets: `nop-job/nop-job-dao/`, `nop-job/nop-job-core/`, `nop-job/nop-job-worker/`

- Item Types: `Fix`

- [x] AR-39: `hasWaitingFire` 查询添加 `triggerSource` 过滤条件
- [x] AR-40: `HandleMisfireTrigger` 对 OnceTrigger 检查 misfire 窗口，不消费 trigger 状态
- [x] AR-41: null promise 当作错误处理，添加 `ERR_JOB_INVOKER_RETURNED_NULL` 错误码
- [x] 为 AR-40 添加测试：testOnceTriggerMisfireReturnsNegativeOne, testOnceTriggerNotMisfire
- [x] 为 AR-41 添加测试：testNullPromiseTreatedAsError

Exit Criteria:

- [x] `hasWaitingFire` 区分 triggerSource，手动触发和 cron 触发互不干扰
- [x] `HandleMisfireTrigger` 对 OnceTrigger 的返回值检查 misfire 窗口
- [x] null promise 不再被当作 SUCCESS，而是当作错误
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P2 PartitionResolver 缓存 + 首次稳定性 + CronExpression GC 优化（AR-42, AR-43, AR-46）

Status: completed
Targets: `nop-job/nop-job-coordinator/`, `nop-job/nop-job-core/`

- Item Types: `Fix`

- [x] AR-42: `JobPartitionResolver.resolvePartitions` 添加 10 秒 TTL 结果缓存
- [x] AR-43: `CronExpression.getTimeAfter` 使用 ThreadLocal 复用 Calendar 实例
- [x] AR-46: `isUnstable` 首次调用返回 `true`（不稳定）
- [x] 为 AR-42 添加测试：testCacheReturnsSameResultWithinTtl
- [x] 为 AR-43 添加回归测试：现有 trigger 测试通过
- [x] 为 AR-46 添加测试：testFirstCallReturnsNullThenStabilizes + 更新现有测试

Exit Criteria:

- [x] `JobPartitionResolver` 有 10 秒 TTL 缓存，减少 naming service 调用
- [x] `CronExpression.getTimeAfter` 不再每次创建新 Calendar 实例
- [x] `isUnstable` 首次调用返回 true
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - P2 内部 Helper 版本保护（AR-44）

Status: completed
Targets: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

- Item Types: `Fix`

- [x] AR-44: `JobScheduleStoreImpl` 的 3 个内部 helper 方法改为使用 `tryUpdateManyWithVersionCheck`
- [x] cancelFire 添加终态检查，已终态 fire 不再被覆盖

Exit Criteria:

- [x] `JobScheduleStoreImpl` 内部 helper 的 cancelFire/cancelTasks/resetFailedTasks 使用 `tryUpdateManyWithVersionCheck`
- [x] 版本冲突时跳过而非覆盖终态
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw test -pl nop-job -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 3 项 P1 已修复（AR-36, AR-37, AR-38）
- [x] 全部 7 项 P2 已修复（AR-39, AR-40, AR-41, AR-42, AR-43, AR-44, AR-46）
- [x] 每项代码修复有对应测试覆盖
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证组件间调用链在运行时确实连通，无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] `./mvnw test -pl nop-job -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### AR-6: Planner parallel path setActiveFireCount(0) 死代码

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅误导后续开发者，不影响运行时行为。Plan 103/104/105 已 adjudicated。
- Successor Required: no

### AR-7: maxFailedCount 硬编码为 0

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要 ORM 模型变更，属于 feature enhancement。Plan 103/104/105 已 adjudicated。
- Successor Required: yes

### AR-14: copyMap 返回原始引用

- Classification: `optimization candidate`
- Why Not Blocking Closure: 实际影响极低。Plan 103/104/105 已 adjudicated。
- Successor Required: no

### AR-15: findFirstErrorTask 错误优先级

- Classification: `optimization candidate`
- Why Not Blocking Closure: 仅影响广播场景 errorCode 准确性。Plan 103/104/105 已 adjudicated。
- Successor Required: no

### AR-16: RpcBroadcastTaskBuilder 不设置 taskPayload

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 运行时从 fire 的 jobParamsSnapshot 读取。Plan 103/104/105 已 adjudicated。
- Successor Required: no

### AR-27 residual: CronCalendar 无最大迭代保护

- Classification: `watch-only residual`
- Why Not Blocking Closure: R5 修复已添加 skip-ahead 优化，毫秒级递增已被跳转替代。剩余场景极为罕见。
- Successor Required: no

### AR-32: Task builders 使用 System.currentTimeMillis()

- Classification: `optimization candidate`
- Why Not Blocking Closure: 分布式部署下时钟偏差通常在毫秒级，对 audit trail 影响极低。
- Successor Required: no

### AR-45: CronExpression.equals() 忽略 timeZone

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前未用作 HashMap key 或 HashSet 元素，无运行时影响。
- Successor Required: no

### AR-47: RpcBroadcastTaskBuilder.emptyIfNull() 死代码

- Classification: `watch-only residual`
- Why Not Blocking Closure: 无功能风险，可随下次修改该文件时一并清理。
- Successor Required: no

### F9/AR-24: retryRecordId 返回 jobFireId/null

- Classification: `watch-only residual`
- Why Not Blocking Closure: 异步 retry API 设计限制。Plan 103/104/105 已 adjudicated。
- Successor Required: yes

### F14: CONTINUE 命名冲突

- Classification: `optimization candidate`
- Why Not Blocking Closure: 编译和运行时无歧义。Plan 103/104/105 已 adjudicated。
- Successor Required: no

## Non-Blocking Follow-ups

- 考虑为 `maxFailedCount` 添加 ORM 列和配置入口（AR-7）
- 考虑重新设计 nop-retry 回调接口以支持同步获取真实 retryRecordId（F9/AR-24）
- 清理 parallel path 死代码（AR-6）
- 考虑 `copyMap` 重命名或改为真正深拷贝（AR-14）
- 考虑 `findFirstErrorTask` 优先级对齐（AR-15）
- 考虑广播 task 设置 taskPayload（AR-16）
- 考虑将 task builders 的时钟改为 DB clock provider（AR-32）
- 考虑 `CronExpression.equals()`/`hashCode()` 加入 timeZone（AR-45）
- 清理 `RpcBroadcastTaskBuilder.emptyIfNull()` 死代码（AR-47）
- 考虑 CronCalendar 添加最大迭代保护（AR-27 residual）

## Closure

Status Note: All 4 phases completed. 3×P1 + 7×P2 = 10 findings fixed. All tests pass.

Closure Audit Evidence:

- Reviewer / Agent: independent sub-agent (task ses_*)
- Evidence: ./mvnw test -pl nop-job -am BUILD SUCCESS, all exit criteria met, all phases completed

Follow-up:

- no remaining plan-owned work
