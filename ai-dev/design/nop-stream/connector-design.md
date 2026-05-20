# 连接器设计：基于 nop-batch 统一抽象

> Status: active（**核心适配器已实现**）
> Created: 2026-05-20
> Revised: 2026-05-20
> Parent: `architecture.md` §5（与 Nop 平台的集成）

## 1. 定位

nop-stream 当前只有 `CollectionSourceFunction`（内存集合 Source）和 `PrintSinkFunction`（控制台 Sink）。要做实际 ETL，需要对接数据库、消息队列、文件等外部系统。

**关键洞察**：Nop 平台的 `nop-batch` 模块已经将所有数据源统一为 `IBatchLoader` / `IBatchConsumer` 两个接口，并提供了文件、ORM、JDBC 等多种实现。nop-stream 不需要为每种数据源分别编写连接器，**只需适配 IBatchLoader → Source、IBatchConsumer → Sink 两个接口**，即可自动获得所有 nop-batch 已有的数据读写能力。

### 1.1 设计决策

**选了什么**：以 `IBatchLoader` / `IBatchConsumer` 为核心桥接点，nop-stream 编写两个薄适配器。

**为什么不用更底层的抽象**：
- `IRecordInput/Output`（nop-dataset）、`IEntityDao`（nop-orm）、`IMessageService`（nop-message）都被 nop-batch 的 Loader/Consumer 实现封装了
- 直接用底层抽象需要为每种数据源写适配器（CSV 适配器、ORM 适配器、JDBC 适配器……），而 nop-batch 已经做了这些
- 两个适配器 vs 七八个适配器，维护成本差距显著

**为什么不是直接复用 nop-batch 的执行引擎**：
- nop-batch 是分块拉模型（chunk-based pull），nop-stream 是逐条推模型（record-by-record push）
- nop-stream 有窗口、状态、时间语义，这些 nop-batch 不需要
- 两者的互补关系详见 §6

## 2. nop-batch 核心：Loader / Consumer

### 2.1 核心接口

```
IBatchLoaderProvider<S>                          // 工厂接口
  └── setup(IBatchTaskContext) → IBatchLoader<S> // 创建 loader 实例

IBatchLoader<S>                                  // 数据读取
  └── load(int batchSize, IBatchChunkContext) → List<S>  // 加载一批数据，空集合表示结束

IBatchConsumerProvider<R>                        // 工厂接口
  └── setup(IBatchTaskContext) → IBatchConsumer<R>       // 创建 consumer 实例

IBatchConsumer<R>                                // 数据写入
  └── consume(Collection<R> items, IBatchChunkContext)   // 批量消费数据
```

**nop-stream 只需要关心这两个接口**。`IBatchTaskContext` 和 `IBatchChunkContext` 是黑板模式（blackboard）的上下文对象——类似 Map 语义的属性容器，与运行时基础设施无耦合。直接 `new BatchTaskContextImpl()` 创建，按需设置属性即可。

### 2.2 nop-batch 已有的实现

这些实现封装了所有底层 IO，nop-stream 通过适配器自动获得：

#### Loader 实现（Source 端）

| 实现类 | 模块 | 封装的底层抽象 | 支持的数据源 |
|---|---|---|---|
| `ResourceRecordLoaderProvider` | nop-batch-core | `IResourceRecordInputProvider` → `IRecordInput` | CSV、JSONL、任意文件格式 |
| `OrmQueryBatchLoaderProvider` | nop-batch-orm | `IEntityDao` → 游标分页（findNext） | ORM 实体逐批查询 |
| `JdbcBatchLoaderProvider` | nop-batch-jdbc | `IJdbcTemplate` → JDBC `PreparedStatement` | SQL 查询 |
| `JdbcPageBatchLoaderProvider` | nop-batch-jdbc | `IJdbcTemplate` → JDBC 分页 | SQL 分页查询 |

#### Consumer 实现（Sink 端）

| 实现类 | 模块 | 封装的底层抽象 | 支持的数据目标 |
|---|---|---|---|
| `ResourceRecordConsumerProvider` | nop-batch-core | `IResourceRecordOutputProvider` → `IRecordOutput` | CSV、JSONL、任意文件格式 |
| `OrmBatchConsumerProvider` | nop-batch-orm | `IEntityDao` → `saveEntity` / `updateEntity` | ORM 实体写入/更新 |
| `JdbcBatchConsumerProvider` | nop-batch-jdbc | `IJdbcTemplate` → JDBC 批量执行 | SQL 批量写入 |

**一个适配器，覆盖所有数据源/目标**：只要 nop-batch 新增 Loader/Consumer 实现（如 Kafka、MongoDB），nop-stream 无需任何修改即可使用。

### 2.3 nop-batch 的附加能力

除了核心 Loader/Consumer，nop-batch 还提供：

| 能力 | 接口 | 用途 |
|---|---|---|
| 记录过滤 | `IBatchRecordFilter` | 在 Source 端过滤不需要的记录 |
| 记录转换 | `IBatchProcessor<S,R>` | flatMap 式的逐条转换 |
| 聚合统计 | `IBatchAggregator` | 读取过程中计算汇总信息 |
| 状态持久化 | `IBatchStateStore` | 记录已处理位置，支持恢复 |
| 多路分发 | `PartitionDispatchLoaderProvider` | 按分区字段分发到不同处理逻辑 |
| 适配器链 | `AdaptedBatchLoaderProvider` | 包装现有 Loader 添加额外逻辑 |

## 3. 适配器设计

### 3.1 BatchLoaderSourceFunction

将 `IBatchLoader<S>` 适配为 `SourceFunction<S>`：

```java
class BatchLoaderSourceFunction<S> implements SourceFunction<S> {
    final IBatchLoaderProvider<S> loaderProvider;
    IBatchLoader<S> loader;
    volatile boolean running = true;

    // batchSize=1 实现逐条推模型
    int batchSize = 1;

    public void run(SourceContext<S> ctx) {
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        loader = loaderProvider.setup(taskContext);
        IBatchChunkContext chunkContext = taskContext.newChunkContext();
        while (running) {
            List<S> batch = loader.load(batchSize, chunkContext);
            if (batch.isEmpty()) break;       // 数据读取完毕
            for (S item : batch) {
                ctx.collect(item);
            }
        }
    }

    public void cancel() {
        running = false;
    }
}
```

**用法**——从 CSV 文件读取：
```java
ResourceRecordLoaderProvider<MyRecord> loaderProvider = new ResourceRecordLoaderProvider<>();
loaderProvider.setRecordIO(new CsvResourceRecordIO());
loaderProvider.setResourcePath("/data/input.csv");

env.addSource(new BatchLoaderSourceFunction<>(loaderProvider), "csv-source");
```

**用法**——从数据库 ORM 读取：
```java
OrmQueryBatchLoaderProvider<MyEntity> loaderProvider = new OrmQueryBatchLoaderProvider<>();
loaderProvider.setEntityName("MyEntity");
loaderProvider.setDaoProvider(daoProvider);

env.addSource(new BatchLoaderSourceFunction<>(loaderProvider), "orm-source");
```

**同一个适配器，不同的 loaderProvider**。这是核心简化。

### 3.2 BatchConsumerSinkFunction

将 `IBatchConsumer<R>` 适配为 `SinkFunction<R>`：

```java
class BatchConsumerSinkFunction<R> implements SinkFunction<R>, AutoCloseable {
    final IBatchConsumer<R> consumer;
    final List<R> buffer;
    final int batchSize;

    // Consumer is eagerly initialized in constructor since SinkFunction has no lifecycle.
    // AutoCloseable.close() is called by StreamSinkOperator to flush remaining buffer.
    public BatchConsumerSinkFunction(IBatchConsumerProvider<R> consumerProvider, int batchSize) {
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        this.consumer = consumerProvider.setup(taskContext);
        this.batchSize = batchSize;
        this.buffer = new ArrayList<>(batchSize);
    }

    public void consume(R value) {
        buffer.add(value);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    public void close() {
        flush();
    }

    private void flush() {
        if (!buffer.isEmpty()) {
            consumer.consume(buffer, null);
            buffer.clear();
        }
    }
}
```

**用法**——写入 CSV 文件：
```java
ResourceRecordConsumerProvider<MyRecord> consumerProvider = new ResourceRecordConsumerProvider<>();
consumerProvider.setRecordIO(new CsvResourceRecordIO());
consumerProvider.setResourcePath("/data/output.csv");

stream.addSink(new BatchConsumerSinkFunction<>(consumerProvider));
```

**用法**——写入数据库：
```java
OrmBatchConsumerProvider<MyEntity> consumerProvider = new OrmBatchConsumerProvider<>();
consumerProvider.setEntityName("MyEntity");
consumerProvider.setDaoProvider(daoProvider);
consumerProvider.setAllowInsert(true);

stream.addSink(new BatchConsumerSinkFunction<>(consumerProvider));
```

### 3.3 适配器总览

nop-stream 只需要**两个适配器类**，约 120 行代码：

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
                   │   nop-batch interfaces   │
                   │  IBatchLoaderProvider<S>  │
                   │  IBatchConsumerProvider<R>│
                   └────────────┬─────────────┘
                                │
        ┌───────────┬───────────┼───────────┬────────────┐
        │  Resource │   ORM     │   JDBC     │  (未来)    │
        │  Record   │  Entity   │  Statement │            │
        │  CSV/JSONL│  Dao      │            │            │
        └───────────┴───────────┴───────────┴────────────┘
```

## 4. IMessageService 与 Debezium：nop-batch 之外的补充

`IMessageService`（消息队列）和 `DebeziumMessageSource`（CDC）是实时数据源，nop-batch 的 `IBatchLoader` 不适合封装（Loader 是同步拉模型，消息是异步推模型）。需要单独适配。

### 4.1 MessageSourceFunction

```java
class MessageSourceFunction<T> implements SourceFunction<T> {
    final IMessageService messageService;
    final String topic;
    IMessageSubscription subscription;
    volatile boolean running = true;

    public void run(SourceContext<T> ctx) throws Exception {
        subscription = messageService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext context) {
                ctx.collect((T) msg);
                return null; // ACK
            }
        });
        while (running) Thread.sleep(1000); // 阻塞等待 cancel
    }

    public void cancel() {
        running = false;
        if (subscription != null) subscription.cancel();
    }
}
```

**已有 IMessageService 实现**：`LocalMessageService`（进程内）、`PulsarMessageService`（Apache Pulsar）。Kafka 接入通过实现 `IMessageService` 的 Kafka 适配器完成，不直接使用 Kafka client API。

### 4.2 DebeziumCdcSourceFunction

```java
class DebeziumCdcSourceFunction implements SourceFunction<ChangeEvent> {
    final DebeziumConfig config;
    DebeziumMessageSource source;
    volatile boolean running = true;

    public void run(SourceContext<ChangeEvent> ctx) throws Exception {
        source = new DebeziumMessageSource(config);
        ICancellable subscription = source.subscribe(event -> ctx.collect(event));
        while (running) Thread.sleep(1000);
        subscription.cancel(); // 清理
    }

    public void cancel() {
        running = false;
        if (source != null) source.stop();
    }
}
```

CDC 的 `ChangeEvent` 天然适合流处理：`timestamp` 可作为事件时间戳，`key` 可用于 keyBy，`after` 是实际数据。

### 4.3 MessageSinkFunction

```java
class MessageSinkFunction<T> implements SinkFunction<T> {
    final IMessageService messageService;
    final String topic;

    public void invoke(T value) {
        messageService.send(topic, value);  // 同步发送。生产环境可改用 sendAsync 提高吞吐
    }
}
```

**2PC 扩展**：Pulsar 支持事务，可实现 `TwoPhaseCommitSinkFunction` 提供 exactly-once 输出。

## 5. 连接器汇总

| 适配器 | 依赖 | 代码量 | 覆盖的数据源/目标 |
|---|---|---|---|
| `BatchLoaderSourceFunction` | nop-batch-core | ~60 行 | CSV、JSONL、ORM、JDBC（及未来所有 Loader） |
| `BatchConsumerSinkFunction` | nop-batch-core | ~60 行 | CSV、JSONL、ORM、JDBC（及未来所有 Consumer） |
| `MessageSourceFunction` | nop-message-core | ~40 行 | Pulsar、LocalMessage、Kafka（待实现 IMessageService 适配） |
| `MessageSinkFunction` | nop-message-core | ~15 行 | Pulsar、LocalMessage、Kafka（待实现 IMessageService 适配） |
| `DebeziumCdcSourceFunction` | nop-message-debezium | ~30 行 | MySQL、PostgreSQL、SQL Server CDC |

**4 个适配器类 + 1 个 CDC 适配器**，约 200 行代码，覆盖文件、数据库、消息队列、CDC 四大数据源。

## 6. 与 nop-batch 的关系

### 6.1 模型对比

| 维度 | nop-batch | nop-stream |
|---|---|---|
| 处理模型 | 分块拉模型（chunk-based pull） | 逐条推模型（record-by-record push） |
| 数据特征 | 有限数据集（全表扫描、文件） | 无限数据流（消息队列、CDC） |
| 状态管理 | 无（每个 chunk 独立） | 有（窗口状态、CEP 状态） |
| 时间语义 | 无 | 事件时间 / 处理时间 |
| Loader 调用 | `load(batchSize, ctx)` → 一批 | `load(1, ctx)` → 逐条 |

### 6.2 互补关系

- **nop-batch 负责"数据怎么读/写"**：封装文件格式、ORM 查询、JDBC 连接等底层细节
- **nop-stream 负责"数据怎么处理"**：窗口聚合、CEP 模式匹配、状态管理等流处理语义
- **共享基础设施**：两者都使用 `IRecordInput/Output`、`IEntityDao`、`IResource` 等底层抽象

**典型 ETL 管线**：
```
nop-batch Loader → nop-stream 窗口/CEP 处理 → nop-batch Consumer
```

### 6.3 batchSize 的含义差异

在 nop-batch 中 `batchSize` 影响性能（更大的批 = 更少的 IO 调用）。在 nop-stream 适配器中：
- `BatchLoaderSourceFunction.batchSize = 1`：逐条发射，保证流语义
- `BatchConsumerSinkFunction.batchSize = 100`：缓冲后批量提交，兼顾性能

## 7. 实现路径

```
阶段一（最小可用）:
  BatchLoaderSourceFunction + BatchConsumerSinkFunction
  → 文件→窗口聚合→文件 的 ETL 管线
  → 数据库→转换→数据库 的 ETL 管线
  （依赖：nop-batch-core + nop-batch-orm/nop-batch-jdbc）

阶段二（实时流）:
  + MessageSourceFunction + MessageSinkFunction
  → 消息驱动的实时处理
  （依赖：nop-message-core + nop-message-pulsar）

阶段三（CDC）:
  + DebeziumCdcSourceFunction
  → 数据库变更实时捕获 + 流处理
  （依赖：nop-message-debezium）

阶段四（Exactly-Once）:
  + CheckpointedSourceFunction 适配
  + Pulsar 2PC SinkFunction
  → 端到端 exactly-once 语义
```

## 8. 已知限制

1. **Kafka IMessageService 适配器未实现** — `nop-message-kafka` 模块为空，需要编写 `IMessageService` 的 Kafka 适配。所有消息队列接入统一走 `IMessageService` 接口，不直接使用底层客户端 API
2. **适配层已实现** — 核心适配器（BatchLoader/Consumer、MessageSource/Sink、DebeziumCdc）已实现并通过测试，位于 `nop-stream-connector` 模块
3. **消息 Source 的背压** — nop-stream 当前无背压机制。消息 Source 消费速度可能快于下游处理速度，需依赖消息系统的 ACK 隐式背压
4. **IBatchChunkContext 在 consume 中** — `BatchConsumerSinkFunction` 的 `consume()` 调用中传 null 作为 `IBatchChunkContext`，这不影响 Consumer 的核心写入逻辑，但会丢失 chunk 级的统计和状态追踪。可按需提供 `taskContext.newChunkContext()` 实例
5. **ORM Source 的增量模式** — `OrmQueryBatchLoaderProvider` 是全表扫描。增量读取需要配置时间戳过滤条件或自增 ID 范围
