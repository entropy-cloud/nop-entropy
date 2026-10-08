# Kafka Streams streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: 最值得借鉴两点——(1) **`EmitStrategy{ON_WINDOW_UPDATE, ON_WINDOW_CLOSE}` + `Suppressed.untilWindowCloses/untilTimeLimit` 把"什么时候发"做成一等且只有两档的显式契约**，正是 nop-stream-sql D11 trigger 应该学的降维方式（先做这两档，不要引入 8 档 trigger 体系）；(2) **`Named` / `Materialized.as` / `ensure.explicit.internal.resource.naming` 的"内部资源命名即稳定 API"治理**——nop 的 `<sql>` 展开产物若要跨版本升级而不重置状态，算子/store/topic 名必须先变成显式契约。

## Context

Kafka Streams 是 Apache Kafka 内置的流处理库（不是 SQL 引擎），提供 KStream/KTable DSL + Processor API + Interactive Queries。ksqlDB 的 SQL 层就是把它整层包起来的，因此它是本次调研的"执行底座样本"：nop-stream-sql 编译出的流管线若要对齐业界成熟语义，Kafka Streams 的窗口/抑制/可查询状态是最近的参照。本文只读本地源码 `/Users/abc/sources/kafka`（`gradle.properties:17` → `4.5.0-SNAPSHOT`，HEAD `7434a60`），**限定 `streams/` 模块**，不涉及同仓库的 `core/`、`connect/`、`server/`、`metadata/` 等模块。

## 项目概览

- **定位**：嵌入式流处理库（library），进程内构建拓扑并运行；无服务端、无 SQL、无自身部署编排。
- **语言**：Java（`streams/`）+ Scala DSL（`streams/streams-scala/`）。
- **许可证**：Apache License 2.0（`LICENSE`）。
- **演进状态**：活跃，4.5.0-SNAPSHOT。近期方向：IQv2（KIP-796）、显式内部资源命名（KIP-1111）、versioned state store、header-aware store 查询、`max.task.idle.ms`。
- **SQL 能力入口**：**没有 SQL 模块**。语义入口在：
  - DSL 抽象：`streams/src/main/java/org/apache/kafka/streams/kstream/`（`KStream`/`KTable`/`TimeWindows`/`Suppressed`/`EmitStrategy`/`Named`/`Materialized`）
  - 图构建：`streams/src/main/java/org/apache/kafka/streams/StreamsBuilder.java`、`kstream/internals/InternalStreamsBuilder.java`、`kstream/internals/graph/`
  - 运行时与查询：`streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java`、`query/`、`state/`
- 同仓库 `docs/streams/` 是权威语义文档（`core-concepts.md`、`developer-guide/interactive-queries.md`、`upgrade-guide.md`、`developer-guide/dsl-topology-naming.md`）。

## 十维度分析

### 1. SQL 前端：parser/planner 自研还是复用

**无 SQL 前端。** Kafka Streams 的"前端"就是 Java DSL 链式 API（`StreamsBuilder.stream()/table()` 起手，逐算子返回 `KStream`/`KTable`）与更低层的 Processor API（`Topology.addSource` `:144` / `addProcessor` `:654` / `addStateStore` `:718`）。

- 历史名 `KStreamBuilder` 已弃用（`org.apache.kafka.streams.kstream.KStreamBuilder` 只残留在 `streams/upgrade-system-tests-0110/`），现名 `StreamsBuilder`；prompt 中的 "KStreamBuilder→topology" 在本版本应读作 **`StreamsBuilder` → `InternalStreamsBuilder` 图 → `Topology`**。
- 没有代价优化器，但有**图重写**：`InternalStreamsBuilder.java:366 rewriteRepartitionNodes()`、`:360` / `:439 rewriteSingleStoreSelfJoin()` —— 在 `build()` 前归并 repartition 节点、合并单 store self-join。
- 因此：SQL→执行的翻译责任全部落在**上游引擎**（ksqlDB、Flink 等）；Kafka Streams 只负责算子图的装配与运行。

**对 nop 的意义**：nop 的 `StreamSqlCompiler` 扮演的是"ksqlDB 的 planner"角色，Kafka Streams 扮演"执行底座"。两者在 nop 内部是同进程的两层，不需要像 ksqlDB 那样把计划序列化过网络——但**算子命名规范仍需抄**（见 §9.2）。

### 2. Table & Changelog 模型

- **双模型一等公民**：`KStream<K,V>` = 分区 record 流（append，允许 null value = tombstone）；`KTable<K,V>` = 按 key 的物化 changelog；`GlobalKTable` = 每实例全量副本。
- **对偶性**是文档核心概念（`docs/streams/core-concepts.md:110` "Table as Stream"、`:128` 聚合输出恒为 KTable）。
- **变更记录类型**：`kstream/internals/Change.java`（old/new 值对），DSL 算子以 `Change` 传递 —— 这是"retract 能力"的载体：KTable 上的聚合/过滤都会产出 old+new，下游可区分插入/撤回。
- **upsert 语义**：KTable 按 key 覆盖写；`KTable.toStream()` / `toTable()` 互转；`aggregate/reduce/count` 全是 changelog 更新。
- **changelog 可控**：`Materialized.withLoggingEnabled(config)` / `withLoggingDisabled()`（`Materialized.java:323/:333`）、`withCachingDisabled()`（`:352`）、`withRetention(...)`（`:371`）。
- **versioned store**（处理乱序的另一条路）：`Stores.persistentVersionedKeyValueStore(...)`（`state/Stores.java:175/:215`）、`versionedKeyValueStoreBuilder`（`:622`）；`docs/streams/core-concepts.md:167-169` 明确：stream-stream join 三种类型都能正确处理乱序，stream-table / table-table 若不用 versioned store 则**不处理乱序**，改用后语义从 offset-based 变 timestamp-based。
- **无 SQL 层的 `INSERT INTO`**：对应物是 `KStream.to(...)`（`KStream.java:543`，旧 `through()` 已移除）、`KStream.toTable()`（`:601`）；写外部系统用 `foreach`（`:406`）/ `flush`。

**与 nop 对照**：nop 是 last-value-wins、无 RowKind/retract（D1 裁定），比 Kafka Streams 弱一档。但 Kafka Streams 的经验是：**retract 是 KTable 的副产品，不是 SQL 层的语法**；nop 若未来要 retract，从"聚合算子输出 old/new"入手比在 SQL 上加 `EMIT CHANGES` 更根本。

### 3. 时间语义

- **无 watermark 对象**。核心概念是 **stream time**：由 `TimestampExtractor` 给每条 record 打时间戳，随 record 到达推进（`docs/streams/core-concepts.md:76`）。
- **grace period = late data 的唯一闸门**（`:134`）："record 的 stream time > window end + grace" 则丢弃该记录。这与 ksqlDB 的 `GRACE PERIOD`、nop 的 `allowedLateness` 是同一语义的三种写法。
- **grace 的 API 设计值得注意**：每种窗口都给成对方法，**把"要不要 grace"写进方法名而不是默认值**：
  - `TimeWindows.ofSizeWithNoGrace(size)`（`TimeWindows.java:110`） vs `ofSizeAndGrace(size, afterWindowEnd)`（`:132`）
  - `SessionWindows.ofInactivityGapWithNoGrace`（`SessionWindows.java:108`） vs `ofInactivityGapAndGrace`（`:130`）
  - `SlidingWindows.ofTimeDifferenceWithNoGrace`（`SlidingWindows.java:109`） vs `ofTimeDifferenceAndGrace`（`:123`）
  - `JoinWindows.ofTimeDifferenceWithNoGrace`（`JoinWindows.java:147`） vs `ofTimeDifferenceAndGrace`（`:125`）；含糊的 `of(size)` 仍在（`:162`）
  
  **对 nop 的启发**：`allowedLateness` 目前只是"放行的属性"，缺一个"明确无 lateness"的显式写法；若未来做 SQL 面窗口子句，建议学这个成对命名，让"无 grace"成为可见选择而非隐式默认。
- **处理时间 vs 事件时间**：由 `TimestampExtractor` 决定（`processor/` 下 `LogAndSkipOnInvalidTimestamp` / `FailOnInvalidTimestamp` / `ExtractRecordMetadataTimestamp`），事件时间为默认。**没有 SQL 级 TIME/SYSTEM 时间切换语法**。
- **idle**：`StreamsConfig.MAX_TASK_IDLE_MS_CONFIG = "max.task.idle.ms"`（`StreamsConfig.java:628`），控制 join/merge 在分区无数据时是否空转等待 —— nop 需对照的"输入空闲"配置。

### 4. 窗口与聚合

**窗口类型**：

| 类型 | 工厂 | 位置 |
|---|---|---|
| 翻滚 / 跳跃 | `TimeWindows.ofSize*(...).advanceBy(...)` | `TimeWindows.java:110/:132/:153` |
| 滑动 | `SlidingWindows.ofTimeDifference*` | `SlidingWindows.java:109/:123` |
| 会话 | `SessionWindows.ofInactivityGap*` | `SessionWindows.java:108/:130` |
| 不限时（无窗口聚合） | `UnlimitedWindows` | `kstream/UnlimitedWindows.java` |

入口：`KGroupedStream.windowedBy(...)` 三形态（`KGroupedStream.java:550/:557/:564`）。

- **named window：Kafka Streams 没有"named window"对象**，命名发生在**算子与 store 两级**：
  - 算子名：`Named.as(name)`（`Named.java:50`）、`withName`（`:56`）；窗口聚合有 `count(Named)`、`aggregate(..., Named, Materialized)` 等重载（`TimeWindowedKStream.java:125/:226/:492/:538/:600/:663`）。
  - store 名：`Materialized.as(String)`（`Materialized.java:204`）、`Materialized.as(DslStoreSuppliers)`（`:169`）。
  - 治理开关：`StreamsConfig.ENSURE_EXPLICIT_INTERNAL_RESOURCE_NAMING_CONFIG = "ensure.explicit.internal.resource.naming"`（`StreamsConfig.java:600`），置 true 时**任何自动生成的内部资源名都会拒绝启动**（`docs/streams/upgrade-guide.md:234`，KIP-1111）。
  - 关键注释（`docs/streams/upgrade-guide.md:562`）：**给 store 起名不会让它可被 IQ 查询**（可查询性由 store 类型决定），但**会**决定 changelog topic 名与升级兼容性。
  
  → ksqlDB 把"名字"放进 SQL 语法（`WINDOW <name> TUMBLING(...)`），Kafka Streams 把"名字"放进 DSL 参数；**两者都没把名字和 IQ 可查询性绑定**。nop 若引入窗口名，必须先定义它驱动什么（store 名？topic 名？IQ key？），否则就是死语法。
- **suppress / 发射时机**是两个正交机制：
  1. `EmitStrategy.StrategyType`：`ON_WINDOW_UPDATE`（每次窗口更新都发）与 `ON_WINDOW_CLOSE`（窗口关闭才发一次）（`EmitStrategy.java:39-40`；`ON_WINDOW_CLOSE` 定义见 `:82` "current event time > window end + grace period"）。
  2. `Suppressed<K>`：`untilWindowCloses(StrictBufferConfig)`（`Suppressed.java:166`）= 终态语义，缓冲至关窗；`untilTimeLimit(Duration, BufferConfig)`（`:180`）= 每条记录等一段时间再发（同 key 新记录覆盖但不重置计时器）；`withName`（`:199`）。buffer 分 `StrictBufferConfig.unbounded()/shutDownWhenFull()` 与 `EagerBufferConfig.emitEarlyWhenFull()`；`withLoggingDisabled()`（`:111`）可关掉抑制缓冲的 changelog（牺牲容错）。
  
  → 这正是 ksqlDB `EMIT CHANGES|FINAL` 的底层实现（ksqlDB `TableSuppressBuilder.java:115-117` 直接调 `suppress().untilWindowCloses(...)`）。
- **聚合函数集**：**没有内置函数集**。只有三种原语 `count()` / `reduce(Reducer)` / `aggregate(Initializer, Aggregator, [Merger])`（session 需 Merger）。TopN / DISTINCT / UDAF 都要用户自己写 `aggregate`。
- **无 HAVING / 无 ORDER BY / 无 TopN**：`KTable.filter(Predicate)` 可替代 HAVING，但没有排序算子。多流聚合 `CogroupedKStream` 用同样原语。

**对 nop 的对照**：nop 的"五聚合 + aggregators 注册表"比 Kafka Streams 的"三原语 + 用户 lambda"更声明式、更可静态校验——这是 nop 的优势；要补的是**发射时机开关**（见结论）与**更宽的聚合面**（`COUNT_DISTINCT`/`TOPK` 这类 ksqlDB 已封装的）。

### 5. Join

| 形态 | API | 说明 |
|---|---|---|
| stream-stream（区间） | `KStream.join/leftJoin/outerJoin(KStream, ValueJoiner, JoinWindows)`（`KStream.java:780` 起多组重载） | 必须 `JoinWindows`；inner/left/outer；乱序由 grace 处理 |
| stream-table（共分区） | `KStream.join(KTable, ValueJoiner)`（`KStream.java:1107`）、`leftJoin`（`:1236`） | 需同 key；默认不处理乱序（除非 versioned store） |
| stream-globalTable（**lookup join**） | `KStream.join(GlobalKTable, keyMapper, ValueJoiner)`（`KStream.java:1344`）、`leftJoin`（`:1497`） | **非共分区**，本地全量表点查 —— 最接近 nop "静态维表 lookup join" 的形态 |
| table-table | `KTable.join/leftJoin/...`（inner/left/right/outer） | 输出为 changelog |
| table-table **外键 join** | `KTable.join(KTable, Function<V,KO>, ValueJoiner)`（`KTable.java:2133` 等多组重载） | 需额外 subscription/response 状态机（`kstream/internals/foreignkeyjoin/` 12 个文件） |

- **无非等值 join**：所有 join 都要求 key 可对齐；区间条件只能走 `JoinWindows`（时间维度），不能是 `a.x < b.y` 这类值比较。**无子查询**。
- join 命名与 store：`StreamJoined.withName`（`StreamJoined.java:208`）、`TableJoined.withName`（`TableJoined.java:134`）；join store retention = window size + grace（`internals/KStreamImplJoin.java:206/:246`）。
- 空转控制：`max.task.idle.ms`（`StreamsConfig.java:628`）。

**对 nop 的对照**：nop 的"双流等值 join（window join 补齐于 watermark）"≈ stream-stream join；nop 的"静态维表 lookup join"≈ `KStream.join(GlobalKTable, keyMapper, ...)`；nop 没有 table-table FK join（那是 changelog 模型才需要的）。

### 6. CEP / 模式匹配

**没有。** `kstream/` 与 `processor/` 下无 pattern/NFA 相关类型。Kafka Streams 的定位是"算子图 + 状态"，模式匹配留给上层。nop 的 CEP 模块是超出项。

### 7. 状态与容错

- **状态后端**：默认 RocksDB，另有 InMemory —— `BuiltInDslStoreSuppliers.java:29`（"support RocksDb and InMemory stores out of the box"）、`:34 ROCKS_DB`、`:35 IN_MEMORY`；窗口/会话的 RocksDB 实现在 `state/internals/`（`RocksDBSegmentedBytesStore`、`RocksDbIndexedTimeOrderedWindowBytesStoreSupplier`、`RocksDBTimeOrderedKeyValueBuffer` 等）。
- **changelog**：每个持久 store 自动带 changelog topic；repartition topic 由图自动插入。
- **commit / checkpoint**：`StreamsConfig.COMMIT_INTERVAL_MS_CONFIG = "commit.interval.ms"`（`:484`）、`state.dir`（`:805`）、`statestore.cache.max.bytes`（`:810`）、standby 副本 `num.standby.replicas`（`:675`）。
- **exactly-once**：`PROCESSING_GUARANTEE_CONFIG = "processing.guarantee"`（`:703`），取值 `at_least_once`（默认）/ `exactly_once_v2`（`:421`），EOS v2 需 broker ≥ 2.5（`docs/streams/upgrade-guide.md:64`）。**输出语义由 sink 端事务保证**——EOS 覆盖面本质是"写 Kafka"，写外部系统需 `TransactionAware` sink。
- **查询可用性前提**：store 名。`StoreQueryParameters.fromNameAndType` / `.withPartition` / `.enableStaleStores`（`StoreQueryParameters.java:42/:54/:63`）。

### 8. 声明形态与可嵌入性

- **纯库形态**：`StreamsBuilder`（`:123 stream` / `:303 table` / `:391 globalTable` / `:591 build()`）→ `Topology` → `new KafkaStreams(topology, config)`。可嵌入任何 JVM 应用。
- **无 SQL 文件、无交互式、无服务端**；声明载体就是 Java/Scala 代码。
- 拓扑可导出为**拓扑描述文本**（`Topology.describe()` / `TopologyDescription`）用于变更审查 —— nop 的 `.stream.xml` + fingerprint 已在做同方向的事。
- 多拓扑：`KafkaStreamsNamedTopologyWrapper`（`processor/internals/namedtopology/`）支持进程内增删 named topology（ksqlDB 的 shared runtime 也用它，见 ksqlDB `KsStateStore.java:78-85`）。

### 9. 架构要点（SQL → 执行的链路参照）

Kafka Streams 自身无 SQL，这里写"**上层 SQL 引擎 → Kafka Streams**"的链路与值得记的设计：

```
[上层 SQL 文本]
   → SQL parser / analyzer / planner（ksqlDB 的事）
   → ExecutionStep 计划（可序列化）
   → KSPlanBuilder（PlanBuilder 实现）
   → StreamsBuilder / KStream·KTable DSL 调用
   → InternalStreamsBuilder：GraphNode 图（kstream/internals/graph/）
        · StreamSourceNode / StreamStreamJoinNode / WindowedStreamProcessorNode
        · TableSuppressNode / StateStoreNode / OptimizableRepartitionNode / GracePeriodGraphNode
   → 图重写：rewriteRepartitionNodes()、rewriteSingleStoreSelfJoin()
   → Topology（InternalTopologyBuilder）→ subtopology / task 划分
   → KafkaStreams 运行时（commit、EOS、IQ）
```

值得记的 3 点：

1. **图是"先自由构造、后统一重写"**（`InternalStreamsBuilder.java:360/:366/:439`）。算子调用时不立即决定 repartition，而是 build 前扫全图归并——这让 DSL 层不必关心分区细节。nop 的 `StreamSqlCompiler` 直接产出"已确定的流模型"（source→window/keyBy→聚合→sink），更简单也更早固化；**可借鉴点是把"能否省一次 repartition"这类优化留在图阶段，而不是在 SQL 翻译期拍板**。
2. **命名即兼容性契约**（`Named` / `Materialized.as` / `StreamJoined.withName` / `Suppressed.withName` / KIP-1111，`StreamsConfig.java:600`）。自动命名会让升级触发 app reset（`docs/streams/upgrade-guide.md:234`）。**nop 的 `<sql>` 展开产物若要支持"改 SQL 不重置状态"，算子/状态名必须由 SQL 声明或稳定派生，不能是序号**。ksqlDB 的 named window 语法正是想解决这件事，但实现没接上（见 ksqlDB 分析）。
3. **IQ 两代 API 分层**：IQv1 = `KafkaStreams.store(StoreQueryParameters)`（`KafkaStreams.java:1867`）拿只读 facade；IQv2 = `KafkaStreams.query(StateQueryRequest)`（`:2162`），统一入口 + 分区级 `QueryResult` + `Position`/`PositionBound` 一致性约束（`docs/streams/developer-guide/interactive-queries.md:609` 起，`:611` 说明 KIP-796 动机）。内置查询类型在 `query/`：`KeyQuery` / `TimestampedKeyQuery` / `VersionedKeyQuery` / `RangeQuery` / `MultiVersionedKeyQuery` / `WindowKeyQuery` / `WindowRangeQuery` / `Timestamped*WithHeadersQuery`。**nop 若要做"查当前值"（pull 语义），IQv2 的 `QueryResult` + `PositionBound` 是最值得抄的返回契约**：把"数据是否够新"变成显式失败原因，而不是静默读旧值。

### 10. 与 nop-stream-sql 的对照与可借鉴点

| Kafka Streams 能力 | nop-stream-sql 现状 | 可借鉴点 |
|---|---|---|
| `EmitStrategy` 两档（UPDATE / CLOSE） | D11 trigger 整体 fail-fast；窗口聚合按 fire，持续聚合逐条 emit | **直接抄两档模型**：`emit="update\|close"`，`close` = 窗口关闭后只发终值；比引入完整 trigger 体系小一个量级 |
| `Suppressed.untilTimeLimit(duration)` | 无 | "去抖/合并"的最小实现，适合告警类输出，可作第二档 |
| `Suppressed.untilWindowCloses` + Strict/Eager buffer | 无 | 做 `EMIT FINAL` 必须同时给 buffer 溢出策略（`shutDownWhenFull` vs `emitEarlyWhenFull`）——nop 至少要有"状态无界怎么办"的显式选择 |
| `*WithNoGrace` / `*AndGrace` 成对 API | `allowedLateness` 属性（D9 放行），无"明确无 lateness"写法 | 成对命名让语义可见；SQL 面可用 `GRACE 0` / `NO GRACE` |
| `Named.as` / `Materialized.as` / KIP-1111 | `<sql>` 展开的算子名由编译器生成 | 把算子/store 名纳入可审查产物（或由 `sinkBean`+查询稳定派生），升级才不需重置状态 |
| `KStream.join(GlobalKTable, keyMapper, ...)` | 静态维表 lookup join（`ITableLookup`） | 形态一致；keyMapper 允许"用字段算出 lookup key"，nop 若只支持字段直连可评估是否要表达式 |
| stream-stream `JoinWindows` + grace | window join 补齐于 watermark | 语义一致；join store retention = size + grace（`KStreamImplJoin.java:206/:246`）可照此推导 nop 状态保留期 |
| versioned store 处理乱序 | 无（D1 last-value-wins） | 长期选项：对"维表 join 结果按事件时间取当时值"的场景，versioned store 比 retract 便宜 |
| IQv2 `QueryResult` + `PositionBound` | 无查询接口（只有 push/窗口输出） | 若做 pull/点查，返回契约照抄：分区级结果 + 显式"未达新鲜度"失败原因 |
| `max.task.idle.ms` | 未见对应配置 | 多输入 join 的空闲分区策略需显式默认值，否则 join 延迟不可控 |
| 无 SQL、无 CEP、无 TopN/HAVING/ORDER BY | 有 SQL 层、有 OVER 分析窗口（WI12）；CEP 有 `cep-design.md` 但不在本 roadmap 纳入面 | nop 领先项，无需对齐 |

## 对照 nop-stream-sql 已交付子集（必填小节）

对照基准：`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4b 纳入面总表、§4a 九项不支持清单，以及 `ai-dev/design/nop-stream/window-failfast-decisions.md` D9–D12。

1. **入口**：nop 是顶层 `<sql sinkBean>` 元素（sql 文本 + 内嵌 schema + sinkBean），经 `ISqlStreamCompiler` SPI 在 `builder.build()` 前展开为完整流模型 XML 回读替换（`stream.xdef`，`TestSqlModelDeclarationE2E`）。Kafka Streams 无对应物——声明面就是 Java DSL。**无语义重叠**；可比的只有"编译产物形态"：nop 产物是流模型 XML，KS 产物是 `Topology.describe()` 文本。**可借鉴**：两者都应可序列化审查，nop 已具备（fingerprint），不必改。
2. **SELECT 投影 / WHERE**：nop 走 `StreamSqlExprCompiler` 标量子集（列引用/字面量/算术/比较/AND-OR-NOT/IS NULL/BETWEEN/IN 值列表），子集外表达式 `nop.err.stream.invalid-arg` fail-fast（`TestStreamSqlExprCompiler.outOfSubsetFailsFastWithStreamSideCode`）。KS 等价物是 `mapValues`/`filter` 的 lambda，**无子集、无 fail-fast 面**（类型系统在 Java 侧，编译期已拦）。**无借鉴点**——nop 的构建期子集校验是 SQL 文本形态的必需品。
3. **五聚合（sum/count/avg/min/max）+ GROUP BY**：KS 只有 `count()` / `reduce()` / `aggregate()` 三原语（`KGroupedStream.java:75/:239` 起，窗口态在 `TimeWindowedKStream.java:94/:125/:492`）。**差异**：nop 的五 id 注册表（`StreamSqlAggregations.builtinIds`）+ aggregatorRef 声明式引用比 KS 的用户 lambda 更可静态校验——**nop 领先，不对齐**。DISTINCT 聚合 nop 侧 fail-fast（`TestStreamSqlAggregations.distinctFailsFast`），KS 侧压根没有这个概念。
4. **`TUMBLE(t, INTERVAL)` 伪表函数**（WI17 grammar 已落地）：KS 对应 `windowedBy(TimeWindows.ofSizeAndGrace(...))`（`KGroupedStream.java:550`）。**差异**：nop 把窗口写进 FROM 的表源分支（`sqlTumbleTableSource`），KS 把窗口挂在 `groupBy` 之后的 `windowedBy`。**可借鉴**：成对的 `*WithNoGrace` / `*AndGrace` API（§3）——nop 的 `allowedLateness`（D9 已放行）缺一个"明确无迟到容忍"的显式写法；以及 `EmitStrategy` 两档（§4）。
5. **双流等值 join**：KS stream-stream 支持 inner/left/outer（`KStream.java:780/:881/:980`），**没有 `RIGHT`**（左右交换即可表达）。nop 的 runtime 支持 LEFT/RIGHT/FULL 补齐（`join-operator.md:24`），但**窗口 join 构造期限 INNER/LEFT、FULL 窗口 join 维持未决**（`join-operator.md:32`、`TestParameterizedJoinModel.fullWindowJoinFailsFast`）——即 nop 的窗口 join 表达面比 KS 窄半档。**可借鉴**：join store retention = window size + grace（`KStreamImplJoin.java:206/:246`）——nop 的窗口 join 状态清理/保留期可照同一公式推导，不必另立规则。
6. **UNION ALL（bag 语义）**：KS 无 UNION，只有 `merge()`（且要求同 key 语义可对齐）。nop 走 `DataStream.union`（WI6，`multi-input-model.md`），重复声明边=重复合流。**nop 领先**。
7. **静态维表 lookup join**：对齐 `KStream.join(GlobalKTable, keyMapper, joiner)`（`KStream.java:1344`）——本地全量、非共分区、按 key 点查，形态与 nop 的 `ITableLookup` process 桥接一致（WI14）。**可借鉴**：KS 的 `keyMapper` 是 `Function<key, value>`（用 value 推导 lookup key），nop 若当前只支持字段直连 key，这是低成本增强。
8. **无窗口持续 GROUP BY**（D1 last-value-wins 终值语义）：KS 对应无窗口 `aggregate`（`UnlimitedWindows`）。**差异是本质的**：KS 输出是 KTable/`Change`（可 retract），nop 是 `<reduce>` 逐条 emit 当前归约值（`StreamReduceOperator.java:87-106`），明确非 append-only 非 retract。**可借鉴**：不要在 SQL 层加 retract 语法；若未来要改善，先让聚合算子可选输出 old/new（`Change` 形态），SQL 层再谈——这是 KS 的分层顺序。
9. **OVER 分析窗口（ROW_NUMBER + FRAME_SLIDING_AGG，D6 事件时间）**：KS **无**。nop 的 `OverWindowOperator`（WI12）+ 每 key 有序缓冲是超出 KS 的能力。**nop 领先**。（注：WI4 的 10 个标准窗口函数 rank/dense_rank/row_number/lead/lag/first_value/last_value/nth_value/percent_rank/cume_dist 是 EQL→RDBMS 方言登记面，不是流运行时，勿混。）
10. **D6 事件时间**：一致。KS 由 `TimestampExtractor` + stream time 推进；nop 由 bigint epoch-millis 时间列 + watermark。两者**都没有 processing-time 的 SQL 级切换**。
11. **D9 allowedLateness = 放行**：一致（KS = window grace period，`docs/streams/core-concepts.md:134`）。**可借鉴**：成对命名让"无 grace"成为可见选择，而不是隐式默认。
12. **D10 accumulationMode / D11 triggerId = 保持 fail-fast**：**这是本调研最重要的落点**。KS 只暴露 `EmitStrategy{ON_WINDOW_UPDATE, ON_WINDOW_CLOSE}` 两档（`EmitStrategy.java:39-40`），ksqlDB 把它们翻译成 `EMIT CHANGES|FINAL`（`ksql-analysis.md:69/:168`）。**结论：nop 若解 D11，范围就锁死在这两档**，`emit="update|close"` 一个属性即可，不要引入 Flink 式多档 trigger 体系。这与既有裁决方向一致：D10 维持 fail-fast 的理由之一正是"D1=(a) 关闭 retract 路线后放行 ACCUMULATING 的前提消失"（`sql-subset-and-semantics.md` §1 输入约束）。
13. **架构（EQL AST → StreamSqlCompiler → 流模型 XML；三注册表；无代价优化器）**：KS 的对应层是 DSL 调用 → `InternalStreamsBuilder` 图 → 图重写（`:360/:366/:439`）→ `Topology`。**可借鉴**：(a) **内部资源显式命名治理**——`Named.as` / `Materialized.as` / KIP-1111 `ensure.explicit.internal.resource.naming`（`StreamsConfig.java:600`，置 true 后自动命名拒绝启动）；nop 的 `<sql>` 展开算子名若含序号，改 SQL 就会漂移，跨版本无法保持状态连续性。(b) 把可做的静态归并（如连续 repartition 合并）留到图阶段，而不是在 SQL 翻译期拍板。
14. **不支持清单（§4a 九项）**：KS 在类型系统层面就不存在全局 ORDER BY/LIMIT、非等值 join、retract、DISTINCT 聚合、CEP——无 fail-fast 需求。nop 的九项各有显式失败路径（错误码 + 钉码测试），**这是 SQL 文本形态独有且必要的一环，保持**。其中第 3 项（retract/CDC）与第 1 项（ORDER BY/LIMIT）在 KS 同样缺位，互相印证首版排除是合理取舍。

## Open Questions

- [ ] D11 若解，`emit="update|close"` 挂在 `<sql>` 元素属性还是 `<aggregate>`/window 声明节点？（KS 的 `EmitStrategy` 是**算子级**参数，`Suppressed` 才是**输出端**参数——nop 需先决定它约束谁）
- [ ] `<sql>` 展开产物的算子/store 命名派生规则：由 `sinkBean` + 查询稳定派生，还是要求 SQL 显式声明？（KIP-1111 的经验是"没有显式名就拒绝启动"，nop 若做不到可审查性，至少要能确定性重算）

## 引用出处

外部源码根：`/Users/abc/sources/kafka`（`gradle.properties:17` → `4.5.0-SNAPSHOT`）。以下路径已逐条验证存在，行号来自实际 grep/sed 定位。

**streams 主干**

- `gradle.properties`（:17 `version=4.5.0-SNAPSHOT`）
- `LICENSE`（Apache License 2.0）
- `streams/src/main/java/org/apache/kafka/streams/StreamsBuilder.java`（:123 `stream(String)`、:303 `table(String)`、:391 `globalTable`、:591 `build()`）
- `streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java`（:1867 `store(StoreQueryParameters)`、:2162 `query(StateQueryRequest)`）
- `streams/src/main/java/org/apache/kafka/streams/StoreQueryParameters.java`（:42 `fromNameAndType`、:54 `withPartition`、:63 `enableStaleStores`）
- `streams/src/main/java/org/apache/kafka/streams/Topology.java`（:144 `addSource`）
- `streams/src/main/java/org/apache/kafka/streams/StreamsConfig.java`（:421 `EXACTLY_ONCE_V2`、:484 `commit.interval.ms`、:600 `ENSURE_EXPLICIT_INTERNAL_RESOURCE_NAMING_CONFIG`、:628 `MAX_TASK_IDLE_MS_CONFIG`、:675 `num.standby.replicas`、:703 `PROCESSING_GUARANTEE_CONFIG`、:805 `state.dir`、:810 `statestore.cache.max.bytes`）

**kstream DSL**

- `.../kstream/KStream.java`（:780 stream-stream `join`、:881 `leftJoin`、:980 `outerJoin`、:1107 `join(KTable)`、:1236 `leftJoin(KTable)`、:1344 `join(GlobalKTable)`、:1497 `leftJoin(GlobalKTable)`）
- `.../kstream/KTable.java`（:2133 foreignKeyExtractor 重载、:2151 BiFunction 变体）
- `.../kstream/KGroupedStream.java`（:75 `count()`、:239 `reduce()`、:550 `windowedBy(Windows)`、:557 `windowedBy(SlidingWindows)`、:564 `windowedBy(SessionWindows)`）
- `.../kstream/TimeWindowedKStream.java`（:94 `count()`、:125 `count(Named)`、:492 `aggregate(...)`）
- `.../kstream/TimeWindows.java`（:110 `ofSizeWithNoGrace`、:132 `ofSizeAndGrace`、:153 `advanceBy`）
- `.../kstream/SessionWindows.java`（:108 `ofInactivityGapWithNoGrace`、:130 `ofInactivityGapAndGrace`）
- `.../kstream/SlidingWindows.java`（:109 `ofTimeDifferenceWithNoGrace`、:123 `ofTimeDifferenceAndGrace`）
- `.../kstream/JoinWindows.java`（:125 `ofTimeDifferenceAndGrace`、:147 `ofTimeDifferenceWithNoGrace`、:162 `of(timeDifference)` 含糊旧签名）
- `.../kstream/EmitStrategy.java`（:39 `ON_WINDOW_UPDATE`、:40 `ON_WINDOW_CLOSE`、:80-82 `ON_WINDOW_CLOSE` 定义 = event time > window end + grace）
- `.../kstream/Suppressed.java`（:132 `BufferConfig.withLoggingDisabled`、:166 `untilWindowCloses`、:180 `untilTimeLimit`、:199 `withName`）
- `.../kstream/Materialized.java`（:169 `as(DslStoreSuppliers)`、:204 `as(String)`、:323 `withLoggingEnabled`、:333 `withLoggingDisabled`、:352 `withCachingDisabled`、:371 `withRetention`）
- `.../kstream/Named.java`（:50 `as`、:56 `withName`）
- `.../kstream/StreamJoined.java`（:208 `withName`）、`.../kstream/TableJoined.java`（:134 `withName`）
- `.../kstream/internals/Change.java`
- `.../kstream/internals/TimeWindowedKStreamImpl.java`
- `.../kstream/internals/KStreamImplJoin.java`（:206 / :246 `retention = size + gracePeriod`）
- `.../kstream/internals/foreignkeyjoin/`（12 个文件）
- `.../kstream/internals/InternalStreamsBuilder.java`（:360 `rewriteSingleStoreSelfJoin` 调用、:363→:366 `rewriteRepartitionNodes`、:439 `rewriteSingleStoreSelfJoin` 定义）
- `.../kstream/internals/graph/`（`GraphNode` / `StreamStreamJoinNode` / `TableSuppressNode` / `WindowedStreamProcessorNode` / `GracePeriodGraphNode` / `OptimizableRepartitionNode` / `StreamSourceNode` / `StateStoreNode` 等，目录实列 29 个文件）

**state / query / 运行时**

- `.../state/BuiltInDslStoreSuppliers.java`（:29 注释、:34 `ROCKS_DB`、:35 `IN_MEMORY`）
- `.../state/Stores.java`（:97/:116/:142 versioned store 工厂引用、:175/:215 `persistentVersionedKeyValueStore`、:622 `versionedKeyValueStoreBuilder`）
- `.../state/internals/`（`RocksDBSegmentedBytesStore`、`RocksDbIndexedTimeOrderedWindowBytesStoreSupplier`、`RocksDBTimeOrderedKeyValueBuffer`）
- `.../query/`（`Query` / `QueryResult` / `Position` / `PositionBound` / `StateQueryRequest` / `StateQueryResult` / `KeyQuery` / `VersionedKeyQuery` / `WindowKeyQuery` / `WindowRangeQuery` 等，目录实列 23 个文件）
- `.../processor/internals/namedtopology/`（`KafkaStreamsNamedTopologyWrapper` / `NamedTopologyBuilder` 等 6 个文件）
- `.../processor/ExtractRecordMetadataTimestamp.java`、`.../processor/LogAndSkipOnInvalidTimestamp.java:51`、`.../processor/FailOnInvalidTimestamp.java:50`、`.../processor/WallclockTimestampExtractor.java`（`TimestampExtractor` 实现集）

**同仓库文档**

- `docs/streams/core-concepts.md`（:110 "Table as Stream"、:134 grace period 定义、:165-169 乱序与 versioned store 语义）
- `docs/streams/upgrade-guide.md`（:234 KIP-1111 `ensure.explicit.internal.resource.naming` 行为、:562 "naming the stores **will not** make them queryable via Interactive Queries"）
- `docs/streams/developer-guide/interactive-queries.md`（:609 IQv1→IQv2 过渡、:611 KIP-796 与 `QueryResult`/`Position` 契约）

**nop 侧对照锚点**

- `ai-dev/design/nop-stream/sql-subset-and-semantics.md`（§1 D1、§3 D4、§5 D6、§4a 九项不支持清单、§4b 纳入面总表）
- `ai-dev/design/nop-stream/window-failfast-decisions.md`（D9/D10/D11/D12）
- `ai-dev/design/nop-stream/join-operator.md`（:15 八项校验、:24 补齐时机、:32 窗口 join 限 INNER/LEFT、:51 FULL 未决）
- `ai-dev/design/nop-stream/window-design.md`（:129 DISCARDING/D9 修订、:196 迟到判断、:205 cleanup time）
- `ai-dev/analysis/streaming-sql-survey/2026-10-07-ksql-analysis.md`（:69 `EMIT FINAL` 落到 `TableSuppressBuilder.java:115-117`、:168 "先只放这两档"）
