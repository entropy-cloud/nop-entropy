# Materialize streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: 最值得借鉴两点——(1) 把**行级算子规范化成一个可序列化的 `MapFilterProject` 三元组**（expressions/predicates/projection，谓词按列序+guard 排列），作为 SQL→流管线的中间表示，正对 nop 的 `StreamSqlCompiler` 缺口；(2) 用 **`WHERE mz_now() < ts + INTERVAL ...` 形式的 temporal filter 表达有界状态窗口**，并在 LIR 层给 `Reduce` 打 `TemporalBucketing` arrangement 策略——这是 nop 在"无 watermark 模型"下也能给持续 GROUP BY 上界的另一条路。

## Context

Materialize 是"把 SQL 查询改写成增量 dataflow"的实时数据平台，PostgreSQL 方言 + PostgreSQL 协议，README 明确宣称支持 HAVING/ORDER BY/LIMIT/DISTINCT 与子查询去关联化——恰好是 nop-stream-sql 构建期 fail-fast 的那一片。它同时是**完全不做 watermark/事件时间**的对照极端：时间语义只有 progress frontier 与处理时间 `mz_now()`。本调研依据本地源码 `/Users/abc/sources/streaming-sql/materialize`（HEAD `80f0437e204f24c59e7cccea9ec80f5ce8f0cac3`，2026-10-07），目的是为 nop 的 DISTINCT/HAVING、维表 join、状态有界性、失败清单边界提供源码级参照，不重复 `2026-10-06a` 已裁定的 vision Non-Goal。

## 项目概览

- **定位**：实时数据集成/查询平台，把 SQL 查询递归维护成 dataflow，强调强一致（Strict Serializable）而非近似结果。
- **语言**：Rust（`src/` 下 140+ crate），TS `console/`，Python 测试工具链（`pyproject.toml`）。
- **许可证**：**Business Source License 1.1**（`LICENSE` 首行）——Additional Use Grant 限 24GiB 内存 / 48GiB 磁盘，Change Date `October 07, 2030` 后转 Apache 2.0。**非开源许可证**，只能作为设计参照，不能引入代码。
- **演进状态**：活跃，HEAD 为 2026-10-07 的 CI 配置提交。
- **SQL 能力入口**：`src/sql/` 是 SQL 前端——`src/sql/src/parse.rs`（re-export `src/sql-parser/src/parser.rs`）→ `src/sql/src/plan/statement.rs:292` `plan()` 分发 → `src/sql/src/plan/query.rs:131` `plan_root_query` 生成 HIR → `src/sql/src/plan/lowering.rs`（HIR→MIR，去关联化）→ `src/expr/src/relation.rs` `MirRelationExpr` → `src/transform/src/lib.rs:742` 规则优化 → `src/compute-types/src/plan/lowering.rs`（MIR→LIR）→ `src/compute/src/render.rs`（LIR→timely/differential dataflow）。

## 十维度分析

### 1. SQL 前端

- **parser**：自研（非 Calcite、非 PG 源码），`src/sql-parser/src/lib.rs:4-6` 明确写"derived from the sqlparser-rs project ... incorporated directly into Materialize on December 21, 2019"，此后独立演进（含 `ENVELOPE`/`AS OF`/`REFRESH`/`SUBSCRIBE` 等扩展）。
- **planner**：自研两段式——AST→HIR（`src/sql/src/plan/query.rs`，带 Scope/列绑定/类型检查）→ HIR→MIR（`src/sql/src/plan/lowering.rs:10` 模块注释："The most crucial part of lowering is **decorrelation**"，即子查询去关联化内置在 lowering 里，不靠独立规则）。
- **optimizer**：`src/transform/src/lib.rs:742` `Optimizer`，`logical_optimizer()`（`:752`）把 `Transform` 实现按阶段拼装：`ColumnKnowledge` / `PredicatePushdown` / `Demand` / `RedundantJoin` / `JoinImplementation` / `ReduceReduction` / `ThresholdElision` / `UnionCancel` / `LiteralLifting` / `CSE` / `Monotonic` 分析等。分析（`analysis/`）与变换（`fusion/`、`movement/`、`canonicalization/`）分层。
- **表达式/关系 IR**：`src/expr/src/relation.rs` `MirRelationExpr`（`:107` 起）与 `src/expr/src/scalar.rs` `MirScalarExpr`；聚合在 `src/expr/src/relation/func.rs:1936` `AggregateFunc`。

### 2. Table & Changelog 模型

- **changelog 的本体是 differential 的 `(row, timestamp, diff: i64)`**：`Diff` 为 `mz_repr::Diff`（i64），-1 即撤回。撤回不是可选能力，而是数据模型本身。
- **关系算子集**（`src/expr/src/relation.rs`）：`Constant(:107)` / `Get(:116)` / `Let(:131)` / `LetRec(:154)` / `Project(:170)` / `Map(:179)` / `FlatMap(:190)` / `Filter(:201)` / `Join(:214)` / `Reduce(:238)` / `TopK(:255)` / **`Negate(:278)`** / **`Threshold(:285)`** / `Union(:292)` / `ArrangeBy(:303)`。`Negate` 撤回 + `Threshold` 去重（保留计数>0 的行）即 DISTINCT 的实现原语。
- **对象形态**（`src/sql-parser/src/ast/defs/statement.rs:44-113`）：`SELECT/INSERT/UPDATE/DELETE`、`CREATE SOURCE/SUBSOURCE/SINK/VIEW/MATERIALIZED VIEW/TABLE/TABLE FROM SOURCE/INDEX/TYPE/CLUSTER/SECRET/CONNECTION/WEBHOOK SOURCE`、`SUBSCRIBE`、`EXPLAIN {PLAN,PUSHDOWN,TIMESTAMP,SINK SCHEMA,ANALYZE}`、`DECLARE/FETCH/PREPARE/EXECUTE`、DDL/DCL。**无 `CREATE FUNCTION`/`CREATE AGGREGATE`**（Statement 枚举中不存在），即无 UDF/UDAF。
- **源端 changelog 形态**：`SourceEnvelope::{None,Debezium,Upsert,CdcV2}`（`src/sql-parser/src/ast/defs/ddl.rs:730`）。`None` = append-only。
- **sink 端 changelog 形态**：`SinkEnvelope::{Debezium,Upsert}`（同文件 `:782`）——**下游必须显式声明要 retract 流还是 upsert 流**。
- **物化视图 vs 虚拟视图**：`CREATE VIEW` 只跑逻辑优化不建 dataflow（`src/adapter/src/optimize/view.rs:16-17` 模块注释："This is used for `CREATE VIEW` statements ... where no physical optimization is needed"）；`CREATE INDEX` / `CREATE MATERIALIZED VIEW` 才进 `src/adapter/src/optimize/{index,materialized_view}.rs` 的完整流水线。
- **表**：`CREATE TABLE` 用户表，无 append-only 开关（changelog 形态由 envelope 承担）。

### 3. 时间语义

- **没有 watermark，没有事件时间**。全仓 `watermark` 命中均为无关标识符（kafka metrics、cluster 模块等），无事件时间推进协议。
- **时间模型 = progress frontier**：所有 collection 携带 `since` / `upper` 两个 antichain frontier（`src/persist-client/src/lib.rs:284,294,453` 等），consolidate 与 peek 都以 frontier 为准；迟到数据不是"被丢弃"而是"在正确的时间戳上补上 diff"。
- **处理时间**：`mz_now()`（`src/sql/src/func.rs:4765`）与遗留别名 `mz_logical_timestamp()`（`:4760`），均可在谓词中出现。
- **temporal filter**：`src/expr/src/scalar.rs:557` `as_mut_temporal_filter`——识别形如 `mz_now() <BINOP> <EXPR>` 的谓词并归一（`:551-556` 注释给出定义），只接受"一侧是 `mz_now()`、另一侧不含 `mz_now()` 且可 cast 到 mz_timestamp"的二元比较，否则报 `Unsupported temporal predicate`。这是 Materialize 给状态上界的唯一机制。
- **batch 读的时间点**：`AS OF <ts>` / `UP TO`；查询须引用可物化函数，除 `mz_now()`（`bail_unsupported!("unmaterializable function (except `mz_now`) in an AS OF query")`）。
- **隔离级别**：`src/sql/src/session/vars/value.rs:878` `IsolationLevel::{ReadUncommitted,ReadCommitted,RepeatableRead,Serializable}` + 内部 `StrongSessionSerializable`，另有 bounded staleness 档。
- **迟到/idle**：不适用——没有事件时间就没有迟到；idle 由 frontier 单调推进表达，无"空闲分区卡水位"问题。

### 4. 窗口与聚合

- **没有流式窗口算子**：无 tumbling/hopping/session/sliding 窗口算子，`MirRelationExpr` 中没有 window 变体。窗口只能通过**"temporal filter 谓词 + GROUP BY"**表达，例如 `test/sqllogictest/temporal_bucketing.slt:32` 的 `WHERE event_time + INTERVAL '45 day' > mz_now() GROUP BY k`，或 `test/sqllogictest/temporal.slt:24` 的 `WHERE mz_now() BETWEEN a AND b`。
- **为这条路做的专门优化**：LIR 层给 `Reduce` 打 `ArrangementStrategy::TemporalBucketing`（`src/compute-types/src/plan.rs:165`，lowering 见 `src/compute-types/src/plan/lowering.rs:45`），执行侧 `src/compute/src/extensions/temporal_bucket.rs` 提供 `bucket(as_of, threshold)`——把待处理 update 按时间分桶、只揭示不超前于 frontier 的数据，使 temporal-filter 上的 GROUP BY 输入 arrangement 有界。测试文档注释见 `test/sqllogictest/temporal_bucketing.slt:11-17`。
- **`OVER` 分析窗口**：支持 `ROW_NUMBER/RANK/DENSE_RANK`（`src/sql/src/func.rs:3847-3853`）、`LAG/LEAD/FIRST_VALUE`（`:3873,:3921,:3952`）及全部聚合带 `OVER`。frame 限制（`src/sql/src/plan/query.rs`）：
  - `:6129` `GROUPS in window frames` 不支持；
  - `:6112` `RANGE in non-default window frames` 不支持（仅默认 frame）；
  - `:5693` `DISTINCT in window aggregates` 不支持；
  - `:5678` `mixed unbounded - offset frames` 不支持；
  - `:5678` 起及 `:6080-6100` frame offset > 1000000 不支持；
  - `:5963` 非聚合窗口函数的 `FILTER` 不支持。
- **聚合函数集**：PG 级全集，`src/expr/src/relation/func.rs:1936` `AggregateFunc`（按类型实例化的 `Max*/Min*/Sum*/Count/Any/All/JsonbAgg/JsonbObjectAgg/...`），另有 `count distinct` 等的去重修正逻辑（同文件 `:138`、`:208` "correct for negative diffs (retractions)"）。
- **DISTINCT 聚合**：**支持**。`AggregateExpr.distinct` 由 `src/sql/src/plan/query.rs` `plan_aggregate_common` 保留（函数尾部 `Ok(AggregateExpr { func, expr, distinct })`（`src/sql/src/plan/query.rs:5416`）），落到 LIR 后 `src/compute/src/render/reduce.rs:517` `if distinct { ... }` 走 `build_reduce_inaccumulable_distinct`（`:781`），复杂查询再按 `simple_aggrs` / `distinct_aggrs` 分组双遍处理（`:1368`、`:1391-1440`）。
- **HAVING**：支持，`src/sql/src/plan/query.rs:2586` 正常绑定成聚合后的谓词。
- **DISTINCT / DISTINCT ON**：支持，`src/sql/src/plan/query.rs:2735` `match s.distinct`，落到 `Threshold` 算子。
- **TopN**：`TopK` 关系算子（`src/expr/src/relation.rs:255`）+ `src/compute/src/render/top_k.rs`，可物化维护。
- **ORDER BY / LIMIT**：查询面由 `RowSetFinishing`（`src/expr/src/relation.rs:3520`）在结果阶段应用；流式（SUBSCRIBE）下 `is_streamable`（`:3554`）要求 `order_by` 为空，`RowSetFinishingIncremental`（`:3644`）维护剩余 limit/offset。

### 5. Join

- **所有 join 都是增量的**（differential），没有"流-流 join vs 维表 join"的分叉，也没有 lookup join 概念——右侧集合同样是被持续维护的 collection。
- **多路 join 实现选择**：`src/expr/src/relation.rs:3211` `JoinImplementation::{Differential, DeltaQuery, IndexedFilter, Unimplemented}`——
  - `Differential`：一串二元 join；
  - **`DeltaQuery`（:3240）**：为每个输入各建一条独立 delta-query dataflow（DCG），README 称可避免中间状态爆炸、测试到 64 路 join；
  - `IndexedFilter`：把常量谓词 join 改走用户索引（如 `(f1=3 AND f2=5) OR (f1=7 AND f2=9)`）。
  - 选择逻辑在 `src/transform/src/join_implementation.rs`（模块注释 `:12-17`："determining the type of join (e.g. differential linear, or delta queries), the orders of collections, lifting predicates if useful arrangements exist, and identifying opportunities to use indexes to replace filters"）。
- **等值/非等值**：等值键走 arrangement join，非等值条件作为 filter 保留在 join 上（无 nop 那样的 fail-fast）。跨库无限制。
- **子查询/LATERAL**：内置去关联化（`src/sql/src/plan/lowering.rs:13` 模块注释），README 称"Materialize's SQL optimizer performs subquery decorrelation out-of-the-box"。
- **窗口 join / interval join**：**不存在**——需要时写成 temporal filter + join，或靠 `mz_now()` 谓词给状态上界。
- **执行**：`src/compute/src/render/join/{delta_join,linear_join}.rs`（模块注释含 watermark 命名的内部 arrange 机制，与事件时间水位无关）。

### 6. CEP / 模式匹配

- **不支持**。`src/sql-parser/src/ast/defs/` 中无 `MATCH_RECOGNIZE` / pattern 相关 AST；`Statement` 枚举亦无对应项。需要 CEP 时只能自己写 UDF（但无 `CREATE FUNCTION`，只能靠连接器/外部处理）。

### 7. 状态与容错

- **状态存放**：
  - **持久层** `src/persist` / `src/persist-client`：LSM-on-object-storage 的 shard，带 `since`/`upper` frontier、lease（`SinceHandle`，`src/persist-client/src/lib.rs:453-493`）与 critical since（`:473`，注释警告"if lost, the shard's since will be permanently stuck"）。
  - **计算层 arrangement**：`src/row-spine` 内存/本地行存，**可从 persist 全量重算**，因此 compute 节点无独立快照协议。
- **checkpoint**：**没有 barrier/checkpoint 概念**——持久化的就是 collection 的 frontier，storage 写入即提交；compute 重启 = 从 persist 重放。
- **exactly-once**：由 persist 的原子 batch 提交 + frontier 单调推进保证；sink 侧 `src/storage/src/sink/kafka.rs:920` "IMPORTANT: to achieve exactly once guarantees, the producer that will resume..."（Kafka producer 幂等/事务），`:300` 提及尚需完整 exactly-once 支持的部分。
- **结果输出语义**：**retract（diff）为默认**；对外可选 `ENVELOPE DEBEZIUM`（完整变更）或 `ENVELOPE UPSERT`（键值覆盖），见 §2。查询结果面另有隔离级别档（`src/sql/src/session/vars/value.rs:878`）。
- **无 allowedLateness / trigger / accumulationMode 概念**：对应 Flink 语义在 Materialize 中无对应物（时间推进是 frontier，不是 watermark）。

### 8. 声明形态与可嵌入性

- **形态**：**服务端 + SQL 文本**。`environmentd`（`src/environmentd/`）暴露 PostgreSQL wire protocol，DDL/DML/SUBSCRIBE 全为 SQL 字符串；无文件级 schema、无库形态 API。
- **可嵌入性**：**不可嵌入**——`src/sql-parser/Cargo.toml:8` 与 `src/sql/Cargo.toml:7` 均 `publish = false`，workspace 内部使用；对外是独立进程（`src/materialized/` 二进制、`src/mz/` CLI）。且 **BSL 1.1 许可证禁止直接复用代码**（Change Date 2030-10-07 才转 Apache 2.0）。
- **对照 nop**：与 RisingWave 同为"服务 + SQL 文本"，与 nop 的 `.stream.xml` 内嵌 `<sql>` 完全相反。Materialize 的参照价值在**编译期中间表示与状态上界策略**，不在形态。

### 9. 架构要点

关键链路：`SQL 文本 → mz_sql_parser → plan::plan()/plan_root_query（HIR）→ plan/lowering.rs（去关联化 → MIR）→ transform::Optimizer::logical_optimizer（规则+分析）→ compute-types::plan::lowering（MIR → LIR，含 MFP/Join/Reduce/TopK/Threshold 计划结构）→ compute::render（LIR → timely/differential dataflow）→ persist（collection 提交）`；各语句类型用 `src/adapter/src/optimize.rs` 的 `Optimize` trait 组织成阶段化 pipeline（`:19-46` 注释说明阶段类型不可跨语句混用、`resolve` 做时间戳选择、`unapply` 取最终结果）。

值得记的三点：

1. **`MapFilterProject` 是行级算子的规范形**（`src/expr/src/linear.rs:45`）：`expressions: Vec<E>` + `predicates: Vec<(usize, E)>`（每条谓词前缀一个"从第几列起可求值"的列号，实现 guarded evaluation）+ `projection: Vec<usize>` + `input_arity`。可序列化、可 `canonicalize_mfp` 规范化、可整体下沉/上提。**这正对应 nop 的"EQL AST → 流模型 XML"之间缺一个可优化的中间层**——nop 现在是直接翻译，没有可做谓词下推/投影裁剪的规范形。
2. **错误语义用双树表达**：`src/compute/src/render.rs:10-32` 模块注释详述"oks tree + errs tree"——标量表达式求值失败不打断流，而是并行产生错误行。nop 当前表达式子集 fail-fast，无需此机制，但若将来放开标量子集，这个设计比 try/catch 更贴合流式。
3. **分析与变换分层、以 lattice 方式传递**：`src/transform/src/analysis/`（`monotonic.rs` 判"逻辑单调且不引入撤回"、`equivalences.rs` 等价类）产出 `Derived` 结果，供 `predicate_pushdown`/`join_implementation`/`column_knowledge` 消费。nop 的三注册表是静态面，缺这类**跨算子的流式分析**；`Monotonic` 分析是将来判断"该分组是否可走 append-only 快路径"的直接工具。

### 10. 与 nop-stream-sql 的对照与可借鉴点

Materialize 是**能力面更宽（DISTINCT/HAVING/子查询/TopN 全开）、时间面更窄（无 watermark、无事件时间）**的对照物。它证明了两件事：(a) DISTINCT/HAVING 不需要"窗口语义"参与，纯粹是编译期改写 + 一个 `Threshold`/第二层 Filter，nop fail-fast 是**范围裁定**而非技术必然；(b) 状态有界性不必依赖 watermark，可以靠"谓词 + arrangement 策略"实现。详细逐条对照见下节。

## 对照 nop-stream-sql 已交付子集

| 能力 | nop-stream-sql 现状 | Materialize 做法 | 差异与可借鉴点 |
|---|---|---|---|
| 入口 | `.stream.xml` 顶层 `<sql sinkBean>`，EQL 复用 ORM parser，内嵌 9 类型 schema | 独立 SQL 文本 + `CREATE TABLE/SOURCE` 列定义，PG 方言 | 形态相反（嵌入式 XML vs 服务 SQL）。**可借鉴**：Materialize 的 AST→HIR→MIR→LIR **四段中间表示**，每段有独立模块与独立优化入口；nop 是 EQL AST 直接编译成流模型 XML，中间无可优化层 |
| SELECT 投影 / WHERE | 已交付 | 支持，且经 `MapFilterProject` 规范化（`src/expr/src/linear.rs:45`） | **可借鉴度最高**：把 nop 的"标量表达式子集 + 谓词"也归一成 MFP 三元组（表达式表 / 带 guard 的谓词表 / 投影下标），后续加谓词下推、常量折叠、投影裁剪都是在这个结构上做一次遍历 |
| 五聚合（sum/count/avg/min/max，COUNT(*)） | 已交付 | PG 全集（`src/expr/src/relation/func.rs:1936`），按类型实例化 | nop 面窄是裁定结果。**可借鉴**：Materialize 的 `AggregateFunc` 按"返回类型 + 输入类型"拆成几十个变体，比"函数名 + 运行时类型"更利于静态校验——nop 的三注册表 aggregator 面可参考此拆法 |
| GROUP BY（无窗口持续） | 已交付，last-value-wins 发射运行值 | `Reduce`，输出带 diff 的撤回流 | **核心差异**。Materialize 能给出正确终态是因为有 `-1` diff；nop 没有。**可借鉴**：不引入 diff 的前提下，用 temporal-filter 思路给持续 GROUP BY 加"可选的时间上界谓词"（见下行） |
| 窗口 / 状态有界性 | `TUMBLE(t, INTERVAL)` 伪表函数，watermark 关窗 | 无窗口算子；`WHERE mz_now() < ts + INTERVAL '45 day'` temporal filter + `ArrangementStrategy::TemporalBucketing`（`src/compute-types/src/plan.rs:165`） | **重要旁路**：Materialize 证明"给状态上界"可以完全不靠 watermark。nop 若需要**处理时间**的持续聚合上界（如"只统计最近 N 天"），可以在 `<sql>` 的 WHERE 中允许一个受限的 `now()` 类谓词，编译成状态 TTL/界标，而不必引入新的窗口算子 |
| TUMBLE 单位/时间列 | SECOND..WEEK，bigint epoch-millis | 无 TUMBLE；interval 走 PG `INTERVAL` 类型 | nop 形态更贴近流用户直觉（且与 RisingWave 一致），不建议改 |
| 双流等值 join（INNER/LEFT/RIGHT/FULL，window join 补齐于 watermark） | 已交付 | 全部 join 增量；多路走 `DeltaQuery` DCG（`src/expr/src/relation.rs:3240`） | nop 的 window join 靠 watermark 补齐，Materialize 无对应物。**可借鉴**（若将来 join 数 > 2）：`JoinImplementation` 把"join 形态选择"做成显式枚举并在规则期决定，而不是在执行期；nop 的 join 注册表可加一档"多路 join 展开策略" |
| 非等值 / 范围 join | fail-fast | 支持（非等值条件保留在 join 上），另有 `IndexedFilter` 走索引 | Materialize 宽松是因为 differential 无状态上界压力。nop 保持 fail-fast 与 vision Non-Goal 一致；**但** `IndexedFilter` 的思路（常量/小集合谓词改走维表索引）与 nop 的静态 lookup join 同源，值得记为将来"维表 + 常量谓词"的快路径 |
| 静态维表 lookup join | 已交付（`ITableLookup`→`IJdbcTemplate`/`IBatchLoader`） | 无专门算子（右侧 collection 也是增量维护的） | 定位差异：Materialize 假设维表也被同步维护，nop 假设维表是外部 DB 快照。**可借鉴**：Materialize 的 `IndexedFilter` 展示了"把常量谓词提前压到维表访问"的优化——nop 可在 `IBatchLoader` 上加"谓词下推到 SQL WHERE" |
| UNION ALL（bag 语义） | 已交付 | `Union`，multiset 天然 | 一致 |
| **DISTINCT 聚合** | **fail-fast** | 支持：`AggregateExpr.distinct` → `build_reduce_inaccumulable_distinct`（`src/compute/src/render/reduce.rs:517,781`），复杂时拆 `simple_aggrs`/`distinct_aggrs` 双遍（`:1368,1440`） | **最直接的可落地项**：`sum(DISTINCT x)` = 先按 x 去重（生成"去重后的流"），再做普通聚合。nop 已有算子展开机制，这是**纯编译期两级展开**，不需要 RowKind/retract/`Threshold` 之外的新语义。建议优先于其它 fail-fast 项放开 |
| **HAVING** | **fail-fast（default-reject）** | 普通聚合后谓词（`src/sql/src/plan/query.rs:2586`） | **成本最低的放开项**：nop 的 SQL→流模型展开天然可以再包一层 Filter 节点，无需新算子、无需 retract。与 `2026-10-06a` P2#16 "DISTINCT/HAVING 窄补强" 的裁定方向一致，Materialize 佐证二者可解耦——**HAVING 可独立于 DISTINCT 先行** |
| CTE / INTERSECT / EXCEPT | fail-fast | 支持（INTERSECT/EXCEPT 需 `Threshold` 类算子） | nop 保持 fail-fast 合理；INTERSECT/EXCEPT 在无 retract 模型下语义不完整，Materialize 能做也是因为它有 diff |
| 全局聚合（无 GROUP BY） | fail-fast | 支持（单键 `Reduce`，带 diff） | 同上：Materialize 有 `-1` 才有意义。**佐证 nop fail-fast 正确** |
| 全局 ORDER BY / LIMIT | fail-fast | 查询面 `RowSetFinishing`（`src/expr/src/relation.rs:3520`）；流式面 `is_streamable` 要求 order_by 为空（`:3554`） | **值得抄的边界定义**：Materialize 也**不允许**带 ORDER BY 的流式结果，只允许 limit/offset 的增量消费，TopN 要物化成 `TopK` 算子。nop 若要放开，应只放"有分组键的 TopN"（对齐 RisingWave `StreamGroupTopN`），全局 ORDER BY 维持 fail-fast——两项目在此结论一致 |
| DISTINCT（非聚合，SELECT DISTINCT） | 不在纳入面 | 支持（`Threshold`） | 若放开，Materialize 的原语是"撤回 + 阈值"；nop 无 retract，**不可直接搬**，需先解决 D1 |
| retract / upsert 输出 | D1 last-value-wins，无 RowKind | diff（i64）为模型本体；sink `ENVELOPE DEBEZIUM/UPSERT`（`ddl.rs:730,782`） | **结构性借鉴**：Materialize 把"下游要什么 changelog"做成 sink 的显式 DDL 选项。nop 即使维持 D1，也可在 `<sql sinkBean>` 上预留一个 `changelog="value"` 属性位，把将来 retract/upsert 的接入点先占住，避免改遍所有 sink |
| 事件时间 / 处理时间 | D6 事件时间 | 只有处理时间 `mz_now()`（`src/sql/src/func.rs:4765`）+ frontier | nop 在事件时间上**强于** Materialize；Materialize 在处理时间窗口上更成熟（temporal filter + bucketing）。二者互补：nop 可借其处理时间路径，不必学其"放弃事件时间" |
| 允许迟到 / D9 allowedLateness | 已放行 | 无此概念 | nop 更强，无借鉴点；反证 D9 是流式语义必需，Materialize 用 frontier 绕开了 |
| 水印声明 | `<window>/<strategy>` 上声明 | 无水位 | nop 的水位声明位在窗口上，而不在 SQL 的时间列上——可参照 RisingWave 而非 Materialize |
| 维表 join vs CEP | CEP 走 `pattern.xdef`（不在 SQL 面） | 无 CEP | Materialize 在 CEP 上无参照价值 |
| Flink SQL 方言 | 非目标 | PG 方言，非 Flink | 一致 |
| 双目标（同查询可译 RDBMS 或编译流管线） | 已交付（W2/T1 边界已声明） | **天然双目标**：`CREATE VIEW` 不建 dataflow、同一条 SQL 在 peek 时批执行；`CREATE INDEX/MV` 才增量维护 | **值得借鉴的模型**：同一份 SQL 文本，"是否物化"是**目录级选项**而非语法差异。nop 的"同查询译 RDBMS 方言或编译流管线"已有该性质，Materialize 证明该性质可以做到用户无感（仅一条 DDL 之差） |

## 引用出处

以下路径均在写入后以 `test -e` 逐条验证存在；行号引用来自本次实际读取的文件内容。

**Materialize 源码**（`/Users/abc/sources/streaming-sql/materialize/`）：

- `LICENSE`（BSL 1.1、Change Date October 07, 2030）、`README.md`（定位与能力声明）
- `src/sql-parser/src/lib.rs:4-6`（sqlparser-rs 血统声明）、`src/sql-parser/Cargo.toml:8`（`publish = false`）
- `src/sql-parser/src/ast/defs/statement.rs:44-113`（`Statement` 枚举，无 `CreateFunction`/`CreateAggregate`）、`src/sql-parser/src/ast/defs/ddl.rs:730`（`SourceEnvelope`）、`:782`（`SinkEnvelope`）
- `src/sql/src/parse.rs`、`src/sql/src/plan.rs`（模块注释：入口为 `statement::handle_statement`——注释已过时，实际入口见下行）、`src/sql/src/plan/statement.rs:292`（`pub fn plan(`）、`src/sql/src/plan/query.rs:131`（`plan_root_query`）、`:2586`（HAVING）、`:2735`（DISTINCT）、`:5678/:5693/:5963/:6080-6100/:6112/:6129`（窗口 frame 与 DISTINCT 聚合限制）
- `src/sql/src/plan/lowering.rs:10-14`（HIR→MIR 与 decorrelation 模块注释）
- `src/sql/src/func.rs:4760`（`mz_logical_timestamp`）、`:4765`（`mz_now`）、`:3847-3952`（窗口函数注册）
- `src/sql/src/session/vars/value.rs:878`（`IsolationLevel`）
- `src/sql/Cargo.toml:7`（`publish = false`）
- `src/transform/src/lib.rs:742`（`Optimizer`）、`:752`（`logical_optimizer`）
- `src/transform/src/join_implementation.rs:12-17`（模块注释：join 实现选择）
- `src/transform/src/column_knowledge.rs:11`（模块注释）、`src/transform/src/analysis/monotonic.rs:11-13`（"logically monotonic ... do not introduce any retractions"）
- `src/expr/src/relation.rs:107,116,131,154,170,179,190,201,214,238,255,278,285,292,303`（`MirRelationExpr` 变体）、`:3211/:3225/:3240/:3244/:3247`（`JoinImplementation`）、`:3520`（`RowSetFinishing`）、`:3554`（`is_streamable`）、`:3644`（`RowSetFinishingIncremental`）
- `src/expr/src/linear.rs:45`（`MapFilterProject`）
- `src/expr/src/scalar.rs:551-557`（temporal filter 定义与 `as_mut_temporal_filter`）
- `src/expr/src/relation/func.rs:138,:208`（retraction 修正注释）、`:1936`（`AggregateFunc`）
- `src/compute/src/render.rs:10-32`（oks/errs 双树模块注释）、`src/compute/src/render/reduce.rs:517`、`:781`、`:1368`、`:1391-1440`（distinct 聚合拆分）、`src/compute/src/render/top_k.rs`、`src/compute/src/render/join/{delta_join,linear_join}.rs`
- `src/compute/src/extensions/temporal_bucket.rs:11,:39,:46`（`TemporalBucketing` 与 `bucket`）
- `src/compute-types/src/plan.rs:165`（`ArrangementStrategy::TemporalBucketing`）、`src/compute-types/src/plan/lowering.rs:10`（MIR→LIR）、`:45`（bucketing 下发）
- `src/adapter/src/optimize.rs:21-46`（`Optimize` 阶段化 pipeline 注释）、`src/adapter/src/optimize/view.rs:16-17`（`CREATE VIEW` 只跑逻辑优化）、`src/adapter/src/optimize/index.rs:10`（`CREATE INDEX` 优化器）
- `src/persist-client/src/lib.rs:284,:294,:453,:473`（since/upper frontier、lease、critical since）、`src/storage/src/sink/kafka.rs:300,:920`（exactly-once）
- `test/sqllogictest/temporal_bucketing.slt:11-17,:32,:48,:60`（temporal filter + GROUP BY 的 EXPLAIN 形态）、`test/sqllogictest/temporal.slt:24`（`mz_now() BETWEEN a AND b`）
- `src/materialized/src/bin/`、`src/mz/src/`（服务/CLI 形态）

**对照基线**：`docs-for-ai/03-modules/nop-stream-sql.md`（nop 已交付/不支持清单、D1/D6/D9 语义）。

**未在本仓库验证**：Materialize 官方文档中的产品级表述（隔离级别/consistency 页面）；`agent-notes/temporal-bucketing-reduce/plan.md`（测试注释引用但仓库内不存在）；BSL "Additional Use Grant" 在具体部署形态下的法律适用。

## Open Questions

- [ ] `MapFilterProject` 式规范形是否值得为 nop-stream-sql 引入——它会改变 `StreamSqlCompiler` 的输出结构，需评估与"双目标=同查询译 RDBMS"的兼容性（MFP 是流/批通吃的形态，理论上是加分项）。
- [ ] HAVING 先行、DISTINCT 殿后的拆分是否与 `2026-10-06a` P2#16 的"合并放开"裁定冲突，需回看 backlog 条目措辞。
- [ ] 处理时间语义（`now()` 类谓词）在 nop 中是否属于 D6 事件时间之外的第二时间域——若引入，会触及 D6 裁定范围。
- [ ] Materialize 是 BSL 许可，任何"代码级复用"都不成立，借鉴只能停留在设计层面——本条为硬约束。
