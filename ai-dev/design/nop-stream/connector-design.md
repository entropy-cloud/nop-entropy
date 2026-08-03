# 连接器设计

> Status: active
> Created: 2026-05-20
> Revised: 2026-05-23
> Parent: `01-architecture-baseline.md` §7（与 Nop 平台的集成）

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

## 4. Split-based Source 协议（FLIP-27 风格）

分布式场景下，source 采用 **FLIP-27 风格的 split-based 架构**：`Source` / `SplitEnumerator` / `SourceReader` / `SourceSplit` 四个核心契约，配合 whole-split assignment（整数 split 分配给 reader，不做 fraction 拆分）。

### 4.0 范式裁定（Stage 49 D1）

**选了什么**：FLIP-27 风格（whole-split assignment）。`Source<OUT,SplitT,EnumStateT>` 工厂创建 `SplitEnumerator<SplitT,StateT>`（coordinator 侧，单点）和 `SourceReader<OUT,SplitT>`（task 侧，每并行实例一个）。Split 是不可分割的整体，由 enumerator 整数分配给 reader，reader 持有整个 split 的 cursor（offset/position）。

**拒绝的范式（Beam-SDF）及逐项裁定**：

| Beam-SDF 元素 | 裁定 | 理由 |
|---------------|------|------|
| `RestrictionTracker<R>` + `tryClaim(restriction, position)` | **reject** | FLIP-27 无 fraction-splitting，whole-split assignment 不需要 restriction 内的位置声明；reader 直接消费整个 split 的 cursor |
| `DynamicSplitRequest{fraction}` + `DynamicSplitResponse{primary,residual}` | **reject** | fraction-splitting 引入跨 reader 的 split stealing 复杂度，与 v1 Non-Goal「跨运行 reader 的弹性 split 再分配」冲突；whole-split assignment 已满足 v1 scope |
| `WatermarkEstimator` | **defer（v1 Non-Goal successor）** | source 侧 watermark estimation 是独立的 watermark 推进模型，与现有 `TimestampsAndWatermarksOperator` 路径不重叠；v1 不引入以避免两套 watermark 路径并存 |
| `SourceEvent` 自定义 coordinator↔reader 事件 | **defer（v1 Non-Goal successor）** | FLIP-27 自定义事件通道用于高级协调（如动态 partition 发现通知）；v1 走 pull 模型（`handleSplitRequests`）已满足动态 split 发现的最小语义 |
| `DrainableSource` marker | **保留** | 与 §5.3 既有 drain 语义对齐；unbounded source 实现 `DrainableSource` 可在 `JobTerminationMode.DRAIN` 时截断为有限，未实现则拒绝 DRAIN（要求 CANCEL） |
| `SourceWorkUnit` 占位类（`io.nop.stream.core.connector.SourceWorkUnit`） | **superseded** | 标 `@Deprecated`。新 `Source` 契约（§4.1）取代其语义；保留类是为了向后兼容已序列化的旧 savepoint（如有），新代码一律用新接口 |

**为什么选 FLIP-27 而非 Beam-SDF**：
- roadmap Stage 49 明确要求「FLIP-27 风格」
- Flink FLIP-27 的 whole-split assignment 与 nop-stream 现有 `SourceEnumerator`（concrete，6-state）+ `SourceEnumeratorState` 数据结构同构（`SourceEnumeratorState.java:22` 的 discovered/unassigned/assigned/finished/pending-ack/discovery-cursor 6 字段即 §5.3 6-state 分解），改造为接口体系代价最小
- Beam-SDF 的 restriction tracker 在没有 split stealing/fraction 需求时引入无收益的复杂度（违反 plan guide #24「不引入无第二消费者的空壳抽象」）

### 4.1 核心契约（接口定义见源码）

```
Source<OUT, SplitT extends SourceSplit, EnumStateT>
  +createEnumerator(ctx): SplitEnumerator<SplitT, EnumStateT>     // coordinator 侧，无并行
  +restoreEnumerator(ctx, state): SplitEnumerator<SplitT, EnumStateT>
  +createReader(ctx): SourceReader<OUT, SplitT>                    // task 侧，每并行一个
  +getEnumeratorStateSerializer(): SimpleVersionedSerializer<EnumStateT>
  +getSplitSerializer(): SimpleVersionedSerializer<SplitT>
  +getBoundedness(): Boundedness

SplitEnumerator<SplitT, StateT>
  +start()                                                          // 部署后启动
  +handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) // reader pull 模型
  +addReader(int subtaskIndex)                                       // reader 注册
  +snapshotState(long checkpointId): StateT                          // coordinator checkpoint
  +close()

SourceReader<OUT, SplitT>
  +start()                                                          // task 线程启动
  +addSplits(List<SplitT> splits)                                    // 接收 enumerator 分配
  +handleNoRecordAvailable()                                         // idle 回调
  +pollNext(): Optional<OUT>                                         // 主循环拉取
  +notifyCheckpointComplete(long checkpointId)
  +snapshotState(long checkpointId): List<SplitT>                    // per-split cursor（task operator state）
  +restoreState(List<SplitT> splits)                                 // 恢复 split cursor
  +close()

SourceSplit                                                        // 接口（非 concrete）
  +splitId(): String
```

**新增 `addSource(Source,...)` 入口**与既有 `addSource(SourceFunction,...)` 并列（D5 Transformation 路由裁定见 §4.3）。

### 4.2 Split 下发机制（D3）

**裁定**：初始 split **deploy 后经控制 RPC 下发**，**不嵌入** `TaskDeploymentDescriptor`。

**理由**：`TaskDeploymentDescriptor`（Stage 42）按设计**不携带 live runtime 对象**——它只携带算子模板与配置，split 是动态发现的运行时数据。把 split 塞进 descriptor 会破坏 descriptor 的「静态模板」语义，并要求 split 在 deploy 时就完全已知（违反「动态发现」目标）。

**下发流程**（控制面，基于 Stage 39 RPC）：

```
1. deployTask 部署 SourceReaderOperator 到各 subtask（不带 split）
2. coordinator 调 Source.createEnumerator() 创建 enumerator（coordinator 侧，单点）
3. enumerator.start() 执行初始 split 发现
4. coordinator 经控制 RPC（Stage 39 IStreamTaskRpcService）调各 subtask 的 SourceReader.addSplits(initialSplits)
5. reader 启动后经控制 RPC 调 enumerator.handleSplitRequest(subtaskIndex) 拉取更多 split（pull 模型）
6. reader 完成 split 后上报 finished split（经控制 RPC），enumerator 更新 finished 集合
```

**LOCAL 模式**（单进程）：coordinator 与 task 在同进程，控制 RPC 退化为直接方法调用（经 `MailboxExecutor` 投递 mail 到 task 线程，保证线程安全）。

**DISTRIBUTED 模式**：经 Stage 39 跨 JVM 控制面 RPC（`StreamControlRpcServer`/`StreamControlRpcProxyFactory`），fencing token 校验同 §2.1.2。

### 4.3 Transformation 路由裁定（D5）

**裁定**：新增独立 `SourceApiTransformation`（与既有 `SourceTransformation` 并列），`StreamGraphGenerator` 新增 `instanceof` 分支构建 `SourceReaderOperatorFactory`。

**理由**：
- 既有 `SourceTransformation` 持 `private final SourceFunction<OUT>`，类型已固定为 SourceFunction 路径；混入 `Source` 路径会污染既有 SourceFunction 编译期类型契约
- `StreamSourceOperator`（既有）专为 `SourceFunction.run(SourceContext)` 的 push 模型设计（mailbox 经 `SourceContext.collect()` emission 点 drain），与新 `SourceReader.pollNext()` 的 pull 模型执行循环不兼容
- 新增独立 transformation + operator 允许两套路径并存且互不污染（既有 SourceFunction 连接器零回归）

### 4.4 动态 split 发现分配裁定（D4）

**roadmap deliverable**「动态 split 发现分配」的最小实现 = **deploy + restore-time discovery** + **reader-driven `handleSplitRequest` pull 模型**。

- **deploy/restore-time discovery**：enumerator 在 `start()` / `restoreState()` 时执行一次性 split 发现（如目录扫描、partition enumeration）
- **reader-driven pull**：reader 完成当前 split 后经 `handleSplitRequest(subtaskIndex)` 向 enumerator 拉取更多 split

**Deferred（optimization candidate）**：持续后台轮询发现 unbounded split（push 模型完整调度）。理由：v1 参考 source 为 bounded（`FileSource`），无 unbounded 触发源；引入无触发源的空调度违反 plan guide #24。successor 由 unbounded source 连接器 plan 驱动（如真实 Kafka partition-as-split）。

### 4.5 旧 concrete 收敛策略（D6）

| 旧 concrete | 处置 | 新归属 |
|------------|------|--------|
| `SourceSplit`（concrete，`runtime/.../source/SourceSplit.java`） | 提升为接口 | 旧字段保留为默认实现 `SimpleSourceSplit`（splitId/description/cursor 三字段） |
| `SourceEnumerator`（concrete，326 行） | 删除 | 语义由新 `SplitEnumerator` 接口 + coordinator 实现（RoundRobinSplitEnumerator）承担 |
| `TestSourceEnumerator` / `TestDistributedExactlyOnce` 相关断言 | 迁移到新体系 | 改测 `SimpleSourceSplit` + 新 coordinator 路径 |

**不保留两套竞争系统**：旧 concrete 删除后，`SourceSplit`（接口）+ `SimpleSourceSplit`（默认实现）是唯一的 split 类型；`SplitEnumerator`（接口）+ 各 source 的具体 enumerator 实现是唯一的 enumerator 类型。

### 4.6 Coordinator-state Checkpoint 落地（D2，复用 §2.6/§5.3 设计）

**确认 `checkpoint-design.md §2.6`（manifest 字段 `sourceEnumeratorSnapshots`）+ §5.3（6-state 分解：discovered/unassigned/assigned/finished/pending-ack/discovery-cursor + restore 规则）为权威分解**。

**代码层落地**（设计层非新发明）：
- `EpochManifest` 新增 `sourceEnumeratorSnapshots` section（keyed by source vertex id），字段名与 §2.6 统一
- `CheckpointCoordinator` checkpoint 时调用各 source 的 `enumerator.snapshotState(epochId)` 写入该 section
- restore 时按 §5.3 规则重建 enumerator（先恢复 enumerator state，再恢复 reader split cursor）
- 序列化形式：经 `Source.getEnumeratorStateSerializer()`（`SimpleVersionedSerializer<EnumStateT>`），与 split serializer 同构

**这是落地已有设计为代码（代码层新机制，设计层非新发明）**。

### 4.7 OperatorCoordinator 抽象 bypass 裁定（D7）

`checkpoint-design.md §5.3.1` G35 把 Stage 49 successor scope 列为「(1) 引入 `OperatorCoordinator` 抽象 + (2) source enumerator state checkpointing」两项。**v1 只做 (2)**：enumerator 硬接到 `JobCoordinator`/`CheckpointCoordinator`，**不引入通用 `OperatorCoordinator` 抽象**。

**裁定**：v1 bypass 为 intentional。理由：单 source 用例不足以驱动通用抽象（避免空壳抽象违反 plan guide #24）；successor（如 sink global committer §5.3.1 item #3）再引入 `OperatorCoordinator` 并把硬接路径重构到抽象下。

**Successor path**：引入 `OperatorCoordinator` 的后续 plan（可能由 transactional JDBC sink Stage 52 或其它 global-committer 用例驱动）。

### 4.8 Split Assignment Recovery 协议

分布式 source checkpoint 涉及三方状态，恢复时必须正确协调：

| 状态 | 持有者 | 内容 | Checkpoint 时机 |
|------|--------|------|----------------|
| **Enumerator State** | SplitEnumerator（JobCoordinator 侧） | 6-state 分解（§5.3）：discovered/unassigned/assigned/finished/pending-ack/discovery-cursor | `snapshotState(epochId)` → 写入 `EpochManifest.sourceEnumeratorSnapshots` section |
| **Reader Split Cursor** | SourceReader（TaskManager 侧） | 当前持有 split 的读取位置（per-split cursor） | reader 的 `snapshotState(epochId)` → 写入 `TaskEpochSnapshot`（task operator state） |
| **Assignment Tracker** | SplitEnumerator（JobCoordinator 侧） | 已下发但 reader 尚未 checkpoint 确认的 split（即 §5.3 的 `pending acknowledgements`） | 与 Enumerator State 同步快照（同一 `snapshotState` 调用） |

**核心问题**：split 在 epoch N 之后、epoch N+1 之前下发给 reader，reader 在 epoch N+1 之前失败。此时：
- Enumerator 已将该 split 从"未分配"移到"已分配"
- Reader 恢复到 epoch N 的状态，**不持有**这个 split
- 如果不做特殊处理，该 split 会丢失

**恢复流程**（§5.3 restore 规则）：

```
1. Coordinator 从 EpochManifest.sourceEnumeratorSnapshots 恢复 Enumerator State
2. Coordinator 从 TaskEpochSnapshot 恢复各 reader 的 split cursor
3. Coordinator 从 pending acknowledgements 中取出 epoch > N 的下发记录
   └── 这些 split 已下发但 reader 未在恢复点确认
4. 对每个"孤儿 split"：
   a. 如果 reader 恢复后报告了该 split（cursor 已包含）→ 正常，无需操作
   b. 如果 reader 未报告该 split → 将 split 归还给 Enumerator 的"未分配"集合
5. Reader 恢复后向 Coordinator 注册（报告自己持有的 split）
6. Coordinator 根据注册信息和归还的 split 重新分配
```

**与 checkpoint-design.md §5.3 的对应关系**：

§5.3 定义的 enumerator state 6 字段中，`pending acknowledgements` 即本节的 assignment tracker。恢复时孤儿 split 回填到 `unassigned` 集合。**§5.3 是权威分解**，本节是其恢复流程的操作化描述。

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

### 5.3 JdbcTwoPhaseCommitSink（事务型 JDBC sink，Stage 52）

#### 5.3.1 定位

复用 `TwoPhaseCommitSinkFunction<IN>` 基础设施（已落地并充分测试），对 JDBC 目标实现 exactly-once 输出。每个 checkpoint epoch 映射一条 JDBC 事务：begin → 内存缓冲 → saveState 转入 pendingCommits → preCommit（仅校验）→ commit（新事务写数据+ledger）→ abort/rollback（丢弃内存）。

#### 5.3.2 D1 裁定：内存缓冲模型（标准 JDBC 无 XA）

**选了什么**：内存缓冲模型（buffer-in-memory），而非「preCommit flush 到 JDBC 连接」模型。

**关键约束**：标准 JDBC `Connection` **不跨 JVM/task 死亡存活**——未提交写入在连接断开时被 DB 回滚。若在 preCommit 时把数据 flush 到一条 JDBC 连接，则该连接在 task 死亡时丢失，数据也被回滚——违反 exactly-once。

**行为语义**：

| 方法 | 行为 |
|------|------|
| `invoke(value)` | 追加到当前 epoch 的**内存**批次缓冲（不触 JDBC）。 |
| `saveState(epochId)` | **覆盖基类**：先把当前内存批次经 `getPendingCommits().put(epochId, batch)` 转入 `pendingCommits[epochId]`（可序列化 `List<Map<String,Object>>`），清空内存缓冲，**再**调 `super.saveState(epochId)`——使该 epoch 批次在**本次** checkpoint 即被持久化（而非落后一个 epoch）。 |
| `preCommit(epochId)` | 因 `saveState` 已完成转入，`preCommit` 仅做 no-op（不触 JDBC）。 |
| `commit(epochId)` | 从 `getPendingCommits().get(epochId)` 读批次，**开一条新 JDBC 事务**（独立 `openConnection`，`autoCommit=false`），在同一 `connection.commit()` 内**原子**写数据 + 插入 ledger 行（`epoch_id` 主键），成功后从 `pendingCommits` 移除。 |
| `rollback()` | 丢弃当前内存批次。 |
| `abort(epochId)` | 丢弃 `pendingCommits[epochId]`（commit 前未触 JDBC，无需 DB 清理）。 |

**saveState 先于 preCommit 的 live-code 顺序**：`StreamSinkOperator.processBarrier` 中 `saveState(epochId)`（`:78`）运行在 `prepareCommit(epochId)`→`preCommit`（`:88`）**之前**。若在 `preCommit` 才把批次转入 `pendingCommits`，则 `saveState(N)` 抓不到 epoch N 的批次（落后一个 epoch，restore 时该 epoch 数据永久丢失）。解决：覆盖 `saveState`。

**subsuming 约束**：基类 `finishCommit(M,true)` 对每个 `eid<=M` 调 `commit(eid)`——每次 `commit(eid)` 是**独立** JDBC 事务（各自的 `openConnection`），**不共享 connection**，保证 per-epoch 原子性与 ledger 一致性。

**幂等 commit 守卫**：`commit` 前在同一事务内查 ledger 表，若该 epoch 已记录则跳过写数据（recover-safe 重提交不产生重复数据）。ledger 表 `epoch_id` 为主键，DB 层面保证幂等。

**拒绝的替代方案**：「preCommit flush 到 JDBC 连接」模型——标准 JDBC 连接不跨死亡存活，task 死亡时丢数据，违反 exactly-once。仅 XA 事务才支持跨死亡存活的事务恢复，但 XA 不在本 plan scope 内。

#### 5.3.3 D2 裁定：模块放置

**选了什么**：新建独立模块 `nop-stream-connector-jdbc`（compile 依赖 `nop-stream-core` + `nop-dao`）。

**拒绝的替代方案**：放入 `nop-stream-connector-batch` 并新增 `nop-dao` compile 依赖。

**理由**：
- `nop-stream-connector-batch` 的 compile 依赖是 `nop-stream-core` + `nop-batch-core`（不含 `nop-dao`）。事务型 JDBC sink 不使用 `nop-batch-core` 的任何类型（`IBatchConsumer` / `IBatchLoader`），放入 batch 模块会引入不必要的传递依赖。
- 已有模块边界先例：`nop-stream-connector-debezium` 独立于 `nop-stream-connector-batch`，各自只引入所需依赖。
- 新模块不违反 AR-2：AR-2 只约束基模块 `nop-stream-connector`（不依赖 `nop-dao`/`nop-batch-jdbc`），不约束新模块。

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
| `JdbcTwoPhaseCommitSink` | nop-dao | ~200 行 | JDBC（多 DB 经 `IDialect`） | 2PC（epoch ledger 幂等 commit） |
| `DebeziumCdcSourceFunction` | nop-message-debezium | ~30 行 | MySQL、PostgreSQL CDC | DrainableSource（设计） |

**Split-based Source（FLIP-27 风格，Stage 49 起）**：

| Source | 模块 | 范围 | 分布式能力 |
|---|---|---|---|
| `FileSource`（bounded 参考 source） | `nop-stream-connector` | 目录→文件 split 枚举、按字节 offset cursor | split-based 并行读，coordinator-state checkpoint（§4.6） |

## 7. 已知限制

1. **Kafka IMessageService 适配器未实现** — `nop-message-kafka` 模块为空（Stage 48 已实现 `KafkaMessageService`，partition-as-split Source 是后续连接器 plan）
2. **消息 Source 的背压** — 当前无背压机制，依赖消息系统 ACK 隐式背压
3. **IBatchChunkContext 传 null** — `BatchConsumerSinkFunction` 的 consume 调用传 null，丢失 chunk 级统计
4. **ORM Source 全表扫描** — 增量读取需配置时间戳过滤或自增 ID 范围
5. **BatchLoaderSourceFunction 不支持 split 拆分** — 批数据源是有限的，whole-split assignment 已足够；fraction-splitting 经 §4.0 D1 裁定 reject
6. **OperatorCoordinator 通用抽象 v1 bypass** — enumerator 硬接到 `JobCoordinator`/`CheckpointCoordinator`，未引入通用 `OperatorCoordinator` 抽象（§4.7 D7）；successor 由 sink global committer 等用例驱动
7. **持续后台轮询发现 unbounded split（push 模型）deferred** — v1 仅支持 deploy/restore-time discovery + reader-driven pull（§4.4 D4）；successor 由 unbounded source 连接器 plan 驱动
8. **`SourceWorkUnit` superseded** — 旧占位类标 `@Deprecated`，新代码用 `Source`/`SourceSplit` 接口（§4.0 D1）
