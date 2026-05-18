# 18 nop-job 容错改进计划

> Plan Status: in progress
> Last Reviewed: 2026-05-18
> Source: `ai-dev/analysis/2026-05-18-fault-tolerance-deep-dive.md`, `ai-dev/analysis/2026-05-18a-powerjob-vs-nop-job-features.md`, `ai-dev/analysis/2026-05-18b-powerjob-vs-nop-job-fault-tolerance.md`, `ai-dev/design/nop-job/cluster-ha-design.md`, `ai-dev/design/nop-job/retry-integration-design.md`
> Related: `14-nop-job-quality-fixes.md`, `17-nop-job-block-strategy-metrics.md`, `16-nop-job-core-redesign.md`

## Purpose

补齐 nop-job 在容错和集群 HA 方面的主要 gap，使其在生产环境下具备：节点故障后自动 partition 接管、fire 失败后自动重试、Worker 故障检测、进度汇报、告警通知。

## Current Baseline

- nop-job 已完成 DB-as-message-queue 三层模型重写（Schedule → Fire → Task）
- 所有 Scanner 通过 `@InjectValue` 注入静态 `assignedPartitions`，未与 nop-cluster 集成
- `NopJobSchedule` 和 `NopJobFire` 均有 `retryPolicyId` 字段但未使用
- `NopJobFire` 有 `retryRecordId` 字段但未使用
- nop-retry 模块已成熟：`IRetryEngine` + `IRetryTask` + `RetryScannerImpl` + `NopRetryPolicy` 实体
- `RetryScannerImpl` 已实现 `INamingService` + `PartitionAssignHelper` 动态分区（参考实现，但无 stabilization window）
- nop-cluster 提供 `IDiscoveryClient`、`INamingService`（extends IDiscoveryClient）、`PartitionAssignHelper`
- 无 Worker 心跳/故障检测（Worker 挂了只能等 Task 超时）
- 无自动重试（fire 失败后直接标记 FAILED）
- 无进度汇报（Worker 不上报执行进度）
- 无告警（失败/超时无通知机制）
- 三份对比分析文档已完成：snail-job 容错（G1-G6）、PowerJob 功能（G1-G5）、PowerJob 容错（G1-G11）
- 两份设计文档已完成：`cluster-ha-design.md`（集群 HA）、`retry-integration-design.md`（重试对接）

## Goals

1. **集群 HA 动态分区**：Coordinator 节点故障后，其 partition 在 ~60s 内被其他节点自动接管
2. **自动重试**：fire 失败且配置了 `retryPolicyId` 时，通过 `IJobRetryBridge` 桥接 nop-retry 自动重试
3. **Worker 故障检测**：Worker 注册到服务发现，Coordinator 能感知 Worker 存活状态
4. **进度汇报**：Worker 上报 task 执行进度到 DB
5. **告警通知**：fire 失败/超时时触发可配置的告警

## Non-Goals

- 不实现 RPC 调度模型（保持 DB-as-message-queue）
- 不引入独立心跳表（复用 `INamingService` 注册机制，注册即心跳）
- 不实现 Leader Election 做分区分配（`PartitionAssignHelper` 是纯函数）
- 不在本计划中实现 Worker 端代码（Worker 端的 executor 实现是独立关注点）
- 不实现 sub-task 级别的进度跟踪（PowerJob 那样的 map-reduce 模式）
- 不修改 nop-retry 模块本身
- 不实现多层超时检测（当前 5s 轮询对大多数场景足够，见 Deferred）
- 不实现脑裂防护（nop-job 的 DB 乐观锁模型天然防脑裂：两个节点不会同时 claim 同一条记录）

## Scope

### In Scope

- nop-job-coordinator Scanner 集成 `INamingService` + `PartitionAssignHelper` 动态分区
- 新增 `IJobRetryBridge` 接口及 no-op 默认实现
- 新增 `nop-job-retry-adapter` 可选模块
- Worker 故障检测机制设计
- Task 进度汇报字段和更新机制
- 告警接口 `IJobAlarmHandler` 及基础实现

### Out Of Scope

- Worker executor 实现改造
- nop-retry 模块修改
- 前端 UI 改造
- 性能优化（如精确超时 HashedWheelTimer）
- 分布式锁替代乐观锁

### Gap 覆盖映射

本计划覆盖了三份分析文档中识别的所有核心 gap。以下是完整映射：

**snail-job 容错对比（G1-G6）的 gap：**

| Gap | 描述 | 计划覆盖 |
|-----|------|---------|
| G1 | 无 Worker 心跳/快速故障检测 | Phase 3 |
| G2 | 无自动重试 | Phase 2 |
| G3 | Coordinator 无动态 Rebalance | Phase 1 |
| G4 | 死信队列 | Phase 2（nop-retry 内建死信 `NopRetryDeadLetter`） |
| G5 | 进度汇报 | Phase 4 |
| G6 | 告警通知 | Phase 5 |

**PowerJob 功能对比（G1-G5）的 gap：**

| Gap | 描述 | 计划覆盖 |
|-----|------|---------|
| G1 | 任务生命周期管理 | 已有（三层模型） |
| G2 | Worker 心跳注册 | Phase 3 |
| G3 | Server HA | Phase 1 |
| G4 | 任务重试 | Phase 2 |
| G5 | 任务工作流 | Non-Goal（独立 feature） |

**PowerJob 容错对比（G1-G11）的 gap：**

| Gap | 描述 | 计划覆盖 | 备注 |
|-----|------|---------|------|
| G1 | Worker 心跳 + 快速故障检测 | Phase 3 | |
| G2 | Instance 级自动重试 | Phase 2 | |
| G3 | 多层超时检测 | Deferred | 当前 5s 轮询足够，见 Deferred |
| G4 | Coordinator 动态 Rebalance | Phase 1 | |
| G5 | 进度汇报 | Phase 4 | |
| G6 | 告警 | Phase 5 | |
| G7 | 脑裂防护 | Non-Goal | DB 乐观锁天然防脑裂 |
| G8 | 任务重试策略 | Phase 2 | |
| G9 | 精确超时（HashedWheelTimer） | Deferred | 性能优化 |
| G10 | Worker 本地持久化 | Non-Goal | DB 模型不需要 |
| G11 | HashedWheelTimer | Deferred | 同 G9 |

## Execution Plan

> **Phase 依赖**：Phase 1（动态分区）建议优先实施，因为 Phase 2-5 的 Scanner 在集群模式下都需要正确的分区计算。Phase 2-5 之间无强依赖，可并行实施。Phase 3（Worker 检测）需要先完成设计文档。

### Phase 1 - 集群 HA 动态分区

Status: completed
Targets: `nop-job-coordinator`, `nop-cluster`

- Item Types: `Fix`, `Decision`

- [x] 为 `JobPlannerScannerImpl`、`JobDispatcherScannerImpl`、`JobCompletionProcessorImpl`、`JobTimeoutCheckerImpl` 添加 `INamingService` 依赖（`INamingService extends IDiscoveryClient`，与 `RetryScannerImpl` 一致），实现 `resolvePartitions()` 方法（参照 `RetryScannerImpl.resolvePartitions()` 的实现模式）
- [x] 添加 `stableWindowMs` 防抖配置（`@InjectValue("@cfg:nop.job.cluster.stable-window-ms|30000")`），在 `resolvePartitions()` 中检测实例列表变化后等待 stabilization window。注意：这是对 `RetryScannerImpl` 参考实现的增强，`RetryScannerImpl` 没有此机制
- [x] 添加配置项 `nop.job.cluster.enable-cluster`（默认 false）和 `nop.job.cluster.service-name`（默认空，取 AppConfig.appName()）
- [x] 验证：当 enable-cluster=false 时行为与现有静态分区完全一致

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 所有 4 个 Scanner 在 `enable-cluster=true` 时通过 `INamingService.getInstances()` + `PartitionAssignHelper.getMyRange()` 动态计算分区
- [x] `enable-cluster=false` 时行为与现有静态 `assignedPartitions` 完全一致
- [x] stabilization window 防抖逻辑可配置且有单元测试覆盖
- [x] `ai-dev/design/nop-job/cluster-ha-design.md` 与实现一致
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 重试桥接（IJobRetryBridge）

Status: completed
Targets: `nop-job-api`, `nop-job-coordinator`

- Item Types: `Fix`, `Decision`, `Proof`

- [x] 在 `nop-job-api` 中定义 `IJobRetryBridge` 接口（`onFireFailed(JobFireFailedEvent)` → `String retryRecordId`）
- [x] 在 `nop-job-coordinator` 中实现 `NoOpJobRetryBridge`（返回 null）
- [x] 在 `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()` 中，fire 失败时调用 `IJobRetryBridge.onFireFailed()`，回填 `retryRecordId`
- [x] retryPolicyId 优先级：`fire.retryPolicyId` > `schedule.retryPolicyId` > 不触发重试
- [x] 死信由 nop-retry 内建处理：重试超限后 `RetryEngineImpl` 自动转入 `NopRetryDeadLetter`，nop-job 无需额外实现

Exit Criteria:

- [x] `IJobRetryBridge` 接口存在于 `nop-job-api`
- [x] `NoOpJobRetryBridge` 为默认实现，不影响现有行为
- [x] `JobCompletionProcessorImpl` 在 fire 失败时调用桥接并回填 `retryRecordId`
- [x] 幂等性：同一 fire 不会创建多条 retry record（通过 `idempotentId` 保证）
- [x] `ai-dev/design/nop-job/retry-integration-design.md` 与实现一致
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Worker 故障检测

Status: completed
Targets: `nop-job-core`

- Item Types: `Decision`

- [x] Task 状态新增 `TASK_STATUS_SUSPICIOUS(15)` 常量

Exit Criteria:

- [x] `TASK_STATUS_SUSPICIOUS` 常量存在于 `_NopJobCoreConstants`
- [x] Worker 注册和检测逻辑的设计文档已写入 `ai-dev/design/nop-job/`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 进度汇报

Status: completed
Targets: `nop-job-dao`, `nop-job-coordinator`

- Item Types: `Decision`, `Proof`

- [x] `NopJobTask` 实体添加 `progress` 字段（INTEGER，0-100 百分比，可空）
- [x] `NopJobTask` 实体添加 `progressMessage` 字段（VARCHAR，可空）
- [x] 添加 Task 进度更新 API（`IJobTaskStore.updateTaskProgress(taskId, progress, message)`）

Exit Criteria:

- [x] `NopJobTask` ORM 模型（`model/*.orm.xml`）中有 `progress` 和 `progressMessage` 字段定义
- [x] `IJobTaskStore` 接口有 `updateTaskProgress` 方法
- [x] Worker 可通过 API 更新 task 执行进度（接口存在且可调用）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 告警通知

Status: completed
Targets: `nop-job-api`, `nop-job-coordinator`

- Item Types: `Decision`

- [x] 在 `nop-job-api` 中定义 `IJobAlarmHandler` 接口（`onFireFailed(event)`, `onFireTimeout(event)`）
- [x] 在 `nop-job-coordinator` 中实现 `NoOpJobAlarmHandler`
- [x] 在 `JobCompletionProcessorImpl` 中 fire 失败/超时时调用 `IJobAlarmHandler`
- [x] 提供 `LoggingJobAlarmHandler` 示例实现（SLF4J warn 级别）

Exit Criteria:

- [x] `IJobAlarmHandler` 接口存在于 `nop-job-api`
- [x] `NoOpJobAlarmHandler` 为默认实现
- [x] `LoggingJobAlarmHandler` 可选实现存在于 `nop-job-coordinator`
- [x] `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()` 在 `FIRE_STATUS_FAILED` 和 `FIRE_STATUS_TIMEOUT` 时调用告警
- [x] `ai-dev/logs/` 对应日期条目已更新

## Risks And Rollback

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 服务发现不稳定导致 partition 频繁变化 | Scanner 短暂重叠/空白，可能漏处理或重复处理 | stabilization window(30s) 防抖 + 乐观锁兜底（重叠不丢不重，只浪费查询） |
| nop-retry 不可用时 fire 失败无法重试 | 失败的 fire 直接标记 FAILED，不重试 | `IJobRetryBridge` 默认 no-op，adapter 可选引入；retry 不可用时降级为无重试 |
| Worker 注册延迟 | Task 可能被误标为 SUSPICIOUS | SUSPICIOUS 不直接标记失败，等待超时确认 |
| 新增 `nop-job-retry-adapter` 模块增加构建复杂度 | 依赖管理 | 模块可选，不引入则完全无影响 |

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
>
> **本计划涉及代码变更，需要构建验证。**

- [ ] 所有 5 个 Phase 的 Exit Criteria 全部满足
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] `./mvnw test -pl nop-job` 通过（含新增测试）
- [ ] checkstyle / 代码规范检查通过
- [ ] 受影响的 `ai-dev/design/nop-job/` 文档已与实现一致
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### 精确超时（HashedWheelTimer）— G3/G9/G11

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 5s 轮询超时检测对大多数场景足够，PowerJob 的 HashedWheelTimer 是性能优化而非功能缺失。DB 模型下扫描间隔本身就决定了超时精度上限
- Successor Required: no

### Sub-task 级进度（MapReduce 模式）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: nop-job 当前是单 task per fire 模型，sub-task 是新 feature
- Successor Required: no

### Worker executor 改造

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Worker 端代码是独立关注点，不影响 Coordinator 侧容错能力
- Successor Required: no

### 脑裂防护 — G7

- Classification: `watch-only residual`
- Why Not Blocking Closure: nop-job 使用 DB 乐观锁（`tryUpdateManyWithVersionCheck`），两个 Coordinator 不会同时 claim 同一条记录。与 PowerJob 的 appId 级 DB 锁选举场景不同
- Successor Required: no

### 多层超时检测 — G3

- Classification: `optimization candidate`
- Why Not Blocking Closure: PowerJob 的多层检查（Worker 心跳→TaskTracker 上报→Server 检查→ProcessorTracker 心跳）是为 RPC 模型设计的。nop-job 的 DB 模型通过 `JobTimeoutCheckerImpl` 单层扫描即可覆盖
- Successor Required: no

## Non-Blocking Follow-ups

- 精确超时替代轮询（HashedWheelTimer 或 ScheduledExecutor 精确调度）
- 告警渠道扩展（邮件/Webhook/DingTalk）
- 重试回调（nop-retry 重试完成后回调 nop-job 更新 fire 状态，已在 `retry-integration-design.md` §3.5 设计但不在本计划实施范围内）
- Metrics 集成（Prometheus/OpenTelemetry）
- Dashboard UI

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>
