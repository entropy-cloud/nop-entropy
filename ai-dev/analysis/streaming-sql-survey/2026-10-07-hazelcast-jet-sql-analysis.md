# Hazelcast（Jet SQL）streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: 最值得借鉴三点——① 把"允许迟到"表达成 `IMPOSE_ORDER(TABLE t, DESCRIPTOR(ts), INTERVAL lag)` 的**第三个参数 lag**，语义等价 nop 的 `allowedLateness` 但写在查询里、随查询走，可直接翻译为 nop 的 sink/查询级 lateness 配置；② 流式查询用一组 `MustNotExecutePhysicalRel` 规则做**语句级 fail-fast**（"Sorting is not supported for a streaming query"、"You must use CREATE JOB statement for a streaming DML query"），与 nop 的 fail-fast 哲学完全同构，可借其"拒绝原因即文档句子"的报错写法；③ `IMPOSE_ORDER` 必须显式包裹每条流输入（`CREATE VIEW s AS SELECT * FROM TABLE(IMPOSE_ORDER(...))`）才能产生水印，**未包裹即无水位**——这给了 nop 一个比"隐式时间列"更严格的可选形态。

## Context

Hazelcast 是集群内存数据平台，Jet 是其流处理引擎（6.0.0-SNAPSHOT 中 Jet 已并入主模块）。SQL 层 `hazelcast-sql` 建在 Apache Calcite 之上，是一个"**流式限制由优化器规则强制**"的实现：所有流式语义（水位、迟到、窗口、join 界）都不写在语法里，而是靠一串 `*MustNotExecuteRule` / `WatermarkRules` 在物理计划阶段落地或拒绝。它被列入本调研，是因为它给出了与 nop-stream-sql 最接近的另一种 fail-fast 路线。本分析依据本地源码 `/Users/abc/sources/streaming-sql/hazelcast`（HEAD `c3232b34`，2026-10-07）。

## 项目概览

- **定位**：内存集群 + SQL 一等查询，`IMAP` 既是状态也是数据源；流式查询用 `CREATE JOB` 常驻执行。
- **许可证**：**核心 `hazelcast` 模块 Apache-2.0**（`hazelcast/pom.xml:4` license header），**`hazelcast-sql` 模块 Hazelcast Community License**（`hazelcast-sql/pom.xml:5`）→ SQL 引擎源码可见但非 OSI 开源，这与 Arroyo（Apache-2.0/MIT）、Feldera（MIT + Enterprise）形成三种许可形态对照。
- **演进状态**：6.0.0-SNAPSHOT，活跃；Jet 已从独立模块并入 `hazelcast/src/main/java/com/hazelcast/jet/`。
- **SQL 前端**：Apache Calcite `1.35.0`（`hazelcast-parent/pom.xml:94`）+ 自研扩展（`IMPOSE_ORDER`/`TUMBLE`/`HOP` 表函数、`CREATE EXTERNAL MAPPING`/`CREATE JOB` DDL）。
- **设计文档**：`docs/design/sql/01..20`（含 `14-keyed-watermark-support.md`、`15-stream-to-stream-join.md`）、`docs/design/jet/013,016,024`、`docs/sql/01..04` —— 三份文档直接解释了水位/排序/快照的取舍，是本次调研中**最成体系的流式语义文档**。

## 十维度分析

### 1. SQL 前端：Calcite 校验 + 白名单强制 + 物理规则拦截

三层把守，每层职责清晰：

1. **parser 扩展**：`hazelcast-sql/.../parse/` 下 `SqlCreateMapping`（`new SqlSpecialOperator("CREATE EXTERNAL MAPPING", SqlKind.CREATE_TABLE)`，`SqlCreateMapping.java:54-55`）、`SqlCreateJob`（`new SqlSpecialOperator("CREATE JOB", SqlKind.OTHER_DDL)`，`SqlCreateJob.java:46-47`）、`SqlCreateView/Index/Type/DataConnection/Snapshot`。
2. **validator 白名单**：`UnsupportedOperationVisitor` 用 `SUPPORTED_KINDS` 正向列表 + `SUPPORTED_OPERATORS`（`UnsupportedOperationVisitor.java:74`、`:91` 起 `SUPPORTED_KINDS.add(...)`），不在名单里的 `SqlKind` 一律 `unsupported(...)`；子句级校验：`FETCH/OFFSET is only supported for the top-level SELECT`（:470-476）、join 类型限 `INNER/COMMA/CROSS/LEFT/RIGHT`（:481-491，否则 `unsupported(join, joinType.name() + " join")`）。**即不支持 FULL / SEMI / ANTI / NATURAL**。
3. **物理规则拦截**：优化器把"不许执行"的计划转成 `MustNotExecutePhysicalRel`，见维度 8。

### 2. Table & Changelog 模型

- **DDL**：`CREATE EXTERNAL MAPPING`（外部表：IMAP/Kafka/文件等，`SqlCreateMapping.java:56`）；`CREATE VIEW`；`CREATE TYPE`；`CREATE INDEX`；`CREATE DATA CONNECTION`；`CREATE SNAPSHOT`；`CREATE JOB`。**未见 `CREATE TABLE` 语句类**（全仓 `grep "\"CREATE TABLE\""` 无命中，`SqlKind.CREATE_TABLE` 实际由 mapping 承载）—— 即"表"在 Hazelcast 中是数据连接器映射，不是独立的存储表。
- **changelog 模型**：**没有显式 changelog 概念**。流式 DML 的输出只能落 IMAP sink；引擎不对外暴露 insert/delete 流，也没有 retract 标记。批式视图 + 流式作业（`CREATE JOB ... INSERT ...`）是唯一组合形态（`StreamingInsertMustNotExecuteRule.java:63-65`）。
- 与 nop 对照：nop 的 sinkBean（RDBMS 落库）与 Hazelcast 的 IMAP sink 都是"终值写入"，两者都不输出 changelog —— 这一点上 nop 与 Hazelcast 同侧，与 Arroyo/Feldera 异侧。

### 3. 时间语义：IMPOSE_ORDER + lag + DropLateItems

- **声明形态（关键差异）**：时间属性不写在 DDL，而是**查询里的表函数包裹**：
  ```sql
  SELECT * FROM TABLE(IMPOSE_ORDER(TABLE stream1, DESCRIPTOR(b), INTERVAL '0.001' SECOND))
  ```
  测试范式见 `SqlStreamingJoinAndAggregationTest.java:65-78`、`SqlTumbleTest.java:229-243`。
- **函数定义**：`ImposeOrderFunction`（`aggregate/function/ImposeOrderFunction.java:44-46`，`super("IMPOSE_ORDER", new WindowOperandMetadata(PARAMETERS, new int[]{2}), RETURN_TYPE_INFERENCE)`），第三个参数是 size/lag 索引。
- **规则转换链**（`opt/logical/WatermarkRules.java`）：
  1. `IMPOSE_ORDER_INSTANCE`（:50）把 `LogicalTableFunctionScan` 转成 `WatermarkLogicalRel`，`lagExpression(scan)` 取第三个操作数（:93-97）、`orderingColumnFieldIndex(scan)` 取 DESCRIPTOR 里的列（:89-91）。
  2. 若已有水位字段则再包一层 `DropLateItemsLogicalRel`（:83-89），**注释明示**："A late item is an item whose timestamp is before the timestamp of the last watermark"（`DropLateItemsLogicalRel.java:37-40`）→ 迟到数据**丢弃，无 late-buffer**。
  3. `WATERMARK_INTO_SCAN_INSTANCE`（:107）把 `WatermarkLogicalRel` 下推进 `FullScanLogicalRel`，此时 `lagExpression` 成为扫描的水位偏移（`WatermarkLogicalRel.java:33`、`opt/FullScan.java:32-50`）。
- **未包裹就没有水位**：`opt/WatermarkKeysAssigner.java` / `HazelcastRelMdWatermarkedFields.java` 负责元数据传播；没有 `IMPOSE_ORDER` 的输入不带水位字段，进而触发流式算子的拒绝规则（见维度 7/8）。
- **设计文档**：`docs/design/sql/14-keyed-watermark-support.md`（keyed watermark：stream-to-stream join 需要多个独立水位实例，并区分"watermark 实例 = 携带值的流项"与"watermark 集合 = 同键集合"）；`docs/design/jet/013-system-time-watermarks.md`（稀疏事件导致事件时间停滞的问题、`withIngestionTime()` 的取舍）；`docs/design/jet/016-event-reordering.md`（round-robin vs 分区对同键顺序的影响）。
- **idle**：不提供 idle 概念，靠 013 文档描述的 system-time watermark 缓解稀疏事件（**未验证**具体配置项）。

### 4. 窗口与聚合

- **只有 TUMBLE 和 HOP，没有 SESSION**：`HazelcastSqlOperatorTable.java:279-281` 只注册了 `IMPOSE_ORDER` / `TUMBLE` / `HOP`（`grep SESSION` 无命中）。
- **参数形态**（`HazelcastTumbleTableFunction.java:31-35`）：`PARAMETERS = [input(ROW), time_col(COLUMN_LIST), window_size(ANY)]`，`super(SqlKind.TUMBLE, new WindowOperandMetadata(PARAMETERS, new int[]{2}), 1)`。
- **使用形态**：`TABLE(TUMBLE( <input>, DESCRIPTOR(ts), INTERVAL '1' SECOND))`，外层 `GROUP BY window_start`（或 `window_end`），附加列 `window_start`/`window_end` —— 测试 `SqlStreamingJoinAndAggregationTest.java:70-78`（`HOP` + `GROUP BY window_end`）、`SqlTumbleTest.java:229-243`。
- **窗口边界不可再被过滤**：`WHERE window_start != window_end` 直接报 `Can't apply filter criteria to window bounds`（`SqlTumbleTest.java:232-243` 断言 `hasMessageEndingWith`）—— 一条很有价值的 fail-fast 校验，nop 若开放 `window_start` 过滤也应照此拦截。
- **聚合白名单**：`AggregateAbstractPhysicalRule.java:85-145` 的 switch 显式列 `COUNT / MIN / MAX / SUM / AVG / JSON_ARRAYAGG / JSON_OBJECTAGG`，`default: throw QueryException.error("Unsupported aggregation function: " + kind)`（同文件 :144-145）。`DISTINCT` 由 `distinct` 标志传入（`AggregateSumSupplier(distinct, ...)`、`AvgSqlAggregations.from(type, distinct)`，:114、:172）。
- **没有 `OVER` 分析窗口**：`UnsupportedOperationVisitor` 的 `SUPPORTED_KINDS` 不含 `OVER`，Java 源码 `grep SqlKind.OVER` 无命中 → `ROW_NUMBER()` 这类窗口函数**不支持**。
- **无窗口持续 GROUP BY 不支持**（见维度 7）。

### 5. Join

- **类型白名单**：`INNER/COMMA/CROSS/LEFT/RIGHT`（`UnsupportedOperationVisitor.java:481-491`），**无 FULL/SEMI/ANTI**。
- **批式执行**：`JoinHashPhysicalRel`（等值）/ `JoinNestedLoopPhysicalRel`（非等值，代价高）都在 `opt/physical/` 中，即**非等值 join 是支持的**（与 Arroyo/Feldera 不同）。
- **流式 join 分两档**：
  - **batch-to-stream**：已有能力（设计文档 `docs/design/sql/15-stream-to-stream-join.md` 背景段："Hazelcast already supports batch to stream joins"）。
  - **stream-to-stream**：`StreamToStreamJoinPhysicalRule.java:152-159` 强制要求"join 条件必须从两侧时间值的**最大差**双向约束"，否则 `fail(join, "A stream-to-stream join must have a join condition constraining the maximum difference between time values of the joined tables in both directions. Time columns on the left side: ... Time columns on the right side: ...")` —— 报错里直接把两侧水位列名打出来，是**高质量诊断信息**的范例。
  - 实现体 `StreamToStreamJoinPhysicalRel`（用 `postponeTimeMap` 做时间界）；设计文档 `15-stream-to-stream-join.md` 目标段明确"状态必须有界（不支持无时间界的 stream-to-stream join）+ 延迟最小化"。
- 子查询：`SqlKind.IN / EXISTS` 在白名单（`UnsupportedOperationVisitor.java:97`、`:100`）。
- **无 ASOF join**（`HazelcastSqlOperatorTable` 无此算子；需确认 —— 未验证）。

### 6. CEP / 模式匹配：无

`grep "MATCH_RECOGNIZE" --include="*.java"` 全仓无命中。

### 7. 状态与容错

- **状态在内存**：`ProcessingGuarantee.java:29-31` javadoc 明确 —— "distributed snapshot algorithm works by sending *barriers* down the stream which upon receiving causes the processors to save their state as a snapshot. **Snapshots are saved in memory and replicated across the cluster.**"（即状态存 IMAP，**不是 RocksDB**）。
- **档位**：`NONE`（`ProcessingGuarantee.java:53`，无快照、不保证正确性）/ `AT_LEAST_ONCE`（:64，可用但可能重复）/ `EXACTLY_ONCE`（:77，对齐 barrier，延迟更高但最强保证）；默认值 `NONE`（`JobConfig.java:80`，setter `:261`）。文档 javadoc 明确 "it must wait until the barrier is received from all inputs before taking a snapshot"（:31-33）。
- **两阶段提交实现**：`impl/processor/TwoPhaseSnapshotCommitUtility.java`（commit 工具）、`impl/operation/SnapshotPhase1Operation.java` + `impl/operation/SnapshotPhase2Operation.java`（两阶段快照操作）、`impl/util/AsyncSnapshotWriter.java`（异步写 IMap）、`impl/execution/SnapshotContext.java`（屏障/槽位管理）。
- **无窗口持续 GROUP BY 明确拒绝**：`StreamAggregateMisusePhysicalRel` javadoc（:36-39）"Streaming aggregation is supported only as windowed with IMPOSE_ORDER"，真正用户可见的报错是 `:79` 的 "Streaming aggregation is supported only for window aggregation, with imposed order, ..." → 流上聚合必须先开窗。
- **排序明确拒绝**：见维度 8。
- **快照安全性设计**：`docs/design/jet/024-non-corruptible-snapshots.md`（标题 frontmatter 写 023，文件名 024）—— IMAP 是 AP 结构、单节点故障可能损坏快照，提出非可损坏快照方案，术语区分 `automatic/exported/terminal/export-only snapshot`。

### 8. 声明形态与可嵌入性（含 fail-fast 规则集）

- **两条语句分工**：
  - `CREATE VIEW ... AS SELECT ... FROM TABLE(IMPOSE_ORDER(TABLE s, DESCRIPTOR(c), INTERVAL '0.001' SECOND))` 给流"上水位"（`SqlStreamingJoinAndAggregationTest.java:65-68`）。
  - `CREATE JOB name OPTIONS(...) AS INSERT INTO ...` 把流式 DML 变成长驻作业（`SqlCreateJob.java:44-47`、字段 `SqlExtendedInsert sqlInsert` + `JobConfig jobConfig` 见 :51-53）。
- **语句级 fail-fast 规则**（`MustNotExecutePhysicalRel` 家族，全部在 `opt/physical/`）：
  - `StreamingSortMustNotExecuteRule.java:61-64`：`sort.fetch == null` → `"Sorting is not supported for a streaming query"`（**有 fetch 的 TopN 放行**）。
  - `StreamingInsertMustNotExecuteRule.java:63-65`：作业未声明 → `"You must use CREATE JOB statement for a streaming DML query"`。
  - `StreamAggregateMisusePhysicalRel.java:79`：无窗口聚合 → `"Streaming aggregation is supported only for window aggregation, with imposed order, ..."`。
  - 这三条与 nop 的 fail-fast（全局 ORDER BY/LIMIT、无窗口全局聚合等）**一一对应**，可直接互为佐证。
- **可嵌入性**：`hazelcast-sql` 是普通 Maven 模块（`hazelcast-sql/pom.xml`），可作依赖嵌入 JVM 应用（`SqlService.execute(...)`），不需要独立集群服务 —— 与 nop 的"进程内执行"形态最接近，但其**许可为 HCL 而非 Apache-2.0**，是引入时必须先评估的边界。
- **计划缓存**：`docs/sql/03-plan-caching.md`、`docs/design/sql/06-plan-caching.md`。

### 9. 架构要点（3 个值得记的设计）

1. **"语义靠规则，不靠语法"**：`IMPOSE_ORDER` 只是普通表函数，真正的水位/迟到语义由 `WatermarkRules`（logical 层转换 + 下推进 scan）与 `WatermarkPhysicalRule`/`opt/WatermarkKeysAssigner.java` 完成。规则分层（logical → physical）+ 元数据（`HazelcastRelMdWatermarkedFields`）的结构与 nop 的"编译期分层"天然契合，但 nop 无需引入 Calcite，只需把同一套分层落到 `StreamSqlCompiler` 内部。
2. **每个流式约束都有一个专门的 Rel 类承载"不许执行"**：`StreamAggregateMisusePhysicalRel` / `StreamingSortMustNotExecutePhysicalRel` / `StreamingInsertMustNotExecutePhysicalRel` —— 不是在某处 `throw`，而是把违规计划显式降级成 `MustNotExecutePhysicalRel` 节点，**错误在计划里可见、可 explain**。nop 可以借鉴这个"错误也是一个节点"的建模（便于把失败节点 ID 打进诊断）。
3. **设计文档即架构资产**：`docs/design/sql/14-keyed-watermark-support.md`、`15-stream-to-stream-join.md`、`docs/design/jet/013/016/024` 覆盖了水位合流、稀疏事件、同键顺序、快照损坏四类真实故障。nop 的 `docs/bugs/` + `docs/plans/` 若按此粒度沉淀，长期价值远超单次实现记录。

### 10. 与 nop-stream-sql 的对照与可借鉴点

- **允许迟到的声明位**：nop 的 `allowedLateness` 是 sink/查询级配置（D9），Hazelcast 是 `IMPOSE_ORDER(..., INTERVAL lag)` 的第三参数。两者语义一致；**可借鉴**：让 lateness 作为 `TUMBLE(...)`/`IMPOSE_ORDER(...)` 的显式参数出现在查询文本中，调试时一眼可见，且天然按输入区分（每条流各自的 lag）。
- **包裹式上水位**：Hazelcast 强制"流必须显式 `IMPOSE_ORDER` 才有水位"，未包裹 → 无水位字段 → 流式算子拒绝。nop 目前靠时间列自动推导（D6 已放行）。**可选强化**：提供一个显式 `event_time_order(...)` 包裹形态，作为对"时间列语义不明确"场景的显式声明，同时保留现有推导作为默认。
- **窗口边界不可过滤**：`Can't apply filter criteria to window bounds`（`SqlTumbleTest.java:232-243`）—— nop 若开放 `window_start`/`window_end`，应加同款校验。
- **流式聚合必须开窗**：`StreamAggregateMisusePhysicalRel.java:79` 与 nop 的"无窗口全局聚合 fail-fast"完全一致 → 可引用为外部佐证。
- **stream-to-stream join 必须双向时间界**：`StreamToStreamJoinPhysicalRule.java:153` 的报错文案（两侧时间列名都列出来）是 nop join 校验报错的最佳模板。
- **不要照搬**：`CREATE JOB` 常驻作业模型（nop 走 sinkBean 回调/批处理触发，不需要作业对象）；IMAP 作为状态后端（nop 复用既有 RDBMS/既有中间件）；HCL 许可（SQL 模块非 OSI，商用需评估）。

## 对照 nop-stream-sql 已交付子集

| nop 基线能力 | Hazelcast 对应 | 差异 / 可借鉴 |
| --- | --- | --- |
| 入口：`.stream.xml` 顶层 `<sql sinkBean>`，EQL 与 ORM 共用 parser | `SqlService.execute(SQL)` + `CREATE JOB ... AS INSERT`（`SqlCreateJob.java:44`） | 语义分工可借鉴：**"能解释"用 VIEW、"要常驻"用 JOB**；nop 可在 `.stream.xml` 上区分 `mode="adhoc|streaming"` |
| 内嵌 schema，9 受管类型闭集 | `CREATE EXTERNAL MAPPING` 映射外部表（`SqlCreateMapping.java:56`） | Hazelcast 无自有存储表；nop 的内嵌 schema 更自洽，保持 |
| SELECT / WHERE / 五聚合 / GROUP BY | 同 + `DISTINCT` 聚合（`AggregateAbstractPhysicalRule.java:114`），聚合集显式 switch 白名单（:100-150） | **可借鉴**：聚合白名单集中在一处并以 `throw "Unsupported aggregation function: X"` 收尾，与 nop 三注册表形态同构 |
| `TUMBLE(t, INTERVAL)` 伪表函数 | `TABLE(TUMBLE/HOP(input, DESCRIPTOR(col), INTERVAL n))`（`HazelcastSqlOperatorTable.java:280-281`） | 形态几乎相同；**可借鉴**其 `HOP(slide, size)` 参数顺序与"只开放 SHORT interval"的收窄（同 Feldera） |
| 窗口列 `window_start/window_end` | 同名（`SqlStreamingJoinAndAggregationTest.java:70-78`） | **字段命名一致**；并加"不可过滤"校验（`SqlTumbleTest.java:232-243`） |
| 双流等值 join（INNER/LEFT/RIGHT/FULL，window join 补齐于 watermark） | 批：等值 hash + 非等值 nested loop；流：batch-to-stream 已支持，stream-to-stream 要求**双向时间界**（`StreamToStreamJoinPhysicalRule.java:153`） | **可借鉴**：报错列出两侧时间列；nop 的 window join 已在 watermark 上补齐，比 Hazelcast 的"必须写 join 时间条件"更省心 —— 这是 nop 的优势，应保留 |
| 静态维表 lookup join | 无独立概念（batch join 中维表侧即静态） | 保持 nop 显式 `lookup join` 形态 |
| 无窗口持续 GROUP BY（D1 last-value-wins） | **不支持**（`StreamAggregateMisusePhysicalRel.java:79`） | **关键分歧**：nop 支持且定义为 D1 last-value-wins；Hazelcast 直接拒绝。nop 的 D1 更宽松，需在文档中明确"终值语义、不保证可回放" |
| UNION ALL（bag 语义） | 标准 UNION/INTERSECT/EXCEPT（`UnsupportedOperationVisitor` 白名单） | 对齐 |
| 分析窗口 OVER（ROW_NUMBER, FRAME_SLIDING_AGG） | **不支持**（`SUPPORTED_KINDS` 无 OVER，Java grep 无命中） | nop 的 `ROW_NUMBER` OVER 能力**领先**于 Hazelcast；可作为 nop 对外能力表的差异点 |
| 不支持：全局 ORDER BY/LIMIT、非等值 join、DISTINCT 聚合、HAVING/CTE、全局聚合、FULL window join、UNION DISTINCT、Flink 方言 | 全局排序拒绝（`StreamingSortMustNotExecuteRule.java:61-64`）；FULL join 拒绝（`UnsupportedOperationVisitor.java:481-491`）；流式全局聚合拒绝；非等值 join **支持**（`JoinNestedLoopPhysicalRel`） | nop 的 fail-fast 面与 Hazelcast 高度重合；**唯一分歧是非等值 join**（nop 拒绝、Hazelcast nested-loop 接受）—— nop 拒绝更利于性能可预期性，可保留 |
| 语义：D1 last-value-wins、D6 事件时间、D9 allowedLateness 已放行 | D6：`IMPOSE_ORDER` + `WatermarkLogicalRel` 下推进 scan；D9：等价（第三参数 lag）；**无 D1**；D10/D11：无 accumulationMode/trigger | D6/D9 可对齐；`lag` 语义即 nop 的 `allowedLateness`，建议 nop 把它提升为查询文本内可见参数 |
| 架构：EQL AST → StreamSqlCompiler → 流模型 XML；三注册表；无代价优化器 | Calcite 全量优化器 + `WatermarkRules`/`MustNotExecute` 系列规则 + `IMPOSE_RULE` 三层 | **可借鉴最小集合**：把"违规计划降级为可见错误节点"这一手搬进 nop 编译器；不引入 Calcite |

## 引用出处

以下路径若无前缀均相对本地源码根 `/Users/abc/sources/streaming-sql/hazelcast`（已在写毕后逐条验证存在；行号来自实际读取内容）。

- `hazelcast-parent/pom.xml` :94（`<calcite.version>1.35.0</calcite.version>`）
- `hazelcast/pom.xml` :4（Apache-2.0 header）
- `hazelcast-sql/pom.xml` :5（Hazelcast Community License header）
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/validate/UnsupportedOperationVisitor.java` :74, :91-129, :470-476, :481-491
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/validate/HazelcastSqlOperatorTable.java` :279-281
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/parse/SqlCreateMapping.java` :52-55
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/parse/SqlCreateJob.java` :44-47, :51-53
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/aggregate/function/ImposeOrderFunction.java` :44-46
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/aggregate/function/HazelcastTumbleTableFunction.java` :31-38
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/logical/WatermarkRules.java` :50-97, :83-89, :107-127
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/logical/WatermarkLogicalRel.java` :33-49
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/logical/DropLateItemsLogicalRel.java` :37-44
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/FullScan.java` :32-50
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/physical/StreamAggregateMisusePhysicalRel.java` :36-39, :79
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/physical/StreamingSortMustNotExecuteRule.java` :61-64
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/physical/StreamingInsertMustNotExecuteRule.java` :63-65
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/physical/StreamToStreamJoinPhysicalRule.java` :145-165, :153
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/physical/AggregateAbstractPhysicalRule.java` :85-145, :114, :144-145, :172
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/physical/{JoinHashPhysicalRel,JoinNestedLoopPhysicalRel,MustNotExecutePhysicalRel,SlidingWindowAggregatePhysicalRel,WatermarkPhysicalRule}.java`
- `hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/metadata/HazelcastRelMdWatermarkedFields.java`、`hazelcast-sql/src/main/java/com/hazelcast/jet/sql/impl/opt/WatermarkKeysAssigner.java`
- `hazelcast-sql/src/test/java/com/hazelcast/jet/sql/impl/aggregate/SqlTumbleTest.java` :229-243
- `hazelcast-sql/src/test/java/com/hazelcast/jet/sql/impl/aggregate/SqlStreamingJoinAndAggregationTest.java` :65-68, :70-78
- `hazelcast/src/main/java/com/hazelcast/jet/config/ProcessingGuarantee.java` :24-33, :53, :64, :77
- `hazelcast/src/main/java/com/hazelcast/jet/config/JobConfig.java` :80, :261
- `hazelcast/src/main/java/com/hazelcast/jet/impl/processor/TwoPhaseSnapshotCommitUtility.java`
- `hazelcast/src/main/java/com/hazelcast/jet/impl/operation/SnapshotPhase1Operation.java`、`hazelcast/src/main/java/com/hazelcast/jet/impl/operation/SnapshotPhase2Operation.java`
- `hazelcast/src/main/java/com/hazelcast/jet/impl/util/AsyncSnapshotWriter.java`
- `hazelcast/src/main/java/com/hazelcast/jet/impl/execution/SnapshotContext.java`
- `docs/design/sql/14-keyed-watermark-support.md`（terminology 段区分 watermark 实例与 watermark 集合）
- `docs/design/sql/15-stream-to-stream-join.md`（goals 段：状态必须有界 + 已支持 batch-to-stream）
- `docs/design/jet/013-system-time-watermarks.md`（稀疏事件 + `withIngestionTime()`）
- `docs/design/jet/016-event-reordering.md`（round-robin vs 分区保序）
- `docs/design/jet/024-non-corruptible-snapshots.md`（IMAP AP 风险 + 2-phase snapshot + snapshot 术语表）
- `docs/sql/01-query-optimizer.md`、`docs/sql/03-plan-caching.md`、`docs/design/sql/06-plan-caching.md`
- 未验证项（已标注）：`ASOF JOIN` 是否存在、idle/system-time watermark 的具体配置项、`IMPOSE_ORDER` 对非时间列的完整校验路径。
