# nop-stream 改进计划

**版本**：v1.0  
**日期**：2026-04-02  
**依据**：`ai-dev/nop-stream-design-review.md`

---

## 概览

改进工作分为三个阶段，按优先级排序：

| 阶段 | 名称 | 目标 | 预估工作量 |
|------|------|------|-----------|
| Phase 1 | 即时修复 | 消除编译错误和明显的正确性 bug | 1-2天 |
| Phase 2 | 架构补全 | 修复 keyBy、per-key 状态、watermark | 3-5天 |
| Phase 3 | 执行引擎重构 | 对接 TaskExecutor、填充空模块 | 2-4周 |

---

## Phase 1：即时修复（P1/P2 级别）

### 1.1 修复 `CompletedCheckpoint` 缺失导致测试无法编译

**问题**

`nop-stream-runtime` 的测试类引用了 `CompletedCheckpoint`，但该类不存在，导致整个模块测试无法编译运行：

```
// nop-stream-runtime/src/test/java/…/TestStateBackendIntegration.java
import io.nop.stream.runtime.checkpoint.CompletedCheckpoint; // 找不到
```

`nop-stream-checkpoint` 模块是空的，`CheckpointCoordinator` 存在于 `nop-stream-runtime`，但它依赖的 `CompletedCheckpoint`、`CheckpointMetaData` 等数据类缺失。

**改进方案**

在 `nop-stream-runtime` 中新建缺失的数据类（不需要完整语义，先满足编译和测试通过）：

```
nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/
  CompletedCheckpoint.java       # 新增
  CheckpointMetaData.java        # 新增（若缺失）
  CheckpointMetrics.java         # 新增（若缺失）
```

`CompletedCheckpoint` 最小实现：

```java
package io.nop.stream.runtime.checkpoint;

public class CompletedCheckpoint {
    private final long checkpointId;
    private final long timestamp;

    public CompletedCheckpoint(long checkpointId, long timestamp) {
        this.checkpointId = checkpointId;
        this.timestamp = timestamp;
    }

    public long getCheckpointId() { return checkpointId; }
    public long getTimestamp()    { return timestamp; }
}
```

**验证**：`./mvnw test -pl nop-stream/nop-stream-runtime` 编译并通过所有测试。

---

### 1.2 消除 `operator` vs `operators` 包命名冲突

**问题**

`nop-stream-core` 中存在两个几乎同名的包，各自有一个 `StreamOperator` 接口：

| 包 | 接口 | 方法签名 |
|----|------|---------|
| `io.nop.stream.core.operator` | `StreamOperator<OUT>` | `initialize()`, `open()`, `close()`, `getOutputType()`, `getName()` |
| `io.nop.stream.core.operators` | `StreamOperator<OUT>` | `open() throws Exception`, `finish() throws Exception`, `close() throws Exception`, `prepareSnapshotPreBarrier()`, … |

`operators.StreamOperator` 是功能更完整的接口（含检查点、watermark），`AbstractStreamOperator` 实现它。`operator.StreamOperator` 是一个简化接口，被 `DataStreamImpl` 的 `operator.SimpleStreamOperatorFactory` 使用。

`StreamExecutionEnvironment.runSource()` 的 `instanceof` 判断用的是 `operators.StreamOperator`，而工厂类在 `operator` 包，造成双包混用。

**改进方案**

保留 `operators` 包（功能完整），废弃 `operator` 包，步骤如下：

**Step 1**：将 `operator.StreamOperatorFactory<OUT>` 接口迁移到 `operators` 包：

```java
// operators/StreamOperatorFactory.java (新建/迁移)
package io.nop.stream.core.operators;

public interface StreamOperatorFactory<OUT> {
    StreamOperator<OUT> createStreamOperator(StreamTask<?, ?> containingTask);
}
```

**Step 2**：将 `operator.SimpleStreamOperatorFactory` 迁移到 `operators` 包，删除 `operator` 包中的同名类。

**Step 3**：更新 `DataStreamImpl`、`StreamExecutionEnvironment`、`OneInputTransformation` 的 import，统一使用 `operators.*`。

**Step 4**：删除 `io.nop.stream.core.operator` 包（3个文件）。

**验证**：`./mvnw test -pl nop-stream/nop-stream-core` 全通过。

---

### 1.3 修复 `map()`/`flatMap()` 输出类型为 `null`

**问题**

`DataStreamImpl.map()` 和 `flatMap()` 调用 `transform()` 时传入 `null` 作为 `TypeInformation`：

```java
// DataStreamImpl.java:121-126
public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
    return transform(
        "Map",
        null,   // ← TypeInformation 为 null
        new StreamMap<>(mapper)
    );
}
```

后续 `OneInputTransformation.getOutputType()` 返回 `null`，调用方若读取类型信息会 NPE。

**改进方案**

引入 `UnknownTypeInformation` 占位符，避免 null 在系统内传播，同时为日后实现真正的类型推断留下扩展点：

```java
// common/typeinfo/UnknownTypeInformation.java (新建)
package io.nop.stream.core.common.typeinfo;

public class UnknownTypeInformation<T> extends TypeInformation<T> {
    public static final UnknownTypeInformation<?> INSTANCE = new UnknownTypeInformation<>();

    @Override
    public String toString() { return "UnknownType"; }
}
```

```java
// DataStreamImpl.java - 修改 map() 和 flatMap()
@SuppressWarnings("unchecked")
public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
    return transform(
        "Map",
        (TypeInformation<R>) UnknownTypeInformation.INSTANCE,
        new StreamMap<>(mapper)
    );
}
```

同时修改 `filter()` 将 `getType()` 调用替换为在 `Transformation` 内部安全处理（filter 已经有类型，不受影响，但应保持一致）。

**验证**：运行 `nop-stream-core` 测试；运行 `FraudDetectionDemo`，不抛 NPE。

---

### 1.4 修复 `DataStreamImpl.sink()` 的 `throws Exception` 语义误导

**问题**

`DataStream` 接口中 `print()`、`sink()`、`collect()` 均声明 `throws Exception`，暗示调用后立即执行，但实际上只是注册了 `SinkTransformation`，真正执行需要用户额外调用 `env.execute()`。

```java
// DataStream.java:87
void print() throws Exception;  // 实际上不执行，只注册
```

**改进方案**

两个选项，选其一：

**选项 A（推荐）**：去掉 `throws Exception`，与 Flink API 保持一致（惰性注册不抛异常）：

```java
// DataStream.java
void print();   // 不再 throws Exception
void sink(SinkFunction<T> sinkFunction);
void collect(SinkFunction<T> collectorFunction);
```

**选项 B**：保持 `throws Exception` 但在 `sink()` 内部自动调用 `environment.execute()`（立即执行语义）。

> 推荐选项 A，因为允许构建多个 sink 再统一执行是更灵活的设计。

**验证**：更新 `DataStreamImpl` 和所有调用方，`FraudDetectionDemo` 编译通过。

---

## Phase 2：架构补全（P1 级别）

### 2.1 修复 `keyBy` 在单线程执行中无效

**问题**

`StreamExecutionEnvironment.buildTransformationChain()` 遇到 `PartitionTransformation` 直接 `continue` 跳过，然后继续处理其 input：

```java
// StreamExecutionEnvironment.java:212-215
if (current instanceof PartitionTransformation) {
    current = ((PartitionTransformation<?>) current).getInput();
    continue;   // ← keyBy 完全被忽略
}
```

在单线程执行中（parallelism=1），"分区"本身没有意义，但 `keyBy` 的语义是**按 key 路由到对应算子实例并隔离状态**。在 parallelism=1 的情况下，key 路由虽然不需要，但**状态隔离**（per-key state）仍然必须正确工作。

**改进方案**

在单线程执行模型中，`keyBy` 对应的语义是：下游算子在处理每个元素前，先调用 `setCurrentKey(key)`，使状态后端以 key 为 scope 读写。

修改 `wireOperatorChain()` 中对 `PartitionTransformation` 的处理：

**Step 1**：`PartitionTransformation` 不再被跳过，而是插入一个 `KeyExtractingOutput` 包装器：

```java
// operators/KeyExtractingOutput.java (新建)
package io.nop.stream.core.operators;

/**
 * 在单线程执行中，包装下游 Input，在转发每个元素前
 * 先提取 key 并设置到目标算子的 KeyContext 中。
 */
public class KeyExtractingOutput<IN> implements Input<IN> {
    private final Input<IN> delegate;
    private final KeySelector<IN, ?> keySelector;
    private final KeyContext keyContext;   // 下游算子实现此接口

    public KeyExtractingOutput(Input<IN> delegate,
                               KeySelector<IN, ?> keySelector,
                               KeyContext keyContext) {
        this.delegate = delegate;
        this.keySelector = keySelector;
        this.keyContext = keyContext;
    }

    @Override
    public void processElement(StreamRecord<IN> record) throws Exception {
        Object key = keySelector.getKey(record.getValue());
        keyContext.setCurrentKey(key);   // 设置 per-key 状态 scope
        delegate.processElement(record);
    }
}
```

**Step 2**：修改 `buildTransformationChain()` 以记录 `PartitionTransformation` 的分区器/keySelector，在 `wireOperatorChain()` 时插入 `KeyExtractingOutput`。

**Step 3**：确保下游算子（`CepOperator`、`WindowOperator` 等）的 `keyedStateBackend` 已正确初始化（见 2.2）。

**验证**：
```java
// 测试：两个 key 的状态互不干扰
env.fromElements(
    new Tuple2<>("a", 1), new Tuple2<>("b", 2), new Tuple2<>("a", 3))
  .keyBy(t -> t.f0)
  .map(sumByKey)
  .collect(results);
// key="a": 1+3=4, key="b": 2
assert results.get("a") == 4;
assert results.get("b") == 2;
```

---

### 2.2 修复 CEP 算子无 per-key 状态隔离

**问题**

`CepOperator.open()` 和 `CepWindowOperator.open()` 使用 `new SimpleKeyedStateStore()` 初始化 `SharedBuffer`：

```java
// CepOperator.java (open 方法中)
partialMatches = new SharedBuffer<>(
    new SimpleKeyedStateStore(),   // ← 全局共享，无 key 隔离
    null,
    new SharedBufferCacheConfig()
);
```

`SimpleKeyedStateStore` 内部是一个全局 `HashMap`，不区分 key，所有 key 的 NFA 状态、部分匹配共享同一存储，导致不同用户（key）的事件序列相互污染。

**根本原因**：`CepOperator` 继承 `AbstractStreamOperator`，已有 `keyedStateBackend` 字段，但 `open()` 没有用它。

**改进方案**

**Step 1**：在 `CepOperator.open()` 中，若 `stateBackend == null` 则初始化为 `MemoryStateBackend`；若已有 `keyedStateBackend` 则直接用，否则从 `stateBackend` 创建：

```java
// CepOperator.java - open() 修改
@Override
public void open() throws Exception {
    super.open();

    // 确保 keyedStateBackend 已初始化
    if (this.keyedStateBackend == null) {
        if (this.stateBackend == null) {
            this.stateBackend = new MemoryStateBackend();
        }
        // key 类型从 KeySelector 泛型参数获取，此处用 Object.class 做占位
        this.keyedStateBackend = this.stateBackend.createKeyedStateBackend(Object.class);
    }

    // 用 keyedStateBackend 初始化状态，替代 SimpleKeyedStateStore
    KeyedStateStore keyedStore = (KeyedStateStore) this.keyedStateBackend;
    computationStates = keyedStore.getState(
        new ValueStateDescriptor<>(NFA_STATE_NAME, NFAState.class));
    elementQueueState = keyedStore.getMapState(
        new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, …));
    partialMatches = new SharedBuffer<>(keyedStore, inputSerializer, new SharedBufferCacheConfig());

    // timer service、metrics 初始化不变
}
```

**Step 2**：`CepOperator.processElement()` 处理每个元素前，先更新 `keyedStateBackend` 的 currentKey：

```java
@Override
public void processElement(StreamRecord<IN> element) throws Exception {
    // 提取 key 并设置 scope（若上层 KeyExtractingOutput 已做此操作则无需重复）
    K key = keySelector.getKey(element.getValue());
    if (keyedStateBackend != null) {
        this.<K>getKeyedStateBackend().setCurrentKey(key);
    }
    // … 原有逻辑
}
```

**Step 3**：`CepWindowOperator` 同理，用 `keyedStateBackend` 替代 `SimpleKeyedStateStore`。

**验证**：
```java
// 两个用户的 CEP 模式互不干扰
// user-A: login -> withdraw（触发 alert）
// user-B: login only（不触发 alert）
// 验证只有 user-A 产生 alert
```

---

### 2.3 修复 CEP `currentWatermark()` 硬返回 `Long.MIN_VALUE`

**问题**

`CepOperator.open()` 中构造了一个匿名 `InternalTimerService`，其 `currentWatermark()` 永远返回 `Long.MIN_VALUE`：

```java
// CepOperator.java - open() 中的匿名 timerService
@Override
public long currentWatermark() {
    return Long.MIN_VALUE;  // ← event-time 超时永远不触发
}
```

这导致 `Pattern.within(Duration.ofSeconds(30))` 的超时清理永远不生效——部分匹配状态无限堆积。

**改进方案**

CEP 需要两种 watermark 语义：
- **处理时间模式**：用 `System.currentTimeMillis()` 驱动超时
- **事件时间模式**：从上游 watermark 更新

**Step 1**：在 `CepOperator` 中增加 `currentWatermark` 字段，由 `processWatermark()` 更新：

```java
// CepOperator.java
private transient long currentWatermark = Long.MIN_VALUE;

@Override
public void processWatermark(Watermark mark) throws Exception {
    // 更新 watermark，触发超时清理
    long newWatermark = mark.getTimestamp();
    if (newWatermark > this.currentWatermark) {
        this.currentWatermark = newWatermark;
        // 推进 NFA 时间，触发 within() 超时
        advanceTime(newWatermark);
    }
    super.processWatermark(mark);  // 继续向下游转发
}

private void advanceTime(long timestamp) throws Exception {
    try (SharedBufferAccessor<IN> sharedBufferAccessor = partialMatches.getAccessor()) {
        Tuple2<Collection<Map<String, List<IN>>>,
               Collection<Tuple2<Map<String, List<IN>>, Long>>> pendingMatchesAndTimeout =
            nfa.advanceTime(sharedBufferAccessor, nfaState, timestamp, afterMatchSkipStrategy);

        // 处理超时匹配
        handleTimedOutSequences(pendingMatchesAndTimeout.f1);
    }
}
```

**Step 2**：修改匿名 timerService 的 `currentWatermark()` 方法返回字段值：

```java
@Override
public long currentWatermark() {
    return CepOperator.this.currentWatermark;
}
```

**Step 3**：在单线程 `StreamExecutionEnvironment` 执行路径中，source 数据注入后需要在最后发送一个 `Watermark(Long.MAX_VALUE)` 表示流结束，触发最终超时清理：

```java
// StreamExecutionEnvironment.runSource() 修改
private void runSource(List<Object> operators) throws Exception {
    // … 原有 open + run 逻辑 …

    // 流结束后发送 EOF watermark
    Object head = operators.get(0);
    if (head instanceof AbstractStreamOperator) {
        ((AbstractStreamOperator<?>) head).processWatermark(Watermark.MAX_WATERMARK);
    }
}
```

**验证**：
```java
// 创建一个 within(5秒) 的 pattern
// 发送第一个事件，等待（模拟时间推进），watermark 超过 5 秒
// 验证部分匹配被清理，不产生 alert
```

---

### 2.4 修复 `WindowOperator` 聚合语义（last-write-wins 问题）

**问题**

`WindowOperator.addWindowElement()` 对于非 `SimpleAccumulator` 类型采用 "last-write-wins"：

```java
// WindowOperator.java:720-722
// Last-write-wins keeps behavior deterministic for non-accumulator ACC types.
setWindowContents(key, window, (ACC) value);
```

当 ACC 为 `List<IN>` 时（collect 所有元素），每个新元素覆盖旧的，导致窗口中只保留最后一个元素。

**改进方案**

引入 `WindowAccumulatorStrategy` 接口，将聚合策略从 `WindowOperator` 中解耦：

```java
// operators/windowing/WindowAccumulatorStrategy.java (新建)
public interface WindowAccumulatorStrategy<IN, ACC> {
    ACC createAccumulator();
    ACC add(ACC accumulator, IN value);
    ACC merge(ACC a, ACC b);
}
```

内置实现：

```java
// ListAccumulatorStrategy：collect 所有元素
public class ListAccumulatorStrategy<IN>
        implements WindowAccumulatorStrategy<IN, List<IN>> {
    public List<IN> createAccumulator() { return new ArrayList<>(); }
    public List<IN> add(List<IN> acc, IN value) { acc.add(value); return acc; }
    public List<IN> merge(List<IN> a, List<IN> b) { a.addAll(b); return a; }
}

// ReduceAccumulatorStrategy：使用 ReduceFunction 聚合
public class ReduceAccumulatorStrategy<IN>
        implements WindowAccumulatorStrategy<IN, IN> { … }
```

修改 `WindowOperator` 构造函数接受 `WindowAccumulatorStrategy`，替换 `instanceof SimpleAccumulator` 判断。

**注意**：这是一个较大的重构，建议与 Phase 3 的 `WindowedStream.apply/reduce/aggregate` API 统一设计后再实施。

**验证**：测试 sliding window + collect，确认窗口内所有元素都被收集。

---

## Phase 3：执行引擎重构（长期）

### 3.1 对接 `TaskExecutor` 与 `StreamExecutionEnvironment`

**问题**

当前存在两套完全平行、互不连通的执行路径：

```
路径 A（实际工作）：
StreamExecutionEnvironment.execute()
  → buildTransformationChain() / instantiateOperators() / wireOperatorChain() / runSource()
  直接操作 Java 对象链，单线程同步执行

路径 B（存在但从未被触发）：
StreamGraphGenerator → StreamGraph
JobGraphGenerator   → JobGraph
TaskExecutor        → Task → JobVertex.getInvokable().invoke()
```

路径 B 中 `Invokable` 接口只是一个桩（placeholder），没有任何实现；`JobVertex.getInvokable()` 在 `Task.run()` 中被调用但不知如何获得实际的算子逻辑。

**改进方案（分步骤）**

#### Step 1：定义 `StreamTask` 作为 `Invokable` 的实现

```java
// execution/StreamTask.java (新建)
public abstract class StreamTask<OUT, OP extends StreamOperator<OUT>>
        implements Invokable<OUT> {

    protected final OP mainOperator;
    protected final StreamConfig config;

    protected StreamTask(OP operator, StreamConfig config) {
        this.mainOperator = operator;
        this.config = config;
    }

    @Override
    public void invoke() throws Exception {
        mainOperator.open();
        try {
            run();          // 子类实现：处理输入直到流结束
            mainOperator.finish();
        } finally {
            mainOperator.close();
        }
    }

    protected abstract void run() throws Exception;
}
```

#### Step 2：`OneInputStreamTask` 从输入队列读取并处理

```java
// execution/OneInputStreamTask.java (新建)
public class OneInputStreamTask<IN, OUT> extends StreamTask<OUT, OneInputStreamOperator<IN, OUT>> {

    private BlockingQueue<StreamRecord<IN>> inputQueue;

    @Override
    protected void run() throws Exception {
        StreamRecord<IN> record;
        while ((record = inputQueue.poll(100, TimeUnit.MILLISECONDS)) != null
               || !isFinished()) {
            if (record != null) {
                mainOperator.processElement(record);
            }
        }
    }
}
```

#### Step 3：`JobGraphGenerator` 为每个 `JobVertex` 创建对应的 `StreamTask`

修改 `JobVertex` 存储算子工厂而非 `Invokable`，在 `TaskExecutor.submitJobVertex()` 时实例化 `StreamTask`。

#### Step 4：`StreamExecutionEnvironment.execute()` 两种模式

引入 `ExecutionMode` 枚举：

```java
public enum ExecutionMode {
    LOCAL_CHAIN,   // 当前单线程链式模式（parallelism=1，快速路径）
    TASK_EXECUTOR  // 基于 TaskExecutor 的多线程模式（parallelism>1）
}
```

`execute()` 根据 `parallelism` 自动选择：parallelism=1 走现有的 `LOCAL_CHAIN` 路径；parallelism>1 走 `TASK_EXECUTOR` 路径。

**阶段性里程碑**：
- M1：`StreamTask` 基类 + `OneInputStreamTask` 实现，单线程 Task 跑通 FraudDetectionDemo
- M2：`TaskExecutor` + `BlockingQueue` 连接相邻 task，parallelism=2 跑通 word count
- M3：完整 `JobGraphGenerator` + `TaskExecutor` 调度

---

### 3.2 填充 `nop-stream-checkpoint` 模块

**当前状态**：模块存在但无任何 Java 源文件。`CheckpointCoordinator`（在 `nop-stream-runtime`）、`LocalFileCheckpointStorage`（在 `nop-stream-core`）等均已实现，但缺少核心数据模型。

**需要实现的类**（按依赖顺序）：

```
nop-stream-checkpoint/src/main/java/io/nop/stream/checkpoint/
  model/
    CompletedCheckpoint.java      # checkpoint 元数据 + 状态句柄引用
    CheckpointMetaData.java       # checkpointId, timestamp
    CheckpointMetrics.java        # 字节数、耗时等指标
    StateHandleID.java            # 状态句柄 ID
  storage/
    CheckpointStorageLocation.java  # 抽象：一次 checkpoint 的存储位置
    CheckpointStreamFactory.java    # 抽象：创建 checkpoint 输出流
  coordinator/
    CheckpointTrigger.java        # 触发策略接口（周期、外部触发）
    CheckpointBarrier.java        # barrier 消息（已在 core 中，迁移过来）
```

**最小可用实现顺序**：
1. 数据模型类（无外部依赖，先完成）
2. `CheckpointStorageLocation` + 内存实现
3. 与 `CheckpointCoordinator` 对接
4. 与 `LocalFileCheckpointStorage` 对接

---

### 3.3 填充 `nop-stream-api` 模块

**目的**：将面向用户的公共 API 接口从 `nop-stream-core` 的实现中分离，使外部模块只依赖 `nop-stream-api`，不感知内部实现。

**需迁移到 `nop-stream-api` 的接口**：

```
nop-stream-api/src/main/java/io/nop/stream/api/
  datastream/
    DataStream.java        # 从 core 迁移
    KeyedStream.java       # 从 core 迁移
    DataStreamSource.java  # 从 core 迁移
  functions/
    MapFunction.java       # 从 core/common/functions 迁移
    FilterFunction.java
    FlatMapFunction.java
    KeySelector.java
    SinkFunction.java
  environment/
    StreamExecutionEnvironment.java  # 接口层，隔离实现
```

迁移后：
- `nop-stream-cep` 依赖 `nop-stream-api` 而非 `nop-stream-core`
- `nop-stream-flink` 依赖 `nop-stream-api`，用 Flink 实现 `DataStream` 接口
- 用户代码只 import `nop-stream-api`，可无缝切换底层实现（本地 vs Flink）

---

### 3.4 填充 `nop-stream-flink` 模块

**目的**：实现原 README 提到的目标——"提供 Flink 实现"，使同一套业务代码可在本地单机和 Flink 集群上运行。

**架构**：

```
nop-stream-flink 依赖 nop-stream-api + flink-streaming-java

FlinkDataStream<T> implements DataStream<T> {
    private final org.apache.flink.streaming.api.datastream.DataStream<T> flinkStream;

    @Override
    public <K> KeyedStream<T, K> keyBy(KeySelector<T, K> key) {
        return new FlinkKeyedStream<>(flinkStream.keyBy(key::getKey));
    }

    @Override
    public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
        return new FlinkSingleOutputStream<>(flinkStream.map(mapper::map));
    }
    // …
}

FlinkStreamExecutionEnvironment extends StreamExecutionEnvironment {
    private final org.apache.flink.streaming.api.environment.StreamExecutionEnvironment env
        = org.apache.flink.streaming.api.environment.StreamExecutionEnvironment.getExecutionEnvironment();
    // …
}
```

---

### 3.5 填充 `nop-stream-flow` 模块

**目的**：基于 Nop Platform 的 NopFlow（工作流/声明式处理）提供流处理的 DSL 入口。

**设计草案**：用 Nop XDSL（XML/YAML）描述流处理拓扑：

```yaml
# stream-job.stream.yaml
x:gen-extends: /nop/stream/xdsl/stream.xdef

source:
  type: collection
  data: [1, 2, 3, 4, 5]

pipeline:
  - op: filter
    condition: "value > 2"
  - op: map
    expr: "value * 10"

sink:
  type: print
```

`nop-stream-flow` 解析此 XDSL，构建 `StreamExecutionEnvironment` 并执行。

---

## 改进执行顺序建议

```
Week 1
  Day 1-2: Phase 1.1 (CompletedCheckpoint) + Phase 1.2 (包合并)
  Day 3:   Phase 1.3 (TypeInformation null) + Phase 1.4 (sink 语义)
  Day 4-5: Phase 2.1 (keyBy 修复) + 测试

Week 2
  Day 1-2: Phase 2.2 (per-key 状态隔离)
  Day 3-4: Phase 2.3 (watermark 修复)
  Day 5:   Phase 2.4 (Window 聚合语义)

Week 3-4: Phase 3.1 (StreamTask + TaskExecutor 对接)
Week 5-6: Phase 3.2 (checkpoint 模块)
Week 7+:  Phase 3.3/3.4/3.5 (api/flink/flow 模块)
```

---

## 测试策略

每个 Phase 完成后，验证以下测试套件：

| 测试目标 | 命令 | 通过标准 |
|---------|------|---------|
| core 基础 | `./mvnw test -pl nop-stream/nop-stream-core` | 全部通过 |
| CEP 引擎 | `./mvnw test -pl nop-stream/nop-stream-cep` | 全部通过 |
| runtime | `./mvnw test -pl nop-stream/nop-stream-runtime` | 全部通过（Phase 1.1 完成后） |
| 欺诈检测示例 | 运行 `FraudDetectionDemo.main()` | 4种模式均输出正确 alert |
| keyBy 集成 | 新增集成测试 | per-key 状态隔离验证通过 |
| watermark | 新增集成测试 | within() 超时正确触发 |

---

## 不在此计划范围内的内容

- 分布式部署（网络层、远程 RPC）
- 生产级序列化（Kryo/Avro 等）
- 反压机制（back-pressure）
- 动态扩缩容（rescaling）
- Web UI / 监控
- 与 Nop IoC 容器的集成（注入 `StreamExecutionEnvironment`）
