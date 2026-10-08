# Nussknacker streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: Nussknacker 是三者中与 nop 定位最接近的「低代码 + 流处理」平台，最值得借鉴两点——(1) `FlinkSqlQueryComponent` 把 Flink SQL 当作场景图里的**一个节点**：场景变量 → `record` 虚表（含 `record_time` 行时间列 + `SOURCE_WATERMARK()`）→ `tableEnv.sqlQuery(...)` → 回转 `DataStream`，这条「变量→虚表→查询→回流」的嵌入回路正是 nop `.stream.xml` 内嵌 EQL 的同构物；(2) DDL 与查询分层——`FlinkDdlParser` 只放行 3 类声明式语句 + `FlinkDataDefinition.registerIn` 幂等注册 + rowtime 列唯一性校验，给出了「表声明 + 查询」双层结构的现成范式，可直接映射到 nop 的 `<sql>` 与其数据源 schema 声明。

## Context

Nussknacker 是面向领域专家的低代码可视化流决策平台（`README.md:28`），底层用 Flink 执行（`build.sbt:265` `flinkV = "1.20.3"`），支持 streaming 与 request-response 两种处理模式（`README.md:38`），表达式层用 SpEL（`README.md:49`）。它被列入本调研，是唯一一个「既有低代码场景 DSL、又在其中嵌入了标准流 SQL」的样本——nop 同样是「低代码 DSL + 内嵌 SQL」的形态，因此 Nussknacker 的**嵌入方式**（SQL 作为节点还是整图、虚表如何命名、事件时间列从哪来）比它的 SQL 能力清单本身更有参考价值。本地源码：`/Users/abc/sources/streaming-sql/nussknacker`，版本 `1.19.0-SNAPSHOT`（`version.sbt:1`），commit `2e8d326`。

## 项目概览

- **定位**：低代码可视化工具，让领域专家构建/运行/监控实时决策算法（`README.md:28`），而非由程序员写代码（`README.md:49` 的 cost/time 论述）。
- **语言 / 许可证**：Scala（sbt 构建），Flink 1.20.3 + `flink-connector-kafka` 3.4.0-1.20（`build.sbt:265`/`:266`）；Apache License 2.0（`README.md:126`）。
- **演进状态**：活跃，`1.19.0-SNAPSHOT`，Flink 1.20 系列。
- **SQL/规则能力入口**（三处，层次不同）：
  1. **规则/表达式前端**：SpEL，两种 flavour——`Standard` 与 `Template`（`SpelFlavour.scala:7`/`:11`/`:13`）；
  2. **流 SQL 查询节点**：`FlinkSqlQueryComponent` + 参数 `flinkSqlQuery`（`FlinkSqlQueryComponentFactory.scala:45`），经组件 `flink-sql-query` 暴露（`FlinkSqlOpsComponentProvider.scala:20-23`）；
  3. **DDL/数据定义**：`FlinkDdlParser`（只放行 3 类语句）+ `FlinkDataDefinition.registerIn`（`FlinkDataDefinition.scala:32`）。
- **模块**：`scenario-api`（场景 DSL：`node.scala` / `CanonicalProcess`）、`scenario-compiler`（SpEL 编译与类型检查）、`components-api`（`ProcessingMode`、组件协议）、`components/sql`（JDBC 维表 enricher）、`engine/flink`（executor / management / components-table / components-base(-unbounded)）、`engine/lite`（轻量引擎）、`designer`（client/server/restmodel）、`extensions-api`。
- **注意**：Nussknacker 的 SQL 能力**只在 Flink 引擎侧**（`engine/flink/components/table/`），lite 引擎无对应实现——这是「低代码平台把 SQL 作为可选引擎特性」的直接证据。

## 十维度分析

### 1. SQL/规则前端：parser 与语义入口

- **入口是一个参数，不是整图**：节点参数 `flinkSqlQuery`（`FlinkSqlQueryComponentFactory.scala:45`，`ParameterDeclaration.mandatory[String]` `:47-48`），SQL 字符串写在场景节点里，**同一场景的其余部分仍是 SpEL 表达式**——SQL 与规则表达式共存于一张场景图。
- **编译期校验**（`FlinkSqlQueryComponentFactory.contextTransformation` `:62`）：从 `flinkSqlQuery` 参数取到字符串后调 `inferTypesAndValidate(context, query, streamTableEnv)`（`:73`、方法定义 `:129`），产出 `outputType` 并写入节点输出类型；空 SQL 报 `CustomNodeError("Query cannot be empty", ...)`（`:113`）；校验异常同样包装成 `CustomNodeError`（`:160`）。
- **changelog 模式校验**：`validateTableChangelogMode(...)`（`:145` 定义、`:154` 被调用）——对查询结果表的 changelog 模式做检查，不通过则转成节点错误。**这相当于 nop 的「构建期 fail-fast」在嵌入式 SQL 上的对应物**。
- **DDL 前端**：`FlinkDdlParser.scala:24` 明确注释「allows only 3 types of statements」；实现上只接受 `SqlCreateCatalog`（`:57`）、`SqlCreateTable`（`:76`），**拒绝 CTAS（`:75`）与其它一切（`:89`）**；parser 用 Flink 自带 `FlinkSqlParserImpl.FACTORY` + `FlinkSqlConformance.DEFAULT`（`:102`/`:103`）。
- 与 nop 对照：nop 的 EQL parser 是**自研/共享 ORM parser** 且入口在 `.stream.xml` 顶层 `<sql>`；Nussknacker 把 SQL 收在节点参数里、入口在 `streamTableEnv.sqlQuery`。两者都是「内嵌 SQL + 编译期类型推导」，差别在 nop 是**整图单 SQL**，Nussknacker 是**图中一个节点**。

### 2. Table & Changelog 模型

- **虚表的构造**（`FlinkSqlQueryComponent.transform`，`FlinkSqlQueryComponent.scala:21-32`）：
  1. `StreamTableEnvironment.create(...)`（`:21`）——**在节点内就地建 tableEnv**；
  2. `start.map(ctx => ctx.toRow(contextSchema), ...)`（`:24`）把场景 `Context` 打成 `Row`；
  3. `tableEnv.createTemporaryView(QueryContextSchema.contextTableName, fromDataStream(rowStream, tableSchemaWithEventTime))`（`:27`）——注册为**名为 `record` 的虚表**（`QueryContextSchema.scala:49` `contextTableName = "record"`）；
  4. `tableEnv.sqlQuery(query.sql)`（`:32`）；
  5. `tableEnv.toDataStream(queryTable)`（`:36`）回转流，再 map 回 `ValueWithContext`。
- **表 schema 由场景 schema 派生**：`tableSchemaWithEventTime`（`QueryContextSchema.scala:39`）= 场景字段 + `columnByMetadata(recordTimeColumn, "TIMESTAMP_LTZ(3)", "rowtime", true)`（`:42`）+ `watermark(recordTimeColumn, "SOURCE_WATERMARK()")`（`:43`）。**行时间列是系统列，不占场景变量位**——反过来说，用户若定义同名变量会被拒绝（测试 `FlinkSqlQueryComponentTest.scala:181`「should raise error when 'record_time' variable is defined in context」，错误文案 `:199`）。
- **changelog 假设**：上游是场景 `Context` 流（append-only），因此虚表天然是 append-only changelog；`validateTableChangelogMode` 兜底拦住需要 retract 的下游用法。
- **与 nop 对照**：nop 的 `<sql>` 直接对**已声明的 source/`from` 数据源**做 EQL，不需要「先把事件流映射成虚表」这一步；但 nop 同样需要一个「时间列从哪来」的答案（D6：bigint epoch-millis）。Nussknacker 用 `record_time TIMESTAMP_LTZ(3)` 系统列 + `SOURCE_WATERMARK()`，比 nop 的「时间列必须出现在 schema 里」更省事——**可借鉴的点是把时间列做成系统列并保留保留名校验**。

### 3. 时间语义

- **行时间列 + 水位线内建**：`record_time`（`QueryContextSchema.scala:50`）类型 `TIMESTAMP_LTZ(3)`，水位线 `SOURCE_WATERMARK()`（`:43`）——由 Flink 自动从行时间派生，场景作者不写水位线。
- **源侧可配水位线参数**：`WatermarkStrategyOptions`（`WatermarkStrategyOptions.scala:9-12`）三个字段 `eventTimeLazyParam` / `maxOutOfOrderness` / `idleTimeout`，文件头 `:8` 留有 `// TODO: Add watermark alignment options`（**未做 watermark alignment**）。`UnionTransformer` 带 `watermarkStrategy: Option[...]`（`UnionTransformer.scala:85`）并在连接流上调 `assignTimestampsAndWatermarks`（`:122`）。
- **迟到事件有测试证据**：`FlinkSqlQueryComponentTest.scala:371`「should propagate source watermark and drop late events in event-time window」，窗口用 `TUMBLE(TABLE record, DESCRIPTOR(record_time), INTERVAL '1' SECOND)`（`:385`）——**水位线从 source 传播到 SQL 节点并生效**。
- **处理时间/空闲**：`idleTimeout` 已进选项（`WatermarkStrategyOptions.scala:12`），与 Debezium heartbeat 解决的是同一类问题（空闲源不推水位线）。
- **与 nop 对照**：nop 的 D6 是 bigint epoch-millis + `timestampsAndWatermarks` + D9 `allowedLateness`；Nussknacker 是 `TIMESTAMP_LTZ(3)` + `SOURCE_WATERMARK()`，**没有显式 allowedLateness 配置面**（迟到即丢，见测试 `:371`）。nop 在「允许迟到」上更宽，Nussknacker 在「时间列免声明」上更省——两点可同时保留。

### 4. 窗口与聚合

- **低代码窗口组件**（`FlinkBaseUnboundedComponentProvider.scala:55-67`，providerName `baseUnbounded` `:21`）：`union-memo`（`:55`）、`aggregate-sliding`（`:56`）、`aggregate-tumbling`（`:59`）、`aggregate-session`（`:61`）、`single-side-join`（`:64`）、`full-outer-join`（`:67`）——**窗口聚合是组件，不是 SQL**。
- **窗口参数与触发器**：`windowLength` 默认 `PT1H`（`sampleTransformers.scala:54`/`:122`）、tumbling 有 `emitWhen`（`:130`）、sliding 有 `emitWhenEventLeft`（`:65`）、session 有 `sessionTimeout` + `endSessionCondition` + `emitWhen`（`:191`/`:193`/`:203`）。触发器实现在 `triggers.scala`：`DelegatingTrigger`（`:9`）、`FireOnEachEvent`（`:30`，每事件触发而非窗口结束）、`ClosingEndEventTrigger`（`:53`，结束条件触发 `FIRE_AND_PURGE`）。
- **聚合函数集**（`aggregates.scala`）：`Sum`（`:25`）/`Max`（`:46`）/`Min`（`:58`）/`Median`（`:70`）/`List`（`:104`）/`Set`（`:132`）/`First`（`:159`）/`Last`（`:183`）/`CountWhen`（`:209`）/`Average`（`:272`）/四个 stddev-variance 变体（`:390` 系）/`MapAggregator`（`:421`，**字段级组合聚合**）/`OptionAggregator`（`:521`）；`ReducingAggregator`（`:568`）与 `MathAggregator`（`:579`，含数值类型提升策略）是基类。
- **聚合以 SpEL 可调用对象暴露**：`@AdditionalVariables(name = "AGG", clazz = classOf[AggregateHelper])`（`sampleTransformers.scala:30`/`:98`/`:167`），编辑器枚举 `#AGG.first/last/min/max/sum/average/countWhen/stddevPop/stddevSamp/varPop/varSamp/median/list/set/approxCardinality`。
- **SQL 侧窗口同样存在**（测试佐证）：`GROUP BY ... TUMBLE(...)`（`FlinkSqlQueryComponentTest.scala:259`/`:272`）、`OVER` 窗口 `ORDER BY record_time`（`:302`）、窗口 Top-N（`:319` 附近）、`MATCH_RECOGNIZE`（`:416`）。
- **与 nop 对照**：nop 支持五聚合 + GROUP BY + TUMBLE + OVER；Nussknacker **两条路都有**（组件路 + SQL 路），且组件路的聚合集比 nop 的五聚合宽得多（median/countWhen/map 组合/方差）。nop 若将来要放宽聚合面，`aggregates.scala` 的 `Aggregator` 抽象（`zero`/`addElement`/`mergeAggregates`/`computeOutputType`）是可直接参照的接口形状——**恰好对应 nop 已有的 aggregators 注册表**。

### 5. Join/关联

- **组件路**：`union-memo`（多路 union + memo）、`single-side-join`（`SingleSideJoinTransformer.scala:75` 强制「恰一 MAIN + 一 JOINED 分支」，带 `stateTimeout`）、`full-outer-join`（`FullOuterJoinTransformer.scala:253` 注入 `AGG` additionalVariables）。
- **SQL 路**：查询在 `record` 虚表上自由 join（测试覆盖单流/多字段投影，`FlinkSqlQueryComponentTest.scala:66`/`:87`/`:104`）。
- **维表 lookup 不走 SQL**：走独立组件 `DatabaseLookupEnricher`（`components/sql/.../service/DatabaseLookupEnricher.scala:50`）——`SELECT * FROM $tableName`（`:101`）+ 按 key 参数 `WHERE ${quoteIdentifier(keyColumn)} = ?`（`:135`），带 `cacheTTL` 参数（`:17`/`:18`/`:84`），结果策略三选一 `ResultSetStrategy`/`SingleResultStrategy`/`UpdateResultStrategy`（`QueryResultStrategy.scala:29`/`:33`/`:37`）。
- **与 nop 对照**：nop 的静态维表 lookup join 是**写在 SQL 里**（`ITableLookup`→`IJdbcTemplate`/`IBatchLoader`），Nussknacker 是**独立 enricher 节点**。两者都是「编译期已知的静态表 + 按 key 查」，但 nop 的形态更接近 SQL 习惯、Nussknacker 的形态更接近低代码节点。nop 侧可借鉴的是 `cacheTTL` + 三种结果策略（多行/单行/更新）这个**策略枚举**。

### 6. CEP/模式匹配

- **场景级不是 CEP**：`Filter`（`node.scala:207`）、`Switch`（`:221`）、`Enricher`（`:287`）、`Join`（`:193`）是低代码控制流节点，逐事件判断，无模式状态。
- **CEP 藏在 Flink SQL 里**：测试 `FlinkSqlQueryComponentTest.scala:416`「should allow pattern recognition with MATCH_RECOGNIZE」，`:430` 展开 `MATCH_RECOGNIZE (` 语法（含 `PARTITION BY` / `ORDER BY record_time` / `MEASURES` / `PATTERN` / `DEFINE`）。
- **与 nop 对照**：nop 把 CEP 归 `nop-stream-cep`（Pattern API + NFA），**不进 SQL 面**；Nussknacker 则让 SQL 面自然携带 `MATCH_RECOGNIZE`（因为底层是 Flink SQL）。这是一条明确的取舍分歧：**nop 的「CEP 不进 SQL」是有意收窄**，而 Nussknacker 借 Flink SQL 之便把 CEP 塞进了 SQL 节点。两者都成立，但 nop 若要保持 fail-fast 面的精简，需继续显式拦截 `MATCH_RECOGNIZE`。

### 7. 状态与容错

- **checkpoint**（`StreamExecutionEnvPreparer.scala`）：`configureCheckpoints`（`:88`）→ `env.enableCheckpointing(...)`（`:95`）、`setMinPauseBetweenCheckpoints`（`:96`，缺省 = 间隔的一半）、`setMaxConcurrentCheckpoints`（`:102`，缺省 1）、`setTolerableCheckpointFailureNumber`（`:107`）；默认间隔来自 `reference.conf:3` `checkpointInterval: 10m`。
- **savepoint 驱动的部署/恢复**（`FlinkDeploymentManager.scala`）：`stopOldJobsIfNeeded`（`:129`）先停旧任务并取 savepoint → `savepointPath = determineSavepointPath(...)`（`:115`，方法定义 `:157`，按 `StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint` / `...FromCustomSavepoint` 分支）→ `jobRunner.runScenarioJob(...)`（`:121`，签名 `FlinkScenarioJobRunner.scala:10`，参数 `savepointPathOpt: Option[String]` `:12`）。恢复入口在 `FlinkScenarioMain.main`（`FlinkScenarioMain.scala:24`，`env = StreamExecutionEnvironment.getExecutionEnvironment` `:45`）。
- **sink 投递语义**（`PartitionByKeyFlinkKafkaSink.scala`）：`sinkDeliveryGuarantee`（`:27`）三态 `EXACTLY_ONCE`（`:32`，带 `transactionalIdPrefix`）/`AT_LEAST_ONCE`（`:35`）/`NONE`，**缺省 `AT_LEAST_ONCE`，注释明说「Flink defaults to NONE」**（`:39`/`:40`）；另把 producer `transaction.timeout.ms` 默认压到 600000（`:20`）。
- **与 nop 对照**：nop 的 checkpoint 走 `nop-stream` 自有 `CheckpointCoordinator` + offset backing store；Nussknacker 完全复用 Flink 的 checkpoint/savepoint，只在**部署层**做「停旧→取 savepoint→带 savepoint 起新」的编排。nop 若做运维面，这套「savepoint 三步编排」是现成剧本；投递语义上 nop 的「默认 at-least-once + 可选 exactly-once」与 Nussknacker 一致。

### 8. 声明形态与可嵌入性

- **声明形态**：场景 = `CanonicalProcess`（`CanonicalProcess.scala:86`）节点图，节点类型固定集合（`node.scala`：`Source` `:178` / `Join` `:193` / `Filter` `:207` / `Switch` `:221` / `Enricher` `:287` / `CustomNode` `:300`），**SQL 是 `CustomNode` 的一种实现**而非独立一等形态。
- **组件注册表**：`ComponentProvider` 协议（`FlinkSqlOpsComponentProvider.scala:7`、`providerName = "flinkSqlOps"` `:9`）+ `ComponentDefinition` 列表（`:20-23`；base-unbounded 见 `FlinkBaseUnboundedComponentProvider.scala:55-67`）——**组件名 → 实现的可插拔注册**，与 nop 的 aggregators/joins/schemas 三注册表同构。
- **可嵌入性**：以库形式嵌入（`designer` server、`engine/flink` executor、`engine/lite`），场景经 `FlinkProcessRegistrar.register` 翻译成 Flink job；处理模式三档 `RequestResponse`/`BoundedStream`/`UnboundedStream`（`ProcessingMode.scala:12`/`:13`/`:14`）。
- **表达式层**：SpEL `Standard`/`Template`（`SpelFlavour.scala:11`/`:13`），SQL 字符串本身以 `spelTemplate` 形式写在参数里（测试 `"flinkSqlQuery" -> "SELECT * FROM record".spelTemplate`，`FlinkSqlQueryComponentTest.scala:87`）——**SQL 被当作可插值的模板**，能引用场景变量。
- **与 nop 对照**：nop 的声明形态是 `.stream.xml`（XDSL）顶层 `<sql sinkBean>`，与 Nussknacker 的「节点参数 `flinkSqlQuery`」在**声明位置**上不同（顶层 vs 节点内），但在**语义**上同为「低代码 DSL 内嵌一段流 SQL」。nop 的「一段 SQL = 一张子图」更接近 Nussknacker 的单 SQL 节点；nop 若要支持「一张场景图里多段 SQL」，Nussknacker 的节点式嵌入是现成模型。

### 9. 架构要点（值得记的设计）

1. **SQL → Flink job 的翻译器在一处**：`FlinkProcessRegistrar`（`FlinkProcessRegistrar.scala:52`，`:47` 注释「main class where we translate Nussknacker model to Flink job」），按 `registerSourcePart`（`:165`）→ `registerJoinPart`（`:185`）→ `registerCustomNodePart`（`:331`）→ `registerSinkPart`（`:278`）→ `registerInterpretation`（`:478`）分段注册，**算子 uid 用 node id**（保证 savepoint 能对上状态）。这与 nop 的「EQL AST → `StreamSqlCompiler` → 流模型 XML」是同一种「单一编译入口」结构。
2. **`record` 虚表 + 系统行时间列是嵌入式 SQL 的最小充分条件**：`contextTableName = "record"`（`QueryContextSchema.scala:49`）、`record_time`（`:50`）、`SOURCE_WATERMARK()`（`:43`）、保留名冲突报错（测试 `:181`）。**四件事缺一不可**——少了任何一件，嵌入的 SQL 要么没有时间语义、要么会污染用户变量空间。
3. **DDL 只放行声明式语句**：`FlinkDdlParser` 只接受 `CREATE CATALOG` / `CREATE TABLE`，拒绝 CTAS 与其它（`FlinkDdlParser.scala:75`/`:89`），随后 `FlinkDataDefinition.registerIn` 用 `tableEnvironment.executeSql(sql)` 幂等注册（`FlinkDataDefinition.scala:32`/`:42`）。**「数据定义只能声明、不能产生数据」是一条可直接抄的约束**，与 nop 的「DDL 只声明数据源、查询只能出现在 `<sql>`」同构。
4. **rowtime 列唯一性在数据定义期校验**：`TableDefinition.scala:38`（行时间列提示）、`:43`（`No rowtime column found`）、`:48`（`More than one rowtime column found`）——**声明期就拦住多行时间列**，避免运行期歧义。
5. **查询结果的 changelog 模式在编译期校验**：`validateTableChangelogMode`（`FlinkSqlQueryComponentFactory.scala:145`/`:154`）→ 不通过转 `CustomNodeError`。**这是 nop「D1 语义降级」的可比物**：Nussknacker 选择在编译期把「查询产生 retract」这类不被下游接受的形态挡掉。

### 10. 与 nop-stream/nop-stream-sql 的对照与可借鉴点

- **对 `nop-stream-sql` 的直接可借鉴项**：
  1. **嵌入回路五步**：`StreamTableEnvironment.create` → `Context.toRow` → `createTemporaryView(record, fromDataStream(..., schemaWithEventTime))` → `sqlQuery(sql)` → `toDataStream` 回流。nop 已有 EQL→流模型的编译链，缺的是**「时间列如何从事件派生」的显式一步**；Nussknacker 的 `record_time` + `SOURCE_WATERMARK()` 给出了最小实现。
  2. **保留名保护**：`record_time` 是系统列，用户同名变量直接报错（测试 `:181`）。nop 的 TUMBLE 时间参数 `t` 也应有同类保留名保护，建议核对是否已做。
  3. **DDL 三类白名单**（只许声明、不许产生数据）+ **幂等注册**（`registerIn`/`executeSql`）+ **rowtime 唯一性校验**——这三条可直接写进 nop 数据源声明的校验规则。
  4. **SQL 是图中节点**：若 nop 未来要「一张场景图多段 SQL」，Nussknacker 的节点式嵌入（SQL 参数 + 编译期类型推导 + changelog 校验）是完整参考实现。
  5. **聚合策略面**：`Aggregator` 抽象与 `#AGG.*` 枚举比 nop 的五聚合宽（median/countWhen/方差/map 组合），可作为 nop 放宽聚合面时的清单。
- **不借鉴 / 需谨慎**：
  - `MATCH_RECOGNIZE` 出现在 SQL 面（`:416`）——与 nop「CEP 不进 SQL 面」的裁定冲突，**维持 nop 的收窄**；
  - SQL 以 `spelTemplate` 形式写在节点参数里——引入 SpEL 模板插值，nop 侧对应物是 XDSL 表达式插值，**不要为抄而引入 SpEL 依赖**。
- **语义边界澄清**：Nussknacker 底层是 Flink SQL，因此它天然具备 retract / update-before 语义；**但其低代码组件路（base-unbounded）并不暴露 retract**——场景图里流转的是 `Context` 而非 changelog。这印证 nop 的 D1 裁定是**可以与「底层支持 retract」并存的**：能力可以存在于引擎层，但 DSL 面不承诺。

## 对照 nop-stream-sql 已交付子集

**入口形态**

| nop-stream-sql | Nussknacker | 差异 / 可借鉴 |
|---|---|---|
| `.stream.xml` 顶层 `<sql sinkBean>`，EQL 共享 ORM parser | 节点参数 `flinkSqlQuery`（`FlinkSqlQueryComponentFactory.scala:45`），底层 `StreamTableEnvironment.sqlQuery` | 位置不同（顶层 vs 节点内），语义同为「DSL 内嵌流 SQL」。可借鉴：节点式嵌入可让 nop 支持「一图多 SQL」；当前 nop 单 SQL 更简单，**维持现状即可**。 |
| 内嵌 schema：9 受管类型闭集（string/int/bigint/smallint/tinyint/float/double/boolean/bytes） | 场景 `ValidationContext` 派生 + 系统列 `record_time TIMESTAMP_LTZ(3)`（`QueryContextSchema.scala:42`） | nop 类型面是**显式闭集**（编译期 fail-fast），Nussknacker 类型面由 SpEL 类型推导开放决定。**闭集更利于跨方言**（RDBMS/流双目标），建议保留。 |
| FROM 表名 = 数据源 bean 名 | 虚表名固定 `record`（`QueryContextSchema.scala:49`），物理表经 DDL `CREATE TABLE` 声明 | nop 的 FROM 是**已注册 bean**（无 DDL 步骤）；Nussknacker 分「虚拟 record 表」与「DDL 声明的物理表」两层。可借鉴：**两层分离**让「查流」与「查外部表」语法统一（都 `FROM <table>`）。 |

**支持清单逐条**

| nop 支持项 | Nussknacker 对应 | 差异 / 可借鉴 |
|---|---|---|
| SELECT 投影 / WHERE | `SELECT input FROM record` 等（测试 `:87`） | 等价。 |
| 五聚合（sum/count/avg/min/max）+ COUNT(*) | 组件路聚合集更宽（`aggregates.scala`：+median/countWhen/stddev/var/list/set/map 组合）；SQL 路 `GROUP BY ... TUMBLE`（测试 `:259`/`:272`） | nop 面窄但 fail-fast 清晰；放宽时参考 `Aggregator` 接口形状。 |
| GROUP BY + TUMBLE(t, INTERVAL)（SECOND..WEEK，bigint epoch-millis） | `TUMBLE(TABLE record, DESCRIPTOR(record_time), INTERVAL '1' SECOND)`（测试 `:385`） | 语法同源（Flink SQL 风格）；时间列类型不同（`TIMESTAMP_LTZ(3)` vs bigint millis）。 |
| 双流等值 join（INNER/LEFT/RIGHT/FULL，watermark 补齐） | 组件路 `single-side-join`（`SingleSideJoinTransformer.scala:75`）/ `full-outer-join`（`:253`）；SQL 路自由 join | nop 在 SQL 面做 join 且有补晚策略；Nussknacker 两条路，组件路带 `stateTimeout`。 |
| 静态维表 lookup join（`ITableLookup`→`IJdbcTemplate`/`IBatchLoader`） | 独立 enricher 节点 `DatabaseLookupEnricher`（`:50`），`SELECT * FROM t` + `WHERE key = ?`（`:101`/`:135`），带 `cacheTTL`（`:17`） | 形态不同（SQL 内 vs 节点）。可借鉴：`cacheTTL` + 三种结果策略（`QueryResultStrategy.scala:29`/`:33`/`:37`）。 |
| UNION ALL | 组件 `union-memo`（`FlinkBaseUnboundedComponentProvider.scala:55`）+ SQL `UNION ALL` | 等价。 |
| 无窗口持续 GROUP BY | 组件路 sliding/session（`:56`/`:61`）与 SQL 持续聚合 | 等价。 |
| 分析窗口 OVER（ROW_NUMBER）+ FRAME_SLIDING_AGG | SQL `OVER ... ORDER BY record_time`（测试 `:302`） | 等价。 |

**不支持清单逐条**

| nop 不支持（fail-fast） | Nussknacker 状态 | 结论 |
|---|---|---|
| 全局 ORDER BY/LIMIT | Flink SQL 支持，Nussknacker 不额外限制 | **差异项**：nop 有意收窄（全局排序在流上无界），维持 fail-fast。 |
| 非等值/范围 join | Flink SQL 支持 | **差异项**：nop 收窄，维持。 |
| DATE/TIMESTAMP/DECIMAL 列绑定 | Flink SQL 支持，Nussknacker 用 `TIMESTAMP_LTZ(3)` | **差异项**：nop 的 9 类闭集是跨方言约束（RDBMS 目标），维持。 |
| CASE/CAST/IN 子查询 | Flink SQL 支持 | **差异项**，维持。 |
| DISTINCT 聚合、HAVING、CTE、INTERSECT/EXCEPT、全局聚合、FULL window join、UNION DISTINCT、Flink SQL 方言 | Flink SQL 全支持，Nussknacker 不做子集裁剪（只做 changelog/类型校验） | **关键差异**：Nussknacker **不裁剪 SQL 面**，靠「编译期类型推导 + changelog 校验」保证正确性；nop 靠**显式白名单**保证正确性与双方言可行。**两者都是编译期约束，机制不同**——nop 的白名单是「为双目标服务」的，不应为对齐而放弃。 |
| CEP 级复杂编排不进 SQL 面 | `MATCH_RECOGNIZE` 可用（测试 `:416`） | **差异项**：nop 维持「CEP 归 `nop-stream-cep`」，继续拦截 `MATCH_RECOGNIZE`。 |
| **retract/CDC（D1 语义降级）** | 引擎层有 retract（Flink），**DSL 组件路不暴露**；SQL 节点经 `validateTableChangelogMode` 校验（`FlinkSqlQueryComponentFactory.scala:145`） | **最佳佐证**：「底层支持 retract」与「DSL 面不承诺 retract」可以并存。nop 的 D1 裁定得到独立样本支持。 |

**语义标注（D1/D6/D9/D10/D11）**

- D1（last-value-wins，无 RowKind/无 retract）：Nussknacker 组件路同为「不暴露 retract」，SQL 路靠 changelog 模式校验兜底——**同构**，维持 D1。
- D6（事件时间，bigint epoch-millis）：Nussknacker 用 `TIMESTAMP_LTZ(3)` + `SOURCE_WATERMARK()`（`QueryContextSchema.scala:42`/`:43`）；nop 用 bigint millis。**时间语义一致（事件时间 + 水位线），表示形式不同**；若 nop 要引入「系统时间列」，可借 `record_time` 的保留名设计，但**不改 D6 的 bigint 契约**。
- D9（allowedLateness 已放行）：Nussknacker 无显式 allowedLateness 面，迟到事件丢弃（测试 `:371`）。nop 更宽，**维持 D9**。
- D10/D11（保持 fail-fast）：Nussknacker 不做子集裁剪、只做校验；nop 的白名单是 fail-fast 的实现手段，**维持**。
- retract/CDC 全链路：**维持 D1 裁定**，并新增佐证「DSL 面可与引擎能力解耦」（见上表）。

**架构对照**

- nop：`EQL AST → StreamSqlCompiler → 流模型 XML`，三注册表（schemas/aggregators/joins），双目标（RDBMS 方言 / 流管线）。
- Nussknacker：`CanonicalProcess 节点图 → FlinkProcessRegistrar`（`:52`，分 source/join/customNode/sink 段注册，算子 uid = node id）→ Flink job；组件经 `ComponentProvider` 注册表（`baseUnbounded` / `flinkSqlOps`）。
- 关键差异：nop 是**单入口编译 + 双目标**，Nussknacker 是**多段注册 + 单目标（Flink）**；nop 需要维持 RDBMS 方言可达，因此必须保留白名单。可借鉴的是 Nussknacker 的**注册表形状**（`ComponentDefinition` 名 → 实现 + docs 链接）与 **uid/node-id 绑定的 savepoint 友好设计**。

## 引用出处

以下路径均已在本地源码库 `/Users/abc/sources/streaming-sql/nussknacker` 逐条验证存在；行号取自实际读取内容。

- `/Users/abc/sources/streaming-sql/nussknacker/README.md:28`（low-code visual tool）、`:38`（streaming + request-response 两模式）、`:49`（SpEL 表达式）、`:126`（Apache License 2.0）
- `/Users/abc/sources/streaming-sql/nussknacker/version.sbt:1`（`1.19.0-SNAPSHOT`）
- `/Users/abc/sources/streaming-sql/nussknacker/build.sbt:265`（`flinkV = "1.20.3"`）、`:266`（`flinkConnectorKafkaV = "3.4.0-1.20"`）
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/sql/FlinkSqlQueryComponent.scala:13`、`:21`、`:24`、`:27`、`:32`、`:36`、`:46`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/sql/FlinkSqlQueryComponentFactory.scala:36`、`:45`、`:47-48`、`:62`、`:73`、`:113`、`:129`、`:145`、`:154`、`:160`、`:173`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/sql/QueryContextSchema.scala:39`、`:42`、`:43`、`:48`、`:49`、`:50`、`:61`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/sql/FlinkSqlOpsComponentProvider.scala:7`、`:9`、`:20-23`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/io/definition/FlinkDdlParser.scala:10`、`:11`、`:24`、`:39`、`:57`、`:75`、`:76`、`:89`、`:102`、`:103`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/io/definition/FlinkDataDefinition.scala:18`、`:32`、`:42`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/io/definition/TableDefinition.scala:38`、`:43`、`:48`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/io/FlinkTableIOComponentProvider.scala:25`、`:46`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/table/src/main/scala/pl/touk/nussknacker/engine/flink/table/io/source/TableSource.scala:67`、`:77`、`:79`、`:102`、`:207`
- `/Users/abc/sources/streaming-sql/nussknacker/scenario-api/src/main/scala/pl/touk/nussknacker/engine/graph/node.scala:178`、`:193`、`:207`、`:221`、`:287`、`:300`
- `/Users/abc/sources/streaming-sql/nussknacker/scenario-api/src/main/scala/pl/touk/nussknacker/engine/canonicalgraph/CanonicalProcess.scala:86`
- `/Users/abc/sources/streaming-sql/nussknacker/scenario-compiler/src/main/scala/pl/touk/nussknacker/engine/spel/SpelFlavour.scala:7`、`:11`、`:13`
- `/Users/abc/sources/streaming-sql/nussknacker/components-api/src/main/scala/pl/touk/nussknacker/engine/api/component/ProcessingMode.scala:12`、`:13`、`:14`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/FlinkBaseUnboundedComponentProvider.scala:21`、`:55`、`:56`、`:59`、`:61`、`:64`、`:67`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/sampleTransformers.scala:22`、`:30`、`:54`、`:65`、`:87`、`:98`、`:122`、`:130`、`:156`、`:167`、`:191`、`:193`、`:203`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/aggregates.scala:25`、`:46`、`:58`、`:70`、`:104`、`:132`、`:159`、`:183`、`:209`、`:272`、`:390`、`:421`、`:521`、`:568`、`:579`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/triggers.scala:9`、`:30`、`:53`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/join/SingleSideJoinTransformer.scala:75`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/join/FullOuterJoinTransformer.scala:253`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/UnionTransformer.scala:85`、`:122`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/DeduplicationTransformer.scala:43`
- `/Users/abc/sources/streaming-sql/nussknacker/utils/components-utils/src/main/scala/pl/touk/nussknacker/engine/util/watermarkstrategy/WatermarkStrategyOptions.scala:8`、`:10`、`:11`、`:12`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/executor/src/main/scala/pl/touk/nussknacker/engine/process/registrar/StreamExecutionEnvPreparer.scala:67`、`:88`、`:95`、`:96`、`:102`、`:107`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/executor/src/main/scala/pl/touk/nussknacker/engine/process/registrar/FlinkProcessRegistrar.scala:47`、`:52`、`:165`、`:185`、`:278`、`:331`、`:478`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/executor/src/main/resources/reference.conf:3`（`checkpointInterval: 10m`）
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/kafka/PartitionByKeyFlinkKafkaSink.scala:20`、`:27`、`:32`、`:35`、`:39`、`:40`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/management/src/main/scala/pl/touk/nussknacker/engine/management/FlinkDeploymentManager.scala:115`、`:121`、`:129`、`:157`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/management/src/main/scala/pl/touk/nussknacker/engine/management/jobrunner/FlinkScenarioJobRunner.scala:10`、`:12`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/executor/src/main/scala/pl/touk/nussknacker/engine/process/runner/FlinkScenarioMain.scala:22`、`:24`、`:45`
- `/Users/abc/sources/streaming-sql/nussknacker/components/sql/src/main/scala/pl/touk/nussknacker/sql/service/DatabaseLookupEnricher.scala:17`、`:18`、`:26`、`:28`、`:50`、`:84`、`:101`、`:135`
- `/Users/abc/sources/streaming-sql/nussknacker/components/sql/src/main/scala/pl/touk/nussknacker/sql/db/query/QueryResultStrategy.scala:17`、`:29`、`:33`、`:37`
- `/Users/abc/sources/streaming-sql/nussknacker/engine/flink/components/base-tests/src/test/scala/pl/touk/nussknacker/engine/flink/table/sql/FlinkSqlQueryComponentTest.scala:87`、`:181`、`:199`、`:259`、`:272`、`:302`、`:371`、`:385`、`:416`、`:430`

未在本仓库验证：

- `validateTableChangelogMode` 的**具体判定逻辑**（何种 changelog 模式会被拒绝）仅按方法名与调用位置推断其用途，**内部实现未逐行核对**。
- `FlinkSqlQueryComponentTest.scala` 中窗口 Top-N / OVER 用例的完整断言（`:302`/`:319` 附近）只读取到 SQL 文本片段，**断言细节未逐条核对**。
- lite 引擎（`engine/lite/`）与 designer 前端（`designer/client/`）未深入，本文关于「SQL 仅在 Flink 引擎侧」的结论基于 `engine/flink/components/table/` 的目录归属与模块划分，**未逐模块穷举验证**。
- `README.md` 关于成本/领域专家的论述段落为产品叙述，**未在代码中验证**。
