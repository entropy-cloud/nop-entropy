# 连接器设计

> Status: active
> Created: 2026-05-20
> Revised: 2026-05-23
> Parent: `architecture.md` §5（与 Nop 平台的集成）

## 1. 定位

nop-stream 通过适配 `nop-batch` 的 `IBatchLoader` / `IBatchConsumer` 两个接口对接数据库、文件等批数据源，同时为消息队列和 CDC 提供独立的流式连接器。

**核心桥接**：nop-batch 已将所有数据源统一为 Loader/Consumer 两个接口，nop-stream 只需两个薄适配器即可获得 CSV、JSONL、ORM、JDBC 等所有 nop-batch 已有的数据读写能力。

**补充连接器**：消息队列（`IMessageService`）和 CDC（Debezium）是异步推模型，nop-batch 的同步拉模型不适合封装，需独立适配。

## 2. nop-batch 核心接口

```
IBatchLoaderProvider<S>                          // 工厂
  └── setup(IBatchTaskContext) → IBatchLoader<S>

IBatchLoader<S>                                  // 数据读取
  └── load(int batchSize, IBatchChunkContext) → List<S>  // 空集合表示结束

IBatchConsumerProvider<R>                        // 工厂
  └── setup(IBatchTaskContext) → IBatchConsumer<R>

IBatchConsumer<R>                                // 数据写入
  └── consume(Collection<R> items, IBatchChunkContext)
```

`IBatchTaskContext` 和 `IBatchChunkContext` 是黑板模式属性容器，与运行时基础设施无耦合，直接 `new BatchTaskContextImpl()` 创建。

### nop-batch 已有实现

| 类型 | 实现类 | 模块 | 支持的数据源 |
|---|---|---|---|
| Loader | `ResourceRecordLoaderProvider` | nop-batch-core | CSV、JSONL、任意文件格式 |
| Loader | `OrmQueryBatchLoaderProvider` | nop-batch-orm | ORM 实体逐批查询 |
| Loader | `JdbcBatchLoaderProvider` | nop-batch-jdbc | SQL 查询 |
| Loader | `JdbcPageBatchLoaderProvider` | nop-batch-jdbc | SQL 分页查询 |
| Consumer | `ResourceRecordConsumerProvider` | nop-batch-core | CSV、JSONL、任意文件格式 |
| Consumer | `OrmBatchConsumerProvider` | nop-batch-orm | ORM 实体写入/更新 |
| Consumer | `JdbcBatchConsumerProvider` | nop-batch-jdbc | SQL 批量写入 |

## 3. 适配器设计

### 3.1 BatchLoaderSourceFunction

将 `IBatchLoader<S>` 适配为 `SourceFunction<S>`。`batchSize=1` 实现逐条推模型。

```java
class BatchLoaderSourceFunction<S> implements SourceFunction<S> {
    final IBatchLoaderProvider<S> loaderProvider;
    int batchSize = 1;

    public void run(SourceContext<S> ctx) {
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        IBatchLoader<S> loader = loaderProvider.setup(taskContext);
        IBatchChunkContext chunkContext = taskContext.newChunkContext();
        while (running) {
            List<S> batch = loader.load(batchSize, chunkContext);
            if (batch.isEmpty()) break;
            for (S item : batch) ctx.collect(item);
        }
    }
}
```

### 3.2 BatchConsumerSinkFunction

将 `IBatchConsumer<R>` 适配为 `SinkFunction<R>`。缓冲后批量提交，兼顾性能。

```java
class BatchConsumerSinkFunction<R> implements SinkFunction<R>, AutoCloseable {
    final IBatchConsumer<R> consumer;
    final List<R> buffer;
    final int batchSize;

    public BatchConsumerSinkFunction(IBatchConsumerProvider<R> provider, int batchSize) {
        this.consumer = provider.setup(new BatchTaskContextImpl());
        this.batchSize = batchSize;
        this.buffer = new ArrayList<>(batchSize);
    }

    public void consume(R value) {
        buffer.add(value);
        if (buffer.size() >= batchSize) flush();
    }

    public void close() { flush(); }

    private void flush() {
        if (!buffer.isEmpty()) { consumer.consume(buffer, null); buffer.clear(); }
    }
}
```

### 3.3 适配器总览

```
                    nop-stream
                 ┌──────────────┐
                 │ SourceFunction│ SinkFunction
                 │    (core)     │    (core)
                 └──────┬───────┘└──────┬──────┘
                        │               │
           ┌────────────┴───────────────┴────────────┐
           │         2 adapter classes                │
           │   BatchLoaderSourceFunction<S>           │
           │   BatchConsumerSinkFunction<R>           │
           └────────────────┬────────────────────────┘
                            │
               ┌────────────┴────────────┐
               │  IBatchLoaderProvider<S> │
               │  IBatchConsumerProvider<R>│
               └────────────┬─────────────┘
                            │
    ┌───────────┬───────────┼───────────┬────────────┐
    │  Resource │   ORM     │   JDBC     │  (未来)    │
    │  CSV/JSONL│  Entity   │  Statement │            │
    └───────────┴───────────┴───────────┴────────────┘
```

## 4. SourceWorkUnit 协议

分布式场景下，Source Split 升级为 `SourceWorkUnit`，支持动态拆分、进度追踪、watermark 状态恢复和 drain 截断。

### 4.1 SourceWorkUnit 结构

```java
class SourceWorkUnit {
    String sourceId;
    String splitId;
    Restriction restriction;
    Coder<Restriction> restrictionCoder;
    TaskLocation owner;
    long sizeEstimate;
    Object progress;
    Object watermarkEstimatorState;
}
```

### 4.2 RestrictionTracker

`RestrictionTracker` 封装 work-unit 的处理进度和游标管理：

```java
interface RestrictionTracker<R> {
    boolean tryClaim(R restriction, long position);
    R getRestriction();
    Object getProgress();
    Object snapshotWatermarkEstimatorState();
}
```

每个 connector 定义自己的 `Restriction` 类型（如 `FileSourceRestriction` 包含 filePath + startOffset + endOffset），并提供对应的 `RestrictionTracker` 实现。

### 4.3 DynamicSplit

**协议**：

```java
class DynamicSplitRequest {
    double fraction;  // 0.0 - 1.0
}

class DynamicSplitResponse<R> {
    R primary;    // 当前 task 继续
    R residual;   // 移交给新 task
    // 不变式：primary ∩ residual = ∅, primary ∪ residual = original
}
```

**触发时机**：

| 触发方 | 条件 | fraction |
|--------|------|----------|
| JobCoordinator | 负载均衡：某 source task 进度落后超阈值 | 0.5 |
| JobCoordinator | 扩缩容：用户请求增加并行度 | 当前行度 / 目标行度 |
| JobCoordinator | Drain：作业进入 DRAIN 模式 | 当前进度 / 总量估算 |

Connector 必须提供 `Restriction.split(double fraction)` 方法，拆分后的 primary 和 residual 必须 disjoint 且 complete。

### 4.4 DrainTruncate

Drain 时将无限 source 截断为有限 primary + residual：

```java
interface DrainableSource<R> {
    DynamicSplitResponse<R> truncateForDrain(R restriction);
}
```

**触发时机**：JobCoordinator 收到 `JobTerminationMode.DRAIN` 信号时调用。

**不支持 drain 的处理**：

| Source 类型 | DRAIN 行为 |
|------------|-----------|
| 实现 `DrainableSource` | 调用 `truncateForDrain()`，处理 primary 后结束 |
| `BoundedSource` | 等待自然结束 |
| Unbounded 且不实现 `DrainableSource` | **拒绝 DRAIN**，要求使用 CANCEL 模式 |

### 4.5 WatermarkEstimator

Source 的 watermark 估计器状态必须进入 checkpoint 以支持恢复后正确推进 watermark：

```java
interface WatermarkEstimator {
    void observe(Object record, long timestamp);
    long getCurrentWatermark();
    Object snapshotState();
    void restoreState(Object state) throws IncompatibleStateVersionException;
}
```

**版本兼容性**：状态通过 `majorVersion.minorVersion` 标识。主版本不一致时恢复失败并抛出 `IncompatibleStateVersionException`。Connector 升级后 state 格式不兼容必须增加 major version。

**集成到 Checkpoint**：`TaskEpochSnapshot` 增加 `Map<String, Object> watermarkEstimatorStates`（sourceId → state）。

### 4.6 Split Assignment Recovery 协议

分布式 source checkpoint 涉及三方状态，恢复时必须正确协调：

| 状态 | 持有者 | 内容 | Checkpoint 时机 |
|------|--------|------|----------------|
| **Enumerator State** | SourceEnumerator（JobCoordinator 侧） | 已发现 split、未分配 split、发现游标、内部簿记 | `snapshotState(epochId)` → 写入 Epoch Manifest |
| **Reader Split Cursor** | SourceReader（TaskManager 侧） | 当前持有 split 的读取位置 | reader 的 `snapshotState(epochId)` → 写入 TaskEpochSnapshot |
| **Assignment Tracker** | SourceEnumerator（JobCoordinator 侧） | `[epochId → [subtaskId → Set<Split>]]` 已下发但 reader 尚未 checkpoint 确认的 split | 与 Enumerator State 同步快照 |

**核心问题**：split 在 epoch N 之后、epoch N+1 之前下发给 reader，reader 在 epoch N+1 之前失败。此时：
- Enumerator 已将该 split 从"未分配"移到"已分配"
- Reader 恢复到 epoch N 的状态，**不持有**这个 split
- 如果不做特殊处理，该 split 会丢失

**恢复流程**：

```
1. Coordinator 从 Epoch Manifest 恢复 Enumerator State
2. Coordinator 从 TaskEpochSnapshot 恢复各 reader 的 split cursor
3. Coordinator 从 Assignment Tracker 中取出 epoch > N 的下发记录
   └── 这些 split 已下发但 reader 未在恢复点确认
4. 对每个"孤儿 split"：
   a. 如果 reader 恢复后报告了该 split（cursor 已包含）→ 正常，无需操作
   b. 如果 reader 未报告该 split → 将 split 归还给 Enumerator 的"未分配"集合
5. Reader 恢复后向 Coordinator 注册（报告自己持有的 split）
6. Coordinator 根据注册信息和归还的 split 重新分配
```

**Assignment Tracker 数据结构**：

```java
class SplitAssignmentTracker {
    // epochId → (subtaskId → 已下发但未确认的 split 集合)
    Map<Long, Map<Integer, Set<SourceSplit>>> pendingAssignments;

    void recordAssignment(long epochId, int subtaskId, SourceSplit split);
    void confirmAssignment(long epochId, int subtaskId, SourceSplit split);
    Set<SourceSplit> getUnconfirmedSplits(long upToEpochId, int subtaskId);
}
```

**与 checkpoint-design.md §5.3 的对应关系**：

checkpoint-design.md §5.3 定义的 enumerator state（discovered / unassigned / assigned / finished / pending acknowledgements / discovery cursor）中，`pending acknowledgements` 就是本节的 `SplitAssignmentTracker.pendingAssignments`。恢复时 `getUnconfirmedSplits()` 的返回值回填到 `unassigned` 集合。

**SplitOwnershipModel**：

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| `STICKY`（默认） | 恢复后 split 优先归还给原 owner subtask | Kafka partition 消费，本地缓存预热 |
| `REASSIGN` | 恢复后 split 全部归还 Enumerator 重新分配 | 文件 source，需要负载均衡 |
| `FIXED` | 恢复后 split 必须归还原 owner，owner 不在则等待 | 有序消费场景 |

Connector 通过 `SourceWorkUnit.ownershipModel` 声明。`STICKY` 和 `REASSIGN` 的区别仅在于 Coordinator 是否优先将归还 split 分配给原 owner。

## 5. 消息队列与 CDC 适配

### 5.1 MessageSourceFunction

```java
class MessageSourceFunction<T> implements SourceFunction<T> {
    final IMessageService messageService;
    final String topic;

    public void run(SourceContext<T> ctx) throws Exception {
        subscription = messageService.subscribe(topic, (t, msg, context) -> {
            ctx.collect((T) msg);
            return null;
        });
        while (running) Thread.sleep(1000);
    }
}
```

已有 `IMessageService` 实现：`LocalMessageService`（进程内）、`PulsarMessageService`（Apache Pulsar）。Kafka 通过实现 `IMessageService` 适配器接入。

### 5.2 MessageSinkFunction

```java
class MessageSinkFunction<T> implements SinkFunction<T> {
    final IMessageService messageService;
    final String topic;

    public void invoke(T value) {
        messageService.send(topic, value);
    }
}
```

Pulsar 支持事务，可实现 `TwoPhaseCommitSinkFunction` 提供 exactly-once 输出。

### 5.3 DebeziumCdcSourceFunction

```java
class DebeziumCdcSourceFunction implements SourceFunction<ChangeEvent> {
    final DebeziumConfig config;

    public void run(SourceContext<ChangeEvent> ctx) throws Exception {
        source = new DebeziumMessageSource(config);
        ICancellable subscription = source.subscribe(event -> ctx.collect(event));
        while (running) Thread.sleep(1000);
    }
}
```

CDC `ChangeEvent` 的 `timestamp` 可作为事件时间戳，`key` 可用于 keyBy，`after` 是实际数据。

## 6. 连接器汇总

| 适配器 | 依赖 | 代码量 | 覆盖的数据源/目标 | 分布式能力 |
|---|---|---|---|---|
| `BatchLoaderSourceFunction` | nop-batch-core | ~60 行 | CSV、JSONL、ORM、JDBC | — |
| `BatchConsumerSinkFunction` | nop-batch-core | ~60 行 | CSV、JSONL、ORM、JDBC | — |
| `MessageSourceFunction` | nop-message-core | ~40 行 | Pulsar、LocalMessage | CheckpointParticipant |
| `MessageSinkFunction` | nop-message-core | ~15 行 | Pulsar、LocalMessage | 2PC（Pulsar） |
| `DebeziumCdcSourceFunction` | nop-message-debezium | ~30 行 | MySQL、PostgreSQL CDC | DrainableSource |

## 7. 已知限制

1. **Kafka IMessageService 适配器未实现** — `nop-message-kafka` 模块为空
2. **消息 Source 的背压** — 当前无背压机制，依赖消息系统 ACK 隐式背压
3. **IBatchChunkContext 传 null** — `BatchConsumerSinkFunction` 的 consume 调用传 null，丢失 chunk 级统计
4. **ORM Source 全表扫描** — 增量读取需配置时间戳过滤或自增 ID 范围
5. **BatchLoaderSourceFunction 不支持 DynamicSplit** — 批数据源是有限的，不需要动态拆分
