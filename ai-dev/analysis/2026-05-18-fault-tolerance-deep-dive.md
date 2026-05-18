# Nop-Job vs Snail-Job 容错机制深度对比分析

> Status: open
> Date: 2026-05-18
> Scope: nop-job, snail-job (外部项目)
> Conclusion: 识别 nop-job 容错 gap，指导后续补齐工作

## Context

前序分析（`2026-05-17-snail-job-vs-nop-job-comparison.md`）已对比了功能维度。本文聚焦**容错**——当 Worker 失效、Coordinator 崩溃、网络分区、任务执行超时/失败等异常场景下，两个系统分别如何保证最终一致性和任务不丢不重。

涉及代码库：
- **nop-job**: `nop-job/nop-job-coordinator/`, `nop-job/nop-job-worker/`, `nop-job/nop-job-dao/`
- **snail-job**: `~/sources/snail-job/` (~1.1.0, Spring Boot + Netty/gRPC + MyBatis-Plus + Pekko Actor)

---

## 1. 架构模型差异（决定容错策略的根本）

| 维度 | nop-job | snail-job |
|------|---------|-----------|
| 通信模型 | **DB 轮询**（Scanner 定时扫描 DB 状态） | **RPC 推送**（Netty/gRPC Server→Client） |
| 状态存储 | Schedule / Fire / Task 三级 ORM 实体 | Job / JobTaskBatch / JobTask + WorkflowTaskBatch |
| 协调模型 | 无中心协调器，多实例通过 partition 分片 + DB 乐观锁 | Server 集群通过 DB 心跳 + bucket 分片 + Pekko Actor |
| Worker 发现 | 无注册机制，Worker 直接读 DB claim task | Client 通过 gRPC 心跳注册到 Server，Server 缓存实例列表 |
| 调度触发 | Planner Scanner 定时扫描 `nextFireTime` | DispatchService 定时扫描 bucket，Actor 驱动执行 |

**核心差异**：nop-job 是 **DB-as-message-queue** 模型，容错依赖 DB 持久化 + 幂等扫描；snail-job 是 **RPC + DB** 混合模型，容错依赖心跳检测 + RPC 重试。

---

## 2. Worker 失效处理

### 2.1 Nop-Job

**检测机制**：无主动心跳。通过 `JobTimeoutCheckerImpl` 定时扫描超时的 Fire/Task 间接检测。

```
JobTimeoutCheckerImpl.runScan():
  1. 查询 fireStatus=RUNNING 且 (now - startTime > fireTimeoutMs) 的 Fire
  2. 查询 taskStatus=RUNNING 且 (now - startTime > taskTimeoutMs) 的 Task
  3. 对超时 Fire/Task:
     a. 调用 IJobCancelHandler.cancelRunningTask() → 尝试 RPC 通知 Worker 停止
     b. 设置状态为 FIRE_STATUS_TIMEOUT / TASK_STATUS_TIMEOUT
     c. 触发 CompletionProcessor 聚合处理
```

**关键代码**：
- `JobTimeoutCheckerImpl.java:31` — 核心超时检测循环
- `DefaultJobCancelHandler.java:23` — cancel 通过 `IJobInvoker.cancelAsync()` 发送 RPC 取消请求
- `JobFireStoreImpl.java:128` — `cancelFire()` 事务性设置 CANCELED 状态

**问题**：
1. **无进度汇报**：Coordinator 不知道 Worker 是否还活着，只能等超时。如果 `fireTimeoutMs` 设置过长（如 1h），Worker 挂了要等 1h 才能发现。
2. **无主动心跳**：Worker 不会定期向 Coordinator 报告存活状态。
3. **cancel 是 best-effort**：`cancelAsync()` 失败只打 warn 日志，不重试。如果 Worker 已死，cancel 通知自然丢失，但 DB 状态会被设为 TIMEOUT，后续不会被重新调度（除非使用 RECOVERY 策略）。
4. **Task claim 后 Worker 挂了**：Task 处于 CLAIMED/RUNNING 状态，只能等超时检测器发现。`lockTimeoutMs` 默认 60s，但 Task 超时时间由 Schedule 配置决定。

### 2.2 Snail-Job

**检测机制**：双层心跳。

1. **Client → Server 心跳**：`ClientRegister.java` 每 10s 发送 `BEAT.PING`（含 labels），Server 端 `InstanceManager` 更新 `lastUpdateAt`。
2. **Server 端超时检查**：`InstanceManager.start()` 定时任务，检查 `now - lastUpdateAt > timeout`（timeout = `DELAY_TIME + DELAY_TIME/3`），超时则从 `INSTANCE_MAP` 移除并关闭 gRPC channel。
3. **gRPC Channel 状态检查**：额外检查 `ConnectivityState` 为 `TRANSIENT_FAILURE` / `SHUTDOWN` 时标记为不存活。

**关键代码**：
- `ClientRegister.java:53` — 客户端定时心跳
- `InstanceManager.java:284-321` — 服务端超时检查 + Channel 状态检查
- `InstanceManager.java:76-108` — 注册/更新实例（含 gRPC Channel 管理）

**优势**：
1. **快速感知**：10s 心跳 + 超时检测，Worker 故障 30-40s 内可发现。
2. **路由感知**：`ClusterClientCallbackHandler` 在任务失败回调时通过 `InstanceManager.getALiveInstanceByRouteKey()` 选择新节点重试。
3. **降级策略**：缓存为空时从 DB 查询 `ServerNode` 表回填。

### 2.3 对比总结

| 场景 | nop-job | snail-job |
|------|---------|-----------|
| Worker 检测方式 | 超时扫描（被动） | 心跳 + Channel 状态（主动） |
| 检测延迟 | 取决于 fireTimeoutMs 配置 | ~30-40s |
| 失效后处理 | 标记 TIMEOUT，不自动重试 | 选择新节点重新分发 |
| 失效后任务状态 | 留在 DB 等超时检测 | 通过回调机制处理 |

---

## 3. Coordinator / Server 失效处理

### 3.1 Nop-Job（Coordinator 失效）

**架构**：Coordinator 是无状态的 Scanner 循环，多个 Coordinator 实例通过 `assignedPartitions` 分片。

**恢复机制**：
1. **DB 即状态**：所有调度状态（nextFireTime, activeFireCount, fireStatus, taskStatus）持久化在 DB。Coordinator 重启后从 DB 读取状态继续。
2. **乐观锁防重复**：`tryLockSchedulesForPlan()` 使用 ORM 版本号检查（`tryUpdateManyWithVersionCheck`），确保同一 Schedule 只被一个 Planner 实例处理。
3. **Fire 锁定**：`tryLockFiresForDispatch()` 设置 `fireStatus=DISPATCHING` + `dispatcherInstanceId`，防止重复分发。
4. **Task 锁定**：`tryLockTasksForExecute()` 设置 `taskStatus=CLAIMED` + `workerInstanceId`。

**问题**：
1. **partition 静态分配，未利用服务发现动态能力**：nop-cluster 已提供 `IDiscoveryClient`（服务发现，含注册心跳）+ `PartitionAssignHelper`（纯函数分区计算），但 nop-job 当前通过 `@InjectValue` 注入静态配置的 `assignedPartitions`，未与服务发现联动。如果某个 Coordinator 挂了，它负责的 partition 上的 Schedule/Fire/Task 不会被处理，直到手动调整配置。**已设计解决方案**：详见 `ai-dev/design/nop-job/cluster-ha-design.md`。
2. **锁超时无自动释放**：Planner 锁定 Schedule 后如果崩溃，`nextFireTime` 被设为 `now + lockTimeoutMs`（默认 60s），等锁超时后其他 Planner 才能处理。这意味着** Coordinator 崩溃会导致最多 60s 的调度延迟**。

**关键代码**：
- `JobScheduleStoreImpl.java:69-85` — `tryLockSchedulesForPlan()` 乐观锁
- `JobFireStoreImpl.java:71-89` — `tryLockFiresForDispatch()` Fire 锁定
- `JobTaskStoreImpl.java:67` — `tryLockTasksForExecute()` Task 锁定

### 3.2 Snail-Job（Server 失效）

**架构**：Server 通过 `ServerNodeBalance` 管理 cluster 内节点，使用 bucket 分片。

**恢复机制**：
1. **Server 注册**：`ServerRegister` 将当前 Server 节点注册到 `sj_server_node` 表（含 `expireAt`）。
2. **Rebalance**：`ServerNodeBalance.run()` 定时扫描 `sj_server_node` 表的存活节点，通过 `AllocateMessageQueueAveragely` 平均分配 bucket。节点变化时触发 rebalance。
3. **Bucket 感知调度**：`DispatchService` 只消费分配给当前节点的 bucket，rebalance 后自动接管新 bucket。
4. **Pekko Actor 驱动**：每个调度任务通过 Actor 模型管理生命周期（含超时 TimerWheel），Actor 重启可恢复。

**关键代码**：
- `ServerNodeBalance.java:49-76` — rebalance 逻辑
- `DispatchService.java:46-74` — bucket 驱动的调度循环
- `DistributeInstance.java` — 全局 bucket 分配状态

**优势**：
1. **动态 rebalance**：Server 挂了后其他节点自动接管其 bucket。
2. **延迟可控**：rebalance 延迟约 `INITIAL_DELAY`（30s）。

### 3.3 对比总结

| 场景 | nop-job | snail-job |
|------|---------|-----------|
| 状态恢复 | DB 持久化，重启即恢复 | DB 持久化 + 内存缓存 |
| 多实例协调 | 静态 partition（已设计动态方案：`IDiscoveryClient` + `PartitionAssignHelper`，不需要选主） | 动态 bucket rebalance |
| 节点失效后接管 | 需要手动重新分配 partition | 自动 rebalance |
| 崩溃后最大延迟 | lockTimeoutMs（默认 60s） | rebalance 初始延迟（30s） |

---

## 4. 进度汇报

### 4.1 Nop-Job

**现状：不支持进度汇报。**

搜索整个 `nop-job` 模块，未发现任何 progress 相关接口或实现。Worker 执行任务时：
1. 从 DB claim task → 设置 `taskStatus=RUNNING`
2. 执行完成后更新 `taskStatus=SUCCESS/FAILED`
3. 中间过程无任何进度信息

**影响**：
- Coordinator 无法知道任务执行到哪一步
- 只能通过超时检测器判断任务是否还在执行
- 长时间运行的任务（如数据处理）无法提供进度百分比

### 4.2 Snail-Job

**现状：有限支持。**

1. `DispatchService` 中有 `progress` 关键词出现，但主要用于调度进度（非任务执行进度）。
2. Client 通过 gRPC 双向通信，理论上可以上报进度，但当前实现中进度上报不完整。
3. Workflow 节点有状态上报（通过 `WorkflowTaskBatch`），但不等同于细粒度执行进度。

**结论**：两者在细粒度进度汇报上都存在 gap。

---

## 5. Task 重试机制

### 5.1 Nop-Job

**现状：无自动重试（已设计对接 nop-retry）。**

- Schedule 实体有 `retryPolicyId` 字段（ORM 中定义），已设计通过 `IJobRetryBridge` 桥接 `nop-retry`。详见 `ai-dev/design/nop-job/retry-integration-design.md`。
- Task 执行失败后直接标记为 `TASK_STATUS_FAILED`，当前不会自动重试（待实现桥接后触发）。
- 唯一的重试级机制是 **RECOVERY 阻塞策略**：下次调度时如果上次 Fire 失败，会重置失败 Fire 为 WAITING 状态重新执行。但这不是 per-task 重试，而是整批重试。

**关键代码**：
- `_NopJobSchedule.java:retryPolicyId` — 字段存在，已设计桥接方案
- `_NopJobFire.java:retryPolicyId, retryRecordId` — 字段存在，已设计桥接方案
- `JobPlannerScannerImpl.shouldRecovery()` — Recovery 策略实现

### 5.2 Snail-Job

**现状：完善的重试体系。**

1. **Job 级重试**：`Job.maxRetryTimes` + `Job.retryInterval` 配置。任务执行失败后通过 `AbstractClientCallbackHandler` 判断是否需要重试：
   - `isNeedRetry()`: 检查重试次数 < maxRetryTimes
   - `updateRetryCount()`: CAS 更新重试次数
   - `RetryJobTimerTask`: 延迟 retryInterval 后重新分发

2. **Retry 模块（独立于 Job）**：snail-job 有独立的 retry-task 模块，支持：
   - `@Retryable` 注解声明式重试
   - `RetryStrategy` 策略接口
   - `AbstractRetryStrategies` 基于guava-retrying 的重试执行
   - `RetryDeadLetter` 死信队列（重试耗尽后进入）
   - 多种重试间隔策略（固定、随机、指数退避）

3. **Cluster 模式自动切换节点重试**：`ClusterClientCallbackHandler.chooseNewClient()` 在重试时通过路由策略选择新的 Client 节点执行。

**关键代码**：
- `AbstractClientCallbackHandler.java:46-51` — 重试判断逻辑
- `Job.java:118-123` — maxRetryTimes / retryInterval 字段
- `AbstractRetryStrategies.java` — Retry 模块核心策略
- `RetryDeadLetterTaskAccess.java` — 死信队列

### 5.3 对比总结

| 维度 | nop-job | snail-job |
|------|---------|-----------|
| 自动重试 | ❌ 无（已设计 `IJobRetryBridge` 桥接 nop-retry） | ✅ Job 级重试 + 独立 Retry 模块 |
| 重试策略配置 | 字段预留但未实现 | maxRetryTimes + retryInterval + 指数退避 |
| 重试时切换节点 | ❌ | ✅ Cluster 模式自动切换 |
| 死信队列 | ❌ | ✅ RetryDeadLetter |
| 声明式重试 | ❌ | ✅ @Retryable 注解 |
| 替代方案 | RECOVERY 策略（整批重试） | 无需替代 |

---

## 6. 超时处理

### 6.1 Nop-Job

**实现**：`JobTimeoutCheckerImpl` 独立的超时扫描器。

```
扫描间隔: scanIntervalMs (默认 5000ms)
批大小: batchSize (默认 100)
分区: assignedPartitions
```

流程：
1. 从 DB 查询 `fireStatus=RUNNING` 且 `startTime + fireTimeout < now` 的 Fire
2. 从 DB 查询 `taskStatus=RUNNING` 且 `startTime + taskTimeout < now` 的 Task
3. 对每个超时 Fire：
   - 调用 `cancelHandler.cancelRunningTask()` 尝试通知 Worker
   - 设置 `fireStatus=FIRE_STATUS_TIMEOUT`，写入 errorCode/errorMessage
   - 更新 Schedule 统计信息
4. 对每个超时 Task 同理

**特点**：
- Fire 级超时和 Task 级超时是**独立的**（Fire 有自己的 timeoutMs）
- 超时检测精度为 `scanIntervalMs`（最差 5s 延迟）
- `@SingleSession` 注解确保每次扫描使用独立 ORM Session

### 6.2 Snail-Job

**实现**：基于 `JobTimerWheel`（HashedWheelTimer）的精确超时。

```
JobExecutorActor.doExecute():
  1. 创建 JobTaskBatch 记录
  2. 根据 executorTimeout 注册 JobTimeoutCheckTask 到 TimerWheel
  3. 分发任务到 Client
```

流程：
1. 任务分发时注册超时检查任务到 `JobTimerWheel`（基于 Netty HashedWheelTimer）
2. 超时触发时 `JobTimeoutCheckTask.run()`：
   - 检查 `JobTaskBatch` 是否已完成（幂等）
   - 调用 `JobTaskStopHandler.stop()` 停止任务
   - 发送告警通知
3. 如果任务在超时前完成，取消 TimerWheel 中的超时任务

**特点**：
- **精确超时**：基于 HashedWheelTimer，不受扫描间隔影响
- **主动式**：分发时即注册超时，无需轮询
- **告警集成**：超时后发送 `JobTaskFailAlarmEvent`

### 6.3 对比总结

| 维度 | nop-job | snail-job |
|------|---------|-----------|
| 超时检测方式 | 定时轮询 DB | HashedWheelTimer 精确定时 |
| 检测精度 | ~5s（scanIntervalMs） | 毫秒级 |
| 超时后处理 | 标记 TIMEOUT + 尝试 cancel | 标记失败 + stop task + 告警 |
| 超时取消 | 无取消机制（轮询式不需要） | TimerWheel.cancel() 主动取消 |

---

## 7. Fire / TaskBatch 完成处理

### 7.1 Nop-Job

**`JobCompletionProcessorImpl`**：聚合 Task 完成状态，决定 Fire 最终状态。

```
扫描间隔: scanIntervalMs (默认 5000ms)
```

流程：
1. 查询所有 Task 都已完成（SUCCESS/FAILED/TIMEOUT/CANCELED）的 Fire
2. 聚合判断：
   - 所有 Task SUCCESS → Fire SUCCESS
   - 任一 Task FAILED → Fire FAILED
   - 任一 Task TIMEOUT → Fire TIMEOUT
   - 任一 Task CANCELED → Fire CANCELED
3. 更新 Fire 状态 + durationMs + endTime
4. 更新 Schedule 聚合统计：
   - `activeFireCount -= 1`
   - `lastFireStatus = fireStatus`
   - `lastDurationMs = fire.durationMs`
   - `totalFireCount += 1`, `successFireCount += 1` 或 `failFireCount += 1`
   - `lastEndTime = now`
5. 如果使用 FIXED_DELAY 触发器，从 `lastEndTime` 开始计算下一次触发时间

**关键代码**：
- `JobCompletionProcessorImpl.java:32` — 完成处理器
- `JobScheduleStoreImpl.java:69-111` — 事务性更新 Schedule 统计

### 7.2 Snail-Job

**`AbstractClientCallbackHandler` + `JobTaskBatchHandler`**：通过 RPC 回调 + Actor 模型处理。

流程：
1. Client 执行完成后通过 gRPC 回调 Server
2. `AbstractClientCallbackHandler.callback()` 处理回调：
   - 判断是否需要重试（重试次数 < maxRetryTimes）
   - 如需重试：注册 `RetryJobTimerTask` 到 TimerWheel
   - 如不重试：更新 `JobTaskBatch` 最终状态
3. `JobTaskBatchHandler` 检查当前 Job 的所有 JobTaskBatch 是否完成
4. 所有 batch 完成后：
   - 更新 Job 的 `nextTriggerAt`
   - 如果是 Workflow 节点，触发 `WorkflowBatchHandler` 处理下一个节点

**特点**：
- **事件驱动**：通过 RPC 回调实时感知完成，无需轮询
- **Actor 隔离**：每个任务类型有独立 Actor 处理
- **Workflow 集成**：Job 完成后可自动触发 Workflow 下游节点

### 7.3 对比总结

| 维度 | nop-job | snail-job |
|------|---------|-----------|
| 完成感知方式 | 定时轮询 DB | RPC 回调 |
| 延迟 | ~5s（scanIntervalMs） | 近实时 |
| 聚合统计 | Schedule 级聚合（fire/task 成功/失败/超时计数） | Job 级统计 |
| Workflow 集成 | ❌（需外部编排） | ✅ 原生支持 |

---

## 8. Store 事务处理

### 8.1 Nop-Job

**大量使用 `@Transactional(propagation = REQUIRES_NEW)`**：

- `tryLockSchedulesForPlan()` — REQUIRES_NEW
- `insertFireAndAdvanceSchedule()` — REQUIRES_NEW
- `overlayFireAndAdvanceSchedule()` — REQUIRES_NEW
- `recoveryFireAndAdvanceSchedule()` — REQUIRES_NEW
- `advanceScheduleAfterSkip()` — REQUIRES_NEW
- `cancelFire()` — REQUIRES_NEW
- `updateTask()` — REQUIRES_NEW

所有关键状态变更都在独立事务中完成，确保：
1. Fire 创建和 Schedule 推进是原子的
2. Cancel 操作（fire + tasks + schedule 更新）是原子的
3. 不会因为外层事务回滚导致状态不一致

**乐观锁**：`tryUpdateManyWithVersionCheck` 确保并发安全。

### 8.2 Snail-Job

使用 Spring `@Transactional` + MyBatis-Plus：
- `JobExecutorActor.doExecute()` 使用 `TransactionTemplate` 编程式事务
- `AbstractClientCallbackHandler.callback()` 使用 `@Transactional` 声明式事务
- 依赖 MyBatis-Plus 的乐观锁插件

---

## 9. Gap 总结与优先级建议

### P0 — 必须补齐（影响生产可用性）

| # | Gap | 描述 | 建议方案 |
|---|-----|------|----------|
| G1 | **无 Worker 心跳/快速故障检测** | Worker 挂了只能等超时 | Worker 注册到 `IDiscoveryClient`（注册即心跳，无需独立心跳表），Coordinator 通过服务发现感知 Worker 存活状态 |
| G2 | **无自动重试** | Task 失败后直接标记 FAILED，不重试 | 已设计：通过 `IJobRetryBridge` 桥接 `nop-retry`，`retryPolicyId` 指向 `nop_retry_policy.sid`，Coordin员在 fire 失败时提交重试。详见 `ai-dev/design/nop-job/retry-integration-design.md` |
| G3 | **Coordinator 无动态 Rebalance** | partition 静态分配，节点失效后无法自动接管 | 已设计：集成 `IDiscoveryClient` + `PartitionAssignHelper`（纯函数，每节点独立计算，不需要选主），stabilization window(30s) 防抖，乐观锁兜底。详见 `ai-dev/design/nop-job/cluster-ha-design.md` |

### P1 — 重要（影响运维体验）

| # | Gap | 描述 | 建议方案 |
|---|-----|------|----------|
| G4 | **无死信队列** | 重试耗尽后任务永久失败 | 添加 dead_letter 表 + 管理界面 |
| G5 | **无告警通知** | 超时/失败后无主动通知 | 实现 IJobAlarmHandler 接口，支持 Webhook/钉钉/邮件 |
| G6 | **无进度汇报** | 长任务无法感知进度 | 添加 task_progress 字段 + Worker 定期更新 |

### P2 — 可选（锦上添花）

| # | Gap | 描述 | 建议方案 |
|---|-----|------|----------|
| G7 | 超时检测精度 | 5s 轮询 vs 毫秒级 TimerWheel | 对于大多数场景 5s 足够，保持现状 |
| G8 | 完成感知延迟 | 5s 轮询 vs RPC 回调实时 | DB 轮询模型决定，接受此 tradeoff |
| G9 | RPC cancel 是 best-effort | cancel 失败不重试 | 可添加重试队列 |

---

## 10. 架构哲学对比

| 维度 | nop-job | snail-job |
|------|---------|-----------|
| **一致性模型** | 强一致（DB 事务 + 乐观锁） | 最终一致（RPC + DB 回填） |
| **容错策略** | 靠 DB 持久化兜底，等待超时恢复 | 主动检测 + 快速切换 |
| **复杂度** | 低（无 RPC，无 Actor，纯 DB 轮询） | 高（gRPC + Pekko Actor + TimerWheel + 心跳） |
| **适用场景** | 中小规模，任务量适中，对实时性要求不高 | 大规模分布式，高吞吐，要求秒级故障感知 |
| **运维成本** | 低（无额外组件） | 中（依赖 gRPC + Actor 系统） |

**核心 tradeoff**：nop-job 选择 **简单可靠**（DB 是唯一 truth，无状态 Coordinator），代价是故障恢复慢、无自动重试（已设计 `IJobRetryBridge` 桥接 nop-retry）。snail-job 选择 **快速响应**（RPC + 心跳 + Actor），代价是架构复杂。

对于 Nop 平台定位（企业级低代码），nop-job 的简单模型是合理的起点，G1-G3 补齐后即可达到生产可用。

---

## Open Questions

- [ ] G1 Worker 心跳：复用 `IDiscoveryClient` 注册机制（Worker 注册为 ServiceInstance），还是需要额外机制？
- [x] G2 retryPolicyId 已设计对接 nop-retry：通过 `IJobRetryBridge` 桥接，可选模块 `nop-job-retry-adapter`。详见 `ai-dev/design/nop-job/retry-integration-design.md`。
- [ ] G3 集成方案：通过 `IDiscoveryClient` 获取所有 Coordinator 实例，`PartitionAssignHelper.getMyRange()` 纯函数计算 partition，不需要选主。stableWindowMs 防抖。详见 `ai-dev/design/nop-job/cluster-ha-design.md`。
- [ ] nop-task 模块是否已提供部分容错能力（如 Step 级重试）？需进一步调研。
