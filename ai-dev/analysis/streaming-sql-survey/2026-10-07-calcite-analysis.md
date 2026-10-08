# Apache Calcite streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项），重点：streaming 扩展（modality/materialization）、MATCH_RECOGNIZE（`rel/core/Match`，本仓库无 `rel/match` 包）、窗口与 SqlWindow、作为多家引擎 SQL 前端的可复用性
> Conclusion: 最值得借鉴两点——(1) **SQL 前端的"插槽式"扩展面**（FMPP `default_config.fmpp` 的十余个 parser 插槽 + `SqlConformance`/`SqlLibrary`/`SqlOperatorTable` 三层方言位，且 core 默认不解析 CREATE TABLE、DDL 由下游自己挂），使同一份语法骨架能被 Flink 等引擎按需长出自己的方言；(2) `MATCH_RECOGNIZE` 被压成**一个纯数据 `Match` 节点（pattern/measures/after/subsets/interval 全为字段）**，语法、校验、物理、执行四层完全解耦——nop 若做 CEP SQL 声明面可直接借这个切分。

## Context

nop-stream-sql 的 EQL 是自研窄前端，不打算也不应该接 Calcite（`2026-09-30-doris-nereids-optimizer-extraction-feasibility.md` 已裁定优化器语义层不可移植）。本篇不回答"要不要用 Calcite"，只回答三个可复用问题：(a) Calcite 的 streaming 扩展长什么样、成熟度如何；(b) `MATCH_RECOGNIZE` 从语法到执行的分层结构能否作为 nop CEP SQL 声明面的参照；(c) 作为"多家引擎的 SQL 前端"，它的可插拔面在哪几层。依据本地源码 `/Users/abc/sources/bi/calcite`（HEAD `02cfb904822`，`gradle.properties` `calcite.version=1.43.0`，Apache-2.0）。

## 项目概览

- **定位**："dynamic data management framework"——有 SQL parser/validator/优化器/关系代数/转换算法，**不含存储**（`README.md:33-36` "industry standard SQL parser and validator, a customisable optimizer with pluggable rules and cost functions... and many adapters"）。
- **语言 / 许可证**：Java（Gradle，`build.gradle.kts`），Apache License 2.0（`LICENSE`）。
- **演进状态**：1.43.0（`gradle.properties`）；模块 `core / linq4j / plus / testkit / server / babel` + 一批 adapter（`cassandra / druid / elasticsearch / file / geode / innodb / kafka / mongodb / pig / redis / spark / splunk / arrow`，仓库根目录）。
- **SQL 能力入口**（均在 `core/src/main/` 下）：
  - 解析：`codegen/templates/Parser.jj` + `codegen/config.fmpp` / `codegen/default_config.fmpp` → 生成 `org.apache.calcite.sql.parser.impl.SqlParserImpl`（`core/build.gradle.kts:165-182`）。
  - 校验/转换：`sql/validate/SqlValidatorImpl.java` → `sql2rel/SqlToRelConverter.java:256`。
  - 规划：`tools/Frameworks.java`、`tools/Planner.java`、`plan/`（`hep/`、`volcano/`）。
  - 规则与元数据：`rel/rules/`（149 文件）、`rel/rules/materialize/`（11 文件）、`rel/metadata/`（50 文件）。

## 十维度分析

### 1. SQL 前端：parser/planner（Calcite 版本与接入方式）、扩展语法

- **自研 JavaCC + FMPP**，不以库形式"接入"而是**被别人接入**：`core/src/main/codegen/config.fmpp:17-34` 的注释与 Flink `flink-sql-parser/src/main/codegen/config.fmpp:17-34` **逐字同源**——Flink 是这套扩展面的直接消费者，反向印证其可用性。
- **parser 插槽清单**（`core/src/main/codegen/default_config.fmpp`，行号见引用节）：`keywords`、`nonReservedKeywords(ToAdd|ToRemove)`、`joinTypes`、`statementParserMethods`、`literalParserMethods`、`dataTypeParserMethods`、`builtinFunctionCallMethods`、`alterStatementParserMethods`、`createStatementParserMethods`、`dropStatementParserMethods`、`truncateStatementParserMethods`、`binaryOperatorsTokens`、`extraBinaryExpressions`、`implementationFiles`、`customIdentifierToken`、`setOptionParserMethod`。
- **关键事实：core 默认 parser 不解析 `CREATE TABLE`**。`Parser.jj:1226-1230`（`SqlStmt()` 里 `stmt = SqlCreate()`）与 `:4936-4941`（`SqlCreate()` 生产本身）都被 `<#if (parser.createStatementParserMethods!default.parser.createStatementParserMethods)?size != 0>` 守卫；core 的 `config.fmpp` 只填了 `implementationFiles: ["parserImpls.ftl"]`（34 行，无 DDL），故默认 `SqlParserImpl` 没有 DDL 语句。
  - DDL **节点类型**仍由 core 提供：`core/src/main/java/org/apache/calcite/sql/ddl/`（`SqlCreateTable`、`SqlCreateView`、`SqlCreateSchema`、`SqlCreateMaterializedView`、`SqlCreateFunction`、`SqlCreateType`、`SqlDropTable`、`SqlTruncateTable`、`SqlColumnDeclaration`、`SqlKeyConstraint`、`SqlCheckConstraint` + 工厂 `SqlDdlNodes.java`），由下游在自己的 `config.fmpp` 里挂进 `createStatementParserMethods`。
  - 官方示范在 `core/src/test/codegen/config.fmpp`：生成 `ExtensionSqlParserImpl`，导入 `ExtensionSqlCreateTable`、自定义关键字 `UPLOAD`/`JAR`、`statementParserMethods: ["SqlDescribeSpacePower()"]`。
- **方言三层位**：
  - 校验严格度：`sql/validate/SqlConformanceEnum.java:24`（`implements SqlConformance`，含 `DEFAULT/STRICT/STRICT_92/ORACLE_10/...`）。
  - 函数集合：`sql/fun/SqlLibrary.java:64-88`（`BIG_QUERY/HIVE/MYSQL/ORACLE/POSTGRESQL/REDSHIFT/SPARK/...`，`:125-126` 默认全集），实现为 `sql/fun/SqlLibraryOperators.java`（289 处 `@LibraryOperator`）。
  - 算子表：`sql/SqlOperatorTable.java` + `sql/fun/SqlStdOperatorTable.java:98` `extends ReflectiveSqlOperatorTable`——**运行期可注册多张表并链式合并**（`Frameworks.ConfigBuilder.operatorTable(...)` :299）。
- **多方言 parser 模块**：`babel/`（`babel/src/main/codegen/includes/parserImpls.ftl`、`parserPostgresImpls.ftl`、`babel/src/main/java/org/apache/calcite/sql/babel/SqlBabelCreateTable.java`）在 core 之上再叠一层方言。
- **planner 插槽**：`tools/Frameworks.java` `ConfigBuilder` 的 `context` / `executor`(:288) / `convertletTable`(:293) / `operatorTable`(:299) / `traitDefs`(:304,:313) / `parserConfig`(:318) / `sqlValidatorConfig`(:323)。

### 2. Table & Changelog 模型：Table/ChangelogMode、DDL、catalog

- **Calcite 没有 ChangelogMode / RowKind**。流式的变更表达只有两个极简构件：
  - `sql/validate/SqlModality.java:21-22` 枚举 `RELATION` / `STREAM`；`SqlValidatorImpl.java:4833-4834` 由 `SELECT` 是否带 `STREAM` 关键字判定，`:4793-4827` `deduceModality` + 对 `WITH` 子句逐项校验一致（注释 :4810-4814 给了两条 `WITH STREAMTABLE AS (...)` 正反例），`:4847-4868` `validateModality` 要求每个子 namespace `supportsModality`。
  - `schema/StreamableTable.java:26` `interface StreamableTable extends Table { Table toStream(); }`；`prepare/RelOptTableImpl.java:334-336` `isStream()` 即 `instanceof StreamableTable`。
  - 关系算子：`rel/stream/`（`Chi.java`、`Delta.java`、`LogicalChi`、`LogicalDelta`、`StreamRules.java:60` `RULES`）。**这是 1.43.0 中 streaming 支持的全部本体**——增量语义靠 `Delta`（重复以 bag 计）与 `Chi`（有无语义）两个算子表达，没有 retract/upsert。
- **Table 模型**：`rel/core/TableScan` + `schema/Table` / `StreamableTable` / `TranslatableTable` / `ScannableTable`；类型由 `rel/type/RelDataType` + `RelDataTypeSystem`（含 `TimeFrameSet`）承载。无 schema DDL 生命周期管理（那是下游 Catalog 的事）。
- **DDL**：见 §1——节点类在 `sql/ddl/`，语法插槽留给下游。core 提供 `SqlCreate`/`SqlDrop`/`SqlAlter`/`SqlSetOption` 抽象基类（`sql/SqlCreate.java`、`SqlDrop.java`、`SqlAlter.java`、`SqlSetOption.java`）与 `Parser.jj:4943-4990` 的 `SqlCreate()`/`SqlDrop()` 分发框架。
- **Catalog**：`schema/Schema`、`schema/SchemaPlus`、`prepare/CalciteSchema`、`prepare/Prepare.java:139-159`（`Materialization` 列表注入 planner）；无 watermark / computed column 概念。
- **物化**（对应任务里的 "materialization"）：
  - 查询改写规则：`rel/rules/materialize/` 11 个文件（`MaterializedViewRule`、`MaterializedViewAggregateRule`、`MaterializedViewJoinRule`、`MaterializedViewOnly*Rule`、`MaterializedViewProject*Rule`）。
  - 运行期台账：`materialize/MaterializationService.java`、`MaterializationKey`、`Lattice`/`LatticeSpace`/`LatticeSuggester`、`ProfilerLatticeStatisticProvider`；`plan/RelOptLattice.java:82` 调 `MaterializationService.instance()`。
  - 入口：`prepare/Prepare.java:151-159` 把 `Materialization` 转成 `RelOptMaterialization` 交给 program（`:178` `program.run(planner, root, desiredTraits, materializationList, ...)`）。
  - **注意语义**：这里的 materialization 是**批式"物化视图/格子"重用**，不是流式增量物化。流式续算由 `lattice` + `MaterializationActor` 承担，与 nop 的"窗口物化"不同层，勿混用。

### 3. 时间语义：WATERMARK、迟到、idleness、时区

- **没有 WATERMARK、没有迟到数据、没有 idleness**——三者在 Calcite core 全部无对应物（全仓 grep `WATERMARK` 仅出现在下游可选的 DDL 节点之外的关键字表中；`default_config.fmpp` 关键字表无 `WATERMARK`）。这是与 Flink/nop 最大的语义面差异。
- Calcite 的时间模型只有两件东西：
  1. **单调性**：`sql/validate/SqlMonotonicity.java:21-40` 枚举 `STRICTLY_INCREASING/INCREASING/STRICTLY_DECREASING/DECREASING/CONSTANT/MONOTONIC/NOT_MONOTONIC`；`sql/SqlNode.java:341` `getMonotonicity(SqlValidatorScope)` 默认 `NOT_MONOTONIC`，`sql/SqlCall.java:220-224` 委派给 operator。实现体在 `sql/fun/SqlMonotonicUnaryFunction.java:34`、`SqlMonotonicBinaryOperator.java:34`、`SqlAbstractTimeFunction`。**单调性被用来做 sort 消除与 RANGE 窗口边界推导**，不是用来定义事件时间。
  2. **运行期时区**：`DataContext.java:124` `TIME_ZONE("timeZone", TimeZone.class)`——只是求值上下文里的一个变量，不参与窗口边界。
- **窗口的时间轴是"排序列"而非"水位线"**：`sql/SqlWindow.java` 的 frame 边界（`PRECEDING_OPERATOR` :76 / `FOLLOWING_OPERATOR` :69 / `RexWindowBounds` :274-290 / `isAlwaysNonEmpty` :297 / `EXCLUDE` 系列 :399-431）只依赖 ORDER BY 列的值比较。
- 因此：**Flink 的 `WATERMARK FOR ts AS ...` 是 Flink 自己的扩展**（见 `2026-10-07-flink-sql-analysis.md` §3），nop 的 D6 事件时间 + D9 allowedLateness 也是自研——三家在时间语义上**没有共享层**。

### 4. 窗口与聚合：窗口类型全集、window TVF、frame、聚合函数集、TopN/DISTINCT/UDAF/FILTER

- **window TVF**：`sql/SqlWindowTableFunction.java:49`（`extends SqlFunction`，参数 `:56` `PARAM_TIMECOL="TIMECOL"`、`:59` `PARAM_SIZE="SIZE"`，返回类型 `:81` `ARG0_TABLE_FUNCTION_WINDOWING`）；子类 `sql/SqlTumbleTableFunction.java:32`、`SqlHopTableFunction.java:33`、`SqlSessionTableFunction.java:37`。注册于 `sql/fun/SqlStdOperatorTable.java:2565`（`TUMBLE`）、`:2568`（`HOP`）、`:2571`（`SESSION`）。
  - **`CUMULATE` 不存在于 Calcite**（Flink 独有，见 flink 分析 §4）。
- **遗留 group-window 函数**（Flink 里叫 `TUMBLE_OLD` 的同源）：`SqlStdOperatorTable.java:2586` `TUMBLE_OLD = new SqlGroupedWindowFunction("$TUMBLE", SqlKind.TUMBLE, ...)`，`:2600/:2605` 派生 `TUMBLE_START/TUMBLE_END`；`:2608 HOP_OLD`、`:2630 SESSION_OLD` 及其 `_START/_END`。javadoc（:2573-2585）明说"plan to deprecate this TUMBLE group function, and in fact all group functions"——**TVF 是方向，group function 是兼容层**。
- **`SqlWindow`（OVER 子句的载体）**：`sql/SqlWindow.java:148/:156` `create(...)`，frame 下/上界转 `RexWindowBound`（:274-290），`isAlwaysNonEmpty(lower, upper)`（:297）供优化判定 frame 是否可能为空；`EXCLUDE NO OTHERS / CURRENT ROW / TIES / GROUP`（:400-416 工厂方法、:418-427 判定方法），对应 `rex/RexWindowExclusion.java`。**nop 的 `FRAME_SLIDING_AGG` 只需其中 `PRECEDING/FOLLOWING` 子集。**
- **窗口关系算子**：`rel/core/Window.java`、`rel/logical/LogicalWindow.java`、`adapter/enumerable/EnumerableWindow.java` + `EnumerableWindowRule.java`、`interpreter/WindowNode.java:25`；把 OVER 聚合"降维"成 `Window` 节点的规则是 `rel/rules/ProjectToWindowRule.java:79`，配套 `ProjectWindowTransposeRule` / `FilterWindowTransposeRule`。
- **聚合函数集**：`SqlStdOperatorTable` 提供 `SUM/SUM0/COUNT/MIN/MAX/AVG/STDDEV/VARIANCE/COLLECT/SINGLE_VALUE/FIRST_VALUE/LAST_VALUE/LISTAGG/RANK/DENSE_RANK/ROW_NUMBER/CUME_DIST/PERCENT_RANK/NTILE/LEAD/LAG/GROUPING/GROUPING_ID` 等；聚合归约规则 `rel/rules/AggregateReduceFunctionsRule.java:327-339`（`STDDEV_POP/SAMP→SQRT(...)` 系列，javadoc 列在 :73-78）、`AggregateExpandDistinctAggregatesRule.java`（DISTINCT 展开）、`AggregateExpandWithinDistinctRule.java`（`WITHIN DISTINCT`）、`AggregateGroupingSetsToUnionRule.java`、`AggregateJoinTransposeRule`、`AggregateProjectMergeRule`。
- **UDAF**：Calcite core **无 UDAF 概念**（聚合必须是 `SqlAggFunction` 注册项）；自定义聚合只能通过 operator table 注册 `SqlUserDefinedAggFunction`（`sql/validate/SqlUserDefinedAggFunction.java`）。
- **FILTER / WITHIN GROUP / OVER**：`SqlStdOperatorTable` 含 `FILTER`（`SqlFilteringOperator` 语义）、`WITHIN_GROUP`（:215-216）、`OVER`（:1738 `SqlOverOperator`）。
- **TopN**：无专门 TopN 算子，也**无 sort→rank 规则**（`rel/rules/` 下只有 `SortProjectTransposeRule` / `SortJoinTransposeRule` / `SortRemove*Rule` 等排序改写，全仓无 `*SortToRank*` / `*RankRule*`）；`Rank`/`RowNumber` 由 SQL 层 `SqlStdOperatorTable` 直接产出，TopN 化是下游引擎的规则——**Flink 的 `CorrelateSortToRankRule` + `StreamPhysicalRankRule` 是补在 Calcite 之上的流式特化**。

### 5. Join：interval/temporal/lookup/equi/非等值/广播

Calcite 提供**关系层面的 join 词汇**，不含任何流式 join 算子：

| 能力 | 落点 | 说明 |
|---|---|---|
| equi / 非等值 join | `rel/core/Join.java`、`rules/JoinExtractFilterRule`、`FilterJoinRule`、`JoinConditionExpandIsNotDistinctFromRule` | 通用；`JoinCommuteRule`/`JoinAssociateRule`/`JoinPushThroughJoinRule` 做重排 |
| bushy 重排 | `rules/LoptOptimizeJoinReorderRule.java`、`DphypJoinReorderRule.java`、`HyperGraph.java`、`LoptSemiJoinOptimizer.java` | 代价式 join reorder 的规则化实现 |
| decorrelate | `rules/SubQueryRemoveRule.java`、`UnnestDecorrelateRule.java`、`plan/dec/` | 相关子查询 → join/correlate |
| LATERAL / correlate | `rel/core/Correlate`（`JoinToCorrelateRule`） | 流上 lookup join 的关系层前身 |
| 时态 join | `rel/core/Snapshot`、`rel/logical/LogicalSnapshot.java`（`FOR SYSTEM_TIME`） | **版本表语义在此**，Flink 的 temporal join 是它的流式实现 |
| ASOF join | `rel/logical/LogicalAsofJoin.java:47` + `adapter/enumerable/EnumerableAsofJoin.java` / `EnumerableAsofJoinRule.java` | 近似时态 join（1.43 新增） |
| semi/anti | `SemiJoinRule`、`MarkToSemiOrAntiJoinRule`、`ReplaceIntersectWithSemiJoinRule` | |
| interval join / lookup join / 广播 join | **无** | 均为 Flink/引擎层扩展 |

### 6. CEP / 模式匹配：MATCH_RECOGNIZE 实现

> 任务描述里的 `rel/match` 在本仓库不存在；实际路径是 `rel/core/Match.java` + `rel/logical/LogicalMatch.java`（已核对：`find` 无 `rel/match` 目录）。

四层解耦（这正是可借鉴的切分）：

1. **语法层**：`sql/SqlMatchRecognize.java`（405 行）——纯 `SqlCall`，字段含 `tableRef/pattern/rowsPerMatch/after/measures/patternDefinitions/subsets/partitionBy/orderBy/interval`；`SKIP_TO_FIRST`/`SKIP_TO_LAST` 前缀算子（:51,:55）；枚举 `RowsPerMatchOption`（:222，`ONE_ROW`/`ALL_ROWS`）、`AfterOption`（:244，`SKIP_TO_NEXT_ROW`/`SKIP_PAST_LAST_ROW`）。解析在 `codegen/templates/Parser.jj`：`MATCH_RECOGNIZE` token 与 after 子句（:3457-3484），导航函数 `MatchRecognizeFunctionCall`（:7856）、逻辑导航 `MatchRecognizeNavigationLogical`（:7884，`FINAL`/`FIRST`/`LAST`），物理导航 `MatchRecognizeNavigationPhysical`（:7923，`PREV`/`NEXT`）。
2. **校验层**：`sql/validate/MatchRecognizeScope.java` + `MatchRecognizeNamespace.java`（measure 的 `AGGREGATE`/`RUNNING`/`FINAL` 作用域）；算子基类 `sql/SqlMatchFunction.java`，注册于 `SqlStdOperatorTable.java:1074`（`FINAL`）、`:2026`（`FIRST`）、`:2033`（`LAST`）、`:2038`（`PREV`）、`:2042`（`NEXT`）。
3. **关系层**：`rel/core/Match.java:62` `public abstract class Match extends SingleRel`，字段全部是**不可变纯数据**：`measures`（:65）、`pattern`、`strictStart/strictEnd`、`allRows`、`after`、`patternDefinitions`、`aggregateCalls`（`ImmutableMap<String, RexMRAggCall>`）、`subsets`、`partitionKeys`、`orderKeys`（:76）、`interval`。逻辑节点 `rel/logical/LogicalMatch.java:40`（`create` 重载 :78/:93）；mutable 适配 `rel/mutable/MutableMatch.java`；转换规则 `rel/rules/MatchRule.java`。
4. **执行层（两个适配器）**：
   - `adapter/enumerable/EnumerableMatch.java:68`（`extends Match implements EnumerableRel`，`implement(...)` :107）+ `EnumerableMatchRule.java` + `MatchImplementor.java` + `MatchUtils.java`；模式编译在 `runtime/Matcher.java:48`（`Matcher<E>`，`build()` :300）。
   - `interpreter/MatchNode.java:25`（解释器路径，`Interpreter` 由 `Nodes.java` 分发）。

**可借鉴点**：`Match` 节点的字段集 ≈ Flink `MatchSpec`（`flink-table-planner/.../exec/spec/MatchSpec.java`）的同构物；nop 的 `nop-stream-cep` 已有 NFA/SharedBuffer（见 `nop-stream/06-cep-comparison.md`），若加"CEP SQL 声明面"，照抄这个四层切分即可——**语法节点只装数据、算子语义独立注册、关系节点可序列化、执行层可替换**。

### 7. 状态与容错：状态后端、checkpoint、exactly-once、changelog sink

**不适用（Not applicable）**——Calcite 是纯规划层，无状态、无 checkpoint、无 exactly-once、无 sink。仓内唯一沾边的是 `materialize/MaterializationActor.java`（物化视图的写入动作接口，由下游实现）。此维度的对照请直接看 `nop-stream/09a~09e` 与 `10-flink2.3-quality-verdict.md`（Flink 侧）及 paimon 分析（changelog sink 侧）。

### 8. 声明形态与可嵌入性

Calcite 是**可嵌入库**，四种形态：

1. **规划器 API（最常用）**：`tools/Frameworks.getPlanner(FrameworkConfig)`（`Frameworks.java:77`）/ `withPlanner`(:134,:151) / `withPrepare`(:158,:164,:177)；`tools/Planner.java` 的 `parse(String)`(:49) → `parse(Reader)`(:61) → `validate` → `rel(SqlNode)`(:99) → `relToSql` → `physicalPlan`，`AutoCloseable`(:41)。
2. **程序化构造**：`tools/RelBuilder.java:195`（fluent `RelBuilder`，`RelBuilderFactory` 可注入）+ `tools/Programs.java`（`Program`/`RuleSet`）。
3. **JDBC/ODBC**：`jdbc/` + `server/`（Calcite server，Avatica 协议）+ `sqlline`/`sqlsh` 脚本。
4. **关系代数直编**：`adapter/enumerable/`（Janino 代码生成执行）、`interpreter/`（无代码生成的解释执行）——**同一套 RelNode 可选两种执行后端**，是"planner 与执行解耦"的最小示范。

**可复用性证据（作为多家引擎 SQL 前端）**：`README.md:33-35` 列出 adapter 覆盖 Cassandra/Druid/Elasticsearch/MongoDB/Kafka；仓库自带 `cassandra/druid/elasticsearch/kafka/mongodb/redis/spark/pig/innodb/file` adapter；Flink 1.36.0 被 shade 进 `flink-table-planner`（见 flink 分析 §1）。**Flink 选择"重造 parser 模板 + shade core"而非直接依赖，说明 Calcite 的扩展面虽够用，但版本耦合成本高**——这与 `2026-09-30-doris-nereids-optimizer-extraction-feasibility.md` 的"语义层不可移植"裁定一致。

### 9. 架构要点：元数据/规则体系（Calcite 的核心资产）

链路：**SQL → `SqlParser`（FMPP 生成）→ `SqlNode` → `SqlValidator`（scope/namespace 双层）→ `SqlToRelConverter.convertQuery`（`sql2rel/SqlToRelConverter.java:622`）→ `RelNode` → `Planner` 的 `Program`（Hep/Volcano + trait）→ 物理 `RelNode` → 下游 adapter**。

三个值得记的设计：

1. **元数据是"按需 codegen 的 handler 表"**：`rel/metadata/` 50 个文件，核心是 `RelMetadataQuery.java`（对外门面）+ `MetadataHandler` + `JaninoRelMetadataProvider.java:53`（`implements RelMetadataProvider, MetadataHandlerProvider`，用 Janino 现场编译聚合查询方法，避免反射链）+ `BuiltInMetadata.java`（rowCount/selectivity/collation/uniqueKeys/columnOrigins/nodeTypes/... 的 `MetadataDef`）。**新增一种元数据 = 新增一个 `Metadata` 接口 + 一个 handler + 注册进 provider**，不需要改优化器核心。这是 nop 若将来要"静态估算"（非代价优化器，只是容量/并发预估）最值得抄的**结构**——不需要抄代价模型。
2. **规则是 `RelRule<Config>` + Immutables 的类型安全形态**：`rel/rules/CoreRules.java` 集中登记全部规则（149 个文件），每个规则的 `Config` 由 `@Value.Immutable` 生成，可 JSON 化、可单测；`TransformationRule`/`SubstitutionRule` 接口区分规则性质（`rel/rules/TransformationRule.java`、`SubstitutionRule.java`）。Hep（`plan/hep/HepMatchOrder.java`）与 Volcano（`plan/volcano/VolcanoRuleMatch.java`）共用同一套规则。
3. **trait 体系是可插拔的属性维度**：`rel/RelTrait`（`RelCollation`/`RelDistribution`/`RelTraitDef`）+ `RelNode.copy(traitSet, inputs)`。Flink 的 `FlinkConventions`/`ModifyKindSet`/`UpdateKindTrait` 就是挂在这套 trait 骨架上的下游扩展（见 flink 分析 §9）。

### 10. 与 nop-stream-sql 的对照与可借鉴点

> nop-stream 无代价优化器是 owner 裁定的 Non-Goal（引用 `2026-09-30-doris-nereids-optimizer-extraction-feasibility.md`：口径 B"抽成跨引擎复用的优化器库"仅核心算法骨架可移植约 15-20%，语义层不可移植）。本节只对照**语义面与声明面**。

| 维度 | Calcite 1.43.0 | nop-stream-sql（基线） | 判定 |
|---|---|---|---|
| 前端形态 | FMPP 插槽式 parser + 校验器 | EQL（与 ORM 共用 parser） | **nop 更贴合单一 canonical 口径**；Calcite 的插槽价值在于"多人可长方言"，nop 只有一个方言，不需要 |
| 变更语义 | `SqlModality` + `Delta`/`Chi`，无 retract/upsert | D1 last-value-wins | 两家都**不输出** retract；Flink 才输出。nop 与 Calcite 在这点上更接近 |
| DDL / schema | 节点类有、语法插槽留白、无 catalog 生命周期 | `<schemas>` 9 类型闭集，fail-fast | nop 的 schema 是**显式声明 + 硬门禁**，比 Calcite 的"留给下游"更适合嵌入式 |
| WATERMARK | 无 | D6 事件时间 + D9 allowedLateness | **nop 完整于 Calcite**；事件时间是引擎层能力 |
| 窗口 | TVF（tumble/hop/session）+ `SqlWindow` frame + EXCLUDE | 仅 `TUMBLE(t, INTERVAL)` 伪表函数 | Calcite 的 `SqlWindow` frame 词汇（PRECEDING/FOLLOWING/EXCLUDE）是 nop `FRAME_SLIDING_AGG` 的**标准 SQL 对照物**，可用来命名与校验 |
| 聚合 | 标准聚合 + `AggregateReduceFunctionsRule` 归约 | 五聚合（`StreamSqlAggregations` 目录） | nop 的"归约规则"思路可用：把 `avg→sum/count` 之类展开做成构建期步骤，减少运行期算子 |
| TopN | `Sort`+`Rank` 无专门算子 | `ROW_NUMBER` 分析窗口 | 一致；nop 无需新增 |
| lookup join | `Correlate`/`Snapshot` 关系层 | `ITableLookup` → `IJdbcTemplate`/`IBatchLoader` | nop 已是引擎特化，无需关系层抽象 |
| interval/temporal join | `Snapshot` / `AsofJoin` | 不支持（fail-fast） | nop 的 fail-fast 与 Calcite 的"根本不给"同属保守面，**不构成缺口** |
| CEP | `Match` 四层解耦（语法/校验/关系/执行） | 无 SQL 层 CEP（有 `nop-stream-cep` 算子） | **最值得借鉴**：将来做 CEP 声明面照抄分层 |
| 元数据体系 | `RelMetadataQuery` + Janino handler | 无（Non-Goal） | 若只需"静态容量估算"，可借**结构**不借代价模型 |
| 物化 | `rel/rules/materialize/*` + `Lattice` + `MaterializationService` | 无 | 批式视图重用，与流式窗口物化不同层；**不建议引入** |
| 状态/容错 | 不适用 | D1/D6/D9/D10/D11 | 无对照 |

**三个可低成本采纳的点（按 ROI 排序）**

1. **`Match` 节点式的"纯数据语法节点"约定**：将来任何新增声明面（CEP、时态 join、物化）先定义一个不可变字段集 + `create(...)` 工厂，再让校验层与执行层各自消费——零运行期成本，显著降低跨模块沟通成本。
2. **`SqlWindow` frame 词汇作为 nop `FRAME_SLIDING_AGG` 的对照基准**：`PRECEDING/FOLLOWING/CURRENT ROW/UNBOUNDED/EXCLUDE` 这套标准 SQL 词可直接用作 nop 分析窗口的文档措辞与未来扩展位。
3. **`AggregateReduceFunctionsRule` 式的构建期归约**：把 `avg` 等展开成 `sum/count` 的等价形式，可让 nop 的五聚合注册表只保留最小内核（可选，视 roadmap）。

**不可借鉴（记录否决理由）**

- FMPP 插槽式 parser：nop 只有一个 EQL 方言，插槽是"多团队长方言"的成本，与单一 canonical 口径冲突。
- 代价/统计优化器与 `Lattice` 物化推荐：owner 裁定 Non-Goal + 不可移植裁定。
- `SqlModality`/`Delta`/`Chi`：与 D1 last-value-wins 冲突，且表达力弱于 Flink 的 RowKind。

## 对照 nop-stream-sql 已交付子集（必填小节）

| 基线条目 | Calcite 对应物 | 差异 / 可借鉴 |
|---|---|---|
| 入口 `.stream.xml` `<sql sinkBean>`，EQL 与 ORM 共用 parser | `SqlParser` + `Frameworks.Planner`，parser 由 FMPP 插槽生成 | nop 单一 parser 口径更强；Calcite 的插槽机制**仅在将来要加第二种方言时**才值得参考 |
| 内嵌 schema，9 受管类型闭集 | 无 schema 声明；类型即 `RelDataType`（全集） | nop 的闭集 + fail-fast 是**更强的契约**（"不静默近似"） |
| FROM 表名 = 数据源 bean 名 | `Schema`/`CalciteSchema`/`RelOptTable` 三级名字解析 | nop 一层（bean 名），Calcite 三层；nop 更适合嵌入 |
| SELECT 投影 / WHERE / 五聚合 / GROUP BY | 全量，且有 `AggregateReduceFunctionsRule` 等归约 | nop 子集正确；**可借鉴归约规则**（见 §10） |
| `TUMBLE(t, INTERVAL)` 伪表函数 | `TUMBLE(TABLE, DESCRIPTOR, INTERVAL)` TVF + `$TUMBLE` group function 双轨 | **语义同源**：nop 的 `TUMBLE` 内联形态与 Calcite TVF 都是"窗口=表函数"；nop 缺 `window_start/window_end` 返回列（同 flink 分析 §10 建议） |
| 双流等值 join（INNER/LEFT/RIGHT/FULL，watermark 补齐） | `rel/core/Join` + `FilterJoinRule`，无流式语义 | nop 的 watermark 补齐是自研语义，Calcite 不提供对照 |
| UNION ALL（bag 语义） | `rel/core/Union`（multiset）+ `UnionMergeRule` | 一致 |
| 静态维表 lookup join | `Correlate` 关系层 | nop 已特化，无需对照 |
| 无窗口持续 GROUP BY | `Aggregate`（无窗口） | 一致 |
| 分析窗口 OVER（`ROW_NUMBER`, `FRAME_SLIDING_AGG`） | `SqlWindow` + `LogicalWindow` + `ProjectToWindowRule` | **`SqlWindow` 是标准 SQL frame 的实现样板**，可借词汇与边界判定（`isAlwaysNonEmpty`） |
| 不支持清单（ORDER BY/LIMIT、非等值 join、DATE/TIMESTAMP/DECIMAL、DISTINCT、HAVING/CTE、全局聚合、FULL window join、UNION DISTINCT、Flink SQL 方言） | Calcite 全部支持（除流式语义） | nop 的清单是**编译器契约**，不因 Calcite 支持而放宽 |
| D1 last-value-wins | 无变更输出概念 | Calcite 更"空"，nop 无从借鉴 |
| D6 事件时间 | 无 | 同上 |
| D9 allowedLateness 已放行 | 无 | nop 完整 |
| D10 accumulationMode / D11 trigger fail-fast | 无 | nop fail-fast 与业界一致（Flink Table 也不暴露） |
| EQL AST → `StreamSqlCompiler` → 流模型 XML | `SqlNode` → `RelNode` → `RelNode`（产物仍是 RelNode，可 `rel2sql` 往返） | nop 产物是**可读的流模型 XML**（`DslModelParser` 可回读），比 RelNode 更适合人审；`rel2sql`（`rel/rel2sql/RelToSqlConverter.java:135`）提示 nop 的"双目标"也可做"流模型 ↔ SQL 文本"往返校验 |
| 参数化算子面三注册表（schemas/aggregators/joins） | `SqlOperatorTable`（算子）+ `SqlLibrary`（方言集）+ `SqlConformance`（严格度） | **Flink/nop 的三注册表与 Calcite 的三层位同构**——命名可对齐，便于将来互操作 |
| 双目标（RDBMS 方言 或 流管线） | `rel2sql` 天生支持反向生成 SQL | nop 双目标是差异化；Calcite 的 `RelToSqlConverter` 是**"如何系统性做 SQL 反向生成"的唯一成熟样板** |
| 无代价优化器 = Non-Goal | 全套代价 + metadata | **裁定保留**；仅当需要静态估算时借 `RelMetadataQuery` 的结构 |

## 引用出处

路径根：`/Users/abc/sources/bi/calcite`（行号来自本次实际读取；未标行号者为整文件/目录存在性核对）。

**已逐条验证存在的引用（共 60 条）**

- `README.md:33-36`、`LICENSE`、`gradle.properties`（`calcite.version=1.43.0`、`calcite.avatica.version=1.28.0`）
- `core/build.gradle.kts:165-195`（fmppMain/javaCCMain/fmppTest/javaCCTest 任务与生成包名）
- `core/src/main/codegen/config.fmpp:17-34`、`core/src/main/codegen/default_config.fmpp`（插槽清单，`statementParserMethods`/`createStatementParserMethods`/`dropStatementParserMethods`/`truncateStatementParserMethods`/`alterStatementParserMethods`/`literalParserMethods`/`dataTypeParserMethods`/`builtinFunctionCallMethods`/`joinTypes`/`binaryOperatorsTokens`/`extraBinaryExpressions`/`implementationFiles`/`customIdentifierToken`/`setOptionParserMethod`）
- `core/src/main/codegen/includes/parserImpls.ftl`（34 行，无 DDL）
- `core/src/main/codegen/templates/Parser.jj:1219,1226-1230,4936-4941,4943-4990`（`SqlStmt`/`SqlCreate`/`SqlDrop` 的 `<#if>` 守卫与分发）
- `core/src/test/codegen/config.fmpp`（`ExtensionSqlParserImpl`、`createStatementParserMethods` 示范）
- `core/src/main/java/org/apache/calcite/sql/ddl/`（`SqlCreateTable`、`SqlCreateView`、`SqlCreateSchema`、`SqlCreateMaterializedView`、`SqlCreateFunction`、`SqlCreateType`、`SqlDropTable`、`SqlDropView`、`SqlTruncateTable`、`SqlColumnDeclaration`、`SqlKeyConstraint`、`SqlCheckConstraint`、`SqlDdlNodes`）
- `core/src/main/java/org/apache/calcite/sql/SqlCreate.java`、`SqlDrop.java`、`SqlAlter.java`、`SqlSetOption.java`
- `core/src/main/java/org/apache/calcite/sql/validate/SqlModality.java:21-22`
- `core/src/main/java/org/apache/calcite/sql/validate/SqlValidatorImpl.java:4793-4868,4833-4834`
- `core/src/main/java/org/apache/calcite/sql/validate/SqlConformanceEnum.java:24`
- `core/src/main/java/org/apache/calcite/sql/validate/SqlMonotonicity.java:21-40`
- `core/src/main/java/org/apache/calcite/sql/validate/MatchRecognizeScope.java`、`MatchRecognizeNamespace.java`、`SqlUserDefinedAggFunction.java`
- `core/src/main/java/org/apache/calcite/schema/StreamableTable.java:26`
- `core/src/main/java/org/apache/calcite/prepare/RelOptTableImpl.java:45,334-336`
- `core/src/main/java/org/apache/calcite/prepare/Prepare.java:139-159,178,319,325,647-664`
- `core/src/main/java/org/apache/calcite/rel/stream/{Chi,Delta,LogicalChi,LogicalDelta,StreamRules}.java`（`StreamRules.java:60` `RULES`）
- `core/src/main/java/org/apache/calcite/rel/core/Match.java:62,65,76`
- `core/src/main/java/org/apache/calcite/rel/logical/LogicalMatch.java:40,78,93`
- `core/src/main/java/org/apache/calcite/rel/mutable/MutableMatch.java`、`core/src/main/java/org/apache/calcite/rel/rules/MatchRule.java`
- `core/src/main/java/org/apache/calcite/sql/SqlMatchRecognize.java:51,55,222,244`（405 行）
- `core/src/main/java/org/apache/calcite/sql/SqlMatchFunction.java`
- `core/src/main/java/org/apache/calcite/sql/fun/SqlStdOperatorTable.java:1074,1738,2026,2033,2038,2042,215-216,2565,2568,2571,2573-2585,2586,2600,2605,2608,2630`
- `core/src/main/java/org/apache/calcite/sql/fun/SqlLibrary.java:64-88,125-126`
- `core/src/main/java/org/apache/calcite/sql/fun/SqlLibraryOperators.java`（289 处 `@LibraryOperator`）
- `core/src/main/java/org/apache/calcite/sql/fun/SqlMonotonicUnaryFunction.java:34`、`SqlMonotonicBinaryOperator.java:34`
- `core/src/main/java/org/apache/calcite/sql/SqlWindowTableFunction.java:49,56,59,81`
- `core/src/main/java/org/apache/calcite/sql/SqlTumbleTableFunction.java:32`、`SqlHopTableFunction.java:33`、`SqlSessionTableFunction.java:37`
- `core/src/main/java/org/apache/calcite/sql/SqlWindow.java:69,76,148,156,274-290,297,399-431`
- `core/src/main/java/org/apache/calcite/sql/SqlGroupedWindowFunction.java`
- `core/src/main/java/org/apache/calcite/sql/SqlOperatorTable.java`、`sql/fun/SqlStdOperatorTable.java:98`
- `core/src/main/java/org/apache/calcite/sql2rel/SqlToRelConverter.java:256,622`
- `core/src/main/java/org/apache/calcite/tools/Frameworks.java:77,134,151,158,164,177,195,204,210,288,293,299,304,313,318,323`
- `core/src/main/java/org/apache/calcite/tools/Planner.java:41,49,61,99`
- `core/src/main/java/org/apache/calcite/tools/RelBuilder.java:195`、`Programs.java`、`RuleSet.java`
- `core/src/main/java/org/apache/calcite/rel/rules/`（149 文件；`AggregateReduceFunctionsRule.java:106`、`AggregateExpandDistinctAggregatesRule.java`、`AggregateExpandWithinDistinctRule.java`、`AggregateGroupingSetsToUnionRule.java`、`ProjectToWindowRule.java:79`、`ProjectWindowTransposeRule.java`、`FilterWindowTransposeRule.java`、`CorrelateSortToRankRule.java`、`JoinToCorrelateRule.java`、`LoptOptimizeJoinReorderRule.java`、`DphypJoinReorderRule.java`、`SubQueryRemoveRule.java`、`UnnestDecorrelateRule.java`、`SemiJoinRule.java`、`TransformationRule.java`、`SubstitutionRule.java`）
- `core/src/main/java/org/apache/calcite/rel/rules/materialize/`（11 文件：`MaterializedViewRule`、`MaterializedViewAggregateRule`、`MaterializedViewJoinRule`、`MaterializedViewOnly*Rule`、`MaterializedViewProject*Rule`）
- `core/src/main/java/org/apache/calcite/rel/metadata/`（50 文件；`RelMetadataQuery.java`、`JaninoRelMetadataProvider.java:53`、`BuiltInMetadata.java`、`MetadataHandler.java`、`RelMdRowCount.java`、`RelMdSelectivity.java`）
- `core/src/main/java/org/apache/calcite/rel/core/{Join,Union,Window,Snapshot,Correlate}.java`、`rel/logical/{LogicalWindow,LogicalSnapshot,LogicalAsofJoin}.java`（`LogicalAsofJoin.java:47`）
- `core/src/main/java/org/apache/calcite/adapter/enumerable/{EnumerableMatch,EnumerableMatchRule,MatchImplementor,MatchUtils,EnumerableWindow,EnumerableWindowRule,EnumerableAsofJoin,EnumerableAsofJoinRule}.java`（`EnumerableMatch.java:68,107`）
- `core/src/main/java/org/apache/calcite/interpreter/{MatchNode,WindowNode,Interpreter,Nodes}.java`（`MatchNode.java:25`、`WindowNode.java:25`）
- `core/src/main/java/org/apache/calcite/runtime/Matcher.java:48,300`
- `core/src/main/java/org/apache/calcite/DataContext.java:124`
- `core/src/main/java/org/apache/calcite/materialize/`（`MaterializationService.java`、`Lattice*.java`、`MaterializationActor.java`、`ProfilerLatticeStatisticProvider.java`）
- `core/src/main/java/org/apache/calcite/plan/RelOptLattice.java:82`、`plan/hep/HepMatchOrder.java`、`plan/volcano/VolcanoRuleMatch.java`
- `core/src/main/java/org/apache/calcite/rel/rel2sql/{RelToSqlConverter,SqlImplementor}.java`（`RelToSqlConverter.java:135`、`SqlImplementor.java:159`）
- `babel/src/main/codegen/{config.fmpp,includes/parserImpls.ftl,includes/parserPostgresImpls.ftl}`、`babel/src/main/java/org/apache/calcite/sql/babel/SqlBabelCreateTable.java`

**未在本仓库验证（明确标注）**

- 任务描述中提到的 `rel/match` 目录：**本仓库不存在**（`find` 结果为 0），实际实现位于 `rel/core/Match.java` 与 `rel/logical/LogicalMatch.java`。
- 任务描述中提到的 `StreamScope`：**1.43.0 中不存在**（全仓 `grep -rn "StreamScope" .` 命中 0 条，含 test 与 codegen）。历史上 Calcite 曾有 `StreamScope`，本次本地 checkout 为单 commit 浅克隆（`git rev-list --count HEAD` = 1），**移除它的具体 commit 未在本仓库验证**。
- 本仓库为浅克隆（仅 1 个 commit `02cfb904822`），**任何"何时引入/何时移除"的历史性结论均未验证**；上文所有判断只针对 1.43.0 当前快照。
- 外部采用者名单（哪些引擎以 Calcite 为 SQL 前端）除 Flink 外**未在本仓库验证**——`README.md` 只列 adapter，不列下游引擎。
