# nop-stream 核心引擎设计

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-25（重组章节：StreamModel 前置为 §1，DataStream API 降为 §2）

## 1. StreamModel 与 StreamComponents

StreamModel 是 nop-stream 的核心模型，所有入口（XDSL、Java API、Delta）最终生成同一套 canonical StreamModel。本节定义模型结构和组件注册表。

### 1.1 StreamModel

`StreamModel` 是 canonical 管线模型，包含 Transformation DAG 和统一的组件注册表：

```java
class StreamModel {
    StreamComponents components;
    Map<String, Transformation> transformations;

    Set<StreamRequirement> getRequirements() {
        return new HashSet<>(components.requirements);
    }
}
```

StreamModel 是唯一入口：Java DataStream API、XDSL 和测试构造器都必须生成同一类 canonical StreamModel。Delta 只能修改模型，不能 patch runtime object 来改变语义。

### 1.2 StreamComponents：统一组件注册表

`StreamComponents` 是所有可复用组件的 canonical 注册表，支持跨 backend 校验、fingerprint 和序列化：

```java
class StreamComponents {
    Map<String, PTransform> transforms;
    Map<String, PCollection> streams;
    Map<String, WindowingStrategy> windowingStrategies;
    Map<String, Coder> coders;
    Map<String, Schema> schemas;
    Map<String, StreamEnvironment> environments;
    Map<String, SideInput> sideInputs;
    List<StreamRequirement> requirements;
    Set<String> checkpointParticipants;  // operatorId 集合
}
```

**设计要点**：

1. 所有组件都有稳定 ID，用于跨文档引用和 fingerprint
2. 组件通过 ID 引用，不是通过内联定义。例如 `WindowedStream` 持有 `windowingStrategyId`，从 `StreamComponents` 中查找
3. requirements 列表声明 pipeline 所需能力，编译器和 backend 必须理解这些需求；未知或不支持的 requirement 必须构建失败
4. checkpointParticipants 集合记录哪些 operator 实现了 `CheckpointParticipant`，用于 checkpoint 时发现和调用
5. components registry 的所有内容参与 `PartitionedPlan` fingerprint，确保 savepoint 恢复时的兼容性

### 1.2.1 StreamComponents 的所有权与传递

**设计决策**：`StreamComponents` 由 `StreamExecutionEnvironment` 持有为字段，在构造时通过 SPI 加载填充。它随 API 调用链向下传递：`StreamExecutionEnvironment` → `DataStream` → `KeyedStream` → `WindowedStream`。

**为什么不是局部变量**：如果 `StreamComponents` 仅在 `buildStreamModel()` 中局部创建，那么在 API 构建阶段（`keyBy().window().aggregate()`）创建的 `WindowedStreamImpl` 无法获取 `WindowOperatorFactory` 等运行时组件，只能回退到 core 内部的简化实现，绕过统一状态后端。这会使 DataStream API 的窗口路径不走 checkpoint 集成的 `WindowOperator`，违反"图模型为唯一执行路径"的约束。

**SPI 加载机制**：`StreamComponents` 中的可插拔组件（如 `IWindowOperatorFactory`、`IDeploymentPlanProvider`、`ICheckpointExecutorFactory`）通过 `ServiceLoader` 从 classpath 自动发现。当 runtime 模块在 classpath 中时，对应的实现类被自动注册。这与 nop-stream 现有的 SPI 模式一致。

**core-only 场景**：当 classpath 中只有 core 没有 runtime 时，SPI 不会发现 `WindowOperatorFactory`。此时窗口操作必须**快速失败**（抛出异常），而非静默回退到不支持 checkpoint 的简化实现。core 模块定义接口和 API，不提供需要状态后端集成的算子实现。

### 1.3 StreamRequirement：能力声明

```java
enum StreamRequirement {
    STATEFUL_PROCESSING,           // 需要状态后端
    KEYED_STATE_PROCESSING,        // 需要 keyed state backend

    SPLITTABLE_SOURCE,             // source 支持动态拆分
    BUNDLE_FINALIZATION,           // source 支持 work-unit finalization
    STABLE_INPUT,                  // source 保证 record 顺序不变
    TIME_SORTED_INPUT,             // source 保证按时间戳排序

    STRICT_EXACTLY_ONCE,           // 要求严格 exactly-once
    EFFECTIVELY_ONCE,              // 接受 effectively-once（幂等/upsert）
    AT_LEAST_ONCE,                 // 接受 at-least-once

    DURABLE_CHECKPOINT,            // 需要 durable checkpoint storage
    INCREMENTAL_CHECKPOINT,        // 支持增量 checkpoint
    UNALIGNED_CHECKPOINT,          // 支持 unaligned checkpoint

    TWO_PHASE_COMMIT_SINK,         // sink 支持两阶段提交
    STAGED_ATOMIC_COMMIT_SINK,     // sink 支持 staged atomic commit
    OUTBOX_EPOCH_LOG_SINK,         // sink 支持 outbox epoch log

    DISTRIBUTED_EXECUTION,         // 需要分布式 runtime
    REMOTE_STATE_SERVICE,          // 需要远程状态服务
    RESCALABLE_STATE,              // 状态支持重新分片

    PROTOCOL_LEVEL_METRICS,        // 支持 protocol-level metrics
    SAMPLED_DATA_TRACING,          // 支持采样数据追踪
}
```

**校验规则**：

| 校验阶段 | 规则 |
|----------|------|
| 编译时 | `StreamCompiler` 检查 pipeline requirement 是否与 source/sink 能力匹配（如 `STRICT_EXACTLY_ONCE` 但 sink 只有 `IDEMPOTENT` 能力，编译失败） |
| Backend | `StreamBackend` 声明 `StreamBackendCapability`，requirement 不在支持列表中则拒绝运行 |
| Runtime | 作业启动时验证所有 required capabilities 是否可用 |

### 1.4 StreamBackendCapability：Backend 能力声明

```java
class StreamBackendCapability {
    Set<StreamRequirement> supportedRequirements;
    Set<StateBackendType> supportedStateBackends;
    Set<CheckpointStorageType> supportedCheckpointStorages;
    Set<FlowControlPolicy> supportedFlowControlPolicies;
    boolean supportsDistributedExecution;
    boolean supportsRemoteStateService;
    boolean supportsRescale;
}
```

本地线程 runtime（当前实现）仅支持 `STATEFUL_PROCESSING`、`KEYED_STATE_PROCESSING`、`DURABLE_CHECKPOINT`、`TWO_PHASE_COMMIT_SINK`，`supportsDistributedExecution = false`。分布式 runtime 在此基础上增加 `DISTRIBUTED_EXECUTION`、`REMOTE_STATE_SERVICE`、`SPLITTABLE_SOURCE` 等能力。

### 1.5 StreamModelFingerprint

`StreamModelFingerprint` 计算 StreamModel 的结构指纹，用于 savepoint 恢复时的兼容性检查：

```java
class StreamModelFingerprint {
    String version;                              // fingerprint schema 版本
    Map<String, String> componentHashes;         // 按 componentId 的 SHA256 hash
    String dagTopologyHash;                      // DAG 拓扑 hash
    String requirementsHash;                     // requirements hash
    String checkpointParticipantsHash;           // checkpointParticipants hash
}
```

**兼容性规则**：

- 版本必须兼容
- DAG 拓扑必须一致
- Requirements 必须完全一致（变更是不兼容的）
- WindowingStrategy 变更需要兼容性检查（allowedLateness 变大兼容；windowFn 类型、accumulationMode、trigger 类型变化不兼容）

**使用场景**：

- **Savepoint 恢复**：比较当前 fingerprint 与 savepoint 中的 fingerprint，不匹配则拒绝恢复或要求迁移 action
- **Delta 定制**：Delta 修改 component 后必须重新计算 fingerprint

`PartitionedPlan.fingerprint()` 调用 `StreamModelFingerprint`，再叠加并行度、分区策略、状态路由。`EpochManifest` 存储 `streamModelFingerprint` 和 `requirements` 列表，用于恢复时验证组件一致性。

## 2. DataStream API 作为 StreamModel 的 Builder

DataStream API 是 StreamModel 的编程构造器。用户通过 DataStream API 构建 Transformation DAG，经 `StreamGraphGenerator` 编译为 StreamModel。它不是最终用户的主入口（主入口是 XDSL 声明式定义），但作为开发阶段的便捷工具保留。

### 2.1 设计决策

采用与 Flink DataStream API 一致的命名和概念模型，保持 API 概念兼容。

### 2.2 流类型层次

```
DataStream<T>                    基础数据流
  ├── map() → SingleOutputStreamOperator<R>
  ├── filter() → SingleOutputStreamOperator<T>
  ├── flatMap() → SingleOutputStreamOperator<R>
  ├── keyBy(key) → KeyedStream<T, K>
  └── addSink() / print()

KeyedStream<T, KEY>              按键分区流
  ├── timeWindow(size) → WindowedStream<T, K, TimeWindow>
  ├── timeWindow(size, slide) → WindowedStream<T, K, TimeWindow>
  ├── countWindow(size) → WindowedStream<T, K, GlobalWindow>
  └── 继承 DataStream 的所有操作

WindowedStream<T, K, W>          窗口化流
  ├── apply(WindowFunction)     → 通过 IWindowOperatorFactory 创建算子
  ├── aggregate(AggregateFunction) → 通过 IWindowOperatorFactory 创建算子
  ├── reduce(ReduceFunction)     → 通过 IWindowOperatorFactory 创建算子
  └── transform(name, typeInfo, operator) → 直接构造
```

### 2.2.1 窗口算子工厂注入

`WindowedStream` 的 `apply()`/`aggregate()`/`reduce()` 通过 `IWindowOperatorFactory` 创建窗口算子（`WindowOperator`），而非直接实例化。工厂的获取路径：

1. `StreamExecutionEnvironment` 构造时通过 SPI 加载 `IWindowOperatorFactory` 到 `StreamComponents`
2. `KeyedStreamImpl.window()` 创建 `WindowedStreamImpl` 时，将 `StreamComponents` 传递给它
3. `WindowedStreamImpl` 从 `StreamComponents` 获取工厂，委托创建算子

**为什么需要工厂而非直接构造**：`WindowOperator` 依赖 `IInternalStateBackend` 和 `InternalTimerService`，这些在 runtime 模块中。core 模块不能直接引用 runtime 的实现类。工厂模式保持 `runtime → core` 的单向依赖。

**工厂缺失时的行为**：如果 SPI 未发现工厂（core-only 场景），窗口操作抛出异常而非静默回退。这是硬约束——不允许在用户不知情的情况下走不支持 checkpoint 的窗口路径。

**拒绝了什么**：

| 方案 | 拒绝理由 |
|------|---------|
| core 内置简化 `WindowAggregationOperator` 作为默认 | 不参与 keyed state 生命周期管理（checkpoint、恢复），静默回退会让用户误以为窗口聚合支持 exactly-once |
| 将 `WindowOperator` 移到 core | 违反分层设计，core 不应包含依赖 `IInternalStateBackend` 的重型算子实现 |
| `StreamComponents` 仅在 `execute()` 时创建 | API 构建阶段创建的 `WindowedStreamImpl` 无法获取工厂，时间点不匹配 |

### 2.3 Transformation DAG

每个 DataStream 操作在内部创建一个 `Transformation` 节点，注册到 `StreamExecutionEnvironment`：

| Transformation | 用途 | 输入数 |
|---|---|---|
| `SourceTransformation` | 数据源 | 0 |
| `OneInputTransformation` | map/filter/flatmap/window | 1 |
| `PartitionTransformation` | keyBy 分区 | 1 |
| `SinkTransformation` | 输出 | 1 |

Transformation 通过 `getInputs()` 构成 DAG。执行时从 Sink 回溯到 Source。

### 2.4 API 使用契约

- 用户必须显式调用 `env.execute()` 触发执行，`addSink()` / `print()` 只注册 Transformation
- `env.execute()` 只能调用一次（设 `executed = true` 防止重复）
- `map()` 和 `flatMap()` 的输出类型信息当前为 `UnknownTypeInformation`，不影响运行时，但类型检查不完整

### 2.5 执行分发：StreamExecutionEnvironment

`StreamExecutionEnvironment` 负责收集 Transformation DAG 并在 `execute()` 时分发到正确的执行后端。

**核心字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `deploymentMode` | `DeploymentMode` | LOCAL（线程池）或 DISTRIBUTED（多 TaskManager） |
| `executionDispatcher` | `IStreamExecutionDispatcher` | 执行分发 SPI，runtime 模块实现 |
| `components` | `StreamComponents` | 统一组件注册表，构造时通过 SPI 加载。随 API 调用链向下传递（见 §1.2.1） |

**`execute()` 的分发路径**：

```
execute(jobName)
   ├── 构建 StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan
   ├── 根据 deploymentMode 分发（LOCAL / DISTRIBUTED）
   └── 返回 StreamExecutionResult
```

**`DeploymentMode` 枚举**（core 模块）：

| 值 | 语义 |
|---|---|
| `LOCAL` | 单进程线程池执行，TaskExecutor 管理 |
| `DISTRIBUTED` | 多 TaskManager 实例，通过 IStreamExecutionDispatcher 分发 |

**`IStreamExecutionDispatcher` SPI**（core 模块定义，runtime 实现）：

```java
interface IStreamExecutionDispatcher {
    boolean supportsDeploymentMode(DeploymentMode mode);
    StreamExecutionResult execute(JobGraph, PartitionedPlan, DeploymentPlan) throws Exception;
}
```

## 3. 算子模型

### 3.1 核心接口层次

所有算子相关类统一位于 `io.nop.stream.core.operators` 包。

```
StreamOperator<OUT>           算子基础接口（生命周期 + checkpoint + key context）
  └── OneInputStreamOperator<IN, OUT>   单输入算子（processElement + watermark）
        ├── StreamSourceOperator<T>     数据源算子
        ├── StreamSinkOperator<T>       输出算子
        ├── StreamMap<IN, OUT>          映射算子
        ├── StreamFilter<IN>            过滤算子
        ├── StreamFlatMap<IN, OUT>      扁平映射算子
        └── WindowOperator<K,IN,ACC,OUT,W>  窗口算子（runtime 模块）

StreamOperatorFactory<OUT>    算子工厂接口
  └── SimpleStreamOperatorFactory<OUT>  简单工厂实现

ChainingStrategy              链化策略枚举（ALWAYS / NEVER / HEAD / HEAD_WITH_SOURCES）
```

### 3.2 算子生命周期

```
open()           初始化（状态后端、timer service 等）
processElement() 处理每条数据
processWatermark() 处理 watermark（推进事件时间）
finish()         所有数据处理完毕
close()          释放资源
```

### 3.3 Output 机制

算子通过 `Output<StreamRecord<T>>` 接口向下游发送数据，采用推模型：

| Output 实现 | 用途 |
|---|---|
| `ChainingOutput<T>` | 直接调用下游算子的 `processElement()`（推模型） |
| `KeyExtractingOutput<T>` | 在 ChainingOutput 前插入 key 提取逻辑 |
| `TimestampedCollector<T>` | 带时间戳的输出收集器（WindowOperator 使用） |

### 3.4 算子链化

当前实现始终将所有算子链化为一条链：

```
Source → [ChainingOutput] → Map → [ChainingOutput] → Window → [ChainingOutput] → Sink
```

Flink 的 ChainStrategy（HEAD/ALWAYS/NEVER）和 slot sharing group 等复杂概念在 nop-stream 中完全去除。

## 4. 稳定身份体系

### 4.1 设计原则

所有持久状态必须有稳定身份。状态路径不能包含 `deploymentId`、`runId` 或 `attemptId`。

### 4.2 身份层次

| 身份 | 作用域 | 生成规则 |
|------|--------|----------|
| `operatorId` | 算子级 | 用户显式 uid > 模型路径 > 结构 hash |
| `taskId` | task 实例级 | `TaskLocation`（包含 `jobId` + `pipelineId` + `vertexId` + `taskIndex`） |
| `pipelineId` | 管线级 | 作业内的管线标识，用于分布式执行时的路由 |
| `checkpointNamespace` | checkpoint 级 | savepoint 的隔离命名空间，支持 protected namespace |
| `StateShard` | 状态路由级 | 替代 Flink key-group，为 keyed state 提供确定性分片路由 |

### 4.3 TaskLocation

```java
class TaskLocation {
    String jobId;
    String pipelineId;
    String vertexId;
    int taskIndex;
}
```

当前单进程执行时，Coordinator 通过 `vertexId` + `taskIndex` 定位本地 task；分布式执行时，通过 `jobId` + `pipelineId` 路由到正确的节点。

### 4.4 状态路由

状态恢复必须按 `operatorId + subtaskIndex + stateShard + stateName` 路由。所有 keyed state 必须有确定性 `StateShard` 路由，保证恢复时状态与算子实例的正确映射。

## 5. 函数接口

### 5.1 用户函数接口

| 接口 | 用途 | 语义 |
|------|------|------|
| `MapFunction<IN, OUT>` | 映射 | 1 → 1 |
| `FlatMapFunction<IN, OUT>` | 扁平映射 | 1 → 0..N |
| `FilterFunction<IN>` | 过滤 | 1 → 0..1 |
| `KeySelector<IN, KEY>` | 键提取 | 1 → KEY |
| `ReduceFunction<T>` | 归约 | 2 → 1 |
| `AggregateFunction<IN, ACC, OUT>` | 聚合 | IN → ACC → OUT |
| `WindowFunction<IN, OUT, KEY, W>` | 窗口全量计算 | Iterable<IN> → OUT |
| `SourceFunction<T>` | 数据源 | → N |
| `SinkFunction<T>` | 输出 | IN → void |

所有函数接口都是 `Serializable`，保持与 Flink 的兼容性。虽然当前单 JVM 执行不需要序列化函数，但接口约束不变。

## 6. Side Output

当前 nop-stream 不支持侧输出（Side Output）。迟到数据、split 路由等场景使用以下替代方案：

| 场景 | 替代方案 | 与 Flink 的关系 |
|------|---------|----------------|
| 窗口迟到数据 | `allowedLateness` 机制开启时默认丢弃（无侧输出） | Flink 通过 `OutputTag` + `SideOutputDataStream` |
| 分支处理 | 使用 `flatMap()` + `Collector` 多次 `collect()` | Flink 通过 `OutputTag` + `sideOutput()` |
| 异常记录路由 | 在算子中显式路由到不同 sink | Flink 通过侧输出 tag |

**不实现侧输出的原因**：
1. 侧输出需要维护一套 OutputTag → StreamEdge 的映射，增加 Transformation DAG 管理复杂度
2. 可以通过 `flatMap()` + 显式多 sink 路由替代
3. 当前阶段不需要侧输出的核心用例

如果未来需要，可参考 Flink 的 `OutputTag<T>` + `SideOutputOutput<T>` + `SideOutputTransformation<T>` 模式实现。设计要点：
- `OutputTag` 需要 `TypeInformation` 和稳定 ID
- `SideOutputTransformation` 是 virtual transformation（不产生独立 StreamNode）
- 侧输出边在 StreamGraph 中作为额外的 StreamEdge（带 outputTag）处理

## 7. Operator State 模型

Operator State（非键控状态）存储算子的全局状态，与 key 无关。典型场景：

| 场景 | 状态内容 | Operator State 类型 |
|------|---------|-------------------|
| Source offset | 每个 source split 的消费位置 | `ListState<SplitState>`（SPLIT_DISTRIBUTE 模式） |
| Kafka partition 分配 | partition → subtask 映射 | `ListState<PartitionMapping>`（BROADCAST 模式） |
| 窗口元数据 | 合并窗口的映射集合 | `ListState<MergedWindow>` |
| CDC 快照阶段 | 表快照的发现进度 | `ListState<SnapshotProgress>` |
| 自定义 sink | 批处理计数器 | `ListState<BatchCounter>` |

### 7.1 与 Keyed State 的区别

| 维度 | Keyed State | Operator State |
|------|------------|---------------|
| 范围 | 每个 key 独立 | 每个 subtask 全局 |
| 分片 | 按 StateShard 确定性分片 | 按 subtask 实例（不按 key） |
| 并发放大镜 | subtask 内按 key 隔离 | subtask 间隔离 |
| 恢复模式 | key→shard 路由（确定性） | SPLIT_DISTRIBUTE / UNION / BROADCAST |

### 7.2 重分布模式

Operator State 的恢复需要指定重分布模式，以适应并行度变化：

| 模式 | 语义 | 使用场景 |
|------|------|---------|
| `SPLIT_DISTRIBUTE` | 状态按 subtask index round-robin 分配 | Source split 位置（最常用） |
| `UNION` | 所有新 subtask 获取所有旧 subtask 的状态全集，自行过滤 | 全局视图 |
| `BROADCAST` | 所有新 subtask 获取完全相同的一份状态拷贝 | 规则/配置信息 |

### 7.3 与 Flink 的差异

| 维度 | Flink | nop-stream（设计） |
|------|-------|-------------------|
| 接口 | `CheckpointedFunction` + `OperatorStateStore` | `CheckpointedFunction` + `OperatorStateStore`（接口一致） |
| 状态后端 | 与 Keyed State 共享 StateBackend | 与 Keyed State 共享 IStateBackend |
| 重分布 | `ListCheckpointed`（split）+ `UnionListState` / `BroadcastState` | 三模式：SPLIT_DISTRIBUTE / UNION / BROADCAST |
| 快照 | operatorStateBackend.snapshot() | operatorStateBackend.snapshot() |
| 恢复 | operatorStateBackend.restore() | operatorStateBackend.restore() |

**当前缺口**：Operator State 尚未实现（见 `completion-roadmap.md` Phase 0.3）。

### 7.4 实现要求

1. `OperatorStateStore` 提供 `getOperatorState(ListStateDescriptor)` → `ListState<T>` 三种重分布模式
2. Operator State 参与 `TaskEpochSnapshot`，进入 checkpoint 持久化
3. 恢复时按重分布模式重新分配状态到各 subtask
4. `StreamComponents.checkpointParticipants` 记录使用 Operator State 的 operator
5. source offset checkpoint 必须通过 Operator State 实现（不可使用 keyed state 的 fake key 替代）

## 8. 关联设计

| 主题 | 文档 |
|------|------|
| 状态管理（ValueState/MapState/ListState、状态后端、序列化） | `state-management-design.md` |
| 时间与 Watermark 模型 | `time-model-design.md` |
| 窗口机制（四要素、WindowOperator、Timer Service） | `window-design.md` |
| 图模型（StreamGraph、JobGraph、算子链化融合） | `graph-model-design.md` |
| Checkpoint 与 Exactly-Once 处理 | `checkpoint-design.md` |
| 分布式 Exactly-Once（PartitionedPlan、StateShard、Epoch Manifest、CheckpointParticipant） | `checkpoint-design.md` |
