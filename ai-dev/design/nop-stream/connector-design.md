# 连接器设计

> Status: active
> Created: 2026-05-20
> Revised: 2026-09-03（item 19：新增 §8 SPI 注册与能力矩阵——D1..D8 裁定：三工厂形态/方向作用域类型名/NopIoC beans.xml 载体/能力描述符单一事实源/XDSL 消费路径/catalog 工具入口/未知类型名 fail-fast/OLAP 最小集三态/tis OQ-2）
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

### 5.4 DebeziumCdcSourceFunction + CDC checkpoint offset 集成（Stage 53）

#### 5.4.1 定位

`DebeziumCdcSourceFunction` 实现 `CheckpointedSourceFunction<ChangeEvent>`，使 CDC 消费位点参与 nop-stream checkpoint 协议：checkpoint 时把 Debezium offset map 持久化进 operator state，恢复时重建 offset store 让引擎从 checkpoint 位点继续消费（无重复、无丢失）。同时修复 `config` 字段的 `transient` 问题（跨 JVM 恢复丢失连接信息）。

#### 5.4.2 D1 裁定：自定义 NopStreamOffsetBackingStore + connector-name registry

**选了什么**：自定义 `NopStreamOffsetBackingStore`（implements Kafka Connect `OffsetBackingStore` SPI），由 in-memory `ConcurrentHashMap<ByteBuffer,ByteBuffer>` 支撑。

**拒绝的替代方案**：
- `FileOffsetBackingStore`：格式是 Kafka Connect 内部序列化（schema envelope + ByteBuffer），手工写文件易出错且格式随版本变化。
- 扩展 `ChangeEventMetadata` 携带 raw Debezium source partition/offset map：`io.debezium.engine.ChangeEvent<byte[],byte[]>` 只暴露 `key()`/`value()`，获取 raw offset 需迁移到 `ChangeEventWithMetadata` + `ChangeConsumer` API（非 trivial）。offset 持久化**完全由 `NopStreamOffsetBackingStore` 承担**（Debezium engine 内部 commit offset → store），不从事件元数据提取。`ChangeEventMetadata` 扩展为 successor。

**接线裁定（Debezium 2.4.0 约束）**：Debezium 2.4.0 的 `DebeziumEngine.Builder` **不暴露** `using(OffsetBackingStore)` 方法，无法直接把 store 实例交给 engine builder。实际机制：engine 从 `offset.storage` 属性读取 store 类 FQCN，经反射实例化（public no-arg constructor + `configure(WorkerConfig)`）。为桥接 source function 创建的实例（持有恢复的 offsets）与 engine 经反射创建的实例，`NopStreamOffsetBackingStore` 维护**静态 registry（connector name → 共享 data map）**：source function 经 `forConnector(name)` 创建实例并 pre-populate，engine 实例在 `configure(WorkerConfig)` 时按 connector name 绑定到同一份 data map。

**registry 生命周期（AR-03 修复，2026-08-13）**：静态 registry 是"只进不出"的设计曾导致同 JVM 内复用连接器名即从陈旧 offset 续跑（崩溃未 checkpoint 的运行、redeploy、不同 pipeline 复用名），未命名连接器共享 `_default_` 桶互相污染。修复后的生命周期契约：

- **首跑清理**：`DebeziumCdcSourceFunction.initializeState` 的两个 fresh 分支（`state == null`；或 `state` 非 null 但无 `cdc-offsets` 条目）在绑定前先调 `NopStreamOffsetBackingStore.clearConnector(name)` 再 `forConnector(name)`——新作业永不继承上一运行遗留的静态 offset，引擎从起点（snapshot）开始。恢复分支（`state` 含 offsets 条目）不清理，保留 checkpoint 恢复语义。
- **未命名连接器 fail-fast**：三处 `_default_` 回退点全部改为抛异常——`DebeziumCdcSourceFunction.resolveConnectorName()` 抛 `StreamException(ERR_STREAM_CONFIG_ERROR)`（connector 模块有 core 依赖）；store 侧 `configure(WorkerConfig)` 与 `ensureBound()` 抛模块本地 `NopException(ERR_DEBEZIUM_CONNECTOR_NAME_REQUIRED)`（nop-message-debezium 无 core 依赖，不可抛 StreamException）。共享桶选项（Option B）显式拒绝：同 JVM 并发未命名 pipeline 静默互相覆盖 offset 无法可靠检测，违反 No-Silent-No-Op。
- **`clearConnector` 语义**：不再仅测试使用——首跑路径按连接器名清除陈旧 entry；测试隔离为次要用途。

**类型不对称 API 使用说明**：`CheckpointedSourceFunction.snapshotState` 写入 `OperatorSnapshotResult`（经 `putOperatorState`），`initializeState` 从 `TaskStateSnapshot`（经 `getOperatorState`）读取。operator state key = `"cdc-offsets"`。offset map 序列化为 `TreeMap<String,String>`（base64 编码 key/value——`ByteBuffer` 不可序列化）。`StreamSourceOperator.snapshotState/restoreState` 负责两个类型之间的转换。

### 5.5 FileTwoPhaseCommitSink（exactly-once 文件 sink，Stage 53）

#### 5.5.1 D2 裁定：模块放置

**选了什么**：放入 `nop-stream-connector/file/`（基模块 `nop-stream-connector`，仅依赖 `nop-stream-core`，已有 `FileSource` 在此子包）。v1 仅 text-line + NIO，无额外依赖。

**拒绝的替代方案**：新建独立模块 `nop-stream-connector-file`。理由：v1 无额外依赖，过早拆分违反 plan guide #24；当 format SPI（CSV/JSON/Parquet）引入时再考虑拆分。

#### 5.5.2 行为模型

每个 checkpoint epoch 映射一个输出文件 + 一条 manifest 记录：

| 方法 | 行为 |
|------|------|
| `invoke(value)` | 追加到当前 epoch 的内存缓冲（`List<String>`）。 |
| `saveState(epochId)` | **覆盖基类**：先把内存缓冲写入 temp file（`{outputDir}/.{epochId}.tmp`），记录 `pendingCommits[epochId] = FilePendingCommit(tempPath, recordCount)`，清空内存缓冲，**再**调 `super.saveState`（saveState-first 模式，与 JDBC sink §5.3.2 一致）。 |
| `preCommit(epochId)` | no-op（temp file 已在 saveState 写入）。 |
| `commit(epochId)` | 从 `pendingCommits[epochId]` 读 `FilePendingCommit`。幂等守卫：先查 manifest，若该 epoch 已记录则跳过。否则 `Files.move(tempPath, finalPath, ATOMIC_MOVE)` + manifest 原子更新（写 `manifest.json.tmp` → `Files.move(ATOMIC_MOVE)`）。 |
| `abort(epochId)` | delete `.{epochId}.tmp`（temp 不存在时 no-op）。 |
| `rollback()` | 丢弃当前内存缓冲。 |

#### 5.5.3 边缘处理：final-exists 但 manifest-missing

若 `Files.exists(finalPath)` 但 manifest 无记录——说明上次 crash 在 rename 后/manifest 写入前。裁定：**修复 manifest**（补写 entry，跳过 rename），而非报错。理由：rename 已成功，数据已 durable，只需同步 manifest。

## 6. 连接器汇总

| 适配器 | 依赖 | 代码量 | 覆盖的数据源/目标 | 分布式能力 |
|---|---|---|---|---|
| `BatchLoaderSourceFunction` | nop-batch-core | ~60 行 | CSV、JSONL、ORM、JDBC | — |
| `BatchConsumerSinkFunction` | nop-batch-core | ~60 行 | CSV、JSONL、ORM、JDBC | — |
| `MessageSourceFunction` | nop-message-core | ~40 行 | Pulsar、LocalMessage | AT_LEAST_ONCE（broker 重投递，无 offset checkpoint） |
| `MessageSinkFunction` | nop-message-core | ~15 行 | Pulsar、LocalMessage | AT_LEAST_ONCE（2PC 候选见 §5.2 注） |
| `JdbcTwoPhaseCommitSink` | nop-dao | ~200 行 | JDBC（多 DB 经 `IDialect`） | 2PC（epoch ledger 幂等 commit） |
| `DebeziumCdcSourceFunction` | nop-message-debezium | ~200 行 | MySQL、PostgreSQL CDC | DrainableSource + CheckpointedSourceFunction（CDC offset checkpoint/restore） |
| `FileTwoPhaseCommitSink` | nop-stream-core | ~200 行 | text-line 文件 | 2PC（temp file + atomic rename + manifest） |

**Split-based Source（FLIP-27 风格，Stage 49 起）**：

| Source | 模块 | 范围 | 分布式能力 |
|---|---|---|---|
| `FileSource`（bounded 参考 source） | `nop-stream-connector` | 目录→文件 split 枚举、按字节 offset cursor | split-based 并行读，coordinator-state checkpoint（§4.6） |

## 7. 已知限制

1. **Kafka IMessageService 适配器未实现** — `nop-message-kafka` 模块为空（Stage 48 已实现 `KafkaMessageService`，partition-as-split Source 是后续连接器 plan）
2. **消息 Source 的背压** — 当前无背压机制，依赖消息系统 ACK 隐式背压
3. **ORM Source 全表扫描** — 增量读取需配置时间戳过滤或自增 ID 范围
4. **BatchLoaderSourceFunction 不支持 split 拆分** — 批数据源是有限的，whole-split assignment 已足够；fraction-splitting 经 §4.0 D1 裁定 reject
5. **OperatorCoordinator 通用抽象 v1 bypass** — enumerator 硬接到 `JobCoordinator`/`CheckpointCoordinator`，未引入通用 `OperatorCoordinator` 抽象（§4.7 D7）；successor 由 sink global committer 等用例驱动
6. **持续后台轮询发现 unbounded split（push 模型）deferred** — v1 仅支持 deploy/restore-time discovery + reader-driven pull（§4.4 D4）；successor 由 unbounded source 连接器 plan 驱动
7. **`SourceWorkUnit` superseded** — 旧占位类标 `@Deprecated`，新代码用 `Source`/`SourceSplit` 接口（§4.0 D1）
8. **Debezium 2.4.0 无 `DebeziumEngine.using(OffsetBackingStore)`** — CDC offset store 经 `offset.storage` FQCN 反射实例化 + connector-name registry 桥接实例（§5.4.2 D1）。successor：当 Debezium 版本升级暴露直接注入 API 时简化桥接
9. **`ChangeEventMetadata` 不携带 raw Debezium source partition/offset map** — v1 offset 持久化完全由 `NopStreamOffsetBackingStore` 承担。successor：迁移 `DebeziumEngineWrapper` 到 `ChangeEventWithMetadata` + `ChangeConsumer` API 以支持 per-event offset 可观测性
10. **文件 sink v1 为 per-checkpoint-epoch 单文件 + text-line** — 滚动策略（按大小/时间切分）与 format SPI（CSV/JSON/Parquet）为 successor
11. **消息 Source/Sink 无 offset checkpoint** — `MessageSourceFunction`/`MessageSinkFunction` 不参与 checkpoint（无 offset 持久化），一致性依赖消息系统 broker 侧重投递（AT_LEAST_ONCE）；exactly-once 消息路径需后续 2PC 消息连接器（见 §5.2 注）
12. **`DrainableSource` 契约未接线** — `DebeziumCdcSourceFunction` 实现了 `truncateForDrain()`，但 runtime DRAIN 收敛路径当前不调用该契约（生产调用点为零）；接线属 runtime 侧决策，见审计报告 2026-09-01 connectors §2.3 W-4

## 8. 连接器 SPI 注册与能力矩阵（item 19 / P-REQ-28，2026-09-03 裁定）

> 本章为架构决策记录（选了什么/为什么/拒绝了什么），不含实现级类签名。代码事实源 = `nop-stream-core` connector registry 包 + 各连接器模块 factory/beans.xml。

### 8.1 D1 工厂接口族与注册资格集

**选了什么**：按方向分立的工厂契约——source 工厂（创建物 = 既有 push 模型 `SourceFunction` 端点实例）、split-source 工厂（创建物 = 既有 FLIP-27 `Source` 端点实例）、sink 工厂（创建物 = 既有 `SinkFunction` 端点实例）三形态，共享「类型名 / 别名 / 能力描述符」公共契约。**不引入新执行契约**：工厂创建物就是既有端点类型，注册层只做发现/构造/能力声明。

**注册资格集 = 8 个端点组件**：

| 方向 | 类型名 | 端点组件 | 模块 |
|---|---|---|---|
| source（split-based） | `file` | `FileSource` | nop-stream-connector |
| source | `message` | `MessageSourceFunction` | nop-stream-connector |
| source | `debezium-cdc` | `DebeziumCdcSourceFunction` | nop-stream-connector-debezium |
| source | `batch-loader` | `BatchLoaderSourceFunction` | nop-stream-connector-batch |
| sink | `file` | `FileTwoPhaseCommitSink` | nop-stream-connector |
| sink | `message` | `MessageSinkFunction` | nop-stream-connector |
| sink | `jdbc-2pc` | `JdbcTwoPhaseCommitSink` | nop-stream-connector-jdbc |
| sink | `batch-consumer` | `BatchConsumerSinkFunction` | nop-stream-connector-batch |

**显式不注册（理由随档）**：`FileSourceReader`（由 `FileSource.createReader()` 内部构造，非独立实例化端点——与能力矩阵「随 source」行一致）；`JdbcTwoPhaseCommitSinkBuilder`（fluent builder 辅助物，其 `build()` 产物 `JdbcTwoPhaseCommitSink` 已注册）；`FileSplit`/`FileSplitEnumerator`/`FileSplitEnumeratorState`/`FilePendingCommit`（split 机制件/值对象，非端点）；`StreamConnectors`（DataStream 静态门面辅助类，非可注册物）。

**拒绝的替代方案**：
- 单一工厂接口返回 `Object` 端点——丢失类型契约，消费者 instanceof 分派等于隐性双契约；FLIP-27 `Source` 与 `SourceFunction` 是两套真实并存的执行契约（§4.3 D5），注册层应如实分立而非抹平。
- 单接口 + source/sink 双创建方法（不支持侧默认抛异常）——每个工厂半无效，违反「工厂创建物即其端点」的直观性。
- 把 FLIP-27 `Source` 包成 `SourceFunction` 适配器再注册——引入新执行语义，违反「不引入新执行契约」边界。

### 8.2 D2 类型名命名空间与别名

**选了什么**：**方向作用域**命名空间——`(direction, typeName)` 二元组唯一标识注册项；类型名 = kebab-case 家族名（清单见 §8.1 表）。`file` 在 source 与 sink 两侧各自注册合法（目录/探测输出均含方向列，无歧义）。

**别名机制**：解析语义 = 类型名精确匹配优先、别名次之；同方向内类型名或别名重复 = 注册期 fail-fast。本期 8 个工厂**别名均为空**——机制为未来重命名/兼容名预留，解析与优先级由注册发现测试以带别名的测试工厂钉定（不留未测分支）。

**拒绝的替代方案**：全局唯一类型名（`file-source`/`file-sink` 前缀式）——方向已由工厂契约承载，前缀冗余；且全局命名空间使某一侧引入新家族名受另一侧占用牵连。

### 8.3 D3 注册载体、聚合机制与既有解析世界的衔接

**注册载体**：各连接器模块在 `_vfs/nop/stream/beans/` 下新增按家族命名的 `connector-{file,message,jdbc,debezium,batch}.beans.xml`（base 模块贡献 file+message 两件），内含**无状态工厂 bean**（NopIoC 发现，无注解扫描——AGENTS 约束）。文件名不匹配 `app-*.beans.xml` 装配模式 → **不自动进入全局 app 容器**，仅显式装配时加载（连接器工厂不泄漏进每个应用的全局容器）。

**聚合机制**：注册中心从**任意** NopIoC 容器按类型收集全部工厂 bean（`getBeansOfType` 语义）一次性构建；构建期校验：类型名/别名冲突、描述符方向与工厂契约一致、描述符 typeName 与工厂声明一致——违者 typed error（fail-fast，无静默跳过）。

**工厂无状态裁定**：全部构造输入经**连接器配置对象**按调用传入——标量参数（string/int/string-list）+ 程序化对象参数（`IMessageService`/`IJdbcTemplate`/`DebeziumConfig`/`IBatchLoaderProvider`/`IBatchConsumerProvider`/recordMapper 函数等代码物/基础设施物）。参数校验边界：工厂校验**必需参数存在**（缺失 = typed error）；**未知参数拒绝属字段级 conf 校验 = item 20 边界，本期显式不做**（避免重复建设）。

**拒绝的替代方案**：
- 基础设施注入工厂 bean（如 message 工厂持有 IMessageService）——工厂绑定特定容器装配，探测/注册复杂化；且 `IJdbcTemplate` 等 per-pipeline 资源是作业作用域而非注册表作用域。
- 注册中心为全局单例（静态持有）——测试隔离差，与 fraud-example 程序化 resolver 世界冲突。
- `ServiceLoader` 发现——绕过「SPI = NopIoC 承载」既有裁定（综合报告 §2.4）。

**与既有 XDSL bean 解析世界的衔接**：**两世界并列、不合并**。既有 `BeanFunctionResolver`（程序化注册 / `GlobalBeanFunctionResolver`→NopIoC 全局容器）继续服务 bean 引用声明（fraud-example S1/S2 不受影响）；注册中心世界服务类型名发现与构造，经显式容器装配消费。本期注册中心**不**接入 `GlobalBeanFunctionResolver`（连接器工厂 bean 不进全局容器）；未来引入类型化声明时由 flow builder 直接消费注册中心（见 §8.5 预案）。

### 8.4 D4 能力描述符与单一事实源

**字段集**（与 `docs-for-ai/03-modules/nop-stream-connectors.md` 矩阵列对齐）：typeName、aliases、direction、componentClass、**交付语义**（source 侧 = `SourceConsistencyCapability` 枚举 / sink 侧 = `SinkConsistencyCapability` 枚举）、**并行度**（枚举：`PARALLEL` / `SINGLE_INSTANCE` / `PLANNING_GATE_PARALLELISM_1`）、**恢复语义**（枚举摘要：`NONE` / `OFFSET_CHECKPOINT` / `SPLIT_CURSOR_CHECKPOINT` / `TWO_PHASE_PENDING_COMMITS` / `BUFFERED_RETRY`）、**参数规格清单**（name / kind=STRING|INT|STRING_LIST|OBJECT / required / description）。

8 组件的声明值（Phase 2 代码实现的事实基线）：

| 类型名 | 交付语义 | 并行度 | 恢复语义 | 必需参数（OBJECT 类 = 程序化供给） |
|---|---|---|---|---|
| `file`（source） | AT_LEAST_ONCE | PARALLEL | SPLIT_CURSOR_CHECKPOINT | `directoryPath`:STRING |
| `message`（source） | AT_LEAST_ONCE（接口声明） | PARALLEL | NONE | `topic`:STRING, `messageService`:OBJECT(IMessageService) |
| `debezium-cdc`（source） | REPLAYABLE | SINGLE_INSTANCE | OFFSET_CHECKPOINT | `config`:OBJECT(DebeziumConfig) |
| `batch-loader`（source） | AT_LEAST_ONCE（声明） | PARALLEL | OFFSET_CHECKPOINT（发射计数） | `loaderProvider`:OBJECT(IBatchLoaderProvider)；可选 `batchSize`:INT |
| `file`（sink） | TWO_PHASE_COMMIT | PLANNING_GATE_PARALLELISM_1 | TWO_PHASE_PENDING_COMMITS | `outputDir`:STRING；可选 `charset`:STRING |
| `message`（sink） | AT_LEAST_ONCE（声明） | PARALLEL | NONE | `topic`:STRING, `messageService`:OBJECT(IMessageService) |
| `jdbc-2pc`（sink） | TWO_PHASE_COMMIT | PLANNING_GATE_PARALLELISM_1 | TWO_PHASE_PENDING_COMMITS | `jdbcTemplate`:OBJECT(IJdbcTemplate), `tableName`:STRING, `columns`:STRING_LIST, `recordMapper`:OBJECT(Function)；可选 `querySpace`:STRING, `ledgerTableName`:STRING |
| `batch-consumer`（sink） | IDEMPOTENT（声明） | PARALLEL | BUFFERED_RETRY | `consumerProvider`:OBJECT(IBatchConsumerProvider)；可选 `batchSize`:INT |

**单一事实源原则**（防止能力声明与门禁两套逻辑漂移）：
- **交付语义**：描述符声明值必须等于端点实例的 `getSourceConsistency()`/`getSinkConsistency()` 返回值——注册发现测试逐工厂构造真实端点断言相等；catalog 探测路径做运行期复核（不一致 = 探测失败，非静默）。
- **2PC 并行度门禁**：描述符 `PLANNING_GATE_PARALLELISM_1` ⟺ 端点 instanceof `TwoPhaseCommitSinkFunction`（与 `StreamGraphGenerator` 规划期门禁同键）——测试断言双向蕴含，描述符不发明第二套门禁逻辑。

**一致性核对口径**（Phase 2 注册断言与 Phase 3 文档同步共用）：
- **结构化相等列**（描述符 ↔ 文档矩阵必须相等）：方向、交付语义（枚举）、并行度（含 2PC 门禁映射）。
- **文档专属列**（描述符只有摘要、细节留文档）：恢复语义 prose（完整 state key/路径/钉定测试——描述符持枚举摘要）、交付语义「依据」细节、测试锚点。

**文档同步机制裁定**：**文档标注代码锚点 + 测试钉定复核清单**——`nop-stream-connectors.md` 能力矩阵每行标注注册类型名 + 工厂 bean/类锚点；注册发现测试断言全部注册类型与能力值（活代码 = 单一事实源），文档与测试互为核对。拒绝：生成式同步（人工中文叙述矩阵，生成管线成本 > 收益，长期生成化列 Non-Blocking Follow-up）；纯清单无锚点（漂移不可检）。

### 8.5 D5 XDSL 消费路径裁定

**裁定：本期不引入 XDSL 类型化连接器声明**。XDSL source/sink 维持 bean 引用 + 内联 xpl 两形态；**FL-1 全部拒绝面维持不变**（source：`params`/`outputType`/`maxParallelism`/非默认 `consistencyCapability`；sink 同族）——注册中心不改变既有声明语义与错误信息。

**理由**：① 8 端点配置面异构（`IMessageService`/`DebeziumConfig`/`IBatchLoaderProvider`/`IJdbcTemplate`/recordMapper 代码物），扁平 XDSL 参数包只对 file 家族干净，强行统一 = 弱类型过渡面；② item 20 conf-validate 将以本注册中心 + 能力描述面（参数规格即字段级校验规格的种子）设计校验命令，类型化 XDSL 声明应与字段规格**一次设计**（避免 params 消费语义做两遍）；③ `maxParallelism` 运行时消费属 item 29 边界，类型化声明引入会立即暴露该未接线面。

**预绑定运行时消费者（Anti-Hollow）**：`StreamConnectorCatalog` 维护/校验工具入口——注册清单枚举（listConnectors / 渲染目录输出）+ 能力探测（probe：类型名 → 注册中心解析 → 工厂构造端点 → 描述符一致性运行期校验 → AutoCloseable 关闭 → 探测输出）。**显式不做字段级 conf 校验**（item 20 边界）。

**落点模块**：注册中心契约/聚合/描述符/catalog 全部落 `nop-stream-core`（连接器模块仅依赖 core 实现工厂 + 贡献 beans.xml）；core 无需新增 nop-ioc 依赖——容器装配由调用方以既有 `BeanContainerBuilder` 显式装配模式完成，core 提供 `connector-*.beans.xml` 的 VFS 发现辅助。**CLI 子命令形态**（可执行分发入口/命令归属模块）= item 20 conf-validate 命令面一并裁定，避免两处 CLI。

**类型化声明引入时预案（记录不实施）**：`bean` 与类型名同时声明 = fail-fast（歧义）；declared `consistencyCapability` ≠ 描述符声明 = fail-fast；`maxParallelism` 维持 fail-fast 直至 item 29；`outputType`/`inputType` 类型推断属后续。

**拒绝的替代方案**：本期引入类型化声明（理由①②③）；注册中心空置无消费者（plan Anti-Hollow 禁令）；注册中心接入 `GlobalBeanFunctionResolver` 使 bean 名与类型名隐式互通（两命名空间语义不同——bean 名 = 实例名、类型名 = 工厂名，隐式互通制造名冲突与意外构造）。

### 8.6 D6 未知类型名解析语义

fail-fast typed error：错误参数含类型名、方向、该方向已注册清单；方向错配（sink 类型名用于 source 解析）为独立错误码（含期望/实际方向），不回落 type-not-found 误导，**禁止静默回落 bean 路径**。

### 8.7 D7 OLAP/数仓端连接器最小集三态裁定（P-REQ-28 验收必答）

逐候选三态（证据基线：roadmap Phase S 场景 S1/S2 均无 OLAP 端需求；fraud-example/quickstart 无 OLAP 用例；SeaTunnel 74 模块组织参照 = 先建注册/目录/能力机制、连接器本体按需分期；tis ~45 端 + Jenkins 市场机制已被综合报告 §2.4 显式不采纳）：

| 候选 | 三态 | 依据与边际成本 | revisit 触发点 |
|---|---|---|---|
| ClickHouse | **defer** | JDBC 驱动存在，边际成本低（`JdbcTwoPhaseCommitSink` 方言适配：台账 DDL/upsert 语义差异）；但零需求证据 + exactly-once 声明需 per-DB 验证矩阵——无场景需求时交付未经验证的 exactly-once 声明违反产品化纪律 | 首个 ClickHouse 目标需求进入 roadmap Phase S 场景 |
| Doris | **defer** | 同 ClickHouse（MySQL 协议 JDBC 可达） | 同上 |
| StarRocks | **defer** | 同 ClickHouse | 同上 |
| Hive | **defer** | HiveServer2 JDBC 可达，但流式 exactly-once 写依赖 commit 语义模拟（partition commit/锁），成本高于 OLAP 同类；批写场景更适合 nop-batch 路径 | 同上（且优先评估 nop-batch 载体） |
| Paimon | **exclude** | 流式湖表格式集成需新依赖面（Paimon SDK）+ Flink 生态深度耦合，与「不引入新执行契约/新框架」边界冲突；若流湖一体需求成立应独立 mission 立项（stop-edit-restart） | —（需求成立时独立立项） |
| Iceberg（裁定中识别候选） | **exclude** | 同 Paimon（表格式同类，一并裁定避免遗留） | — |

**最小集结论**：本期 go-minimal 集为**空**——分期裁定 = defer×4（需求门控）+ exclude×2（依据在案），符合 P-REQ-28「最小集或分期」验收口径。交付预案（defer 项触发后）：connector-jdbc 方言子类 + 能力矩阵行 + 注册工厂，不新建模块。roadmap 不追加无需求 Follow-up（defer 项为需求门控；归属见 plan Deferred But Adjudicated）。

### 8.8 D8 tis OQ-2 裁定：Delta 定制作为「连接器市场」替代机制

**裁定：部分采纳（适用边界裁定）**。

- **适合 Delta 覆盖的配置面**：既有连接器在具体管道中的差异化——拓扑级增删改/参数覆盖/环境差异（S2 delta 先例）；连接器工厂 beans.xml 本身是 XDSL、可被 Delta 节点级定制（复用平台 beans.xml Delta 能力）。这是「市场替代」的真实含义：**定制既有组件装配**，而非安装新插件。
- **必须走工厂参数/代码的面**：新端点类型、新交付语义、新执行契约——新连接器 = 新代码 + 新依赖 jar，Delta 无法承载代码与 classpath；本体扩展必须落代码 + SPI 工厂注册（本章机制）。

**结论**：跳过 Jenkins 式插件运行时**成立**（NopIoC + beans.xml Delta + SPI 注册构成扩展机制闭合）；但「连接器市场」产品形态（发现/安装/版本治理）不成立亦不采纳。tis 报告（`2026-08-14d`）OQ-2 条目写回收敛，报告 Status 保持 open（OQ-1/OQ-3 未决且 nop-batch/job/metadata/ai 侧结论未吸收，如实标注）。

**拒绝的替代方案**：全面采纳「插件 = Delta 包 + IoC bean 即完整市场替代」（忽略代码/依赖分发不可 Delta 承载）；完全不采纳（忽略配置面 Delta 已有活用例与 beans.xml 可 Delta 的事实）。

### 8.9 决策汇总表

| # | 裁定 | 一句话 |
|---|---|---|
| D1 | 三工厂形态 + 8 端点资格集 | source/split-source/sink 分立，创建物=既有端点，Reader/Builder 显式不注册 |
| D2 | 方向作用域类型名 | (direction, typeName) 唯一，kebab-case，别名机制预留本期为空 |
| D3 | NopIoC beans.xml 载体 + 按类型聚合 | 模块 `connector-*.beans.xml` 不进全局容器；工厂无状态、配置对象传参 |
| D4 | 能力描述符 + 单一事实源 | 交付语义=端点实例声明（测试钉定）；2PC 门禁=instanceof 同键 |
| D5 | 本期不引入 XDSL 类型化声明 | 消费者 = StreamConnectorCatalog（core）；CLI 归 item 20；FL-1 维持 |
| D6 | 未知类型名 fail-fast | typed error 含清单，方向错配独立错误码，禁回落 bean 路径 |
| D7 | OLAP 最小集 = 空（defer×4/exclude×2） | 需求门控 defer，湖格式 exclude，预案在案 |
| D8 | tis OQ-2 部分采纳 | 配置面 Delta 适用，本体分发不适用，市场形态不采纳 |
