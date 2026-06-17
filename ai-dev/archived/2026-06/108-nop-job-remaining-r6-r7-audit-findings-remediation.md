# 108 nop-job Remaining R6/R7 Audit Findings Remediation

> Plan Status: completed
> Last Reviewed: 2026-06-04
> Reviewed By: Independent sub-agent adversarial review R1 (ses_171287921ffe) — Not Ready (AR-44 already fixed; resolved) + R2 (ses_17125edd1ffe) — Not Ready (AR-40/AR-43 already fixed; resolved)
> Source: `ai-dev/audits/2026-06-04-adversarial-review-nop-job/01-open-findings.md` (R7 cumulative: 16 unfixed items), `ai-dev/audits/2026-06-03-adversarial-review-nop-job/summary.md`
> Related: `ai-dev/plans/107-nop-job-round7-and-deep-audit-remediation.md` (completed, excluded these items as out-of-scope)

## Purpose

修复 7 轮对抗性审查（AR-1~AR-53）中仍然存活的 13 项发现中的高价值 P2 项和 4 项高价值 P3。Plan 107 覆盖了 Round 7 新发现和深度审计 P2 项，但明确将 R6 未修复的 P2 和所有 P3 排除在 scope 之外。本计划承接这些剩余项。经独立审查两轮验证，AR-44/AR-43/AR-40 已在先前计划中修复，从 scope 移除。

## Current Baseline

- Plan 107 已完成：AR-48~53 已修复，深度审计 12 项 P2 已修复，AR-37 已验证
- 7 轮累计 53 项发现中，43+ 已修复（含 AR-40/AR-43/AR-44 已由先前计划修复），13 项仍未修复
- `./mvnw clean test -pl nop-job -am` 当前基线为 BUILD SUCCESS
- **仍然存活的未修复项**（经 R7 验证 + 两轮独立审查确认）：

### P2 项（8 项 = 6 核心发现 + 2 残留项）

| ID | 描述 | 来源轮次 | 修复复杂度 |
|----|------|---------|-----------|
| AR-27 residual | CronCalendar getNextIncludedTime 无最大迭代保护 | R5 | 中 |
| AR-31 residual | updateTask 失败 silent early-return 路径无日志 | R5 | 低 |
| AR-42 | JobPartitionResolver 每次扫描查询 naming service — 无缓存 | R6 | 低 |
| AR-46 | JobPartitionResolver 首次调用信任 naming service — 启动时集群不稳 | R6 | 低 |
| AR-48 | NopJobScheduleBizModel.recalculateNextFireTime 使用 System.currentTimeMillis | R7 | 低 |
| AR-49 | rerunFire 从旧 fire 复制 jobParamsSnapshot — 过时参数 | R7 | 低 |
| AR-50 | tryMarkDispatchTimeout 使用 loadSchedule — deleted schedule 创建永久毒丸 | R7 | 低 |
| AR-51 | timeout checker 忽略 updateTask 返回值 — 竞态无可见性 | R7 | 低 |
| AR-52 | NopRetryJobRetryBridge 返回 null — retryRecordId 跨系统追踪断裂 | R7 | 高（跨系统） |

> **注意**: AR-40（OnceTrigger misfire — `HandleMisfireTrigger:29-33` 已有 OnceTrigger 专项保护）、AR-43（CronExpression GC — `CronExpression:59,94-98` 已使用 ThreadLocal）、AR-44（ScheduleStore helper — 内部 helper 全部已使用 `tryUpdateManyWithVersionCheck`）均已在先前计划中修复，从本计划 scope 中移除。

### P3 项（8 项）

| ID | 描述 |
|----|------|
| AR-6 | Planner parallel path setActiveFireCount(0) 死写 |
| AR-7 | maxFailedCount 硬编码为 0 |
| AR-14 | copyMap 返回原始引用而非副本 |
| AR-15 | findFirstErrorTask 优先级不一致 |
| AR-16 | RpcBroadcastTaskBuilder 不设置 taskPayload |
| AR-45 | CronExpression.equals() 忽略 timeZone |
| AR-47 | RpcBroadcastTaskBuilder.emptyIfNull() 死代码 |
| AR-53 | completeTaskWithFailure 忽略 updateTask 返回值 |

## Goals

- 修复全部 8 项 in-scope P2 发现（7 核心 + AR-52 状态确认）
- 修复部分高价值 P3 项（AR-6 死写、AR-45 equals 时区、AR-47 死代码、AR-53 失败结果丢失）
- 所有代码修复有对应测试覆盖
- 推进 AR-52 (retryRecordId) 至可接受状态（评估回调响应 + 更新 ORM 注释）

## Non-Goals

- 不修复低影响 P3 项（AR-7 maxFailedCount、AR-14 copyMap 引用、AR-15 findFirstErrorTask 优先级、AR-16 广播 taskPayload）
- 不重新设计 nop-retry 集成接口（AR-52 根本解决方案需独立设计）
- 不重新设计调度器架构、ORM 模型结构或跨模块 API
- 不修改 `HandleMisfireTrigger` 的整体 misfire 策略（AR-40 聚焦 OnceTrigger 路径的防御性处理）

## Scope

### In Scope

- P2 修复：AR-42, AR-46, AR-48, AR-49, AR-50, AR-51, AR-27 residual, AR-31 residual
- P2 状态确认：AR-52 — 读取 `NopRetryJobRetryBridge` 的 `callAsync` 响应类型定义（`ApiResponse<Map<String, Object>>`），确认响应 body 是否包含 retry task ID；若不包含，仅更新 ORM 列注释为"异步重试，当前不可用"，不实现回调端点
- 高价值 P3：AR-6 (死写), AR-45 (equals 时区), AR-47 (死代码), AR-53 (失败结果丢失)

### Out Of Scope

- P3 项：AR-7, AR-14, AR-15, AR-16（watch-only residual，6 轮审查确认无升级趋势）
- 跨模块重新设计（AR-52 的根本解决需独立 plan）

## Execution Plan

### Phase 1 - 用户可感知缺陷与时钟一致性修复（AR-48, AR-49, AR-50）

Status: completed
Targets: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`

- Item Types: `Fix`

- [x] **AR-48 (P2)**: `NopJobScheduleBizModel.recalculateNextFireTime` 将 `System.currentTimeMillis()` 改为 `scheduleStore.getCurrentTime()`。`scheduleStore` 已注入到 BizModel。添加测试验证使用 DB clock。
- [x] **AR-49 (P2)**: `NopJobFireBizModel.buildRecoveryFire` 从当前 schedule 获取 `jobParamsSnapshot` 和 `retryPolicyId`，不从 `sourceFire` 复制。**注意**：`buildRecoveryFire(NopJobFire sourceFire, IServiceContext context)` 方法签名不含 `schedule` 参数，需改为 `buildRecoveryFire(NopJobFire sourceFire, NopJobSchedule schedule, IServiceContext context)` 或在方法内重新加载 schedule（前者更优，schedule 已在 `rerunFire` 中加载）。添加测试验证 rerun 使用最新 schedule 参数。测试：`TestNopJobFireBizModel.test_rerunFireUsesCurrentScheduleParams`
- [x] **AR-50 (P2)**: `JobTimeoutCheckerImpl.tryMarkDispatchTimeout` 将 `loadSchedule` 改为 `tryLoadSchedule`，null 时调用 `fireStore.failFireWithoutSchedule` — 与 `JobCompletionProcessorImpl` 已有模式一致。添加测试验证 deleted schedule 场景下 fire 被强制失败。

Exit Criteria:

- [x] `recalculateNextFireTime` 使用 DB clock（`scheduleStore.getCurrentTime()`），测试：`TestNopJobScheduleBizModel.test_recalculateNextFireTimeUsesDbClock`
- [x] `buildRecoveryFire` 从 schedule 获取参数，不从旧 fire 复制，测试：`TestNopJobFireBizModel.test_rerunFireUsesCurrentScheduleParams`
- [x] `tryMarkDispatchTimeout` 处理 deleted schedule（`tryLoadSchedule` + `failFireWithoutSchedule`），测试：`TestJobTimeoutChecker.test_dispatchTimeoutScheduleDeleted`
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required（纯内部行为修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 性能优化：Calendar 与 PartitionResolver（AR-27, AR-42, AR-46）

Status: completed
Targets: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CronCalendar.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPartitionResolver.java`

- Item Types: `Fix`

- [x] **AR-27 residual (P2)**: `CronCalendar.getNextIncludedTime` 添加 `MAX_ITERATION` 常量（如 10000），超出时 break 并返回 `timeStamp`。在 else 分支（毫秒递增）优先跳转到 `baseCalendar.getNextIncludedTime()` 结果。
- [x] **AR-42 (P2)**: `JobPartitionResolver` 添加 `volatile List<ServiceInstance> cachedServers` + `volatile long cacheTimestamp`，TTL 5-10 秒。`resolvePartitions` 先检查缓存有效性，过期才查 naming service。`isUnstable` 比较仍然基于最新查询。**注意**：AR-42 和 AR-46 修改同一文件，应一起实现。
- [x] **AR-46 (P2)**: `JobPartitionResolver.isUnstable` 首次调用时返回 `true`（不稳定），强制跳过当前周期。添加 `boolean initialized` 标志。

Exit Criteria:

- [x] `CronCalendar.getNextIncludedTime` 有最大迭代保护（`MAX_ITERATION`），不退化为毫秒级扫描。测试：`TestCronCalendar.test_maxIterationProtection`
- [x] `JobPartitionResolver` 有 5-10 秒 TTL 缓存，有测试验证缓存命中和过期。测试：`TestJobPartitionResolver.test_cacheHitAndExpiry`
- [x] `JobPartitionResolver` 首次调用返回不稳定，有测试验证。测试：`TestJobPartitionResolver.test_firstCallUnstable`
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 并发安全收口：超时可见性与结果丢失防护（AR-51, AR-31）

Status: completed
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`

- Item Types: `Fix`

- [x] **AR-51 (P2)**: `JobTimeoutCheckerImpl.tryMarkTimeout` 和 `markSuspiciousAsTimeout` 检查 `updateTask` 返回值。`false` 时 reload task 检查状态：如果已终态则跳过，如果仍非终态则记录 WARN 日志。
- [x] **AR-31 residual (P2)**: `JobWorkerScannerImpl.handleExecutionResult` 中 `updateTask` 失败后的 silent early-return 路径（freshTask 仍为 RUNNING/CLAIMED），添加 WARN 日志记录结果被丢弃。

Exit Criteria:

- [x] timeout checker 检查 `updateTask` 返回值，版本冲突时有 WARN 日志，测试：`TestJobTimeoutChecker.test_timeoutUpdateTaskVersionConflictLogsWarn`
- [x] worker 结果丢弃路径有 WARN 日志（`handleExecutionResult` 中 freshTask 仍为 RUNNING/CLAIMED）
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 高价值 P3 清理（AR-6, AR-45, AR-47, AR-53）与 AR-52 状态确认

Status: completed
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`, `nop-job/nop-job-retry-adapter/`

- Item Types: `Fix | Decision`

- [x] **AR-6 (P3)**: `JobPlannerScannerImpl:172` 移除 `schedule.setActiveFireCount(0)` 死写，添加注释说明 `insertFireAndAdvanceSchedule` 内部已正确递增计数器。
- [x] **AR-45 (P3)**: `CronExpression.equals()` 和 `hashCode()` 加入 `timeZone` 比较。
- [x] **AR-47 (P3)**: 删除 `RpcBroadcastTaskBuilder.emptyIfNull()` 死代码方法。
- [x] **AR-53 (P3)**: `JobWorkerScannerImpl.completeTaskWithFailure` 检查 `updateTask` 返回值，`false` 时记录 WARN 日志。
- [x] **AR-52 (P2→状态确认)**: 读取 `NopRetryJobRetryBridge.whenComplete` 回调中的 `resp` 类型定义，确认响应是否包含 retry task ID。**本项的实现范围限于**：(a) 如果响应包含 ID → 提取并调用 `fireStore.updateRetryRecordId`；(b) 如果不包含 → 仅更新 ORM 列注释为"异步重试，当前不可用"，不做回调端点实现。

Exit Criteria:

- [x] `setActiveFireCount(0)` 死写已移除，有注释说明
- [x] `CronExpression.equals/hashCode` 包含 timeZone，测试：`TestCronExpression.test_equalsDifferentTimeZone`
- [x] `emptyIfNull()` 死代码已删除
- [x] `completeTaskWithFailure` 检查返回值并有 WARN 日志
- [x] AR-52 的 `whenComplete` 回调响应已评估，ORM 注释已更新为准确状态
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 8 项 in-scope P2 发现已修复或确认状态（AR-27, AR-31, AR-42, AR-46, AR-48, AR-49, AR-50, AR-51 + AR-52 状态确认）
- [x] AR-52 已确认状态：ORM 注释准确，或回调机制已实现
- [x] 4 项高价值 P3 已修复（AR-6, AR-45, AR-47, AR-53）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] Anti-Hollow Check: closure audit 已验证（a）组件间调用链在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-job -am` 成功
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Low-Impact P3 Items

- Classification: `watch-only residual`
- Why Not Blocking Closure: 全部为 P3，不影响数据完整性或状态机正确性。已在 7 轮审查中确认无升级趋势。
- Successor Required: no
- Items:
  - AR-7 (P3): maxFailedCount hardcoded to 0 — 无 ORM column，功能未暴露
  - AR-14 (P3): copyMap 返回原始引用 — ORM flush 保护，实际无数据污染
  - AR-15 (P3): findFirstErrorTask 优先级不一致 — 仅影响 errorCode 展示
  - AR-16 (P3): RpcBroadcastTaskBuilder 无 taskPayload — 执行路径不依赖 payload

## Non-Blocking Follow-ups

- 考虑将 `JobPartitionResolver` 的缓存 TTL 提升为可配置项
- 考虑为 `NopRetryJobRetryBridge` 添加回调端点机制（AR-52 根本解决方案）

## Closure

Status Note: 全部 4 个 Phase 执行完成，8 项 P2 + 4 项高价值 P3 + AR-52 状态确认均已完成。独立 closure audit (ses_170fa1f41ffe) 通过，Anti-Hollow Check 全部 10 项 PASS。

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (ses_170fa1f41ffe)
- Evidence: Anti-Hollow Check 全部 PASS — 源码验证所有修复调用真实方法，无空方法体/no-op。Commits: 495956db0, 83fc84b29, be337f1fc, 158414fae。`./mvnw test -pl nop-job -am` BUILD SUCCESS (167 tests, 0 failures)。

Follow-up:

- 考虑将 `JobPartitionResolver` 的缓存 TTL 提升为可配置项
- 考虑为 `NopRetryJobRetryBridge` 添加回调端点机制（AR-52 根本解决方案）
