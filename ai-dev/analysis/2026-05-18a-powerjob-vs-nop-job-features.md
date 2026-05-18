# Nop-Job vs PowerJob 功能对比分析

> Status: open
> Date: 2026-05-18
> Scope: nop-job, PowerJob (外部项目)
> Conclusion: 待定 — 本分析用于理解 PowerJob 功能全景，识别 nop-job 功能 gap

## Context

PowerJob（原 OhMyScheduler）是一个成熟的分布式任务调度与计算框架，由阿里巴巴开源，在 GitHub 拥有 7k+ star。本文从功能维度对比 nop-job 与 PowerJob，识别 nop-job 需要补齐的能力。

涉及代码库：
- **PowerJob**: `~/sources/PowerJob/` (~4.3.9, Spring Boot + Akka/HTTP + JPA + MongoDB)
- **nop-job**: `nop-job/` (Nop Platform 2.0, NopOrm + DB 轮询)

---

## 1. 总体架构对比

### 1.1 PowerJob 架构

```
┌─────────────────────┐     ┌──────────────────────┐     ┌──────────────┐
│  Worker (业务应用    │◄───►│  Server (调度中心)    │◄───►│  Admin UI    │
│  Spring Boot Starter)│     │  Spring Boot         │     │  (Vue)       │
│  ─────────────      │     │  ──────────────       │     └──────────────┘
│  TaskTracker        │     │  PowerScheduleService │
│  ProcessorTracker   │     │  DispatchService      │
│  WorkerHealthReporter│     │  InstanceManager      │
│  Akka/HTTP Client   │     │  ServerElectionService│
└─────────────────────┘     │  HashedWheelTimer     │
                            │  WorkflowEngine       │
                            │  Akka/HTTP Server     │
                            └──────────┬────────────┘
                                       │
                                 ┌─────▼──────┐
                                 │ MySQL/PG/  │
                                 │ MongoDB    │
                                 └────────────┘
```

- **通信协议**: Akka Remote (默认) / HTTP，可扩展
- **Worker 模式**: 业务应用嵌入 `powerjob-worker-spring-boot-starter`，通过 Akka/HTTP 与 Server 通信
- **Server**: Spring Boot 单体，支持多实例 HA（DB 锁选举）
- **管理端**: 内嵌 Vue 管理后台（单体合一）

### 1.2 Nop-Job 架构

```
┌───────────────────────────────────────────────┐
│  Nop Platform                                 │
│  ┌────────────────────┐  ┌──────────────────┐ │
│  │ nop-job            │  │ nop-task         │ │
│  │  Coordinator       │  │  流程编排引擎     │ │
│  │  Planner Scanner   │  │  36种Step Type   │ │
│  │  Dispatcher Scanner│  └──────────────────┘ │
│  │  Completion Proc   │                       │
│  │  Timeout Checker   │                       │
│  │  Worker Scanner    │                       │
│  │  JobInvoker        │                       │
│  └────────┬───────────┘                       │
│           │ DB 轮询（无 RPC）                   │
│  ┌────────▼──────────────────────────────────┐│
│  │  NopOrm / MySQL/PG/Oracle/SQLite          ││
│  └───────────────────────────────────────────┘│
└───────────────────────────────────────────────┘
```

- **通信模型**: DB 轮询（Scanner 定时扫描 DB 状态变更）
- **无 RPC**: Coordinator 和 Worker 通过共享数据库协调

### 1.3 架构对比

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| 通信模型 | DB 轮询 | Akka/HTTP RPC |
| Worker 发现 | 无（直接读 DB） | 心跳注册 + 内存缓存 |
| 状态同步 | DB 实体状态变更 | RPC 上报 + DB 持久化 |
| 部署复杂度 | 低（无额外通信层） | 中（需 Akka/HTTP） |
| 延迟 | ~5s（轮询间隔） | 近实时 |

---

## 2. 调度能力对比

### 2.1 触发类型

| 触发类型 | nop-job | PowerJob |
|----------|---------|----------|
| CRON 表达式 | ✅ | ✅ |
| 固定频率 (FIXED_RATE) | ✅ | ✅ |
| 固定延时 (FIXED_DELAY) | ✅ | ✅ |
| 单次执行 (API) | ✅ | ✅ |
| 每日时间区间 | ❌ | ✅ `DailyTimeIntervalStrategyHandler` |
| 工作流触发 | ❌（需外部编排） | ✅ `WorkflowTimingStrategyHandler` |

### 2.2 调度引擎

**nop-job**: `JobPlannerScannerImpl` 定时扫描 `nextFireTime <= now` 的 Schedule，乐观锁锁定后创建 Fire。

**PowerJob**: `PowerScheduleService` 按类型分线程调度（CRON、FIXED_RATE、FREQUENT、Workflow），通过 `TimingStrategyService` 计算下次触发时间，使用 `HashedWheelTimer` 精确触发。

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| 调度精度 | 轮询间隔 ~5s | HashedWheelTimer 毫秒级 |
| 分线程调度 | ❌ 单 Scanner | ✅ 按 Job 类型分线程 |
| 调度策略扩展 | `ITrigger` 接口 | `TimingStrategyHandler` 策略模式 |

---

## 3. 执行模型对比

### 3.1 执行类型

| 执行类型 | nop-job | PowerJob |
|----------|---------|----------|
| 单机执行 | ✅ | ✅ `STANDALONE` |
| 广播执行 | ✅ `rpcBroadcast` | ✅ `BROADCAST` |
| MapReduce | ❌ | ✅ `MAP_REDUCE` |
| Map | ❌ | ✅ `MAP` |

**PowerJob 的 MapReduce 是核心差异化能力**：
- 用户通过 `ProcessResult map(List<SubTask> tasks)` 拆分子任务
- 框架自动将子任务分发到多台 Worker 并行执行
- `reduce()` 方法聚合所有子任务结果
- `HeavyTaskTracker` 在 Worker 端管理子任务派发和状态追踪

**nop-job 的替代方案**：通过 `nop-task` 流程编排引擎实现类似能力（Parallel Step + DAG），但非原生 MapReduce 语义。

### 3.2 执行器类型

| 执行器类型 | nop-job | PowerJob |
|-----------|---------|----------|
| Java (内置/容器) | ✅ `IJobInvoker` SPI | ✅ `ProcessorType.EMBEDDED_JAVA / OUTER_JAVA` |
| Shell | ❌ | ✅ `ProcessorType.SHELL` |
| Python | ❌ | ❌（通过 HTTP 扩展） |
| HTTP | ✅ RPC Invoker | 通过自定义 Processor |
| 容器热加载 | ❌ | ✅ 动态容器 (Jar 热部署) |

### 3.3 Worker 模型

**nop-job**: Worker 也是 DB Scanner（`JobWorkerScannerImpl`），从 DB claim Task 后通过 `IJobInvoker` 执行。

**PowerJob**: Worker 端有复杂的分层模型：
- `TaskTracker` — 管理 JobInstance 的整个生命周期（派发子任务、聚合结果）
- `ProcessorTracker` — 管理单个 Worker 节点的处理器实例
- `HeavyTaskTracker` vs `LightTaskTracker` — 大任务（MR）vs 小任务（单机）
- Worker 本地使用 SQLite 持久化子任务状态

---

## 4. 工作流能力对比

### 4.1 PowerJob 工作流

PowerJob 有**原生 DAG 工作流引擎**：
- `WorkflowDAG` — DAG 图数据结构（支持多根节点）
- `PEWorkflowDAG` — 持久化的 DAG 模型（Node + Edge）
- 节点类型：Job 节点、判断节点（Decision）、嵌套工作流（NestedWorkflow）
- 边支持条件表达式
- `WorkflowInstanceManager` 管理工作流实例生命周期
- 失败处理：支持节点级重试、跳过、暂停

### 4.2 nop-job 工作流

nop-job 自身无工作流能力，依赖 `nop-task` 模块：
- 36 种 Step Type（Sequential、Parallel、Fork/Join、Condition、Loop、Graph/DAG 等）
- 更通用的流程编排（非仅任务调度）
- 支持 Suspend/Sleep/Retry/Timeout

| 维度 | nop-job + nop-task | PowerJob |
|------|-------------------|----------|
| DAG 原生支持 | 通过 nop-task Graph Step | ✅ 原生 DAG |
| 条件分支 | 通过 nop-task Condition | ✅ Decision Node |
| 嵌套工作流 | 通过 nop-task SubFlow | ✅ NestedWorkflow Node |
| 工作流上下文 | nop-task 变量系统 | `appendedWfContext` |
| 失败策略 | Step 级 Retry/Timeout | 节点级重试/跳过/暂停 |

---

## 5. 分布式能力对比

### 5.1 Server HA

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| 多 Server 集群 | 静态 partition（已设计动态方案：`IDiscoveryClient` + `PartitionAssignHelper`，不需要选主） | DB 锁选举（per-app） |
| 选举机制 | 无 | `ServerElectionService` |
| 故障接管 | 需手动调整 partition | 自动重新选举 |
| 分配粒度 | partition 级 | appId 级（每个 app 由一个 Server 负责调度） |

**PowerJob Server 选举**：
- `AppInfoDO.currentServer` 记录每个 app 的当前调度 Server
- Worker 连接时触发选举：如果当前 Server 不可用（ping 超时 1s），通过 DB 分布式锁重新选举
- 选举使用 `LockService.tryLock()` — 基于 DB 的分布式锁

### 5.2 Worker 管理

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| Worker 注册 | 无 | ✅ `WorkerHealthReporter` 心跳 |
| Worker 发现 | 读 DB | 内存缓存 + DB 降级 |
| Worker 过滤 | 无 | ✅ `WorkerFilter` 链（CPU/内存/磁盘/指定机器/超载） |
| Worker 选择策略 | 无（直接 claim） | ✅ `TaskTrackerSelectorService`（多策略） |
| Worker 超载保护 | 无 | ✅ `isOverload` 标记 |

**PowerJob Worker 过滤器链**：
- `DisconnectedWorkerFilter` — 排除断连 Worker
- `SystemMetricsWorkerFilter` — 按 CPU/内存/磁盘指标过滤
- `DesignatedWorkerFilter` — 按指定机器过滤

### 5.3 路由策略

| 策略 | nop-job | PowerJob |
|------|---------|----------|
| 随机 | ❌ | ✅ |
| 轮询 | ❌ | ✅ |
| 哈希 | ❌ | ✅ |
| 最不经常使用 | ❌ | ✅ |
| 最近最久未使用 | ❌ | ✅ |
| 容量最大 | ❌ | ✅ |
| 指定机器 | ❌ | ✅ `designatedWorkers` |

---

## 6. 监控告警对比

| 维度 | nop-job | PowerJob |
|------|---------|----------|
| 运行时状态 | Fire/Task 状态机 | Instance 状态机 + TaskTracker 实时上报 |
| 进度汇报 | ❌ | ✅ `TaskTrackerReportInstanceStatusReq`（含 totalTaskNum/succeedTaskNum/failedTaskNum） |
| 系统指标 | ❌ | ✅ `SystemMetrics`（CPU/内存/磁盘/负载） |
| 告警通知 | ❌ | ✅ `AlarmCenter`（钉钉/邮件/Webhook） |
| 运行日志 | DB 存储 | ✅ 在线日志流 + 本地文件 |
| 实例详情 | DB 查询 | ✅ `InstanceDetail`（含子任务统计） |

---

## 7. 持久化模型对比

### 7.1 实体对比

| nop-job | PowerJob | 说明 |
|---------|----------|------|
| `NopJobSchedule` | `JobInfoDO` | 调度配置 |
| `NopJobFire` | `InstanceInfoDO` | 一次执行实例 |
| `NopJobTask` | Worker 本地 `task_info` (SQLite) | 子任务 |
| ❌ | `WorkflowInfoDO` | 工作流定义 |
| ❌ | `WorkflowInstanceInfoDO` | 工作流实例 |
| ❌ | `AppInfoDO` | 应用信息 |
| ❌ | `ServerNodeDO` | 服务器节点 |

### 7.2 状态机对比

**nop-job Fire 状态**：
```
WAITING(0) → DISPATCHING(10) → RUNNING(20) → SUCCESS(30)
                                              → FAILED(40)
                                              → TIMEOUT(50)
                                              → CANCELED(60)
```

**PowerJob Instance 状态**：
```
WAITING_DISPATCH(1) → WAITING_WORKER_RECEIVE(2) → RUNNING(3) → SUCCEED(5)
                                                                     → FAILED(4)
                                                                     → CANCELED(9)
                                                                     → STOPPED(10)
```

PowerJob 多了 `WAITING_WORKER_RECEIVE` 中间态（RPC 确认），nop-job 的 `DISPATCHING` 类似但含义不同（DB 状态标记，非 RPC 确认）。

---

## 8. 功能 Gap 总结

### P0 — 核心功能缺失

| # | Gap | PowerJob 实现 | nop-job 建议 |
|---|-----|--------------|-------------|
| G1 | MapReduce 执行 | `HeavyTaskTracker` + 子任务派发 | 通过 nop-task Parallel/DAG 或独立实现 |
| G2 | Worker 心跳注册 | `WorkerHealthReporter` + 内存缓存 | 复用 `IDiscoveryClient` 注册机制（注册即心跳） |
| G3 | Server HA 选举 | `ServerElectionService` + DB 锁 | 不需要选主，`IDiscoveryClient` + `PartitionAssignHelper` 纯函数计算 partition。详见 `ai-dev/design/nop-job/cluster-ha-design.md` |
| G4 | 任务重试 | `instanceRetryNum` + `taskRetryNum` | 已设计：`IJobRetryBridge` 桥接 `nop-retry`，`retryPolicyId` 指向 `nop_retry_policy.sid`。详见 `ai-dev/design/nop-job/retry-integration-design.md` |

### P1 — 重要功能差距

| # | Gap | PowerJob 实现 | nop-job 建议 |
|---|-----|--------------|-------------|
| G5 | Worker 过滤/选择 | `WorkerFilter` 链 + 路由策略 | 添加 Worker 注册 + 指标上报 + 选择器 |
| G6 | 进度汇报 | `TaskTrackerReportInstanceStatusReq` | 添加 task_progress 字段 |
| G7 | 告警通知 | `AlarmCenter`（钉钉/邮件/Webhook） | 实现 IJobAlarmHandler |
| G8 | DAG 工作流 | `WorkflowDAG` + 多种节点类型 | 依赖 nop-task 补齐 |
| G9 | 容器热部署 | 动态 Jar 加载 | 低优先级 |

### P2 — 体验提升

| # | Gap | 说明 |
|---|-----|------|
| G10 | 每日时间区间触发 | `DailyTimeIntervalStrategyHandler` |
| G11 | Worker 超载保护 | `isOverload` 标记 + 指标过滤 |
| G12 | 分线程调度 | 按 Job 类型分线程提高调度吞吐 |
| G13 | 在线日志流 | Worker 实时推送执行日志 |

---

## 9. PowerJob 的不足（nop-job 可借鉴避免）

1. **Worker 本地 SQLite 依赖**：PowerJob 的 MR 模式要求 Worker 本地 SQLite，增加运维复杂度。nop-job 的 DB-as-truth 模型天然避免了此问题。
2. **Akka 依赖**：Akka License 变更（2022 年后 BSL），PowerJob 已支持 HTTP 协议替代，但架构仍依赖 Akka 概念。
3. **单点调度**：PowerJob 的 appId 级调度意味着每个 app 同一时刻只有一个 Server 负责调度，调度吞吐受限于单机。nop-job 的 partition 分片天然支持水平扩展。
4. **无限流/阻塞策略配置**：PowerJob 的阻塞策略只有丢弃，不支持 OVERLAY（cancel 旧的）和 RECOVERY。
