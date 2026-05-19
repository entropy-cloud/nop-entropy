# nop-stream 核心引擎设计

> Status: active
> Created: 2026-05-19

## 1. DataStream API 设计

### 1.1 设计决策

**选了什么**：采用与 Flink DataStream API 一致的命名和概念模型。

**为什么**：
1. Flink 的 DataStream API 是业界事实标准，降低学习成本
2. 保持 API 兼容性，未来 nop-stream-flink 可将 nop-stream 的 Transformation 映射到 Flink Transformation
3. 概念模型经过大规模验证，设计成熟

**拒绝了什么**：
- 基于拉模型（Iterator）的 API —— 不适合流式场景
- 完全自创的 API —— 学习成本高，且无法复用 Flink 生态知识

### 1.2 流类型层次

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
  ├── apply(WindowFunction)     → 抛 UnsupportedOperationException
  ├── aggregate(AggregateFunction) → 抛 UnsupportedOperationException
  ├── reduce(ReduceFunction)     → 抛 UnsupportedOperationException
  └── transform(name, typeInfo, operator) → 唯一可用的入口
```

**关键行为差异**：`WindowedStreamImpl` 的 `apply()`、`aggregate()`、`reduce()` 全部抛出 `UnsupportedOperationException`，提示"需要 nop-stream-runtime 模块的 WindowOperator"。这是因为 core 模块只定义 API 接口，不包含 WindowOperator 实现。实际使用时需要：
1. 直接构造 `WindowOperator`（在 runtime 模块中）
2. 通过 `WindowedStreamImpl.transform()` 将其包装为 `OneInputStreamOperator` 注册到 DataStream

这种分层设计的原因：core 模块不依赖 runtime 模块，但 runtime 模块依赖 core。将 WindowOperator 的创建放在 runtime 模块保持了依赖方向的单向性。

### 1.3 Transformation DAG

每个 DataStream 操作在内部创建一个 `Transformation` 节点，注册到 `StreamExecutionEnvironment`：

| Transformation | 用途 | 输入数 |
|---|---|---|
| `SourceTransformation` | 数据源 | 0 |
| `OneInputTransformation` | map/filter/flatmap/window | 1 |
| `PartitionTransformation` | keyBy 分区 | 1 |
| `SinkTransformation` | 输出 | 1 |

Transformation 通过 `getInputs()` 构成 DAG。执行时从 Sink 回溯到 Source。

### 1.4 API 使用契约

- 用户必须显式调用 `env.execute()` 触发执行，`addSink()` / `print()` 只注册 Transformation
- `env.execute()` 只能调用一次（设 `executed = true` 防止重复）
- `map()` 和 `flatMap()` 的输出类型信息当前为 `UnknownTypeInformation`，不影响运行时，但类型检查不完整

## 2. 算子模型

### 2.1 核心接口层次

```
StreamOperator<OUT>           算子基础接口（生命周期 + checkpoint + key context）
  └── OneInputStreamOperator<IN, OUT>   单输入算子（processElement + watermark）
        ├── StreamSourceOperator<T>     数据源算子
        ├── StreamSinkOperator<T>       输出算子
        ├── StreamMap<IN, OUT>          映射算子
        ├── StreamFilter<IN>            过滤算子
        ├── StreamFlatMap<IN, OUT>      扁平映射算子
        └── WindowOperator<K,IN,ACC,OUT,W>  窗口算子（在 runtime 模块）
```

### 2.2 算子生命周期

```
open()          初始化（状态后端、timer service 等）
processElement() 处理每条数据
processWatermark() 处理 watermark（推进事件时间）
finish()        所有数据处理完毕
close()         释放资源
```

### 2.3 Output 机制

算子通过 `Output<StreamRecord<T>>` 接口向下游发送数据：

| Output 实现 | 用途 |
|---|---|
| `ChainingOutput<T>` | 直接调用下游算子的 `processElement()`（推模型） |
| `KeyExtractingOutput<T>` | 在 ChainingOutput 前插入 key 提取逻辑 |
| `TimestampedCollector<T>` | 带时间戳的输出收集器（WindowOperator 使用） |

**设计决策**：使用推模型而非拉模型。
- **为什么**：推模型天然适合流处理，数据到达即处理
- **拒绝了什么**：拉模型（Iterator）需要主动拉取，不适合事件驱动场景

### 2.4 算子链化（Chaining）

当前实现始终将所有算子链化为一条链：

```
Source → [ChainingOutput] → Map → [ChainingOutput] → Window → [ChainingOutput] → Sink
```

**简化点**：Flink 有 ChainStrategy（HEAD/ALWAYS/NEVER）和 slot sharing group 等复杂概念，nop-stream 完全去除。

## 3. 状态管理

### 3.1 状态后端接口

```
IStateBackend                          状态后端工厂
  └── IKeyedStateBackend<K>            按 key 分区的状态存储
        ├── setCurrentKey(K)           设置当前 key
        ├── setCurrentNamespace(String) 设置命名空间（用于 Window 隔离）
        ├── getState(ValueStateDescriptor)
        ├── getListState(ListStateDescriptor)
        └── getMapState(MapStateDescriptor)
```

### 3.2 实现类

| 实现类 | 位置 | 特点 |
|---|---|---|
| `MemoryStateBackend` | core | 内存实现，HashMap 存储 |
| `MemoryKeyedStateBackend<K>` | core | 按 (key, namespace) 二元组分区的内存状态 |
| `SimpleKeyedStateStore` | core | **非键控**的全局状态存储，无 key 隔离 |

### 3.3 设计决策

**选了什么**：简化状态后端接口，去除 key-group 分区和分布式快照。

**简化了什么**（对比 Flink）：
- Flink 的 `KeyedStateBackend` 有 key-group 概念用于分布式重分布 → nop-stream 去除
- Flink 有 `OperatorStateBackend` 用于非键控算子 → nop-stream 未实现
- Flink 有 `RocksDBStateBackend` 等多种实现 → nop-stream 当前仅内存

**已知限制**：
- `SimpleKeyedStateStore` 不感知 key，所有 key 共享同一状态 → CepOperator 和 CepWindowOperator 受影响
- `WindowOperator` 使用 `MapState<String, ACC>` 而非标准的 `AppendingState` → 聚合语义有偏差（last-write-wins）

### 3.4 状态使用方式

```java
// 状态后端创建
IStateBackend stateBackend = new MemoryStateBackend();
IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

// 在算子中使用
keyedBackend.setCurrentKey("user123");
keyedBackend.setCurrentNamespace("window-1h");
ValueState<Long> countState = keyedBackend.getState(
    new ValueStateDescriptor<>("count", Long.class));
```

## 4. 窗口机制

窗口机制的完整设计详见 `window-design.md`。

该文档覆盖：窗口模型四要素（WindowAssigner + Trigger + Evictor + WindowFunction）的交互流程、窗口类型和 Trigger 体系、WindowOperator 的非合并/合并窗口两条处理路径、聚合语义的三路分支（null / SimpleAccumulator / last-write-wins）、Timer Service 的工作机制、以及与 Flink 窗口的差异和已知限制。

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

### 5.2 设计决策

所有函数接口都是 `Serializable`，因为 Flink 中需要跨 JVM 传输。nop-stream 保留了这个约束以保持兼容性，虽然当前单 JVM 执行不需要序列化函数。

## 6. 图模型

StreamGraph（逻辑拓扑）和 JobGraph（优化后的执行计划）的设计详见 `graph-model-design.md`。

该文档覆盖：两层图模型的职责边界、StreamGraphGenerator 的 Transformation 分发逻辑、JobGraphGenerator 的算子链判定条件和融合算法、ResultPartitionType 的数据交换模式、Task/TaskExecutor 的运行时执行模型，以及与当前快速路径的关系和对接所需的工作。
