# 19 nop-job 测试覆盖与容错加固

> Plan Status: planned
> Last Reviewed: 2026-05-18
> Review Round: 2 (3 agents reviewed, improvements applied)
> Source: `ai-dev/analysis/2026-05-18-fault-tolerance-deep-dive.md`, `ai-dev/analysis/2026-05-18b-powerjob-vs-nop-job-fault-tolerance.md`, `ai-dev/plans/18-nop-job-fault-tolerance-improvement-plan.md`
> Related: `18-nop-job-fault-tolerance-improvement-plan.md` (Plan 18 已 completed 但 Closure Gates 未闭合)

## Purpose

Plan 18 完成了 nop-job 容错功能的接口和实现骨架（动态分区、重试桥接、Worker 故障检测、进度汇报、告警通知），但：
1. **单元测试严重不足**：新增的容错逻辑（动态分区 resolvePartitions、retry bridge 调用、alarm handler 调用、进度更新）缺少 focused unit test
2. **端到端测试缺失**：没有验证 Planner → Dispatcher → Worker → Completion 完整生命周期的集成测试
3. **容错场景测试缺失**：Coordinator 崩溃恢复、Worker 故障超时检测、乐观锁竞争、partition rebalance 等关键容错场景无测试覆盖
4. **G1 Worker 心跳未真正实现**：只有 `TASK_STATUS_SUSPICIOUS` 常量，Worker 没有注册到 `INamingService`，Coordinator 没有利用服务发现感知 Worker
5. **G2 nop-retry 集成缺少真实适配器**：只有 `IJobRetryBridge` 接口 + `NoOpJobRetryBridge`，没有 `nop-job-retry-adapter` 模块对接 `IRetryEngine`
6. **G4 Dispatch 超时检查未实现**：TimeoutChecker 只检查 RUNNING 状态的 Task/Fire，没有检查 DISPATCHING 状态下长时间无 Task 的 Fire

本计划补齐缺失实现 + 补齐测试 + 修复测试中发现的问题，使 nop-job 的容错能力经过验证后才进入生产。

## Current Baseline

### G1-G4 实现状态审计（2026-05-18 代码核查）

| Gap | 描述 | 代码现状 | 结论 |
|-----|------|---------|------|
| **G1** | Worker 心跳/快速故障检测 | `TASK_STATUS_SUSPICIOUS(15)` 常量存在于 `_NopJobCoreConstants:79`，但 **Worker 未注册到 INamingService**，Coordinator 未通过服务发现感知 Worker。Worker Scanner 无 IDiscoveryClient 依赖 | **❌ 未实现** |
| **G2** | 自动重试 | `IJobRetryBridge` 接口 + `NoOpJobRetryBridge` 已存在。`JobCompletionProcessorImpl:216-241` 已调用 bridge 和 alarm。**但 `nop-job-retry-adapter` 模块不存在**，无 `NopRetryJobRetryBridge` 对接 `IRetryEngine`。集成链路：接口已打通，真实适配器未实现 | **⚠️ 接口已实现，适配器未实现** |
| **G3** | Coordinator 动态 Rebalance | `JobPartitionResolver` 已实现（INamingService + PartitionAssignHelper + stabilization window + isUnstable 检测）。4 个 Scanner 全部注入了 `partitionResolver` 并调用 `resolvePartitions()` | **✅ 已实现** |
| **G4** | Dispatch 超时检查 | `JobTimeoutCheckerImpl` 只扫描 `taskStatus=RUNNING` 的 Task。**无 `FIRE_STATUS_DISPATCHING` 超时检查**。Fire 处于 DISPATCHING 但长时间无 Task 的场景未覆盖 | **❌ 未实现** |

### 已完成的其他功能

- `IJobAlarmHandler` + `NoOpJobAlarmHandler` + `LoggingJobAlarmHandler` — 已实现
- `IJobTaskStore.updateTaskProgress()` — 已实现（`JobTaskStoreImpl:118`）
- `JobAlarmEvent` / `JobFireFailedEvent` 事件对象 — 已实现
- `JobCompletionProcessorImpl` 中 fire 失败时调用 retry bridge + alarm — 已实现
- 乐观锁 + REQUIRES_NEW 事务保护 — 已实现

### 现有测试

- `TestJobCoordinatorScanner` — Planner + Dispatcher + Completion 基本流程（1 个测试）
- `TestJobWorkerScanner` — Worker 执行成功/失败（3 个测试）
- `TestJobConcurrency` — 乐观锁竞争（3 个测试）
- `TestDefaultJobTaskBuilder` — TaskBuilder 默认行为
- `TestJobStoreImpl` — Store CRUD
- `TestTrigger` / `TestJobTriggerCalculator` — Trigger 算法
- `TestLocalJobScheduler` — 旧版 Scheduler

### 缺失测试

动态分区、retry bridge 调用路径、alarm handler 调用路径、超时检测、进度汇报、recovery 阻塞策略、partition rebalance 后处理、完整 E2E 生命周期

## Goals

1. **补齐 G1**：Worker 注册到 INamingService，Coordinator 通过服务发现感知 Worker 存活，快速标记 SUSPICIOUS
2. **补齐 G2**：实现 `nop-job-retry-adapter` 模块，对接 `IRetryEngine`，使 retry bridge 完整打通
3. **补齐 G4**：在 TimeoutChecker 中增加 DISPATCHING 状态 Fire 的超时检查
4. 每个容错机制都有至少 1 个 focused unit test 验证其核心行为
5. 完整的 E2E 生命周期测试：Schedule 创建 → Planner 触发 → Dispatcher 分发 → Worker 执行 → Completion 聚合
6. 关键容错场景的集成测试：Worker 超时、Coordinator 崩溃恢复、乐观锁竞争、partition rebalance
7. 所有新增测试在 `./mvnw test -pl nop-job` 下通过

## Non-Goals

- 不修改 Plan 18 已完成的接口或核心实现逻辑（除非测试发现 bug）
- 不实现 nop-job-retry-adapter（Plan 18 的 Non-Blocking Follow-up）
- 不实现前端 UI
- 不做性能/压力测试
- 不实现 HashedWheelTimer 精确超时

## Scope

### In Scope

- nop-job-coordinator 单元测试和集成测试
- nop-job-worker 单元测试和集成测试
- nop-job-dao Store 层测试
- nop-job-core Trigger 测试补充
- 容错场景端到端测试
- 测试中发现的问题修复

### Out Of Scope

- nop-job-retry-adapter 模块
- nop-job-web / nop-job-app
- 前端 UI 测试
- 性能基准测试

## Execution Plan

> **Phase 依赖**：Phase 1A/1B/1C 是缺失实现的补齐，应优先于测试。Phase 2-6 是测试，Phase 7 是修复和回归。

### Phase 1A - G1: Coordinator Worker 存活检查（SUSPICIOUS 标记）

Status: planned
Targets: `nop-job-coordinator`

- Item Types: `Fix`

- [ ] 在 `JobTimeoutCheckerImpl.scanOnce()` 中增加 Worker 存活检查：通过 `INamingService.getInstances()` 获取活跃 Worker 列表（复用 `JobPartitionResolver.namingService`），如果 RUNNING Task 的 `workerInstanceId` 不在活跃列表中，标记 Task 为 `TASK_STATUS_SUSPICIOUS`，下一轮扫描时如果 Worker 仍不在列表，标记为 `TASK_STATUS_TIMEOUT`
- [ ] Worker 端**无需额外代码**：`AutoRegistration`（`rpc-cluster-defaults.beans.xml`）已由 nop-rpc-cluster 提供，配置 `nop.cluster.registration.enabled=true` 即自动注册。注册即心跳，由服务发现基础设施（Nacos/DB）负责续期和健康检查
- [ ] `TASK_STATUS_SUSPICIOUS` → `TASK_STATUS_TIMEOUT` 的转换：SUSPICIOUS 状态在下一轮扫描时若 Worker 仍失联，转为 TIMEOUT
- [ ] `enable-cluster=false` 时跳过存活检查（退化为现有纯超时检测）

Exit Criteria:

- [ ] Coordinator TimeoutChecker 能检测到 Worker 下线并标记 SUSPICIOUS → TIMEOUT
- [ ] Worker 端零代码改动，仅配置 `nop.cluster.registration.enabled=true`
- [ ] `enable-cluster=false` 时行为不变
- [ ] `ai-dev/design/nop-job/` 文档已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 1B - G2: nop-job-retry-adapter 实现

Status: planned
Targets: `nop-job/nop-job-retry-adapter`（新模块）

- Item Types: `Fix`

- [ ] 创建 `nop-job-retry-adapter` 模块（pom.xml，依赖 nop-job-api + nop-retry-core）
- [ ] 实现 `NopRetryJobRetryBridge`：
  - 从 `JobFireFailedEvent` 读取 `retryPolicyId`
  - 通过 `IRetryEngine.newRetryTask()` 创建 retry task
  - `.withPolicyId(event.getRetryPolicyId())`
  - `.withIdempotentId("job-fire:" + event.getJobFireId())` 幂等控制
  - `.callAsync()` 提交重试
  - 返回 retry record ID
- [ ] 注册为 IoC bean（`@Inject` 时优先于 NoOpJobRetryBridge）
- [ ] 模块可选：不引入时 NoOpJobRetryBridge 生效，引入时自动切换

Exit Criteria:

- [ ] `nop-job-retry-adapter` 模块存在且可编译
- [ ] `NopRetryJobRetryBridge` 对接 `IRetryEngine` 可工作
- [ ] 不引入 adapter 时 NoOpJobRetryBridge 不受影响
- [ ] `ai-dev/design/nop-job/retry-integration-design.md` 与实现一致
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 1C - G4: Dispatch 超时检查

Status: planned
Targets: `nop-job-coordinator`

- Item Types: `Fix`

- [ ] 在 `JobTimeoutCheckerImpl.scanOnce()` 中增加 `FIRE_STATUS_DISPATCHING` 超时检查：
  - 查询 `fireStatus=DISPATCHING` 且 `startTime + dispatchTimeoutMs < now` 的 Fire
  - 标记 Fire 为 `FIRE_STATUS_TIMEOUT`（dispatch 超时）
  - 更新 Schedule 统计
  - 触发 alarm
- [ ] 新增配置项 `nop.job.coordinator.dispatch-timeout-ms`（默认 300000，即 5 分钟）
- [ ] Dispatch 超时与 Task 超时是独立的：Fire 可能 dispatch 超时（无 Worker 认领），也可能 task 超时（Worker 执行超时）

Exit Criteria:

- [ ] TimeoutChecker 扫描 `DISPATCHING` 状态的超时 Fire
- [ ] 超时后 Fire 标记 TIMEOUT + 触发 alarm
- [ ] Schedule 统计正确更新
- [ ] 不影响现有 Task 级超时检测
- [ ] `ai-dev/design/nop-job/` 文档已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 动态分区单元测试

Status: planned
Targets: `nop-job-coordinator/src/test/java/`, `nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPartitionResolver.java`

- Item Types: `Proof`

- [ ] `TestJobPartitionResolver` — 验证 `resolvePartitions()` 核心行为：
  - enable-cluster=false 时返回 null（不分区）
  - 静态 assignedPartitions 覆盖动态计算
  - INamingService 返回多个实例时正确计算 partition range
  - stabilization window 期间使用缓存结果（实例列表变化后 30s 内不重新分配）
  - 实例列表从 [A, B] 变为 [A]（B 下线）后 window 结束时重新分配
  - 实例列表为空时返回 null
  - **[Round 2]** stabilization window 边界条件：window 刚过期时立即重新计算
  - **[Round 2]** 实例列表频繁抖动（A-B-A-B）：stabilization window 防止反复重分配
  - **[Round 2]** 实例列表从 [A] 变为 [A, B, C]：partition 正确缩窄
- [ ] 确认测试不依赖外部服务发现（mock INamingService）

Exit Criteria:

- [ ] `TestJobPartitionResolver` 存在且包含上述 9 个场景
- [ ] `./mvnw test -pl nop-job/nop-job-coordinator -Dtest=TestJobPartitionResolver` 通过
- [ ] No owner-doc update required（测试不改变行为契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Retry Bridge 与 Alarm Handler 调用路径测试

Status: planned
Targets: `nop-job-coordinator/src/test/java/`, `nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`

- Item Types: `Proof`

- [ ] `TestJobCompletionProcessorRetryBridge` — 验证 CompletionProcessor 的 retry bridge 调用路径：
  - fire 失败 + schedule 有 retryPolicyId → 调用 `IJobRetryBridge.onFireFailed()` 且返回 retryRecordId
  - fire 失败 + schedule 无 retryPolicyId → 不调用 bridge
  - fire 失败 + fire 自身有 retryPolicyId（优先于 schedule）→ 使用 fire 级别的 policyId
  - fire 超时 → 不调用 retry bridge（超时不走重试）
  - bridge 抛异常 → 不影响 fire 状态更新（吞异常只打日志）
  - 同一 fire 不创建多条 retry record（幂等）
  - **[Round 2]** 幂等性详细验证：fire 已有 retryRecordId 时跳过 bridge 调用
- [ ] `TestJobCompletionProcessorAlarm` — 验证 CompletionProcessor 的 alarm handler 调用路径：
  - fire 失败 → 调用 `IJobAlarmHandler.onFireFailed()`
  - fire 超时 → 调用 `IJobAlarmHandler.onFireTimeout()`
  - alarm handler 抛异常 → 不影响 fire 状态更新
  - fire 成功 → 不调用 alarm
  - **[Round 2]** 验证 alarm event 包含正确的 duration、errorCode、errorMessage 字段
- [ ] 验证 `NoOpJobRetryBridge` 和 `NoOpJobAlarmHandler` 的默认行为

Exit Criteria:

- [ ] `TestJobCompletionProcessorRetryBridge` 存在且包含上述 7 个场景
- [ ] `TestJobCompletionProcessorAlarm` 存在且包含上述 5 个场景
- [ ] 所有测试通过 `./mvnw test -pl nop-job/nop-job-coordinator`
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 超时检测与进度汇报测试

Status: planned
Targets: `nop-job-coordinator/src/test/java/`, `nop-job-worker/src/test/java/`

- Item Types: `Proof`

- [ ] `TestJobTimeoutChecker` — 验证 TimeoutChecker 核心行为：
  - task 超时（startTime + taskTimeoutMs < now）→ 标记 TASK_STATUS_TIMEOUT
  - fire 超时（startTime + fireTimeoutMs < now）→ 标记 FIRE_STATUS_TIMEOUT
  - 超时 task 触发 cancelHandler.cancelRunningTask()
  - 未超时的 task 不受影响
  - cancel 失败（cancelHandler 抛异常）→ 仍标记 TIMEOUT（best-effort cancel）
  - **[Round 2]** 边界条件：taskTimeoutMs=0（永不超时或立即超时的语义）
  - **[Round 2]** 多个 task 同一 fire 下部分超时部分正常 → 只标记超时的 task，fire 等所有 task 完成后再聚合
- [ ] `TestJobTaskProgress` — 验证进度汇报：
  - `IJobTaskStore.updateTaskProgress()` 正确更新 progress 和 progressMessage
  - progress 值范围验证（0-100）
  - 更新已完成 task 的 progress 不抛异常（静默忽略或断言状态）
  - **[Round 2]** progress 从 50 更新到 75：中间进度更新正确递增
  - **[Round 2]** progress=100 但 task 状态仍为 RUNNING：进度和状态独立

Exit Criteria:

- [ ] `TestJobTimeoutChecker` 存在且包含上述 7 个场景
- [ ] `TestJobTaskProgress` 存在且包含上述 5 个场景
- [ ] 所有测试通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 阻塞策略测试

Status: planned
Targets: `nop-job-coordinator/src/test/java/`

- Item Types: `Proof`

- [ ] `TestBlockStrategies` — 验证 4 种阻塞策略在 Planner 中的行为：
  - DISCARD：schedule 有 active fire → 跳过本次调度（不创建新 fire）
  - OVERLAY：schedule 有 active fire → 取消旧 fire，创建新 fire
  - RECOVERY：schedule 上次 fire 失败 → 重置失败 fire 为 WAITING 重新执行
  - SKIP（CONCURRENT）：schedule 有 active fire → 仍创建新 fire（并行执行）
- [ ] 验证 schedule 统计信息（activeFireCount、totalFireCount 等）在每种策略下正确更新

Exit Criteria:

- [ ] `TestBlockStrategies` 存在且覆盖 4 种策略
- [ ] 所有测试通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 端到端生命周期测试

Status: planned
Targets: `nop-job-coordinator/src/test/java/`

- Item Types: `Proof`

- [ ] `TestJobE2EHappyPath` — 完整生命周期：
  1. 创建 Schedule（CRON 触发器，nextFireTime = now）
  2. Planner 扫描 → 创建 Fire + Task
  3. Dispatcher 扫描 → 标记 Fire DISPATCHING
  4. Worker claim Task → 执行成功
  5. CompletionProcessor 扫描 → 聚合完成 → 更新 Schedule 统计
  6. 验证：Fire 最终状态 SUCCESS，Task SUCCESS，Schedule 统计正确
- [ ] `TestJobE2EFailure` — 完整失败路径：
  1. 创建 Schedule + Planner → Fire + Task
  2. Worker 执行失败
  3. CompletionProcessor 聚合 → Fire FAILED
  4. 验证：alarm 被调用，retry bridge 被调用（如配置了 retryPolicyId）
- [ ] `TestJobE2ETimeout` — 完整超时路径：
  1. 创建 Schedule + Planner → Fire + Task
  2. Task 超时（模拟 worker 挂了）
  3. TimeoutChecker 检测 → 标记 TIMEOUT
  4. CompletionProcessor 聚合 → Fire TIMEOUT
  5. 验证：alarm 被调用
- [ ] `TestJobE2ERecovery` — Recovery 策略路径：
  1. Schedule 配置 RECOVERY 阻塞策略
  2. 第一次 fire 失败
  3. 下次调度时 Planner 检测到上次失败 → 重置 fire 为 WAITING
  4. Worker 重新执行成功
  5. 验证：最终 Fire SUCCESS

Exit Criteria:

- [ ] 4 个 E2E 测试存在且全部通过
- [ ] 每个测试验证完整的状态链：Schedule → Fire → Task 的所有中间状态和最终状态
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - 容错场景集成测试

Status: planned
Targets: `nop-job-coordinator/src/test/java/`

- Item Types: `Proof`

- [ ] `TestCoordinatorCrashRecovery` — Coordinator 崩溃恢复：
  1. Planner 锁定 Schedule 后模拟崩溃（lockTimeoutMs 后 lock 过期）
  2. 另一个 Planner 实例扫描到同一 Schedule
  3. 验证：新 Planner 成功创建 Fire，Schedule 不丢失
- [ ] `TestPartitionRebalance` — Partition 动态重分配：
  1. 两个 Coordinator 实例 [A, B]，A 负责分区 [0,50)
  2. B 下线后 A 的 partition 扩展为 [0,100)
  3. 验证：A 能处理原来属于 B 的 partition 中的 Schedule
  4. **[Round 2]** 验证：rebalance 过程中正在处理的 Schedule 不会被丢失（乐观锁兜底）
- [ ] `TestWorkerFailureDetection` — Worker 故障检测：
  1. Worker claim Task 后模拟超时
  2. TimeoutChecker 检测到超时
  3. 验证：Task 标记为 TIMEOUT，cancel handler 被调用
- [ ] `TestConcurrentSchedulerInstances` — 多实例竞争（扩展现有 TestJobConcurrency）：
  - 两个 Planner 同时扫描到同一 Schedule → 只有一个成功创建 Fire
  - 两个 Worker 同时 claim 同一 Task → 只有一个成功执行
- [ ] **[Round 2]** `TestEventualConsistencyAfterCrash` — 最终一致性验证：
  1. 创建 3 个 Schedule（partition 0, 1, 2）
  2. Coordinator A（负责 partition 0-2）处理一轮后模拟崩溃
  3. Coordinator B 接管所有 partition
  4. 验证：所有 3 个 Schedule 最终都被正确处理（不丢失不重复）
- [ ] **[Round 2]** `TestIdempotentTaskExecution` — 任务执行幂等性：
  1. Worker claim Task 后执行成功，但 completion 通知前 Worker 重启
  2. 另一个 Worker 尝试 claim 同一 Task
  3. 验证：Task 不会被重复执行（状态已是 SUCCESS/CLAIMED 不允许再次 claim）
- [ ] **[Round 2]** `TestStabilizationWindowDuringRebalance` — 稳定窗口抖动测试：
  1. 实例列表快速变化 [A,B] → [A] → [A,B] → [A]
  2. 验证：30s 窗口内 partition 不变，窗口结束后才更新

Exit Criteria:

- [ ] 7 个容错测试存在且全部通过
- [ ] 测试覆盖了 Plan 18 中 G1（动态分区）、G3（Coordinator HA）的核心容错场景
- [ ] **[Round 2]** 最终一致性、幂等性、stabilization window 抖动场景已覆盖
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - 测试中发现的问题修复与回归验证

Status: planned
Targets: `nop-job` 全模块

- Item Types: `Fix`, `Proof`

- [ ] 修复 Phase 1-6 测试中发现的任何 bug
- [ ] 确认所有测试（包括原有测试）在 `./mvnw test -pl nop-job` 下通过
- [ ] 确认 `./mvnw compile -pl nop-job -am` 通过

Exit Criteria:

- [ ] `./mvnw test -pl nop-job` 全部通过（含新增测试 + 原有测试）
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] checkstyle 通过
- [ ] 若修复了 bug，`ai-dev/design/nop-job/` 文档已同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Review History

### Round 1 (2026-05-18)

三个独立审查 agent 并行审查，发现以下改进项：

**arch-reviewer 发现：**
- 缺少 trigger 类型覆盖（CRON/fixed-delay/once）的测试建议
- 建议增加 TASK_STATUS_SUSPICIOUS 状态测试
- 确认 Phase 依赖关系正确（Phase 1 优先，Phase 2-5 可并行）

**test-reviewer 发现：**
- 建议增加 progress 边界测试（中间进度更新、progress 与状态独立性）
- 建议增加 timeoutMs=0 的边界条件测试
- 建议增加 alarm event 字段验证
- 确认现有测试基础设施（JunitBaseTestCase + @NopTestConfig + localDb）可支持所有提议的测试

**fault-tolerance-reviewer 发现：**
- **关键缺失**：最终一致性测试（崩溃后所有 Schedule 最终被处理）
- **关键缺失**：幂等性测试（retry bridge 对同一 fire 不重复调用、Task 不被重复执行）
- **关键缺失**：stabilization window 抖动测试（实例列表快速变化时的防抖行为）
- 建议增加 rebalance 过程中正在处理的 Schedule 不丢失的验证

**Round 1 结论**：计划整体结构合理，但容错测试缺少 3 个关键场景（最终一致性、幂等性、抖动）。已将改进项标记为 **[Round 2]** 合并到各 Phase 中。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 8 个 Phase 的 Exit Criteria 全部满足（含 Phase 1A/1B/1C 的缺失实现补齐）
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] `./mvnw test -pl nop-job` 通过（含新增测试 + 原有测试）
- [ ] checkstyle / 代码规范检查通过
- [ ] G1 Worker 心跳：Worker 注册 INamingService + Coordinator SUSPICIOUS 检测已实现并有测试
- [ ] G2 nop-retry 集成：nop-job-retry-adapter 模块存在且对接 IRetryEngine，集成测试通过
- [ ] G4 Dispatch 超时：TimeoutChecker 扫描 DISPATCHING 超时 Fire，有测试覆盖
- [ ] 每个容错机制（动态分区、retry bridge、alarm、进度汇报、超时检测）至少有 1 个 focused test
- [ ] E2E 生命周期测试覆盖 happy path + failure + timeout + recovery
- [ ] 容错场景测试覆盖 Coordinator 崩溃 + partition rebalance + Worker 故障
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### nop-job-retry-adapter 模块实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: adapter 是 nop-retry 集成的可选运行时，Plan 18 的 bridge 接口 + no-op 默认实现已覆盖契约。测试用 mock bridge 验证调用路径即可
- Successor Required: yes
- Successor Path: `ai-dev/plans/` (后续计划)

### 性能/压力测试

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划聚焦功能正确性验证，性能测试是独立关注点
- Successor Required: no

### HashedWheelTimer 精确超时

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 5s 轮询足够（见 Plan 18 Deferred）
- Successor Required: no

## Non-Blocking Follow-ups

- nop-job-retry-adapter 实现（包含 NopRetryJobRetryBridge 实际集成测试）
- 告警渠道扩展（邮件/Webhook/DingTalk）的集成测试
- Dashboard UI 测试
- 与 nop-task 联合的 DAG 工作流 E2E 测试

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: arch-reviewer (sisyphus-junior), test-reviewer (sisyphus-junior), fault-tolerance-reviewer (sisyphus-junior)
- Evidence: Round 1 review via team mode (teamRunId=48d1092b), findings applied as [Round 2] items
- Team: plan-review-team, 3 parallel reviewers, all tasks completed
