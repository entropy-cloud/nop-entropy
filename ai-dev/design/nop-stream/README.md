# Nop Stream 设计文档

> Status: active
> Created: 2026-05-19
> Updated: 2026-06-30（补充分层概览、系统关系图、数据/控制双路径）

---

## 一、系统概览

### 1.1 架构鸟瞰

```
┌────────────────────────────────────────────────────────────────────────┐
│                         用户 API 层                                     │
│  DataStream API / KeyedStream / WindowedStream / CEP.pattern()         │
│  (nop-stream-core: datastream 包)                                      │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │ 构造 Transformation DAG
┌──────────────────────────▼─────────────────────────────────────────────┐
│                     核心模型层                                          │
│  StreamModel + StreamComponents + Transformation DAG                   │
│  (nop-stream-core: model 包)                                           │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │ 编译
┌──────────────────────────▼─────────────────────────────────────────────┐
│                     图模型与执行计划层                                   │
│  StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan            │
│  (nop-stream-core: graph / jobgraph / execution 包)                    │
└──────────┬───────────────────────────────────────┬─────────────────────┘
           │ LOCAL                                │ DISTRIBUTED
┌──────────▼──────────────┐           ┌───────────▼─────────────────────┐
│  TaskExecutor (线程池)   │           │  IStreamExecutionDispatcher    │
│  (nop-stream-runtime)   │           │  → JobCoordinator + TaskManager │
└──────────┬──────────────┘           │  (nop-stream-runtime)           │
           │                          └───────────┬─────────────────────┘
           │                                      │
┌──────────▼──────────────────────────────────────▼─────────────────────┐
│                     算子运行时层                                        │
│  StreamOperator + OperatorChain + WindowOperator + CepOperator        │
│  + RecordWriter/RecordReader + ResultPartition/InputChannel           │
│  (nop-stream-core: operators 包; nop-stream-runtime)                  │
└──────────┬──────────────────────────────────────┬─────────────────────┘
           │ 读写                                  │ checkpoint
┌──────────▼──────────┐            ┌──────────────▼─────────────────────┐
│  状态与时间层        │            │  容错层                             │
│  IStateBackend      │            │  CheckpointCoordinator             │
│  IKeyedStateBackend │            │  Epoch Manifest + Barrier          │
│  InternalTimerSvc   │            │  CheckpointParticipant + 2PC       │
│  WatermarkStrategy  │            │  (nop-stream-core: checkpoint 包)   │
│  (nop-stream-core)  │            └──────────────┬─────────────────────┘
└──────────┬──────────┘                           │ 存储
           │                          ┌───────────▼─────────────────────┐
           │                          │  存储层                          │
           │                          │  ICheckpointStorage             │
           │                          │  ├─ LocalFileCheckpointStorage  │
           │                          │  └─ JdbcCheckpointStorage       │
           │                          │  (nop-stream-runtime)           │
           │                          └─────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     集成层                                            │
│  连接器: BatchLoaderSource / BatchConsumerSink / MessageSource       │
│  (nop-stream-connector)                                              │
│  CEP: NFA + SharedBuffer + Pattern DSL                              │
│  (nop-stream-cep)                                                    │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.2 模块划分与职责边界

| 模块 | 包路径 | 核心类/接口 | 职责 | 依赖 | 运行进程 |
|------|--------|------------|------|------|---------|
| **nop-stream-core** | `datastream` | `DataStream`, `KeyedStream`, `WindowedStream`, `StreamExecutionEnvironment` | 用户 API DSL、Transformation DAG 构建 | 无 | Client JVM |
| | `model` | `StreamModel`, `StreamComponents`, `StreamRequirement` | 核心模型 + 组件注册表 + fingerprint | `datastream` | Client JVM |
| | `graph` | `StreamGraph`, `StreamNode`, `StreamEdge`, `StreamGraphGenerator` | 逻辑 DAG 生成 | `model` | Client JVM |
| | `jobgraph` | `JobGraph`, `JobVertex`, `JobEdge`, `OperatorChain`, `JobGraphGenerator` | 算子链优化 + 物理执行图 | `graph` | Client JVM |
| | `execution` | `PartitionedPlan`, `DeploymentPlan`, `GraphExecutionPlan`, `IStreamExecutionDispatcher` | 并行展开 + 节点映射 + 执行分发 SPI | `jobgraph` | Client JVM |
| | `operators` | `StreamOperator`, `OneInputStreamOperator`, `AbstractStreamOperator`, `StreamOperatorFactory`, `Output`, `ChainingStrategy` | 算子基类 + 接口 + 工厂 + 链化策略 | 无 | 所有 JVM |
| | `state` | `IStateBackend`, `IKeyedStateBackend`, `IInternalStateBackend`, `ValueState`, `MapState`, `ListState`, `StateDescriptor` | 状态接口 + 状态后端 SPI | 无 | 所有 JVM |
| | `checkpoint` | `CheckpointCoordinator`, `CheckpointBarrier`, `CheckpointPlan`, `CheckpointParticipant`, `EpochManifest`, `TaskEpochSnapshot`, `ProcessingGuarantee` | Checkpoint 协议 + 协调器 + 参与者 SPI | `operators`, `state` | Task JVM |
| | `time` | `WatermarkStrategy`, `WatermarkGenerator`, `TimestampAssigner`, `Watermark` | 时间模型 + Watermark 接口 | 无 | Task JVM |
| | `functions` | `MapFunction`, `FlatMapFunction`, `FilterFunction`, `KeySelector`, `ReduceFunction`, `AggregateFunction`, `WindowFunction`, `SourceFunction`, `SinkFunction` | 用户函数接口 | 无 | 所有 JVM |
| **nop-stream-runtime** | `taskmanager` | `TaskExecutor`, `Task`, `SubtaskTask`, `StreamTaskInvokable` | Task 线程池 + 生命周期管理 | → core | Task JVM |
| | `transport` | `RecordWriter`, `RecordReader`, `InputGate`, `ResultPartition`, `InputChannel` | 算子间数据交换（同进程 BlockingQueue / 跨进程 IMessageService） | → core | Task JVM |
| | `coordinator` | `JobCoordinator`, `IStreamCoordinatorRpcService` | 作业协调（plan 分发 + epoch 触发 + fencing） | → core | Coordinator JVM |
| | `rpc` | `IStreamTaskRpcService` | TaskManager 控制面接口 | → core | Task JVM |
| | `checkpoint` | `GraphModelCheckpointExecutor`, `JdbcCheckpointStorage`, `LocalFileCheckpointStorage` | Checkpoint 执行器 + 持久化实现 | → core | Task JVM |
| | `watermark` | `TimestampsAndWatermarksOperator` | Watermark 生成算子实现 | → core | Task JVM |
| | `cluster` | `InMemoryClusterRegistry`, `NodeLease`, `RuntimeNode` | 集群注册表 + 租约管理 | → core | Coordinator JVM |
| **nop-stream-connector** | — | `BatchLoaderSourceFunction`, `BatchConsumerSinkFunction`, `MessageSourceFunction`, `MessageSinkFunction`, `DebeziumCdcSourceFunction` | 连接器适配器 | → core, nop-batch | Source/Sink JVM |
| **nop-stream-cep** | — | `NFA`, `NFACompiler`, `SharedBuffer`, `Pattern`, `CepOperator`, `CepPatternModel` | CEP 引擎 | → core, nop-xlang | Task JVM |
| **nop-stream-flow** | — | (规划) XDSL StreamModel 编排 | 声明式定义 + Delta 定制 | → core | Client JVM |
| **nop-stream-checkpoint** | — | (规划) 从 runtime 分离 | 独立 checkpoint 协调器 + 存储 | → core | (待定) |
| **nop-stream-flink** | — | (规划) Flink 后端适配 | Transformation → Flink DataStream | → core, flink | (待定) |
| **nop-stream-api** | — | (规划) 公共接口抽取 | 接口与实现解耦 | 无 | (待定) |

### 1.3 依赖方向

```
运行时和集成模块 → core → api（规划中，当前核心实现在 core 中）
                 ↓
              nop-platform（IJdbcTemplate / IMessageService / IEvalFunction / IDialect 等）

具体：
  nop-stream-runtime       → nop-stream-core
  nop-stream-connector     → nop-stream-core + nop-batch-core
  nop-stream-cep           → nop-stream-core + nop-xlang
  nop-stream-flow (规划)    → nop-stream-core
  nop-stream-checkpoint (规划) → nop-stream-core
  nop-stream-flink (规划)    → nop-stream-core + flink 依赖
```

---

## 二、核心概念关系

### 2.1 概念转化路径（从用户意图到运行时）

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  用户意图表达                                                                    │
│                                                                                 │
│  Java API:  env.addSource(src).map(fn).keyBy(ks).window(wa).aggregate(af).sink()│
│  XDSL:      <stream:StreamModel><source .../><transform .../><edge .../></...>  │
│  Delta:     <stream:StreamModel x:extends="base"><transform .../></...>         │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ① StreamModel（canonical 模型，唯一入口）                                      │
│     ├── StreamComponents: 所有可复用组件注册表（transforms/streams/              │
│     │                      windowingStrategies/coders/schemas/requirements）     │
│     └── Transformation DAG: Source → OneInput → Partition → OneInput → Sink    │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ StreamGraphGenerator
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ② StreamGraph（逻辑 DAG）                                                      │
│     ├── StreamNode: id, operatorFactory, parallelism, keySelector, windowAssigner│
│     └── StreamEdge: sourceId, targetId, partitioner                             │
│                                                                                 │
│     注: PartitionTransformation 保留为独立节点（partitioner 存入入边）            │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ JobGraphGenerator（算子链融合）
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ③ JobGraph（优化后的执行图）                                                    │
│     ├── JobVertex: id, parallelism, operatorChains[], invokableClassName        │
│     ├── JobEdge: sourceVertexId, targetVertexId, ResultPartitionType            │
│     └── OperatorChain: operators[]（同一线程顺序执行）                            │
│                                                                                 │
│     链化条件: forward + 同并行度 + 无分支 + 无合并 + 非 source + 非 sink          │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ PartitionedPlanBuilder
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ④ PartitionedPlan（并行展开 + 分区 + 状态路由）                                 │
│     ├── 每个 vertex 的 parallelism                                               │
│     ├── 每个 operator 的稳定 operatorId                                          │
│     ├── 每个 key 到 StateShard 的路由                                            │
│     ├── 每个 StateShard 的 ownerSubtask                                          │
│     ├── 每条边的 partition policy（FORWARD/HASH/REBALANCE/BROADCAST/UNION）      │
│     └── checkpoint ACK 集合（哪些 task 必须 ACK）                                │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ DeploymentPlanBuilder
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ⑤ DeploymentPlan（节点映射 + 资源配置）                                         │
│     ├── 将 subtask 映射到 runtime node                                           │
│     ├── transport backend 绑定（local BlockingQueue / remote IMessageService）   │
│     ├── state backend 绑定（Memory / 未来 RocksDB）                              │
│     ├── checkpoint storage 绑定（LocalFile / JDBC）                              │
│     ├── EdgeConfig flow control（BLOCKING_QUEUE / CREDIT_BASED / ACK_WINDOW）    │
│     └── MemoryBudget 分配                                                       │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ execute() 分发
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
┌──────────────────────┐  ┌──────────────────────────────┐
│  LOCAL               │  │  DISTRIBUTED                  │
│  TaskExecutor        │  │  IStreamExecutionDispatcher  │
│  (线程池调度)         │  │  → EmbeddedDistributedExec   │
│                      │  │    (JobCoordinator + TM 集群) │
│  同 JVM，直接 Java    │  │                              │
│  调用传递数据         │  │  跨 JVM，通过 Nop RPC /       │
│                      │  │  IMessageService 通信         │
└──────────────────────┘  └──────────────────────────────┘
```

### 2.2 稳定身份体系

```
operatorId      算子级稳定身份          用户显式 uid > 模型路径 > 结构 hash
taskId          Task 实例身份           TaskLocation(jobId, pipelineId, vertexId, taskIndex)
pipelineId      管线级标识              作业内管线路由
StateShard      状态分片路由             stableHash(key) mod stateShardCount
checkpointNs    checkpoint 命名空间      savepoint 隔离
```

### 2.3 组件注册表（StreamComponents）

`StreamComponents` 是系统可移植性的核心，所有可复用组件通过稳定 ID 引用：

```
StreamComponents
  ├── transforms: Map<String, PTransform>           ← 算子定义
  ├── streams: Map<String, PCollection>             ← 流定义
  ├── windowingStrategies: Map<String, WindowingStrategy>  ← 窗口策略
  ├── coders: Map<String, Coder>                    ← 序列化器
  ├── schemas: Map<String, Schema>                  ← schema 定义
  ├── environments: Map<String, StreamEnvironment>  ← 环境配置
  ├── sideInputs: Map<String, SideInput>            ← 侧输入
  ├── requirements: List<StreamRequirement>         ← 能力需求
  └── checkpointParticipants: Set<String>           ← 参与 checkpoint 的 operatorId
```

所有内容参与 fingerprint 计算，保证跨 backend 校验和 savepoint 兼容性检查的一致性。

---

## 三、两条关键路径

### 3.1 数据路径（一条记录的生命周期）

```
Source 读取线程
  │
  ├── SourceFunction.run(ctx)
  │     └── ctx.collect(record)   ← 产生 StreamRecord
  │
  ├── TimestampsAndWatermarksOperator.processElement()  (如有配置)
  │     └── timestampAssigner.extractTimestamp() → 设置到 StreamRecord
  │     └── watermarkGenerator.onEvent() → 跟踪最大时间戳
  │
  ├── 算子链内传递（ChainingOutput → 直接方法调用）
  │     ├── StreamMap.processElement()    → output.collect()
  │     ├── StreamFilter.processElement() → output.collect() (if passes)
  │     ├── StreamFlatMap.processElement() → output.collect() (0..N)
  │     └── WindowOperator.processElement()
  │            ├── WindowAssigner.assignWindows() → 窗口集合
  │            ├── windowState.add(element)       → 增量/追加到窗口
  │            └── Trigger.onElement() → 触发计算或继续等待
  │
  ├── 跨链传递（需要跨 Task 边界时）
  │     └── RecordWriter(partitioner) → ResultPartition → InputChannel → RecordReader → 下游算子
  │
  ├── CheckpointBarrier（周期性注入，与数据混合在同一通道）
  │     ├── Source 读取线程在 safe point 注入 barrier
  │     ├── 随数据流传播 → InputGate 对齐（等待所有 channel 的 barrier）
  │     ├── 对齐完成后 → snapshotState() → ACK → 释放阻塞 channel
  │     └── 传播到下游 Task
  │
  └── SinkFunction.invoke(record)
        └── 写入外部系统（DB / 消息队列 / 文件）

中间可能有 Watermark 穿插：
  └── Watermark 推进 → InternalTimerService.advanceWatermark()
        → 触发事件时间定时器 → WindowOperator.onEventTime() → emitWindowContents()
```

### 3.2 控制路径（Checkpoint 生命周期）

```
JobCoordinator / CheckpointCoordinator
  │
  ├── 1. TRIGGER
  │     └── 周期性定时器到期 → 生成 epochId → 通知所有 source 注入 barrier
  │
  ├── 2. INJECT
  │     └── Source 读取线程：记录当前 offset → 向所有输出 channel 发送 CheckpointBarrier(N)
  │
  ├── 3. ALIGN（仅 STRICT_EXACTLY_ONCE 模式）
  │     └── 多输入 Task：
  │           ├── 收到 channel C 的 barrier N → 标记 C 已对齐
  │           ├── 阻塞 channel C 的 barrier 后数据（缓冲区等待）
  │           ├── 继续处理未对齐 channel 的数据
  │           └── 所有 channel 对齐 → 进入 SNAPSHOT
  │
  ├── 4. SNAPSHOT
  │     └── 每个 Task：
  │           ├── operator.snapshotState() → 遍历所有 operator 的状态
  │           ├── keyed state shards → 序列化为 StateSegment
  │           ├── timer state → 序列化
  │           ├── source offset / sink transaction → 序列化
  │           ├── CheckpointParticipant.saveState() / prepareCommit()
  │           └── → 发送 TaskEpochSnapshot ACK 到 Coordinator
  │
  ├── 5. DURABLE
  │     └── Coordinator 收齐所有 ACK：
  │           ├── 生成 EpochManifest（含 plan fingerprint + task snapshots + checksum）
  │           ├── 持久化 manifest 到 ICheckpointStorage
  │           └── manifest durable 完成 → 进入 COMMIT
  │
  ├── 6. COMMIT
  │     └── Coordinator 通知所有 sink / CheckpointParticipant：
  │           ├── finishCommit(N, true) → 提交 epoch N 的 transaction
  │           ├── subsuming contract: 可提交所有 epoch ≤ N 的 pending transaction
  │           └── commit 必须幂等
  │
  └── 7. RECOVERY（故障时）
        └── detect failure → fence old attempt → load latest durable manifest →
            restore state segments → restart tasks with new fencingToken →
            resume from epoch+1
```

---

## 四、文件索引与阅读路径

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，从高层设计原则到分项设计逐层展开：

1. **愿景层** — 定位、成功标准、约束、non-goals、设计不变量
2. **架构基线层** — 模块划分、分层设计、执行模型、分布式控制面、数据流模型
3. **核心模型层** — StreamModel、DataStream API、算子模型、稳定身份
4. **图模型与执行层** — StreamGraph、JobGraph、算子链化、Task/TaskExecutor
5. **容错层** — Checkpoint、Epoch 协议、Exactly-Once、恢复
6. **状态与时间层** — 状态管理、窗口机制、时间模型
7. **集成层** — 连接器、CEP 引擎
8. **参考层** — 架构对比、组件路线

---

## 愿景层

- `00-vision.md`
  - 产品定位、成功标准、不可违反的约束、显式 non-goals、设计收敛路径、必须由人决策的决策点、核心取舍、设计不变量、拒绝了什么

## 架构基线层

- `01-architecture-baseline.md`
  - 模块划分与依赖方向、七层分层设计、五层执行管线、分布式控制面（三面架构）、数据流模型、与 Nop 平台的集成

## 核心模型层

- `core-design.md`
  - StreamModel、StreamComponents、StreamRequirement、StreamBackendCapability、StreamModelFingerprint
  - DataStream API（Builder）、Transformation DAG
  - 算子模型、算子生命周期、Output 机制、算子链化
  - 稳定身份体系（operatorId、taskId、StateShard、TaskLocation）
  - 函数接口

## 图模型与执行层

- `graph-model-design.md`
  - 三层转换管线（Transformation → StreamGraph → JobGraph → Task）
  - 算子链识别算法、链化条件
  - StreamGraph / JobGraph 数据结构
  - Task / SubtaskTask / TaskExecutor 运行时执行
  - 执行路径统一（图模型为唯一路径）
  - 与 Flink 的差异

## 容错层

- `checkpoint-design.md`
  - Epoch Checkpoint 协议（生命周期、Barrier 注入/对齐、Snapshot、Manifest）
  - CheckpointParticipant（泛化事务参与）
  - ProcessingGuarantee（四种保证级别）
  - Source / Sink Exactly-Once 协议
  - JobTerminationMode
  - 故障恢复模型（fencing、Coordinator HA、恢复兼容性）
  - Serializer Fingerprint 策略
  - 可观测性契约
  - 容错约束边界（§13：执行路径分层 + 容错契约 + 缓解选项 + 不变量与契约的关系；实现状态与缺口见 `component-roadmap.md` §3 C5）

## 状态与时间层

- `state-management-design.md`
  - 状态接口层次（ValueState/MapState/AppendingState/ListState）
  - StateShard（确定性分片路由）、StatePath（持久化路径）
  - 状态后端（IStateBackend → IKeyedStateBackend → IInternalStateBackend）
  - 序列化策略、State Segment、Timer State、内存预算

- `window-design.md`
  - 窗口四要素（WindowAssigner + Trigger + Evictor + WindowFunction）
  - WindowingStrategy（可序列化模型）
  - 统一算子架构（单一 WindowOperator）
  - InternalWindowFunction 适配层、WindowOperatorBuilder
  - PaneState、WindowCompatibilityCheck
  - 合并窗口处理流程

- `time-model-design.md`
  - WatermarkStrategy、TimestampAssigner、WatermarkGenerator
  - Watermark 生成策略（Ascending / BoundedOutOfOrderness）
  - Watermark 传播机制
  - TimestampsAndWatermarksOperator

## 集成层

- `connector-design.md`
  - nop-batch 桥接（BatchLoaderSourceFunction / BatchConsumerSinkFunction）
  - SourceWorkUnit 协议（RestrictionTracker、DynamicSplit、DrainTruncate、WatermarkEstimator）
  - Split Assignment Recovery 协议
  - 消息队列与 CDC 适配

- `cep-design.md`
  - Pattern DSL、NFA 编译与匹配
  - SharedBuffer（引用计数 + Dewey 编号）
  - CepOperator、匹配后策略
  - 声明式模型（XMeta）

## 参考层

- `comparison.md`
  - 与 Flink / SeaTunnel / NiFi / Node-RED / StreamSets 的架构对比

- `component-roadmap.md`
  - 组件分解（C1–C8 + D1–D9 + P1–P4）
  - 开发方法（审计-规划-执行循环）
  - 已知技术债

---

## 阅读顺序

**必读路径**（理解定位 → 架构 → 核心模型）：

1. `00-vision.md` — 设计原则、约束、non-goals
2. `01-architecture-baseline.md` — 架构基线、模块划分、执行管线
3. `core-design.md` — StreamModel、DataStream API、算子模型

**按需深入**：

4. `graph-model-design.md` — 图模型转换、算子链化、执行路径
5. `checkpoint-design.md` — Checkpoint 协议、Exactly-Once
6. `state-management-design.md` — 状态后端、StateShard、序列化
7. `window-design.md` — 窗口机制、Trigger、Evictor
8. `time-model-design.md` — Watermark、时间戳分配
9. `connector-design.md` — 连接器适配
10. `cep-design.md` — CEP 引擎

**扩展方向**：

11. `comparison.md` — 架构对比（Flink / SeaTunnel / NiFi）
12. `component-roadmap.md` — 组件路线和开发方法
