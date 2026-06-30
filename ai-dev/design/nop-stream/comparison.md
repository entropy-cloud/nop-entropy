# nop-stream 与 Flink / SeaTunnel 核心功能对比分析

> Status: active
> Created: 2026-05-19
> Updated: 2026-06-30（重写为深度核心功能对比，基于 Flink/SeaTunnel 源码审计）
> Parent: `README.md`（参考层）

---

## 1. 设计哲学

| 维度 | Flink | SeaTunnel (Zeta) | nop-stream |
|------|-------|-------------------|------------|
| **定位** | 通用分布式流批一体处理引擎 | 分布式数据集成平台（ETL 管道） | 声明式图模型驱动的可分布式流处理引擎 |
| **目标用户** | 实时数仓、流式分析、CEP 开发者 | 数据工程师（ETL 管道配置） | Nop 平台用户，需要嵌入式 CEP/窗口聚合的场景 |
| **核心抽象** | DataStream API / Table API / SQL | Config (HOCON) → Action DAG | StreamModel（可序列化算子图） |
| **运行后端** | 自有 Runtime（Akka/Netty） | Hazelcast IMDG（自有 Zeta Engine） | LOCAL 线程池 / DISTRIBUTED Nop RPC |
| **规模** | PB 级 | TB~PB 级 | GB~几十 GB 级 |
| **API 入口** | DataStream / SQL / Table API | HOCON 配置文件 | XDSL 声明式 / DataStream API / Delta（规划） |
| **框架定位** | 自包含全栈引擎 | 可切换后端（Flink/Spark/Zeta） | Nop 平台正式模块，复用平台基础设施 |

### 1.1 关键抽象对比

| 概念 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 管线模型 | `Transformation` DAG + `StreamGraph` | `Action` DAG（SourceAction/SinkAction/TransformAction） | `StreamModel` + `StreamComponents` |
| 物理执行图 | `ExecutionGraph`（3 层图） | `PhysicalPlan`（4 层图） | `JobGraph` → `PartitionedPlan`（2 层图） |
| 算子工厂 | `StreamOperatorFactory` + `ChainingStrategy` | `TableSourceFactory` / `TableSinkFactory` SPI | `StreamOperatorFactory` + `ChainingStrategy` |
| 状态后端 | `StateBackend`（HashMap/RocksDB） | 无独立后端（checkpoint 序列化到文件） | `IStateBackend`（仅 Memory） |
| 连接器 SPI | `SourceFunction` / `SinkFunction`（旧）| `SeaTunnelSource` / `SeaTunnelSink` | `SourceFunction` / `SinkFunction` |
| | `Source` / `Sink`（FLIP-27/FLIP-191 新）| 带 4 个类型参数 + Factory SPI | + `IBatchLoader` / `IBatchConsumer` 桥接 |
| 集群管理 | 自有 ResourceManager + Slot | Hazelcast IMap + 自有 ResourceManager | Nop 平台 `ILeaderElector` + `IDiscoveryClient` |

---

## 2. 核心数据模型

### 2.1 nop-stream: StreamModel + StreamComponents

nop-stream 以 **StreamModel** 为核心，是可序列化的算子图及其组件注册表（`StreamComponents`），包含 transforms、streams、windowingStrategies、coders、schemas、environments 和 requirements。

```java
class StreamModel {
    StreamComponents components;          // 统一组件注册表
    Map<String, Transformation> transformations; // Transformation DAG
    Set<StreamRequirement> getRequirements();
}
```

**三种入口**：XDSL 声明式、Java DataStream API、Delta 定制——最终生成同一套 canonical StreamModel。

### 2.2 Flink: Transformation + StreamGraph + JobGraph + ExecutionGraph

Flink 使用**三层（实际四层）图模型**：

| 层 | 类 | 作用域 | 可序列化 | 核心关注点 |
|---|-----|--------|---------|-----------|
| Transformation | `Transformation<T>` (`flink-core`) | API 构建时 | 否 | 用户意图：算子、类型、并行度、uid |
| StreamGraph | `StreamGraph` (`flink-runtime`) | Client 端生成 | 是 | 逻辑拓扑：StreamNode + StreamEdge |
| ExecutionGraph | `ExecutionGraph` (`flink-runtime`) | JobManager 调度 | 是 | 运行时调度：ExecutionJobVertex + ExecutionVertex + Execution |
| Task | `Task` (`flink-runtime`) | TaskManager 执行 | 否 | 物理执行：一个并行实例 |

**关键差异**:

- Flink 有个独立的 `ExecutionGraph` 层（包含 ExecutionVertex + ExecutionAttempt 的三层抽象），管理调度状态机（CREATED → SCHEDULED → DEPLOYING → RUNNING → FINISHED/FAILED/CANCELED）。nop-stream 用 `IStreamExecutionDispatcher` SPI + `DeploymentMode` 枚举替代，不需要这个三层抽象。
- Flink 的 `Transformation` 也有两层：`Transformation<T>` 基类在 `flink-core` 中，具体子类（`OneInputTransformation`、`SourceTransformation` 等）在 `flink-streaming-java` 中。StreamGraph 生成时使用 **Translator 模式**（每个 Transformation 子类有对应 `TransformationTranslator`）。
- Flink 的 `StreamGraph` 同时包含 `slotSharingGroup`、`coLocationGroup` 等调度提示。nop-stream 去除这些。

**Translator Pattern (Flink)**：

```java
// Flink 的 StreamGraphGenerator 使用策略模式映射 Transformation 类型到 Translator
private static final Map<Class<? extends Transformation>, TransformationTranslator<?, ?>> translatorMap;
static {
    tmp.put(OneInputTransformation.class, new OneInputTransformationTranslator<>());
    tmp.put(SourceTransformation.class, new SourceTransformationTranslator<>());
    tmp.put(SinkTransformation.class, new SinkTransformationTranslator<>());
    tmp.put(PartitionTransformation.class, new PartitionTransformationTranslator<>());  // Virtual
    tmp.put(UnionTransformation.class, new UnionTransformationTranslator<>());           // Virtual
}
```

**Virtual Transformation**：`PartitionTransformation`、`UnionTransformation`、`SideOutputTransformation` 不产生独立 StreamNode，只修改边的属性。nop-stream 的 `PartitionTransformation` 在 StreamGraph 中保留为独立节点，在 JobGraph 阶段优化掉——相同目标但实现不同。

### 2.3 SeaTunnel: Action + LogicalDag + ExecutionPlan + Pipeline + PhysicalPlan

SeaTunnel (Zeta Engine) 使用**四层图模型**：

| 层 | 类 | 作用域 | 职责 |
|---|-----|--------|------|
| Action | `SourceAction` / `SinkAction` / `TransformAction` | Client 解析 | 从 HOCON 配置解析出的算子封装 |
| LogicalDag | `LogicalDag`（LogicalVertex + LogicalEdge）| Client 端 | Action 的逻辑 DAG，无调度信息 |
| ExecutionPlan | `ExecutionPlan`（ExecutionVertex + ExecutionEdge + Pipeline）| Server 端 | 面向执行优化的计划，含 Pipeline 分组 |
| PhysicalPlan | `PhysicalPlan`（SubPlan + PhysicalVertex）| Server 端调度 | 运行时物理计划，含 Task 实例和线程分组 |

**SeaTunnel 的特色**：
- **Pipeline 隔离**：`ExecutionPlan` 按 shuffle 边界切分为多个 `Pipeline`，每个 Pipeline 可独立调度、独立 checkpoint、独立故障恢复
- **Action Chain 优化**：`TransformChainAction` 将相邻的多个 Transform Action 链化，与 SeTunnel source 相邻的 transform 自动链入 `SourceSeaTunnelTask`
- **TaskGroup（线程共享）**：多个 `PhysicalVertex` 可共享一个 `TaskGroupDefaultImpl` 线程（协程式调度），与 Flink 的每个 Task 独立线程不同
- **Enumerator 独立 Task**：`SourceSplitEnumeratorTask` 是独立的 coordinator task，在 JobMaster 侧运行，负责 split 发现与分配

**与 nop-stream 的对应**：

| SeaTunnel | nop-stream | 备注 |
|-----------|-----------|------|
| `SourceAction` | `SourceTransformation` | 同：source 入口 |
| `TransformAction` | `OneInputTransformation` | 同：转换操作 |
| `SinkAction` | `SinkTransformation` | 同：输出终点 |
| `TransformChainAction` | 算子链（OperatorChain） | 同：链化优化 |
| `Pipeline`（shuffle 边界） | `JobVertex`（chain 边界） | SeaTunnel 按 shuffle 边界分组，nop-stream 按 chain 边界分组 |
| `PhysicalVertex` | `Task` / `SubtaskTask` | 同：运行时执行单元 |

---

## 3. 执行管线与图模型

### 3.1 图转换层级对比

```
nop-stream:                              Flink:                                SeaTunnel:
StreamModel                              Transformation DAG                    Action DAG
    ↓                                       ↓                                       ↓
StreamGraph                              StreamGraph                          LogicalDag
(逻辑 DAG)                               (逻辑 DAG + slot 提示)                (逻辑 DAG)
    ↓                                       ↓                                       ↓
JobGraph                                 JobGraph                             ExecutionPlan
(算子链融合)                              (算子链融合 + 物理配置)                (Pipeline 分组)
    ↓                                       ↓                                       ↓
PartitionedPlan                          ExecutionGraph                       PhysicalPlan
(并行展开 + 分区)                         (调度状态机 + 故障恢复)               (Task 分组 + 线程共享)
    ↓
DeploymentPlan
(节点映射 + 配置)
```

**Flink 独特**：`ExecutionGraph` 层管理 `ExecutionVertex` → `Execution` 的调度状态机（CREATED→RUNNING→FINISHED/FAILED），每个 Execution 代表一次 attempt。nop-stream 不需要这层，因为 LOCAL 模式不需要分布式调度状态机，DISTRIBUTED 模式通过 `IStreamExecutionDispatcher` SPI 实现。

**SeaTunnel 独特**：`Pipeline` 隔离允许每个 Pipeline 独立 checkpoint 和独立恢复。nop-stream 当前按 JobGraph 整体 checkpoint（不按 Pipeline/Shuffle 边界隔离）。

**nop-stream 独特**：`PartitionedPlan` + `DeploymentPlan` 将"并行展开"和"节点映射"分离为两个独立层，每个都有完整的状态路由和 checkpoing ACK 集合信息。Flink 的这些信息分散在 ExecutionGraph 和 Scheduler 中。

### 3.2 算子链化机制

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 链化判据 | `ChainingStrategy`（ALWAYS/NEVER/HEAD/HEAD_WITH_SOURCES）+ parallelism + forward + slot sharing group | 相邻 `TransformAction` 自动链入 `TransformChainAction`；source 相邻的 transform 链入 `SourceSeaTunnelTask` | 6 条硬条件（forward、同并行度、无分支、无合并、非 source 上游、非 sink 下游） |
| 链内调用 | `ChainingOutput` 直接调用下游 `processElement()` | 同一 `TransformChainAction` 内顺序调用 | `ChainingOutput` 直接调用下游 `processElement()` |
| 跨链通信 | `RecordWriter` → Netty → `InputGate` | `IntermediateQueue`（BlockingQueue） | `RecordWriter` → `ResultPartition` → `InputChannel` → `RecordReader` |
| 链内生命周期 | Task 管理 Chain：先 open 所有算子（从尾到头），再处理数据 | Action chain 在同一步骤中顺序执行 | `OperatorChain` 统一管理 open/close |
| 用户控制 | `disableChaining()` / `startNewChain()` | 自动判定 | 自动判定，无用户控制 API |

### 3.3 Task 执行模型

| 维度 | Flink | SeaTunnel (Zeta) | nop-stream |
|------|-------|-------------------|------------|
| 执行单元 | `StreamTask` 子类（`OneInputStreamTask` 等）+ 每个 Task 一个线程 | `SeaTunnelTask` 子类（`SourceSeaTunnelTask` 等）+ 多个 Task 共享 TaskGroup 线程 | `Task` / `SubtaskTask` + `TaskExecutor` 线程池 |
| 处理循环 | Mailbox 事件循环：`processInput()`（处理网络输入）+ `processMailbox()`（timer/checkpoint） | `pollNext()` 循环 + checkpoint barrier 中断 | `run()` 循环：`processElement()` → `processWatermark()` |
| 输入处理器 | `StreamInputProcessor`（读取 `CheckpointedInputGate` → demux 成 records/watermarks/barriers） | `Collector`（checkpoint lock）+ barrier 作为特殊 record | `RecordReader`（读取 `InputChannel` → `StreamElement` 队列） |
| 生命周期 | `invoke()`: `beforeInvoke()` → `run()` → `afterInvoke()` → `cleanUp()` | `init()` → `pollNext()` 循环 → `close()` | `open()` → `invoke()` → `close()`（在 finally 中保证） |
| 检查点交互 | `prepareSnapshotPreBarrier()` → `snapshotState()`（return `OperatorSnapshotFutures`，同步+异步两阶段） | `snapshotState()`（同步）+ `CheckpointBarrier` 作为 record 通过数据流传播 | `processBarrier()` → `snapshotState()`（同步 + 传播 + ACK） |

**Flink Mailbox 模式差异**：Flink 使用 `MailboxProcessor` 事件循环——正常处理循环是 `processInput()`，可被 `MailboxDefaultAction` 中断来处理 `Mail`（timer、checkpoint action）。nop-stream 没有这个模式，使用更简单的线程执行模型。Mailbox 模式的优势是单线程避免了并发同步问题，但增加复杂度。

**SeaTunnel TaskGroup 差异**：SeaTunnel 的多个 Task 共享一个线程（`TaskGroupDefaultImpl`），使用协程式调度。nop-stream 每个 Task 一个线程。TaskGroup 的优势是减少线程数，劣势是单个 Task 阻塞会影响同组其他 Task。

---

## 4. API 层

### 4.1 DataStream API 对比

| 操作 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 创建环境 | `StreamExecutionEnvironment.getExecutionEnvironment()` | HOCON config → `SeaTunnel` 入口 | `new StreamExecutionEnvironment()` |
| 数据源 | `env.addSource(new SourceFunction)` / `env.fromSource(Source)` | `Source { ... }` 配置块 | `env.addSource(new SourceFunction)` |
| Map | `ds.map(new MapFunction)` | `Transform { ... }` + SQL transform | `ds.map(new MapFunction)` |
| FlatMap | `ds.flatMap(new FlatMapFunction)` | `Transform { ... }` + SQL transform | `ds.flatMap(new FlatMapFunction)` |
| Filter | `ds.filter(new FilterFunction)` | SQL `WHERE` | `ds.filter(new FilterFunction)` |
| KeyBy | `ds.keyBy(new KeySelector)` | `source.partitionBy(...)` | `ds.keyBy(new KeySelector)` |
| Window | `keyed.window(TumblingEventTimeWindows.of(Time.seconds(5)))` | SQL 窗口或自定义 plugin | `keyed.timeWindow(Time.seconds(5))` |
| Reduce | `keyed.timeWindow().reduce(new ReduceFunction)` | 无原生窗口聚合 | `keyed.timeWindow().reduce(new ReduceFunction)` |
| Aggregate | `keyed.timeWindow().aggregate(new AggregateFunction)` | 无原生窗口聚合 | `keyed.timeWindow().aggregate(new AggregateFunction)` |
| Sink | `ds.addSink(new SinkFunction)` / `ds.sinkTo(Sink)` | `Sink { ... }` 配置块 | `ds.addSink(new SinkFunction)` |
| CEP | `CEP.pattern(ds, pattern).select(...)` | 无原生 CEP | `CEP.pattern(ds, pattern).select(...)` |
| Time/Watermark | `ds.assignTimestampsAndWatermarks(WatermarkStrategy)` | 内部处理（不暴露给用户） | `ds.assignTimestampsAndWatermarks(WatermarkStrategy)` |
| Side Output | `ds.sideOutput(outputTag)` / `OutputTag` | 无侧输出概念 | 无（用 flatMap 替代） |
| SQL | `TableEnvironment.sqlQuery("SELECT ...")` | `Transform { Sql { ... } }` | ❌ 明确不实现 |
| 双流 Join | `ds1.join(ds2).where(...).equalTo(...).window(...)` | 通过 SQL 或外部插件 | ❌ 明确不实现 |

### 4.2 API 设计模式对比

| 模式 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 构建方式 | 编程式 Builder（DSL chain） | 声明式 HOCON 配置 | 编程式 DSL + 规划 XDSL 声明式 |
| 类型信息 | `TypeInformation` + `TypeHint` | `SeaTunnelRowType` 定义列 | `TypeInformation`（Flink 兼容接口） |
| 输出类型 | 自动推断（`returns()` 方法覆盖） | 由 CatalogTable 定义 Schema | `UnknownTypeInformation`（待完善） |
| UID 设置 | `ds.uid("my-operator")` 用于 savepoint 兼容 | 无 UID 概念（基于配置 ID） | `transformation.setUid()`（匹配 Flink 模式） |
| 算子工厂 | `StreamOperatorFactory` SPI | `TableSourceFactory` / `TableSinkFactory` SPI | `StreamOperatorFactory`（匹配 Flink 模式） |

**SeaTunnel 的配置式 API** 是 nop-stream 规划中 XDSL 声明式入口的重要参考：

```hocon
// SeaTunnel HOCON 配置示例
env {
  parallelism = 2
  checkpoint.interval = 60000
}

source {
  Kafka {
    topic = "input"
    schema = { fields { name = string, age = int } }
  }
}

transform {
  Sql {
    query = "SELECT name, age FROM input WHERE age > 18"
  }
}

sink {
  Console {}
}
```

nop-stream 规划的 XDSL 声明式入口应类似：

```xml
<!-- nop-stream XDSL 规划 -->
<stream:StreamModel x:extends="base">
    <source name="source1" xdef:ref="KafkaSource">
        <property name="topic" value="input"/>
    </source>
    <transform name="filter1" xdef:ref="FilterTransform">
        <property name="condition" value="age > 18"/>
    </transform>
    <sink name="sink1" xdef:ref="ConsoleSink"/>
    <edge from="source1" to="filter1" partitioner="FORWARD"/>
    <edge from="filter1" to="sink1" partitioner="FORWARD"/>
</stream:StreamModel>
```

### 4.3 窗口 API 完整度

| 特性 | Flink | nop-stream |
|------|-------|------------|
| Tumbling EventTime Window | ✅ | ✅ |
| Sliding EventTime Window | ✅ | ✅ |
| Session EventTime Window | ✅ | ⚠️ 设计完成，MergingWindowAssigner 路径已定义 |
| Tumbling ProcessingTime Window | ✅ | ✅（接口存在） |
| Count Window | ✅ | ✅ |
| Global Window | ✅ | ✅ |
| `Evictor` | ✅ | ✅（CountEvictor/TimeEvictor/DeltaEvictor） |
| `Trigger`（Custom） | ✅ | ✅（完整移植） |
| `allowedLateness` | ✅ | ✅ |
| Side Output (late data) | ✅ | ⚠️ 接口预留未实现 |
| Session Window Merge | ✅ | ⚠️ MergingWindowSet 已实现，端到端验证未完成 |
| PaneState (early/on-time/late) | ✅ | ⚠️ 模型定义完成，算子集成未完成 |

---

## 5. 算子模型

### 5.1 核心接口层次对比

| Flink | SeaTunnel | nop-stream |
|-------|-----------|------------|
| `StreamOperator<OUT>` | 无算子接口（按 Action 类型分发） | `StreamOperator<OUT>` |
| `OneInputStreamOperator<IN, OUT>` | `SeaTunnelMapTransform<T>` / `SeaTunnelFlatMapTransform<T>` | `OneInputStreamOperator<IN, OUT>` |
| `TwoInputStreamOperator<IN1, IN2, OUT>` | 无等价物 | ❌ 不实现 |
| `StreamOperatorFactory<OUT>` | `TableSourceFactory` / `TableSinkFactory` / `TableTransformFactory` | `StreamOperatorFactory<OUT>` |
| `AbstractStreamOperator<OUT>` | 无（Action 直接实现运行逻辑） | `AbstractStreamOperator<OUT>` |
| `AbstractUdfStreamOperator<OUT, F>` | 无（transform 直接包含函数） | `AbstractUdfStreamOperator<OUT, F>` |

**SeaTunnel 的差异**：SeaTunnel 没有 StreamOperator 概念。源代码中的 Action 直接包含用户函数（`SeaTunnelTransform.map()`），执行时由 `SourceSeaTunnelTask` / `TransformSeaTunnelTask` 驱动。这比 Flink/nop-stream 的 StreamOperator 层次更简单，但灵活性更低（无法插入前置/后置处理逻辑）。

### 5.2 算子生命周期

| 阶段 | Flink | nop-stream |
|------|-------|------------|
| 构造 | `operatorFactory.createStreamOperator()` | `operatorFactory.createStreamOperator()` |
| 设置 | `setup(streamTask, config, output)` | `setup(output)` |
| 初始化状态 | `initializeState(StateInitializationContext)` | `initializeState(StateInitializationContext)` |
| 打开 | `open()` | `open()` |
| 处理 | `processElement()` / `processWatermark()` | `processElement()` / `processWatermark()` |
| 完成 | `finish()` | `finish()` |
| 关闭 | `close()` | `close()` |
| 快照 | `snapshotState(checkpointId, timestamp, ...)` → `OperatorSnapshotFutures` | `snapshotState(operatorStateStore)` |
| 预备屏障 | `prepareSnapshotPreBarrier(checkpointId)` | (nop-stream 无此独立阶段) |

**关键差异**：
- Flink 的 `snapshotState()` 返回 `OperatorSnapshotFutures`（异步快照结果），分同步和异步两阶段。nop-stream 当前是同步快照。
- Flink 有 `prepareSnapshotPreBarrier()`：在 barrier 发射之前做出最后的记录输出（如触发 watermark）。nop-stream 合并到 barrier 处理中。
- SeaTunnel 的 `SourceReader.snapshotState()` 只返回 split 列表状态，没有完整的算子状态快照机制。

### 5.3 Output 机制对比

| 概念 | Flink | nop-stream |
|------|-------|------------|
| 链内传递 | `ChainingOutput`（直接方法调用） | `ChainingOutput`（直接方法调用） |
| 跨链传递 | `RecordWriterOutput` → Netty `RecordWriter` → NetworkBuffer → `InputGate` | `RecordWriter` → `ResultPartition` → `InputChannel` → `RecordReader` |
| Barrier 传递 | 作为 `CheckpointBarrier` 混入数据流 | 作为 `CheckpointBarrier`（`StreamElement` 子类）混入数据流 |
| Watermark 传递 | `Watermark` 对象通过 Output 传播 | `Watermark` 对象通过 Output 传播 |
| Side Output | `OutputTag` + `SideOutputOutput` | 无（计划用 flatMap 替代） |
| 延迟标记 | `LatencyMarker` | 无 |

---

## 6. 状态管理

### 6.1 状态类型对比

| 状态类型 | Flink | SeaTunnel | nop-stream |
|----------|-------|-----------|------------|
| `ValueState<T>` | ✅ HashMap/RocksDB | 无等效用户状态 | ✅ Memory |
| `ListState<T>` | ✅ | 无（connector 可自定义） | ✅ Memory |
| `MapState<K, V>` | ✅ | 无 | ✅ Memory |
| `ReducingState<T>` | ✅ | 无 | ✅ Memory（通过 `InternalAppendingState`） |
| `AggregatingState<IN, ACC, OUT>` | ✅ | 无 | ✅ Memory（`InternalAppendingState` + `AggregatingStateDescriptor`） |
| `BroadcastState<K, V>` | ✅ | 无 | ❌ 不实现 |
| Operator State | ✅ `OperatorStateStore` | ✅ `SourceReader.snapshotState()` 返回 `List<SplitT>` | ❌ 缺口（Phase 0.3 规划） |

### 6.2 状态后端对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 内存后端 | `HashMapStateBackend` | 无独立后端 | `MemoryStateBackend` + `MemoryKeyedStateBackend` |
| RocksDB | `EmbeddedRocksDBStateBackend` | 无 | ❌ 规划 |
| 状态分片 | **Key-Group**：`maxParallelism` + `KeyGroupRange` | 无 | **StateShard**：固定不可变 `stateShardCount` |
| Operator State | 接口完整 + CheckpointedFunction | 仅 Source Split 状态 | ❌ 缺口 |
| Keyed State 接口 | `KeyedStateStore.getState(desc)` | 无 | `IKeyedStateBackend.getState(desc)` + `getInternalAppendingState(desc)` |
| Operator State 接口 | `OperatorStateStore.getListState(desc)` (RedistributionMode: SPLIT_DISTRIBUTE/UNION/BROADCAST) | 无 | ❌ 缺口 |
| Checkpoint 存储 | `CheckpointStorage` (FileSystem/JobManager) | `CheckpointStorage` (LocalFile/HDFS) | `ICheckpointStorage` (LocalFile/JDBC) |
| State TTL | ✅ `StateTtlConfig` | 无 | ❌ |

### 6.3 Key-Group vs StateShard 对比

Flink 的 Key-Group 模型是 nop-stream StateShard 的重要参考：

| 维度 | Flink Key-Group | nop-stream StateShard |
|------|-----------------|----------------------|
| 数学定义 | `groupId = hash(key) % maxParallelism` | `shardId = stableHash(key) % stateShardCount` |
| 上界 | `maxParallelism`（默认 128，建议 ≤ 32768） | `stateShardCount`（固定） |
| SubTask 映射 | 静态：`KeyGroupRangeAssignment.assignToKeyGroupRange()` | 固定：`shardId → ownerSubtask` 在 `PartitionedPlan` 中记录 |
| 并行度变化 | Key→Group 映射不变，Group→SubTask 范围重算，状态局部恢复 | `stateShardCount` 不变时 `ownerSubtask` 可以重算，但 count 变化需要显式迁移 |
| 运行时状态查找 | `currentKey` → `KeyGroupRange` → 本地或远程 | `currentKey` → `stateShardId` → 本地或远程 |
| 恢复效率 | 增量恢复（SST 文件按 Key-Group 读取） | 全量恢复（按 stateShard 读取） |
| 与序列化耦合 | 深度耦合（TypeSerializerSnapshot + KeyGroup 前缀） | 轻量（JSON 序列化，Key 只是字符串 hash） |

**nop-stream 采用 StateShard 而非 Key-Group 的原因**：
- 不需要 Flink 的动态并行度调整
- 不需要 Flink 的序列化器体系来编码 key
- 当前状态大小（几十 GB）可以用 JSON + Memory 后端承载
- Key-Group 的优势在大状态（100GB+）+ RocksDB + 增量 checkpoint 时才充分体现

### 6.4 InternalAppendingState 设计要点

nop-stream 的 `InternalAppendingState<K,N,IN,ACC,OUT>` 是窗口聚合状态的核心。Flink 也有类似概念（`MergingState` + `InternalAppendingState`），但 nop-stream 的版本经过简化：

| 特性 | Flink | nop-stream |
|------|-------|------------|
| Reducing 模式 | `ReducingStateDescriptor` → `InternalReducingState` | `getInternalAppendingState(ReducingStateDescriptor)` |
| Aggregating 模式 | `AggregatingStateDescriptor` → `InternalAggregatingState` | `getInternalAppendingState(AggregatingStateDescriptor)` |
| Namespace 支持 | 默认 namespace（`VoidNamespace`）+ 自定义（WindowOperator 中） | 泛型 N（WindowOperator 中 namespace = Window） |
| 内部存储 | RocksDB/Flink 序列化 | Memory HashMap + JSON |

---

## 7. Checkpoint 与 Exactly-Once

### 7.1 Barrier 机制对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| Barrier 类型 | `CheckpointBarrier`（id + timestamp + options） | `CheckpointBarrier`（id + timestamp + type + prepareCloseTasks） | `CheckpointBarrier`（id + timestamp） |
| 注入方式 | Source Operator 通过 `SourceOperator` 的 RPC 触发 | Coordinator 发送 `CheckpointBarrierTriggerOperation` 给所有 starting subtask | Source 读取线程在 safe point 注入 |
| 对齐算法 | `SingleCheckpointBarrierHandler` + `CheckpointedInputGate`（aligned/unaligned） | `SeaTunnelTask` 收到 barrier 后 snapshot 并传播 | `InputGate` 内联对齐逻辑（已实现 `BarrierAligner` 未启用） |
| Unaligned | ✅ 完整 | ❌ 无 | ❌ 规划（Phase 4） |
| 对齐超时 | `checkpointing.alignment-timeout` | 无 | ✅ `barrierAlignmentTimeout`（默认 30s） |
| 超时后行为 | aligned→unaligned 回退（或 task 失败） | 无 | Task FAILED → 触发恢复 |
| 多输入对齐 | `CheckpointBarrierHandler` 统一管理 | 单输入为主 | `InputGate.readMultiChannel()` 内联 |

### 7.2 Checkpoint 协调对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 协调器 | `CheckpointCoordinator`（JobManager 内） | `CheckpointCoordinator`（JobMaster 内，per pipeline） | `CheckpointCoordinator`（`GraphExecutionPlan` 构建时创建） |
| Checkpoint 触发 | 周期性定时器 + 手动 savepoint | 周期性定时器 + savepoint | 周期性定时器 + 手动 |
| Pending 管理 | `Map<Long, PendingCheckpoint>` + 状态机（多个并发） | `Map<Long, PendingCheckpoint>`（当前仅一个） | `Map<Long, PendingCheckpoint>`（当前强制 `maxConcurrent=1`） |
| ACK 机制 | JobManager 收集所有 Task 的 ACK | Coordinator 收集所有 Task 的 ACK per pipeline | Coordinator 收集所有 Task 的 ACK |
| Snapshot 内容 | `TaskStateSnapshot`（operator state + keyed state + timers） | `ActionSubtaskState`（serialized byte[] per action） | `TaskEpochSnapshot`（operator snapshots + keyed state shards + timers） |
| Complete 条件 | 所有 Task ACK | 所有 Task ACK per pipeline | 所有 Task ACK |
| 存储 | `CompletedCheckpointStore`（FS/JobManager）| `CheckpointStorage`（LocalFile/HDFS） | `ICheckpointStorage`（LocalFile/JDBC） |
| 恢复 | 从最新 CompletedCheckpoint 全局恢复 | 从最新 CompletedCheckpoint per pipeline 恢复 | 从最新 durable epoch manifest 恢复 |
| Coordinator HA | `StandaloneCheckpointIDCounter` + `ZooKeeperCompletedCheckpointStore` | Hazelcast IMap HA | 规划（Phase 3：`ILeaderElector` + `SysDaoLeaderElector`） |

### 7.3 Two-Phase Commit 对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| Sink 2PC | `TwoPhaseCommitSinkFunction`（preCommit → commit → abort） | `SinkWriter.prepareCommit()` + `SinkAggregatedCommitter.commit()` | `TwoPhaseCommitSinkFunction` + `CheckpointParticipant` 泛化 |
| 参与者 | 仅 sink | `SinkAggregatedCommitter` 全局协调 | 泛化所有 transactional operator（source + sink + external state） |
| Transaction 身份 | `transactionId = operatorId + subtaskIndex + epochId` | 无显式 identity（依赖外部系统事务） | `{jobId}:{pipelineId}:{operatorId}:{subtaskIndex}:{epochId}` |
| Commit 幂等 | 用户负责实现 | `SinkCommitter.commit()` 应幂等 | 依赖 transaction id 幂等查询或重复 commit |
| 回滚 | abort 最新 durable epoch 之后的 non-durable transaction | `SinkCommitter.abort()` / `SinkAggregatedCommitter.abort()` | 保留 pending transaction → subsuming commit → 或全局恢复时 abort |

### 7.4 Exactly-Once 能力对比

| 核心能力 | Flink | SeaTunnel | nop-stream |
|----------|-------|-----------|------------|
| Source 可重放 | ✅ Kafka offset / FS 位置 | ✅ SourceReader snapshotState | ✅ Replayable source 接口 |
| Sink 严格提交 | ✅ TwoPhaseCommitSinkFunction | ✅ SinkCommitter + SinkAggregatedCommitter | ✅ CheckpointParticipant + TwoPhaseCommitSinkFunction |
| State 快照 | ✅ Keyed + Operator state | ✅ ActionSubtaskState | ✅ Keyed state（Operator state 缺口） |
| Barrier 对齐 | ✅ Aligned + Unaligned | ✅ Aligned only | ✅ Aligned（Unaligned 规划） |
| 故障恢复 | ✅ 区域级/全局 + checkpoint | ✅ per Pipeline 恢复 | ✅ 全局 epoch 恢复 |
| Fencing | ✅ ExecutionAttempt | ✅ Hazelcast lease | ✅ fencingToken + epochId |
| Exactly-Once 校验 | ✅ 编译期 + 运行时 | ⚠️ 部分（依赖 sink 实现） | ✅ `StreamRequirement` + `StreamBackendCapability` |

**核心缺口**：nop-stream 缺失 Operator State 体系，意味着 source offset、split assignment 等无法 checkpoint。这直接阻碍源端 exactly-once 的实现。

---

## 8. 时间模型与 Watermark

### 8.1 Watermark 生成对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 策略接口 | `WatermarkStrategy<T>`（统一入口） | 无暴露给用户（引擎内部处理） | `WatermarkStrategy<T>`（完整移植） |
| TimestampAssigner | `TimestampAssigner<T>`（从事件提取时间戳） | 无 | `TimestampAssigner<T>` |
| WatermarkGenerator | `WatermarkGenerator<T>`（onEvent + onPeriodicEmit） | 无 | `WatermarkGenerator<T>` |
| 单调递增 | `AscendingTimestampsWatermarks` | 无 | `AscendingTimestampsWatermarks` |
| 有界乱序 | `BoundedOutOfOrdernessWatermarks` | 无 | `BoundedOutOfOrdernessWatermarks` |
| 空闲检测 | `withIdleness(Duration)` | 无 | `withIdleness(Duration)`（接口存在） |
| 对齐 | `withWatermarkAlignment(group, drift)` | 无 | `withWatermarkAlignment(group, drift)`（接口存在） |
| 算子 | `TimestampsAndWatermarksOperator`（自动插入） | 无（内部实现） | `TimestampsAndWatermarksOperator`（未自动插入） |

### 8.2 Watermark 传播对比

| 维度 | Flink | nop-stream |
|------|-------|------------|
| 传播机制 | `Output.emitWatermark()` → 下游 `processWatermark()` | 同（移植自 Flink） |
| 多输入合并 | `StatusWatermarkValve`（per-input watermark 跟踪 + idle 检测 + 组合 watermark = min of active inputs） | `WatermarkOutputMultiplexer`（存在但未使用）+ `CombinedWatermarkStatus` |
| 空闲标记 | `WatermarkStatus`（ACTIVE / IDLE） | `WatermarkStatus`（ACTIVE / IDLE） |
| 周期性发射 | `autoWatermarkInterval` 配置 + `onPeriodicEmit` | 硬编码 `watermarkInterval=0`（未生效） |
| 并行源对齐 | `WatermarkAlignment`（group + drift + min ahead + max drift） | 接口存在但无 coordinator 支持 |

### 8.3 集成状态

SeaTunnel 内部使用系统时间戳，不暴露事件时间/watermark 给用户。Flink 和 nop-stream 共享相同的水位线模型。nop-stream 的接口完整移植自 Flink，但：
- `TimestampsAndWatermarksOperator` 未自动插入图模型管线
- `watermarkInterval` 硬编码为 0（周期性发射不生效）
- 多输入 watermark 合并未接入执行路径

---

## 9. 窗口机制

### 9.1 窗口模型对比

| 维度 | Flink | nop-stream |
|------|-------|------------|
| 窗口四要素 | WindowAssigner + Trigger + Evictor + WindowFunction | 同（移植自 Flink） |
| WindowAssigner | Tumbling/Sliding/Session/Global + EventTime/ProcessingTime | 同 |
| Trigger | EventTime/ProcessingTime/Count/Continuous/Purging/Delta/ProcessingTimeout | 同（完整移植） |
| Evictor | Count/Time/Delta | 同 |
| WindowingStrategy | 无显式模型对象（分散在配置中） | `WindowingStrategy` 显式模型，参与 fingerprint |
| 统一算子 | `WindowOperator`（单一算子处理所有窗口类型） | `WindowOperator`（同） |
| 内部函数适配 | `InternalWindowFunction` + `ListState` / `AggregatingState` 策略 | `InternalWindowFunction` + `InternalAppendingState` / `InternalListState` 策略 |
| 合并窗口 | `MergingWindowSet` | `MergingWindowSet` |
| Timer Service | `InternalTimerService`（key + namespace + timestamp） | `InternalTimerService`（key + namespace + timestamp） |
| Pane 语义 | 完整（EARLY/ON_TIME/LATE） | 模型定义完成，算子集成未完成 |

### 9.2 增量 vs 全量路径

| 路径 | Flink | nop-stream |
|------|-------|------------|
| 增量（无 Evictor） + AggregateFunction | `AggregatingStateDescriptor` → `InternalAggregatingState` | `AggregatingStateDescriptor` → `InternalAppendingState`（`MemoryInternalAggregatingState`） |
| 增量（无 Evictor） + ReduceFunction | `ReducingStateDescriptor` → `InternalReducingState` | `ReducingStateDescriptor` → `InternalAppendingState`（`MemoryInternalAppendingState`） |
| 全量（ProcessWindowFunction） | `ListStateDescriptor` → `InternalListState` | `ListStateDescriptor` → `InternalListState`（`MemoryInternalListState`） |
| 有 Evictor | 一律 `ListStateDescriptor` → `InternalListState` | 同 |

### 9.3 窗口性能考虑

SeaTunnel 没有窗口抽象（窗口通过 SQL transform 实现），不在此对比。

nop-stream 的窗口实现与 Flink 的架构高度一致，主要差异：
1. 状态后端不同（Memory vs RocksDB）——大窗口（百万级 key）在 nop-stream 可能 OOM
2. Timer checkpoint 完整但未端到端验证
3. Pane 语义定义完整但未在 `emitWindowContents` 中集成

---

## 10. 连接器架构

### 10.1 Source 架构对比

| 维度 | Flink (FLIP-27) | SeaTunnel | nop-stream |
|------|-----------------|-----------|------------|
| Source 接口 | `Source<T, SplitT, StateT>` | `SeaTunnelSource<T, SplitT, StateT>` | `SourceFunction<T>`（旧） |
| Source Reader | `SourceReader<T, SplitT>` + `ReaderOutput<T>` | `SourceReader<T, SplitT>` + `Collector<T>` | `SourceContext<T>`（内联） |
| Split Enumerator | `SplitEnumerator<SplitT, StateT>` | `SourceSplitEnumerator<SplitT, StateT>` | ❌ 不实现（拆分片在 connector-design.md §4 设计未实现） |
| Split | `SourceSplit` | `SourceSplit`（`splitId()`） | `SourceWorkUnit`（设计） |
| 可重放性 | `Boundedness` + checkpoint offset | `Boundedness` + `snapshotState()` | `SourceFunction` 实现负责 |
| 工厂发现 | SPI + `DynamicTableSourceFactory` | `@AutoService(TableSourceFactory.class)` | 手动构造（`env.addSource()`） |
| 并行度 | `SourceReader` 实例数 = 并行度 | `SupportParallelism` marker | 当前固定 1 |

### 10.2 Sink 架构对比

| 维度 | Flink (FLIP-191) | SeaTunnel | nop-stream |
|------|------------------|-----------|------------|
| Sink 接口 | `Sink<InputT, CommitT, StateT, GlobalCommitT>` | `SeaTunnelSink<IN, StateT, CommitInfoT, AggregatedCommitInfoT>` | `SinkFunction<T>`（旧） |
| Sink Writer | `SinkWriter<InputT, CommitT, StateT>` | `SinkWriter<T, CommitInfoT, StateT>` | `SinkFunction.invoke()` |
| Committer | `Committer<CommitT>` | `SinkCommitter<CommitInfoT>` + `SinkAggregatedCommitter<CommitInfoT, AggregatedCommitInfoT>` | `TwoPhaseCommitSinkFunction`（2PC） |
| Exactly-Once | `Committer` + `State` | `prepareCommit()` + `commit()` + `abort()` | `CheckpointParticipant` + preCommit/commit/abort |
| 幂等性 | 用户负责 | `SinkCommitter.commit()` 应幂等 | Sink idempotent 声明 |

### 10.3 nop-stream 桥接模式 vs 原生连接器

nop-stream 的连接器主要通过 **桥接 nop-batch 的 Loader/Consumer 接口** 实现：

```java
// nop-stream 桥接模式（两适配器覆盖 nop-batch 所有实现）
BatchLoaderSourceFunction<S>   // IBatchLoaderProvider → SourceFunction
BatchConsumerSinkFunction<R>   // IBatchConsumerProvider → SinkFunction
```

**这种方式与 Flink/SeaTunnel 的差异**：

| 维度 | Flink 原生连接器 | SeaTunnel 连接器 | nop-stream 桥接方式 |
|------|-----------------|-----------------|-------------------|
| 连接器数量 | ~40+ | ~60+ | 取决于 nop-batch 实现（已有 4 Loader + 3 Consumer） |
| 开发成本 | 高（每个连接器需要 Source + Sink + Format + Config） | 高（每个连接器 5-7 个类） | 低（nop-batch 已有 Loader/Consumer 自动接入） |
| 灵活性 | 高（原生控制 split/offset/state） | 高（原生控制 split/offset/state） | 低（依赖 nop-batch 的 Loader/Consumer 抽象，无法精细控制并发度） |
| 分布式支持 | 完整（split-based 并行读取） | 完整（split-based 并行读取 + enumerator） | 有限（批数据源不支持动态 split，消息队列通过 IMessageService 适配） |
| Exactly-Once | 完整（offset checkpoint + 2PC） | 完整（source snapshot + sink commit） | 部分（消息队列 source 可实现 CheckpointParticipant，批 source 有限） |

### 10.4 SeaTunnel Connector 开发模式

SeaTunnel 的每个连接器遵循一致的模块结构，对 nop-stream 的 XDSL 连接器注册表设计有参考价值：

```
// SeaTunnel 连接器规范结构
connector-<name>/
    source/
        <Name>Source.java                      implements SeaTunnelSource
        <Name>SourceFactory.java              @AutoService(TableSourceFactory.class)
        <Name>SourceReader.java               implements SourceReader
        <Name>SourceSplit.java                implements SourceSplit
        <Name>SourceSplitEnumerator.java      implements SourceSplitEnumerator
    sink/
        <Name>Sink.java                        implements SeaTunnelSink
        <Name>SinkFactory.java                @AutoService(TableSinkFactory.class)
        <Name>SinkWriter.java                 implements SinkWriter
        <Name>SinkCommitter.java              (optional) implements SinkCommitter
    config/
        <Name>Config.java / Option constants
```

nop-stream 规划中的 XDSL 声明式入口应采用类似模式：通过 `StreamComponents` 注册表声明连接器组件，而非通过 SPI 动态发现。

---

## 11. CEP 引擎

nop-stream-cep 直接从 Flink CEP 剥离，两者架构一致：

| 组件 | Flink CEP | nop-stream-cep | 差异 |
|------|-----------|----------------|------|
| Pattern DSL | `Pattern.begin().where().followedBy()` | 同（直接剥离） | 无（类名、方法签名一致） |
| NFA | `NFA<T>` + `NFACompiler` | 同 | 无 |
| SharedBuffer | `SharedBuffer<T>` + Dewey 编号 + 引用计数 | 同 | 无 |
| CepOperator | `CepOperator<IN, OUT>` | 同 | nop-stream 使用 `SimpleKeyedStateStore` |
| 匹配后策略 | `AfterMatchSkipStrategy`（NoSkip/SkipPastLast/SkipToFirst/SkipToLast） | 同 | 无 |
| 事件时间超时 | `within(Time)` + `currentWatermark()` | 同（`currentWatermark()` 返回 `Long.MIN_VALUE`，不生效） | 未集成 watermark |
| 状态持久化 | `KeyedStateBackend` | `SimpleKeyedStateStore`（无 key 隔离） | 待修复 |
| 声明式模型 | 无 | `CepPatternModel` + `CepPatternBuilder` + XMeta | nop-stream 独有 |

**SeaTunnel 无 CEP 能力**。

---

## 12. 类型系统与序列化

### 12.1 类型系统对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 类型抽象 | `TypeInformation<T>` 层次（Basic/Composite/Generic） | `SeaTunnelDataType` 层次（BasicType/ArrayType/MapType/DecimalType） | `TypeInformation<T>`（Flink 兼容接口） |
| 行类型 | `Row` + `RowTypeInfo` | `SeaTunnelRow` + `SeaTunnelRowType`（强 Schema 约束） | `Row` + `RowTypeInfo`（移植） |
| 序列化器 | `TypeSerializer<T>` + `TypeSerializerSnapshot<T>`（完整版本兼容） | `Serializer<T>` 接口 + Java 序列化（默认） | `TypeSerializer<T>`（简化，无实际序列化） |
| 传递方式 | `TypeInformation` 在 Transformation 中传递 | `CatalogTable` + `TableSchema` 在 Action 中传递 | `TypeInformation` 在 Transformation 中传递 |
| 泛型擦除 | `TypeHint` 辅助信息 | 强类型 Schema（列名+类型+约束） | `UnknownTypeInformation`（当前默认） |

### 12.2 序列化策略对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 数据序列化 | Flink Serializer（自定义高性能）、Kryo、Avro | Java 序列化（默认）、Avro、Protobuf（可插拔） | `JsonTool.serialize()`（JSON） |
| Meta 序列化 | Java 序列化（可配置） | Java 序列化 | JSON（`JsonTool`） |
| 状态序列化 | TypeSerializer + Key-Group 编码 | Java 序列化（`byte[]`） | JSON（状态值 `snapshotState()` 时序列化） |
| Schema 版本 | `TypeSerializerSnapshot.resolveSchemaCompatibility()`（4 态：兼容/需迁移/需重配/不兼容） | 无 | `SerializerFingerprint`（2 态：兼容/不兼容） + `StateMigrationFunction` |
| Checksum | 可选 | 无 | 每个 segment 必须有 checksum |

**性能权衡**：
- Flink：二进制序列化，高性能但复杂度高
- SeaTunnel：Java 序列化 + Avro 可选，简单但性能中
- nop-stream：JSON 序列化，简单但体积大、速度慢（适合几十 GB 级别）

---

## 13. 部署与分布式执行

### 13.1 部署模式对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 单机模式 | `LocalStreamEnvironment`（同 JVM 线程） | 集群模式必需 | `DeploymentMode.LOCAL`（线程池） |
| 独立集群 | YARN/K8s/Standalone | Hazelcast 集群 | `DeploymentMode.DISTRIBUTED`（规划中通过 Nop RPC） |
| 资源管理 | Slot、SlotPool、ResourceManager | Hazelcast `ResourceManager` + `SlotProfile` | `IStreamExecutionDispatcher` SPI |
| 高可用 | ZooKeeper + Standalone HA / K8s | Hazelcast IMap 自动恢复 | 规划 Phase 3：`SysDaoLeaderElector` |
| 多 JVM | 独立进程（JobManager + TaskManagers） | Hazelcast nodes（CooperativeService） | `EmbeddedDistributedExecutor`（同 JVM 模拟）+ 规划跨 JVM |

### 13.2 分布式控制面对比

| 角色 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 主节点 | `JobManager`（JobGraph → ExecutionGraph → Scheduler） | `JobMaster`（LogicalDag → ExecutionPlan → PhysicalPlan → Pipeline 调度） | `JobCoordinator`（PartitionedPlan → DeploymentPlan → Task 分配） |
| 工作节点 | `TaskManager`（Slot → Task 执行） | `TaskExecutionServer`（TaskGroup → SeaTunnelTask 执行） | `TaskManager`（SubtaskTask 执行）+ `TaskExecutor` |
| 调度器 | `SchedulerNG`（ExecutionGraph 调度状态机 + Slot 分配） | `JobMaster.stateProcess()`（SubPlan 状态机 + ResourceManager 交互） | LOCAL: `TaskExecutor` 线程池<br>DISTRIBUTED: `EmbeddedDistributedExecutor` |
| 协调器 | `CheckpointCoordinator` | `CheckpointManager` + `CheckpointCoordinator`（per pipeline） | `CheckpointCoordinator`（per job） |
| RPC | Akka / RPC 抽象 | Hazelcast 操作（`CheckpointBarrierTriggerOperation` 等） | `IStreamTaskRpcService` / `IStreamCoordinatorRpcService` |
| 集群注册 | `ResourceManager` + `TaskExecutorRegistration` | Hazelcast 原生发现 | `ClusterRegistry`（`InMemoryClusterRegistry` 实现） |

### 13.3 nop-stream 分布式架构特色

nop-stream 的分布式模式采用**三面分离**架构，与 Flink 和 SeaTunnel 的分布式模型有根本差异：

| 面 | 职责 | nop-stream | Flink 等价物 | SeaTunnel 等价物 |
|---|------|-----------|-------------|-----------------|
| 控制面 | 作业调度、task 分配 | `IStreamTaskRpcService` / `IStreamCoordinatorRpcService` | Akka RPC | Hazelcast Operations |
| 数据面 | 记录传输、barrier/watermark | `IMessageService` + `RemoteResultPartition` / `RemoteInputChannel` | Netty + `NetworkBufferPool` | Hazelcast `IntermediateQueue` |
| 编排面 | Invokable 安装、算子链配置 | DSL + NopIoC 容器（跨 JVM 不下发对象，只下发 plan 描述） | Java 对象序列化 + RPC | Hazelcast 分布式数据 |

**三面分离的优势**：
- 面之间无耦合：可以独立替换传输实现（`IMessageService` 可选择 Pulsar/DB 等后端）
- 编排面不依赖运行时：跨 JVM 部署时只传输 DSL 描述，不需要序列化算子对象
- 控制面强类型：`IStreamTaskRpcService` / `IStreamCoordinatorRpcService` 是 Java 接口，可生成代理

---

## 14. 核心取舍总结

### 14.1 保留的核心功能

| 核心功能 | Flink 实现 | SeaTunnel 实现 | nop-stream 实现 | 成熟度 |
|----------|-----------|---------------|-----------------|--------|
| DataStream API | 完整 | HOCON 配置式 | 子集（map/filter/flatMap/keyBy/sink） | ✅ 高 |
| 窗口聚合 | 完整 | SQL 窗口仅 | Tumbling/Sliding/Count | ✅ 高 |
| Keyed State | Value/List/Map/Reducing/Aggregating | 无等效 | Value/List/Map + InternalAppendingState | ✅ 高 |
| Checkpoint | 分布式 barrier + 状态快照 | Barrier + ActionState | Barrier + Epoch Checkpoint + CheckpointParticipant | ✅ 高 |
| CEP | NFA + SharedBuffer | ❌ 无 | NFA + SharedBuffer（直接剥离） | ✅ 高 |
| 图模型 | 3 层图 | 4 层图 | 2 层图 + PartitionedPlan | ✅ 高 |
| Watermark | 完整 | 内部处理 | 接口完整（集成不完全） | ⚠️ 中 |
| 分布式执行 | 完整 | 完整 | 设计完成，实现进行中 | ⚠️ 中 |

### 14.2 推迟/不实现的功能

| 功能 | 在 Flink 中的状态 | 在 SeaTunnel 中的状态 | nop-stream 决策 |
|------|------------------|---------------------|-----------------|
| 双流 Join | ✅ 完整 | ✅ SQL Join | ❌ 不实现 |
| SQL API | ✅ Table API + SQL | ✅ SQL Transform | ❌ 不实现 |
| 大规模分布式 | ✅ 数千节点 | ✅ 数百节点 | ❌ 几十 GB 级别 |
| 异步算子 | ✅ AsyncDataStream | ❌ 无 | ❌ 不实现 |
| 动态并行度 | ✅ （通过 savepoint） | ⚠️ 有限 | ❌ 不实现（运行时） |
| Unaligned Checkpoint | ✅ | ❌ | ❌ 规划 Phase 4 |
| Side Output | ✅ | ❌ | ❌ 用 flatMap 替代 |
| Broadcast State | ✅ | ❌ | ❌ 不实现 |
| Operator State | ✅ | ✅ Source 状态 | ⚠️ 规划 Phase 0.3 |
| State TTL | ✅ | ❌ | ⚠️ 规划 Phase 1 |
| RocksDB 状态后端 | ✅ | ❌ | ⚠️ 规划 Phase 1 |
| Key-Group 重分布 | ✅ | ❌ | ⚠️ 规划 Phase 2 |
| Source Split 体系 | ✅ (FLIP-27) | ✅ | ⚠️ 规划 Phase 5 |
| 声明式编排 | ❌（SQL 除外） | ✅ HOCON | ⚠️ 规划 Phase 5 |
| Flink 后端适配 | — | ✅ translation layer | ⚠️ 规划 Phase 5 |

### 14.3 nop-stream 的独特价值

相比 Flink 和 SeaTunnel，nop-stream 在以下场景有独特优势：

| 场景 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 嵌入式 CEP（如欺诈检测） | 需部署 Flink 集群（重） | ❌ 不支持 | ✅ 作为库直接嵌入 Nop 应用 |
| Nop 平台深度集成 | ❌ | ❌ | ✅ IJdbcTemplate + IDialect + IBatchLoader + IEvalFunction + Delta 定制 |
| 零配置启动 | ❌ 需搭建集群 | ❌ 需 Hazelcast 集群 | ✅ `new StreamExecutionEnvironment().execute()` |
| StreamModel 可逆计算 | ❌ | ❌ | ✅ 三入口归一 + Delta + fingerprint |
| 分布式 exactly-once | ✅ 工业级 | ✅ 生产级 | ⚠️ 核心设计完整，实现进行中 |
| 连接器生态 | ✅ 丰富 | ✅ 丰富 | ✅ 通过 nop-batch 桥接覆盖 |


## 文档索引

| 主题 | 本系统设计文档 |
|------|---------------|
| 设计原则与约束 | `00-vision.md` |
| 架构基线与模块划分 | `01-architecture-baseline.md` |
| 核心模型 | `core-design.md` |
| 图模型与执行 | `graph-model-design.md` |
| Checkpoint | `checkpoint-design.md` |
| 状态管理 | `state-management-design.md` |
| 窗口机制 | `window-design.md` |
| 时间与 Watermark | `time-model-design.md` |
| 连接器 | `connector-design.md` |
| CEP 引擎 | `cep-design.md` |
| 组件路线 | `component-roadmap.md` |
| 完善路线图 | `completion-roadmap.md` |
