# Feldera streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: 最值得借鉴三点——① 用**列级 `LATENESS` 注解**而非水位列声明时间语义，并由编译期单调性分析自动插入 GC/retention 算子（`InsertLimiters`），让"有界状态"成为推导结果而非手工配置；② 输出侧把 changelog 语义做成显式档位（默认 insert/delete、`append_only` 表、`emit_final` 视图、`soft_delete` 连接器），与 nop 的 D1 last-value-wins 可直接叠加成"终值 / 删除补偿"两档；③ 用 `RejectUnsupportedPlans` 这种"validator 通过但我不支持"的**二次拒绝层**给出带文档锚点的失败原因，是 nop fail-fast 报错质量的最佳范本。

## Context

Feldera 是基于 DBSP 增量计算理论的流/批一体查询引擎：SQL 由 Java 编译器（Calcite）编译成 Rust 电路代码，再链接 `dbsp` crate 运行。它被列入本调研，是因为它代表了与 nop-stream-sql 完全不同的另一种解法——**不做流式专用算子，而是把"时间属性"做成静态分析问题**。本分析依据本地源码 `/Users/abc/sources/streaming-sql/feldera`（Rust workspace + Java 编译器）。

## 项目概览

- **定位**：Database Stream Processor，SQL/Python 表达式 → DBSP 电路 → 持续增量计算（`docs.feldera.com/docs/sql/intro.mdx:25-44`：输入是 changes，输出视图也是 changes）。
- **许可证**：MIT（开源版）+ Feldera Enterprise 分离 —— `LICENSE` 明确 "OPEN-SOURCE EDITION - MIT LICENSE ... except for Feldera Enterprise code"，Enterprise 段落另列 `https://www.feldera.com/terms`。
- **语言构成**：Rust workspace `crates/`（`dbsp`、`sqllib`、`storage`、`adapters`、`pipeline-manager`、`rest-api`、`nexmark`、`iceberg` 等 20 个 crate）+ Java 编译器 `sql-to-dbsp-compiler/SQL-compiler`（836 个 java 文件）。
- **SQL 前端**：Apache Calcite，版本来自 `sql-to-dbsp-compiler/calcite_version.env`（`CALCITE_CURRENT="1.42.0"`，`CALCITE_NEXT="1.43.0"`，从 calcite 仓库 `main` 分支构建）。
- **演进状态**：活跃。递归视图、`emit_final`、故障恢复等仍标 experimental / Enterprise-only。

## 十维度分析

### 1. SQL 前端：Calcite 全量前端 + Feldera 自建的"二次拒绝层"

- 编译入口 `DBSPCompiler`：状态机式地按顺序编译多条语句（javadoc 见 `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/DBSPCompiler.java:126`，类在同文件 :126；内部持有 `sqlToRelCompiler`（:187）与 `relToDBSPCompiler`）。
- 解析层 `SqlLateness` / `SqlExtendedColumnDeclaration` 是自研 parser 扩展（`frontend/parser/SqlLateness.java:23`，列级注解字段 `lateness` / `defaultValue` 见 `frontend/parser/SqlExtendedColumnDeclaration.java:58-59`）。
- **关键设计**：Calcite 的 validator 会放行很多 SQL，但 Feldera 不支持，于是加了独立一遍 `RejectUnsupportedPlans extends RelHomogeneousShuttle`（`frontend/calciteCompiler/RejectUnsupportedPlans.java:54`）专门拦截，拒绝清单（类 javadoc :27-44）：
  - ROW 值等值比较（只实现 `IS [NOT] DISTINCT FROM`）
  - `MODE(DISTINCT value)`
  - `OVER` 内的 DISTINCT（如 `COUNT(DISTINCT x) OVER (...)`）
  - 聚合的 `WITHIN DISTINCT`
  - RANGE frame 的 offset 上界作用在 nullable `ORDER BY` 表达式上
  - `SIMILAR TO` / `NOT SIMILAR TO`
  - 每条拒绝都挂文档锚点（`ROW_DOCUMENTATION` / `WINDOW_DOCUMENTATION` / `DISTINCT_WINDOW_DOCUMENTATION` / `WITHIN_DISTINCT_DOCUMENTATION` 常量，同文件 :56-66）。
- 文档侧还有第二份白名单：`docs.feldera.com/docs/sql/unsupported-operations.md:11`（不支持的聚合：`PERCENTILE_DISC/PERCENTILE_CONT/CORR/REGR_SLOPE/REGR_INTERCEPT/REGR_R2/JSON_AGG/JSON_OBJECT_AGG/LISTAGG`；:17 `WITHIN DISTINCT`；:23 `NTILE/NTH_VALUE/PERCENT_RANK/CUME_DIST`）。
- 函数注册集中在 `calciteCompiler/CalciteFunctions.java`（窗口表函数注册见 :508-513：`HOP`/`TUMBLE`/`SESSION` → `table#...`）。

### 2. Table & Changelog 模型

- **DDL**：`CREATE TABLE ... WITH (...)`、`PRIMARY KEY`、列级 `LATENESS` 注解；连接器语义在 WITH 选项里（`'append_only' = 'true'`、`'materialized' = 'true'`）。主键语义文档：`docs.feldera.com/docs/connectors/unique_keys.md:33`（同一主键的 insert 会覆盖旧行）。
- **视图类型四档**：普通 `CREATE VIEW` / `CREATE MATERIALIZED VIEW`（`docs.feldera.com/docs/sql/materialized.md:19` 表级 `'materialized' = 'true'`、:27-30 `CREATE MATERIALIZED VIEW my_view AS SELECT ...`）/ `LOCAL VIEW` / `DECLARE RECURSIVE VIEW`（`docs/sql/recursion.mdx:32`，Turing-complete，实验性）。
- **changelog 模型（核心）**：默认视图输出就是 insert/delete 流：
  - `docs/sql/intro.mdx:29-44`："Changes in the output views are specified in terms of insertions and deletions."
  - 想要 append-only 就把表标 `append_only`（`docs/sql/streaming.md:21-27`），编译器据此做更强优化。
  - 想要"只出终值"用 `emit_final`（`docs/sql/streaming.md:29-41`，明确标 experimental）。
  - 删除补偿可由连接器 `soft_delete` + 时间过滤器实现（`docs/sql/streaming.md:44-89`）。
- **materialized 表/视图**是"计算结果保留在内存可查询"的能力，与流式 changelog 正交（`docs/sql/materialized.md:19-30`）。

### 3. 时间语义：LATENESS 列注解 + 水线（waterline）+ 单调性分析

- **没有 `WATERMARK FOR` DDL**，而是列级常量迟到界注解：
  - 语句形态 `LATENESS`（`frontend/parser/SqlLateness.java:23-40`：`OPERATOR = new SqlSpecialOperator("LATENESS", SqlKind.OTHER)`，三操作数 view/column/expression）。
  - 文档：`docs/sql/streaming.md:12`（"LATENESS expressions"）、:14 明确"在 lateness 范围之外的迟到更新不被接受"。
- **术语是 waterline 而非 watermark**：`crates/dbsp/src/operator/time_series/waterline.rs:41` `waterline_monotonic`，javadoc 说明输出是"上一批与当前批的最大值取单调不减"（:35-40）。文档刻意区分 waterline（派生标量）与 watermark（事件时间位点）。
- **迟到数据的处理位置在源算子**：`circuit/operator/DBSPInputMapWithWaterlineOperator.java:30-32` —— "This operator has 3 outputs: the data, the waterline, and the error stream with records that missed the lateness."（错过 lateness 的记录进 error stream 被丢弃，不静默）。
- **自动 GC 是编译期推导出来的**：`visitors/outer/monotonicity/InsertLimiters.java:88-105` javadoc 列出该 pass 插入的算子 —— `DBSPControlledKeyFilterOperator`（丢弃无用元组）、`DBSPWaterlineOperator`（源附近，带 lateness 信息）、`DBSPIntegrateTraceRetainKeys/ValuesOperator`（从积分算子裁剪）、`DBSPPartitionedRollingAggregateWithWaterlineOperator`，并且它会把 `DBSPSourceMultisetOperator` 改写成 `DBSPInputMapWithWaterlineOperator`。
- **无界状态会静态告警**：`visitors/outer/FindUnboundedState.java:98`（`class FindUnboundedState extends Passes`），文档在 `docs/sql/streaming.md:154`（"Unbounded state warnings"）→ :168-169（表声明了 `LATENESS` 或 `append_only` 即可消除告警）。
- **idle**：概念不存在（LATENESS 是静态常量界，不涉及分区空闲）—— 对 nop 是 N/A。

### 4. 窗口与聚合

- **窗口通过 FROM 子句表函数声明**（标准 SQL/Calcite 形态，非自造语法）：
  - `TUMBLE(data, DESCRIPTOR(timecol), size [, offset])`（`docs/sql/table.md:24-46`），实现 `CalciteToDBSPCompiler.compileTumble`（`frontend/CalciteToDBSPCompiler.java:578`），三条硬校验：interval 必须 "short"（天及以下，:607）、必须是常量（:612）、必须为正（:618）。
  - `HOP(data, DESCRIPTOR(timecol), slide, size [, offset])`（`docs/sql/table.md:71-94`），实现 `compileHop`（同文件 :658），同样要求 short interval（:674）。
  - `SESSION(data, DESCRIPTOR(timecol) [, DESCRIPTOR(key)], size)`（`docs/sql/table.md:127-168`）—— **实现方式特别**：在 Calcite 优化阶段被改写成标准关系算子（`frontend/calciteCompiler/optimizer/SessionRewriteRule.java:26`，改写规则见其 javadoc：LAG → 断点标记 → 运行 SUM 分配 session id → 自连接 + MIN/MAX 聚合得到 `window_start/window_end`）。
  - 三者都产出附加列 `window_start` / `window_end`（`docs/sql/table.md:68-69`、:117-118、:181-183）。
  - 表函数统一分发入口 `visitTableFunction`（`CalciteToDBSPCompiler.java:693`，仅识别 HOP/TUMBLE 两支，其余走默认分支）。
- **聚合**：标准语法 `agg '(' [ALL|DISTINCT] ... ')' [FILTER (WHERE ...)]`（`docs/sql/aggregates.md:11-21`），DISTINCT 聚合**支持**（同文档说明 :21-24；但 `OVER` 内 DISTINCT 被 `RejectUnsupportedPlans` 拒绝）。
- **OVER 分析窗口**：支持 `ARRAY_AGG/AVG/COUNT/DENSE_RANK/FIRST_VALUE/LAG/LAST_VALUE/LEAD/MAX/MIN/RANK/ROW_NUMBER/SUM`（`docs/sql/aggregates.md:196-280`，F/L 仅限特定 frame 边界：`FIRST_VALUE` 只接受 `UNBOUNDED PRECEDING ... CURRENT ROW/UNBOUNDED FOLLOWING`）；`NTILE/NTH_VALUE/PERCENT_RANK/CUME_DIST` 不支持（`unsupported-operations.md:23`）。
- **滚动/时间序列聚合**：`crates/dbsp/src/operator/time_series/{rolling_aggregate.rs, window.rs}` 提供随时间窗口滚动的聚合算子，对应 `DBSPPartitionedRollingAggregateWithWaterlineOperator`。

### 5. Join

- **等值 join**：`crates/dbsp/src/operator/join.rs`（增量等值 join）、`semijoin.rs`（半连接）、`distinct.rs`（增量去重）。
- **非等值/范围 join**：`crates/dbsp/src/operator/join_range.rs:14-40` `stream_join_range` —— 文档注释明确"非增量，按批 join"（"This operator is non-incremental, i.e., it joins the pair of batches it receives at each timestamp ignoring previous inputs"）→ 即非等值 join 可用但代价高。
- **ASOF JOIN（时间匹配 join）**：标准语法 + 自定义扩展：
  - `docs/sql/grammar.md:455`：`tableExpression [ LEFT ] ASOF JOIN tableExpression MATCH_CONDITION '(' booleanExpression ')' joinCondition`
  - `:746`："Currently, only `>=` is supported"（MATCH_CONDITION 只支持 `>=`）
  - `:751`："Currently, only the `LEFT` form of the `ASOF JOIN` is supported."
  - 实现 `crates/dbsp/src/operator/asof_join.rs`。
- 相关子查询/EXISTS/IN 由 Calcite 展开后走常规算子（`grammar.md` 关键字表 :868 列出 `MATCH_CONDITION/MATCH_RECOGNIZE/...` 等保留字）。

### 6. CEP / 模式匹配：无

`MATCH_RECOGNIZE` 只出现在保留关键字表（`docs/sql/grammar.md:868`），Java 源码 `grep SqlMatchRecognize|MatchRecognize|MATCH_RECOGNIZE` **无命中** → 未实现。

### 7. 状态与容错

- **状态实现**：Rust 侧 `crates/storage`（可溢写、分层存储）+ `crates/dbsp` 的积分/trace 数据结构（`operator/{integrate,accumulate_trace,accumulate_trace_balanced}.rs`），**不是 RocksDB**。
- **有界性靠注解推导**：`LATENESS` / `append_only` / 时间过滤器 → `InsertLimiters` 插入 retain 算子裁剪历史（`InsertLimiters.java:88-105`）；文档总结于 `docs/sql/streaming.md:219`："`append_only` and temporal filters let the compiler garbage-collect..."。
- **故障恢复 = Enterprise-only**：`docs/pipelines/fault-tolerance-overview.md:3`（note: Enterprise-only feature）、:22 at-least-once（定期 checkpoint）、:27 exactly-once（额外的 input/output journaling）；细节在 `docs/pipelines/fault-tolerance.md:3`（同为 Enterprise）、:40 at-least-once、:51 exactly-once。
- **checkpoint 指标**：`docs/pipelines/metrics.md` 暴露 checkpoint 相关指标（开源版也有 checkpoint 概念但不提供跨进程恢复保证）。
- **changelog 输出与状态的关系**：因为输出本身是 insert/delete 流（`intro.mdx:43-44`），retact 不是额外能力，而是默认形态 —— 这与 Arroyo 的"retract 标记列 + sink 协商"形成两种路线对照。

### 8. 声明形态与可嵌入性

- **声明形态**：一个 pipeline = 一条 SQL 文本（多语句：`CREATE TABLE` / `LATENESS` / `CREATE VIEW` / `CREATE MATERIALIZED VIEW`），由 `DBSPCompiler` 顺序编译（`DBSPCompiler.java:126` javadoc："The compiler is stateful: it compiles a sequence of SQL statements"）。
- **编译产物**：`CalciteToDBSPCompiler` 把 RelNode 映射成 `DBSPSimpleOperator` 并产出 `DBSPCircuit`（`CalciteToDBSPCompiler.java:248` javadoc："The compiler is stateful... The result is a circuit which has an input for each table and an output for each (non-local) view"）→ `RustFileWriter` 用 `ToRustVisitor` 生成 Rust 源码（`backend/rust/RustFileWriter.java:140`）→ `cargo build` 链接 `dbsp`。
- **可嵌入性**：`dbsp` 是独立 Rust crate（可作为增量计算库嵌入）；`SQL-compiler` 是 Java 库（无服务依赖，可直接调用）；完整产品形态经 `crates/pipeline-manager` + `crates/adapters` 提供 REST/连接器。
- 优化器步骤序列显式可配：`frontend/calciteCompiler/optimizer/CalciteOptimizer.java:232`（`addStep(new SimpleOptimizerStep("Rewrite SESSION", 0, ...)`）。

### 9. 架构要点（3 个值得记的设计）

1. **"关系代数先标准化，流式语义靠静态分析补齐"**：SESSION 这类流式窗口不在算子层特判，而是被 `SessionRewriteRule` 改写成标准 join/aggregate（`SessionRewriteRule.java:26-62`）；流式专属（水线、迟到、GC）全部由 `InsertLimiters` 这一遍单调性分析事后插入（`InsertLimiters.java:88-105`）。**结果是 SQL 语法面几乎等于标准 SQL**，用户不需要学流式专有构造。
2. **两层拒绝策略**：`RejectUnsupportedPlans` 只拦"validator 接受但语义上 Feldera 不做"的构造并附文档锚点（`RejectUnsupportedPlans.java:27-44`），另有 `FindUnboundedState.java:98` 对无界状态**只告警不拒绝**（`streaming.md:154`）。拒绝/告警的分级判断清晰。
3. **输出即 changelog**：默认视图输出 insert/delete（`intro.mdx:43-44`），需要收敛成终值再显式 `emit_final`（`streaming.md:29-41`）或 `append_only`（:21-27）。**把"终值语义"做成显式可选档位**，比在引擎里硬编码单一语义更灵活。

### 10. 与 nop-stream-sql 的对照与可借鉴点

- **时间属性声明形态**：nop 目前靠 `TUMBLE(t, INTERVAL)` 的时间列 + `allowedLateness` 配置（D9）；Feldera 用**列级 `LATENESS` 注解**直接写在列上（`SqlExtendedColumnDeclaration.java:58`），更接近"schema 自描述"。可借鉴：把允许迟到写成 `<sql>` 内嵌 schema 的列属性，而不是 sink/查询级配置。
- **有界状态推导**：Feldera 不让用户配"状态 TTL"，而是从 `LATENESS`/`append_only` 推出 retain 条件（`InsertLimiters`）。nop 若要做状态清理，走同一条路（从时间列 + lateness 推出裁剪点）比让用户配 TTL 更安全。
- **changelog 档位**：nop 的 D1 是 last-value-wins；Feldera 给出三档（默认 delete 流 / `append_only` / `emit_final`）。nop 可先做两档：默认终值 + `appendOnly` 开关，`emit_final`/`soft_delete` 留作后续。
- **报错质量**：`RejectUnsupportedPlans` 每条拒绝挂文档锚点 —— nop 的 fail-fast 三元组（节点 ID + 行号 + 拒绝理由）可再加"文档锚点"字段，直接跳到能力白皮书对应条目。
- **窗口形态差异**：Feldera 的 `TUMBLE/HOP` 是 `FROM TABLE(...)` 表函数（`docs/sql/table.md:24-94`），nop 是 SELECT/GROUP BY 里的伪表函数；两者都产出 `window_start/window_end`，**字段命名可直接对齐**（nop 若新增 `hop()`，沿用 `window_start/window_end` 即可与外部生态互认）。
- **不要照搬**：Feldera 的 Java 编译器 + Rust 代码生成链路（`DBSPCompiler` → `CalciteToDBSPCompiler` → `RustFileWriter`）意味着产物要 `cargo build`，这对 nop 的"XML → 即时执行"模型不可接受；nop 应保持"轻量编译 + 直接调度"，只借其静态分析思路。

## 对照 nop-stream-sql 已交付子集

| nop 基线能力 | Feldera 对应 | 差异 / 可借鉴 |
| --- | --- | --- |
| 入口：`.stream.xml` 顶层 `<sql sinkBean>`，EQL 与 ORM 共用 parser | 单条 SQL 文本（多语句）经 `DBSPCompiler` 顺序编译 | 形态不同；**可借鉴**其"列级注解"表达方式，落到 nop 的 schema 内嵌属性上 |
| 内嵌 schema，9 受管类型闭集 | `CREATE TABLE` 列声明 + `LATENESS` 列注解（`SqlExtendedColumnDeclaration.java:58`） | **最直接可借鉴**：把 `allowedLateness` 从查询配置下沉为列属性 |
| SELECT / WHERE / 五聚合 / GROUP BY | 标准聚合 + `FILTER` + `DISTINCT`（`aggregates.md:11-24`） | Feldera 聚合面宽；nop 五聚合闭集可保持，但可加 `FILTER` 子句（代价低、语义清晰） |
| `TUMBLE(t, INTERVAL)` 伪表函数 | `TUMBLE` / `HOP` / `SESSION` 表函数（`table.md:24-168`） | **可借鉴**：`HOP`（slide/size）与 `SESSION`（gap + 可选 key）三种形态 + 严格"short interval / 常量 / 正值"校验（`CalciteToDBSPCompiler.java:607-618`） |
| 窗口列 `window_start/window_end`（bigint epoch-millis） | 同名 `window_start/window_end`（`table.md:68-69`） | **字段命名完全一致**，nop 无需改名即可对外互认 |
| 双流等值 join（INNER/LEFT/RIGHT/FULL，window join 补齐于 watermark） | 等值 join（`join.rs`）+ ASOF（`asof_join.rs`，仅 LEFT + `MATCH_CONDITION` 仅 `>=`，`grammar.md:455/746/751`） | **可借鉴**：`LEFT ASOF JOIN ... MATCH_CONDITION (ts >= ts)` 是 nop 目前完全没有的"按时间就近匹配"形态，且其限制写法（仅 LEFT、仅 `>=`）是好的 fail-fast 模板 |
| 静态维表 lookup join | 普通 join 中维表侧标 `LATENESS`/`PRIMARY KEY` 即可（`streaming.md:168`、`unique_keys.md:33`） | Feldera 没有独立 lookup join 概念；nop 的 `IJdbcTemplate` 显式维表更明确，保持现状 |
| 无窗口持续 GROUP BY（D1 last-value-wins） | 默认 insert/delete changelog（`intro.mdx:43-44`），可 `emit_final` 收敛（`streaming.md:29-41`） | **差异最大**：Feldera 默认 retract、可选终值；nop 默认终值、无 retract。建议 nop 先增 `emitFinal`/`appendOnly` 两档，再评估 retract |
| UNION ALL（bag 语义） | 标准 UNION / INTERSECT / EXCEPT（`RejectUnsupportedPlans.java:40` 提到 grouping constructs 用 IS NOT DISTINCT FROM 比较） | 对齐；nop 的 UNION DISTINCT fail-fast 可保留 |
| 分析窗口 OVER（ROW_NUMBER, FRAME_SLIDING_AGG） | `ROW_NUMBER/RANK/DENSE_RANK/LEAD/LAG/FIRST_VALUE/LAST_VALUE/...`（`aggregates.md:196-280`） | 能力更宽；nop 的 `FRAME_SLIDING_AGG` 对应其窗口聚合，可按需补 `LEAD/LAG`（在已开窗前提下） |
| 不支持：全局 ORDER BY/LIMIT、非等值 join、DISTINCT 聚合、HAVING/CTE、全局聚合、FULL window join、UNION DISTINCT、Flink 方言 | ORDER BY 被**忽略并告警**（`CalciteToDBSPCompiler.java:2676 warnNoSort`，issue #2833）；`WITHIN DISTINCT`/`OVER` 内 DISTINCT/ROW 等值被拒绝（`RejectUnsupportedPlans.java:27-44`）；不支持聚合清单见 `unsupported-operations.md:11` | **关键分歧**：Feldera 对 `ORDER BY` 宽容（忽略 + warn），nop 选择 fail-fast。nop 的严格策略更不易出静默错误，建议保留，并借鉴 Feldera 的"文档锚点"提升可诊断性 |
| 语义：D1 last-value-wins、D6 事件时间、D9 allowedLateness 已放行 | D6：waterline 派生（`waterline.rs:41`）；D9：等价（`LATENESS` 就是常量迟到界）；D1：相反（默认 retract） | D6/D9 可直接对齐；D10 accumulationMode / D11 trigger 在 Feldera 处同样不存在（窗口固定触发），nop 继续 fail-fast 合理 |
| 架构：EQL AST → StreamSqlCompiler → 流模型 XML；三注册表；无代价优化器 | Calcite 完整优化器 + 单调性分析 pass（`CalciteOptimizer.java:232`、`InsertLimiters.java:88-105`）+ Rust 代码生成 | **可借鉴的最小集合**：单调性分析这一遍（不需要引入完整优化器也能做"哪些列单调递增"的静态推导），nop 可用它驱动窗口/状态裁剪 |

## 引用出处

以下路径若无前缀均相对本地源码根 `/Users/abc/sources/streaming-sql/feldera`（已在写毕后逐条验证存在；行号来自实际读取内容）。

- `LICENSE`（MIT 开源版 + Feldera Enterprise 段落）
- `sql-to-dbsp-compiler/calcite_version.env`（CALCITE_CURRENT="1.42.0"）
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/DBSPCompiler.java` :126, :187
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/frontend/CalciteToDBSPCompiler.java` :248, :578, :607, :612, :618, :658, :674, :693, :2676, :2685
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/frontend/parser/SqlLateness.java` :23
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/frontend/parser/SqlExtendedColumnDeclaration.java` :58-59
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/frontend/calciteCompiler/RejectUnsupportedPlans.java` :27-44, :54, :56-66
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/frontend/calciteCompiler/CalciteFunctions.java` :508-513
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/frontend/calciteCompiler/optimizer/CalciteOptimizer.java` :232
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/frontend/calciteCompiler/optimizer/SessionRewriteRule.java` :26, :62, :92
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/visitors/outer/monotonicity/InsertLimiters.java` :88-105, :105
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/visitors/outer/FindUnboundedState.java` :98
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/circuit/operator/DBSPInputMapWithWaterlineOperator.java` :30-32
- `sql-to-dbsp-compiler/SQL-compiler/src/main/java/org/dbsp/sqlCompiler/compiler/backend/rust/RustFileWriter.java` :140
- `crates/dbsp/src/operator/time_series/waterline.rs` :35-41
- `crates/dbsp/src/operator/join_range.rs` :14-40
- `crates/dbsp/src/operator/asof_join.rs`、`crates/dbsp/src/operator/join.rs`、`crates/dbsp/src/operator/distinct.rs`、`crates/dbsp/src/operator/semijoin.rs`
- `crates/dbsp/src/operator/time_series/rolling_aggregate.rs`、`crates/dbsp/src/operator/time_series/window.rs`
- `docs.feldera.com/docs/sql/intro.mdx` :25-44
- `docs.feldera.com/docs/sql/streaming.md` :12, :21-27, :29-41, :44-89, :154, :168-169, :219
- `docs.feldera.com/docs/sql/table.md` :24-46, :68-69, :71-94, :117-118, :127-168, :181-183
- `docs.feldera.com/docs/sql/aggregates.md` :11-24, :196-280
- `docs.feldera.com/docs/sql/unsupported-operations.md` :11, :17, :23
- `docs.feldera.com/docs/sql/grammar.md` :455, :746, :751, :868
- `docs.feldera.com/docs/sql/materialized.md` :19, :27-30
- `docs.feldera.com/docs/sql/recursion.mdx` :32
- `docs.feldera.com/docs/connectors/unique_keys.md` :33
- `docs.feldera.com/docs/pipelines/fault-tolerance-overview.md` :3, :22, :27
- `docs.feldera.com/docs/pipelines/fault-tolerance.md` :3, :40, :51
- 未验证项（已标注）：`docs/pipelines/metrics.md` 的 checkpoint 指标行号、非等值 join 的完整语义边界、`JOIN` 对 nullable 列的处理。
