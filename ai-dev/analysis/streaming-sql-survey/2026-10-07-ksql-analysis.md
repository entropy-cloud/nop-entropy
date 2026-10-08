# ksqlDB streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: 最值得借鉴两点——(1) **push / pull 双查询形态 + `EMIT CHANGES|FINAL` 输出精化作为一级语法**，把"持续发射"与"窗口终态只发一次"变成用户可见的显式契约（nop-stream-sql 当前只有 D1 last-value-wins 单一输出，无对应开关）；(2) **把 WHERE 谓词编译成 `LookupConstraint` 三档（key 等值 / range / 全表扫描）的约束建模**，可直接搬到 nop-stream-sql 的维表 lookup join 与 join key 约束校验上。

## Context

ksqlDB 是 Confluent 的 "SQL-on-Kafka" 流式 SQL 引擎：用户写 SQL，服务端编译成 Kafka Streams 拓扑常驻执行，并提供 push query（持续）、pull query（点查）与物化视图式 state store 查询。它是"流 SQL 直接落地到已有流引擎"这条路线的成熟样本，与 nop-stream-sql 的 `<sql sinkBean>` → 流管线编译目标同构，因此列入本调研。本文只读本地源码 `/Users/abc/sources/streaming-sql/ksql`（pom 版本 `8.5.0-0`，HEAD `7c0049a0`），重点 `ksqldb-engine` 与 `ksqldb-parser`，并交叉阅读同仓库 `docs/` 语义说明。

## 项目概览

- **定位**：事件流上的交互式 SQL 引擎（persistent query + transient query + materialized table 点查），不是批处理 ETL。
- **语言**：Java（ANTLR4 语法 + Java 执行层）；前端仓库另有 JS CLI/控制台。
- **许可证**：Confluent Community License 1.0（`pom.xml:41-44`、`README.md:143-145`）——非 Apache-2.0，借鉴语义设计可行，复制代码需注意。
- **演进状态**：活跃维护，8.5.x；引擎内并存两套 planner——默认路径（`planner/` + `physicalplanner` 执行步）与实验性新 planner（`logicalplanner/` + `physicalplanner`，由 `ksql.new.query.planner.enabled` 门控，且大量子句尚未支持，见 §9.3）。
- **SQL 能力入口模块**：
  - 语法：`ksqldb-parser/src/main/antlr4/io/confluent/ksql/parser/SqlBase.g4`（673 行）+ `ksqldb-parser/src/main/java/io/confluent/ksql/parser/AstBuilder.java`（1728 行）
  - 分析/校验：`ksqldb-engine/src/main/java/io/confluent/ksql/analyzer/`（`Analyzer` 773 行、`QueryAnalyzer`、`PullQueryValidator`、`AggregateAnalyzer`）
  - 规划：`ksqldb-engine/src/main/java/io/confluent/ksql/planner/`（`LogicalPlanner` 1221 行 + `plan/*Node`）
  - 执行步 → Streams：`ksqldb-execution/.../execution/plan/`（43 个 `ExecutionStep`）+ `ksqldb-streams/.../execution/streams/KSPlanBuilder.java`

## 十维度分析

### 1. SQL 前端：parser/planner 自研还是复用

**完全自研，不使用 Calcite。** 一个 ANTLR4 组合语法 `SqlBase.g4` 同时覆盖 DDL/DML/查询/运维指令，`AstBuilder` 直接产出自有 AST（`parser/tree/*`，119 个 Java 文件），表达式类型在 `ksqldb-execution/.../execution/expression/tree/` 下单独一套。

- 语句入口：`SqlBase.g4:47-48` `statement : query #queryStatement`；DDL 见 `:75-88`（`#createStream` / `#createStreamAs` / `#createTable` / `#createTableAs` / `#insertInto` / `#insertValues`）。
- 查询规则（子句顺序即解析顺序，**`EMIT` 是查询的一等子句**）：`SqlBase.g4:118-132`
  ```
  query : SELECT ... FROM relation (WINDOW windowExpression)? (WHERE ...)? (GROUP BY ...)?
         (PARTITION BY ...)? (HAVING ...)? (EMIT resultMaterialization)? limitClause? ;
  resultMaterialization : CHANGES | FINAL ;
  ```
- 表达式子集极大：CASE/CAST/ARRAY/MAP/STRUCT 构造、subscript、lambda、`IS DISTINCT FROM`、`AT TIME ZONE`（`SqlBase.g4:300-335`），远超 nop-stream-sql 的标量子集。
- 分析层不是"AST→plan"一步到位，而是 `QueryAnalyzer`（`analyzer/QueryAnalyzer.java:35-48`）内部组合 `Analyzer` + `PullQueryValidator` + `PushQueryValidator`，先产出 `Analysis` 再交给 planner。

**与 nop-stream-sql 对照**：nop 走的是"复用 EQL parser（与 ORM 同口径）+ 独立 `StreamSqlCompiler`"路线，语法面窄但单一口径；ksqlDB 自研 ANTLR 语法维护成本高（语法+AST+两套 planner 都要跟），但换来子句顺序、`EMIT`、`PARTITION BY` 这类流特性的一等公民地位。

### 2. Table & Changelog 模型

- **stream / table 双模型是核心**：`CREATE STREAM` = 分区的、append-only 的不可变集合（`docs/concepts/streams.md:9-15`：现有行不可更新/删除）；`CREATE TABLE` = 按 key 的 changelog / upsert 视图（`docs/concepts/tables.md`，含 tombstone 删除与后台 compact）。
- **DDL 变体**：`CREATE STREAM/TABLE`（声明外部 topic）与 `CREATE ... AS SELECT`（物化 + 生成输出 topic），另有 `CREATE OR REPLACE`、`DROP ... (DELETE TOPIC)`、`ALTER ... ADD COLUMN`（`SqlBase.g4:75-86`）。
- **INSERT INTO 语义**（`docs/developer-guide/ksqldb-reference/insert-into.md:17-45`）：`INSERT INTO <stream> SELECT ...` 与 `INSERT INTO <stream> VALUES ...`；schema 与 key 必须匹配，否则报错；`PARTITION BY` 在 JOIN/WHERE 之后、SELECT 之前生效。注意 INSERT INTO 目标限于 Stream（文档明确 "Tables are not supported" 针对该语句形态）。
- **聚合输出天然是 KTable（changelog）**：`AggregateNode` 的 `super(..., DataSourceType.KTABLE, ...)`（`planner/plan/AggregateNode.java:94`）——group-by 聚合产生可被 retract/update 的中间结果；对表源的聚合走 `KudafUndoAggregator`（`ksqldb-execution/.../function/udaf/KudafUndoAggregator.java:29`），即 UDAF 显式支持 **undo/retract**。
- **无全局聚合**：`AggregateNode.java:188-189` 构建期抛 `"CREATE TABLE AS SELECT statement does not support aggregate function ... without a GROUP BY clause."` —— 与 nop-stream-sql 的"全局聚合 fail-fast"同向。

### 3. 时间语义

- **时间列可声明**：`WITH (TIMESTAMP='col', TIMESTAMP_FORMAT='...')` 覆盖 `ROWTIME` 伪列（`ksqldb-common/.../properties/with/CommonCreateConfigs.java:39-40`；`docs/reference/sql/time.md:24-36`）。未声明时 `ROWTIME` = Kafka record timestamp。
- **grace period（late data 界）是窗口/连接的显式子句**：`GRACE PERIOD number windowUnit`（`SqlBase.g4:181-182`），可挂在 TUMBLING/HOPPING/SESSION（`:191/:195/:199`）与 join `WITHIN`（`:257/:258`）上。
- **默认 grace = 24 小时**（`docs/concepts/time-and-windows-in-ksqldb-queries.md:405`、`docs/developer-guide/ksqldb-reference/select-push-query.md:268`）；越过 grace 的记录视为 *late* 且不进入该窗口（同文件 :397）。注意实现里另有一个 `zeroGracePeriod`（`analyzer/RewrittenAnalysis.java:65-66,149-153`），但它**仅在 `EMIT FINAL` 且用户未写 grace 时**注入——保证 suppress 能立即关窗。
- **没有显式 watermark 声明、没有 idle 检测的 SQL 语句面**：watermark/grace 全部下沉为 Kafka Streams 行为；配置经 `ksql.streams.*` 前缀透传（`KsqlConfig.java:248`）。这一点与 nop-stream-sql（`allowedLateness` D9 已放行、watermark 由产物自动补）方向一致：**都不把 watermark 做成 SQL 子句**。
- 时间单位限 `DAYS/HOURS/MINUTES/SECONDS/MILLISECONDS`（`docs/reference/sql/time.md:8-17`）——无日历单位，与 nop 的 `INTERVAL` 单位闭集同构。

### 4. 窗口与聚合

**窗口类型**（`SqlBase.g4:185-200`）：

| 类型 | 语法 | 备注 |
|---|---|---|
| 翻滚 | `TUMBLING(SIZE n U[, RETENTION ..][, GRACE PERIOD ..])` | `:190-192` |
| 跳跃 | `HOPPING(SIZE n U, ADVANCE BY n U, ...)` | `:194-196` |
| 会话 | `SESSION(n U[, RETENTION ..][, GRACE ..])` | `:198-200` |

- **named window 是真实语法**：`windowExpression : (IDENTIFIER)? (tumbling|hopping|session)WindowExpression`（`SqlBase.g4:185-188`），`AstBuilder.java:496-520` 把标识符大写后塞进 `WindowExpression.windowName`，默认名 `DEFAULT_WINDOW_NAME = "StreamWindow"`（`AstBuilder.java:234`）。功能测试里确有 `WINDOW nWINDOW SESSION (1 SECONDS)` 形态（`ksqldb-functional-tests/src/test/resources/query-validation-tests/earliest-offset-udaf.json:315`）。**但**：全仓库 `getWindowName()` 仅 3 处调用（`RewrittenAnalysis.java:193`、`StatementRewriter.java:351`、AST 自身），未观察到它进入物理计划或状态存储命名——`windowName` 目前更像"AST 保真字段"。此点标注为**未在本仓库验证其运行期用途**。
- **suppress = `EMIT FINAL`**：语法层只有 `CHANGES | FINAL`（`SqlBase.g4:130-132`），`FINAL` 被规划为独立的 `SuppressNode`（`planner/plan/SuppressNode.java:33-40` 的类注释明确说明："added to the logical plan if the user specifies ... EMIT FINAL"，且必须位于 output node 之前），物理层落到 Kafka Streams `suppress(...).untilWindowCloses(strictBufferConfig)`（`ksqldb-streams/.../streams/TableSuppressBuilder.java:115-117`）。要求输入必须是 `SchemaKTable`，否则报错（`SuppressNode.java:75-80`）。
- **聚合函数集**（`docs/developer-guide/ksqldb-reference/aggregate-functions.md`，17 个）：`AVG / COUNT / SUM / MIN / MAX / COUNT_DISTINCT / COLLECT_LIST / COLLECT_SET / CORRELATION / EARLIEST_BY_OFFSET / LATEST_BY_OFFSET / HISTOGRAM / STDDEV_SAMP / STDDEV_SAMPLE / TOPK / TOPKDISTINCT`。实现分布于 `ksqldb-engine/.../function/udaf/**`（如 `count/CountDistinct.java`、`topk/TopkKudaf.java`、`map/HistogramUdaf.java`）。
- **DISTINCT 聚合、ORDER BY/LIMIT 输出排序**：UDAF 走 `COUNT_DISTINCT` 这类显式函数而非 `COUNT(DISTINCT x)` 语义糖；push query 支持 `LIMIT`（查询规则里 `limitClause?`），pull query 的 LIMIT 需 `ksql.query.pull.limit.clause.enabled=true`（`PullQueryValidator.java:43-45`）。
- **HAVING 有**（`SqlBase.g4:125`、`AggregateNode.java:174`）；`GROUP BY` 要求 SELECT/HAVING 中必须出现聚合（`AggregateAnalyzer.java:245`）。
- **TopN**：无排名窗口函数（无 `ROW_NUMBER() OVER`）；用 `TOPK(col, k)` UDAF 返回数组近似。

### 5. Join

- **连接类型**：`INNER / LEFT [OUTER] / RIGHT / [FULL] OUTER`（`SqlBase.g4:244-250`）。
- **仅等值条件**：`Analyzer.java:372-375` —— 非 `ComparisonExpression(EQUAL)` 直接抛 `"Only equality join criteria is supported."`，且单个比较表达式（不支持 `a.x=b.x AND a.y=b.y` 复合键）。
- **stream-stream 必须带 `WITHIN`**：`JoinNode.java:423-429` 显式报错；`WITHIN` 支持对称 `(<before>, <after>)` 或单值，均可带 `GRACE PERIOD`（`SqlBase.g4:256-262`），AST 落到 `WithinExpression`（含 `gracePeriod` 字段，`parser/tree/WithinExpression.java:37`），物理层转 Kafka `JoinWindows`。
- **table-table 支持外键 join（FK join）**：`JoinNode.java:550-576` 走 `leftTable.foreignKeyLeftJoin/foreignKeyInnerJoin`，`RIGHT OUTER JOIN on a foreign key is not supported`（`:566`）；投影必须包含外键列（`:262`）。这实质是 **lookup join 的流式版本**。
- **多路 join**：`JoinTree` 把二元 join 串成链，左右可 flip（`Analyzer.java:393-396` 注释说明 flipped 语义）。
- **pull query 的 join 单独禁止**：`PullQueryValidator.java:55-58` "Pull queries don't support JOIN clauses."；同样禁 `WINDOW/GROUP BY/PARTITION BY/HAVING/EMIT/INTO`（`:59-81`）。**pull query 可以查"包含这些子句的物化表"**——即查询的是已物化的 state store，而不是在查询里重新聚合（`docs/developer-guide/ksqldb-reference/select-pull-query.md:42`）。
- **pull query 的 WHERE 被编译成约束**：`planner/plan/QueryFilterNode.java:228-262` 把谓词抽取为 `LookupConstraint`——key 等值 → `KeyConstraint(EQUAL)`，`< / <= / > / >=` → 对应 `ConstraintOperator`，其余 → `NonKeyConstraint`；计划类型枚举 `PullPhysicalPlanType { KEY_LOOKUP, RANGE_SCAN, TABLE_SCAN, UNKNOWN }`（`execution/pull/PullPhysicalPlan.java:186-191`）。默认只开 key 等值，范围/全表扫描需 `ksql.query.pull.table.scan.enabled=true`（同上文档 :45-50）。
- **非等值 join、子查询、CTE**：语法与分析层均无（无 `LATERAL`/`CORRELATED` 子查询规则）。

### 6. CEP / 模式匹配

**没有。** 语法无 `MATCH_RECOGNIZE`/`PATTERN`，engine 无 CEP 包。ksqlDB 的"模式"只能靠连续 push query + 外部应用状态表达。nop-stream 的 CEP 模块在此维度是超出项。

### 7. 状态与容错

- **状态存储**：全部复用 Kafka Streams —— RocksDB（配套模块 `ksqldb-rocksdb-config-setter/`）与 in-memory；窗口/会话 store、aggregate store 均由 `ksqldb-streams/.../execution/streams/` 下的 `*Builder` 通过 `Materialized` 建立（如 `StreamAggregateBuilder.java:278-339` 传 `window.getGracePeriod()`）。
- **changelog**：状态 store 自动带 changelog topic；sink 侧 windowed 结果额外有 retention 配置（`KsqlConfig.java:1045` `defaultSinkWindowChangeLogAdditionalRetention`）。
- **输出语义**：`processing.guarantee` 支持 `at_least_once` / `exactly_once_v2`（经 `ksql.streams.*` 透传，`ValidatedCommandFactory.java:167` 在变更该属性时要求停掉持久查询；`docs/operate-and-deploy/exactly-once-semantics.md:9-26`）。默认 at-least-once。
- **一致性边界**：DDL/DML 先写 command topic（`Command`），恢复时重放——`QueryPlan`（`engine/QueryPlan.java:32-41`，字段 `sources / sink / physicalPlan / queryId / runtimeId`）是可序列化的持久化计划。
- **pull query 的容错**：standby 读可开（`KsqlConfig.java:284-297` `ksql.query.pull.enable.standby.reads`），并有 HA 路由 `execution/pull/HARouting.java` 把请求路由到 active/standby 副本。

### 8. 声明形态与可嵌入性

三种形态共用同一 `KsqlEngine`：

1. **服务端 + REST/CLI**：`ksqldb-rest-app`（含 command topic、鉴权、HA pull query）、`ksqldb-cli`（交互式）、`ksqldb-ksqldb-api-client`。
2. **嵌入库**：`ksqldb-engine/.../embedded/KsqlContext.java:65`（`implements AutoCloseable`，`create(...)` + `execute(...)`，`:78`、`:300`）——可作为 JVM 内库直接驱动。
3. **持久查询即声明**：`CREATE STREAM/TABLE AS SELECT` 编译为常驻查询并注册到 `QueryRegistry`（`engine/QueryRegistry.java` in `query/`）；`EXPLAIN/SHOW QUERIES/TERMINATE/PAUSE/RESUME` 管理（`SqlBase.g4:64-68`）。

**可嵌入性结论**：`ksqldb-engine` + `ksqldb-parser` + `ksqldb-streams` 是明确可拆的库形态（`KsqlContext` 就是证据），但强依赖 Kafka/Kafka Streams 运行时与 Confluent 许可证，nop-stream-sql 不能直接复用代码，只能复用语法与规划形态。

### 9. 架构要点（SQL → 执行）

**9.1 主链路（默认 planner）**

```
文本
 → KsqlEngine.parse()      (KsqlEngine.java:285)
 → KsqlEngine.prepare()    (:290)      PreparedStatement<?> = AST + 语句类型
 → KsqlEngine.plan()       (:298)      KsqlPlan{ DDL?, QueryPlan }
 → KsqlEngine.execute()    (:308)
     → EngineExecutor.planQuery()      (EngineExecutor.java:743-837)
         QueryAnalyzer.analyze → Analysis
         QueryEngine.buildQueryLogicalPlan → OutputNode（PlanNode 树）
         QueryIdUtil.buildId → QueryId
         QueryEngine.buildPhysicalPlan → ExecutionStep<?> 树（可序列化）
     → EngineExecutor.executePersistentQuery(queryPlan, ...)  (:1132)
         KSPlanBuilder（ksqldb-streams）把 ExecutionStep 翻成 Kafka Streams DSL → Topology → KafkaStreams
```

值得记的设计：

1. **"可序列化物理计划"是契约，不是中间产物**：`ksqldb-execution/.../execution/plan/` 的 43 个 `ExecutionStep`（`StreamSource/StreamFilter/StreamSelect/StreamGroupBy/StreamWindowedAggregate/StreamStreamJoin/TableSuppress/StreamSink/...`）带版本与 `PlanInfo`，能写进 command topic、能在恢复时与旧计划做 diff（`EngineExecutor.java:818-833` 用 `oldPlanInfo` 比较）。**对 nop 的启发**：把 `<sql>` 展开后的流模型 XML 视为"可持久化的物化计划"，版本化它，升级时才能做 plan diff 而不是重跑。
2. **DDL 与 DQL 统一进 `KsqlPlan`**，`EngineExecutor.execute()` 先判 `plan.getQueryPlan().isPresent()`（`:178-183`）决定走 DDL 还是查询分支——单入口单恢复语义。
3. **push/pull 是"同一 Analysis + 不同 Validator + 不同 PhysicalPlan"**，而不是两套语法：`QueryAnalyzer` 注入 `PullQueryValidator` / `PushQueryValidator`（`analyzer/QueryAnalyzer.java:35-48`），pull 分支另建 `PullPhysicalPlan`（`EngineExecutor.java:960` `buildPullPhysicalPlan`），push 另有 scalable push（`:945` `buildScalablePushPhysicalPlan`）。

**9.2 `EngineExecutor.throwIfUnsupported` 是能力边界的执行体**（`EngineExecutor.java:705-741`）：新 planner 下 WHERE/GROUP BY/HAVING/WINDOW/PARTITION BY/LIMIT/JOIN 逐条抛 `UnsupportedOperationException` 并回指配置开关。**这与 nop-stream-sql 的构建期 fail-fast 清单是同一套工程手法**——把"不支持"写成带开关的显式错误，而不是静默降级。

**9.3 `Analyzer` 是子句级校验中心**（`analyzer/Analyzer.java:274-335`）：对 WHERE/GROUP BY/PARTITION BY/HAVING/SELECT/JOIN ON 逐个 `columnValidator.analyzeExpression(...)`，错误信息里带子句名——排错体验好，值得抄。

### 10. 与 nop-stream-sql 的对照与可借鉴点

落到具体能力：

| ksqlDB 能力 | nop-stream-sql 现状 | 可借鉴点 |
|---|---|---|
| `EMIT CHANGES`（默认）/ `EMIT FINAL` | 无输出精化语法；D1 last-value-wins，窗口按 fire、持续聚合逐条 emit | 引入**输出精化开关**（哪怕只在 `<sql>` 属性上，如 `emit="changes\|final"`），映射到窗口 fire 策略 / trigger 的一个受控子集（D11 当前 fail-fast，可先把 `final` 做成"窗口关闭后只发终值"的窄实现） |
| `PARTITION BY <expr>` 查询级重分区 | 无；key 由 source/transform 决定 | 作为 `<sql>` 的可选子句，编译期生成 repartition 节点；与"FROM 表名 = 数据源 bean 名"模型兼容 |
| `LookupConstraint` 三档（key/range/table-scan） | 静态维表 lookup join（`ITableLookup`→`IJdbcTemplate/IBatchLoader`）只有一种"等值点查"形态 | 把 lookup 输入约束显式建模成枚举 + 构建期判定，非法条件 fail-fast；同时给 pull/点查式接口留 range 档 |
| push/pull 双形态 | 只有 push（持续/窗口）；无"查当前值"语义 | nop 的 state store 若可被外部点查（对应 `useCache`/`get`），pull query 的 `KEY_LOOKUP` 建模是现成参照 |
| stream/table 双模型 + retract | 单一 last-value-wins，无 RowKind | **短期不引入**（D1 已裁定），但 `KudafUndoAggregator` 的"聚合函数自带 undo"接口是将来加 retract 的最小改动面 |
| `COUNT_DISTINCT` / `TOPK` / `HISTOGRAM` / `COLLECT_LIST` | 五聚合闭集，DISTINCT fail-fast | 若扩聚合面，优先 `COUNT_DISTINCT`（纯增量可归约）与 `TOPK`（有界堆），`COLLECT_LIST` 需要容量上限否则状态无界 |
| named window 语法 | 无 | 可作低成本可读性补充（`WINDOW hourly TUMBLING(...)`），但需先定义其运行期用途（store 命名/多窗口同表），否则就是死语法——ksqlDB 自己就处在这个状态 |
| 仅等值 join + 单比较表达式 | 等值 join（含 FULL window join 部分） | 一致；ksqlDB 连复合等值键都不支持，nop 的"双流等值 join"不落下风 |
| 无 CEP | nop 有 CEP | nop 优势项 |

## 对照 nop-stream-sql 已交付子集（必填小节）

逐条对照基线中与 ksqlDB 重叠的能力：

1. **入口形态**：nop = `.stream.xml` 顶层 `<sql sinkBean>` + EQL 文本 + 内嵌 `<schemas>`；ksqlDB = 交互式 SQL 语句 / `CREATE ... AS SELECT` 持久对象。**差异**：nop 是"编译期一次性展开为流模型"，ksqlDB 是"语句即服务端对象"。**可借鉴**：ksqlDB 把 schema 来自 metastore，nop 把 schema 内嵌声明——nop 的 9 受管类型闭集 + fail-fast 比 ksqlDB 的运行时 schema 推导更早失败，保持；但可借 `ALTER ... ADD COLUMN` + schema-change 重编译思路（SeaTunnel 的 `mapSchemaChangeEvent` 同向）。
2. **SELECT 投影 / WHERE**：一致。ksqlDB 额外有 CASE/CAST/STRUCT/lambda；nop 明确 fail-fast 标量子集外表达式——**保持**，这是刻意的窄面。
3. **五聚合 / GROUP BY**：nop 五聚合（sum/count/avg/min/max，`COUNT(*)`）+ 无窗口持续 GROUP BY；ksqlDB 17 个 UDAF + HAVING。**差异**：nop 无 HAVING（default-reject）、无 `COUNT_DISTINCT`。**可借鉴**：`COUNT_DISTINCT` 是最省的增量扩展（去重集需上限）；HAVING 可先编译成 post-filter 而非新算子。
4. **`TUMBLE(t, INTERVAL)` 伪表函数**：nop 的窗口是**写在 FROM 里的伪表函数**（`FROM TUMBLE(orders.ts, INTERVAL 5 SECOND)`），ksqlDB 是 `WINDOW TUMBLING(...)` 挂在查询上。**差异**：语法位置不同；nop 的 TUMBLE 是流目标专用（W2/T1 SQL 通道原样回显）。**可借鉴**：ksqlDB 的 `WINDOW` 子句位置让"同一 FROM + 不同窗口"更自然，但 nop 的表函数形态与 EQL parser 更贴——不建议改，仅记录形态差异。
5. **双流等值 join（INNER/LEFT/RIGHT/FULL）+ watermark 补齐**：语义对齐（ksqlDB 的 `WITHIN` + grace ↔ nop 的 window join 补齐于 watermark）。**差异**：ksqlDB 的 `RIGHT`/`OUTER` join 在 stream-stream 上可用，nop 也有 RIGHT/FULL window join 支持；nop 的 **FULL window join 有不支持项**（基线："FULL 窗口 join fail-fast"，指特定形态）——ksqlDB 无此限制。**可借鉴**：`WITHIN (before, after)` 的**非对称窗口**是 nop 没有的（nop 只有对称 window join），若未来扩展，语法可直接抄 `SqlBase.g4:256-262`。
6. **静态维表 lookup join**：nop `ITableLookup`；ksqlDB 对应物是 **stream-table join** 与 **table-table FK join**（`JoinNode.java:550-576`）。**可借鉴**：FK join 的"投影必须含外键列"校验（`JoinNode.java:262`）——nop 的 lookup join 可加同款构建期检查，防止输出丢失被 join 的 key。
7. **UNION ALL（bag 语义）**：ksqlDB 无 UNION（语法里没有）——nop 领先。
8. **分析窗口 OVER（ROW_NUMBER / FRAME_SLIDING_AGG）**：ksqlDB 无排名窗口函数，用 `TOPK` 近似。nop 领先。
9. **语义 D1（last-value-wins 终值）**：**这是最大的语义差异**。ksqlDB 的持续聚合是 **changelog（可 retract）** 输出，`EMIT FINAL` 再收敛为终值；nop 是 last-value-wins，无 retract/upsert。**可借鉴**：不必推翻 D1，但应借 `EMIT FINAL` 的**声明位置**——把"我只要终态"做成用户可见的显式契约，而不是让用户猜"窗口聚合按 fire / 持续聚合逐条"。
10. **语义 D6 事件时间**：一致（ksqlDB 用 `TIMESTAMP` 列 / record timestamp；nop 用 bigint epoch-millis 时间列）。
11. **D9 allowedLateness**：一致方向（ksqlDB = `GRACE PERIOD` 子句；nop = `<window>/<strategy>` 属性）。**可借鉴**：ksqlDB 把 grace 写在**窗口定义处**（一个子句），nop 分散在 `<window>` 与 `<strategy>` 两处属性——若未来做 SQL 面的窗口，建议把 lateness 收到窗口声明旁。
12. **D10 accumulationMode / D11 trigger 保持 fail-fast**：ksqlDB 恰好用 `EMIT CHANGES|FINAL` 覆盖了 trigger 的最常用两档（`ON_WINDOW_UPDATE` / `ON_WINDOW_CLOSE`，Kafka Streams `EmitStrategy`）。**这是最直接的降维方案**：nop 若解 D11，先只放这两档。
13. **架构**：nop = EQL AST → `StreamSqlCompiler`（SPI）→ 流模型 XML，三注册表（schemas/aggregators/joins），无代价优化器；ksqlDB = ANTLR AST → Analysis → PlanNode → ExecutionStep → Kafka Streams DSL。**可借鉴**：`ExecutionStep` 作为**可序列化、可 diff 的中间计划**这一层，nop 目前直接"SQL → 流模型 XML"，缺少一个显式的、版本化的算子序列化层（流模型 XML 其实可以承担这个角色，但需要保证 fingerprint/diff 能力）。
14. **不支持清单的表达方式**：nop 用错误码（`nop.err.*`）fail-fast；ksqlDB 用 `PullQueryValidator.RULES` 列表 + `throwIfUnsupported` 逐条消息。**可借鉴**：把 nop 的"不支持清单"做成**结构化的规则表**（条件 → 错误文案 → 建议开关），比散落的 `throw` 更易审计、也更易生成文档。

## 引用出处

以下路径在写完后逐条 `ls`/`test -f` 验证存在；行号来自实际读到的文件内容。

**ksqldb-parser**

- `ksqldb-parser/src/main/antlr4/io/confluent/ksql/parser/SqlBase.g4`（673 行；:47-48 statement/queryStatement，:75-88 DDL/INSERT，:118-132 query + resultMaterialization，:181-200 grace + 三种窗口 + named window，:242-262 join/WITHIN）
- `ksqldb-parser/src/main/java/io/confluent/ksql/parser/AstBuilder.java`（1728 行；:234 DEFAULT_WINDOW_NAME，:496-520 visitWindowExpression）
- `ksqldb-parser/src/main/java/io/confluent/ksql/parser/tree/WindowExpression.java`（windowName 字段与 `getWindowName()`）
- `ksqldb-parser/src/main/java/io/confluent/ksql/parser/tree/WithinExpression.java`（:37 gracePeriod 字段）

**ksqldb-engine**

- `ksqldb-engine/src/main/java/io/confluent/ksql/engine/KsqlEngine.java`（:285 parse / :290 prepare / :298 plan / :308 execute）
- `ksqldb-engine/src/main/java/io/confluent/ksql/engine/EngineExecutor.java`（1158 行；:178-183 DDL 分支，:705-741 throwIfUnsupported，:743-837 planQuery，:945/:960 scalable push / pull physical plan，:1132 executePersistentQuery）
- `ksqldb-engine/src/main/java/io/confluent/ksql/engine/QueryPlan.java`（:32-41 持久化字段）
- `ksqldb-engine/src/main/java/io/confluent/ksql/engine/QueryEngine.java`（:57 buildQueryLogicalPlan，:84 buildPhysicalPlan）
- `ksqldb-engine/src/main/java/io/confluent/ksql/analyzer/Analyzer.java`（773 行；:274 visitQuery，:304-335 子句校验，:372-375 仅等值 join，:420-423 isEqualityJoin）
- `ksqldb-engine/src/main/java/io/confluent/ksql/analyzer/QueryAnalyzer.java`（:35-48 组合两个 Validator）
- `ksqldb-engine/src/main/java/io/confluent/ksql/analyzer/PullQueryValidator.java`（189 行；:43-48 LIMIT 文案，:50-88 规则表）
- `ksqldb-engine/src/main/java/io/confluent/ksql/analyzer/RewrittenAnalysis.java`（:65-66 zeroGracePeriod，:149-153 EMIT FINAL 时注入 grace）
- `ksqldb-engine/src/main/java/io/confluent/ksql/analyzer/AggregateAnalyzer.java`（:245 GROUP BY 必须含聚合）
- `ksqldb-engine/src/main/java/io/confluent/ksql/planner/plan/AggregateNode.java`（405 行；:94 DataSourceType.KTABLE，:156-177 buildStream，:188-189 无 GROUP BY 报错）
- `ksqldb-engine/src/main/java/io/confluent/ksql/planner/plan/JoinNode.java`（903 行；:262 FK join 投影约束，:411-429 StreamToStreamJoiner + WITHIN 必需，:550-576 FK join 分支）
- `ksqldb-engine/src/main/java/io/confluent/ksql/planner/plan/SuppressNode.java`（:33-40 类注释，:75-88 buildStream）
- `ksqldb-engine/src/main/java/io/confluent/ksql/planner/plan/QueryFilterNode.java`（:228-262 extractLookupConstraints）
- `ksqldb-engine/src/main/java/io/confluent/ksql/execution/pull/PullPhysicalPlan.java`（:186-191 PullPhysicalPlanType）
- `ksqldb-engine/src/main/java/io/confluent/ksql/embedded/KsqlContext.java`（:65 class，:78 create，:300 execute）

**其他模块**

- `ksqldb-common/src/main/java/io/confluent/ksql/properties/with/CommonCreateConfigs.java`（:39-40 TIMESTAMP/TIMESTAMP_FORMAT）
- `ksqldb-common/src/main/java/io/confluent/ksql/util/KsqlConfig.java`（:248 KSQL_STREAMS_PREFIX，:284-297 standby reads，:1045 sink changelog retention）
- `ksqldb-execution/src/main/java/io/confluent/ksql/execution/function/udaf/KudafUndoAggregator.java`（:29 class）
- `ksqldb-execution/src/main/java/io/confluent/ksql/execution/plan/`（43 个 ExecutionStep 文件，目录已列）
- `ksqldb-streams/src/main/java/io/confluent/ksql/execution/streams/KSPlanBuilder.java`（:62 class）
- `ksqldb-streams/src/main/java/io/confluent/ksql/execution/streams/TableSuppressBuilder.java`（:115-117 suppress().untilWindowCloses）
- `ksqldb-streams/src/main/java/io/confluent/ksql/execution/streams/StreamAggregateBuilder.java`（:278/:309/:339 gracePeriod 传递）
- `ksqldb-rest-app/src/main/java/io/confluent/ksql/rest/server/computation/ValidatedCommandFactory.java`（:167 processing.guarantee 变更校验）
- `ksqldb-functional-tests/src/test/resources/query-validation-tests/earliest-offset-udaf.json`（:315 named window 用例）

**文档（同仓库）**

- `docs/reference/sql/time.md`（:8-17 时间单位，:24-36 TIMESTAMP 属性）
- `docs/concepts/streams.md`（:9-15 append-only）、`docs/concepts/tables.md`（changelog/tombstone）
- `docs/concepts/time-and-windows-in-ksqldb-queries.md`（:397 late 定义，:405 默认 grace 24h，:600 RETENTION/GRACE 示例）
- `docs/developer-guide/ksqldb-reference/select-push-query.md`（:68-86 EMIT 精化，:268 默认 grace）
- `docs/developer-guide/ksqldb-reference/select-pull-query.md`（:35-50 pull 语句限制与 table scan 开关）
- `docs/developer-guide/ksqldb-reference/insert-into.md`（:17-45 INSERT INTO 语义与 PARTITION BY 位置）
- `docs/developer-guide/ksqldb-reference/aggregate-functions.md`（17 个聚合函数条目）
- `docs/operate-and-deploy/exactly-once-semantics.md`（:9-26 at-least-once / exactly-once）

**许可证/版本**

- `pom.xml`（:39 version `8.5.0-0`，:41-44 Confluent Community License）
- `LICENSE`、`LICENSE-ConfluentCommunity`、`README.md`（:143-145）

**同批已有分析（引用，不重复其内容）**

- `ai-dev/analysis/2026-09/2026-09-01-kafka-streams-productization-analysis.md`（Kafka Streams 产品化面，本文只补 SQL 语义面）
- `ai-dev/analysis/2026-10/2026-10-06a-nop-stream-vs-flink-tdengine-hazelcast-seatunnel-comparison.md`（§二 nop 基线、§三 缺口对照）

**未能验证 / 标注**

- named window 的运行期用途（是否影响 store/topic 命名）——**未在本仓库验证**，仅确认 `windowName` 有 3 处读取点。
- `ksqlDB` 的非等值 join 在未来版本是否放开——**未在本仓库验证**（当前 8.5.0-0 明确禁止）。
