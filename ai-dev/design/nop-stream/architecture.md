# nop-stream 整体架构

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-24

## 1. 定位与设计目标

nop-stream 是 Nop 平台的流处理引擎，定位为**可分布式执行的流处理内核**。

设计目标是以 Nop 平台的模型驱动和可逆计算思想表达分布式流处理的不变量，提供确定性分区、可恢复状态、epoch checkpoint、source/sink 协议化 exactly-once，以及可由 Java API、XDSL 和 Delta 共同驱动的执行计划。

**必须达成的能力**：

| 能力 | 设计要求 |
|---|---|
| 分布式执行 | 一个作业可拆分为多个 task fragment，部署到多个 runtime node 上执行 |
| 端到端 exactly-once | source offset、operator state、sink transaction 必须绑定到同一个 checkpoint epoch |
| 确定性分区 | key、state shard、subtask、edge channel 的映射必须由模型决定，不能由运行时对象顺序推导 |
| 稳定状态路由 | 恢复时必须按 `operatorId + subtaskIndex + stateShard + stateName` 路由状态 |
| 可声明和可定制 | Java API 和 XDSL 都必须落到同一套 canonical StreamModel，Delta 只作用于模型层 |
| 可替换后端 | 本地线程、远程进程、未来外部引擎适配都必须遵守同一语义契约 |

**核心取舍**：

- **保留**：Flink DataStream API 概念、流处理语义、Barrier 快照、算子链化、多 Task 并行执行
- **去除**：复杂 Join（双流 Join / interval join / broadcast join）、广播流、异步算子、完整 key-group 重分布
- **聚焦**：单流窗口聚合 + CEP 模式匹配 + Checkpoint 容错

**明确不做的**：

- 双流 Join（复杂度极高，用例有限，可通过 CEP 或外部 lookup 替代）
- SQL API（Nop 平台已有 GraphQL，流式 SQL 需求不迫切）
- 大规模并行（目标场景是中等规模 ETL，不是 PB 级流处理）
- 复制 Flink Runtime 结构（不引入 SlotSharingGroup、Netty 网络栈、二进制序列化体系）

## 2. 模块划分

```
nop-stream/
├── nop-stream-api          [规划] 用户可见 API、function contract、source/sink 一致性能力声明
├── nop-stream-core         [实现] StreamModel、StreamComponents、图模型、PartitionedPlan、DeploymentPlan
├── nop-stream-runtime      [实现] RuntimeTopology、task 执行、transport backend、fencing、node lifecycle
├── nop-stream-checkpoint   [规划] Epoch coordinator、manifest、state segment、checkpoint/savepoint storage
├── nop-stream-connector    [实现] 连接器适配层：replayable source、transactional sink、SourceWorkUnit
├── nop-stream-cep          [实现] Pattern/NFA/SharedBuffer、CEP operator（接入统一状态后端）
├── nop-stream-flow         [规划] XDSL StreamModel 编排，支持 Delta 定制
├── nop-stream-flink        [规划] 可选外部后端适配（不作为内核设计来源）
└── nop-stream-fraud-example[实现] 端到端欺诈检测示例
```

### 2.1 模块职责边界

| 模块 | 职责 | 依赖方向 |
|------|------|----------|
| **nop-stream-api** | 用户可见 API、SourceFunction/SinkFunction contract、一致性能力枚举、公共模型接口 | 无依赖 |
| **nop-stream-core** | StreamModel + StreamComponents、StreamGraph/JobGraph、PartitionedPlan/DeploymentPlan、优化和校验、StreamRequirement 校验 | → api |
| **nop-stream-runtime** | RuntimeTopology、本地/分布式 task 执行、transport backend（local queue / remote RPC / message bus）、fencing、node lifecycle、EdgeConfig flow control | → core |
| **nop-stream-checkpoint** | Epoch coordinator、manifest 生成与发布、state segment descriptor、checkpoint/savepoint storage contract、CheckpointParticipant 调度 | → core |
| **nop-stream-connector** | Replayable source（SourceWorkUnit + RestrictionTracker）、transactional/idempotent sink（CheckpointParticipant）、split/offset 协议适配 | → core |
| **nop-stream-cep** | Pattern DSL、NFA 编译、SharedBuffer、CepOperator（通过标准 state/timer 接口接入统一后端）、声明式模型（pattern.xdef） | → core |
| **nop-stream-flow** | XDSL StreamModel 编排、Delta 定制支持 | → core |
| **nop-stream-flink** | 可选外部后端适配，将 core API 的 Transformation 映射到 Flink DataStream API | → core |

### 2.2 依赖方向

依赖只能从右向左：运行时和集成模块依赖 core，core 依赖 api，api 不依赖任何实现模块。

```
runtime / checkpoint / connector / cep / flow  →  core  →  api
```

关键约束：

| 规则 | 说明 |
|---|---|
| core 不依赖 runtime | core 只定义模型和编译结果 |
| checkpoint 不依赖具体 transport | checkpoint 通过 plan 和 task identity 工作 |
| connector 不依赖具体 runtime | connector 声明 source/sink 能力和状态协议 |
| cep 不依赖 runtime checkpoint 实现 | CEP operator 通过标准 state/timer 接口接入 |

## 3. 分层设计

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

## 4. 执行模型

### 4.1 五层执行管线

```
StreamModel
    → StreamGraph
    → JobGraph
    → PartitionedPlan
    → DeploymentPlan
    → RuntimeTopology
```

| 层 | 职责 | 是否持久化 |
|---|---|---|
| `StreamModel` | 用户意图的规范模型，包含 StreamComponents registry 和 Transformation DAG。来自 Java API、XDSL 或 Delta 合成 | 是 |
| `StreamGraph` | 逻辑 DAG，表达 source、operator、sink 和边语义 | 可持久化 |
| `JobGraph` | 算子链化和逻辑优化后的作业图 | 可持久化 |
| `PartitionedPlan` | 并行展开、state shard、subtask、edge channel、partition policy、checkpoint route 的语义计划。是分布式 exactly-once 的中心模型 | 必须持久化 |
| `DeploymentPlan` | 将 partitioned task 映射到 runtime node、transport backend、state backend binding、checkpoint storage、EdgeConfig flow control、memory budget | 必须持久化 |
| `RuntimeTopology` | 运行时实例视图：attempt、心跳、通道状态、checkpoint 进度。可重建，不允许反向生成状态路径或分区规则 | 可重建 |

**关键决策**：`PartitionedPlan` 承载并行度、分区、状态路由和 checkpoint ACK 集合。运行时只能执行它，不能重新发明拓扑语义。本地线程执行只是 `DeploymentPlan` 的一种 backend，分布式语义不能依赖本地线程模型。

### 4.2 PartitionedPlan 必须记录的信息

| 信息 | 原因 |
|---|---|
| 每个 vertex 的 parallelism | 运行时必须创建所有 subtask |
| 每个 operator 的稳定 operatorId | 状态恢复不能依赖链内 index 或对象顺序 |
| 每个 key 分配到哪个 state shard | keyed state 必须可以跨节点定位和恢复 |
| 每个 state shard 由哪个 subtask 拥有 | failure/restart/rescale 需要确定状态归属 |
| 每条边的 partition policy（FORWARD / HASH / REBALANCE / BROADCAST / UNION / SINGLETON） | keyBy、forward、broadcast、rebalance 必须进入执行计划 |
| 每个 subtask 的输入/输出 channel | barrier 对齐和数据传输需要通道级身份 |
| checkpoint ACK 集合 | Coordinator 必须知道哪些 task 必须 ACK |

### 4.3 StreamComponents 与 PartitionedPlan 的关系

`PartitionedPlan` 不直接引用 `StreamComponents`，但 `Epoch Manifest` 必须包含 `StreamComponents` 的 fingerprint 和 requirement 列表。这保证恢复时的模型一致性和 backend 能力校验。

### 4.4 执行流程

```
execute(jobName)
   ├── 收集所有 SinkTransformation
   ├── 构建 StreamModel（含 StreamComponents）
   ├── StreamGraphGenerator.generate() → StreamGraph
   ├── JobGraphGenerator.generate() → JobGraph（算子链融合优化）
   ├── PartitionedPlanGenerator.generate() → PartitionedPlan
   ├── DeploymentPlanGenerator.generate() → DeploymentPlan
   └── 根据 DeploymentMode 分发执行
        ├── LOCAL → GraphExecutionPlan → TaskExecutor
        │     └── StreamTaskInvokable
        │          ├── wire operators（ChainingOutput 串联）
        │          ├── open() 所有算子
        │          ├── source.run() 推数据
        │          ├── MAX_WATERMARK 触发最终窗口计算
        │          └── close() 所有算子
        └── DISTRIBUTED → IStreamExecutionDispatcher.execute()
             └── EmbeddedDistributedExecutor
                  ├── 创建 JobCoordinator（持有 canonical plan）
                  ├── 创建 N 个 TaskManager（各自持有 TaskExecutor）
                  ├── 将 TaskManager 注册为 IStreamTaskRpcService
                  ├── RemoteGraphExecutionPlanBuilder 构建跨节点执行计划
                  └── JobCoordinator 分发 subtask 到各 TaskManager
```

`DeploymentMode` 枚举（`LOCAL` / `DISTRIBUTED`）定义在 core 模块。`IStreamExecutionDispatcher` SPI 接口由 runtime 模块实现，`StreamExecutionEnvironment.execute()` 通过 `executionDispatcher` 字段路由到正确的执行器。

### 4.5 CEP 独立执行路径

CEP 模块可以直接使用，不依赖 `StreamExecutionEnvironment`：

```java
Pattern<T, ?> pattern = Pattern.<Transaction>begin("start")
    .where(new SimpleCondition<>("amount > 1000", ...))
    .timesOrMore(2)
    .within(Time.seconds(30));

NFA<Transaction> nfa = NFACompiler.compile(pattern, ...);
SharedBuffer<Transaction> buffer = new SharedBuffer<>(stateStore, ...);
Collection<Map<String, List<Transaction>>> matches = nfa.process(event, timestamp);
```

独立 API 保留用于单线程内存模式。分布式 CEP operator 必须接入统一 state/timer 后端。

## 5. 分布式控制面契约

nop-stream 需要最小分布式控制面契约，保证 `DeploymentPlan` 能被可靠下发、执行、监控和恢复。不复制 Flink 的控制面结构，但 task attempt、lease、assignment、fencing 这些分布式正确性概念不能省略。

### 5.0 三面架构

分布式执行采用三面分离架构：

| 面 | 职责 | 传输方式 |
|---|---|---|
| **控制面** | 作业调度、task 分配、cancel、状态查询 | `IStreamTaskRpcService` / `IStreamCoordinatorRpcService` 强类型接口 |
| **数据面** | 记录传输、barrier 传播、watermark 传播 | `IMessageService` + RemoteResultPartition / RemoteInputChannel |
| **编排面** | Invokable 安装、算子链配置 | 直接 Java 调用（同进程内） |

**控制面接口**：

```java
// TaskManager 暴露给 Coordinator 的服务接口
interface IStreamTaskRpcService {
    void submitTask(Subtask subtask);
    void cancelTask(String vertexId, int taskIndex);
    TaskState getTaskState(String vertexId, int taskIndex);
}

// Coordinator 暴露给 TaskManager 的服务接口
interface IStreamCoordinatorRpcService {
    void registerTaskManager(String nodeId, IStreamTaskRpcService taskRpcService);
    void unregisterTaskManager(String nodeId);
}
```

**关键设计决策**：不使用适配器模式包装 `IRpcService`。`TaskManager IS-A IStreamTaskRpcService`，`JobCoordinator IS-A IStreamCoordinatorRpcService`。嵌入式模式下直接 Java 调用；分布式模式下由 Nop RPC 框架生成远程代理。

### 5.1 控制面角色

| 角色 | 职责 |
|---|---|
| `JobCoordinator` | 持有 canonical plan、生成 DeploymentPlan、分配 task、触发 epoch、维护 fencing token |
| `RuntimeNode` | 注册到集群、汇报心跳、承载 task attempt、暴露本节点资源和 transport endpoint |
| `TaskAttempt` | 某个 stable task 的一次执行尝试，绑定 attemptId 和 fencing token |
| `NodeLease` | RuntimeNode 的存活租约，超时后其 task attempt 被视为失效 |
| `ClusterRegistry` | 记录 active coordinator、runtime nodes、node lease 和 task assignment 的一致视图 |

### 5.2 职责边界

| 主题 | 契约 |
|---|---|
| plan 所有权 | `JobCoordinator` 是 `PartitionedPlan` 和 `DeploymentPlan` 的唯一写入者 |
| task 下发 | `JobCoordinator` 将 task assignment 下发到 `RuntimeNode`，RuntimeNode 只执行不改写语义 |
| 心跳 | RuntimeNode 周期性报告 node 状态、task attempt 状态、checkpoint progress 和资源占用 |
| 失败检测 | `NodeLease` 超时、task attempt 失败、transport channel 关闭都触发 coordinator 恢复流程 |
| task 撤销 | 新 attempt 创建前必须 fence 旧 attempt；旧 attempt 的输出、ACK、commit 全部被拒绝 |
| checkpoint 触发 | 只有 active `JobCoordinator` 可以创建 epoch；source task 只响应带有效 fencing token 的 epoch |
| 调度策略 | 初始版本采用全局恢复和重新部署；局部恢复是后续优化 |

### 5.3 作业终止模式

| 模式 | 语义 | 适用场景 |
|---|---|---|
| `CANCEL` | 尽快停止，可 abort non-durable work，不保证输出完整 | 强制停止、开发调试 |
| `DRAIN` | Source truncate 成有限 work，terminal epoch durable 后结束，保证已处理数据的 exactly-once | 优雅关闭、版本升级 |
| `SUSPEND` | 停止新输入，导出可恢复 savepoint，不要求 sink final commit | 暂停作业、状态迁移 |
| `EXPORT_SAVEPOINT` | 生成 protected checkpointNamespace 的 savepoint，不停止作业 | 定期备份、状态快照 |

### 5.4 处理保证

| 模式 | Barrier 行为 | 恢复后行为 |
|---|---|---|
| `STRICT_EXACTLY_ONCE` | 已收到 barrier 的 channel 阻塞 barrier 后 records，等待所有 channel 对齐 | 从 durable epoch 重放，不重复副作用 |
| `AT_LEAST_ONCE` | 继续处理已收到 barrier channel 的 barrier 后 records | 从 durable epoch 重放，可能重复处理 |
| `EFFECTIVELY_ONCE` | 数据处理层按 exactly-once 或 at-least-once 执行，外部效果依赖幂等/upsert | sink 不需要严格 2PC |
| `BEST_EFFORT` | 可禁用 checkpoint | 不保证状态一致性 |

## 6. 数据流模型

### 6.1 Transformation DAG

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

### 6.2 流类型层次

```
DataStream<T>
  └── KeyedStream<T, KEY>           (增加了 keyBy 语义)
        └── WindowedStream<T, K, W>  (增加了 window 语义)
```

- `DataStream`：基础流，支持 map/filter/flatMap/keyBy/sink
- `KeyedStream`：按键分区的流，支持 window 操作
- `WindowedStream`：窗口化流，支持 apply/aggregate/reduce

### 6.3 算子链（Operator Chain）

算子通过 Output 接口串联：

```
StreamSource → ChainingOutput → StreamMap → ChainingOutput → WindowOperator → ChainingOutput → StreamSink
```

链内通过 `ChainingOutput` 直接调用，跨链通过 `RecordWriter/InputGate` 传递。算子链融合在 JobGraphGenerator 阶段完成：多个满足链接条件的 StreamNode 被合并为一个 JobVertex，在同一个线程中顺序执行。

### 6.4 分区策略

每条边必须在 `PartitionedPlan` 中记录 partition policy：

| Policy | 语义 | 使用场景 |
|---|---|---|
| `FORWARD` | 上下游 subtask 一一对应 | chain 边界、同并行度直连 |
| `HASH` | 按 key hash 到 state shard，再映射到 owner subtask | keyBy、keyed window、keyed CEP |
| `REBALANCE` | round-robin 或负载均衡分发 | 无 key 的并行扩散 |
| `BROADCAST` | 复制到所有下游 subtask | 配置流、规则流 |
| `UNION` | 多上游合并到下游输入集合 | 多 source 合并 |
| `SINGLETON` | 所有数据汇聚到 subtask 0 | 全局 sink、全局聚合 |

### 6.5 分布式背压

分布式 edge 通过 `EdgeConfig` 配置 flow control：

| 策略 | 行为 | 适用场景 |
|---|---|---|
| `BLOCKING_QUEUE` | 队列满时阻塞 sender | 单进程、低延迟 |
| `CREDIT_BASED` | receiver 授予 sender 发送额度 | 分布式、高吞吐 |
| `ACK_WINDOW` | sender 只能领先 receiver 一个窗口 | 有序、可靠传输 |

`EdgeConfig` 还定义 queue capacity、receive window、packet size 等参数，参与 `DeploymentPlan` 的内存预算计算。

## 7. 与 Nop 平台的集成

### 7.1 集成点

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

### 7.2 序列化策略

- **metadata**：plan、manifest、state segment descriptor 必须 JSON round-trip
- **payload**：默认使用 `JsonTool`，允许通过状态后端声明可替换 payload codec
- **约束**：每个 state name 必须记录 value schema version 和 checksum

## 8. 设计原则

### 8.1 模型优先

架构核心以模型不变量为中心，而不是以运行时类层次为中心。执行计划用可序列化、可对比、可 Delta 定制的 plan 表达分布式语义。状态恢复用稳定 ID 和 epoch manifest 路由，不依赖对象实例和链内下标。

**StreamModel 是唯一入口**：Java DataStream API、XDSL 和测试构造器都必须生成同一类 canonical StreamModel。Delta 只能修改模型，不能 patch runtime object 来改变语义。

### 8.2 语义不降级

- source 不可重放或 sink 不具备严格提交能力时，不允许声明 `STRICT_EXACTLY_ONCE`
- barrier 只能由 source 读取线程注入，并随数据 channel 传播
- epoch manifest durable 之前，sink transaction 不得 commit
- 旧 attempt 和旧 coordinator 必须被 fencing
- 语义等级必须在 runtime metrics 中暴露

### 8.3 稳定身份

所有持久状态必须有稳定 `operatorId`（用户显式 uid > 模型路径 > 结构 hash）。所有 keyed state 必须有确定性 `StateShard` 路由。状态路径不能包含 `deploymentId`、`runId` 或 `attemptId`。

### 8.4 可移植后端

同一 StreamModel 可编译到本地 runtime、分布式 runtime 或外部 backend（如 Flink）。backend 通过 `StreamBackendCapability` 声明支持的能力，编译器校验 pipeline 的 requirement 是否被满足。

### 8.5 最小控制面

控制面使用 Nop 的模型和租约抽象表达，不引入 Flink 的 SlotSharingGroup、ExecutionAttempt 层级和网络栈。但 task attempt、lease、assignment、fencing 这些分布式正确性概念不能省略。

### 8.6 Flink 兼容但不耦合

学习 Flink 已验证的流处理语义边界，保持 API 概念兼容，避免实现结构耦合。Flink 后端作为可选适配，不主导内核模型。

## 9. 设计不变量

以下不变量不可违反：

1. 所有持久状态必须有稳定 `operatorId`
2. 所有 keyed state 必须有确定性 `StateShard` 路由
3. `PartitionedPlan` 是 parallelism、edge partition、state route、checkpoint route 的唯一语义来源
4. barrier 只能由 source 读取线程注入，并随数据 channel 传播
5. epoch manifest durable 之前，sink transaction 不得 commit
6. 恢复必须从最新 durable epoch manifest 开始
7. source 不可重放或 sink 不具备严格提交能力时，不允许声明 `STRICT_EXACTLY_ONCE`
8. 旧 attempt 和旧 coordinator 必须被 fencing
9. timer state 是窗口和 CEP exactly-once 的必要状态
10. Delta 只能修改模型，不能 patch runtime object 来改变语义
11. 所有 `StreamModel` 必须包含 `StreamComponents` registry
12. 所有 `StreamRequirement` 必须在编译时和运行时校验
13. 所有 transactional operator 必须实现 `CheckpointParticipant`
14. 所有分布式 edge 必须配置 `EdgeConfig`
15. 所有作业终止必须明确 `JobTerminationMode`
