# Apache Paimon streaming SQL 能力分析（流式湖仓）

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项），重点：changelog 生成/消费、partial-update、lookup join 缓存、与 Flink SQL 集成的 source/sink DDL
> Conclusion: 最值得借鉴两点——(1) **changelog 是"可配置的表属性"而非"引擎内建语义"**（`changelog-producer = none|input|lookup|full-compaction` 四档，每档明确"before image 何时产生、延迟多大、消费者要做什么"），这套**四选一 + 代价表**的声明面比 Flink 的 RowKind 更适合 nop 的窄口径；(2) **watermark/computed column/metadata column 被序列化成表 options**（`schema.watermark.0.rowtime/strategy.expr/strategy.data-type`），使"时间语义"成为可持久化、可迁移的元数据——nop 的 `.stream.xml` 可直接采用同形做法。

## Context

nop-stream-sql 目前的动态维表 lookup 是"静态维表 + 每次查库"的最简形态，且 D1 裁定不输出 retract/upsert。Paimon 是"流式湖仓"路线的代表：它不提供 SQL 引擎，而是**把变更语义下沉到存储层**，由 Flink/Spark/Hive 的 SQL 前端消费。本篇要回答：Paimon 的 changelog 生成/消费、partial-update、lookup 缓存三块，有哪些**声明面与语义面**的资产可被 nop 借鉴（尤其在不引入 retract 输出的前提下如何描述变更）。依据本地源码 `/Users/abc/sources/streaming-sql/paimon`（HEAD `64dc7c1cbb8`，`pom.xml:34` `2.2-SNAPSHOT`，Apache-2.0）。

## 项目概览

- **定位**：lake format + LSM 结构的实时湖仓（`README.md:20-22` "Apache Paimon is a lake format that enables building a Realtime Lakehouse Architecture with Flink and Spark for both streaming and batch operations"；前身 Flink Table Store）。
- **语言 / 许可证**：Java（+ `paimon-python`、`paimon-spark` 多版本模块），Apache License 2.0（`LICENSE`）。
- **演进状态**：`2.2-SNAPSHOT`（`pom.xml:34`）；模块 `paimon-api / paimon-common / paimon-core / paimon-format / paimon-flink / paimon-spark / paimon-hive / paimon-iceberg / paimon-arrow / paimon-lance / paimon-service / paimon-lumina / paimon-mosaic / ...`（仓库根目录）。`paimon-flink/` 下按 Flink 版本分模块：`paimon-flink-1.16 … 1.22 / 2.0 / 2.1 / 2.2` + `paimon-flink1-common` + `paimon-flink2-common`（**同一份 SQL 集成代码维护 11 个 Flink 版本**）。
- **SQL 能力入口**：**Paimon 自己没有 SQL parser**。入口全部是"别人的标准接口"：
  - Flink：`paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/FlinkTableFactory.java:39`（`DynamicTableSource/Sink` factory）+ `FlinkCatalogFactory.java:34` `IDENTIFIER = "paimon"` + `FlinkGenericCatalogFactory`，经 SPI 注册于 `paimon-flink-common/src/main/resources/META-INF/services/org.apache.flink.table.factories.Factory:16-18`。
  - Spark：`paimon-spark/paimon-spark-common` + `paimon-spark-3.2 … 4.1`。
  - Hive / Python / Program API / CLI Action：`paimon-hive`、`paimon-python`、`docs/docs/program-api`、`paimon-flink/paimon-flink-common/.../action/`。
- **语义核心入口**：`paimon-api/src/main/java/org/apache/paimon/CoreOptions.java`（351 个 `public static final ConfigOption`）、`paimon-core/src/main/java/org/apache/paimon/mergetree/compact/`（merge function 家族）。

## 十维度分析

### 1. SQL 前端：parser/planner（Calcite 版本与接入方式）、扩展语法

- **不适用 / 无自有前端**：Paimon 不含任何 SQL parser、validator、planner（全仓无 `SqlParser`/`Calcite`/`RelNode` 类）。SQL 完全由宿主引擎提供：
  - Flink SQL 侧只做 **DDL 映射与 schema 读写**（见 §2/§8）；
  - Spark SQL 侧走 Spark 的 `Table`/`ScanBuilder` 接口（`paimon-spark-common`）。
- 因此本维度对 nop 的意义是**反向的**：nop 的 EQL 是自有前端，Paimon 证明了"存储/湖仓层可以完全不碰 SQL"这条分工线——**如果 nop 未来要把"流语义"与"SQL 语法"解耦，Paimon 是现成的分工样板**。
- 与 flink/calcite 分析的关系：Paimon 是它们的**下游消费者**——`FlinkTableFactory` 实现 Flink 的 `DynamicTableSourceFactory`（§8），changelog 由 Flink 的 `ChangelogMode` 消费。

### 2. Table & Changelog 模型：Table/ChangelogMode、DDL、catalog

- **表模式**：append-only 表 vs primary-key 表（LSM merge-tree），由 `PRIMARY KEY ... NOT ENFORCED` + `bucket` 决定；`CoreOptions.java:121` `bucket`（`-1` dynamic / `-2` postpone / `>0` fixed）、`:136` `bucket-key`、`:736` `merge-engine`。
- **merge-engine 四型**（决定"同一主键多条记录如何合成一条"）：
  - `deduplicate`（默认）、`first-row`（`mergetree/compact/FirstRowMergeFunction.java`）
  - `partial-update`（`mergetree/compact/PartialUpdateMergeFunction.java`，767 行）——**§9 展开**
  - `aggregation`（`mergetree/compact/aggregate/factory/`，23 个 field-level 聚合工厂：`FieldSumAggFactory`、`FieldMin/Max`、`FieldFirstValue/LastValue`、`FieldFirstNonNullValue/LastNonNullValue`、`FieldBoolAnd/Or`、`FieldListaggAgg`、`FieldCollectAgg`、`FieldMergeMapAgg`、`FieldHllSketchAgg`、`FieldThetaSketchAgg`、`FieldRoaringBitmap32/64Agg`、`FieldPrimaryKeyAgg`、`FieldNestedPartialUpdateAgg` 等）
- **Changelog 生成是表属性**：`CoreOptions.java:1104` `CHANGELOG_PRODUCER = key("changelog-producer")`；枚举 `:5331-5340`：

  | 值 | 语义（源码原文） |
  |---|---|
  | `none`（默认） | "No changelog file." |
  | `input` | "Double write to a changelog file when flushing memory table, the changelog is from input." |
  | `full-compaction` | "Generate changelog files with each full compaction." |
  | `lookup` | "Generate changelog files through 'lookup' compaction." |

  配套开关：`:1113` `changelog-producer.ignore-update-before`、`:1122` `changelog-producer.ignore-delete`、`:1131` `changelog-producer.row-deduplicate`（"Whether to generate -U, +U changelog for the same record"）、`:1138` `changelog-producer.row-deduplicate-ignore-fields`；`FlinkConnectorOptions.java:115` `changelog-producer.compaction-interval`。
- **官方四选一决策表**（`docs/docs/primary-key-table/changelog-producer.md:39-42`）：`none`→"Consumer state, if needed / Incremental snapshot reads / The consumer accepts upserts or can normalize them"；`input`→"Complete changelog supplied by the source"；`lookup`→"Lookup of existing rows during compaction / The input has no before images and consumers need complete changes"；`full-compaction`→"Difference between full-compaction results / Consumers can wait for periodic full compactions"。**每档都写清了"谁负责 before image"与"消费者需要做什么"——这是 nop 描述 D1 语义时最缺的措辞格式。**
- **DDL（Flink SQL 侧）**：schema 存于表目录 `schema/`（`paimon-core/.../schema/SchemaManager.java`），Flink DDL 通过 options 往返：
  - 非物理列（computed / metadata / watermark）被**序列化成表 options**：`paimon-flink/.../utils/FlinkCatalogPropertiesUtil.java:54` `SCHEMA = "schema"`，`:57` `serializeNonPhysicalNewColumns`（键 `schema.<i>.name/data-type/expr/metadata/virtual`），`:96` `serializeNewWatermarkSpec`（键 `schema.watermark.0.rowtime` / `schema.watermark.0.strategy.expr` / `schema.watermark.0.strategy.data-type`），`:214` `deserializeWatermarkSpec`；常量在 `utils/FlinkDescriptorProperties.java:50-60`（`VIRTUAL`/`WATERMARK`/`WATERMARK_ROWTIME`/`WATERMARK_STRATEGY_EXPR`/`WATERMARK_STRATEGY_DATA_TYPE`）。
  - **一个表只允许一条 watermark**：`FlinkCatalog.java:1185-1187` `checkArgument(watermarkSpecs.size() == 1)`；`:1115` 注释 "Serialize virtual columns and watermark to the options"。
- **Catalog**：`FlinkCatalogFactory.java:34` `IDENTIFIER = "paimon"`（Flink `CREATE CATALOG ... WITH ('type'='paimon', 'warehouse'=...)`），底层 `paimon-core/.../catalog/Catalog.java`；另有 `FlinkGenericCatalogFactory` 做跨 catalog 委托。

### 3. 时间语义：WATERMARK 定义语法、迟到数据、idleness、时区

- **WATERMARK 语法由 Flink 提供，Paimon 只负责持久化**：`WATERMARK FOR ts AS ts - INTERVAL '5' SECOND` 由 Flink parser 解析（见 flink 分析 §3），Paimon 把它降为 `schema.watermark.0.*` 三个 option（§2），`FlinkCatalog.java:601-609` 在 schema change 时增删这些键、`:896-908` `applyNewWatermarkSpec`。
- **Paimon 自有的"扫描水位"是 snapshot/watermark 元数据而非事件时间**：`CoreOptions.java:1252` `scan.watermark`（"Optional watermark used in case of 'from-snapshot' scan mode"）、`:1276` `scan.snapshot-id`、`:1324` `streaming.read.snapshot.delay`。
- **流读的 watermark 行为是 Flink connector 选项**（`paimon-flink/.../FlinkConnectorOptions.java`）：
  - `:126` `changelog-producer.compaction-interval`
  - `:126-131` `scan.watermark.emit.strategy`（枚举 `ON_PERIODIC`（"interval is controlled by Flink 'pipeline.auto-watermark-interval'"）/ `ON_EVENT`（"Emit watermark per record"），见 `FlinkConnectorOptions.java` `WatermarkEmitStrategy`）
  - `:138` `scan.watermark.idle-timeout`（**idleness 支持在 connector 层**）
  - `:132` `scan.watermark.alignment.group`、`:147` `scan.watermark.alignment.max-drift`、`:155` `scan.watermark.alignment.update-interval`（**多分区 watermark 对齐**）
- **迟到数据**：Paimon 自身**无 lateness 概念**——它给的是"完整 changelog"，迟到容忍由下游 Flink 算子的 `allowedLateness`/state TTL 决定。`changelog-producer.md:146` 明确"short Flink checkpoint intervals combined with many [commits]" 影响 changelog 粒度。
- **时区**：仅 `CoreOptions.java` 的 `scan.timestamp-millis` 描述里出现"use local time zone"（`from-timestamp` 扫描模式的时间戳换算），窗口语义不涉及。

### 4. 窗口与聚合：窗口类型、window TVF、frame、聚合函数集、TopN/DISTINCT/UDAF/FILTER

- **Paimon 没有窗口**（无 tumble/hop/session、无 TVF、无 frame、无 TopN、无 HAVING/DISTINCT）——SQL 窗口全部由 Flink/Spark 执行。
- **但它有"存储层聚合"**，这是本维度唯一可借鉴的部分：
  - `merge-engine = aggregation` + `fields.<col>.aggregate-function = sum|min|max|...`（`docs/docs/primary-key-table/merge-engine/aggregation.mdx`）
  - 工厂目录 `paimon-core/src/main/java/org/apache/paimon/mergetree/compact/aggregate/factory/`（23 个，见 §2 列表）。
  - partial-update 也接受聚合（`docs/docs/primary-key-table/merge-engine/partial-update.md:135` "Aggregation For Partial Update"，`:140` 默认 `last_non_null_value`，`:190` "Default Aggregation Function"，`:207-214` `fields.default-aggregate-function` 与显式函数的优先级）。
  - 聚合字段的排除规则：`:140` "Primary-key fields and sequence-group ordering fields are not aggregated"。
- **与 nop 五聚合的关系**：nop 的 `StreamSqlAggregations` 是**运行期算子注册表**，Paimon 的是**存储期字段属性**。两者可在同一"参数化算子面"下并列：nop 若要支持"upsert 流的字段级合并"，Paimon 这套 `fields.<col>.<agg>` 命名是现成的声明格式（与 `fields.<col>.sequence-group` 同形）。

### 5. Join：interval/temporal/lookup/常规/非等值/广播

- **Paimon 只参与 lookup join**（维表侧），不实现任何流式 join。文档 `docs/docs/flink/sql-lookup.mdx:33`："Lookup Joins are a type of join in streaming queries. It is used to enrich a table with data that is queried from Paimon. The join requires one table to have a processing time attribute and the other table to be backed by a lookup source connector."
- **六种 lookup 策略表**（`docs/docs/flink/sql-lookup.mdx:35-44`）：

  | 场景 | 策略 | 约束 |
  |---|---|---|
  | 维表能装进每个 lookup task | Normal lookup | 每个 task 默认持全量本地副本 |
  | 维表更新晚于主流 | Retry lookup | miss 会延迟处理，重试次数有界 |
  | 重试阻塞无关记录 | Async retry | 无序输出仅适用 append 流；CDC 受排序约束 |
  | 全量本地副本过大 | Shuffle lookup | Flink 2.0+、固定桶、join key 含全部 bucket key |
  | 只需最新分区 | Dynamic partition | 需配置分区选择与刷新间隔 |
  | 已有共享查询服务 | Query Service | 需固定桶主键表 |

- **代码落点**：`paimon-flink/.../lookup/FileStoreLookupFunction.java:89`（`implements Serializable, Closeable`）、`LookupTable.java`、`FullCacheLookupTable.java:80`（`create(Context, lruCacheSize)` :452）、`LookupFileStoreTable.java`、`LookupStreamingReader.java`、`LookupDataTableScan.java`、`AsyncLookupFunctionWrapper.java`、`LookupRuntimeProviderFactory.java`。
- **缓存档位**（`FlinkConnectorOptions.java:274-278` `LOOKUP_CACHE_MODE = key("lookup.cache")` 默认 `AUTO`；枚举 `:628-641`）：
  - `AUTO`——"Auto mode, try to use partial mode."（`:232-254` 在满足条件时自动升级为 full 并打日志）
  - `FULL`——"Use full caching mode."（`:263-277` 强制 full cache table）
  - `MEMORY`——"Use in-memory caching mode."
  - 相关：`:261` `lookup.async`、`:268` `lookup.bootstrap-parallelism`、`:274` async 线程数 `:274` 区段、`:298` `lookup.dynamic-partition.refresh-interval`（默认 1h）、`:306` `lookup.refresh.async`、`:331` `lookup.refresh.time-periods-blacklist`、`:340` `lookup.refresh.full-load-threshold`；分桶裁剪 `FileStoreLookupFunction.java:574` `getRequireCachedBucketIds()`（"cache only the necessary buckets for each subtask"）。
- **等值/非等值/interval/temporal/广播**：无（由 Flink planner 提供，见 flink 分析 §5）。

### 6. CEP / 模式匹配

**不适用（Not applicable）**——Paimon 无 `MATCH_RECOGNIZE`、无 NFA、无模式匹配算子。CEP 对照请看 `nop-stream/06-cep-comparison.md`、`09d-flink2.3-compare-time-window-cep.md` 与 calcite 分析 §6（`Match` 四层切分）。

### 7. 状态与容错：状态后端、checkpoint、exactly-once、changelog sink

本篇只看**与"changelog sink"直接相关**的部分，checkpoint 协议细节见既有 Flink 分析。

- **两阶段提交**：`paimon-flink/.../sink/CommitterOperator.java:46`（`extends AbstractStreamOperator`）、`:57` 注释 "Remaining data will be committed in `notifyCheckpointComplete`"、`:196` `notifyCheckpointComplete(long checkpointId)`。配套 `Committer.java`、`StoreCommitter.java`、`StoreMultiCommitter.java`、`AutoTagForSavepointCommitterOperator.java`（`sink.savepoint.auto-tag`，`FlinkConnectorOptions.java:349`）。
- **checkpoint 对齐**：`FlinkConnectorOptions.java:241` `source.checkpoint-align.enabled`、`:248` `source.checkpoint-align.timeout`——**source 侧主动对齐 checkpoint，是"湖仓源"特有的语义保证开关**。
- **changelog 可长于 snapshot 生命周期**：
  - `paimon-core/.../Changelog.java` javadoc："The metadata of changelog. It generates from the snapshot file during expiration. So that the changelog of the table can outlive the snapshot's lifecycle."——`Changelog extends Snapshot`。
  - `paimon-core/.../utils/ChangelogManager.java:60` `CHANGELOG_PREFIX = "changelog-"`，`:116` 路径 `branchPath + "/changelog/" + CHANGELOG_PREFIX + snapshotId`，`:141/:149` 列举 changelog 文件。
  - 生命周期：`paimon-core/.../table/ExpireChangelogImpl.java`、`operation/ChangelogDeletion.java`、`CompactedChangelogPathResolver.java`；管理入口 `paimon-flink-common/.../action/ExpireChangelogsAction.java` 与 `procedure/ExpireChangelogsProcedure.java`（Flink/Spark 双端都有）。
  - compact 侧 changelog 产出：`mergetree/compact/ChangelogMergeTreeRewriter.java`、`FullChangelogMergeTreeCompactRewriter.java`、`LookupMergeTreeCompactRewriter.java`、`FullChangelogMergeFunctionWrapper.java`、`LookupChangelogMergeFunctionWrapper.java`、`ChangelogResult.java`。
- **changelog 消费**：
  - 跟随读：`table/source/snapshot/ChangelogFollowUpScanner.java:33` `shouldScanSnapshot`（判 `snapshot.changelogManifestList() != null`）、`:43` `scan`（`withMode(ScanMode.CHANGELOG)`）。
  - 增量读：`table/source/splitread/IncrementalChangelogReadProvider.java`（`beforeFiles` 反向读 + `afterFiles` 正向读 拼 `ConcatRecordReader`，再 `unwrap` 成 `InternalRow`——**"before + after 差分"是 changelog 的通用重建式**）。
  - changelog 压缩：`paimon-flink-common/.../compact/changelog/`（`ChangelogCompactCoordinateOperator`、`ChangelogCompactSortOperator`、`ChangelogCompactTask`、`CompactedChangelogReadOnlyFormat` 等）。
- **consumer 位点**：`paimon-core/.../consumer/{Consumer,ConsumerManager}.java`（`none` 模式下由 consumer state 承担位点，见 `changelog-producer.md:39`）。
- **upsert sink 的 key-only 删除**：`FlinkConnectorOptions.java:528` `sink.key-only-deletes.enabled`——**与 Flink `ChangelogMode.keyOnlyDeletes` 同一个概念**（见 flink 分析 §2），可作为 nop 若引入"只带 key 的删除"时的命名参照。

### 8. 声明形态与可嵌入性：SQL 客户端/API/库形态

Paimon 的"声明形态"是**表属性（WITH options）+ Catalog**，五种接入：

1. **Flink SQL DDL**（最常用）：
   ```sql
   CREATE CATALOG paimon_catalog WITH ('type'='paimon', 'warehouse'='file:/path');
   CREATE TABLE t (id INT PRIMARY KEY NOT ENFORCED, name STRING)
     WITH ('bucket'='4', 'changelog-producer'='lookup', 'merge-engine'='partial-update');
   ```
   - factory：`FlinkTableFactory.java:54` `createDynamicTableSource` / `:60` `createDynamicTableSink` → `AbstractFlinkTableFactory.java:89/:111`。
   - SPI：`META-INF/services/org.apache.flink.table.factories.Factory:16-18`。
   - **懒建表**：`FlinkTableFactory.java:66-86` `createTableIfNeeded`——当 option `AUTO_CREATE` 打开且 `FileSystemSchemaManager.latest()` 为空时自动 `createTable`（DDL-only 场景无需先建目录表）。
2. **Spark SQL / Hive**：`paimon-spark/*`、`paimon-hive`。
3. **Python**：`paimon-python`、`docs/docs/pypaimon`。
4. **Program API**：`docs/docs/program-api`（`FileStoreTable`、`ReadBuilder`、`WriteBuilder`）。
5. **Action CLI**：`paimon-flink-common/.../action/`（`MySqlSyncTableAction`、`CompactAction`、`MergeIntoAction`、`RollbackToAction`、`CreateTagAction`…），经 `META-INF/services/org.apache.paimon.factories.Factory` 注册。

**可嵌入性判定**：`paimon-core` 是纯库（无宿主依赖），`paimon-flink-common` 才依赖 Flink Table API——**分层干净**。但 SQL 面完全依附宿主，**没有"独立 SQL 客户端"**（`paimon-flink/paimon-flink-cli` 不存在；只有 Flink 自带的 `flink-sql-client`）。

### 9. 架构要点：changelog 机制 + partial-update + lookup 缓存（三个值得记的设计）

1. **changelog 是"生成策略"而非"数据模型"**。四档 `changelog-producer` 的差别只在**before image 何时、由谁产生**（§2 表 + `changelog-producer.md:39-42`）；下游永远消费"完整变更"。配套文档还给出取舍：`:109-110` "`lookup` is incompatible with `full-compaction.delta-commits`";`:127-128` "consumers with a latency budget of tens of minutes may use periodic full compaction. For lower-latency generated changelogs, evaluate `lookup`"。**"策略 + 延迟/成本代价表 + 兼容性约束"三件套，是 nop 描述 D1 语义时可以直接套用的文档格式。**
2. **partial-update 的 sequence-group：用"版本字段"实现字段级 retract**（`PartialUpdateMergeFunction.java:67` `SEQUENCE_GROUP = "sequence-group"`）：
   - 声明形：`fields.<ordering-field>.sequence-group = <protected fields>`（`partial-update.md:75,:88-89`）；支持多排序键（`:122` `fields.profile_version,profile_offset.sequence-group = name,city`）。
   - 语义：`partial-update.md:227-233` "Retract protected fields according to the group version and aggregate function / For a sequence group, a newer or equal retraction clears non-aggregated protected fields to null."
   - 删行控制：`CoreOptions.java:1205` `partial-update.remove-record-on-delete`、`:1213` `partial-update.remove-record-on-sequence-group`；约束互斥与 PK 校验在 `PartialUpdateMergeFunction.java:68-78`（"Primary key columns cannot be put in sequence-group"）、`:510-528`。
   - **核心洞察**：Paimon **不需要 RowKind retract 流**也能做到"部分字段回撤"——靠的是**存储层的版本字段 + 合并函数**。这与 nop 的 D1（无 retract 输出）**兼容**，是三项目中唯一能给 nop 提供"无 retract 的更新语义"参考的方案。
3. **lookup 缓存是"模式 + 分桶裁剪 + 刷新触发器"三件套**（§5）：`AUTO` 自动在 partial/full 间选、`getRequireCachedBucketIds()` 按 subtask 只缓存需要的桶、`lookup.dynamic-partition.refresh-interval` 独立刷新。**nop 的 `ITableLookup` 若要加缓存，直接按这三件套的形状做声明面即可，不需要新算子。**

### 10. 与 nop-stream-sql 的对照与可借鉴点

> nop-stream 无代价优化器是 owner 裁定的 Non-Goal（引 `2026-09-30-doris-nereids-optimizer-extraction-feasibility.md`）。Paimon 本身也无优化器，故本节只对照**语义面与声明面**。

| 维度 | Paimon 2.2-SNAPSHOT | nop-stream-sql（基线） | 判定 |
|---|---|---|---|
| SQL 前端 | 无（宿主提供） | EQL 自有 parser | 分工不同；**可借鉴"存储/语义层与语法层解耦"的边界** |
| 变更语义 | `changelog-producer` 四档 + 完整 changelog | D1 last-value-wins，无 RowKind | **Paimon 的四档决策表是 nop 描述 D1 的最佳措辞模板**；nop 目前等价于 `none`（消费者自己归纳） |
| partial-update / 字段级 retract | `sequence-group` + `remove-record-on-sequence-group` | 无 | **唯一与 D1 兼容的更新语义参考**；若将来要"多流合并后的字段级覆盖"，按 `fields.<col>.sequence-group` 形状做 |
| watermark | 序列化为 `schema.watermark.0.*` options | 由 `<window>`/`<sql>` 隐含（bigint epoch-millis） | **可借鉴**：把 watermark 声明升格为可持久化的模型属性 |
| idleness / watermark 对齐 | `scan.watermark.idle-timeout`、`scan.watermark.alignment.*` | 基线未提及 | 可借鉴（同 flink 分析 §10 建议） |
| lookup join | 六策略 + `lookup.cache` AUTO/FULL/MEMORY + 分桶裁剪 + 刷新触发器 | `ITableLookup` 静态维表 | **最高 ROI 借鉴点**：三件套只加声明维度、不改算子 |
| 存储层聚合 | `merge-engine=aggregation` + 23 个 field agg | 五聚合（运行期） | 不同层；但 `fields.<col>.<fn>` 的命名形状可复用 |
| 窗口 / TopN / CEP | 无 | TUMBLE / 分析窗口 / 无 SQL 层 CEP | nop 完整 |
| 状态 / 容错 | 两阶段 commit + changelog 长于 snapshot + consumer 位点 | D1/D6/D9 + nop-stream checkpoint | **"changelog 文件可独立于 snapshot 过期"** 是 nop 若做持久化 changelog 时的关键设计 |
| 双目标（RDBMS 方言 / 流管线） | 无 | 有 | nop 差异化保留 |
| 代价优化器 | 无 | 无（Non-Goal） | 一致 |

**三个可低成本采纳的点（按 ROI 排序）**

1. **`lookup.cache` 三件套声明位**：给 `.stream.xml` 的 lookup join 增加可选 `cacheMode="none|partial|full"` + `refreshInterval`，实现仍走 `ITableLookup`——纯声明面扩展，零算子改动。
2. **changelog 语义的"四档 + 代价表"文档格式**：把 D1（last-value-wins）写成 Paimon 式的表：`谁产生 before image / 消费者需要做什么 / 延迟代价`——比现在"非 append-only、非 retract"的否定式描述更可执行。
3. **watermark 作为可序列化模型属性**：若 nop 需要"跨环境保持同一时间语义"，照 Paimon 的 `schema.watermark.0.{rowtime,strategy.expr,strategy.data-type}` 三键形状把 watermark 提到 `.stream.xml` 顶层属性。

**不可借鉴（记录否决理由）**

- LSM merge-tree / compaction / changelog 文件格式：属存储层，与 nop 的算子内状态无关。
- `changelog-producer` 的四档实现（input/lookup/full-compaction 双写）：nop 无湖仓存储，无法复用，只借**措辞格式**。
- 多 Flink 版本模块矩阵（11 个版本）：nop 单宿主，不需要。

## 对照 nop-stream-sql 已交付子集（必填小节）

| 基线条目 | Paimon 对应物 | 差异 / 可借鉴 |
|---|---|---|
| 入口 `.stream.xml` `<sql sinkBean>`，EQL 与 ORM 共用 parser | 无自有 SQL；`CREATE TABLE ... WITH ('connector'='paimon', ...)` | nop 有声明面，Paimon 只有 options 面；**options 的 key 命名法（`<域>.<对象>.<属性>`）值得借鉴**（`fields.<col>.sequence-group`、`lookup.cache`、`changelog-producer.ignore-delete`） |
| 内嵌 schema，9 受管类型闭集 | schema 存 `schema/` 目录，由 Flink `ResolvedSchema` 往返；非物理列降级为 options | **Paimon 的"非物理列 = options"做法**（`FlinkCatalogPropertiesUtil`）提示 nop：watermark/computed column 可同样升格为显式属性 |
| FROM 表名 = 数据源 bean 名 | Catalog + 表路径 + `bucket`/`partition` 定位 | nop 一层命名，Paimon 多层；各适配其场景 |
| SELECT 投影 / WHERE / 五聚合 / GROUP BY | 无查询能力（宿主执行） | 无对照 |
| `TUMBLE(t, INTERVAL)` 伪表函数 | 无窗口 | 无对照；nop 完整 |
| 双流等值 join（watermark 补齐） | 无流式 join | 无对照 |
| UNION ALL（bag 语义） | 无 | 无对照 |
| 静态维表 lookup join（`ITableLookup` → `IJdbcTemplate`/`IBatchLoader`） | 六策略 + `lookup.cache` AUTO/FULL/MEMORY + 分桶裁剪 + 动态分区刷新 | **最强可借鉴项**：nop 是"无缓存静态查库"，Paimon 给了完整的缓存声明面（三件套），可分阶段引入 |
| 无窗口持续 GROUP BY | 无（存储层 `merge-engine=aggregation` 是字段级合并，非分组聚合） | **层次不同**：nop 是按 key 分组归约，Paimon 是按主键合并行；不可混同，但 `fields.<col>.<agg>` 命名可复用 |
| 分析窗口 OVER（`ROW_NUMBER`, `FRAME_SLIDING_AGG`） | 无 | 无对照 |
| 不支持清单（ORDER BY/LIMIT、非等值 join、DATE/TIMESTAMP/DECIMAL、DISTINCT、HAVING/CTE、全局聚合、FULL window join、UNION DISTINCT、Flink SQL 方言） | 整体无查询层 | nop 的清单是**编译器契约**，Paimon 无从提供对照 |
| D1 last-value-wins | `changelog-producer=none` 下消费者自行归纳；`deduplicate` merge-engine 亦为 last-wins | **语义近亲**：Paimon `deduplicate` + `sequence.field`（`CoreOptions.java:1164`）≈ nop 的 last-value-wins；`sequence.field.sort-order`（:1172）是 nop 可加的排序位 |
| D6 事件时间 | watermark 由宿主定义并序列化为 options | nop 应把 watermark 提为显式模型属性（§10 建议 3） |
| D9 allowedLateness 已放行 | 无 lateness（由宿主算子负责） | nop 完整于 Paimon |
| D10 accumulationMode / D11 trigger fail-fast | 无 | nop fail-fast 与业界一致 |
| EQL AST → `StreamSqlCompiler` → 流模型 XML | 无编译器；DDL → `SchemaManager.createTable` | 无对照；但 Paimon 的"options 即声明"与 nop 的"模型 XML 即产物"同属**可持久化声明**路线 |
| 参数化算子面三注册表（schemas/aggregators/joins） | `CoreOptions`（351 选项）+ `aggregate/factory`（23 聚合）+ `lookup/`（缓存/策略） | **Paimon 的"域→工厂→实例"注册形状与 nop 三注册表同构**，可对照命名 |
| 双目标（RDBMS 方言 或 流管线） | 无 | nop 差异化保留 |
| 无代价优化器 = Non-Goal | 无优化器 | 一致 |

## 引用出处

路径根：`/Users/abc/sources/streaming-sql/paimon`（行号来自本次实际读取；未标行号者为整文件/目录存在性核对）。

**已逐条验证存在的引用（共 58 条）**

- `README.md:20-22`、`LICENSE`、`pom.xml:34`（`2.2-SNAPSHOT`）
- `paimon-api/src/main/java/org/apache/paimon/CoreOptions.java:121,136,736,1104,1113,1122,1131,1138,1164,1172,1205,1213,1229,1252,1276,1324,1350,1358,5331-5340`
- `paimon-core/src/main/java/org/apache/paimon/Changelog.java`
- `paimon-core/src/main/java/org/apache/paimon/utils/ChangelogManager.java:54,60,116,141,149`
- `paimon-core/src/main/java/org/apache/paimon/table/source/snapshot/ChangelogFollowUpScanner.java:33,43`
- `paimon-core/src/main/java/org/apache/paimon/table/source/splitread/IncrementalChangelogReadProvider.java`
- `paimon-core/src/main/java/org/apache/paimon/table/ExpireChangelogImpl.java`、`paimon-core/src/main/java/org/apache/paimon/operation/ChangelogDeletion.java`、`paimon-core/src/main/java/org/apache/paimon/utils/CompactedChangelogPathResolver.java`
- `paimon-core/src/main/java/org/apache/paimon/mergetree/compact/{ChangelogMergeTreeRewriter,FullChangelogMergeTreeCompactRewriter,LookupMergeTreeCompactRewriter,FullChangelogMergeFunctionWrapper,LookupChangelogMergeFunctionWrapper,ChangelogResult,PartialUpdateMergeFunction,FirstRowMergeFunction}.java`（`PartialUpdateMergeFunction.java:67,68-78,510-528`）
- `paimon-core/src/main/java/org/apache/paimon/mergetree/compact/aggregate/factory/`（23 个 `Field*AggFactory`）
- `paimon-core/src/main/java/org/apache/paimon/schema/SchemaManager.java`
- `paimon-core/src/main/java/org/apache/paimon/consumer/{Consumer,ConsumerManager}.java`
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/FlinkTableFactory.java:39,54,60,66-86`
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/AbstractFlinkTableFactory.java:77,89,111,122`
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/FlinkCatalogFactory.java:34`
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/FlinkCatalog.java:174,601-609,880-892,896-908,1014-1017,1115,1184-1187`
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/utils/FlinkCatalogPropertiesUtil.java:43-47,54,57,96-107,214,227`
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/utils/FlinkDescriptorProperties.java:50-60,72,82-87`
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/FlinkConnectorOptions.java:48,57,67,76,98,115,126,132,138,147,155,163,171,178,185,196,204,214,225,233,241,248,255,261,268,274,298,306,324,331,340,349,461,469,528,628-641`
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/lookup/{FileStoreLookupFunction,LookupTable,FullCacheLookupTable,LookupFileStoreTable,LookupStreamingReader,LookupDataTableScan,AsyncLookupFunctionWrapper,LookupRuntimeProviderFactory}.java`（`FileStoreLookupFunction.java:89,232-277,574`、`FullCacheLookupTable.java:80,452`）
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/sink/{CommitterOperator,Committer,StoreCommitter,StoreMultiCommitter,AutoTagForSavepointCommitterOperator}.java`（`CommitterOperator.java:46,57,196`）
- `paimon-flink/paimon-flink-common/src/main/java/org/apache/paimon/flink/compact/changelog/`（`ChangelogCompactCoordinateOperator`、`ChangelogCompactSortOperator`、`ChangelogCompactTask`、`CompactedChangelogReadOnlyFormat` 等）
- `paimon-flink/paimon-flink-common/src/main/resources/META-INF/services/org.apache.flink.table.factories.Factory:16-18`
- `paimon-flink/paimon-flink-common/src/main/resources/META-INF/services/org.apache.paimon.factories.Factory`
- `paimon-flink/` 目录列表（`paimon-flink-1.16 … 1.22 / 2.0 / 2.1 / 2.2 / paimon-flink1-common / paimon-flink2-common`）
- `docs/docs/primary-key-table/changelog-producer.md:39-42,49-56,76,90,102-110,115,122,127-128,146`
- `docs/docs/primary-key-table/merge-engine/partial-update.md:25,29,35,69,71,75,88-89,104,119,122,135,140,158-159,186,190,193,207-214,217,220,227-239`
- `docs/docs/primary-key-table/merge-engine/{aggregation.mdx,first-row.md,index.md}`
- `docs/docs/flink/sql-lookup.mdx:33,35-44`（含 6 策略表与 DDL 示例）
- `docs/docs/primary-key-table/`、`docs/docs/program-api`、`docs/docs/pypaimon`（目录存在性）
- `paimon-spark/`（`paimon-spark-3.2 … 4.1` + `paimon-spark-common` 目录列表）

**未在本仓库验证（明确标注）**

- Paimon 官方文档站点措辞（`docs/docs/**` 为仓库内 md/mdx 源文件，已读取本地内容；**其发布版网页未访问**）。
- 除 Flink/Spark 外的其他 SQL 引擎接入（Trino/Presto 等）：**本仓库无对应模块，未验证**。
- `paimon-lumina` / `paimon-mosaic` 模块的用途：**只核对目录存在，未读源码，不作结论**。
- changelog 四档的**实际延迟/吞吐数值**：文档只给定性描述（"tens of minutes" / "lower-latency"），**无本仓库内的基准数据**。
