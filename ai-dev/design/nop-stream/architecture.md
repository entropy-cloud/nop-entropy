# nop-stream 整体架构

> Status: active
> Created: 2026-05-19

## 1. 定位与设计目标

nop-stream 是 Nop 平台的流处理引擎，定位为 **Flink 的简化实现**。

**核心目标**：
- 提供单流窗口操作能力（tumbling/sliding/count window）
- 支持基于主记录 ID 的 hash join 查询关联记录
- 覆盖 SeaTunnel 等常用 ETL 软件的核心功能
- 保持通用性，不限于特定业务场景

**明确不做的**：
- 分布式调度（JobManager/TaskManager 模型）
- 跨 JVM 的 RPC 通信
- Key-group 分区和重分布
- 大规模数据并行处理

**设计决策**：选择简化而非完整实现，原因：
1. Nop 平台的目标场景是中小规模 ETL，不是 PB 级流处理
2. 单 JVM 执行消除了分布式一致性问题的复杂性
3. API 设计与 Flink 保持兼容，未来可对接 Flink 后端作为执行层

## 2. 模块划分

```
nop-stream/
├── nop-stream-api          [空]  公共 API 接口定义（规划中）
├── nop-stream-core         [完整] 核心抽象和 API
├── nop-stream-connector    [完整] 连接器适配层（Batch/Message/CDC）
├── nop-stream-runtime      [部分] 运行时实现
├── nop-stream-cep          [完整] CEP 复杂事件处理引擎
├── nop-stream-checkpoint   [空]  检查点模块（规划中）
├── nop-stream-flink        [空]  Flink 后端适配（规划中）
├── nop-stream-flow         [空]  NopFlow 声明式编排（规划中）
└── nop-stream-fraud-example[完整] 欺诈检测示例
```

### 2.1 模块职责边界

| 模块 | 职责 | 依赖 |
|------|------|------|
| **nop-stream-core** | DataStream API 接口、算子基类、状态后端接口、Transformation DAG、StreamGraph/JobGraph 图模型、执行环境 | 无外部框架依赖 |
| **nop-stream-runtime** | WindowOperator 实现、CepWindowOperator、CheckpointCoordinator、TimestampsAndWatermarksOperator | 依赖 core |
| **nop-stream-cep** | Pattern DSL、NFA 编译、SharedBuffer、CepOperator、事件匹配 | 依赖 core |
| **nop-stream-connector** | 连接器适配层：BatchLoader/Consumer、MessageSource/Sink、DebeziumCdc | 依赖 core + nop-batch-core + nop-message-core(可选) + nop-message-debezium(可选) |
| **nop-stream-fraud-example** | 4 种欺诈检测模式的完整演示 | 依赖 cep |

### 2.2 分层设计

```
┌──────────────────────────────────────────┐
│  用户 API 层                              │
│  DataStream / KeyedStream / WindowedStream │
├──────────────────────────────────────────┤
│  Transformation 层                        │
│  Source / Sink / OneInput / Partition      │
│  Transformation DAG                       │
├──────────────────────────────────────────┤
│  执行层                                   │
│  StreamExecutionEnvironment               │
│  ├── 快速路径: chain + push               │
│  └── 完整路径: StreamGraph → JobGraph     │
│               → TaskExecutor (未对接)     │
├──────────────────────────────────────────┤
│  算子层                                   │
│  StreamMap / StreamFilter / StreamFlatMap │
│  WindowOperator / CepOperator             │
│  StreamSource / StreamSink                │
├──────────────────────────────────────────┤
│  状态 & 时间层                            │
│  IStateBackend / IKeyedStateBackend       │
│  Trigger / WindowAssigner / Evictor       │
│  InternalTimerService                     │
└──────────────────────────────────────────┘
```

## 3. 执行模型

### 3.1 当前实现：快速路径（Chain + Push）

`StreamExecutionEnvironment.execute()` 实现了一个**单线程、同步、单 JVM** 的执行模型：

```
execute()
  └── executePipeline(sinkTransform)
        ├── buildTransformationChain()    // 从 sink 回溯到 source，构建有序链
        ├── extractKeySelectors()         // 提取 PartitionTransformation 中的 KeySelector
        │                                 // PartitionTransformation 本身从链中移除
        ├── instantiateOperators()        // 按链顺序实例化算子
        ├── wireOperatorChain()           // 用 ChainingOutput 串联相邻算子
        │                                 // 若下游有 KeySelector，插入 KeyExtractingOutput
        └── runSource()                   // open() 所有算子（tail→head 逆序）
                                        // → source.run() 推数据通过链
                                        // → 发送 MAX_WATERMARK 触发最终窗口计算
```

**数据流方向**：Source → Operator1 → Operator2 → ... → Sink（推模型）

**关键设计决策**：
- ChainingOutput 直接调用下一个算子的 `processElement()`，无中间队列
- Watermark、WatermarkStatus、LatencyMarker 都通过同一 Output 接口传播
- 所有算子共享同一线程，无并发问题，但也无法利用多核
- Source 数据发送完毕后，自动发送 `Watermark.MAX_WATERMARK`（Long.MAX_VALUE）。这会使所有事件时间窗口的 Trigger 判定为超时，触发最终窗口计算。这是无持续 watermark 场景下确保窗口结果输出的关键机制

**keyBy 的实际行为**：`PartitionTransformation` 在 `extractKeySelectors()` 中被移除（不实例化为算子），但其 `KeySelector` 被提取。当 wireOperatorChain 检测到下游算子有对应的 KeySelector 时，插入 `KeyExtractingOutput` 包装器，在每条记录到达下游算子前调用 `keySelector.getKey(element)` 并设置到算子的 `keyedStateBackend`。因此 WindowOperator 的 `processElement()` 中 `keySelector.getKey()` 调用拿到正确的 key，状态也是按 key 隔离的。但数据本身未做 hash 分区——所有记录都经过同一条链。

### 3.2 未对接路径：StreamGraph → JobGraph → TaskExecutor

core 模块中存在一套更完整的执行架构，但当前未与 `StreamExecutionEnvironment.execute()` 对接：

```
Transformation DAG
    ↓ StreamGraphGenerator
StreamGraph (StreamNode + StreamEdge)
    ↓ JobGraphGenerator
JobGraph (JobVertex + JobEdge)
    ↓ TaskExecutor
Task (线程池并行执行)
```

**未对接的原因**：快速路径已经可以满足单机场景。对接 TaskExecutor 路径需要额外实现以下组件：`Invokable` 接口（将算子包装为可执行任务）、`RecordWriter/RecordReader`（算子间的数据交换）、`InputGate`（多输入端的屏障对齐）。当前这些组件只有接口定义，没有实现。优先级低于完善核心处理逻辑。

**规划**：未来当需要并行执行时，应打通这条路径。

### 3.3 CEP 独立执行路径（当前推荐）

CEP 模块（`nop-stream-cep`）可以直接使用，不依赖 `StreamExecutionEnvironment`。**这是当前推荐的 CEP 使用方式**，因为基于 DataStream 的 CepOperator 依赖 SimpleKeyedStateStore（无 key 隔离），而独立模式可以让使用者自行提供合适的状态存储。

```
Pattern<T, ?> pattern = Pattern.<Transaction>begin("start")
    .where(new SimpleCondition<>("amount > 1000", ...))
    .timesOrMore(2)
    .within(Time.seconds(30));

NFA<Transaction> nfa = NFACompiler.compile(pattern, ...);
SharedBuffer<Transaction> buffer = new SharedBuffer<>(stateStore, ...);

// 直接向 NFA 喂数据
Collection<Map<String, List<Transaction>>> matches =
    nfa.process(event, timestamp);
```

FraudDetectionDemo 展示了这种用法：直接构造 NFA + SharedBuffer，手动推进时间，完全绕过 DataStream API。

## 4. 数据流模型

### 4.1 Transformation DAG

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

每个 Transformation 持有 `getInputs()` 引用其上游。执行时从 Sink 回溯到 Source 构建链。

### 4.2 流类型层次

```
DataStream<T>
  └── KeyedStream<T, KEY>           (增加了 keyBy 语义)
        └── WindowedStream<T, K, W>  (增加了 window 语义)
```

- `DataStream`：基础流，支持 map/filter/flatMap/keyBy/sink
- `KeyedStream`：按键分区的流，支持 window 操作
- `WindowedStream`：窗口化流，支持 apply/aggregate/reduce

### 4.3 算子链（Operator Chain）

执行时，算子通过 Output 接口串联：

```
StreamSource → ChainingOutput → StreamMap → ChainingOutput → WindowOperator → ChainingOutput → StreamSink
```

当存在 KeySelector 时，插入 `KeyExtractingOutput`：

```
... → KeyExtractingOutput(keySelector) → WindowOperator
```

## 5. 与 Nop 平台的集成

### 5.1 当前集成方式

nop-stream 目前是独立的 Java 库，不依赖 NopIoC 或其他 Nop 模块：
- 无 beans.xml 配置
- 无 XDSL 模型定义
- 无 ORM 集成代码

### 5.2 规划的集成方式

| 规划模块 | 集成方式 |
|---------|---------|
| `nop-stream-flow` | 通过 NopFlow 声明式编排流处理任务，用 XDSL 定义 Source/Transform/Sink |
| `nop-stream-flink` | 将 core API 的 Transformation 映射到 Flink DataStream API |
| `nop-stream-api` | 从 core 中抽取公共接口，使 API 层与实现层解耦 |

## 6. 模块成熟度评估

| 模块 | 成熟度 | 可用于生产 |
|------|--------|-----------|
| nop-stream-core (DataStream API + 算子) | 原型 | 仅测试/演示 |
| nop-stream-cep (Pattern + NFA + SharedBuffer) | **较成熟** | ✅ 单线程内存模式 |
| nop-stream-runtime (WindowOperator) | 原型 | 需修复聚合语义 |
| nop-stream-fraud-example | 演示 | ✅ 作为使用范例 |
