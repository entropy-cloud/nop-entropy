# Debezium streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: Debezium 最值得借鉴的两点——(1) 「snapshot → streaming 单 offset 交接 + schema history 先行恢复」的启动序列，以及 signal 驱动的分块增量快照（window open/close + key 去重窗口），是 nop-stream-connector-debezium 补齐「快照与流不重不漏」的现成范式；(2) 把 `op` 码 + `snapshot` 标记 + `ts_ms/ts_us/ts_ns` 做成单一不变的事件信封，是「CDC 事件模型」与「流查询 changelog」之间的最小接口契约——nop D1 不支持 retract 不妨碍先落地这套信封。

## Context

Debezium 是 CDC 领域的事实标准：把数据库行级变更抽成统一的变更事件模型，经 Kafka/嵌入式引擎投递给下游。它被列入本调研，是因为 nop-stream 的 CDC 子系统（`nop-stream/nop-stream-connector-debezium`）与 `nop-stream-sql` 的语义边界直接相关——`nop-stream-sql` 的 D1 裁定「last-value-wins 终值语义，无 RowKind/无 retract」，而 retract 的输入侧正是 Debezium 这类 CDC 源；本篇要回答的是：Debezium 侧的事件模型、快照+流、schema change 到底提供什么，nop 侧拿到后能用到什么程度。所依据的本地源码：`/Users/abc/sources/streaming-sql/debezium`（shallow clone，`pom.xml:13` = `3.8.0-SNAPSHOT`，即 3.7 之后的开发线）。

## 项目概览

- **定位**：低延迟变更数据捕获平台——「为每个行级变更产生事件、统一模型、只暴露已提交变更」（`README.md:16`，原文 "a single model of all change events"）。核心是连接器（源）+ 变更事件信封 + 分发（Kafka Connect / Embedded / Server）。
- **语言 / 许可证**：Java（构建需 JDK 21+，`README.md` Building 节）；Apache License 2.0（`README.md:9`）；`debezium-ddl-parser` 的 ANTLR 语法为 MIT。
- **演进状态**：活跃。本仓库版本 `3.8.0-SNAPSHOT`（`pom.xml:13`），`SnapshotType` 已含 `INITIAL/BLOCKING/INCREMENTAL`，signal 框架与 blocking snapshot 就位——对应 nop `cdc-design.md` 引用的 3.7+ 能力面。
- **SQL/规则能力入口**：Debezium **不是查询引擎**，其「SQL 能力」有两处：
  1. **DDL 解析**（schema change）：`debezium-ddl-parser/src/main/antlr4/...` 下 8 个 ANTLR4 语法（MariaDB / MySQL generated / MySQL legacy / Oracle PL/SQL 各 lexer+parser）——**没有 PostgreSQL 语法**（PG 走 pgoutput 逻辑复制，不需要 DDL 解析）；
  2. **快照/增量快照的 SELECT 生成**：`debezium-connector-common/.../pipeline/source/snapshot/chunked/SnapshotChunkQueryBuilder.java` 与 `ChunkBoundaryCalculator.java`。
- **模块**：`debezium-api`（引擎面 `ChangeEvent`/`DebeziumEngine`/`Snapshotter` SPI）、`debezium-connector-common`（信封、pipeline、schema history、relational 映射）、各 `debezium-connector-*`、`debezium-embedded`（含 `async/` = AsyncEmbeddedEngine）、`debezium-storage-*`（offset 与 schema history 的 file/jdbc/kafka/s3/redis/rocksdb 等实现）、`debezium-ddl-parser`、`debezium-sink`、`debezium-server`、`debezium-testing`。
- **注意（本地仓库限制）**：本 clone 中 `debezium-core/` 仅含 `pom.xml`，**源码不在本仓库**（`git ls-files debezium-core` = 1）。凡属 `io.debezium.document` / `io.debezium.utils` 等 core 包的结论均**未在本仓库验证**。

## 十维度分析

### 1. SQL/规则前端：parser 与语义入口

- **没有 SELECT 层的查询前端**——Debezium 的语义入口是「连接器配置 + 事件信封」，不是查询语言。
- DDL 前端：`debezium-ddl-parser/src/main/antlr4/io/debezium/ddl/parser/{mariadb,mysql/generated,mysql/legacy,oracle/generated}/*.g4` 共 8 个语法文件，MySQL 保留 `generated/` 与 `legacy/` 双 parser（兼容老版本 binlog 格式）。解析产物喂给 `HistorizedDatabaseSchema.applySchemaChange` 与 `TableSchemaBuilder`。
- 行级 SQL 前端：`pipeline/source/snapshot/chunked/SnapshotChunkQueryBuilder` 生成 `SELECT ... WHERE key > ? AND key <= ? ORDER BY key` 的分块查询；`ChunkBoundaryCalculator:50` 注释指明分块键可为 PK 或 `message.key.columns`。
- 与 nop 对照：nop-stream-sql 的入口是 EQL（SELECT），Debezium 的入口是配置 + 信封；**两者不在同一层**——Debezium 是 nop SQL 的「上游数据源语义提供者」。

### 2. Table & Changelog 模型：变更事件信封

- **信封结构**（`debezium-connector-common/.../data/Envelope.java`）：`SCHEMA_VERSION = 2`（`:32`），字段族由 `FieldName` 定义——`before`（`:94`）、`after`、`op`、`source`、`transaction`、`ts_ms`（`:119`）、`ts_us`、`ts_ns`（并入 `ALL_FIELD_NAMES` 静态集合）。信封由 `Envelope.defineSchema()`（`:226`）按表 schema 构造。
- **操作码**（`Envelope.Operation` `:37`）：`READ("r")`（`:42`，快照读）、`CREATE("c")`、`UPDATE("u")`、`DELETE("d")`、`TRUNCATE("t")`、`MESSAGE("m")`。**这是一套显式 changelog 记号**，与 Flink `RowKind`（+I/-D/+U/-U）同构但更细。
- **`source` 子结构**（`connector/AbstractSourceInfo` `:20`，key 常量 `:22-33`）：`version` / `connector` / `name` / `ts_ms` / `ts_us` / `ts_ns` / `snapshot` / `db` / `schema` / `table` / `collection` / `sequence`——**`snapshot` 标记是区分快照期与流式期的唯一显式记号**（`SNAPSHOT_KEY = "snapshot"` `:28`）。
- **快照期标记**（`connector/SnapshotRecord.java` `:16`）：`TRUE` / `FIRST` / `FIRST_IN_DATA_COLLECTION` / `LAST_IN_DATA_COLLECTION` / `LAST` / `FALSE` / `INCREMENTAL`——粒度到「整次快照 / 单表 / 增量窗口」，`fromSource(Struct)` / `toSource(Struct)` 直接读写 `source` 结构。
- **快照类型**（`connector/SnapshotType.java` `:14`）：`INITIAL` / `BLOCKING` / `INCREMENTAL`。
- **关系模型映射**：`relational/TableSchema.java` + `relational/TableSchemaBuilder.java:56` 把 DB 表结构编译成 Kafka Connect 的 key/value `Schema`；`relational/Key.java:117` 支持 `message.key.columns` 正则定制主键（`:118` 给出 `inventory.customers:pk1,pk2;(.*).purchaseorders:pk3,pk4` 示例）。
- **脱敏/列裁剪**：`relational/RelationalDatabaseConnectorConfig` 的 `MASK_COLUMN_WITH_HASH`（`:473`，`column.mask.hash.<algo>.with.salt.<salt>`）、`MASK_COLUMN`（`:482`）、`TRUNCATE_COLUMN`（`:489`），实现于 `relational/mapping/{MaskStrings,TruncateColumn,ColumnMappers}`。
- **TOAST/未变列补全**：`processors/reselect/ReselectColumnsPostProcessor.java:68`——对「变更列清单里标记为未提供」的列回源重查（Postgres TOAST 场景），是**信封不完整时的后处理补全**。
- **与 nop 对照**：nop 的 CDC 目标端 `dmlMode`（`INSERT`/`UPSERT`/`UPSERT_DELETE`，见 `cdc-design.md` §3.5）消费 `op` 码的子集；在 D1（last-value-wins）下只用 `after` + `op`，`before` 天然闲置——**这就是「CDC retract 不支持」在数据面的具体形态**。

### 3. 时间语义

- 三组时间戳并存且分工明确：
  - **连接器处理时间**：信封顶层 `ts_ms/ts_us/ts_ns`（`Envelope.FieldName.TIMESTAMP` 等）——`System.currentTimeMillis` 派生，表示连接器生成事件的时刻；
  - **源时间**：`source.ts_ms/ts_us/ts_ns`（`AbstractSourceInfo.TIMESTAMP_KEY` 等）——binlog 事务时间 / LSN 关联时间；
  - **序**：`source.sequence` + 分区全序（`README.md` Basic architecture：Kafka 保证变更事件被复制且**全序**、同一库表按源发生顺序投递）。
- **没有 watermark 概念**，也不做乱序重排：保证的是「同一 data collection 按源库发生顺序投递」，下游要 watermark 需自行派生（如 nop 侧从 `source.ts_ms` 派生）。
- **心跳（空闲不阻塞）**：`heartbeat/Heartbeat.java:28` `heartbeat.interval.ms`、`Heartbeat.TOPICS_PREFIX`；`pipeline/EventDispatcher.dispatchHeartbeatEvent`（`:459`）/ `alwaysDispatchHeartbeatEvent`（`:454`）——作用等价于 Flink 的 idling watermark。
- **事务元数据**：`pipeline/txmetadata/`（`TransactionMonitor` / `TransactionContext` / `TransactionStatus`）；`EventDispatcher.dispatchTransactionStartedEvent`（`:396`）、`dispatchTransactionCommittedEvent`（`:385`）——下游可按事务边界分组（事务型 exactly-once sink 的前提）。
- **与 nop 对照**：nop 的 D6 是事件时间 + `timestampsAndWatermarks` + `allowedLateness`（D9 已放行）。**CDC 源没有原生 watermark**，nop 侧必须自己完成「`source.ts_ms` → bigint epoch-millis 时间列 → watermark」的派生；`heartbeat` 则对应 nop 空闲源的 watermark 推进需求，是可直接抄的机制。

### 4. 窗口与聚合

- **不支持 / 不适用**——Debezium 无窗口、无聚合、无 GROUP BY。
- 唯一的「类窗口」结构是**增量快照的去重窗口**：`AbstractIncrementalSnapshotChangeEventSource` 里以 `window`（key → row 的 Map）缓存窗口内行（`window.put(keyStruct, row)` `:702`），窗口外的流式事件经 `deduplicateWindow(...)`（`:201`）从 Map 中移除对应 key，`sendWindowEvents`（`:174`）在窗口关闭时一次性 flush。**这是「以 key 为粒度的去重窗口」，不是时间窗口**，但它解决的问题（快照读与增量写交叠导致重复）在 nop 侧同样存在。
- 与 nop 对照：nop 的 TUMBLE/GROUP BY/OVER 是查询层能力，与 Debezium 正交；Debezium 提供的是**源侧不重复保证**，nop 不应在源侧重造。

### 5. Join/关联

- **不支持 / 不适用**——Debezium 无 join、无维表关联。
- 最接近的是 `RelationalDatabaseConnectorConfig.MSG_KEY_COLUMNS`（`:324`）与 `processors/reselect` 的回源查询——**单表按 key 的点查/重查**，不构成 join 语义。
- 与 nop 对照：nop 的静态维表 lookup join（`ITableLookup`→`IJdbcTemplate`/`IBatchLoader`）与 CDC 源正交；但 `nop-stream-connector-jdbc` 若做「CDC 目标端按 key 取现值」，可参考 `ReselectColumnsPostProcessor` 的回源 + 缓存结构（`processors/reselect/cache/`）。

### 6. CEP/模式匹配

- **不适用**——Debezium 无 pattern、无 filter/switch 规则语言。
- 最接近的「规则」是 **signal 框架**（`pipeline/signal/`）：`SignalProcessor`（`:48`）、`SignalPayload`（`:14`）、`SignalAction` SPI、`StandardActionProvider` / `snapshotting/` 动作族——它是**控制面指令通道**（通过专用表/主题下发 `execute`/`stop`/`pause`/`snapshot-window-open` 等），不是事件模式匹配。
- 与 nop 对照：nop 的 filter/switch 属场景编排层（Nussknacker 那一层），CEP 归 `nop-stream-cep`；Debezium 两层都没有，**不构成对照项**。

### 7. 状态与容错

- **offset 是唯一恢复真相源**：`pipeline/spi/OffsetContext`（`:33`）持有读取位点 + `getIncrementalSnapshotContext()`（`:118`）持有增量快照的 chunk 位置；由 `debezium-storage-*` 持久化（`debezium-storage/debezium-storage-api/.../{OffsetStore,OffsetStorageReader,OffsetStorageWriter}`；实现覆盖 file / jdbc / kafka / s3 / azure-blob / redis / rocksdb / rocketmq / chronicle-queue / configmap）。
- **schema 也需恢复**：`relational/history/SchemaHistory`（`SchemaHistory.java:34`）——`record(...)`（`:183`）写入 DDL，`recover(offsets, tables, ddlParser)`（`:224`）重放；存储实现同上（`FileSchemaHistory` / `JdbcSchemaHistory` / `AzureBlobSchemaHistory` 等）。
- **顺序保证**：per-partition total order（Kafka）；同一 data collection 按源顺序。**exactly-once 不在源侧承诺**——`README.md` 明确「客户端自行决定要 exactly-once 还是 at-least-once」，源侧只保证「不丢、有序」。
- **快照→流的不重不漏**靠：单 offset 交接（见 §9）+ 增量快照去重窗口（§4）+ `SnapshotRecord` 标记（§2）。
- 与 nop 对照：nop 的 checkpoint（`checkpoint-design.md`）+ `NopStreamOffsetBackingStore`（`cdc-design.md` §3.4）承担同一职责；Debezium 的**存储可插拔面**（10 种 offset/history 后端）说明「offset 与 schema history 必须可插拔」是被验证过的设计，nop 选 `JdbcSchemaHistory` 作默认与此一致。

### 8. 声明形态与可嵌入性

- **三种形态**（`README.md` Basic architecture）：
  1. **Kafka Connect**（分布式平台形态）——最重，靠 Kafka 提供复制/容错；
  2. **Embedded connector engine**（库形态）——`debezium-api` 的 `DebeziumEngine`（`DebeziumEngine.java:46`）在应用进程内运行，事件直接回调；`debezium-embedded/async/` 为 AsyncEmbeddedEngine（nop `cdc-design.md` D1 选型）；
  3. **Debezium Server**（独立服务形态）——把事件推到非 Kafka 目的地。
- **嵌入面 API**：`ChangeEvent<K,V>`（`debezium-api/.../engine/ChangeEvent.java:17`，`key()/value()/headers()/destination()/partition()`）、`DebeziumEngine.ChangeConsumer.handleBatch`（`:184`）、`RecordCommitter`（`:131`）——**批量 + 显式 markProcessed/markBatchFinished**，是把「提交语义」交给宿主的关键接口；`Snapshotter` SPI（`debezium-api/.../spi/snapshot/Snapshotter.java:28`）暴露 `shouldSnapshotData`（`:43`）/ `shouldSnapshotSchema`（`:51`）/ `shouldStream`（`:56`）/ `shouldSnapshotOnSchemaError`（`:61`）/ `shouldSnapshotOnDataError`（`:66`）——**快照策略可替换**。
- **与 nop 对照**：nop 选择「库形态 + 自有 checkpoint」与 Debezium embedded 路线一致；`Snapshotter` SPI 的存在说明「快照何时该跑」本就是可策略化维度，nop 的 `snapshot.mode` 对应物可参照其枚举（§9）。

### 9. 架构要点（值得记的设计）

1. **snapshot → streaming 的单 offset 交接**（`pipeline/ChangeEventSourceCoordinator`）：
   - `start(...)`（`:124`）在单独线程里先恢复 schema history（`schema.isHistorized() && getSchemaHistory().exists()` → `recover(previousOffsets)`，`:146`）再启动快照源；
   - `executeChangeEventSources(...)`（`:209`）：`doSnapshot(...)`（`:216`）拿 `SnapshotResult<O>` → `running && snapshotResult.isCompletedOrSkipped()` 才继续（`:222`）→ 可选 `delayStreamingIfNeeded(...)`（`:224`，`streaming.delay.ms`）→ **把 `snapshotResult.getOffset()` 直接作为 `streamEvents(...)` 的起始 offset**（`:227`）。
   - 三个关键点：**schema 先恢复、offset 单一交接（无双主）、快照完成才进流**。`SnapshotResult`（`pipeline/spi/SnapshotResult.java:8`）只有 `isCompletedOrSkipped()`（`:30`）/ `isCompleted()`（`:34`）两态——状态机刻意简单。
2. **snapshot.mode 枚举把「跑不跑快照」做成配置矩阵**：`BinlogConnectorConfig.SnapshotMode`（`:148`）：`always` / `when_needed` / `initial` / `no_data`（只 schema）/ `recovery` / `initial_only` / `configuration_based` / `custom`——**每种模式的注释都写明「适用的恢复场景」**，是可直接抄的运维面清单。
3. **signal 驱动的增量快照 + 窗口去重**：
   - `AbstractIncrementalSnapshotChangeEventSource` 抽象出 `emitWindowOpen(...)`（`:215`，注释「Update low watermark」）与 `emitWindowClose(...)`（`:220`，注释「Update high watermark」），由各连接器实现——PG 见 `PostgresReadOnlyIncrementalSnapshotChangeEventSource:105/:111`，binlog 见 `BinlogReadOnlyIncrementalSnapshotChangeEventSource:129/:134`；
   - 窗口开关通过 signal 下发：`OpenIncrementalSnapshotWindow.NAME = "snapshot-window-open"`（`pipeline/signal/actions/snapshotting/OpenIncrementalSnapshotWindow.java:14`）、Close 同族；`AbstractIncrementalSnapshotContext.openWindow(id)`（`:121`）/ `closeWindow(id)`（`:131`），并显式处理**重启后收到的乱序窗口信号**（`:168` 注释「out-of-order windowing signals after connector restart … ignored」）；
   - 窗口内行 `window.put`（`:702`），窗口外的流式事件 `deduplicateWindow`（`:201`）移除——**以 key 为粒度消除「快照读到的旧行 vs 流式到达的新行」重复**。
   - **这是本篇对 nop 最有操作价值的设计**：nop 若要支持「初始快照与 binlog 增量并发」，可直接映射为「窗口开关 + key 去重 + 乱序信号忽略」三条规则。
4. **DDL → schema history → 事件并行下发**：`SchemaChangeEvent`（`schema/SchemaChangeEvent.java:29`）带 `SchemaChangeEventType`（`:158`，`ofDatabase/ofSnapshotCreate/ofCreate/ofAlter/ofRename/ofDrop/ofTruncate` 工厂 `:230-368`）、`getDdl()`、`getTableChanges()`；`HistorizedDatabaseSchema`（`schema/HistorizedDatabaseSchema.java:27`）提供 `applySchemaChange` / `recover` / `ddlFilter()` / `skipUnparseableDdlStatements()` / `storeOnlyCapturedTables()`——**schema 变更与数据变更走同一分发通道**，下游不需另开订阅。

### 10. 与 nop-stream/nop-stream-sql 的对照与可借鉴点

- **对 `nop-stream-connector-debezium` 的直接可借鉴项**：
  1. **启动序列三条不变式**：schema history 先 recover → 快照跑完拿 `SnapshotResult` → 用同一 offset 起流。`cdc-design.md` 已把 offset 收进 `NopStreamOffsetBackingStore`，但「schema 先于数据」这条在文档里是隐含的——Debezium 把它写成了 `start()` 的第一段代码。
  2. **快照模式矩阵**：`SnapshotMode` 八种值 + `Snapshotter` SPI 的五个 `should*` 决策点，是 nop `snapshot.*` 配置面的现成清单。
  3. **增量快照的窗口开关 + key 去重 + 乱序信号忽略**——nop 若要做「不暂停流的补数」，这是最小实现路径。
- **对 `nop-stream-sql` 的直接可借鉴项**：
  1. **信封即 changelog 契约**：`op`（r/c/u/d/t）+ `snapshot` + `ts_ms` 三件套是一个**与查询语言解耦的 changelog 记号层**。nop D1 不支持 retract，但完全可以在 CDC source 侧先落地这个记号（只消费 `c/u/d` + `after`），为将来 D1 松动留下**已格式化的输入**——避免届时再改 connector 契约。
  2. **`heartbeat` 对应空闲源 watermark 推进**：nop 的事件时间路径在「源静默」时会卡 watermark，Debezium heartbeat 是已验证的解法。
  3. **`ts_ms` 三粒度（ms/us/ns）**：nop 时间列钉死 bigint epoch-millis（D6），Debezium 提供到 ns 的源侧精度——若将来要亚毫秒窗口，源侧已备好，改动只在 nop 的派生层。
- **不借鉴 / 不适用**：Debezium 无窗口、无聚合、无 join、无 CEP——十维度中 4/5/6 三条均为「不适用」，不构成对 nop SQL 子集的对照样本。
- **关于「CDC retract 不支持」（D1 裁定）**：Debezium **本身不产生 retract 语义**——它产生的是「行级变更事件」，retract 是**查询/聚合层**（update-before）概念。两者不冲突：Debezium 证明「输入侧可以携带完整 changelog 记号」，nop 当前只是**在消费端不解释其中的撤回语义**。D1 裁定维持不变，但可补一条边界说明：**D1 限制的是 nop-stream-sql 的输出语义，不限制 CDC 输入信封的记号完备性**。

## 对照 nop-stream-sql 已交付子集

**入口形态**

| nop-stream-sql | Debezium | 差异 / 可借鉴 |
|---|---|---|
| `.stream.xml` 顶层 `<sql sinkBean>`，EQL 共享 ORM parser | 无查询语言；入口 = 连接器配置 + `DebeziumEngine.Builder` | 不同层：Debezium 是 source，nop `<sql>` 是 source 之后的查询。可借鉴：`Snapshotter` SPI 的 `should*` 决策面可映射为 nop CDC source 的配置决策表。 |
| 内嵌 schema：9 受管类型闭集 | `TableSchemaBuilder` 从 DB 元数据**自动生成** schema | 相反方向：Debezium 类型面由源决定（开放），nop 查询面由 schema 声明决定（闭集）。**不可类比**——nop 的闭集是查询编译期约束，Debezium 是数据到达期映射。 |
| FROM 表名 = 数据源 bean 名 | `source.db/schema/table` 标识数据集合（`AbstractSourceInfo:29-32`） | nop 的 bean 名对应 Debezium 的 `dataCollectionId`——两者都是「数据集合标识」，可作为 connector↔SQL 的绑定点命名参考。 |

**支持清单逐条**

| nop 支持项 | Debezium 对应 | 差异 / 可借鉴 |
|---|---|---|
| SELECT 投影 / WHERE | 不适用（无查询层） | — |
| 五聚合 / GROUP BY | 不适用 | — |
| TUMBLE(t, INTERVAL) 伪表函数 | 不适用；但 `source.ts_ms` 提供时间列原料 | **可借鉴**：TUMBLE 时间列要求 bigint epoch-millis，CDC source 派生时应直接产出该形态，避免下游再转换。 |
| 双流等值 join / UNION ALL | 不适用 | — |
| 静态维表 lookup join | `ReselectColumnsPostProcessor` 的按 key 回源（`processors/reselect/`） | 形似不同：那是「补全未变列」，不是维表关联。可借鉴其实现结构（cache + 按列回源）。 |
| 无窗口持续 GROUP BY | 不适用 | — |
| 分析窗口 OVER | 不适用 | — |

**不支持清单逐条**

| nop 不支持（fail-fast） | Debezium 状态 | 结论 |
|---|---|---|
| 全局 ORDER BY/LIMIT、非等值 join、DATE/TIMESTAMP/DECIMAL 列绑定、CASE/CAST/IN 子查询、DISTINCT 聚合、HAVING/CTE/INTERSECT/EXCEPT、全局聚合、FULL window join、UNION DISTINCT、Flink SQL 方言 | 不适用（无查询层） | Debezium 不构成对这些 fail-fast 项的对照证据。 |
| **CEP 级复杂编排不进 SQL 面** | 不适用（signal 是控制面，非 pattern） | 不构成对照。 |
| **retract/CDC（D1 语义降级）** | Debezium 提供完整 changelog 记号（`op` + `before/after` + `snapshot`），但**不产生 retract** | **关键澄清**：D1 限制的是 nop-stream-sql 的**输出语义**，不是 CDC **输入记号**。建议在 `nop-stream-sql.md` §2 的「性质不同」段落补一句边界说明：CDC source 侧的 `op`/`before` 记号完备性不受 D1 限制，D1 只禁止聚合输出按 update-before 发射。 |

**语义标注（D1/D6/D9/D10/D11）**

- D1（last-value-wins，无 RowKind/无 retract）：Debezium 侧**记号完备**（有 `op`、有 `before`），nop 侧**消费不完整**（只用 `after` + `op∈{c,u,d}`）。差异定位清晰，见上表。
- D6（事件时间，bigint epoch-millis）：Debezium 有三粒度源时间但**无 watermark**；nop 需自行派生 → 与 §3 结论一致。
- D9（allowedLateness 已放行）：Debezium 无对应概念；CDC 源天然「按源顺序、无迟到」，但**快照期与流式期交叠**会造成「看起来迟到」的旧行——正是去重窗口要解决的问题，nop 侧需等价机制（或以 `snapshot` 标记短路）。
- D10/D11（保持 fail-fast）：Debezium 无对应约束，维持 fail-fast。
- retract/CDC 全链路：维持 D1 裁定的不支持项；边界澄清见上表。

**架构对照**

- nop：`nop-stream-connector-debezium`（Debezium embedded → `SourceFunction`，offset 进 checkpoint）+ `nop-stream-sql`（EQL → `StreamSqlCompiler` → 流模型）。
- Debezium：`ChangeEventSourceCoordinator`（snapshot → streaming 单 offset 交接）+ `SchemaHistory`（先恢复）+ `signal`（增量快照控制）+ `storage-*`（offset/history 可插拔）。
- 关键差异：**Debezium 的 offset 与 schema history 是两个独立真相源**（`OffsetContext` + `SchemaHistory`），nop `cdc-design.md` §3.4 把 offset 收进 checkpoint、schema history 走 `JdbcSchemaHistory`——**同样是两个真相源，分工一致**。可补的是「recover 顺序」的显式约定：Debezium 在 `start()` 里先 recover schema 再起快照，nop 应在文档/代码中把这条顺序钉成不变式。

## 引用出处

以下路径均已在本地源码库 `/Users/abc/sources/streaming-sql/debezium` 逐条验证存在；行号取自实际读取内容。

- `/Users/abc/sources/streaming-sql/debezium/README.md:9`（Apache License 2.0）、`:10`（ANTLR MIT）、`:16`（统一变更事件模型）、`:24`（Basic architecture / 全序 / exactly-once 归客户端）、`:26`（embedded connector engine）、`:57`（JDK 21）
- `/Users/abc/sources/streaming-sql/debezium/pom.xml:13`（`3.8.0-SNAPSHOT`）
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/data/Envelope.java:32`（SCHEMA_VERSION）、`:37`（Operation）、`:42`（READ）、`:94`（BEFORE）、`:119`（TIMESTAMP）、`:226`（defineSchema）
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/connector/AbstractSourceInfo.java:20`、`:22-33`（key 常量）、`:28`（SNAPSHOT_KEY）
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/connector/SnapshotRecord.java:16`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/connector/SnapshotType.java:14`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/relational/TableSchemaBuilder.java:56`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/relational/Key.java:117`、`:118`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/relational/RelationalDatabaseConnectorConfig.java:324`（MSG_KEY_COLUMNS）、`:473`（MASK_COLUMN_WITH_HASH）、`:482`（MASK_COLUMN）、`:489`（TRUNCATE_COLUMN）
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/processors/reselect/ReselectColumnsPostProcessor.java:68`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/heartbeat/Heartbeat.java:28`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/EventDispatcher.java:74`、`:385`、`:396`、`:454`、`:459`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/ChangeEventSourceCoordinator.java:124`、`:146`、`:209`、`:216`、`:224`、`:227`、`:234`、`:298`、`:335`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/spi/SnapshotResult.java:8`、`:30`、`:34`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/spi/OffsetContext.java:33`、`:118`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/SnapshottingTask.java:16`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/AbstractSnapshotChangeEventSource.java:70`、`:92`、`:194`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/spi/SnapshotChangeEventSource.java:20`、`:34`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/spi/StreamingChangeEventSource.java:21`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/snapshot/incremental/AbstractIncrementalSnapshotChangeEventSource.java:159`、`:173`、`:201`、`:215`、`:220`、`:702`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/snapshot/incremental/AbstractIncrementalSnapshotContext.java:121`、`:131`、`:168`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/snapshot/incremental/IncrementalSnapshotChangeEventSource.java:24`、`:36`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/signal/SignalProcessor.java:48`、`SignalPayload.java:14`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/signal/actions/snapshotting/OpenIncrementalSnapshotWindow.java:14`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/signal/actions/AbstractSnapshotSignal.java:20`、`:23`（FIELD_DATA_COLLECTIONS）、`:32`（内嵌 SnapshotType）、`:49`（type 缺省 INCREMENTAL）
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/snapshot/chunked/ChunkBoundaryCalculator.java:50`（`keyColumns` = PK or `message.key.columns`）
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/pipeline/source/snapshot/chunked/SnapshotChunkQueryBuilder.java`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/schema/SchemaChangeEvent.java:29`、`:158`、`:230-368`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/schema/HistorizedDatabaseSchema.java:27`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-common/src/main/java/io/debezium/relational/history/SchemaHistory.java:34`、`:183`、`:224`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-binlog/src/main/java/io/debezium/connector/binlog/BinlogConnectorConfig.java:148`（SnapshotMode）
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-binlog/src/main/java/io/debezium/connector/binlog/BinlogReadOnlyIncrementalSnapshotChangeEventSource.java:129`、`:134`
- `/Users/abc/sources/streaming-sql/debezium/debezium-connector-postgres/src/main/java/io/debezium/connector/postgresql/PostgresReadOnlyIncrementalSnapshotChangeEventSource.java:105`、`:111`
- `/Users/abc/sources/streaming-sql/debezium/debezium-api/src/main/java/io/debezium/engine/ChangeEvent.java:17`
- `/Users/abc/sources/streaming-sql/debezium/debezium-api/src/main/java/io/debezium/engine/DebeziumEngine.java:46`、`:131`、`:184`
- `/Users/abc/sources/streaming-sql/debezium/debezium-api/src/main/java/io/debezium/spi/snapshot/Snapshotter.java:28`、`:43`、`:51`、`:56`、`:61`、`:66`
- `/Users/abc/sources/streaming-sql/debezium/debezium-ddl-parser/src/main/antlr4/io/debezium/ddl/parser/`（mariadb / mysql/generated / mysql/legacy / oracle/generated 共 8 个 `.g4`）
- `/Users/abc/sources/streaming-sql/debezium/debezium-storage/`（storage-api + file / jdbc / kafka / s3 / azure-blob / redis / rocksdb / rocketmq / chronicle-queue / configmap 等子模块）
- `/Users/abc/sources/streaming-sql/debezium/debezium-storage/debezium-storage-jdbc/src/main/java/io/debezium/storage/jdbc/history/JdbcSchemaHistory.java`
- `/Users/abc/sources/streaming-sql/debezium/debezium-storage/debezium-storage-api/src/main/java/io/debezium/spi/storage/OffsetStore.java`
- `/Users/abc/sources/streaming-sql/debezium/debezium-embedded/src/main/java/io/debezium/embedded/async/`（AsyncEmbeddedEngine）

未在本仓库验证：

- `debezium-core/` 源码缺失（本地仅 `pom.xml`，`git ls-files` 证实只跟踪 1 个文件），故 `io.debezium.document.*`、`io.debezium.utils.*`、`io.debezium.schema.SchemaFactory` 等 core 侧类的内部实现**未在本仓库验证**；本文对 `SchemaFactory` 等仅按引用关系提及，未下行为结论。
- `README.md` 中关于 Kafka Connect 分布式容错、exactly-once 语义的描述性段落按原文引用，**未在本仓库验证其对应实现代码**（Kafka Connect 侧代码不在本仓库）。
- `SnapshotChunkQueryBuilder` / `ChunkBoundaryCalculator` 的具体 SQL 拼接细节仅按文件存在与 `ChunkBoundaryCalculator:50` 的注释推断，**具体实现未逐行核对**。
