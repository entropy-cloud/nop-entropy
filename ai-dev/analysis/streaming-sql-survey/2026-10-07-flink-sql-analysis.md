# Flink streaming SQL 能力分析（Table/SQL 层）

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项），仅覆盖 `flink-table/` 下 Table/SQL 层（api / planner / parser / client），不覆盖 runtime/checkpoint（已有 `nop-stream/01-flink-source-audit.md`、`09a~09e`、`10-flink2.3-quality-verdict.md`）
> Conclusion: 最值得借鉴两点——(1) 把 **changelog 语义做成贯穿 planner 的可推导 trait**（`ChangelogMode` + `FlinkChangelogModeInferenceProgram`），让"下游需要什么变更形态"成为编译期静态属性而非运行期约定；(2) **窗口 TVF 化**（`TUMBLE/HOP/CUMULATE/SESSION` 作为返回 `window_start/window_end/window_time` 的表函数），使窗口退化为普通关系算子从而可被通用规则复用。

## Context

Flink 是 nop-stream 对标的主参照系，但既往分析集中在 runtime / checkpoint / window / CEP 内核，**SQL 声明面与 planner 结构基本空白**。nop-stream-sql 已交付 `<sql sinkBean>` EQL 子集（见基线），需要回答：Flink SQL 在"声明面 → 可执行体"这条链上做了哪些设计，其中哪些对 nop-stream-sql 的后续演进是**语义/声明面**上的可借鉴资产（而非"没有代价优化器"这类 owner 已裁定的 Non-Goal）。依据本地源码 `/Users/abc/sources/flink`（HEAD `c0f8d1a1e09`，release 2.3.0，Apache-2.0）。

## 项目概览

- **定位**：批流一体的分布式计算引擎；Table & SQL 是其声明式层（关系代数 → 优化 → 执行图）。
- **语言 / 许可证**：Java + Scala（planner 大量 Scala），Apache License 2.0（`LICENSE`）。
- **演进状态**：本地 checkout 为 `2.3.0` release commit（`pom.xml:17` `<version>2.3.0</version>`）；Table 层处于 V2 稳定期，新增 materialized table / model / procedure / vector-search / ML-predict 等 DDL 面。
- **SQL 能力入口**（模块路径，均在 `flink-table/` 下，见 `flink-table/pom.xml` module 列表）：
  - `flink-sql-parser/` — JavaCC+FMPP 生成的 SQL 语法（含全部 DDL 方言）。
  - `flink-table-common/` — 对外稳定类型：`Schema`、`WatermarkSpec`、`ChangelogMode`、connector lookup cache SPI。
  - `flink-table-api-java/` — `TableEnvironment` 命令面 + `ConfigOption` 配置面。
  - `flink-table-planner/` — 校验、优化（Hep/Volcano program 链）、物理规则、ExecNode、codegen。
  - `flink-sql-client/`、`flink-sql-gateway/`、`flink-sql-jdbc-driver/` — 三种嵌入/接入形态。

## 十维度分析

### 1. SQL 前端：parser/planner 与扩展语法

- **Calcite 版本 = 1.36.0**，集中在 `flink-table/pom.xml:81` `<calcite.version>1.36.0</calcite.version>`（`flink-sql-parser`、`flink-table-calcite-bridge` 均引该属性）。
- **双 parser 策略**：
  - `flink-sql-parser` 用自己的 FMPP 模板重新生成 parser（`flink-sql-parser/src/main/codegen/config.fmpp:17-34` 明写"allow clients to extend Calcite's SQL parser…insert new parser method implementations"），数据字典在 `codegen/data/Parser.tdd`（关键字 `WATERMARK`:272、`HOP`:384、`SESSION`:509、`TUMBLE`:587），语法规则在 `codegen/templates/Parser.jj`（9132 行）与 `codegen/includes/parserImpls.ftl`（3936 行，全部 DDL）。
  - Calcite 自带 `Parser.jj` 仍被复用为基座（`Parser.jj:77` `import org.apache.calcite.sql.SqlMatchRecognize;`；`:2921-2929` `FOR SYSTEM_TIME AS OF` 子句）。
- **planner 插件面**：`flink-table-calcite-bridge/pom.xml:31` 描述 "This module contains the Calcite dependencies for writing planner plugins (e.g. SQL dialects)"——即外部可注册 dialect / rule。
- **Calcite 被 shade 进 planner**：`flink-table-planner/pom.xml:63` 注释 "Mainly used for calcite-core, relocated in org.apache.flink.calcite.shaded"（`:372-400` relocation 段，Calcite 自身 relocation 当前被注释，Guava/commons 等仍 shade）。这使 planner 与用户 classpath 上的 Calcite 版本解耦——**嵌入式部署的关键工程决策**。
- **扩展语法实例**：`MATCH_RECOGNIZE`（`Parser.jj:3063-3180` 完整规则，含 `ROWS PER MATCH` / `AFTER MATCH SKIP`）、window TVF（`Parser.jj:7174-7184`）、导航函数 `LAST/FIRST/PREV/NEXT/FINAL`（`Parser.jj:7856-7934`）。

### 2. Table & Changelog 模型

- **`ChangelogMode`**（`flink-table-common/.../connector/ChangelogMode.java`）是变更语义的唯一闭集：
  - `INSERT_ONLY`（:38-39）、`UPSERT`（:41-47，`keyOnlyDeletes=true`）、`UPSERT_WITH_FULL_DELETES`（:49-55）、`ALL`（:57-63，含 `UPDATE_BEFORE`）；快捷构造 `insertOnly()` :76、`upsert()` :84、`all()` :103。
  - `keyOnlyDeletes` 是一个精妙的小设计：告诉 sink "DELETE 只带主键"，从而可省掉 full-row 物化（对应 `table.exec.sink.upsert-materialize` 的取舍，见 `ExecutionConfigOptions.java:145`）。
- **DDL**：
  - `Schema`（`flink-table-common/.../api/Schema.java:78` `watermarkSpecs` 字段）承载 column / computed column / metadata column / primary key / watermark / index 六类信息。
  - 语法侧：`parserImpls.ftl:1118` 调 `Watermark(context)`、`:1122` `void Watermark(...)`、`:1164` `ComputedColumn(...)`、`:1193` `MetadataColumn(...)`、`:1408` `ColumnConstraint(...)`、`:1432` `TableConstraint(...)`；语句节点 `flink-sql-parser/.../ddl/SqlWatermark.java:39`（javadoc 示例 `WATERMARK FOR ts AS ts - INTERVAL '5' SECOND`）、`ddl/table/SqlCreateTable.java`。
  - **一个表只允许一条 watermark**：`parserImpls.ftl:1135` `multipleWatermarksUnsupported()`。
- **Catalog**：`flink-table-common/.../catalog/Catalog.java` + `CatalogTable/View/Partition/Model/MaterializedTable`，`flink-table-api-java/.../catalog/FunctionCatalog.java` 独立管理函数命名空间；`TableEnvironment` 提供 `createCatalog/loadModule/createTemporaryTable/createModel`（`TableEnvironment.java:400-1009`）。

### 3. 时间语义

- **WATERMARK 定义**：`WATERMARK FOR <rowtime> AS <expr>`，解析成 `WatermarkSpec(rowtimeAttribute, watermarkExpression)`（`WatermarkSpec.java:36`，`getRowtimeAttribute()` :60）。表达式逐行求值、由框架周期性发射**最大值**（`Schema.java:543-551` 的 javadoc 明确"periodically emit the largest generated watermark / smaller than last emitted → no new watermark"）。
- **rowtime/procime 双时间属性**由类型系统携带（`SqlWindowTableFunction.java:76-80` 的 `window_time` "TIMESTAMP type with time attribute metadata"）。
- **迟到数据**：Table 层窗口/interval join 的 `allowedLateness` 实际**写死为 0**——`StreamExecIntervalJoin.java:400` 显式 `0L, // allowedLateness`。即 Flink SQL 的窗口语义是"watermark 越过 window end 即丢弃迟到记录"，与 nop-stream D9（`allowedLateness` 已放行、可配置）相比，**nop 侧更宽**。
- **idleness**：`ExecutionConfigOptions.java:67-75` `table.exec.source.idle-timeout`（默认 0=关闭），"source will be marked as temporarily idle… ignore watermarks from this source while it is idle"。
- **时区**：`TableConfigOptions.java:105-106` `table.local-time-zone`；窗口聚合把 `ZoneId shiftTimeZone` 作为 ExecNode 构造参数（`StreamExecWindowAggregateBase.java:75`）——日历窗口（MONTH/YEAR 级）与本地时区绑定。
- **状态空闲清理**：`ExecutionConfigOptions.java:50-57` `table.exec.state.ttl`。
- **mini-batch 迟到容忍**：`ExecutionConfigOptions.java:677` `TABLE_EXEC_MINIBATCH_ALLOW_LATENCY`。

### 4. 窗口与聚合

- **窗口类型全集（window TVF）**：`TUMBLE` / `HOP` / `SESSION` / `CUMULATE` 四个表函数 + group-window 遗留形态。
  - `flink-table-planner/.../functions/sql/SqlWindowTableFunction.java:53-60` javadoc："`TUMBLE`, `HOP`, `CUMULATE` and `SESSION`"，并说明"extends Calcite's SqlWindowTableFunction, to support additional `window_time` time attribute column"。
  - 返回行结构：`window_start` / `window_end` / `window_time` 三列（`SqlWindowTableFunction.java:70-80` 与 `:153`）。
  - `SqlTumbleTableFunction.java:39-41`、`SqlCumulateTableFunction.java:39-42`（`CUMULATE` 为 Flink 独有，Calcite 无）。
  - 操作数签名 `TUMBLE(TABLE t, DESCRIPTOR(timecol), interval[, offset])`（`SqlTumbleTableFunction.java:64-67` `getAllowedSignatures`）。
- **offset 参数**：TUMBLE/HOP/CUMULATE 支持第 4 参 `PARAM_OFFSET`（`SqlTumbleTableFunction.java:46`）。
- **窗口规则**：`rules/physical/stream/PullUpWindowTableFunctionIntoWindowAggregateRule.java`、`ExpandWindowTableFunctionTransposeRule.java`、`rules/logical/ProjectWindowTableFunctionTransposeRule.java`——TVF 与聚合的上提/转置是常规优化，佐证"窗口=普通算子"的收益。
- **聚合函数集**（`functions/sql/FlinkSqlOperatorTable.java:1143-1156`）：`SUM/SUM0/COUNT/COLLECT/MIN/MAX/AVG/STDDEV(_POP/_SAMP)/VARIANCE(_POP/_SAMP)/INTERNAL_WELFORD_M2/SINGLE_VALUE/APPROX_COUNT_DISTINCT/ARRAY_AGG`；`:1001-1009` `FIRST_VALUE/LAST_VALUE/LISTAGG`；`:1278-1285` 窗口函数 `RANK/DENSE_RANK/ROW_NUMBER/CUME_DIST/PERCENT_RANK/NTILE/LEAD/LAG`。
- **TopN**：`rules/physical/stream/StreamPhysicalRankRule.scala`（普通 TopN）、`StreamPhysicalWindowRankRule.scala`（窗口 TopN）、`StreamPhysicalWindowDeduplicateRule.scala`（首末行去重）、`rules/logical/CorrelateSortToRankRule.java`（相关子查询 → Rank）。
- **DISTINCT 聚合**：`rules/logical/FlinkAggregateExpandDistinctAggregatesRule.java:74`（"expands distinct aggregates (such as COUNT(DISTINCT x))"，含"approximate + accurate distinct 同参数"的显式报错 :131）。
- **GROUPING SETS/CUBE/ROLLUP**：`plan/rules/FlinkStreamRuleSets.scala:305` 与 `FlinkBatchRuleSets.scala:298` 均挂 `DecomposeGroupingSetsRule.INSTANCE`。
- **UDAF / TABLE AGGREGATE**：`flink-table-common/.../functions/AggregateFunction.java`、`TableAggregateFunction.java`、`ChangelogFunction.java`（UDAF 可声明 `emitChanges` 返回 `ChangelogMode`——**用户自定义函数也接入变更模型**）；物理规则 `StreamPhysicalGroupTableAggregateRule.scala`、`StreamPhysicalGroupWindowTableAggregateRule.scala`。
- **FILTER / OVER**：`rules/physical/stream/StreamPhysicalOverAggregateRule.scala`、`WindowAggregateReduceFunctionsRule.java`（窗口聚合的 reduce-functions 展开）。

### 5. Join

| 类型 | planner 落点 | 备注 |
|---|---|---|
| interval join（事件/处理时间） | `plan/nodes/exec/stream/StreamExecIntervalJoin.java:361-404`（`ProcTimeIntervalJoin` / `RowTimeIntervalJoin`） | 时间上界/下界在 `IntervalJoinSpec.WindowBounds`；事件时间 `allowedLateness=0`（:400） |
| temporal join（版本表） | `nodes/physical/stream/StreamPhysicalTemporalJoin.scala`、`rules/physical/stream/StreamPhysicalTemporalJoinRule.java`、`rules/logical/EventTimeTemporalJoinRewriteRule.java`、`TemporalJoinRewriteWithUniqueKeyRule.java` | `FOR SYSTEM_TIME AS OF` 语法（`Parser.jj:2921-2929`） |
| lookup join | `nodes/physical/stream/StreamPhysicalLookupJoin.scala`、`plan/nodes/exec/stream/StreamExecLookupJoin.java`、`plan/utils/LookupJoinUtil.java` | 见下 |
| 常规 equi join | `StreamPhysicalJoinRule.java` / `StreamPhysicalMultiJoinRule.java` | 流上 regular join 需 state TTL 收敛 |
| 广播 join | `plan/optimize/StreamPhysicalDeltaJoinForceValidator.java`、`rules/physical/stream/DeltaJoinRewriteRule.java` | delta join（v2 新增） |

- **非等值 join**：可在 `JoinConditionTypeCoerceRule` / `SimplifyJoinConditionRule` 后落地为 calc+join，但**流上 interval join 才是范围条件的正解**；常规流 join 的非等值条件由 `StreamPhysicalJoinRuleBase` 约束。
- **lookup join 的声明面 = hint + 表属性**：
  - hint：`hint/JoinStrategy.java:51` `LOOKUP("LOOKUP")`，选项定义在 `LookupJoinHintOptions.java:39`（`table`）/`:45`（`async`）/`:63`（`capacity`）/`:77`（`retry-predicate`）；校验在 `hint/FlinkHintStrategies.java:118-247`。
  - 表属性：`flink-table-common/.../connector/source/lookup/LookupOptions.java:38` `lookup.cache`、`:57` `lookup.partial-cache.expire-after-access`、`:63` `expire-after-write`、`:69` `cache-missing-key`、`:76` `max-rows`。
  - 缓存实现：partial（`:425` `partialCachingLookupProvider.getCache()`）与 full（`LookupJoinUtil.java:448` `LookupFullCache`，`:566` "lookup 'FULL' cache must be bounded"）；full cache 还支持 `CacheReloadTrigger`（`lookup/cache/trigger/PeriodicCacheReloadTrigger.java`、`TimedCacheReloadTrigger.java`）与 InputFormat 全量装载（`:579-584` `InputFormatCacheLoader`）。

### 6. CEP / 模式匹配

- **Flink CEP SQL = `MATCH_RECOGNIZE`，流批两侧都有 physical 节点**：
  - 解析：`Parser.jj:3063-3180`（`<MATCH_RECOGNIZE>` :3089，`ROWS PER MATCH` :3109/3113，`AFTER MATCH SKIP` :3124-3151）。
  - 逻辑：`plan/logical/MatchRecognize.scala` → `FlinkLogicalMatch.scala` → `StreamPhysicalMatch.scala` / `BatchPhysicalMatch.java`；规则 `StreamPhysicalMatchRule.scala:30`、`BatchPhysicalMatchRule.java`。
  - 执行节点：`plan/nodes/exec/stream/StreamExecMatch.java`、`exec/spec/MatchSpec.java:84`（`interval` 字段 = `WITHIN` 子句，null 表示未定义）。
  - codegen：`codegen/MatchCodeGenerator.scala`（832 行），`:339` "Flink does not support physical offsets within partition."（`PREV/NEXT` 在 partition 内的显式限制）。
- **时间属性专用算子**：`plan/utils/MatchUtil.scala` 识别 `MATCH_PROCTIME` / `MATCH_ROWTIME`（`isMatchTimeIndicator`），`SqlStdOperatorTable.FINAL` 包裹（`isFinalOnMatchTimeIndicator`）。
- **实现载体是 flink-cep 的 NFA**，不在本分析范围（已有 `nop-stream/06-cep-comparison.md`、`09d` 覆盖）；本层的可借鉴点仅是**声明面**：pattern/measures/after/subsets/interval 被压成一个 `MatchSpec` 纯数据对象（`MatchUtil.createMatchSpec`），便于序列化与版本化。

### 7. 状态与容错（Table 层挂钩点）

仅列 Table 层与状态/容错的**契约面**，细节见既有分析：

- 状态 TTL：`ExecutionConfigOptions.java:50` `table.exec.state.ttl`；hint 侧 `hint/StateTtlHint.java`（可对单条查询覆盖）。
- 上游变更去重 / 幂等：`table.exec.source.cdc-events-duplicate`（:79-81）。
- sink 变更物化：`table.exec.sink.upsert-materialize`（:145，`AUTO/ALWAYS/NEVER`）+ 自适应阈值（:165/:185/:207）——**在 sink 侧按需补 UPDATE_BEFORE**，是"ChangelogMode 推导"的落地收益之一。
- sink keyed shuffle：`table.exec.sink.keyed-shuffle`（:232）。
- mini-batch：`MINI_BATCH` 规则组（`FlinkStreamProgram.scala:328-338` `FlinkMiniBatchIntervalTraitInitProgram` + `MINI_BATCH_RULES`）。
- checkpoint/exactly-once 协议本身在 `flink-runtime`，不重复。

### 8. 声明形态与可嵌入性

四种形态，全部围绕"同一套 planner"：

1. **进程内嵌**：`TableEnvironment.create(EnvironmentSettings)` / `create(Configuration)`（`TableEnvironment.java:101/:128`），`executeSql`（`TableEnvironmentImpl.java:940`）、`sqlQuery`（:921）、`explainSql`（:877）。
2. **SQL Client（CLI 进程）**：`flink-sql-client/`（`org.apache.flink.table.client.SqlClient`）。
3. **SQL Gateway（长驻服务 + REST/session）**：`flink-sql-gateway/`（`SqlGateway`、`service/`、`rest/`）。
4. **JDBC Driver**：`flink-sql-jdbc-driver/`（`org.apache.flink.table.jdbc.FlinkDriver` 等），标准 `java.sql` 接口接入。
5. **编译计划外置**：`TableEnvironment` 支持 `CompiledPlan` / `PlanReference`（`PlannerBase.scala:195-213` 从文件/字符串读回 `ExecNodeGraph`）——**planner 产物可序列化、可跨版本回放**，这是 nop-stream-sql "流模型 XML 即产物" 的同构思路（nop 已具备，Flink 走 JSON plan 路线）。

嵌入障碍提示：planner shade 了 Calcite/Guava（`flink-table-planner/pom.xml:372-400`），但 planner 本身体积大、依赖 Janino + Scala library，**作为轻量库嵌入成本高**；`flink-table-planner-loader` 模块即为此存在（`flink-table/pom.xml` module 列表）。

### 9. 架构要点

链路：**SQL 文本 → `SqlParser`（FMPP 生成）→ `SqlNode` → `SqlValidator`（Flink 扩展的 FlinkSqlValidator）→ `SqlNodeToOperationConversion`（`operations/SqlNodeToOperationConversion.java:151`）→ `Operation` → `PlannerBase.translateToRel`（`PlannerBase.scala:248`）→ `RelNode` → `optimize`（:394/:401）→ `translateToExecNodeGraph`（:412）→ `ExecNodeGraph` → `translateToPlan`（:449）→ `Transformation` DAG。**

值得记的三个设计：

1. **Program 链而非扁平规则集**。`plan/optimize/program/FlinkStreamProgram.scala:32-44` 把流优化切成 14 个具名阶段（`subquery_rewrite` / `temporal_join_rewrite` / `decorrelate` / `default_rewrite` / `predicate_pushdown` / `join_reorder` / `multi_join` / `project_rewrite` / `logical` / `logical_rewrite` / `time_indicator` / `physical` / `physical_rewrite`），每个阶段内部再套 Hep（`FlinkHepRuleSetProgram`，可选 `RULE_SEQUENCE`/`RULE_COLLECTION` 与 `BOTTOM_UP`/`TOP_DOWN`）或 Volcano（`FlinkVolcanoProgram`，:262-268 / :294-300）。**"哪类优化属于哪个阶段"是显式命名的**，比"一个大规则池"更利于裁剪与教学。
2. **changelog 推导作为独立 program**。`FlinkStreamProgram.scala:303-359` 的 `PHYSICAL_REWRITE` 阶段顺序固定：`FlinkMarkChangelogNormalizeProgram` → watermark transpose → **`FlinkChangelogModeInferenceProgram`**（:327）→ minibatch → duplicate-changes → physical rewrite。`FlinkChangelogModeInferenceProgram.scala:54-94` 分三步：`satisfy ModifyKindSet` → `satisfy UpdateKind`（在 `ONLY_UPDATE_AFTER` 与 `BEFORE_AND_AFTER` 间试选 :70-77）→ `satisfy DeleteKind`（sink 侧先试 `DELETE_BY_KEY` 再 `FULL_DELETE` :90-94）。源码注释（:310-318）解释了**为什么 transpose 必须排在推导之前**（投影掉 metadata 列可能让 ChangelogNormalize 变冗余；条件下推可能把 UPDATE_BEFORE 降级为 DELETE）——这是全篇最值得抄的一段注释。
3. **时间指示符是独立 program**。`TIME_INDICATOR`（:291 `FlinkRelTimeIndicatorProgram`）在 logical 与 physical 之间单独跑一轮，把 rowtime/procime 类型收敛——时间语义被**从类型系统里"最后清洗一次"**，而不是散落在规则里。

### 10. 与 nop-stream-sql 的对照与可借鉴点

> 说明：nop-stream-sql **无代价优化器是 owner 裁定的 Non-Goal**（同 `2026-09-30-doris-nereids-optimizer-extraction-feasibility.md` 的口径 B 裁定：优化器语义层不可移植）。本节只对照**语义面与声明面**。

| 维度 | Flink SQL | nop-stream-sql（基线） | 判定 |
|---|---|---|---|
| 变更语义 | `ChangelogMode` 闭集 + 编译期推导 | D1 last-value-wins，无 RowKind | nop 为**语义降级**（已裁定），非缺陷；但 `keyOnlyDeletes` 这种"只带 key 的 DELETE"标记值得在将来引入 sink 契约时参考 |
| 窗口 | window TVF，返回 `window_start/window_end/window_time` | `TUMBLE(t, INTERVAL)` 伪表函数，作用于投影/GROUP BY 前 | **方向一致**；Flink 的 TVF 返回显式窗口列，nop 可考虑同样暴露 `window_start/window_end` 作为可投影列 |
| 窗口类型 | tumble/hop/session/cumulate + offset | 仅 TUMBLE | nop 缺口，但是否补属于 roadmap 裁量 |
| 迟到 | Table 层 `allowedLateness=0`（`StreamExecIntervalJoin.java:400`） | D9 `allowedLateness` 已放行 | **nop 更宽**；Flink 用户实际靠 sink 侧容忍与 state TTL 补 |
| 时区 | `table.local-time-zone` 注入窗口 | 时间列 bigint epoch-millis，无时区概念 | nop 需要时仅需"窗口边界用 UTC 固定"这一条裁决即可，不必复刻 ZoneId |
| idleness | `table.exec.source.idle-timeout` | 未在基线提及 | 可借鉴为一个显式配置项（否则单源空闲会卡住 watermark） |
| lookup join | hint(`LOOKUP`) + `lookup.cache` 全/partial + reload trigger + async | `ITableLookup`→`IJdbcTemplate`/`IBatchLoader`，静态维表 | nop 是"静态维表"最简形态；**可借鉴点是把 cache 语义（NONE/PARTIAL/FULL + 过期策略）作为 lookup 声明面的可选维度**，而非新算子 |
| 五聚合 | 20+ 内建聚合 + UDAF | 五聚合 + `StreamSqlAggregations` 目录 | Flink 的 `ChangelogFunction`（UDAF 声明变更输出）与 nop 的聚合注册表形态可对接 |
| 表达式子集 | 全量 Calcite 表达式 | 标量子集，子集外 fail-fast | nop 的 fail-fast 是**正确**的（不静默近似），Flink 的宽松度来自 Calcite 多年语义补丁，不必追赶 |
| 声明载体 | DDL SQL 文本 | `.stream.xml` 元素 + CDATA | nop 单一 canonical 模型是差异化（见 `10-flink2.3-quality-verdict.md` 的 API 维度裁定） |
| 产物可序列化 | `CompiledPlan` / `ExecNodeGraph` JSON | 流模型 XML（回读 → `StreamModelDslBuilder`） | 同构思路，nop 已具备 |

**三个可低成本采纳的点（按 ROI 排序）**

1. **具名阶段化编译管线**：把 `StreamSqlCompiler` 的"parse → validate → 手术"再显式拆成带名字的阶段（哪怕只有 3-4 段），错误定位与后续加规则会显著容易——零语义变化。
2. **`table.exec.source.idle-timeout` 等价物**：单数据源空闲时的 watermark 推进策略，做成 `.stream.xml` 顶层可选属性。
3. **window TVF 的返回列**：让 `TUMBLE` 查询可投影 `window_start/window_end`，与 Flink 对齐后未来做 SQL 双目标对照测试更省事。

**不可借鉴（记录否决理由）**

- 代价优化器 / Volcano / metadata 体系：owner 裁定 Non-Goal，且 `2026-09-30-doris-nereids-optimizer-extraction-feasibility.md` 已裁定"语义层不可移植"。
- `ChangelogMode` 推导：依赖 retract/upsert 输出，与 D1 直接冲突。
- Calcite shade + Janino planner 嵌入：与 nop-stream-sql 的"轻量构建期编译器"定位不符。

## 对照 nop-stream-sql 已交付子集（必填小节）

| 基线条目 | Flink 对应物 | 差异 / 可借鉴 |
|---|---|---|
| 入口 `.stream.xml` `<sql sinkBean>` | `CREATE TABLE` + `INSERT INTO` DDL 文本 | Flink 用纯 SQL 文本承载 schema；nop 把 schema 抽到 `<schemas>`，**类型闭集（9 受管类型）在 nop 是显式 fail-fast，在 Flink 是 DataType 全集 + 隐式转换** |
| EQL 语法（与 ORM 共用 parser） | FMPP/JavaCC 独立 SQL parser（`flink-sql-parser`） | 单一 parser 复用是 nop 的口径优势（`source-of-truth` 一致）；Flink 选择"重造 parser 模板"是因为要插 DDL，nop 若未来加 DDL 可参考 `config.fmpp` 的四条扩展点 |
| FROM 表名 = 数据源 bean 名 | Catalog 三级命名 + connector factory | Flink 走 SPI（`DynamicTableSourceFactory`），nop 走 bean 名——**等价的"名字→实现"间接层**，nop 更轻 |
| SELECT/WHERE/五聚合/GROUP BY | 同名能力，表达式集大得多 | nop 子集是闭集 fail-fast，符合 owner 口径 |
| `TUMBLE(t, INTERVAL)` 伪表函数 | `TUMBLE(TABLE t, DESCRIPTOR(col), INTERVAL [, offset])` | **语法形态不同**：Flink 先 TVF 后 SELECT；nop 是 `FROM TUMBLE(...)` 内联——语义等价，nop 需自证 GROUP BY 与窗口的绑定顺序 |
| 双流等值 join（INNER/LEFT/RIGHT/FULL，window join 补齐于 watermark） | `StreamPhysicalWindowJoinRule` + interval join + regular join | nop 的"watermark 补齐"与 Flink window join 同构；Flink 额外有 interval/temporal/lookup 三种时间 join |
| UNION ALL（bag 语义） | `StreamPhysicalUnionRule.scala` | 一致；Flink 的 UNION DISTINCT 依赖 `Distinct` 算子，nop fail-fast 正确 |
| 静态维表 lookup join（`ITableLookup`） | lookup join + hint + partial/full cache + async | nop 是最简形态；**可借鉴 lookup cache 的声明维度**（见 §10） |
| 无窗口持续 GROUP BY | `StreamPhysicalGroupAggregateRule.scala` + minibatch | Flink 用 minibatch 换吞吐（`MINI_BATCH_RULES`），nop 发射运行值（D1）语义不同但同为"持续聚合" |
| 分析窗口 OVER（`ROW_NUMBER`, `FRAME_SLIDING_AGG`） | `StreamPhysicalOverAggregateRule.scala` + `RANK/DENSE_RANK/NTILE/LEAD/LAG` | nop 的两个算子 id 对应 Flink 的 Rank 与 OverAggregate；Flink 还有 `CUME_DIST/PERCENT_RANK` |
| 不支持清单（ORDER BY/LIMIT、非等值 join、DATE/TIMESTAMP/DECIMAL、DISTINCT、HAVING/CTE、全局聚合、FULL window join、UNION DISTINCT、Flink SQL 方言） | Flink 全部支持 | **Flink 侧无对应 fail-fast 需求**；nop 的清单是编译器契约，不应为"对齐 Flink"而放宽 |
| D1 last-value-wins | `ChangelogMode` + retract/upsert | 冲突已裁定：nop 降级，非缺陷 |
| D6 事件时间 | rowtime attribute + watermark | 一致 |
| D9 allowedLateness 已放行 | Table 层 `allowedLateness=0` | **nop 比 Flink 宽**，无须反向收窄 |
| D10 accumulationMode / D11 trigger fail-fast | Flink Table 层不暴露 trigger | Flink 也不给，nop fail-fast 与业界一致 |
| EQL AST → `StreamSqlCompiler` → 流模型 XML | `SqlNode → Operation → RelNode → ExecNodeGraph → Transformation` | 层级更多但同为"声明→可执行"；nop 三层（AST→流模型→管线）更短，是**嵌入式定位的正确取舍** |
| 参数化算子面三注册表（schemas/aggregators/joins） | `FlinkSqlOperatorTable` + `FunctionCatalog` + hint/options 三处 | Flink 把注册面拆成"算子表 / 函数目录 / hint 选项"，nop 三注册表的划分与其**几乎同构** |
| 双目标（RDBMS 方言 或 流管线） | `rel2sql` 只在 Calcite（Flink 未暴露） | nop 双目标是差异化能力，Flink 无 |
| 无代价优化器 = Non-Goal | Volcano + Hep + metadata 全套 | **裁定保留**，不追 |

## 引用出处

路径根：`/Users/abc/sources/flink`（下述相对路径均以该根起算；行号来自本次实际读取的文件内容）。

- `flink-table/pom.xml:81` — `calcite.version=1.36.0`
- `flink-table/pom.xml` — module 列表（flink-sql-parser / flink-table-planner / flink-sql-client / flink-sql-gateway / flink-sql-jdbc-driver / flink-table-calcite-bridge）
- `LICENSE` — Apache License 2.0
- `flink-table/flink-sql-parser/src/main/codegen/config.fmpp:17-34` — FMPP 扩展点注释
- `flink-table/flink-sql-parser/src/main/codegen/data/Parser.tdd:272,384,509,587` — 关键字表
- `flink-table/flink-sql-parser/src/main/codegen/templates/Parser.jj:77,2921-2929,3063-3180,7174-7184,7856-7934`
- `flink-table/flink-sql-parser/src/main/codegen/includes/parserImpls.ftl:1118,1122,1135,1164,1193,1408,1432`
- `flink-table/flink-sql-parser/src/main/java/org/apache/flink/sql/parser/ddl/SqlWatermark.java:39`
- `flink-table/flink-sql-parser/src/main/java/org/apache/flink/sql/parser/ddl/table/SqlCreateTable.java`
- `flink-table/flink-table-common/src/main/java/org/apache/flink/table/connector/ChangelogMode.java:38-63,76,84,103`
- `flink-table/flink-table-common/src/main/java/org/apache/flink/table/catalog/WatermarkSpec.java:36,49,60`
- `flink-table/flink-table-common/src/main/java/org/apache/flink/table/api/Schema.java:78,543-551`
- `flink-table/flink-table-common/src/main/java/org/apache/flink/table/connector/source/lookup/LookupOptions.java:38,57,63,69,76`
- `flink-table/flink-table-common/src/main/java/org/apache/flink/table/connector/source/lookup/cache/trigger/PeriodicCacheReloadTrigger.java`
- `flink-table/flink-table-common/src/main/java/org/apache/flink/table/connector/source/lookup/cache/trigger/TimedCacheReloadTrigger.java`
- `flink-table/flink-table-common/src/main/java/org/apache/flink/table/functions/{AggregateFunction,TableAggregateFunction,ChangelogFunction}.java`
- `flink-table/flink-table-api-java/src/main/java/org/apache/flink/table/api/TableEnvironment.java:101,128,400-1009`
- `flink-table/flink-table-api-java/src/main/java/org/apache/flink/table/api/internal/TableEnvironmentImpl.java:877,921,940`
- `flink-table/flink-table-api-java/src/main/java/org/apache/flink/table/api/config/ExecutionConfigOptions.java:50,67,79,145,232,677`
- `flink-table/flink-table-api-java/src/main/java/org/apache/flink/table/api/config/TableConfigOptions.java:105`
- `flink-table/flink-table-api-java/src/main/java/org/apache/flink/table/api/config/LookupJoinHintOptions.java:39,45,63,77`
- `flink-table/flink-table-planner/pom.xml:63,372-400`
- `flink-table/flink-table-calcite-bridge/pom.xml:31,43,49`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/operations/SqlNodeToOperationConversion.java:151`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/functions/sql/FlinkSqlOperatorTable.java:1001-1009,1143-1156,1278-1285,1334`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/functions/sql/SqlWindowTableFunction.java:53-60,63,70-80,153`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/functions/sql/SqlTumbleTableFunction.java:39,41,46,64-67`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/functions/sql/SqlCumulateTableFunction.java:39,42`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/plan/nodes/exec/stream/StreamExecIntervalJoin.java:361-404`（`:400` `allowedLateness=0`）
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/plan/nodes/exec/stream/StreamExecWindowAggregateBase.java:75`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/plan/nodes/exec/spec/MatchSpec.java:84`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/plan/rules/logical/FlinkAggregateExpandDistinctAggregatesRule.java:74,131`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/plan/utils/LookupJoinUtil.java:425,448,566,579-584`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/hint/JoinStrategy.java:51`
- `flink-table/flink-table-planner/src/main/java/org/apache/flink/table/planner/hint/FlinkHintStrategies.java:118-247`
- `flink-table/flink-table-planner/src/main/scala/org/apache/flink/table/planner/delegation/PlannerBase.scala:175,184,195-213,248,394,401,412,449`
- `flink-table/flink-table-planner/src/main/scala/org/apache/flink/table/planner/plan/optimize/program/FlinkStreamProgram.scala:32-44,262-300,303-359`
- `flink-table/flink-table-planner/src/main/scala/org/apache/flink/table/planner/plan/optimize/program/FlinkChangelogModeInferenceProgram.scala:54-94`
- `flink-table/flink-table-planner/src/main/scala/org/apache/flink/table/planner/plan/rules/FlinkStreamRuleSets.scala:305`
- `flink-table/flink-table-planner/src/main/scala/org/apache/flink/table/planner/plan/rules/FlinkBatchRuleSets.scala:298`
- `flink-table/flink-table-planner/src/main/scala/org/apache/flink/table/planner/plan/utils/MatchUtil.scala`（`createMatchSpec`、`isMatchTimeIndicator`）
- `flink-table/flink-table-planner/src/main/scala/org/apache/flink/table/planner/codegen/MatchCodeGenerator.scala:339`
- `flink-table/flink-table-planner/src/main/scala/org/apache/flink/table/planner/plan/rules/physical/stream/StreamPhysicalMatchRule.scala:30`
- 存在性已核对的文件（未逐行引用）：`rules/physical/stream/{StreamPhysicalWindowJoinRule,StreamPhysicalTemporalJoinRule,StreamPhysicalIntervalJoinRule,StreamPhysicalLookupJoinRule,StreamPhysicalRankRule,StreamPhysicalWindowRankRule,StreamPhysicalWindowDeduplicateRule,StreamPhysicalOverAggregateRule,StreamPhysicalGroupTableAggregateRule}.scala|java`、`rules/logical/{EventTimeTemporalJoinRewriteRule,TemporalJoinRewriteWithUniqueKeyRule,CorrelateSortToRankRule,DecomposeGroupingSetsRule}.java`、`flink-table-common/.../catalog/Catalog.java`、`flink-table-api-java/.../catalog/FunctionCatalog.java`、`flink-sql-client/`、`flink-sql-gateway/`、`flink-sql-jdbc-driver/`
