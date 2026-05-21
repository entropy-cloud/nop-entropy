# nop-stream 整体架构

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-21

## 1. 定位与设计目标

nop-stream 是 Nop 平台的流处理引擎，定位为**可分布式执行的 Flink 简化版**。

**核心目标**：
- 提供单流窗口操作能力（tumbling/sliding/count window）
- 支持 CEP 复杂事件处理（NFA + SharedBuffer）
- 提供 Checkpoint 容错和端到端 exactly-once 语义
- API 设计与 Flink 保持兼容，未来可对接 Flink 后端作为执行层
- 覆盖 SeaTunnel 等常用 ETL 软件的核心功能

**核心取舍**：
- **保留**：Flink DataStream API 概念、流处理语义、Barrier 快照、算子链化、多 Task 并行执行
- **去除**：复杂 Join（双流 Join / interval join / broadcast join）、广播流、异步算子、完整的 key-group 重分布
- **聚焦**：单流窗口聚合 + CEP 模式匹配 + Checkpoint 容错

**明确不做的**：
- 双流 Join（复杂度极高，用例有限，可通过 CEP 或外部 lookup 替代）
- SQL API（Nop 平台已有 GraphQL，流式 SQL 需求不迫切）
- 大规模并行（目标场景是中等规模 ETL，不是 PB 级流处理）

## 2. 模块划分

```
nop-stream/
├── nop-stream-api          [空]  公共 API 接口定义（规划中）
├── nop-stream-core         [实现] 核心抽象和 API
├── nop-stream-connector    [实现] 连接器适配层（Batch/Message/CDC）
├── nop-stream-runtime      [实现] 运行时实现（WindowOperator、Checkpoint、执行引擎）
├── nop-stream-cep          [实现] CEP 复杂事件处理引擎
├── nop-stream-checkpoint   [空]  检查点独立模块（规划中）
├── nop-stream-flink        [空]  Flink 后端适配（规划中）
├── nop-stream-flow         [空]  NopFlow 声明式编排（规划中）
└── nop-stream-fraud-example[实现] 欺诈检测示例
```

### 2.1 模块职责边界

| 模块 | 职责 | 依赖 |
|------|------|------|
| **nop-stream-core** | DataStream API 接口、算子基类、状态后端接口、Transformation DAG、StreamGraph/JobGraph 图模型、执行环境 | 无外部框架依赖 |
| **nop-stream-runtime** | WindowOperator 实现、CheckpointCoordinator、JdbcCheckpointStorage（基于 IJdbcTemplate）、TaskExecutor、RecordWriter/RecordReader/InputGate | 依赖 core + nop-dao-jdbc |
| **nop-stream-cep** | Pattern DSL、NFA 编译、SharedBuffer、CepOperator、声明式模型（pattern.xdef） | 依赖 core + nop-xlang |
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
│  ├── 快速路径: chain + push（单线程）     │
│  └── 图模型路径: StreamGraph → JobGraph   │
│               → Task → TaskExecutor       │
│               支持 checkpoint 集成        │
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
├──────────────────────────────────────────┤
│  存储层                                   │
│  ICheckpointStorage                       │
│  ├── LocalFileCheckpointStorage           │
│  └── JdbcCheckpointStorage (IJdbcTemplate)│
└──────────────────────────────────────────┘
```

## 3. 执行模型

### 3.1 快速路径（Chain + Push）

`StreamExecutionEnvironment.execute()` 实现了一个**单线程、同步**的执行模型：

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

**适用场景**：简单的单链流处理，不需要 checkpoint。

### 3.2 图模型路径（StreamGraph → JobGraph → TaskExecutor）

`StreamExecutionEnvironment.executeWithGraphModel()` 走图模型路径，支持 checkpoint 集成和多 Task 并行执行：

```
Transformation DAG
    ↓ StreamGraphGenerator
StreamGraph (StreamNode + StreamEdge)
    ↓ JobGraphGenerator（算子链融合优化）
JobGraph (JobVertex + JobEdge)
    ↓ GraphExecutionPlan（拓扑排序）
Task[] → TaskExecutor（线程池并行执行）
```

**已实现**：
- StreamGraph / JobGraph 两层转换
- 算子链融合优化
- TaskExecutor 基于线程池的并行执行
- RecordWriter/RecordReader/InputGate 跨 Task 数据交换
- 单链和多链管线均支持
- Checkpoint 集成（barrier 注入 → 传播 → 快照 → ACK → 持久化 → 恢复）

### 3.3 CEP 独立执行路径

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

快速路径中，算子通过 Output 接口串联：

```
StreamSource → ChainingOutput → StreamMap → ChainingOutput → WindowOperator → ChainingOutput → StreamSink
```

图模型路径中，算子链被融合为 JobVertex，跨链数据通过 RecordWriter/InputGate 传递。

## 5. 与 Nop 平台的集成

### 5.1 当前集成方式

nop-stream 与 Nop 平台的集成点：

| 集成点 | 方式 | 模块 |
|--------|------|------|
| 数据库访问 | `IJdbcTemplate` + `IDialect`（多数据库适配） | runtime (JdbcCheckpointStorage) |
| 批量数据源 | `IBatchLoader` / `IBatchConsumer` 桥接 | connector |
| 消息队列 | `IMessageService` 桥接 | connector |
| CDC 数据源 | `DebeziumCdcSourceFunction` 桥接 | connector |
| CEP 条件表达式 | `IEvalFunction` (nop-xlang) | cep |
| 序列化 | `JsonTool`（状态快照） | core/runtime |
| 错误处理 | `NopException` + `ErrorCode` | 所有模块 |

### 5.2 规划的集成方式

| 规划模块 | 集成方式 |
|---------|---------|
| `nop-stream-flow` | 通过 NopFlow 声明式编排流处理任务，用 XDSL 定义 Source/Transform/Sink |
| `nop-stream-flink` | 将 core API 的 Transformation 映射到 Flink DataStream API |
| `nop-stream-api` | 从 core 中抽取公共接口，使 API 层与实现层解耦 |

## 6. 模块成熟度评估

| 模块 | 成熟度 | 可用于生产 | 说明 |
|------|--------|-----------|------|
| nop-stream-core (DataStream API + 算子) | 中 | API 层可用，WindowedStreamImpl 的 apply/aggregate/reduce 待对接 | |
| nop-stream-cep (Pattern + NFA + SharedBuffer) | **高** | ✅ 单线程内存模式 | 最成熟的子模块 |
| nop-stream-runtime (WindowOperator + Checkpoint) | 中 | 需修复聚合语义后可用 | WindowOperator 有多处正确性问题 |
| nop-stream-connector | 高 | ✅ 5 个适配器已实现 | |
| nop-stream-fraud-example | 中 | ✅ 作为使用范例 | 需修复已知 bug |

详细的组件分解和开发路线见 `component-roadmap.md`。
