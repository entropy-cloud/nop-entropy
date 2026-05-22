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

**关键行为**：`WindowedStreamImpl` 的 `apply()`、`aggregate()`、`reduce()` 当前抛出 `UnsupportedOperationException`。这是**实现缺口**，不是设计选择。需要 core 模块定义一个 `WindowOperatorFactory` 接口，runtime 模块提供实现，通过注册机制连接——保持 `runtime → core` 的依赖方向。详见 `component-roadmap.md` 阶段 2。

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

> 所有算子相关类统一位于 `io.nop.stream.core.operators` 包（2026-05-20 清理后消除 operator/operators 分裂）。

```
StreamOperator<OUT>           算子基础接口（io.nop.stream.core.operators，生命周期 + checkpoint + key context）
  └── OneInputStreamOperator<IN, OUT>   单输入算子（processElement + watermark）
        ├── StreamSourceOperator<T>     数据源算子
        ├── StreamSinkOperator<T>       输出算子
        ├── StreamMap<IN, OUT>          映射算子
        ├── StreamFilter<IN>            过滤算子
        ├── StreamFlatMap<IN, OUT>      扁平映射算子
        └── WindowOperator<K,IN,ACC,OUT,W>  窗口算子（在 runtime 模块）

StreamOperatorFactory<OUT>    算子工厂接口（io.nop.stream.core.operators）
  └── SimpleStreamOperatorFactory<OUT>  简单工厂实现

ChainingStrategy              链化策略枚举（ALWAYS / NEVER / HEAD / HEAD_WITH_SOURCES）
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

状态管理的完整设计详见 `state-management-design.md`。

该文档覆盖：状态类型体系（ValueState / MapState / ListState / AppendingState）、状态后端接口层次（IStateBackend → IKeyedStateBackend → IInternalStateBackend）、MemoryKeyedStateBackend 的 HashMap 存储结构、序列化策略（运行时无序列化 + Checkpoint 用 JsonTool）、内存模型与消耗控制、与 Flink 状态管理的完整对比、以及各算子的状态使用方式。

**快速参考**：当前唯一实现是 `MemoryStateBackend` → `MemoryKeyedStateBackend`，底层为 `HashMap<TypedNamespaceAndKey, value>`。无 TTL、无 spill、无内存限制。

## 4. 时间与 Watermark 模型

事件时间语义和 Watermark 机制的完整设计详见 `time-model-design.md`。

该文档覆盖：WatermarkStrategy 统一抽象（时间戳分配 + watermark 生成）、TimestampAssigner 和 WatermarkGenerator 接口、两种内置生成策略（AscendingTimestampsWatermarks / BoundedOutOfOrdernessWatermarks）、WatermarkOutput 和 WatermarkOutputMultiplexer 的传播机制、TimestampsAndWatermarksOperator 的桥接角色。

## 5. 窗口机制

窗口机制的完整设计详见 `window-design.md`。

该文档覆盖：窗口模型四要素（WindowAssigner + Trigger + Evictor + WindowFunction）的交互流程、窗口类型和 Trigger 体系、WindowOperator 的非合并/合并窗口两条处理路径、聚合语义的三路分支（null / SimpleAccumulator / last-write-wins）、Timer Service 的工作机制、以及与 Flink 窗口的差异和已知限制。

## 6. 函数接口

### 6.1 用户函数接口

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

### 6.2 设计决策

所有函数接口都是 `Serializable`，因为 Flink 中需要跨 JVM 传输。nop-stream 保留了这个约束以保持兼容性，虽然当前单 JVM 执行不需要序列化函数。

## 7. 图模型

StreamGraph（逻辑拓扑）和 JobGraph（优化后的执行计划）的设计详见 `graph-model-design.md`。

该文档覆盖：两层图模型的职责边界、StreamGraphGenerator 的 Transformation 分发逻辑、JobGraphGenerator 的算子链判定条件和融合算法、ResultPartitionType 的数据交换模式、Task/TaskExecutor 的运行时执行模型。
