# nop-stream 架构基线

**日期**：2026-05-19（更新于 2026-06-06）
**范围**：`nop-stream` 模块
**状态**：active

---

## 一、设计结论

1. 系统分为七层：API → StreamComponents → Transformation → 执行计划 → 算子 → 状态&时间 → 存储
2. 核心模型是 StreamModel——可序列化算子图，三种入口（XDSL / Java API / Delta）最终生成同一类 canonical 模型
3. 六阶段执行管线：StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology（图模型层数仍为 2：StreamGraph → JobGraph；详见 §四）
4. 依赖方向严格单向：runtime/checkpoint/connector/cep/flow → core → api

## 二、模块划分

```
nop-stream/
├── nop-stream-core         [实现] StreamModel、StreamComponents、图模型、PartitionedPlan、DeploymentPlan、Checkpoint 类型定义
├── nop-stream-runtime      [实现] RuntimeTopology、task 执行、transport backend、fencing、node lifecycle、Checkpoint 协调器与存储
├── nop-stream-connector    [实现] 连接器适配层：replayable source、transactional sink、SourceWorkUnit
├── nop-stream-cep          [实现] Pattern/NFA/SharedBuffer、CEP operator（接入统一状态后端）
├── nop-stream-flow         [规划] XDSL StreamModel 编排，支持 Delta 定制
└── nop-stream-fraud-example[实现] 端到端欺诈检测示例
```

### 模块职责边界

| 模块 | 职责 | 依赖方向 |
|------|------|----------|
| **nop-stream-core** | StreamModel + StreamComponents、StreamGraph/JobGraph、PartitionedPlan/DeploymentPlan、优化和校验、StreamRequirement 校验、Checkpoint 类型定义（`core.checkpoint` 包） | 无 |
| **nop-stream-runtime** | RuntimeTopology、本地/分布式 task 执行、transport backend、fencing、node lifecycle、EdgeConfig flow control、Checkpoint 协调器与存储实现（`runtime.checkpoint` 包） | → core |
| **nop-stream-connector** | Replayable source（SourceWorkUnit + RestrictionTracker）、transactional/idempotent sink（CheckpointParticipant）、split/offset 协议适配 | → core |
| **nop-stream-cep** | Pattern DSL、NFA 编译、SharedBuffer、CepOperator（通过标准 state/timer 接口接入统一后端）、声明式模型（pattern.xdef） | → core |
| **nop-stream-flow** | XDSL StreamModel 编排、Delta 定制支持 | → core |

### 依赖方向

依赖只能从右向左：运行时和集成模块依赖 core，core 不依赖任何实现模块。

```
runtime / connector / cep / flow  →  core
```

关键约束：

| 规则 | 说明 |
|---|---|
| core 不依赖 runtime | core 只定义模型和编译结果 |
| connector 不依赖具体 runtime | connector 声明 source/sink 能力和状态协议 |
| cep 不依赖 runtime checkpoint 实现 | CEP operator 通过标准 state/timer 接口接入 |

## 三、分层设计

```
┌──────────────────────────────────────────────────────────────────┐
│  用户 API 层                                                     │
│  DataStream / KeyedStream / WindowedStream                       │
├──────────────────────────────────────────────────────────────────┤
│  StreamComponents 层                                             │
│  Transforms / Streams / WindowingStrategies / Coders / Schemas   │
│  Environments / SideInputs / Requirements / CheckpointParticipants│
├──────────────────────────────────────────────────────────────────┤
│  Transformation 层                                               │
│  Source / Sink / OneInput / Partition Transformation DAG          │
├──────────────────────────────────────────────────────────────────┤
│  执行计划层                                                      │
│  StreamModel → StreamGraph → JobGraph                           │
│              → PartitionedPlan → DeploymentPlan                  │
├──────────────────────────────────────────────────────────────────┤
│  算子层                                                         │
│  StreamMap / StreamFilter / StreamFlatMap                        │
│  WindowOperator / CepOperator / StreamSource / StreamSink        │
├──────────────────────────────────────────────────────────────────┤
│  状态 & 时间层                                                   │
│  IStateBackend / IKeyedStateBackend / StateShard                 │
│  Trigger / WindowAssigner / Evictor / InternalTimerService       │
├──────────────────────────────────────────────────────────────────┤
│  存储层                                                         │
│  ICheckpointStorage                                              │
│  ├── LocalFileCheckpointStorage                                  │
│  └── JdbcCheckpointStorage (IJdbcTemplate)                       │
└──────────────────────────────────────────────────────────────────┘
```

**StreamComponents 层**是模型可移植性的核心。所有可复用组件（transforms、streams、windowingStrategies、coders、schemas、environments）都通过稳定 ID 引用，而非内联定义。组件注册表参与 fingerprint 计算，保证跨 backend 校验和 savepoint 兼容性检查的一致性。

**StreamRequirement** 声明 pipeline 的能力需求（如 `STRICT_EXACTLY_ONCE`、`DISTRIBUTED_EXECUTION`），编译器和 backend 在运行前必须校验这些需求是否被满足。

## 四、执行模型

### 图模型层数与执行管线分层（D71 — vs Flink 有意设计差异）

nop-stream 区分三个互不混用的视角，避免层数口径在三份文档间冲突：

| 视角 | 层数 | 内容 | 出处 |
|---|---|---|---|
| **图模型**（与 Flink 同口径） | **2 层** | `StreamGraph` → `JobGraph` | `graph-model-design.md` §1.1/§8 |
| **执行管线**（nop-stream 自身视角） | **6 阶段** | `StreamModel` → `StreamGraph` → `JobGraph` → `PartitionedPlan` → `DeploymentPlan` → `RuntimeTopology` | 本节下表 |
| **部署计划分层**（独立的部署抽象维度，**不计入图模型层数**） | 2 层 | `PartitionedPlan` → `DeploymentPlan` | 本节下表后两行 |

**有意设计差异（vs Flink）**：

- **拒绝 Flink `ExecutionGraph`（第三层图模型）**：nop-stream 的分布式调度通过 `IStreamExecutionDispatcher` SPI + `DeploymentMode` 枚举实现，不需要 Flink 风格的 `ExecutionVertex` + `ExecutionAttempt` 三层调度抽象。LOCAL 模式 `JobGraph` 直接生成 `Task`，由 `TaskExecutor` 线程池执行；DISTRIBUTED 模式 `JobGraph` 生成 `Subtask` 集合，由 `EmbeddedDistributedExecutor` 拆分到多个 `TaskManager` 实例。
- **将部署抽象独立成 `PartitionedPlan`/`DeploymentPlan`**：Flink 把部署信息内联在 `ExecutionGraph`；nop-stream 把并行展开、分区策略、state shard 路由、checkpoint ACK 集合、节点映射、transport/state backend binding 独立成可序列化的 plan 对象，使部署决策可脱离 runtime 单独持久化与审计。这是**部署维度的有意细化**，不增加图模型层数。
- 因此比较 Flink vs nop-stream 的**图模型层数**应为：Flink **3 层**（StreamGraph → JobGraph → ExecutionGraph）vs nop-stream **2 层**（StreamGraph → JobGraph）；`PartitionedPlan`/`DeploymentPlan` 作为 nop-stream 的部署抽象维度另行记录，不计入图模型层数。

### 六阶段执行管线

```
StreamModel
    → StreamGraph
    → JobGraph
    → PartitionedPlan
    → DeploymentPlan
    → RuntimeTopology
```

| 阶段 | 职责 | 是否持久化 |
|---|---|---|
| `StreamModel` | 用户意图的规范模型，包含 StreamComponents registry 和 Transformation DAG。来自 Java API、XDSL 或 Delta 合成 | 是 |
| `StreamGraph` | 逻辑 DAG，表达 source、operator、sink 和边语义 | 可持久化 |
| `JobGraph` | 算子链化和逻辑优化后的作业图 | 可持久化 |
| `PartitionedPlan` | 并行展开、state shard、subtask、edge channel、partition policy、checkpoint route 的语义计划。是分布式 exactly-once 的中心模型（**部署计划分层**） | 必须持久化 |
| `DeploymentPlan` | 将 partitioned task 映射到 runtime node、transport backend、state backend binding、checkpoint storage、EdgeConfig flow control、memory budget、subtask→node 物理分配（`DeploymentAssignment`）（**部署计划分层**） | 必须持久化 |
| `RuntimeTopology` | 运行时实例视图：attempt、心跳、通道状态、checkpoint 进度。可重建，不允许反向生成状态路径或分区规则 | 可重建 |

**关键决策**：`PartitionedPlan` 承载并行度、分区、状态路由和 checkpoint ACK 集合。运行时只能执行它，不能重新发明拓扑语义。本地线程执行只是 `DeploymentPlan` 的一种 backend，分布式语义不能依赖本地线程模型。

### 运行时执行链路（live 概览，与 checkpoint-design §2.2 分工）

> 本节给执行模型概览与管线层职责；**barrier 协议细节、对齐规则、abort 接线契约以 `checkpoint-design.md` §2.2—§2.4 与 §8.7 为权威**，此处不重复。

执行入口与控制面：

| 组件 | 角色 | live 锚点 |
|---|---|---|
| `GraphModelCheckpointExecutor` | checkpoint 启用时的执行入口；构建 `GraphExecutionPlan` → 装配 `CheckpointCoordinator`、per-subtask `CheckpointBarrierTracker`、`InputGate` | `STRM-024` |
| `StreamTaskInvokable` | 每个 task 的执行 invokable；按 JobGraph 位置扮演 SOURCE/MIDDLE/SINK/SELF_CONTAINED；持有 `MailboxExecutor` 控制面，主循环 `processInputGate()` | `STRM-023` |
| `InputGate` | 多输入合并读取 + 生产 barrier 对齐 + watermark 合并 + channel 阻塞/恢复 | `STRM-021` |
| `TaskExecutor` | 线程池调度器，按 `JobVertex` parallelism 创建 `Task`/`SubtaskTask` | `STRM-025` |
| `SubtaskTask` | 分布式执行单元，状态机 CREATED→RUNNING→CANCELING→COMPLETED/FAILED/CANCELED | `STRM-026` |

Checkpoint 触发链路（概览）：

```
CheckpointCoordinator (runtime/checkpoint)
   ↓ triggerCheckpoint (injector 线程, 同步 prime ack 计数)
StreamSourceOperator.offerBarrier()
   ↓ CONTROL-priority mail (非 finished source) / injector 线程直接调用 (finished source)
SourceContext.collect() (source task 线程, drain mail)
   ↓ snapshotState → emitBarrier
RecordWriter.emitBarrier() (广播到全部分区)
   ↓ in-band 数据通道
InputGate.handleBarrierNonRecursive() (下游 task)
   ↓ blockConsumption / resumeConsumptionAll / 单一对齐 barrier 输出
CheckpointBarrierTracker.processBarrier() (per-task ACK 聚合)
   ↓ 收齐回调 Consumer<TaskStateSnapshot>
CheckpointCoordinator (manifest durable → sink commit)
```

完整 barrier 注入规则、对齐规则、对齐超时、abort 接线见 `checkpoint-design.md` §2.2—§2.4、§8.7。本节只标注"链路上有哪些 live 类"，避免重复 barrier 协议细节。

### PartitionedPlan 必须记录的信息

| 信息 | 原因 |
|---|---|
| 每个 vertex 的 parallelism | 运行时必须创建所有 subtask |
| 每个 operator 的稳定 operatorId | 状态恢复不能依赖链内 index 或对象顺序 |
| 每个 key 分配到哪个 state shard | keyed state 必须可以跨节点定位和恢复 |
| 每个 state shard 由哪个 subtask 拥有 | failure/restart/rescale 需要确定状态归属 |
| 每条边的 partition policy（FORWARD / HASH / REBALANCE / BROADCAST / UNION / SINGLETON） | keyBy、forward、broadcast、rebalance 必须进入执行计划 |
| 每个 subtask 的输入/输出 channel | barrier 对齐和数据传输需要通道级身份 |
| checkpoint ACK 集合 | Coordinator 必须知道哪些 task 必须 ACK |

### 执行流程

```
                        ┌─────────────────────────────┐
  XDSL 声明式定义 ──────┤  XDSL Parser               │
                        │  (加载 .graph.xml 直接构造)  │
                        └─────────────┬───────────────┘
                                      │
                        ┌─────────────▼───────────────┐
  Java DataStream API ──┤  StreamModel Builder        │
                        │  (从 Transformation DAG 构造)│
                        └─────────────┬───────────────┘
                                      │
                          ┌───────────▼───────────┐
                          │     StreamModel       │
                          │  (canonical 模型入口)   │
                          └───────────┬───────────┘
                                      │
                          ┌───────────▼───────────┐
                          │   StreamGraph         │
                          │   (逻辑 DAG)           │
                          └───────────┬───────────┘
                                      │
                          ┌───────────▼───────────┐
                          │   JobGraph            │
                          │   (算子链融合优化)       │
                          └───────────┬───────────┘
                                      │
                          ┌───────────▼───────────┐
                          │   PartitionedPlan     │
                          │   (并行展开 + 分区)     │
                          └───────────┬───────────┘
                                      │
                          ┌───────────▼───────────┐
                          │   DeploymentPlan      │
                          │   (节点映射 + 配置)     │
                          └───────────┬───────────┘
                                      │
               ┌──────────────────────┼──────────────────────┐
               │                      │                      │
    ┌──────────▼──────────┐  ┌───────▼───────────────┐
    │  LOCAL               │  │  DISTRIBUTED          │
    │  GraphExecutionPlan  │  │  IStreamExecutionDispatcher│
    │  → TaskExecutor      │  │  → EmbeddedDistributedExec │
    │    (线程池调度)        │  │    (JobCoordinator + TM)   │
    └─────────────────────┘  └────────────────────────┘
```

`DeploymentMode` 枚举（`LOCAL` / `DISTRIBUTED`）定义在 core 模块。`IStreamExecutionDispatcher` SPI 接口由 runtime 模块实现，`StreamExecutionEnvironment.execute()` 通过 `executionDispatcher` 字段路由到正确的执行器。

## 五、分布式控制面契约

### 三面架构

| 面 | 职责 | 传输方式 |
|---|---|---|
| **控制面** | 作业调度、task 分配、cancel、状态查询 | `IStreamTaskRpcService` / `IStreamCoordinatorRpcService` 强类型接口 |
| **数据面** | 记录传输、barrier 传播、watermark 传播 | `IMessageService` + RemoteResultPartition / RemoteInputChannel |
| **编排面** | Invokable 安装、算子链配置 | 同进程：直接 Java 调用；跨进程：各节点 Bean 容器本地构建，编排面只下发 DSL/plan 描述 |

**关键设计决策**：不使用适配器模式包装 `IRpcService`。`TaskManager IS-A IStreamTaskRpcService`，`JobCoordinator IS-A IStreamCoordinatorRpcService`。嵌入式模式下直接 Java 调用；分布式模式下由 Nop RPC 框架生成远程代理。

**跨 JVM 编排面设计**：`StreamTaskInvokable` 包含 live operator 对象，不可跨进程序列化传输。跨 JVM 部署基于以下机制：

1. **DSL 驱动**：引擎执行 DSL（XLang StreamModel），各节点从 DSL 构建本地的 StreamGraph → JobGraph → OperatorChain
2. **Bean 容器**：NopIoC 容器中注册了所需的 operator、source、sink 等 bean，各节点通过容器获取依赖
3. **编排面只传 plan 描述**：跨 JVM 时编排面不下发 invokable 对象，只下发 `PartitionedPlan` / `DeploymentPlan` 描述（可序列化）

### 数据面 — `IMessageService` 复用为有意设计（D72）

**选了什么**：数据面（记录传输、barrier 传播、watermark 传播）跨 JVM 复用 Nop 平台的 `IMessageService`（`SysDaoMessageService` / `PulsarMessageService`），通过 `RemoteResultPartition`（生产端）和 `RemoteInputChannel`（消费端）以 pub/sub topic 形式传输，topic 命名为 `nop-stream.{jobId}.{edgeId}.{sourceSubtask}.{targetSubtask}`。

**与 Flink 的差异**：Flink 数据面基于 **Netty + MemorySegments + NetworkBufferPool**——自建专用网络栈、独立线程模型、credit-based flow control、buffer 引用计数回收。nop-stream 选择**不自建网络栈**，把传输委托给平台消息基础设施。

**为什么如此设计**：

- **平台基建复用优先**：`IMessageService` 在 Nop 平台已有成熟实现（DB-backed `SysDaoMessageService`、`PulsarMessageService`），跨 JVM 传输是其本职能力。流处理引擎重复造网络栈违反"分布式能力一律 WIRE 平台，不自建"的路线图原则（见 `nop-stream-production-roadmap.md` Cross-cutting concerns）。
- **背压与持久化由后端承担**：`SysDaoMessageService`（DB）天然持久化、天然有界（**注**：DB「有界」= 磁盘容量上限，写入超限抛异常而非 flow control；polling 仅决定 consumer 消费速率、不回压 producer）；`PulsarMessageService` 天然 pub/sub 背压（producer pending-message 队列饱和时回压 producer）。flow control 不需要在 nop-stream 内重造（Stage 26 的 `IBufferPool` 仅承担**进程内** backpressure，跨 JVM 由后端提供）。
- **与 Stage 40 接线对齐**：`RemoteResultPartition`/`RemoteInputChannel` 已预留 `IMessageService` 注入点，Stage 40 WIRE `SysDaoMessageService`/`PulsarMessageService` 作为真实后端。两种后端的序列化契约与裸 `StreamMessageEnvelope` 不兼容（`SysDaoMessageService` 仅忠实持久化 `ApiRequest.data`；`PulsarMessageService` 默认 `Schema.STRING` 需 String 值），故 Stage 40 引入 `IDataPlaneWireCodec` SPI（`SysDaoWireCodec` / `PulsarStringWireCodec` / `IdentityWireCodec`）+ `DataPlaneMessageServiceAdapter` 装饰器：数据面视图在 send 侧把 envelope 适配为后端忠实承载的 wire 形态、在 subscribe 侧还原。`Remote*` 类保持后端无关；codec 由部署选择，`nop-stream-runtime` 不引入后端硬依赖（vision §三 约束 8）。控制面与数据面共享同一 `IMessageService` 实例但 topic 不相交（`nop-stream.rpc.*` vs `nop-stream.{jobId}.*`），仅数据面视图经 adapter 包装，两面互不干扰。
- **拒绝的方案**：移植 Flink Netty 栈（引入 Netty 依赖、NetworkBufferPool 抽象、credit-based flow control 协议）—— vision 约束 7 明确排除。

### `ClusterRegistry` JDBC durability — 有意简化（D73）

**选了什么**：`ClusterRegistry`（coordinator / runtime node / lease / task assignment 的一致视图）提供 `InMemoryClusterRegistry`（开发测试）和 `JdbcClusterRegistry`（生产）两种实现。生产实现用 JDBC + `IJdbcTemplate` 多数据库适配，自动建表（`nop_stream_coordinator` / `nop_stream_node` / `nop_stream_task_assignment`），索引 `lease_expire_at` / `node_id`。

**与 Flink 的差异**：Flink HA 依赖 **ZooKeeper**（`LeaderElectionService` + `ZooKeeperLeaderElectionDriver` + `ZooKeeperHaServices`），需要外部协调服务作为强一致性后端。nop-stream 选择**用业务库 JDBC 表承担 durability**，不引入 ZooKeeper 依赖。

**为什么如此设计**：

- **零基建部署**：JDBC 后端复用业务库（与 `JdbcCheckpointStorage` 同库），生产部署无需额外 ZooKeeper/Kubernetes 集群，降低 nop-stream 的部署门槛（中小规模生产场景）。
- **与 Stage 41 决策点 D7 关联**：`ClusterRegistry` 收敛到平台 discovery（`IDiscoveryClient`/`INamingService`）是 Stage 41 的决策点——JDBC 实现作为**当前阶段的简化**，等 Stage 41 平台 discovery 接入后由平台基建承担，而非在 nop-stream 内自建 HA 栈。
- **拒绝的方案**：(a) 引入 ZooKeeper 强依赖（违反"零基建部署"目标）；(b) 完全自建 leader election 与 HA 协议（与 Stage 38 `SysDaoLeaderElector` 接入路径矛盾）。JDBC + leader elector（Stage 38）的组合是 nop-stream HA 的最小基建路径。
- **已知取舍**：JDBC 写 lease 与 ZooKeeper 比较，lease TTL 粒度更粗（默认 15s）、跨节点时钟漂移敏感——这是为"零基建部署"付出的代价，由 `ClusterRegistry` 实现负责 lease TTL 校验的容错（详见 `07-distributed-comparison.md` §6 "JdbcClusterRegistry provides durable alternative"）。

### 平台 discovery 单向注册 — 有意共存（G51, D7 deferred）

**选了什么**：nop-stream 节点通过声明平台 `AutoRegistration` 范式的 bean（`StreamNodeAutoRegistration`，消费 `INamingService`），在启动时注册到平台 discovery，注销时从 discovery 移除。注册为**单向**（nop-stream → 平台 discovery），不在 nop-stream 内消费 discovery 读取做分配或故障检测——ClusterRegistry 仍是 nop-stream 运行时分配/故障检测的唯一消费源。二者在节点存活期保持一致（注册的节点 = lease 活跃的节点）。

**ServiceInstance 字段映射**（`ServiceInstance` 无 `capacity` 字段）：

| ServiceInstance 字段 | 来源 |
|---|---|
| `instanceId` | nodeId |
| `addr` + `port` | endpoint（解析 host:port） |
| `weight` | capacity |
| `metadata["capacity"]` | capacity（显式冗余，便于读取） |
| `serviceName` | `"nop-stream"` |

**注册用 `INamingService.registerInstance`（非只读的 `IDiscoveryClient`）**。遵循平台 bean 生命周期范式（`@PostConstruct` 注册 / `@PreDestroy` 注销），而非在 `TaskManager.start()` 内嵌注册逻辑。

**与 Stage 41 决策点 D7 关联**：ClusterRegistry 完全替换为平台 discovery vs 对接共存，是 Stage 41 决策点。本阶段让二者共存且 discovery 单向注册已满足 G51「节点注册/发现 WIRE 平台」目标，不预判 D7。

### 控制面角色

| 角色 | 职责 |
|---|---|
| `JobCoordinator` | 持有 canonical plan、消费 DeploymentPlan 已物化的 subtask→node 分配（或 fallback 到 runtime round-robin）、触发 epoch、维护 fencing token、per-subtask attempt 编号（G56）、global restart 上限（G56）、JobStatus 终态（FAILED/CANCELED）、per-task 终态上报处理（G52）。Stage 28 起经 `IStreamCoordinatorRpcService` 暴露完整控制面契约：`terminate(JobTerminationMode)`（4 模式）、`abortCheckpoint(epochId)`（触发 LOCAL abort handler）、`getJobStatus()`（返回 `JobStatusResponse` 含状态 + 失败原因）—— local 契约完整，Stage 39 远程化仅加 transport 层 |
| `RuntimeNode` | 注册到集群、汇报心跳、承载 task attempt、暴露本节点资源和 transport endpoint、per-task liveness 上报（piggyback heartbeat） |
| `TaskAttempt` | 某个 stable task 的一次执行尝试，绑定 attemptId（UUID）、attemptNumber（单调递增，per-subtask）和 fencing token；历史保留于 `ClusterRegistry.getAttemptHistory`（G56） |
| `NodeLease` | RuntimeNode 的存活租约，超时后其 task attempt 被视为失效（节点级兜底检测，与 per-task liveness 并存 G52） |
| `ClusterRegistry` | 记录 active coordinator、runtime nodes、node lease 和 task assignment 的一致视图；attempt 历史 append-only（G56，非覆盖式） |

### Dispatcher 最小化 — 有意设计（G26, Stage 28 Decision）

**裁定**：`IStreamExecutionDispatcher` 仅含三个**部署入口**方法（`supportsDeploymentMode` / `getExpectedNodeIds` / `execute`），不承载 job 生命周期管理。这是有意设计，非缺口。

**理由**：在当前同步 `execute()` 模型下，`JobCoordinator` 是 `execute()` 方法内的局部变量，`execute()` 返回后即销毁（见 `EmbeddedDistributedExecutor`）。dispatcher 上新增生命周期方法（terminate/query）在当前架构下**无 coordinator 可委托**。job 生命周期管理（terminate / abort / status）经 Phase 1 暴露的 coordinator RPC 接口（`IStreamCoordinatorRpcService`）完成。

**Successor**：异步 submit + poll 形态的 dispatcher（持有长生命周期 coordinator）属 Stage 39（cross-JVM RPC）。在此之前，dispatcher 保持为部署入口，所有生命周期控制走 coordinator RPC。

### 进程内 backpressure 契约 — IBufferPool 两级（G27, Stage 28 Decision）

**裁定**：in-process backpressure = Stage 26 `IBufferPool`（两级）：(1) per-partition `ResultPartition` 队列阻塞（`queue.put()` 满时阻塞生产者）；(2) per-job `IBufferPool.acquire()` 全局阻塞（跨多 partition 全局聚合内存上限，防 fan-out OOM）。两级均在**进程内**生效。

**跨 JVM backpressure** 由 `IMessageService` 后端提供（Stage 40 已 WIRE）：`SysDaoMessageService`（DB）天然持久化，**不提供 producer 回压**（DB 写入无界——磁盘满才失败且抛异常，非 flow control；polling 仅决定 consumer 消费速率）；`PulsarMessageService` 经 Pulsar producer pending-message 队列饱和提供 producer 回压。nop-stream **不重建 Flink Netty 网络栈**（vision 约束 7）。两种后端的 wire-format 适配由 `IDataPlaneWireCodec` 承担（`SysDaoWireCodec` / `PulsarStringWireCodec`），使裸 `StreamMessageEnvelope` 能被后端忠实承载（见上 D72）。

**CREDIT_BASED / ACK_WINDOW 永久排除**：这两个 `FlowControlPolicy` 枚举值是 Flink Netty credit-based flow control 的产物，在 nop-stream 的设计下**永远不需要**（进程内走 BLOCKING_QUEUE + IBufferPool，跨 JVM 走 IMessageService 后端）。Stage 28 将其从枚举中移除并清理所有引用（测试 / javadoc / 注释），闭合 Hollow gap。

### 作业终止模式

| 模式 | 语义 | 适用场景 |
|---|---|---|
| `CANCEL` | 尽快停止，可 abort non-durable work，不保证输出完整 | 强制停止、开发调试 |
| `DRAIN` | Source truncate 成有限 work，terminal epoch durable 后结束，保证已处理数据的 exactly-once | 优雅关闭、版本升级 |
| `SUSPEND` | 停止新输入，导出可恢复 savepoint，不要求 sink final commit | 暂停作业、状态迁移 |
| `EXPORT_SAVEPOINT` | 生成 protected checkpointNamespace 的 savepoint，不停止作业 | 定期备份、状态快照 |

### Coordinator Leader Election / HA（G24, G25, Stage 38）

`JobCoordinator` 是逻辑单点。Stage 38 起，控制面经平台 `ILeaderElector`（生产部署用 `SysDaoLeaderElector`，JDBC lease 后端，零 ZooKeeper 依赖）实现 leader-gated HA 生命周期。**本节为 Stage 38 + Stage 39 fencing 统一落地状态**（接口编程 + 部署期 bean 注入 + fencing token String→long epoch 统一已落地；跨 JVM 控制面 RPC 远程化属 Stage 39 Phase 2）。

**HA 生命周期状态机**：

| 状态 | 进入条件 | 控制面行为 |
|---|---|---|
| **STANDBY**（初态，HA 模式） | `start()` 注册 `ILeaderElectionListener` 后立即返回 | `assignTasks`/`triggerCheckpoint`/`collectAck`/`reportTaskStatus`/`reportNodeTaskLiveness` 全部显式拒绝（warn 日志，**不静默 no-op**，闭合 #24） |
| **ACTIVE**（HA 模式） | `becomeLeader(LeaderEpoch)` 回调 | 控制面就绪，派生 fencing token，重建工作集 |
| **ACTIVE**（非 HA 模式） | `start()`（elector == null） | 等价于既有单实例行为（随机 UUID + 立即 active），零回归 |

**关键不变量**：

- `whenElectionCompleted()` **禁止用作 ACTIVE 触发条件**——它仅表示「本轮选举有结果」，结果可能是别的节点当选。`AbstractLeaderElector.onElectionCompleted` 在 follower 路径也 complete，若用它作为 ACTIVE 条件，follower 会误入 ACTIVE 破坏不变量 #8。ACTIVE 转换**只**由 `becomeLeader` 回调驱动。
- **deactivate ≠ stop**：leadership-loss (`becomeFollower`) 翻转 `active = false`，但**不**调用 `stop()`——`stop()` 内的 `failureDetector.shutdownNow()` + `checkpointCoordinator.shutdown()` 不可逆。standby 保留 detector / listener 以便重新当选。只有作业真正终止（CANCEL/FAIL/DRAIN/SUSPEND）才调 `stop()`。
- **null epoch 安全降级**：`SysDaoLeaderElector` 异常/续期失败路径会传 `becomeFollower(null)`，`onStop` 默认也调 `becomeFollower(null)`——null epoch 按 STANDBY 安全降级，不改变动作。
- **接线验证**：`addElectionListener` 必须在运行时确实被调用并驱动状态转换（测试 elector grant/revoke → coordinator 收到回调 → 状态翻转），非 stub。

**单调 long fencing epoch（Stage 39 已落地，闭合 M6）**：

Stage 39 把原复合 String fencing token（`leaderId@epoch#recoveryGen`）统一为单一单调 `long` epoch。编码方案（`JobCoordinator.deriveHaFencingEpoch`）：

```
fencing_epoch = leaderEpochValue * EPOCH_SCALE + recoveryGen        // EPOCH_SCALE = 1_000_000
```

| 分量 | 何时变化 | 用途 |
|---|---|---|
| `leaderEpochValue`（leadership epoch 组件） | 仅 leadership 切换时（elector grant 新 `LeaderEpoch`） | fencing 旧 leader 的 stale control（不变量 #8） |
| `recoveryGen`（recovery generation 组件） | 每次 `globalRecovery()` 递增（同一 leader 内的作业重启） | fencing 同一 leader 内上一轮 recovery 的 stale task |

**两不变量证明（单一 long 比较同时成立）**：

- **stale-leader 拒绝**：leadership 切换使 `leaderEpochValue` 单调递增（平台 `ILeaderElector` 保证 cluster-wide 单调）。新 leader 的 epoch = `newLeaderEpoch * EPOCH_SCALE` 严格大于旧 leader 经任意次 recovery 后的最大 epoch（`oldLeaderEpoch * EPOCH_SCALE + (EPOCH_SCALE-1)`），因 `newLeaderEpoch > oldLeaderEpoch`。故旧 leader 的 stale control 被单一 long 比较拒绝。
- **同 leader 上一轮 recovery 拒绝**：同 leader 内 `globalRecovery()` 仅递增 `recoveryGen`（`< EPOCH_SCALE`），epoch 严格单调递增，故上一轮 recovery 的 stale task 被拒绝。

- 非 HA 模式（elector == null）`leaderEpochValue = 0`，`recoveryGen` 在 `start()` 时 seed 为 1（初始 epoch = 1，区别于 0「未初始化」哨兵），`globalRecovery()` 递增。fencing 有效（零回归）。
- `globalRecovery()` 在 HA/非 HA 模式均轮转完整 long epoch 并经 `updateFencingToken(long)` 推送给所有 TaskManager（数据面 `RemoteInputChannel`/`RemoteResultPartition` 改为单一 long epoch 比较）。

**Decision 2（持久化边界）**：`ClusterRegistry` 接口签名改为 `long fencingEpoch`；`JdbcClusterRegistry` 在 SQL 边界以 `String.valueOf(long)` 单值写入既有 `fencing_token VARCHAR(255)` 列（**不迁移 DDL**，Option B），读回经 `Long.parseLong`。持久化边界 String 是 `String.valueOf(long)` 单值，非历史复合 `leaderId@epoch#recoveryGen`。

**控制面 / 数据面 fencing 调用点**：

| 调用点 | 携带 epoch 的字段 | 校验点 |
|---|---|---|
| `assignTasks()` | `TaskAssignment.fencingEpoch` | TaskManager `receiveAssignment` |
| `triggerCheckpoint()` | `CheckpointBarrierSignal.fencingEpoch` | TaskManager `triggerCheckpoint` |
| `collectAck()` | `CheckpointAckMessage.fencingEpoch` | JobCoordinator（epoch 等值校验） |
| `reportTaskStatus()` | `TaskStatusReport.fencingEpoch` | JobCoordinator |
| 数据面 envelope | `StreamMessageEnvelope.epochId`（单一 long，Stage 39 收敛双键） | `RemoteInputChannel` / `RemoteResultPartition`（单一 long epoch 比较） |

**部署形态**：

| 面 | 选举 / 协调后端 | Stage |
|---|---|---|
| 控制面（coordinator） | 平台 `ILeaderElector`（生产 `SysDaoLeaderElector` JDBC lease） | Stage 38 已 WIRE |
| 数据面（task 间消息） | 平台 `IMessageService`（`SysDaoMessageService` DB / `PulsarMessageService`） | Stage 40 已 WIRE（DB + Pulsar 两种后端，经 `IDataPlaneWireCodec` 适配真实后端） |
| 跨 JVM 控制面 RPC | `IStreamTaskRpcService`（task 侧）+ `IStreamCoordinatorRpcService`（coordinator 侧）经 `MessageRpcServer` 远程暴露 | Stage 39 Phase 2 已 WIRE |

### 跨 JVM 控制面 RPC 接线拓扑（Stage 39 Phase 2 已落地）

控制面控制调用经平台 RPC 框架（`MessageRpcServer` over `IMessageService` + `RpcServiceProxyFactoryBean`/`MessageRpcClient`）跨 JVM 传输，而非直接 Java 引用。

**接线拓扑（Phase 2 Decision 1）**：

- **task 侧**：每个 TaskManager 节点在 topic `nop-stream.rpc.task.{nodeId}` 上经 `StreamControlRpcServer`（= `MessageRpcServer` + `ReflectiveRpcService`）暴露 `IStreamTaskRpcService`。
- **coordinator 侧**：coordinator 在 topic `nop-stream.rpc.coordinator.{jobId}` 上暴露 `IStreamCoordinatorRpcService`（task→coordinator 上行）。
- **per-nodeId 远程 proxy map**：coordinator 持有 `Map<String, IStreamTaskRpcService>`，每个值为 `StreamControlRpcProxyFactory`（= `RpcServiceProxyFactoryBean` + `MessageRpcClient` + `RpcChannelState`）构建的 RPC 代理，替代 `EmbeddedDistributedExecutor` 直接 `taskRpcServices` map 注入（`EmbeddedDistributedExecutor.java:150-153`）。task 侧同理持有一个 coordinator 的 RPC 代理（`tm.setCoordinatorRpcService(proxy)`）。
- **RPC server 生命周期**：task 进程持有 task 侧 server（与 TaskManager 同生命周期）；coordinator 进程持有 coordinator 侧 server。
- **coordinator 长生命周期**：`RpcDistributedExecutor.startJob()` 返回 `DistributedJobHandle`（持有 coordinator + servers + proxies），`execute()` 返回不再立即 `stop()`（兑现 `IStreamExecutionDispatcher.java:34` deferred 契约「异步 submit + poll dispatcher」）。
- **与 `EmbeddedDistributedExecutor` 关系**：`EmbeddedDistributedExecutor` 保留为直接引用 fast-path（同 JVM、无 RPC 开销）；`RpcDistributedExecutor` 为分布式形态（RPC-wired 控制面）。两者都实现 `IStreamExecutionDispatcher`。

**server 选型（Phase 2 Decision 3）**：选 `MessageRpcServer`（over `IMessageService`），拒 `SimpleRpcServer`（socket）。理由：(1) 与 Stage 40 数据面 `IMessageService` 后端统一；(2) topic 寻址，免 per-node 端口分配；(3) 不引入 Flink Netty 栈（vision §三 约束 7）。

**RPC 消息适配（Phase 2）**：默认反射分发（`ReflectiveRpcService` + `DefaultRpcMessageTransformer` 按参数名映射）已足够，无需自造 `IRpcMessageAdapter`。仅有的定制是 `StreamControlRpcTransformer`（继承 `DefaultRpcMessageTransformer`）：void 控制调用标记 oneWay（fire-and-forget，coordinator 不阻塞等待 per-task 响应；接线验证经可观测副作用——计数器/状态——断言，非返回值）；request-response 调用（`getJobStatus`）补默认 timeout 与 request-id（`MessageRpcClient` 不像 `SimpleRpcClient` 自动生成）。`StreamControlRpcServer` 用 `CorrelatingRpcService` 包装器为响应补 `relId`（`MessageRpcServer` 不像 `SimpleRpcServer` 调用 `enrichResponse`）。

**IoC 接线（Phase 2 Decision 2 Option B）**：nop-stream 首个 `beans.xml`（`stream-control-rpc.beans.xml`，位于 `nop-stream-runtime` 的 `_vfs/nop/stream/beans/`）作为 Stage 42 多 JVM 部署脚手架，装配可配置 `IMessageService` bean + RPC server/proxy 接线模板。`TestStreamControlRpcBootstrap` 经 NopIoC 加载该 beans.xml，断言 transport bean 实例化 + RPC server/proxy 类（以 IoC 提供的 transport）真实承载控制调用，确保结构非空壳。E2E 程序化构造（`RpcDistributedExecutor`）与 IoC 装配共存：前者用于同 JVM RPC 验证，后者用于生产部署脚手架。

**测试基建**：

- 测试用 `TestLeaderElector`（`nop-stream-runtime` test scope，**非生产组件**）：实现 `ILeaderElector` 全部方法，提供确定性 `grantLeadership(epoch)` / `loseElectionTo(otherHost, epoch)` / `revokeLeadership()` 同步触发 listener，覆盖单进程 leader-switch E2E（两 coordinator + 测试 elector）。
- `SysDaoLeaderElector` 真实 JDBC smoke check（Stage 38 Phase 3，`nop-sys-dao` test scope）：`TestJobCoordinatorWithSysDaoLeaderElector` 用 H2 + AutoTest 把生产 `SysDaoLeaderElector` bean 经 `JobCoordinator.setLeaderElector` 注入，验证「首个生产用户集成」真实可启动、可竞选、lease 续期正常、fencing token 来自 `LeaderEpoch`（断言精确编码 `hostId@epoch#recoveryGen`）。`nop-stream-runtime` 经 test-scope 依赖反向接入 `nop-sys-dao`（生产 deps 不污染；部署期接线方向 = sys-dao bean → coordinator setter）。

**平台集成契约（Phase 3 发现并记录）**：

- **F0a 当前 leadership 不重放给新 listener**：`SysDaoLeaderElector.refreshLeader` 仅更新 lease 时间戳，不重调 `onBecomeLeader`。coordinator 端 workaround：`start()` 注册 listener 后查 `elector.isLeader()`，若已是 leader 则用当前 `LeaderEpoch` 自激活（best-effort，异常不阻塞 start）。平台层修复（`AbstractLeaderElector.registerListener` 重放当前 leadership）作为单独 follow-up。
- **F0b 启动期 `leader-epoch-mismatch` 日志噪声**：currentEpoch=-1（合法「未参与过选举」初态）与 db leaderEpoch 不匹配时打 ERROR 日志。生产运营需识别该日志为启动期良性事件。
- **F1 restartElection 粒度**：`restartElection` 把 epoch `++1` 并立即过期 lease，下一轮 `changeLeader` 再 `++1`，实际净增 2——epoch 单调（契约满足），但跳变粒度为 2。
- **F2 生产 lease 时长**：默认 `leaseMs` 偏小，生产部署需在 beans.xml 显式放大（建议 15-30s 容忍 JDBC 抖动）；nop-stream 不内嵌生产默认值。
- **F3 回调线程模型**：`SysDaoLeaderElector` 在自身 polling 线程回调 listener——`JobCoordinator` 已将 `active`/`currentLeadership` 标为 `volatile`、`recoveryGen` 为 `AtomicLong` 保证可见性。

### 跨 JVM 数据面接线拓扑（Stage 40 已落地）

数据面 record/barrier/watermark 经平台 `IMessageService` 真实后端（`SysDaoMessageService` DB / `PulsarMessageService`）跨 JVM 传输，而非 `LocalMessageService` 内存直通。

**接线拓扑（Phase 1 Decision）**：

- **后端选择是部署决策**：`nop-stream-runtime` 对 `nop-sys-dao` / `nop-message-pulsar` **无硬依赖**（vision §三 约束 8）。应用层装配具体 `IMessageService` 后端 bean 并选定匹配的 `IDataPlaneWireCodec`，经 `EmbeddedDistributedExecutor.setDataPlaneWireCodec(...)` / `RpcDistributedExecutor.setDataPlaneWireCodec(...)` 注入。`stream-data-plane.beans.xml`（`_vfs/nop/stream/beans/`，与 Stage 39 `stream-control-rpc.beans.xml` 并列）为 Stage 42 多 JVM 部署脚手架。
- **wire-format 适配（`IDataPlaneWireCodec` SPI）**：裸 `StreamMessageEnvelope` 与两种后端的序列化契约不兼容——`SysDaoMessageService` 仅忠实持久化 `ApiRequest.data`（裸 envelope 丢失 body，只留类名）；`PulsarMessageService` 默认 `Schema.STRING` 需 String 值。故：`SysDaoWireCodec` 在 send 侧把 envelope 适配为 `ApiRequest{data: envelopeMap}`（barrier/watermark payload 经 `DataPlaneWireSupport` 摊平为可序列化 Map，receive 侧 `StreamElementCodec.decode` 还原）；`PulsarStringWireCodec` 把 envelope 序列化为 JSON String；`IdentityWireCodec` 用于 `LocalMessageService`（对象引用直通）。codec 仅引用 `ApiRequest`/`JsonTool`，不 import 后端类，故不引入后端依赖。
- **`DataPlaneMessageServiceAdapter` 装饰器**：仅包装数据面视图（`RemoteGraphExecutionPlanBuilder` / `Remote*` 持有的 `IMessageService`），send 侧 `codec.toWire`、subscribe 侧 `codec.fromWire`；控制面 RPC 保持裸 `IMessageService`。两面共享同一后端实例但 topic 不相交，互不干扰。envelope fencing（单一 long epoch 过滤）在 `RemoteInputChannel` 不变，跨后端均生效。
- **与 Stage 39 beans.xml 的关系**：并列文件，共存。数据面与控制面可共享同一 `IMessageService` transport bean（topic 寻址，无冲突）。
- **与程序化构造的关系**：`EmbeddedDistributedExecutor` / `RpcDistributedExecutor` 接受 `IMessageService` + 可选 codec（默认 identity）= 同 JVM 测试 fast-path；IoC beans = 生产部署路径。二者共存（同 Stage 39 裁定）。

**Backpressure 契约（按后端拆分，Phase 2）**：

- **Pulsar**：cross-JVM producer 回压由 Pulsar producer pending-message 队列饱和承担（producer 感知并阻塞/降速）。
- **SysDaoMessageService（DB）**：**不提供 producer 回压**——DB 写入无界（磁盘满才失败且抛异常，非 flow control），polling 仅决定 consumer 消费速率。改为验证「record 经 `NopSysEvent` 表持久化中转」（Phase 1 Proof）。设计措辞「SysDao 天然有界」已修正为「= 磁盘容量上限，非 flow control」。
- 两后端均**无自建 credit-based / ACK_WINDOW**（vision §三 约束 7 永久排除；Stage 28 已从 `FlowControlPolicy` 枚举移除）。

### 跨 JVM 任务部署（Stage 42 Phase 0 已落地）

Stage 42 Phase 0 解决了多 JVM 部署的核心阻塞：原 `RpcDistributedExecutor.installInvokablesAndRun()` 把在 coordinator JVM 内通过 `RemoteGraphExecutionPlanBuilder` 构造的 `StreamTaskInvokable`（非序列化、含 `OperatorChain`/`RecordWriter`/`InputGate`）通过直接 Java 方法调用塞给 TaskManager —— 这只在同 JVM 成立。Phase 0 引入 **remote-deploy 模式**：TaskManager 在本地从可序列化的模型元数据重建自己的 invokable，与「图模型为核」（vision §三 约束 1）一致。

**Remote-deploy 模式（additive，零回归）**：

- **可序列化部署描述符**：`TaskDeploymentDescriptor` 携带 `{jobId, vertexId, subtaskIndex, nodeId, attemptId, attemptNumber, fencingEpoch, JobGraph, DeploymentPlan, checkpointRestorePath}` —— **仅模型元数据，无 live operator 对象**。所有 TaskManager JVM 共享同一 classpath（同 JARs），各自从 `JobGraph`（`Serializable`，携带 per-vertex `OperatorChain` 模板）+ `DeploymentPlan` 本地重建。`StreamComponents` 注册表（`Serializable`，`StreamComponents.java:41`）确保算子可重建。
- **新 RPC 方法 `deployTask`**：`IStreamTaskRpcService.deployTask(descriptor, fencingEpoch)` 声明为 **`default` 方法**，默认抛 `UnsupportedOperationException`。约 12 个 in-process 测试 double 无需任何改动即可编译。生产实现位于 `TaskManager.deployTask`。
- **本地重建路径**：`SubtaskPlanBuilder.buildSubtaskInvokable(descriptor)` 在 TaskManager JVM 内调 `RemoteGraphExecutionPlanBuilder.buildRemoteOnly(jobGraph, deploymentPlan, true)`，用本 TaskManager 自己的 `IMessageService` 实例接同一后端 → 派生出 **与 coordinator 视图相同**的数据面 topic 名（确定性 topic 命名 `StreamTopicNaming.buildTopic`），再抽出分配给本节点的 subtask，本地安装 invokable。避免了 (a) 复制复杂 edge-wiring/InputGate/RecordWriter 构造逻辑，(b) 序列化 live runtime 对象。
- **`deployTask` vs `receiveAssignment` 语义**：descriptor 自包含 `TaskAssignment` 元数据，**remote-deploy 模式下 `receiveAssignment` 不再单独调用**。`JobCoordinator.assignTasks()` 在 `remoteDeployMode=true` 时调 `rpc.deployTask(descriptor, epoch)`；in-process 模式继续 `rpc.receiveAssignment(taskAssignment)` + 直接 `installInvokable`。两路径互斥，由 `JobCoordinator.remoteDeployMode` 与 `RpcDistributedExecutor.remoteDeployMode` 标志切换。
- **Recovery 继承同一模式**：`globalRecovery()` → `rotateFencingEpochAndRestore()` → `assignTasks()` 自动按当前模式重新部署。recovery descriptor 携带 rotated fencing epoch + checkpoint restore path + 递增的 attemptNumber，使 replacement TaskManager 能从共享 `LocalFileCheckpointStorage` 路径恢复状态。原 `coordinator.setAutoRecoverOnFailedReport(false)` 之所以禁用，正是因为 in-process 路径无法在 recovery 后 redeploy task logic —— remote-deploy 模式闭合了这个 gap。
- **无静默跳过（plan guide #24）**：`TaskManager.deployTask` 在 fencing mismatch / target-node mismatch / 容量耗尽 / JobGraph 缺失 / invokable 构造失败 时抛 `StreamException` **并** 向 coordinator 上报 FAILED `TaskStatusReport`（`deployTask` RPC 为 one-way，throw 不传播回 coordinator，必须显式上报才能触发 recovery）。
- **`JobCoordinator.remoteDeployMode` 注入**：`setRemoteDeployMode(true)` + `setJobGraph(jobGraph)` + `setCheckpointStoragePath(path)`。`assignTasks()` 在 `remoteDeployMode=true` 但 `jobGraph==null` 时 fail-fast（plan guide #24）。

**接线验证（plan guide #23，anti-hollow）**：

- `TestRpcDistributedExecutorRemoteDeployE2E` —— 通过 `RpcDistributedExecutor(remoteDeployMode=true)` 跑 source→map→sink 全链路：coordinator 的 `assignTasks()` 真实构造 descriptor 并经 RPC `deployTask` 发给（in-process RPC-reached）task 节点；每个 TaskManager 真实在本地重建 invokable 并跑 —— sink 收齐预期 record 仅在 deployTask 真实生效时成立。
- `TestJobCoordinatorRemoteDeploy` —— 接线断言：remote-deploy 模式下 `assignTasks()` 调 `deployTask`（不调 `receiveAssignment`），recovery 路径重发 `deployTask` 并携带 rotated epoch + checkpoint path，in-process 模式保持 `receiveAssignment` 路径不变，`remoteDeployMode=true` 但缺 `jobGraph` fail-fast。
- `TestTaskDeploymentDescriptor` —— Java 序列化 round-trip 验证（模型元数据保真，非 live operator）。

**测试基建**：

- DB（Phase 1）：`TestDataPlaneSysDaoBackendE2E`（`nop-sys-dao` test scope，遵循 Stage 38 elector smoke check 模式，避免循环 test 依赖）用 H2 + AutoTest 把生产 `SysDaoMessageService` 经 `DataPlaneMessageServiceAdapter` + `SysDaoWireCodec` 注入 `Remote*`，断言 record 经 `NopSysEvent` 表中转（查表断言）+ fencing 过滤 + exactly-once + barrier/watermark/EOS 传播。
- Pulsar（Phase 2）：`TestDataPlanePulsarBackendE2E`（`nop-stream-runtime` test scope）经 `@EnabledIfSystemProperty("nop.stream.test.pulsar.enabled")` 门禁 + CI 提供的 broker 实例（`nop.stream.test.pulsar.serviceUrl`）跑真实 Pulsar 跨 JVM E2E。codec 逻辑由 `TestSysDaoWireCodec` / `TestPulsarStringWireCodec` round-trip 单测钉住（无需后端，始终跑）。模块不内嵌 testcontainers / pulsar-broker（依赖重，Pulsar 2.8.0），broker 由 CI 提供。

### JobStatus + Restart Strategy（G56）

| 状态 | 含义 | 触发 |
|---|---|---|
| `CREATED` | JobCoordinator 构造后、start 前 | 构造器 |
| `RUNNING` | 正常运行 | `start()` |
| `FAILED` | 终态：global restart 上限耗尽 | `failJob(cause)` —— 仅 `globalRecovery()` 内部当 `restartCount > maxRestarts` 触发 |
| `CANCELED` | 终态：用户主动 CANCEL | `terminate(CANCEL)` 路径 —— Stage 28 起 `terminateCancel()` 显式设 `jobStatus = CANCELED` 后再 `stop()` |

- 重启计数器 **仅 `globalRecovery()` 递增**；默认上限 `maxRestarts=3`（可配）。Stage 27 已正式裁定 targeted/scoped 重启在当前架构下 **NO-GO**（全 pipelined → 单 region，drain/reconnect 不可设计），详见 `failover-design.md`。per-region 计数器随 G57/G28 一起 deferred → Stage 44（需 blocking edge + supervision loop 前置）。
- `JobStatus.FAILED` 后 `assignTasks()` 显式拒绝（#24 — 不静默跳过）。
- cancel 规范化（G58）：`Task.cancel` / `SubtaskTask.cancel` / `RunningTask.cancel` 统一经 `CANCELING` 中间态；分布式 `RunningTask.cancel()` 在 `future.cancel(true)` 之前先调用 `invokable.getMailboxExecutor().signalCancel()`（与 Stage 17 mailbox cooperative cancel 对齐）；`RunningTask.cancel()` null-check `invokable` 处理 cancel-before-invokable 竞态。

### 处理保证

| 模式 | Barrier 行为 | 恢复后行为 |
|---|---|---|
| `STRICT_EXACTLY_ONCE` | 已收到 barrier 的 channel 阻塞 barrier 后 records，等待所有 channel 对齐 | 从 durable epoch 重放，不重复副作用 |
| `AT_LEAST_ONCE` | 继续处理已收到 barrier channel 的 barrier 后 records | 从 durable epoch 重放，可能重复处理 |
| `EFFECTIVELY_ONCE` | 数据处理层按 exactly-once 或 at-least-once 执行，外部效果依赖幂等/upsert | sink 不需要严格 2PC |
| `BEST_EFFORT` | 可禁用 checkpoint | 不保证状态一致性 |

## 六、数据流模型

### Transformation DAG

用户通过 DataStream API 构建的程序，内部维护一个 Transformation DAG：

```
SourceTransformation<T>
    ↓
OneInputTransformation<T, R>  (map/filter/flatMap/window)
    ↓
PartitionTransformation<T>    (keyBy)
    ↓
OneInputTransformation<...>   (window operator)
    ↓
SinkTransformation<T>
```

### 流类型层次

```
DataStream<T>
  └── KeyedStream<T, KEY>           (增加了 keyBy 语义)
        └── WindowedStream<T, K, W>  (增加了 window 语义)
```

### 算子链（Operator Chain）

算子通过 Output 接口串联：

```
StreamSource → ChainingOutput → StreamMap → ChainingOutput → WindowOperator → ChainingOutput → StreamSink
```

链内通过 `ChainingOutput` 直接调用，跨链通过 `RecordWriter/InputGate` 传递。算子链融合在 JobGraphGenerator 阶段完成。

### 分区策略

每条边必须在 `PartitionedPlan` 中记录 partition policy：

| Policy | 语义 | 使用场景 |
|---|---|---|
| `FORWARD` | 上下游 subtask 一一对应 | chain 边界、同并行度直连 |
| `HASH` | 按 key hash 到 state shard，再映射到 owner subtask | keyBy、keyed window、keyed CEP |
| `REBALANCE` | round-robin 或负载均衡分发 | 无 key 的并行扩散 |
| `BROADCAST` | 复制到所有下游 subtask | 配置流、规则流 |
| `UNION` | 多上游合并到下游输入集合 | 多 source 合并 |
| `SINGLETON` | 所有数据汇聚到 subtask 0 | 全局 sink、全局聚合 |

### 进程内背压（Stage 28 G27 裁定）

进程内 edge 通过 `EdgeConfig` 配置 flow control。Stage 28 起 `FlowControlPolicy` 仅保留 `BLOCKING_QUEUE`（CREDIT_BASED / ACK_WINDOW 永久排除，见 §五 G27 裁定）：

| 策略 | 行为 | 适用场景 |
|---|---|---|
| `BLOCKING_QUEUE` | 队列满时阻塞 sender | 进程内（per-partition `ResultPartition` 队列 + per-job `IBufferPool` 全局配额，见 §六「进程内缓冲池」） |

跨 JVM 背压由 `IMessageService` 后端提供（Stage 40），不在 `FlowControlPolicy` 枚举中建模。

### 统一数据通道

所有在算子间传输的数据单元都是 `StreamElement` 的子类：

| 类型 | 用途 | 传输方式 |
|------|------|----------|
| `StreamRecord<T>` | 业务数据 | 标准 RecordWriter → ResultPartition → InputChannel → RecordReader |
| `CheckpointBarrier` | checkpoint 同步信号 | 同 StreamRecord 走同一通道，作为普通 StreamElement 排队传输 |
| `Watermark` | 事件时间推进信号 | 同上 |
| `WatermarkStatus` | 空闲/活跃状态标记 | 同上 |

三者通过统一的 `RecordWriter → ResultPartition → InputChannel → RecordReader` 管线传输。`ResultPartition` 可以是：

- **本地模式**：`BlockingQueue`（同进程内 Task 间传递）
- **分布式模式**：`RemoteResultPartition`（基于 `IMessageService` 跨进程传输）

算子层不感知 `ResultPartition` 的实现方式。Barrier 不需要独立 RPC 通道。

### 进程内缓冲池（IBufferPool，G53）

进程内 per-partition backpressure **已存在**（`ResultPartition.queue.put()` 满时阻塞生产者）。为治理**跨多 partition 的全局聚合内存上限**（防止 fan-out 场景下 N 个 partition 各占 1024 导致 OOM），引入 `IBufferPool` SPI：

| 维度 | 决策 |
|---|---|
| **单位** | 元素计数制（与 `ResultPartition` 队列、`EdgeConfig.queueCapacity` 单位一致）。**不与 `MemoryBudget.networkBuffers`（字节制）换算**——单位不可比；字节制治理留给 off-heap/RocksDB 工作（Stage 30+）。 |
| **基数** | **per-job 单实例**。一次 `execute()` 内所有 partition 共享同一 pool，跨 partition 全局聚合上限才可观测。 |
| **注入路径** | 经 `GraphExecutionPlan.build(...)` 工厂传入。旧 build() 重载内部默认创建一个 per-build pool（向后兼容、不强制迁移调用点/测试）；新重载显式接收 pool 参数。 |
| **ResultPartition 构造** | 保留现有 2 构造向后兼容（pool=null 表示仅 per-partition 有界队列=当前行为，测试/Remote 子类不改）；新增 pool 感知构造供生产 build() 路径使用。pool 非 null 时 write 先向 pool 申请配额（满时阻塞=全局 backpressure），再入队（满时阻塞=per-partition backpressure）；read 归还配额。 |
| **耗尽契约** | pool 耗尽时**阻塞**申请方（与 `queue.put()` 阻塞语义一致），**不抛 RuntimeException**。阻塞可被 cancel/interrupt/close 唤醒，不永久死锁。 |
| **公平性** | Memory 实现用**公平** Semaphore（FIFO），防止单一高速 partition 独占配额饿死其它 partition。 |
| **`EdgeConfig.queueCapacity`** | 接线到 per-partition 容量（`GraphExecutionPlan` 创建 partition 时传入），不再恒用 1024 默认值。 |
| **Remote 排除** | `RemoteResultPartition` / `RemoteInputChannel` **有意不消费 pool**（前者 override write() 直接走 `IMessageService`，后者自带本地队列）。跨 JVM 生产端 bound 属 Stage 40 `IMessageService` 后端，非遗漏。 |
| **`IWriteStatus` 语义** | `isBackpressured`/`getAvailableCapacity`/`getTotalCapacity` 保持 **per-partition 私有队列口径**（不变）；pool 另外暴露独立的**全局聚合计量**（`IBufferPool.getGlobalUsage()` 等），二者不混用。 |
| **生命周期** | `GraphExecutionPlan` 持有 pool 引用；`execute()` 结束/异常时关闭 pool（唤醒被全局耗尽阻塞的生产者）。checkpoint 恢复重建 plan 时**创建新 pool**（不复用失败 attempt 的 pool，避免 leaked permits 饿死恢复）。 |

## 七、与 Nop 平台的集成

| 集成点 | 方式 | 模块 |
|--------|------|------|
| 数据库访问 | `IJdbcTemplate` + `IDialect`（多数据库适配） | runtime (JdbcCheckpointStorage) |
| 批量数据源 | `IBatchLoader` / `IBatchConsumer` 桥接 | connector |
| 消息队列 | `IMessageService` 桥接 | connector |
| CDC 数据源 | `DebeziumCdcSourceFunction` 桥接 | connector |
| CEP 条件表达式 | `IEvalFunction` (nop-xlang) | cep |
| 序列化 | `JsonTool`（状态快照 metadata） | core/runtime |
| 错误处理 | `NopException` + `ErrorCode` | 所有模块 |
| 声明式编排 | XDSL + Delta 定制 StreamModel | flow |

### 序列化策略

- **metadata**：plan、manifest、state segment descriptor 必须 JSON round-trip
- **payload**：默认使用 `JsonTool`，允许通过状态后端声明可替换 payload codec
- **约束**：每个 state name 必须记录 value schema version 和 checksum

## 八、与其他流处理引擎的架构对比

### 8.1 核心架构差异

| 维度 | Flink | SeaTunnel (Zeta) | nop-stream |
|------|-------|-------------------|------------|
| **图模型层级** | 3 层（StreamGraph → JobGraph → ExecutionGraph） | 4 层（Action DAG → LogicalDag → ExecutionPlan → PhysicalPlan） | 2 层（StreamGraph → JobGraph）+ PartitionedPlan/DeploymentPlan |
| **调度模型** | JobManager（全局调度）+ TaskManager（多机多进程）+ Slot 资源管理 | JobMaster（Pipeline 调度）+ Hazelcast 分布式协程 | LOCAL: TaskExecutor 线程池；DISTRIBUTED: IStreamExecutionDispatcher SPI |
| **算子链化** | ChainingStrategy（ALWAYS/NEVER/HEAD）+ 6 条件 | TransformChainAction（自动链化相邻 Transform） | 6 条件自动判定 |
| **Task 执行** | 每个 Task 独立线程 + Mailbox 事件循环 | 多个 Task 共享 TaskGroup 线程（协程式） | 每个 Task 独立线程 |
| **RPC** | Akka/RPC 抽象 | Hazelcast Operations | IStreamTaskRpcService 强类型接口 |
| **数据交换** | Netty + MemorySegments + NetworkBufferPool | Hazelcast IntermediateQueue | LOCAL: BlockingQueue；DISTRIBUTED: IMessageService |
| **分布式 HA** | ZooKeeper + Standalone HA / K8s | Hazelcast IMap 自动恢复 | 已落地（Stage 38）：`ILeaderElector` WIRE 进 `JobCoordinator` + 平台 `SysDaoLeaderElector`（JDBC lease，零 ZooKeeper 依赖）+ composite fencing token |

### 8.2 Flink Translator 模式参考

Flink 的 `StreamGraphGenerator` 使用 **Translator 模式**（策略模式）将 `Transformation` 子类映射到 `TransformationTranslator`：

```java
// Flink 模式：每个 Transformation 子类有对应 Translator
translatorMap.put(OneInputTransformation.class, new OneInputTransformationTranslator<>());
translatorMap.put(SourceTransformation.class, new SourceTransformationTranslator<>());
translatorMap.put(SinkTransformation.class, new SinkTransformationTranslator<>());
translatorMap.put(PartitionTransformation.class, new PartitionTransformationTranslator<>());
```

nop-stream 当前在 `StreamGraphGenerator` 中直接用 `instanceof` 分支处理。如果未来新增 Transformation 类型（如 `UnionTransformation`、`SideOutputTransformation`），建议迁移到 Translator 模式。

### 8.3 SeaTunnel Pipeline 隔离参考

SeaTunnel 的 `ExecutionPlan` 按 shuffle 边界切分为多个 `Pipeline`，每个 Pipeline：
- 独立触发 checkpoint
- 独立故障恢复
- 独立调度

nop-stream 当前按 JobGraph 整体调度和 checkpoint。如果未来需要 Pipeline 级别隔离（如多 source 作业中一个 source 失败不影响其他 source），可参考 SeaTunnel 的 Pipeline 模型。设计要点：
- Pipeline 边界 = shuffle 边（keyBy、rebalance 等需要跨 channel 传递的边）
- 每个 Pipeline 有独立的 CheckpointPlan 和 CheckpointCoordinator
- Pipeline 间的数据交换通过中间队列或消息服务

## 九、与已有设计的关系
