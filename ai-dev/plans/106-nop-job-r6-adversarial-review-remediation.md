# 106 nop-job Round 6 Adversarial Review Remediation

> Plan Status: planned
> Last Reviewed: 2026-06-03
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

Status: planned
Targets: `nop-job/nop-job-worker/`, `nop-job/nop-job-service/`, `nop-job/model/nop-job.orm.xml`

- Item Types: `Fix`

- [ ] AR-36: `JobWorkerScannerImpl.handleExecutionResult` 在写入 task 结果前调用 `fireStore.loadFire(task.getJobFireId())` 检查 fire 是否已为终态（CANCELED/TIMEOUT/FAILED/SUCCESS）。若 fire 已终态，跳过写入并记录 WARN 日志。`fireStore` 已注入（`JobWorkerScannerImpl.java:36,54-57`），无需额外注入
- [ ] AR-37: `RpcJobInvoker` 两处 `invokeAsync` 调用（`RpcJobInvoker.java:61,89`），将 `null` 超时替换为具体值。方案：在 `DefaultJobExecutionContextBuilder` 构建 context 时（约 line 82），将 `schedule.getTimeoutSeconds()` 存入 `IJobInstanceState.getAttributes()`（key=`"timeoutSeconds"`）。`RpcJobInvoker` 从 `jobCtx.getAttributes().get("timeoutSeconds")` 读取超时值，fallback 到全局默认 60 秒。此方案避免修改 `nop-job-api` 公共接口
- [ ] AR-38: 在 `nop-job/model/nop-job.orm.xml` 中 `NopJobFire` 实体的 indexes 区域添加 `<index name="IX_NOP_JOB_FIRE_SCHEDULE_STATUS" unique="false"><column name="jobScheduleId"/><column name="fireStatus"/></index>`
- [ ] 为 AR-36 添加测试：模拟 fire 已 CANCELED 时 worker handleExecutionResult 不写入 SUCCESS 结果
- [ ] 为 AR-37 添加测试：验证 RpcJobInvoker 传入非 null 超时值
- [ ] 为 AR-38 添加验证：确认 ORM 模型变更后 `./mvnw compile -pl nop-job -am` 通过

Exit Criteria:

- [ ] `handleExecutionResult` 检查 fire 终态，已取消/超时的 fire 对应 task 不被写入 SUCCESS
- [ ] `RpcJobInvoker` RPC 调用传入非 null 超时（来自 schedule 或默认值）
- [ ] `nop_job_fire` 表有 `(jobScheduleId, fireStatus)` 复合索引
- [ ] 新增测试覆盖，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P2 Unique Key 幂等性 + OnceTrigger Misfire + Null Promise（AR-39, AR-40, AR-41）

Status: planned
Targets: `nop-job/nop-job-dao/`, `nop-job/nop-job-core/`, `nop-job/nop-job-worker/`

- Item Types: `Fix`

- [ ] AR-39: `JobScheduleStoreImpl.hasWaitingFire` 查询添加 `triggerSource` 过滤条件，与 unique key 定义对齐。同时评估是否应在 `insertFireAndAdvanceSchedule` 的 try-catch 中处理 unique key 冲突（而非依赖幂等性检查）
- [ ] AR-40: `HandleMisfireTrigger.nextScheduleTime` 在调用内部 trigger 获取 `nextTime` 后，检查 `nextTime > 0 && nextTime < afterTime - misfireThreshold`。若超出窗口，返回 -1（不再触发）。注意：`OnceTrigger.nextScheduleTime()` 有副作用（设置 `first=false`，`OnceTrigger.java:17-35`），misfire 检查必须避免消费 trigger 状态。方案：先检查 `trigger` 是否为 `OnceTrigger` 且 `scheduleTime < afterTime - misfireThreshold`，直接返回 -1 而不调用 `trigger.nextScheduleTime()`
- [ ] AR-41: `JobWorkerScannerImpl` 中 `invoker.invokeAsync(ctx)` 返回 null 时，传入错误参数调用 `handleExecutionResult`（类似 `handleExecutionResult(taskId, null, new NopException(ERR_JOB_INVOKER_RETURNED_NULL))`）。需在 `JobCoreErrors`（worker 模块可访问）中添加 `ERR_JOB_INVOKER_RETURNED_NULL` 错误码（英文 description）
- [ ] 为 AR-39 添加测试：验证不同 triggerSource 的 fire 不被幂等性检查误拦截
- [ ] 为 AR-40 添加测试：验证 OnceTrigger 超过 misfire 阈值后不再触发
- [ ] 为 AR-41 添加测试：验证 invoker 返回 null 时 task 被标记为失败而非成功

Exit Criteria:

- [ ] `hasWaitingFire` 区分 triggerSource，手动触发和 cron 触发互不干扰
- [ ] `HandleMisfireTrigger` 对 OnceTrigger 的返回值检查 misfire 窗口
- [ ] null promise 不再被当作 SUCCESS，而是当作错误
- [ ] 新增测试覆盖，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P2 PartitionResolver 缓存 + 首次稳定性 + CronExpression GC 优化（AR-42, AR-43, AR-46）

Status: planned
Targets: `nop-job/nop-job-coordinator/`, `nop-job/nop-job-core/`

- Item Types: `Fix`

- [ ] AR-42: `JobPartitionResolver.resolvePartitions` 添加结果缓存。使用 `volatile long lastResolveTime` + 缓存结果字段，TTL=10 秒。`getInstances` 结果在 TTL 内直接返回缓存。`isUnstable` 仍使用实际查询结果更新
- [ ] AR-43: `CronExpression.getTimeAfter` 每次创建 `new GregorianCalendar()`（line 140）。`CronExpression` 实例被多线程共享（planner scanner + completion processor 可同时访问同一 schedule 的 trigger），因此**不能使用实例字段**。方案：使用 `ThreadLocal<GregorianCalendar>`，`withInitial(() -> { Calendar cal = new GregorianCalendar(); cal.setTimeZone(timeZone); return cal; })`。每次调用 `getTimeAfter` 时 `cal = threadLocalCal.get(); cal.setTimeInMillis(afterTime)`，try-finally 中不 remove（ThreadLocal 复用）。注意：`timeZone` 在 `CronExpression` 构造时确定，ThreadLocal 初始化时需引用 `this.timeZone`
- [ ] AR-46: `JobPartitionResolver.isUnstable` 中 `if (prev == null)` 分支返回 `true`（不稳定）而非 `false`，强制首次调用等待一个稳定窗口
- [ ] 为 AR-42 添加测试：验证 10 秒 TTL 内多次调用只查询一次 naming service
- [ ] 为 AR-43 添加测试：验证 getTimeAfter 结果正确性不变（回归测试）
- [ ] 为 AR-46 添加测试：验证首次调用返回不稳定状态

Exit Criteria:

- [ ] `JobPartitionResolver` 有 10 秒 TTL 缓存，减少 naming service 调用
- [ ] `CronExpression.getTimeAfter` 不再每次创建新 Calendar 实例
- [ ] `isUnstable` 首次调用返回 true
- [ ] 新增测试覆盖，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - P2 内部 Helper 版本保护（AR-44）

Status: planned
Targets: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

- Item Types: `Fix`

- [ ] AR-44: `JobScheduleStoreImpl` 的 3 个内部 helper 方法改为使用 `tryUpdateManyWithVersionCheck`：(1) `cancelFire(NopJobFire, Timestamp)` — fire 状态更新；(2) `cancelTasks(String, Timestamp)` — task 批量取消；(3) `resetFailedTasks(String, Timestamp)` — task 重置。版本冲突时检查当前 DB 状态，若已为终态则跳过。与 AR-33/AR-34 修复的外部 `cancelFire`（`JobFireStoreImpl`）保持一致
- [ ] 为 AR-44 添加测试：模拟 overlay 路径内部 cancel 与 timeout 并发，验证 fire/task 终态不被覆盖

Exit Criteria:

- [ ] `JobScheduleStoreImpl` 内部 helper 的 cancelFire/cancelTasks/resetFailedTasks 使用 `tryUpdateManyWithVersionCheck`
- [ ] 版本冲突时跳过而非覆盖终态
- [ ] 新增测试覆盖，测试通过
- [ ] `./mvnw test -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 3 项 P1 已修复（AR-36, AR-37, AR-38）
- [ ] 全部 7 项 P2 已修复（AR-39, AR-40, AR-41, AR-42, AR-43, AR-44, AR-46）
- [ ] 每项代码修复有对应测试覆盖
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

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
