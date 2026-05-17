# Snail-Job vs Nop-Job 功能对比分析

> Status: open
> Date: 2026-05-17
> Scope: nop-job, nop-task, snail-job (外部项目)
> Conclusion: 待定 — 本分析用于指导用 nop-job + nop-task 替代 snail-job 的方案设计

## Context

当前项目使用 [snail-job](https://gitee.com/aizuda/snail-job) 作为分布式任务调度和重试平台。目标是**完全替代 snail-job**，改为使用 nop-job 作为调度框架，同时作业内部编排使用 nop-task 模块。

本分析从**功能维度、架构设计、持久化模型、执行模型、分布式能力、监控/告警、多语言支持、运维管理**等角度进行深度对比，识别需要补齐的 gap，为后续方案设计提供依据。

涉及代码库：
- **snail-job**: `~/sources/snail-job`（版本 ~1.1.0, based on Spring Boot + MyBatis/MyBatis-Plus）
- **nop-job**: `nop-job/` (nop-entropy 的一部分，基于 Nop Platform 2.0)
- **nop-task**: `nop-task/` (nop-entropy 的流程编排引擎)

---

## 1. 总体架构对比

### 1.1 Snail-Job 架构

```
┌────────────────┐     ┌────────────────┐     ┌────────────────┐
│  Client(Java/  │◄───►│  Server(Spring │◄───►│  Admin UI     │
│  Python/Go)    │     │  Boot)         │     │  (Vue-based)  │
│  ─────────     │     │  ─────────     │     └────────────────┘
│  @JobExecutor  │     │  Dispatcher    │
│  IJobExecutor  │     │  Scheduler     │
│  TriggerHandler│     │  JobService    │
│  Netty Client  │     │  OpenAPI       │
└────────────────┘     └───────┬────────┘
                              │
                        ┌──────▼────────┐
                        │  MySQL        │
                        └───────────────┘
```

- **通信协议**: Netty（C/S 双向通信）
- **客户端模式**: 业务应用嵌入 SDK，通过 Netty 注册到 Server
- **Server**: Spring Boot 单体 + MySQL + MyBatis-Plus
- **管理端**: 独立的 Vue 管理后台

### 1.2 Nop-Job + Nop-Task 架构

```
┌───────────────────────────────────────────────┐
│  Nop Platform                                 │
│  ┌────────────────────┐  ┌──────────────────┐ │
│  │ nop-job            │  │ nop-task         │ │
│  │  ─────────         │  │  ─────────       │ │
│  │  Coordinator (调度) │  │  ITask (流程编排) │ │
│  │  Planner/Dispatcher│  │  36 种 Step Type │ │
│  │  CompletionProc    │  │  Sequential/     │ │
│  │  TimeoutChecker    │  │  Parallel/       │ │
│  │                    │  │  Fork/Join/      │ │
│  │  Worker (执行)     │  │  Condition/      │ │
│  │  JobInvoker        │  │  Loop/Graph(DAG) │ │
│  │  InvokerResolver   │  │  Retry/Timeout/  │ │
│  └────────┬───────────┘  │  Suspend/Sleep   │ │
│           │              └────────┬─────────┘ │
│           │ 通过 nop-task 驱动     │           │
│           └───────────────────────┘           │
│  ┌─────────────────────────────────────────┐  │
│  │  ORM (NopOrm) / GraphQL / IoC / XLang  │  │
│  └─────────────────────────────────────────┘  │
└───────────────────────────────────────────────┘
```

- **架构风格**: 独立 Coordinator + Worker 节点（或合并部署）
- **通信机制**: **`nop-rpc`** — Worker 注册为 RPC 服务提供者，Coordinator 通过 RPC 服务发现 + 直接 RPC 调用分发任务，无需自定义通信协议
- **Worker 注册发现**: 通过 `nop-rpc` 服务注册与发现机制，不需要额外设计
- **与平台的集成深度**: 使用 Nop ORM、IoC、RPC、GraphQL、XLang DSL
- **nop-task 集成**: nop-job 内部可使用 nop-task 作为 DAG/工作流执行引擎

---

## 2. 功能维度逐项对比

### 2.1 任务类型

| 维度 | Snail-Job | Nop-Job | Nop-Task (补齐) |
|------|-----------|---------|-----------------|
| **单机执行 (CLUSTER)** | `JobTaskTypeEnum.CLUSTER` — 集群中选一个节点执行 | `IJobInvoker.invokeAsync()` — 基本执行模式 | ✅ 可以直接映射 |
| **广播执行 (BROADCAST)** | `BROADCAST` — 集群全部节点执行 | 无原生广播支持 | ⚠️ 需在 Worker 层实现广播调度 |
| **分片执行 (SHARDING)** | `SHARDING` — 按分片索引执行不同数据 | 无原生分片模型 | ⚠️ 需要设计分片参数传递机制 |
| **Map 任务** | `MAP` — 拆分数据为多个子任务并行 | 无内置 Map 模式 | ✅ 可以用 nop-task `ForkTaskStep` + `ParallelTaskStep` 实现 |
| **MapReduce** | `MAP_REDUCE` — Map + Reduce 阶段 | 无内置 MapReduce | ✅ 可以用 nop-task 多阶段编排实现 |
| **工作流/DAG** | `Workflow` — 决策节点 + Job 节点 + 阻塞策略 | 无内置工作流 | ✅ 用 nop-task `GraphTaskStep` 实现 DAG 编排 |
| **一次性任务** | 支持（trigger 触发） | `onceTask=true` | ✅ |
| **CRON 定时** | CRON 表达式 | `TriggerSpec.cronExpr` | ✅ |
| **固定频率** | CRON 支持 | `TriggerSpec.repeatInterval` + `repeatFixedDelay` | ✅ |
| **延迟任务** | 支持 delayed trigger | `OnceTrigger` + `minScheduleTime` | ✅ |
| **固定时间窗口** | 有限支持 | `minScheduleTime` / `maxScheduleTime` | ✅ |

### 2.2 调度与触发器

| 维度 | Snail-Job | Nop-Job |
|------|-----------|---------|
| CRON 表达式 | ✅ 完整支持 | ✅ `CronExpression` + `CronTrigger` |
| 固定间隔 | ✅ | ✅ `PeriodicTrigger` |
| 固定延迟 | ✅ | ✅ `repeatFixedDelay` |
| 日历调度（跳过节假日） | ❌ 无 | ✅ `AnnualCalendar`, `MonthlyCalendar`, `WeeklyCalendar`, `DailyCalendar`, `CronCalendar`, `HolidayCalendar` |
| 暂停日历 | ❌ 无 | ✅ `PauseCalendarTrigger` |
| 执行次数限制 | ❌ 无 | ✅ `maxExecutionCount` / `LimitCountTrigger` |
| 时间窗口限制 | ❌ 无 | ✅ `minScheduleTime` / `maxScheduleTime` / `LimitTimeTrigger` |
| 错过调度处理 | ✅ 有限支持 | ✅ `HandleMisfireTrigger` + `misfireThreshold` |
| 触发后自动删除 | ❌ 无 | ✅ `onceTask` 标记 |

### 2.3 执行模型

| 维度 | Snail-Job | Nop-Job | Nop-Task (补齐) |
|------|-----------|---------|-----------------|
| 同步执行 | ✅ | ✅ `CompletionStage` 异步返回 | ✅ |
| 异步执行 | ✅ 内置线程池 | ✅ `CompletionStage` | ✅ |
| 超时控制 | ✅ `executorTimeout` | ✅ `TriggerSpec` + `JobTimeoutChecker` | ✅ `TimeoutTaskStepWrapper` |
| 任务中断/取消 | ✅ 通过时间轮停止 | ✅ `IJobInvoker.cancelAsync()` | ✅ |
| 并行数控制 | ✅ `parallelNum` | 限制执行线程池 | ✅ |
| 失败策略 (SKIP/BLOCK) | ✅ `FailStrategyEnum` | ⚠️ 需设计 | ✅ `TryTaskStepWrapper` |
| 重试机制 | ✅ `RetryBlockStrategyEnum` | ⚠️ 需设计 | ✅ `RetryTaskStepWrapper` |
| 任务编排 | ✅ Workflow 节点 | ❌ 无 | ✅ nop-task 全套 Step 类型 |

### 2.4 阻塞与冲突处理

| 维度 | Snail-Job | Nop-Job |
|------|-----------|---------|
| DISCARD — 放弃新建批次 | ✅ | ✅ `JobPlannerScannerImpl.shouldDiscard()` |
| OVERLAY — 停止当前批次新建 | ✅ | ⚠️ 创建新 fire 但未 cancel 旧 fire 执行，需完善 |
| CONCURRENCY — 并发执行 | ✅ | ✅ PARALLEL 默认行为 |
| RECOVERY — 重新执行失败任务 | ✅ | ❌ 需实现 |

### 2.5 分布式能力

| 维度 | Snail-Job | Nop-Job |
|------|-----------|---------|
| 节点注册发现 | ✅ Netty 自动注册 | ✅ nop-rpc 服务注册发现 |
| 客户端负载均衡 | ✅ ConsistentHash / Random / Round / LRU / First / Last | ❌ 需设计 |
| 服务端分片 | ✅ ConsistentHash + Average | ❌ 需设计 |
| 故障转移/Failover | ✅ 节点下线自动迁移任务 | ⚠️ 需设计（Coordinator 层面可支持） |
| 全流程高可用 | ✅ Server 集群 + MySQL 持久化 | ⚠️ Coordinator 需要 HA 设计 |
| 秒级调度 | ✅ 时间轮 + Netty | ✅ `IJobPlannerScanner` 轮询扫描 |

### 2.6 任务重试

| 维度 | Snail-Job | Nop-Job |
|------|-----------|---------|
| 重试场景 | ✅ **作为核心产品特性** — 独立的 retry 模块 | ❌ 无内置重试平台 |
| 重试策略 | ✅ 固定间隔 / 指数退避 / 自定义 | ⚠️ 需要自行实现或另外设计方案 |
| 重试次数 | ✅ 可配 | ⚠️ |
| 重试持久化 | ✅ 独立 retry 表 | ❌ |
| 重试管理 UI | ✅ 完整管理界面 | ❌ |

> **注意**: snail-job 的定位本身就是"分布式任务重试和调度平台"，重试是核心功能之一。
> 如果仅仅替换 job 调度部分，重试能力需要单独评估。

### 2.7 持久化模型

#### Snail-Job 关键表

| 表 | 作用 |
|----|------|
| `job` | 作业定义（CRON、执行器、参数） |
| `job_task` | 作业每次触发生成的任务批次 |
| `job_task_detail` | 每个执行节点的任务明细 |
| `job_summary` | 执行汇总/统计 |
| `retry_task` | 重试任务（独立功能） |
| `server_node` | 服务节点注册信息 |
| `notify_*` | 通知告警相关 |

#### Nop-Job 关键实体

| 实体 | 表名 | 作用 |
|------|------|------|
| `NopJobSchedule` | `nop_job_schedule` | 调度定义（jobName, jobGroup, executorRef, triggerSpec） |
| `NopJobFire` | `nop_job_fire` | 每次触发执行记录 |
| `NopJobTask` | `nop_job_task` | 任务实例/执行记录 |

#### Nop-Task 关键实体

| 实体 | 表名 | 作用 |
|------|------|------|
| `NopTaskDefinition` | `nop_task_definition` | 任务流程定义 |
| `NopTaskInstance` | `nop_task_instance` | 流程实例 |
| `NopTaskStepInstance` | `nop_task_step_instance` | 步骤实例 |

### 2.8 多语言支持

| 维度 | Snail-Job | Nop-Job |
|------|-----------|---------|
| Java | ✅ 原生支持（@JobExecutor 注解） | ✅ IJobInvoker |
| Python | ✅ 独立 Python SDK 项目 | ❌ 需自行实现 HTTP API 调用 |
| Go | ✅ 独立 Go SDK 项目 | ❌ 需自行实现 |
| HTTP 调用 | ✅ `AbstractHttpExecutor` | ✅ 通过自定义 IJobInvoker |
| Shell/PowerShell | ✅ `AbstractShellExecutor` / `AbstractPowerShellExecutor` | ✅ 通过自定义 IJobInvoker |
| 脚本执行 | ✅ `AbstractScriptExecutor` | ✅ 通过自定义 IJobInvoker |

### 2.9 告警与监控

| 维度 | Snail-Job | Nop-Job |
|------|-----------|---------|
| 内置告警 | ✅ 抽象告警体系（钉钉、邮件、Webhook 等） | ❌ 无 |
| 作业执行日志 | ✅ `SnailJobLogManager` + 在线日志查看 | ✅ 可用 Nop Log 机制 |
| 执行统计 | ✅ `job_summary` 汇总表 | ⚠️ 部分可以从 `NopJobFire` 聚合 |
| 管理仪表盘 | ✅ 完整的 Vue 管理界面 | ⚠️ `nop-job-web` 有基本页面 |
| OpenAPI | ✅ 完整的 REST API（Job/Workflow CRUD + 触发）| ✅ Nop 平台 GraphQL |

### 2.10 运维管理

| 维度 | Snail-Job | Nop-Job |
|------|-----------|---------|
| 作业 CRUD | ✅ `JobApi` | ✅ `NopJobScheduleBizModel` + GraphQL |
| 手动触发 | ✅ | ✅ |
| 暂停/恢复 | ✅ | ✅ `SUSPENDED` 状态 |
| 在线查看日志 | ✅ 管理台 | ⚠️ 需对接 Nop Log |
| 权限管理 | ✅ 有权限体系 | ✅ Nop 平台权限机制 |
| 命名空间/分组 | ✅ namespace + group | ✅ `JobSpec.jobGroup` |
| 迁移工具 | ✅ xxl-job 迁移脚本 | ❌ |

---

## 3. Nop-Job + Nop-Task 核心优势

### 3.1 流程编排远超 Snail-Job 的 Workflow

Snail-Job 的工作流只支持"决策节点 + Job 节点"两种类型，阻塞策略也只有 4 种。

Nop-Task 提供了**36 种 Step 类型**，覆盖：

| 类别 | Step 类型 |
|------|-----------|
| **顺序执行** | `SequentialTaskStep` |
| **并行执行** | `ParallelTaskStep` |
| **Fork/Join** | `ForkTaskStep`, `ForkNTaskStep` |
| **条件分支** | `IfTaskStep`, `ChooseTaskStep`, `SelectorTaskStep`, `DecisionTaskStep` |
| **循环** | `LoopTaskStep`, `LoopNTaskStep` |
| **DAG 图** | `GraphTaskStep`（任意有向无环图） |
| **调用** | `CallTaskStep`, `CallStepTaskStep`, `DelegateTaskStep` |
| **异常处理** | `TryTaskStepWrapper` |
| **重试** | `RetryTaskStepWrapper` |
| **超时** | `TimeoutTaskStepWrapper` |
| **限流** | `ThrottleTaskStepWrapper`, `RateLimitTaskStepWrapper` |
| **延迟/睡眠** | `SleepTaskStep`, `DelayTaskStep` |
| **挂起** | `SuspendTaskStep` |
| **校验** | `ValidatorTaskStepWrapper` |
| **同步** | `SyncTaskStepWrapper` |
| **事务** | `TransactionTaskStepDecorator` |
| **Bean/静态方法/表达式** | `BeanTaskStep`, `InvokeStaticTaskStep`, `EvalTaskStep` |

这使 nop-job 的作业内部编排能力远远超过 snail-job 的 workflow。

### 3.2 与 Nop 平台深度集成

- **ORM**: NopOrm 统一持久化
- **GraphQL**: 自动生成 CRUD GraphQL API
- **IoC**: 依赖注入 + Beans 容器
- **XLang DSL**: 任务流程可以 XML/JSON 定义
- **Delta 定制**: 不需要修改基础产品代码
- **权限**: 复用 Nop 平台权限模型

### 3.3 触发器模型更丰富

Nop-Job 的 `TriggerSpec` 支持：

- CRON + 固定间隔 + 固定延迟
- **日历系统**: 6 种日历（年/月/周/日/CRON/假日）支持跳过特定日期
- **执行次数限制** 和 **时间窗口**
- **Misfire 处理**（错过调度的补偿策略）
- **暂停日历**（类似 snail-job 的阻塞策略）

### 3.4 异步原生

`IJobInvoker.invokeAsync()` 返回 `CompletionStage`，天然支持异步编排。

---

## 4. Gap 分析 — Nop-Job 需要补齐的能力

### P0 — 必须补齐（否则无法替代）

> **通信机制不是 gap**: Worker 注册发现 + 任务分发直接用 `nop-rpc`，Worker 启动时注册 RPC 服务（如 `IJobWorker`），Coordinator 通过 RPC 服务发现获取 Worker 列表，直接 RPC 调用分发任务。不需要额外设计通信协议或注册中心。

| Gap | 说明 | 建议方案 |
|-----|------|----------|
| **广播/分片执行** | 同 snail-job BROADCAST / SHARDING | ✅ 已实现 `RpcBroadcastTaskBuilder`（`IJobTaskBuilder` 实现），每个实例一个 task，payload 自动注入 `targetHost`/`shardingIndex`/`shardingTotal` |
| **阻塞策略** | DISCARD ✅ / OVERLAY ⚠️ 需完善 cancel / RECOVERY ❌ 未实现 | 详见 `ai-dev/design/nop-job/block-strategy-design.md` |
| **手动触发/CRUD 管理页** | `nop-job-web` 需要达到 snail-job 管理台的 CRUD 操作水平 | 利用 Nop 平台代码生成补充页面 |

### P1 — 重要但不阻塞最小可用

| Gap | 说明 | 建议方案 |
|-----|------|----------|
| **Map/MapReduce** | 需要通过 nop-task 实现 | nop-job 触发 nop-task 流程，用 `ForkTaskStep` + 聚合 Step 编排；内部状态直接走 nop-task 的实例/步骤实例历史记录，不需要 nop-job 额外记录 |
| **告警通知** | 钉钉/邮件/Webhook 告警 | 可基于 Nop 平台的 `IEventPublisher` 或自定义告警处理 |
| **执行统计/监控 Dashboard** | 如 snail-job 的 job_summary 仪表盘 | 设计 `JobMetrics` 记录并 GraphQL 查询 |
| **集群限流** | Worker 节点总并发控制 | 可用 `RateLimitTaskStepWrapper` + 分布式限流 |
| **执行日志在线查看** | snail-job 可以在线查看执行日志 | 对接 Nop 日志收集与展示 |
| **OpenAPI** | snail-job 有独立 REST API 供外部系统调用 | Nop GraphQL 已提供，可以补充 REST |

### P2 — 可后续迭代

| Gap | 说明 |
|-----|------|
| **Python SDK** | 非 Java 系统需要通过 HTTP/RPC 调用 |
| **Go SDK** | 同上 |
| **xxl-job 迁移工具** | 如果涉及从 xxl-job 迁移 |
| **运维告警** | 如执行失败自动告警 |
| **执行历史归档** | 长周期历史数据的清理/归档 |

---

## 5. 推荐替代策略

### 阶段一：核心调度能力就位

```
目标：实现 snail-job CLUSTER（单机执行）+ CRON/固定频率调度

1. 使用 nop-job Coordinator + Worker 架构
2. 定义 IJobWorker RPC 接口，Worker 注册 RPC 服务，Coordinator 通过 RPC 分发任务
3. 补齐手动触发 + CRUD 管理页面
4. 打通 nop-job → nop-task 的调用链路
```

### 阶段二：高级执行模式（IJobTaskBuilder + nop-task 编排）

```
目标：实现 BROADCAST / SHARDING / MAP / MAP_REDUCE

1. 广播/分片：统一用 RpcDistributedTaskBuilder，每个实例一个 task，自动注入 shardingIndex/shardingTotal header
2. 阻塞策略：在调度入口增加阻塞判断（DISCARD/OVERLAY/RECOVERY）
3. Map/Reduce：nop-job 触发 nop-task 流程，内部状态走 nop-task 历史记录
```

### 阶段三：nop-task 深度集成

```
目标：Map/MapReduce 及复杂 Job 使用 nop-task 编排

1. nop-job 触发后，启动 nop-task 流程实例
2. Map：ForkTaskStep 拆分数据 → ParallelTaskStep 并行执行
3. MapReduce：ForkTaskStep 拆分 → 并行执行 → SequentialTaskStep 聚合
4. 内部状态（NopTaskInstance / NopTaskStepInstance）天然记录每一步执行历史
5. 利用 nop-task 的 Retry/Timeout/Transaction/Suspend 装饰器
```

### 阶段四：运维完善

```
目标：达到或超越 snail-job 的运维水平

1. 执行日志在线查看
2. 告警通知集成
3. 执行统计 Dashboard
4. 执行历史管理（归档/清理）
```

---

## 6. 模块对应关系

| Snail-Job 模块 | 对应 Nop 模块 | 备注 |
|----------------|--------------|------|
| `snail-job-client` | `nop-job-worker` | Worker 端 SDK/invoker |
| `snail-job-client-starter` | `nop-job-app` | 启动集成配置 |
| `snail-job-server-dispatcher` | `nop-job-coordinator` | 调度 Coordinator |
| `snail-job-common` | `nop-job-api` + `nop-job-core` | 公共 API 与核心引擎 |
| `snail-job-datasource` | `nop-job-dao` (NopOrm) | 持久化 |
| `snail-job-server-interface` | `nop-job-service` + `nop-job-web` | 业务服务与 API |
| — | `nop-task` | 流程编排引擎（新增） |
| `snail-job-admin` (Vue) | `nop-job-web` | 管理页面 |

---

## 7. 关键接口映射

| Snail-Job | Nop-Job | 状态 |
|-----------|---------|------|
| `IJobExecutor.jobExecute(JobContext)` | `IJobInvoker.invokeAsync(IJobExecutionContext)` | ✅ 直接映射 |
| `@JobExecutor(name, method)` | `IJobInvokerResolver.resolveInvoker()` | ✅ |
| `JobArgs` / `ShardingJobArgs` | `IJobExecutionContext` | ✅ |
| `TriggerTypeEnum` CRON | `TriggerSpec.cronExpr` | ✅ |
| — | 日历系统（6 种 Calendar） | ✅ Nop 独有 |
| `JobBlockStrategyEnum` | — | ❌ 需补齐 |
| `FailStrategyEnum` | `TryTaskStepWrapper` | ✅ nop-task 覆盖 |
| `AllocationAlgorithmEnum` | — | ❌ 需补齐 |
| `ExecutorTypeEnum` (Python/Go) | — | ⚠️ HTTP Invoker 方案 |
| Workflow (决策+Job) | `GraphTaskStep` + `DecisionTaskStep` | ✅ nop-task 覆盖 |

---

## 8. 结论与建议

### 优势总结

| | Snail-Job | Nop-Job + Nop-Task |
|---|---|---|
| **流程编排** | 仅有决策+Job 两种节点 | 36 种 Step 类型，完整 DAG 图编排 |
| **触发器** | CRON + 固定频率 | CRON + 固定频率 + 固定延迟 + 6 种日历 + 次数/时间限制 |
| **框架集成** | Spring Boot 单体 | Nop Platform 全栈（Orm/GraphQL/IoC/DSL） |
| **可定制性** | 配置 + 插件 | Delta 定制 + XLang DSL + IoC |
| **异步支持** | 线程池 | CompletionStage 原生异步 |
| **多语言** | Java + Python + Go | Java 为主，需自行扩展 |
| **运维管理** | 成熟完整 | 基础页面，需补充 |

### 判断

1. **纯调度层面（CRON/固定频率触发执行）**, nop-job 已有较完整覆盖，甚至在某些方面（日历系统、触发器约束）比 snail-job 更强。

2. **执行模式层面（BROADCAST/SHARDING/MAP/MAP_REDUCE/workflow）**, nop-job 原生不提供这些模式的实现，但：
   - **流程编排（workflow/MAP/MAP_REDUCE）完全可以用 nop-task 覆盖甚至超越** snail-job 的 workflow
   - **广播/分片**统一用 `RpcDistributedTaskBuilder`，每个实例一个 task，自动注入 `shardingIndex`/`shardingTotal` header，接收方自行决定行为

3. **通信机制不是 gap**: Worker 注册发现 + 任务分发直接用 `nop-rpc`。**广播/分片 task 构建由 `IJobTaskBuilder` 完成**，接口已支持返回多 task，不需要在通信层额外设计。

4. **任务重试是 snail-job 的核心功能**，nop-job 无此设计。如果业务需要重试平台，需要单独评估方案。

### 推荐

**完全替代 snail-job 是可行的**，但需要分阶段实施：

```
优先替代:  纯定时调度 + 作业内部用 nop-task 编排
核心设计:  BlockStrategy（阻塞策略）
实现基础:  IJobTaskBuilder（广播/分片多 task 构建）+ nop-rpc（通信）
补齐关键:  广播/分片 IJobTaskBuilder 实现 + 阻塞策略
后续迭代:  监控 Dashboard / 告警 / 多语言 SDK
```

---

## Open Questions

- [ ] Coordinator 的 HA（高可用）如何设计？是否需要引入分布式锁或选举机制？
- [ ] 阻塞策略（DISCARD/OVERLAY/RECOVERY）的具体实现方案？
- [ ] Worker 侧当前是 DB 轮询拉取模式，是否改为 Coordinator RPC 推送模式？
- [ ] nop-job 的 NopJobFire 和 NopJobTask 分层是否足够支持 MAP/MAP_REDUCE 的子任务粒度？
- [ ] 现有的 snail-job 运行中的任务如何平滑迁移到 nop-job？
- [ ] 如果保留重试需求，是否将重试作为独立流程用 nop-task 编排实现？

---

## References

- [Snail-Job 官方文档](https://snailjob.opensnail.com)
- [Nop-Job 源码](nop-job/)
- [Nop-Task 源码](nop-task/)
- [Nop 平台模块结构](docs-for-ai/01-repo-map/module-groups.md)
- [标准业务模块骨架](docs-for-ai/01-repo-map/domain-module-pattern.md)

## 附加数据

### Nop-Job 模块文件统计

| 模块 | 主要职责 |
|------|---------|
| `nop-job-api` | 接口契约: JobSpec, TriggerSpec, IJobInvoker, IJobScheduler |
| `nop-job-core` | 核心引擎: 触发器实现（CRON/Periodic/Once/Misfire）, 日历系统（6种） |
| `nop-job-coordinator` | 调度协调器: Planner, Dispatcher, Completion, Timeout |
| `nop-job-worker` | 执行器: JobWorker, InvokerResolver, ExecutionContext |
| `nop-job-dao` | 持久层: NopJobSchedule, NopJobFire, NopJobTask |
| `nop-job-service` | 业务服务: BizModel, 逻辑编排 |
| `nop-job-web` | 管理页面: view.xml, page.yaml |

### Nop-Task Step 类型完整列表

```
顺序执行:   SequentialTaskStep
并行执行:   ParallelTaskStep, ForkTaskStep, ForkNTaskStep
条件分支:   IfTaskStep, ChooseTaskStep, SelectorTaskStep
循环:       LoopTaskStep, LoopNTaskStep
图编排:     GraphTaskStep (DAG)
输入输出:   BuildOutputTaskStepWrapper
调用:       CallTaskStep, CallStepTaskStep, DelegateTaskStep
Bean调用:   BeanTaskStep
静态方法:   InvokeStaticTaskStep
表达式:     EvalTaskStep
终结点:     EndTaskStep, ExitTaskStep
装饰器:
  - TryTaskStepWrapper (try/catch)
  - RetryTaskStepWrapper (重试)
  - TimeoutTaskStepWrapper (超时)
  - ThrottleTaskStepWrapper (节流)
  - RateLimitTaskStepWrapper (限速)
  - SyncTaskStepWrapper (同步)
  - ValidatorTaskStepWrapper (校验)
  - RunOnContextTaskStepWrapper (上下文)
  - ExecutorTaskStepWrapper (Executor)
特殊:
  - SleepTaskStep, DelayTaskStep (延迟)
  - SuspendTaskStep (挂起)
  - DelegateTaskStep (委托)
```

---

> 本分析由 AI 完成，基于对 snail-job（~/sources/snail-job）和 nop-entropy（nop-job + nop-task）的源码阅读。结论和建议供方案设计参考，需要人工复核验证。
