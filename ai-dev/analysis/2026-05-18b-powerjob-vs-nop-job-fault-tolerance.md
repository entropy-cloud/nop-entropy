# Nop-Job vs PowerJob 容错机制深度对比分析

> Status: open
> Date: 2026-05-18
> Scope: nop-job, PowerJob (外部项目)
> Conclusion: 识别 nop-job 容错 gap，指导后续补齐工作

## Context

前序分析（`2026-05-18a-powerjob-vs-nop-job-features.md`）已对比了功能维度。本文聚焦**容错**——当 Worker 失效、Server 崩溃、网络分区、任务执行超时/失败等异常场景下，两个系统分别如何保证最终一致性和任务不丢不重。

前序分析（`2026-05-18-fault-tolerance-deep-dive.md`）已对比了 nop-job 与 snail-job 的容错。本文在类似框架下对比 nop-job 与 PowerJob。

涉及代码库：
- **nop-job**: `nop-job/nop-job-coordinator/`, `nop-job/nop-job-worker/`, `nop-job/nop-job-dao/`
- **PowerJob**: `~/sources/PowerJob/` (~4.3.9, Spring Boot + Akka/HTTP + JPA)

---

## 1. 架构模型差异（决定容错策略的根本）

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| 通信模型 | **DB 轮询** | **RPC 推送** (Akka/HTTP) + DB 持久化 |
| Worker 发现 | 无注册，Worker 直接读 DB | 心跳注册到 Server，内存缓存 + DB 降级 |
| 调度模型 | 无中心，多 Coordinator 通过 partition 分片 | appId 级选举，每个 app 由一个 Server 负责调度 |
| 任务执行 | Worker 是 DB Scanner | Worker 端 TaskTracker 管理完整生命周期 |
| 状态流转 | Fire/Task 两级状态机 | Instance/Task 两级 + Server 端三层检查 |

**核心差异**：nop-job 将 DB 作为唯一的协调通道（简单的 DB-as-queue）；PowerJob 使用 RPC 实时通信 + DB 作为持久化后备，并在 Worker 端引入 TaskTracker 作为子协调器。

---

## 2. Worker 失效处理

### 2.1 Nop-Job

**检测机制**：无主动心跳。通过 `JobTimeoutCheckerImpl` 定时扫描超时 Fire/Task 间接检测。

```
JobTimeoutCheckerImpl.runScan():
  1. 查询 fireStatus=RUNNING 且超时的 Fire
  2. 查询 taskStatus=RUNNING 且超时的 Task
  3. 对超时实体:
     a. 调用 DefaultJobCancelHandler → RPC 尝试 cancel（best-effort）
     b. 设置状态 TIMEOUT
     c. 触发 CompletionProcessor 聚合
```

**问题**：
1. **无心跳**：Coordinator 不知道 Worker 是否存活
2. **检测延迟 = fireTimeoutMs**：如果设为 1h，Worker 挂了要等 1h
3. **cancel 是 best-effort**：`cancelAsync()` 失败只打 warn 日志
4. **无自动重试（已设计对接 nop-retry）**：超时后直接标记失败，待 `IJobRetryBridge` 实现后通过 `nop-retry` 自动重试

### 2.2 PowerJob

**检测机制**：三层检测，远比 nop-job 完善。

#### 第一层：Worker 心跳

```
WorkerHealthReporter.run0():
  定期向 Server 发送 WorkerHeartbeat（含 SystemMetrics、isOverload）
  Server 端 WorkerClusterManagerService 更新内存中的 WorkerInfo
  超时未心跳的 Worker 标记为 timeout
```

`WorkerInfo.timeout()` 判断：`heartbeatTime + timeout > now`，默认超时约 90s。

#### 第二层：TaskTracker 状态上报

```
TaskTracker → Server:
  TaskTrackerReportInstanceStatusReq {
    instanceId, instanceStatus,
    totalTaskNum, succeedTaskNum, failedTaskNum,
    reportTime, sourceAddress
  }
```

Server 端 `InstanceManager.updateStatus()`：
- 通过 `reportTime` 丢弃过期上报（幂等）
- 通过 `sourceAddress` 丢弃脑裂上报（非目标 TaskTracker）
- 实时更新 Instance 状态

#### 第三层：Server 端 `InstanceStatusCheckService`

这是 PowerJob 的**兜底容错核心**，运行多个检查线程：

| 检查线程 | 检查目标 | 超时阈值 | 处理策略 |
|----------|---------|---------|---------|
| `CheckWaitingDispatchInstance` | `WAITING_DISPATCH` 状态的 Instance | 30s | 重新派发（`maxInstanceNum` 限流） |
| `CheckWaitingWorkerReceiveInstance` | `WAITING_WORKER_RECEIVE` 状态的 Instance | 60s | 重新派发到其他 Worker |
| `CheckRunningInstance` | `RUNNING` 状态但长时间无上报的 Instance | 60s | 判断是否重试（`instanceRetryNum`） |
| `CheckWorkflowInstance` | 长时间 WAITING 的 WorkflowInstance | 60s | 重试工作流 |

**关键代码**（`InstanceStatusCheckService.java:220-256`）：

```java
// RUNNING 超时检查
if (instance.getRunningTimes() < jobInfoOpt.get().getInstanceRetryNum()) {
    // 重试次数未耗尽，重新派发
    dispatchService.redispatchAsync(instance.getInstanceId(), InstanceStatus.RUNNING.v);
} else {
    // 重试耗尽，标记失败
    updateFailedInstance(e, SystemInstanceResult.REPORT_TIMEOUT);
}
```

#### 第四层：Worker 端 ProcessorTracker 心跳

Worker 内部也有心跳机制：
- `ProcessorTrackerStatusHolder` 管理所有 ProcessorTracker 状态
- `ProcessorTracker` 定期向 `TaskTracker` 上报心跳
- `TaskTracker` 检测到 ProcessorTracker 失联后，将其标记为 disconnected
- 后续子任务不会派发到 disconnected 的 ProcessorTracker
- 提供 `getAllDisconnectedProcessorTrackers()` 供故障诊断

### 2.3 对比总结

| 场景 | nop-job | PowerJob |
|------|---------|----------|
| Worker 检测方式 | 超时扫描（被动） | 心跳 + 状态上报 + 多层兜底扫描 |
| 检测延迟 | fireTimeoutMs（可能很长） | ~60s（三层检查） |
| 失效后处理 | 标记 TIMEOUT，不自动重试 | 自动重新派发到其他 Worker |
| 重试次数控制 | ❌ 无 | ✅ `instanceRetryNum` |
| 脑裂保护 | ❌ | ✅ sourceAddress 校验 |
| Worker 内部故障 | 不感知 | ✅ ProcessorTracker 心跳 |

---

## 3. Server / Coordinator 失效处理

### 3.1 Nop-Job（Coordinator 失效）

**架构**：多 Coordinator 实例通过静态 `assignedPartitions` 分片。nop-cluster 已提供 `IDiscoveryClient`（服务发现，含注册心跳）+ `PartitionAssignHelper`（纯函数分区计算），但 nop-job 当前通过 `@InjectValue` 注入静态配置，未集成这些能力。

**恢复机制**：
1. **DB 即状态**：所有状态持久化在 DB，重启后继续
2. **乐观锁防重复**：`tryLockSchedulesForPlan()` 使用版本号检查
3. **锁定超时自动恢复**：`lockTimeoutMs`（默认 60s）后锁自然过期

**问题**：
1. **partition 静态分配，未利用服务发现动态能力**：Coordinator 挂了其 partition 不会被自动接管。需要集成 `IDiscoveryClient` + `PartitionAssignHelper`，纯函数计算 partition，不需要选主。详见 `ai-dev/design/nop-job/cluster-ha-design.md`。
2. **锁超时无自动释放**：崩溃后最多 60s 延迟

### 3.2 PowerJob（Server 失效）

**架构**：多 Server 实例通过 DB 锁实现 appId 级选举。

**选举机制**（`ServerElectionService`）：

```
1. Worker 请求调度 → Server 查询 AppInfoDO.currentServer
2. 如果 currentServer 可用（ping 超时 1s）→ 返回
3. 如果不可用：
   a. 通过 LockService.tryLock("server_elect_" + appId, 30000) 获取 DB 锁
   b. 再次确认 currentServer 仍不可用（double-check）
   c. 将本机地址写入 currentServer（篡位）
   d. 释放锁
4. Worker 使用新的 Server 地址
```

**故障恢复**：
1. **App 级粒度**：每个 app 独立选举，一个 Server 挂了只影响它负责的 app
2. **自动接管**：其他 Server 通过选举自动接管
3. **检查线程自动适配**：`InstanceStatusCheckService` 只检查 `currentServer == 本机` 的 app

**关键代码**（`InstanceStatusCheckService.java:72`）：
```java
List<Long> allAppIds = appInfoRepository.listAppIdByCurrentServer(
    transportService.defaultProtocol().getAddress());
```

### 3.3 对比总结

| 场景 | nop-job | PowerJob |
|------|---------|----------|
| 状态恢复 | DB 持久化 | DB 持久化 |
| 多实例协调 | 静态 partition（已设计：`IDiscoveryClient` + `PartitionAssignHelper`，不需要选主） | appId 级 DB 锁选举 |
| 节点失效后接管 | ❌ 需手动调整 | ✅ 自动选举 |
| 分配粒度 | partition（粗粒度） | appId（细粒度） |
| 崩溃后最大延迟 | lockTimeoutMs（60s） | 选举延迟（~10s） |

---

## 4. 进度汇报

### 4.1 Nop-Job

**现状：不支持进度汇报。** Worker 执行任务过程中无任何中间状态上报。

### 4.2 PowerJob

**完善的进度汇报机制**：

1. **Instance 级进度**：`TaskTrackerReportInstanceStatusReq` 包含：
   - `totalTaskNum` / `succeedTaskNum` / `failedTaskNum` — 子任务级进度
   - `instanceStatus` — Instance 当前状态
   - `result` — 执行结果

2. **系统指标上报**：`WorkerHeartbeat.systemMetrics` 包含：
   - CPU 核心数 / 使用率
   - 内存使用率
   - 磁盘使用率
   - 计算综合得分 `calculateScore()`

3. **ProcessorTracker 心跳**：Worker 内部 ProcessorTracker 定期向 TaskTracker 上报状态

4. **Admin UI 实时展示**：前端可实时查看运行中任务进度、Worker 负载等

### 4.3 对比

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| 任务进度 | ❌ | ✅ 子任务级进度统计 |
| 系统指标 | ❌ | ✅ CPU/内存/磁盘 |
| Worker 负载 | ❌ | ✅ isOverload + 任务数统计 |
| 实时展示 | 仅 DB 查询 | Admin UI 实时 |

---

## 5. Task 重试机制

### 5.1 Nop-Job

**现状：无自动重试（已设计对接 nop-retry）。** `retryPolicyId` 字段已预留，已设计通过 `IJobRetryBridge` 桥接 `nop-retry`，Coordinator 在 fire 失败时提交重试。详见 `ai-dev/design/nop-job/retry-integration-design.md`。唯一类似重试的机制是 RECOVERY 阻塞策略（整批重试）。

### 5.2 PowerJob

**双层重试**：

#### Instance 级重试

```java
// JobInfoDO
private Integer instanceRetryNum;  // Instance 级最大重试次数

// InstanceStatusCheckService.handleRunningInstance()
if (instance.getRunningTimes() < jobInfoOpt.get().getInstanceRetryNum()) {
    dispatchService.redispatchAsync(instance.getInstanceId(), ...);
}
```

- 检测到 Instance 超时/失败后，自动重新派发
- `DispatchService.redispatchAsync()` 将状态重置为 `WAITING_DISPATCH`
- 重新选择 Worker 执行
- `runningTimes` 记录已执行次数（含重试）

#### Task 级重试

```java
// JobInfoDO
private Integer taskRetryNum;  // Task 级最大重试次数
```

在 Worker 端 `TaskTracker` 内部实现：
- 子任务执行失败后，在 Worker 内部直接重试
- 无需重新从 Server 派发，减少网络开销
- 适合 MapReduce 中的子任务级容错

### 5.3 对比

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| Instance 级重试 | ❌ | ✅ `instanceRetryNum` |
| Task 级重试 | ❌ | ✅ `taskRetryNum` |
| 重试时切换节点 | ❌ | ✅ 自动选择新 Worker |
| 重试计数 | ❌ | ✅ `runningTimes` |
| 重试间隔策略 | ❌ | 固定（立即重试） |
| 整批重试（替代） | RECOVERY 策略 | 无 |

---

## 6. 超时处理

### 6.1 Nop-Job

`JobTimeoutCheckerImpl` 定时扫描（5s 间隔）：
- Fire 级超时 + Task 级超时（独立配置）
- 超时后标记 TIMEOUT + 尝试 cancel
- `@SingleSession` 独立 Session

### 6.2 PowerJob

**Server 端**：
1. `InstanceTimeWheelService` — 基于 `HashedWheelTimer` 的精确超时
   - 调度时注册超时任务
   - 超时触发后通过 `InstanceManager` 处理
2. `InstanceStatusCheckService` — 三层兜底检查（见第 2 节）
   - `DISPATCH_TIMEOUT_MS = 30000`
   - `RECEIVE_TIMEOUT_MS = 60000`
   - `RUNNING_TIMEOUT_MS = 60000`

**Worker 端**：
- `TaskTracker` 内部有独立的超时检查
- 子任务超时后在本 Worker 内标记失败并重试（`taskRetryNum`）

### 6.3 对比

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| Server 超时检测 | 轮询（~5s） | HashedWheelTimer + 多层检查 |
| Worker 端超时 | ❌（无感知） | ✅ TaskTracker 内部超时 |
| 超时后处理 | 标记 TIMEOUT | 标记失败 + 自动重试（如配置） |
| 精度 | ~5s | 毫秒级（TimerWheel） |
| 多层兜底 | ❌ | ✅ Dispatch/Receive/Running 三层 |

---

## 7. MapReduce 失败处理

### 7.1 Nop-Job

不原生支持 MapReduce。通过 nop-task 的 Parallel Step 可实现类似效果，但失败处理依赖 nop-task 的 Step 级重试。

### 7.2 PowerJob

**完善的 MR 容错**：

1. **子任务级重试**：`taskRetryNum` 配置，在 Worker 内部自动重试失败的子任务
2. **ProcessorTracker 失联检测**：`ProcessorTrackerStatusHolder` 追踪每个 Worker 节点状态
   - 失联的节点不再派发新子任务
   - 已派发但超时的子任务被重新分配到其他节点
3. **TaskTracker 单点管理**：所有子任务状态由 TaskTracker 统一管理
   - 使用本地 SQLite 持久化子任务状态（防丢失）
   - TaskTracker 定期向 Server 上报聚合状态
4. **Root Task 失败 → Instance 失败**：如果 root task（map 阶段）失败，整个 Instance 失败并触发 instance 级重试

---

## 8. Workflow 失败处理

### 8.1 Nop-Job

无原生 Workflow。依赖 nop-task。

### 8.2 PowerJob

1. **节点级失败处理**：`WorkflowInstanceManager` 检测工作流节点失败
   - 节点失败后，下游节点不会触发
   - 工作流整体标记为 FAILED
2. **工作流重试**：`InstanceStatusCheckService.checkWorkflowInstance()` 检查长时间 WAITING 的工作流实例并重试
3. **条件节点**：Decision 节点根据条件表达式选择分支
4. **嵌套工作流**：NestedWorkflow 节点可嵌套其他工作流

---

## 9. 幂等与防重

### 9.1 Nop-Job

- **乐观锁**：`tryUpdateManyWithVersionCheck` 防止并发修改
- **DB 状态机**：状态流转通过 DB 约束，已完成的 Fire/Task 不会被重复处理
- **Scan 幂等**：Scanner 每次扫描都带状态过滤，已完成的不被扫到

### 9.2 PowerJob

- **reportTime 校验**：丢弃过期的状态上报（`req.getReportTime() <= instanceInfo.getLastReportTime()`）
- **sourceAddress 校验**：丢弃非目标 TaskTracker 的上报（防脑裂）
- **UseCacheLock**：`@UseCacheLock(type = "processJobInstance", key = "#instanceId")` 分段锁
- **DB 分布式锁**：`LockService.tryLock()` 用于 Server 选举等关键操作
- **Redispatch CAS**：`instanceInfoRepository.updateStatus...` CAS 更新状态

---

## 10. Gap 总结与优先级

### 与 PowerJob 对比，nop-job 的容错 Gap

#### P0 — 必须补齐

| # | Gap | PowerJob 实现 | 建议方案 |
|---|-----|--------------|----------|
| G1 | **Worker 心跳 + 快速故障检测** | `WorkerHealthReporter` + `WorkerInfo.timeout()` | 复用 `IDiscoveryClient` 注册机制（注册即心跳） |
| G2 | **Instance 级自动重试** | `instanceRetryNum` + `redispatchAsync()` | 已设计：`IJobRetryBridge` 桥接 `nop-retry`，`retryPolicyId` 指向 `nop_retry_policy.sid`。详见 `ai-dev/design/nop-job/retry-integration-design.md` |
| G3 | **多层超时检查** | Dispatch/Receive/Running 三层检查 | 在现有 TimeoutChecker 基础上增加 Dispatch 超时检查 |
| G4 | **Coordinator 动态 Rebalance** | appId 级选举 | 已设计：集成 `IDiscoveryClient` + `PartitionAssignHelper`（纯函数，每节点独立计算，不需要选主），stabilization window(30s) 防抖，乐观锁兜底。详见 `ai-dev/design/nop-job/cluster-ha-design.md` |

#### P1 — 重要

| # | Gap | PowerJob 实现 | 建议方案 |
|---|-----|--------------|----------|
| G5 | 进度汇报 | `TaskTrackerReportInstanceStatusReq` | 添加 task_progress 字段 + Worker 定期更新 |
| G6 | 告警通知 | `AlarmCenter`（钉钉/邮件/Webhook） | 实现 IJobAlarmHandler |
| G7 | 脑裂保护 | sourceAddress 校验 | 添加 workerInstanceId 校验 |
| G8 | Worker 过滤 | `WorkerFilter` 链 | Worker 注册时上报指标 + 选择器 |

#### P2 — 可选

| # | Gap | PowerJob 实现 | 建议方案 |
|---|-----|--------------|----------|
| G9 | Task 级重试 | `taskRetryNum`（Worker 端） | 低优先级，依赖子任务模型 |
| G10 | Worker 本地持久化 | SQLite 本地 task_info | nop-job 的 DB 模型天然不需要 |
| G11 | HashedWheelTimer 精确超时 | `InstanceTimeWheelService` | 5s 轮询对大多数场景足够 |

---

## 11. 与 Snail-Job 容错对比的关系

| 容错维度 | nop-job gap | snail-job 实现 | PowerJob 实现 | nop-job 建议统一方案 |
|---------|------------|---------------|--------------|-------------------|
| Worker 检测 | 无心跳 | 10s gRPC 心跳 | 定期心跳 + 多层扫描 | **复用 `IDiscoveryClient` 注册机制（注册即心跳）**（统一） |
| 自动重试 | 无（已设计 `IJobRetryBridge` 桥接 nop-retry） | Job 级 + Retry 模块 | instanceRetryNum + taskRetryNum | **`IJobRetryBridge` + nop-retry**（统一） |
| Server HA | 静态 partition | 动态 bucket rebalance | appId 级 DB 锁选举 | **`IDiscoveryClient` + `PartitionAssignHelper`（纯函数，不需要选主）**（统一） |
| 进度汇报 | 无 | 有限 | 完善（子任务级） | **task_progress 字段**（统一） |
| 超时检测 | 单层轮询 | HashedWheelTimer | TimerWheel + 多层检查 | **多层 Scanner**（统一） |
| 告警 | 无 | 告警模块 | AlarmCenter | **IJobAlarmHandler**（统一） |

**结论**：三家系统的容错 gap 在 nop-job 端高度重叠（G1-G4 在三份分析中反复出现）。补齐 G1-G4 即可同时满足与 snail-job 和 PowerJob 的容错对齐。

---

## 12. PowerJob 容错的不足

PowerJob 并非完美，以下是其容错方面的弱点：

1. **单点调度瓶颈**：每个 app 由一个 Server 负责调度，该 Server 成为热点。nop-job 的 partition 分片天然支持水平扩展。
2. **Worker 端 SQLite 依赖**：MapReduce 模式要求 Worker 本地 SQLite，增加运维复杂度。nop-job 的纯 DB 模型更简单。
3. **无阻塞策略**：PowerJob 只有丢弃策略（`DISCARD`），不支持 OVERLAY（cancel 旧的）和 RECOVERY（重试失败的）。nop-job 的四种阻塞策略更完善。
4. **重试间隔固定**：立即重试，无退避策略。snail-job 支持指数退避更优。
5. **选举开销**：每次 Worker 重连都可能触发 DB 锁选举，高频场景下可能有性能问题。

nop-job 可在补齐容错 gap 的同时，保持现有优势（简单模型、水平扩展、多种阻塞策略）。
