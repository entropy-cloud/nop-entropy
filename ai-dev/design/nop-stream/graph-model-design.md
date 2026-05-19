# 图模型与执行引擎设计

> Status: active（**核心组件已实现，未与执行路径对接**）
> Created: 2026-05-19
> Parent: `architecture.md` §3（执行模型）

## 1. 定位

nop-stream 的图模型是 DataStream API 与执行引擎之间的中间表示。它将用户通过 API 构建的 Transformation DAG 经过两层转换——StreamGraph 和 JobGraph——最终变为可执行的 Task 集合，由 TaskExecutor 调度执行。

这套设计的目标是：**将"用户描述的计算逻辑"与"如何执行这些逻辑"解耦**。当前 `StreamExecutionEnvironment.execute()` 走的是快速路径（直接折叠 Transformation 为算子链），绕过了图模型。未来对接后，图模型将支持算子链优化、并行度调整、分区策略和数据交换模式。

### 1.1 设计决策

**选了什么**：Flink 风格的两层图模型（StreamGraph → JobGraph），中间通过算子链优化（operator chaining）。

**为什么不是一层**：
- 一层 DAG 直接执行会丧失优化机会（无法做算子链融合、分区策略调整）
- 两层模型将"逻辑拓扑"和"物理执行计划"分离，各自可以独立演进

**为什么不是三层**（Flink 有 ExecutionGraph）：
- nop-stream 不做分布式调度（无 JobManager/TaskManager），不需要 ExecutionGraph 这层物理调度抽象
- JobGraph 直接生成 Task 即可

## 2. 三层转换管线

```
用户 API                    逻辑图                    优化后的执行图               运行时
───────                    ──────                    ──────────────              ──────

DataStream API         StreamGraph               JobGraph                    Task[]
  .addSource()    ──►   StreamNode         ──►   JobVertex (chain融合)  ──►   Task
  .map()                StreamEdge               JobEdge                     TaskExecutor
  .keyBy()                                       ResultPartitionType
  .addSink()
     │                 StreamGraphGenerator      JobGraphGenerator
     └── Transformation ──────────────► ─────────────────────►
         DAG               (1:N 映射)               (M:N 融合)
```

### 2.1 每层的职责边界

| 层 | 输入 | 输出 | 关注点 |
|---|---|---|---|
| Transformation DAG | 用户 API 调用链 | 逻辑算子 + 数据依赖 | 用户意图的表达 |
| StreamGraph | Transformation DAG | StreamNode + StreamEdge | 完整的逻辑拓扑（1:1 对应 Transformation，但增加了分区策略信息） |
| JobGraph | StreamGraph | JobVertex + JobEdge | 优化后的执行计划（算子链融合、分区类型确定） |
| Task[] | JobGraph | 可执行任务 | 线程池调度的运行时单元 |

## 3. StreamGraph：逻辑拓扑

> **实现状态**：已完成。StreamGraphGenerator 可正确处理 SourceTransformation、OneInputTransformation、SinkTransformation、PartitionTransformation 四种类型。

### 3.1 数据结构

| 组件 | 关键属性 | 职责 |
|---|---|---|
| `StreamNode` | id (int), name, operatorFactory, outputType, parallelism, keySelector?, windowAssigner?, trigger? | 一个逻辑算子。包含算子工厂、类型信息、并行度和可选的窗口配置 |
| `StreamEdge` | sourceId (int), targetId (int), partitioner?, outputTag? | 节点间的连接。包含分区策略（null = forward）和可选的侧输出标签 |
| `StreamGraph` | streamNodes (Map), streamEdges (Map), sourceIDs (List), sinkIDs (List) | 完整的 DAG 容器。追踪所有节点、边、Source 和 Sink 入口 |

### 3.2 生成过程

`StreamGraphGenerator` 从 Sink Transformation 列表出发，递归回溯处理所有上游 Transformation：

| Transformation 类型 | 处理方式 |
|---|---|
| `SourceTransformation` | 创建 StreamNode + 注册为 sourceID。无输入边 |
| `OneInputTransformation` | 递归处理 input → 创建 StreamNode（含 keySelector、operatorFactory）→ 创建 StreamEdge（forward，null partitioner） |
| `SinkTransformation` | 递归处理 input → 创建 StreamNode（用 SinkOperatorFactory 包装 SinkFunction）→ 创建 StreamEdge → 注册为 sinkID |
| `PartitionTransformation` | 递归处理 input → 创建 StreamNode（用 PartitionOperatorFactory 占位）→ 创建 StreamEdge（**在入边上设置 partitioner，出边为 forward/null partitioner**） |

**关键点**：PartitionTransformation 在 StreamGraph 中被保留为一个独立节点（不像快速路径中直接被移除）。这个节点在 JobGraph 阶段会被优化掉（不产生独立的 JobVertex），但它的分区策略信息被保留在 JobEdge 的 `ResultPartitionType` 中。注意 partitioner 被设置在 Partition 节点的**入边**（从上游节点到 Partition 节点），而 Partition 节点的出边始终是 forward（null partitioner）。

### 3.3 设计约束

- 每个 Transformation 只处理一次（`processedTransformations` 集合去重）
- StreamEdge 的 partitioner 为 null 时表示 forward（同并行度的直接传递）
- StreamNode ID 就是 Transformation ID，全局唯一

## 4. JobGraph：优化后的执行计划

> **实现状态**：已完成。JobGraphGenerator 实现了算子链识别、JobVertex 创建和 JobEdge 连接。Invokable 是 placeholder。

### 4.1 核心优化：算子链融合

JobGraph 与 StreamGraph 的核心区别在于**算子链融合**（operator chaining）：多个满足条件的 StreamNode 被合并为一个 JobVertex，在同一个线程中顺序执行，消除序列化和线程切换开销。

#### 链接判定条件

两个相邻的 StreamNode 可以被链化，当且仅当**同时满足**以下条件：

| # | 条件 | 原因 |
|---|---|---|
| 1 | 并行度相同 | 不同并行度意味着需要数据重分布 |
| 2 | 边的 partitioner 为 null（forward） | 非 forward 分区（如 hash、round-robin）意味着数据需要跨实例传递 |
| 3 | 上游不是 Sink | Sink 是链的终止边界 |
| 4 | 下游不是 Source | Source 是链的起始边界 |
| 5 | 上游只有一条出边（无分支） | 分支意味着数据需要复制到多条路径 |
| 6 | 下游只有一条入边（无合并） | 合并意味着数据来自多个上游，需要独立的交换机制 |

#### 链识别算法

```
identifyChains(streamGraph)
    ├─ 从所有 sourceID 出发，DFS 构建链
    ├─ 处理未被 source 可达的孤立节点
    └─ 每条链 = 满足 canChain 条件的连续 StreamNode 序列

buildChain(currentNode, chain, processedNodes)
    ├─ 加入当前节点到 chain
    ├─ 如果只有一条出边
    │   └─ 如果 canChain(current, next)
    │       └─ 递归 buildChain(next)
    └─ 否则，链结束（分支节点是链边界）
```

**链边界**：Source、Sink、分支节点（多条出边）、合并节点（多条入边）、分区变换节点（非 forward partitioner）——这些节点各自成为独立 JobVertex 的起点。

### 4.2 数据结构

| 组件 | 关键属性 | 职责 |
|---|---|---|
| `JobVertex` | id (String), name, parallelism, operatorChains (List), invokable | 一个可调度的执行单元。包含融合后的算子链 |
| `JobEdge` | sourceId (String), targetId (String), resultPartitionType | 两个 JobVertex 间的数据交换关系 |
| `OperatorChain` | operators (List\<StreamOperator\>) | 一组在同一线程中顺序执行的算子。有 open/processElement/close 生命周期 |
| `Invokable` | invoke() | 可执行任务的抽象接口。当前是 placeholder，只做 open→invoke→close |
| `ResultPartitionType` | PIPELINED / PIPELINED_BOUNDED / BLOCKING | 中间结果的传输模式 |
| `JobGraph` | jobName, vertices (Map), edges (List) | 优化后的执行计划 |

### 4.3 ResultPartitionType：数据交换模式

| 类型 | 语义 | 适用场景 |
|---|---|---|
| `PIPELINED` | 流式传输，生产和消费同时进行，无背压 | forward 分区（同实例内直接传递） |
| `PIPELINED_BOUNDED` | 流式传输但有界缓冲，满时阻塞生产者（背压） | 非 forward 分区（hash、round-robin 等需要跨实例传递） |
| `BLOCKING` | 批式传输，生产者全部完成后消费者才开始 | 批处理场景（当前 nop-stream 未使用） |

**分区类型选择**：`determinePartitionType()` 的规则是——partitioner 为 null → PIPELINED，否则 → PIPELINED_BOUNDED。当前未使用 BLOCKING。

### 4.4 ID 映射

- StreamNode 用 Integer ID（继承自 Transformation ID）
- JobVertex 用 String ID（"vertex-" + 链首节点的 Integer ID）
- `nodeToVertexMap` 维护 StreamNode ID → JobVertex ID 的映射，用于创建 JobEdge 时查找

### 4.5 JobEdge 创建逻辑

遍历 StreamGraph 的所有 StreamEdge，如果 source 和 target 属于**不同的 JobVertex**（即不在同一条链中），则创建一条 JobEdge。同一条链内的 StreamEdge 不产生 JobEdge（算子间通过 ChainingOutput 直接调用）。

## 5. Task 与 TaskExecutor：运行时执行

> **实现状态**：Task 和 TaskExecutor 已实现。但 Invokable 的 invoke() 是 placeholder（只做 open/close，不实际读取输入或处理数据）。对接时需要实现 RecordWriter/RecordReader/InputGate 等数据交换组件。

### 5.1 Task

Task 是 JobVertex 的一个并行实例，实现了 `Runnable`，可在线程池中执行。

| 状态 | 含义 |
|---|---|
| CREATED | 已创建，未运行 |
| RUNNING | 正在执行 |
| COMPLETED | 成功完成 |
| FAILED | 执行失败 |
| CANCELED | 被取消 |

Task 的执行流程：`open(所有 OperatorChain) → invokable.invoke() → close(所有 OperatorChain, 在 finally 中保证执行)`。

每个 Task 知道自己的 `taskIndex`（0 到 parallelism-1），用于确定自己处理的数据分区。

### 5.2 TaskExecutor

TaskExecutor 管理一个固定大小的线程池（默认 CPU 核数），按 JobVertex 的并行度创建对应数量的 Task 实例并提交执行。

| 方法 | 职责 |
|---|---|
| `submitJobVertex(jobVertex)` | 按 parallelism 创建 N 个 Task 并提交 |
| `submitTask(task)` | 提交单个 Task |
| `awaitCompletion(timeout)` | 等待所有 Task 完成 |
| `shutdown()` | 优雅关闭线程池 |

**设计约束**：
- TaskExecutor 不做 DAG 拓扑调度——它不判断 vertex 之间的依赖顺序。当前假设所有 vertex 可以同时启动（适合流式场景：所有算子持续运行）。
- 线程安全：使用 ConcurrentHashMap 跟踪 Task 和 Future。

## 6. 完整管线：从 API 到执行

以下是一个完整的例子，展示数据如何从用户 API 经过三层转换到达 TaskExecutor：

```
用户代码:
  env.addSource(src)     // SourceTransformation (id=1, parallelism=1)
     .map(fn)            // OneInputTransformation (id=2, parallelism=1)
     .keyBy(ks)          // PartitionTransformation (id=3, parallelism=1)
     .window(wa)         // OneInputTransformation (id=4, parallelism=1)
     .aggregate(af)      // OneInputTransformation (id=5, parallelism=1)
     .addSink(sink)      // SinkTransformation (id=6, parallelism=1)

StreamGraph (6 nodes, 5 edges):
  [1:Source] --forward--> [2:Map] --hash(partitioner)--> [3:Partition]
  [3:Partition] --forward--> [4:Window] --forward--> [5:Aggregate] --forward--> [6:Sink]

  注意: 只有 2→3 边有 hash partitioner（由 keyBy 产生），其余边都是 forward

JobGraph (经算子链融合):
  链识别: [1,2] 可链化 (forward, 同并行度, 无分支)
          [3] 独立 (partitioner != null → 链边界)
          [4,5,6] 可链化 (forward, 同并行度, 无分支)

  Vertex-1: "Source -> Map" (chain: [SourceOperator, MapOperator])
       |
       | JobEdge (PIPELINED_BOUNDED, 因为 partitioner != null)
       |
  Vertex-4: "Window -> Aggregate -> Sink" (chain: [WindowOperator, AggregateOperator, SinkOperator])

TaskExecutor:
  Task "vertex-1#0" (Source -> Map)              → 线程 1
  Task "vertex-4#0" (Window -> Aggregate -> Sink) → 线程 2

  两个 Task 并行运行，通过 RecordWriter/RecordReader 交换数据
```

## 7. 与当前快速路径的关系

### 7.1 快速路径（当前 execute()）

```
Transformation DAG → 直接构建算子链 → Source.run() 同步执行
```

快速路径跳过了 StreamGraph/JobGraph/TaskExecutor，直接从 Transformation 列表中提取算子工厂，用 `ChainingOutput` 串联，在同一线程中同步执行。

### 7.2 图模型路径（设计目标）

```
Transformation DAG → StreamGraph → JobGraph → Task[] → TaskExecutor
```

图模型路径增加了算子链优化、分区策略和并行执行的能力。

### 7.3 两条路径的适用场景

| 维度 | 快速路径 | 图模型路径 |
|---|---|---|
| 执行方式 | 单线程同步 | 多线程并行 |
| 算子链 | 简单线性链（无分支合并处理） | 通用 DAG（支持分支、合并、多 Source） |
| 分区 | keyBy 被跳过，实际不做 hash 分区 | keyBy 产生独立 vertex，真正按 key 分区 |
| Checkpoint | 未集成 | 可与 CheckpointCoordinator 集成（每个 Task 独立做快照） |
| 适用场景 | 简单的单流处理 | 需要并行、多条流合并/分支的场景 |

## 8. 与 Flink 的差异

| 维度 | Flink | nop-stream |
|---|---|---|
| 图层数 | 3 层（StreamGraph → JobGraph → ExecutionGraph） | 2 层（StreamGraph → JobGraph） |
| 调度模型 | JobManager（全局调度）+ TaskManager（多机多进程） | TaskExecutor（单机线程池） |
| 数据交换 | Netty RPC + MemorySegments + NetworkBufferPool | 无实现（需要 RecordWriter/RecordReader） |
| Slot 管理 | SlotSharingGroup、CoLocationGroup | 无 |
| Failover | Region-based failover、全局/局部恢复 | 无（未来可基于 checkpoint 做简单恢复） |
| 算子链条件 | 可通过 `disableChaining()` / `startNewChain()` 精细控制 | 仅按 6 条规则自动判定，无用户控制 API |

### 8.1 简化决策

- **无 ExecutionGraph**：单机不需要将 JobVertex 拆分为 ExecutionVertex + ExecutionAttempt 的三层调度
- **无 Slot**：没有多租户资源隔离的需求
- **无 Netty**：单机线程池间交换数据可以用 BlockingQueue，不需要网络通信
- **无精细链控制**：当前阶段自动判定足够，未来可按需添加

## 9. 对接所需的工作

将图模型路径接入 `StreamExecutionEnvironment.execute()`，需要以下步骤：

1. **execute() 中走图模型路径**
   - 收集所有 SinkTransformation
   - `StreamGraphGenerator.generate(sinkTransformations)` → StreamGraph
   - `JobGraphGenerator.generate(streamGraph)` → JobGraph
   - 将 JobGraph 提交给 TaskExecutor

2. **实现数据交换组件**
   - `RecordWriter<T>`：将记录写入对应分区的输出缓冲区
   - `RecordReader<T>`：从输入缓冲区读取记录
   - `InputGate`：管理多输入端的屏障对齐（与 BarrierAligner 集成）
   - 内部可基于 `BlockingQueue<StreamRecord>` 实现单机数据交换

3. **实现 Invokable 的实际执行逻辑**
   - Source Task：调用 SourceFunction.run()，输出通过 RecordWriter
   - Middle Task：从 InputGate 读取 → 通过 OperatorChain 处理 → 输出到 RecordWriter
   - Sink Task：从 InputGate 读取 → 通过 OperatorChain 处理（最后一个算子是 SinkFunction）

4. **与 Checkpoint 集成**
   - CheckpointCoordinator 按 JobVertex 的 Task 注册为需要 ACK 的 task
   - Barrier 通过 RecordWriter/RecordReader 传播
   - InputGate 在收到 barrier 时触发 BarrierAligner 对齐

5. **拓扑排序调度**
   - 当前 TaskExecutor 不考虑 vertex 间的依赖顺序
   - 对于流式作业，所有 vertex 可以同时启动（Source 先产出数据，其他 vertex 等待输入）
   - 但需要确保所有 InputGate 在数据到达前已就绪

## 10. 已知限制

1. **Invokable 是 placeholder** — 只做 open/close，不读取输入也不处理数据
2. **无数据交换实现** — RecordWriter/RecordReader/InputGate 均未实现
3. **无拓扑调度** — TaskExecutor 不判断 vertex 依赖顺序，假设所有 vertex 可同时启动
4. **PartitionTransformation 在 StreamGraph 中保留为独立节点** — 它在 JobGraph 阶段不产生独立 vertex，但其 partitioner 信息被传递到 JobEdge。如果 PartitionTransformation 的前后节点被链化到不同 vertex，分区逻辑才能生效；如果它们被链化到同一 vertex，partitioner 信息被忽略（与快速路径行为一致）
5. **并行度固定为 1** — 当前所有 Transformation 的 parallelism 默认为 1。图模型路径支持 parallelism > 1 的拓扑结构，但数据交换组件需要按 parallelism 创建对应的 RecordWriter 分区
6. **Invokable 与 Task 重复管理 OperatorChain 生命周期** — `Task.run()` 在 finally 块中调用 `closeOperatorChains()`，而 placeholder `Invokable.invoke()` 内部也调用 `operatorChain.open()` 和 `operatorChain.close()`。实际的 Invokable 实现不应自行管理链生命周期，应交给 Task 统一处理
