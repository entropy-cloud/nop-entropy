# nop-stream 架构基线

**日期**：2026-05-19（更新于 2026-06-06）
**范围**：`nop-stream` 模块
**状态**：active

---

## 一、设计结论

1. 系统分为七层：API → StreamComponents → Transformation → 执行计划 → 算子 → 状态&时间 → 存储
2. 核心模型是 StreamModel——可序列化算子图，三种入口（XDSL / Java API / Delta）最终生成同一类 canonical 模型
3. 五层执行管线：StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology
4. 依赖方向严格单向：runtime/checkpoint/connector/cep/flow → core → api

## 二、模块划分

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

### 模块职责边界

| 模块 | 职责 | 依赖方向 |
|------|------|----------|
| **nop-stream-api** | 用户可见 API、SourceFunction/SinkFunction contract、一致性能力枚举、公共模型接口 | 无依赖 |
| **nop-stream-core** | StreamModel + StreamComponents、StreamGraph/JobGraph、PartitionedPlan/DeploymentPlan、优化和校验、StreamRequirement 校验 | → api |
| **nop-stream-runtime** | RuntimeTopology、本地/分布式 task 执行、transport backend、fencing、node lifecycle、EdgeConfig flow control | → core |
| **nop-stream-checkpoint** | Epoch coordinator、manifest 生成与发布、state segment descriptor、checkpoint/savepoint storage contract、CheckpointParticipant 调度 | → core |
| **nop-stream-connector** | Replayable source（SourceWorkUnit + RestrictionTracker）、transactional/idempotent sink（CheckpointParticipant）、split/offset 协议适配 | → core |
| **nop-stream-cep** | Pattern DSL、NFA 编译、SharedBuffer、CepOperator（通过标准 state/timer 接口接入统一后端）、声明式模型（pattern.xdef） | → core |
| **nop-stream-flow** | XDSL StreamModel 编排、Delta 定制支持 | → core |
| **nop-stream-flink** | 可选外部后端适配，将 core API 的 Transformation 映射到 Flink DataStream API | → core |

### 依赖方向

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

### 五层执行管线

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

### 控制面角色

| 角色 | 职责 |
|---|---|
| `JobCoordinator` | 持有 canonical plan、生成 DeploymentPlan、分配 task、触发 epoch、维护 fencing token |
| `RuntimeNode` | 注册到集群、汇报心跳、承载 task attempt、暴露本节点资源和 transport endpoint |
| `TaskAttempt` | 某个 stable task 的一次执行尝试，绑定 attemptId 和 fencing token |
| `NodeLease` | RuntimeNode 的存活租约，超时后其 task attempt 被视为失效 |
| `ClusterRegistry` | 记录 active coordinator、runtime nodes、node lease 和 task assignment 的一致视图 |

### 作业终止模式

| 模式 | 语义 | 适用场景 |
|---|---|---|
| `CANCEL` | 尽快停止，可 abort non-durable work，不保证输出完整 | 强制停止、开发调试 |
| `DRAIN` | Source truncate 成有限 work，terminal epoch durable 后结束，保证已处理数据的 exactly-once | 优雅关闭、版本升级 |
| `SUSPEND` | 停止新输入，导出可恢复 savepoint，不要求 sink final commit | 暂停作业、状态迁移 |
| `EXPORT_SAVEPOINT` | 生成 protected checkpointNamespace 的 savepoint，不停止作业 | 定期备份、状态快照 |

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

### 分布式背压

分布式 edge 通过 `EdgeConfig` 配置 flow control：

| 策略 | 行为 | 适用场景 |
|---|---|---|
| `BLOCKING_QUEUE` | 队列满时阻塞 sender | 单进程、低延迟 |
| `CREDIT_BASED` | receiver 授予 sender 发送额度 | 分布式、高吞吐 |
| `ACK_WINDOW` | sender 只能领先 receiver 一个窗口 | 有序、可靠传输 |

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

## 八、与已有设计的关系

| 主题 | 文档 |
|------|------|
| 设计原则、non-goals、约束、不变量 | `00-vision.md` |
| 核心模型（StreamModel、DataStream API、算子、稳定身份） | `core-design.md` |
| 图模型（StreamGraph、JobGraph、算子链化） | `graph-model-design.md` |
| Checkpoint 与 Exactly-Once | `checkpoint-design.md` |
| 状态管理 | `state-management-design.md` |
| 窗口机制 | `window-design.md` |
| 时间与 Watermark | `time-model-design.md` |
| 连接器 | `connector-design.md` |
| CEP 引擎 | `cep-design.md` |
| 架构对比 | `comparison.md` |
| 组件路线 | `component-roadmap.md` |
