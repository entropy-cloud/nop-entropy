# RisingWave streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: 最值得借鉴两点——(1) 把**允许迟到量直接写进水位表达式**（`WATERMARK FOR col AS col - INTERVAL '4' SECOND`）+ 空闲 subtask 的全局水位刷新，用一个声明解决 D9 的"谁来推进水位"；(2) 把 **changelog 形态（Retract/AppendOnly/Upsert）做成规划期显式属性**（`StreamKind`），让有状态算子在建图期就知道自己吃的是不是可回退流，为 D1 从 last-value-wins 升级留出单点缝。

## Context

RisingWave 是"开源流式数据库"，与 nop-stream-sql 一样走**非 Flink SQL 栈**的独立实现路线，且其 SQL 前端用的是与 nop 高度同构的两个设计：`FROM TUMBLE(...)` 伪表函数表达窗口、`FROM 表名` 即数据源名——这使它成为本调研中与 nop-stream-sql 形态最接近的对照物。本调研依据本地源码 `/Users/abc/sources/streaming-sql/risingwave`（HEAD `e58cfcde2034ec2583576fdb2f25a3a7d0281e69`，2026-10-07），目的是为 nop-stream-sql 的 DISTINCT/HAVING、retract、水位声明、维表 join、失败清单边界等决策提供源码级参照，不重复 `2026-10-06a` 五方对比中已裁定的 vision Non-Goal。

## 项目概览

- **定位**：事件流平台 / 流式数据库，PostgreSQL 协议接入，持续增量物化视图（MV）替代 Debezium+Flink+服务库组合。
- **语言**：Rust（`src/` 下 40+ crate），附 Java connector node（`java/`）、TS dashboard。
- **许可证**：Apache License 2.0（`LICENSE` 首行）。
- **演进状态**：活跃，HEAD 为 2026-10-07 的依赖升级提交。
- **SQL 能力入口**：`src/frontend/` 是 SQL 编译核心——`src/sqlparser/`（词法/语法）→ `src/frontend/src/binder/`（绑定）→ `src/frontend/src/planner/`（绑定树→逻辑计划）→ `src/frontend/src/optimizer/`（规则优化+流化）→ `src/frontend/src/stream_fragmenter/`（切片成 fragment）→ meta/compute 执行。表达式与聚合实现在 `src/expr/`，流算子在 `src/stream/src/executor/`。

## 十维度分析

### 1. SQL 前端

- **parser**：自研（`src/sqlparser/`），源自 sqlparser-rs 血统但已独立演进；`src/sqlparser/src/lib.rs:17` 自述为 "ANSI:SQL 2011 lexer and parser"。扩展了 `TUMBLE`/`HOP`/`GAP_FILL`/`WATERMARK` 伪表函数、`WATERMARK FOR ... AS ...` DDL 子句、`EMIT IMMEDIATELY|ON WINDOW CLOSE`、`AS OF PROCESS TIME` 等方言。
- **planner/binder**：自研，**不使用 Calcite**。binder 按语法位置分文件：`src/frontend/src/binder/relation/{window_table_function,watermark,gap_fill,match_recognize,join,subquery}.rs`。
- **optimizer**：规则集 + 分阶段流水线，`src/frontend/src/optimizer/logical_optimization.rs` 组装数百条 `Rule`（如 `DistinctAggRule`、`OverWindowToTopNRule`、`SplitNowAndRule`、`ApplyToJoinRule`），由 `src/frontend/src/optimizer/heuristic_optimizer.rs:38` 的 `HeuristicOptimizer` 按 TopDown/BottomUp 遍历逐条应用。**不是代价模型优化器**，是 cascades 风格的启发式规则阶段制。

### 2. Table & Changelog 模型

- **对象形态**：`CREATE SOURCE` / `CREATE TABLE`（可带 connector，即"表即源"）/ `CREATE MATERIALIZED VIEW` / `CREATE VIEW` / `CREATE INDEX` / `CREATE SINK` / `CREATE SUBSCRIPTION`。
- **changelog 分类是显式规划属性**：`src/frontend/src/optimizer/property/stream_kind.rs:22` 定义三档——
  - `Retract`：完整旧值先行（Insert/Delete/UpdateDelete/UpdateInsert）；
  - `AppendOnly`：仅 Insert；
  - `Upsert`：更新只发新值、Delete 可能只带主键列（注释明写"**有状态算子通常无法正确处理，必须先转成 Retract**"）。
- **表级 append-only 开关**：`CREATE TABLE ... APPEND ONLY`（见 `e2e_test/streaming/watermark.slt` 的 `... APPEND ONLY` 形态）；`src/frontend/src/handler/create_table.rs:783` 强制"非 TTL 水位只允许定义在 append-only 表上"。
- **聚合可回退性**：`src/expr/core/src/aggregate/mod.rs:136` `build_retractable` vs `build_append_only`——同一聚合有 append-only 快路径与可回退实现两套，规划期选择。
- **显式 changelog 物化**：`src/stream/src/executor/changelog.rs:235` `ChangeLogMode::{Normal,Keyed}`，把 delete/update 归一成 insert 输出（下游 sink 要 append-only changelog 时用）。
- **物化视图**：`CREATE MATERIALIZED VIEW` 即默认持续物化；`src/frontend/src/handler/create_mv.rs:142` 支持 `EMIT ON WINDOW CLOSE`（延迟到窗口关闭才发射）。

### 3. 时间语义

- **水位声明（DDL 面）**：`WATERMARK FOR <col> AS <expr>`（`src/sqlparser/src/ast/ddl.rs:965` `SourceWatermark`），可 `ALTER WATERMARK FOR ... AS ... [WITH TTL]`（`src/frontend/src/handler/alter_watermark.rs:25`）。绑定在 `src/frontend/src/handler/create_source.rs:683` `bind_source_watermark`，**强校验表达式返回类型必须与列类型完全一致**，并做 session 时区重写。
- **关键点**：水位表达式形如 `date_time - INTERVAL '4' SECOND`（`e2e_test/nexmark/create_sources.slt:35`），即 **allowed lateness 直接编码在水位表达式里**——水位比最大事件时间落后 4 秒，等于"4 秒内的迟到数据仍被接受"。
- **迟到数据处理**：`src/stream/src/executor/watermark_filter.rs` 计算 `event_time >= watermark` 作为保留谓词（`:357` `build_watermark_filter_expr`），低于水位的行直接丢弃；水位本身在 checkpoint barrier 上持久化到 state table，并在 vnode 重分布时从全局表取 max。
- **空闲（idle）处理**：同文件 `idle_input`——若某 subtask 在 barrier 期间没有数据，连续 `UPDATE_GLOBAL_WATERMARK_FREQUENCY_WHEN_IDLE` 个 checkpoint 后异步刷新全局最大水位，**避免空闲分区卡住下游水位**。
- **水位作为规划属性**：`src/frontend/src/optimizer/property/watermark_columns.rs:25` `WatermarkColumns`（列→水位分组 id），逐算子传播；`StreamWatermarkFilter`（`src/frontend/src/optimizer/plan_node/stream_watermark_filter.rs:32`）插入在源后。
- **事件/处理时间**：事件时间由 `WATERMARK FOR` 列承载；处理时间用 `proctime()` 标量（`src/frontend/src/binder/expr/function/builtin_scalar.rs:785`）；`now()` 谓词被 `SplitNowAndRule` / `FilterWithNowToJoinRule`（`src/frontend/src/optimizer/rule/stream/`）拆成 temporal filter。

### 4. 窗口与聚合

- **窗口类型全集**：
  - 表函数窗口：**TUMBLE / HOP**（`src/frontend/src/binder/relation/window_table_function.rs:28` `WindowTableFunctionKind::{Tumble,Hop}`），第三种 `GAP_FILL`（`src/frontend/src/binder/relation/gap_fill.rs`）用于补洞填值。
  - **SESSION 窗口仅 batch 可用**：`e2e_test/batch/over_window/session/mod.slt.part:1` 明写 "Because currently general streaming version of session window is not supported yet, we only add e2e for batch mode."
  - 分析窗口 `OVER`：`ROW_NUMBER/RANK/DENSE_RANK/LAG/LEAD` + 聚合（`src/expr/core/src/window_function/kind.rs:27`），frame 支持 `ROWS/RANGE/SESSION`（`src/sqlparser/src/ast/mod.rs:963` `WindowFrameUnits::Session`）。
- **frame**：`OVER` 帧完整；表函数窗口的 frame 就是窗口本身（size/slide/offset，`src/frontend/src/optimizer/plan_node/generic/hop_window.rs:34`）。
- **聚合函数集**：`proto/expr.proto:499` `AggCall.Kind` 列出 SUM/MIN/MAX/COUNT/AVG/STRING_AGG/ARRAY_AGG/VAR_POP/VAR_SAMP/STDDEV_*/BIT_*/BOOL_*/JSONB_AGG/PERCENTILE_*/MODE/ARG_MIN/ARG_MAX/GROUPING/`USER_DEFINED` 等 30+ 种，另有 `WRAP_SCALAR`（把标量函数当聚合用）。
- **DISTINCT 聚合**：支持，且有专门改写——`src/frontend/src/optimizer/rule/distinct_agg_rule.rs:29` "Transform distinct aggregates to `LogicalAgg` -> `LogicalAgg` -> `Expand` -> `Input`"（用 Expand 加 flag 列，先去重再聚合）；`src/stream/src/executor/aggregate/distinct.rs:141` 是流侧实现。
- **TopN**：`StreamTopN` / `StreamGroupTopN`（`src/frontend/src/optimizer/plan_node/stream_{topn,group_topn}.rs`），即流上带分组的 TopN 可用。
- **UDAF**：`CREATE AGGREGATE`（`src/frontend/src/handler/create_aggregate.rs`）+ `AggCall::USER_DEFINED`。

### 5. Join

- **分派是一张能力决策表**（`src/frontend/src/optimizer/plan_node/logical_join.rs:1650` `to_stream`）：
  1. `AsofInner/AsofLeftOuter` → `to_stream_asof_join`；
  2. 有等值键 → 右侧若是 `AS OF PROCESS TIME` 扫描则 `to_stream_temporal_join`（维表 join），否则 `to_stream_hash_join`；
  3. 无等值键但可转 temporal → `to_stream_nested_loop_temporal_join`；
  4. 无等值键但可转区间谓词 → `to_stream_dynamic_filter`（**动态过滤替代非等值 join**）；
  5. 否则 `NotSupported("streaming nested-loop join")`，错误文案直接建议改写为 dynamic filter。
- **维表 join**：`temporal_join_on()`（同文件 `:1101`）——右侧是 logical scan 且 `AsOf::{ProcessTime,ProcessTimeBroadcast}`（`src/sqlparser/src/ast/mod.rs:3927`）才算 temporal join；仅支持 INNER/LEFT OUTER（`:1113`），还会做索引选择（`:1142`）。执行器 `src/stream/src/executor/temporal_join.rs`。
- **区间/非等值**：不支持通用 range join，靠 `StreamDynamicFilter` + `now()` temporal filter 覆盖常见区间场景。
- **子查询**：支持（含相关子查询），`LogicalApply` + `ApplyToJoinRule`、`PullUpCorrelatedPredicateRule` 等去关联化规则在 `logical_optimization.rs`。
- **窗口 join**：**没有专用算子**——两侧都 `FROM TUMBLE(...)`，把 `window_start/window_end` 作为 join key（`e2e_test/streaming/time_window_join.slt:23-27`），状态天然按窗口有界。

### 6. CEP / 模式匹配

- **支持**，且相当完整：`src/frontend/src/binder/relation/match_recognize.rs`（2226 行）实现 SQL:2016 `MATCH_RECOGNIZE`——`PARTITION BY` / `ORDER BY` / `PATTERN` / `DEFINE` / `MEASURES` / `ONE|ALL ROWS PER MATCH` / `EXCLUDE` / `AFTER MATCH SKIP ...` / `PERMUTE`（`MAX_PERMUTE_VARS = 6`，`:1025`）。
- 流侧 NFA 执行器：`src/stream/src/executor/match_recognize/{nfa,executor,incremental}.rs`，`nfa.rs` 有 `Quantifier` / `Pattern` / `SkipMode` / `SkipDegradation` / `ScanBudget`，带状态预算上限。
- 对 nop：nop-stream 已有 Flink 风格 NFA（`pattern.xdef`），此处参照价值在**预算/上限的防御性设计**（PERMUTE 组合爆炸的显式 cap 与错误文案）。

### 7. 状态与容错

- **状态存放**：Hummock（LSM-on-object-storage，`src/storage/src/hummock/`）+ 每算子 `StateTable`；聚合状态 `src/stream/src/executor/aggregate/{agg_group,agg_state,agg_state_cache}.rs`。
- **checkpoint**：全局 barrier 协议，meta 侧 `src/meta/src/barrier/`（`manager.rs` / `schedule.rs` / `checkpoint/`）；`src/meta/src/barrier/command.rs:819` `BarrierKind::{Initial,Barrier,Checkpoint(Vec<u64>})`——checkpoint barrier 携带前若干个非 checkpoint 的 prev-epoch，实现增量/合并提交。
- **exactly-once**：端到端依赖 sink 语义，`src/connector/src/sink/mod.rs:262` `sink_is_exactly_once(properties)` 按 connector 类型（Iceberg/DeltaLake/`is_exactly_once` 属性）判定；barrier + Hummock 原子提交保证算子内状态 exactly-once。
- **结果输出语义**：**retract 为主**——聚合算子输出 `Op::{Insert,Delete,UpdateDelete,UpdateInsert}` 完整变更（`build_retractable`），`StreamKind` 在规划期就声明该流的形态；`StreamChangeLog` 可把 retract 归一成 append-only 变更日志供 sink 消费。
- **初始化/backfill**：`src/stream/src/executor/backfill/{snapshot_backfill,arrangement_backfill,no_shuffle_backfill}`——新 MV 从上游快照补齐时保持进度一致。

### 8. 声明形态与可嵌入性

- **形态**：**服务端 + SQL 文本**。PostgreSQL wire protocol，DDL/DML 全部是 SQL 字符串（`CREATE SOURCE/TABLE/MATERIALIZED VIEW/INDEX/SINK/SUBSCRIPTION`），无文件级 schema、无库形态 API。
- **可嵌入性**：**不可嵌入**。`src/sqlparser/Cargo.toml` 带 `disable-publish = true`，workspace 内 crate 均为内部使用；对外是独立进程（`risedev` / `standalone` / docker）。`java/` 只是 connector JNI 绑定，不是 SQL 引擎嵌入库。
- **对照 nop**：nop-stream-sql 是**反向形态**——`.stream.xml` 内嵌 `<sql>` + 内嵌 `<schemas>` + `sinkBean` 绑定，经 SPI 编译成流模型。两者在"窗口用 `FROM TUMBLE(...)` 伪表"这一点上同构，在"DDL 内联 schema vs XML 内联 schema"上等价，在"服务 vs 嵌入库"上完全相反。

### 9. 架构要点

关键链路：`SQL 文本 → risingwave_sqlparser → Binder（BoundXxx）→ Planner（LogicalXxx）→ HeuristicOptimizer 分阶段规则 → to_stream 流化（StreamXxx）→ StreamFragmenter.build_graph（src/frontend/src/stream_fragmenter/mod.rs:168）→ meta 调度 → compute 执行`。

值得记的三点：

1. **"属性驱动的流化"而非"算子驱动"**：`StreamKind`（可回退性）、`WatermarkColumns`（水位列分组）、`EmitOnWindowClose`、`MonotonicityMap` 都是 plan node 的**属性**，`to_stream` 阶段据此选择算子实现。这与 nop 的"参数化算子面三注册表（schemas/aggregators/joins）"是同一思路的更完整版本——值得 nop 借鉴的是把**可回退性**也升格为一等属性。
2. **能力不足时给"改写建议"而不是裸报错**：非等值 join 的 NotSupported 文案直接指向 dynamic filter 文档（`logical_join.rs:1694` 起）。nop 的 fail-fast 错误码体系已有此雏形（`nop.err.*`），可补"改写建议"字段。
3. **规则阶段制而非代价优化**：`logical_optimization.rs` 把规则分组为若干 pass（谓词下推→关联化→聚合改写→窗口改写→TopN→...），每 pass 内固定顺序。nop 明确"无代价优化器"，与 RW 同路；RW 的可借鉴处是**pass 的命名分组与"该 pass 后计划必须满足什么不变量"的隐式契约**。

### 10. 与 nop-stream-sql 的对照与可借鉴点

形态最接近（`FROM TUMBLE`、FROM=表名、自研 parser、无代价优化器），差异集中在**changelog/retract 一档**与**失败清单宽窄**。详见下节逐条对照。核心借鉴两条：水位声明即迟到容忍度、changelog 形态即规划属性。

## 对照 nop-stream-sql 已交付子集

| 能力 | nop-stream-sql 现状 | RisingWave 做法 | 差异与可借鉴点 |
|---|---|---|---|
| 入口 | `.stream.xml` 顶层 `<sql sinkBean>`，EQL 复用 ORM parser，内嵌 9 类型 schema | 独立 SQL 文本 + DDL 内联 schema，`risingwave_sqlparser` | 同构思路（内联 schema、FROM=表名）。**可借鉴**：RW 把 schema 校验失败与类型不一致都翻成带"期望类型 vs 实际类型"的绑定错误（`create_source.rs:707-710`），nop 的 `nop.err.stream.invalid-arg` 可补同等信息量 |
| SELECT/WHERE/五聚合/GROUP BY | 已交付 | 全支持（聚合集大得多，`proto/expr.proto:499`） | nop 面窄是裁定结果；**可借鉴**：RW 的 `WRAP_SCALAR`（标量当聚合）是一种廉价扩聚合面的手段 |
| `TUMBLE(t, INTERVAL)` 伪表 | 已交付，时间列 bigint epoch-millis，SECOND..WEEK | `FROM TUMBLE(tbl, ts_col, INTERVAL ...)` + `HOP` | **形态几乎一致**（nop 少一参：RW 多传表名，nop 用 FROM 表名承担）。RW **多出 HOP**（步进滑窗），nop 无 hopping——若补滑窗，优先按 RW 的表函数形态而非引入新算子 |
| HOP / SESSION | 不支持 | HOP 流上支持；SESSION **流上也不支持**（batch only） | 佐证 nop "只做 TUMBLE" 不是孤例；若要补 SESSION，RW 的现状是明确的"尚未做"参照 |
| 双流等值 join（INNER/LEFT/RIGHT/FULL） | 已交付，window join 补齐于 watermark | hash join；窗口 join 靠双侧 TUMBLE 造 window_start key（`time_window_join.slt:23-27`） | nop 的 window join 是**内置补齐**，RW 要用户手写 key 对齐——nop 更省心，无需借鉴；RW 的 FULL JOIN 在窗口内可用，与 nop 一致 |
| 非等值/范围 join | fail-fast | 不支持通用 range join，但有 **dynamic filter 改写**兜底（`logical_join.rs:1695` 起） | 与 vision Non-Goal 不冲突的**薄层**：RW 也没做通用 range join，而是把常见"区间谓词"转成动态过滤。nop 若遇 `ts BETWEEN a AND b` 类需求，可参照"转成有界状态的 filter"而非放开 join |
| UNION ALL（bag） | 已交付 | Union 天然 multiset | 一致 |
| 静态维表 lookup join | 已交付（`ITableLookup`→`IJdbcTemplate`/`IBatchLoader`） | `AS OF PROCESS TIME` temporal join + 索引选择（`logical_join.rs:1101/1142`），仅 INNER/LEFT OUTER | RW 的 process-time temporal join 在语义上**就是 nop 的静态 lookup join**（都是"取当前版本"）。**可借鉴**：RW 允许为维表建索引后自动改走索引；nop 可在 `IBatchLoader` 层面暴露"已物化则走物化"的路径 |
| 无窗口持续 GROUP BY | 已交付（last-value-wins 发射运行值） | `StreamHashAgg` 输出完整 retract | **核心差异**。RW 的 `build_retractable` 两套实现 + `StreamKind` 规划期标注是 nop D1 升级的最短路径参考：先在编译产物里标"本管线可回退/不可回退"，暂不实现 retract，为将来留缝 |
| 分析窗口 OVER（ROW_NUMBER / FRAME_SLIDING_AGG） | 已交付 | `RowNumber/Rank/DenseRank/Lag/Lead` + 聚合，frame `ROWS/RANGE/SESSION` | nop 子集合理。**可借鉴**：RW 把 `SESSION` frame 只放给 batch 的"显式分档"做法，可直接抄进 nop 的 fail-fast 文案 |
| DISTINCT 聚合 | fail-fast | 两级聚合改写：`Expand(flag) → 中间 agg → 最终 agg`（`distinct_agg_rule.rs:29`）；流侧 `aggregate/distinct.rs` | **可借鉴度最高**：nop 已有三注册表与展开机制，把 `sum(DISTINCT x)` 编译成"先按 x 去重、再求和"的两级算子是纯编译期工作，不需要 RowKind/retract。建议列 roadmap 候选 |
| HAVING | fail-fast（default-reject） | 普通聚合后谓词，随 predicate pushdown 处理 | **可借鉴**：nop 的 SQL→流模型展开是单层的，HAVING 可以廉价地展开成"聚合结果再包一层 Filter 节点"——成本远低于 DISTINCT。建议优先于 DISTINCT 放开 |
| CTE / INTERSECT / EXCEPT | fail-fast | 全支持（`binder/set_expr.rs:64` `Union/Except/Intersect`） | 与 vision"窄子集"一致，不建议跟进；RW 的 `Intersect/Except` 在流上仍需阈值算子，成本高 |
| 全局聚合（无 GROUP BY） | fail-fast | 支持（`StreamSimpleAgg`），输出 retract | RW 能做是因为**必须配 retract**；nop 无 retract 时全局聚合只能给运行值且永不收敛。**佐证 nop 的 fail-fast 是正确裁定**，不建议放开 |
| 全局 ORDER BY / LIMIT | fail-fast | 流上支持 `StreamTopN` / `StreamGroupTopN` | **可借鉴的窄口**：`GROUP BY k ORDER BY agg LIMIT n`（group top n）在 nop 上可做成"与 group key 同键的有界 TopN"；纯全局 ORDER BY 仍应 fail-fast |
| 水位声明 / allowedLateness | D9 已放行（`<window>`/`<strategy>` 上），SQL 侧无声明位 | `WATERMARK FOR col AS col - INTERVAL '4' SECOND`，DDL 级声明，ALTER 可改 | **最值得借鉴**：把迟到容忍量并入水位表达式，一处声明同时表达"水位推进规则 + 允许迟到"。nop 现在 D9 在 window/strategy 上、SQL 时间列只有 bigint，建议评估把 allowedLateness 下沉到 `<sql>` 的时间列声明 |
| 迟到数据处理 | D9 allowedLateness 语义消费 | `event_time >= watermark` 过滤 + 水位持久化 + 空闲时全局水位刷新（`watermark_filter.rs`） | **可借鉴**：nop 的 allowedLateness 目前只推迟窗口关闭点；RW 还回答了"谁推进水位、空闲怎么办"。nop 若多 source/多分区，需同样的"全局最大水位"机制 |
| retract / upsert 输出 | D1 last-value-wins，无 RowKind | `StreamKind::{Retract,AppendOnly,Upsert}` 规划属性 + `StreamChangeLog` 归一 | **结构性借鉴**：先在 `StreamSqlCompiler` 产物上标注 changelog 形态（哪怕只有 append/retract 两档），把 D1 升级从"改所有算子"降为"改标注 + 改发射点" |
| 事件时间 / 处理时间 | D6 事件时间 | 事件时间 = `WATERMARK FOR` 列；处理时间 = `proctime()` / `now()` temporal filter | nop 事件时间已对齐；**可借鉴**：RW 的 `now()` 谓词被 `SplitNowAndRule` 拆成 temporal filter，是 nop 未来做"处理时间维表 join 刷新"的参考 |
| 全局 ORDER BY 之外的 CEP | 不在 SQL 面（nop CEP 走 `pattern.xdef`） | `MATCH_RECONIZE` 完整实现 | nop CEP 走声明式 XDSL 是差异化路线，不改；**仅借鉴** RW 的 PERMUTE/状态预算上限设计 |
| Flink SQL 方言 | 非目标 | 自研方言，非 Flink | 一致 |

## 引用出处

以下路径均在写入后以 `test -e` 逐条验证存在；行号引用来自本次实际读取的文件内容。

**RisingWave 源码**（`/Users/abc/sources/streaming-sql/risingwave/`）：

- `LICENSE`（Apache-2.0）、`README.md`（定位）
- `src/sqlparser/src/lib.rs:17`（自研 parser 说明）、`src/sqlparser/Cargo.toml`（`disable-publish`）
- `src/sqlparser/src/ast/ddl.rs:965`（`SourceWatermark`）、`src/sqlparser/src/ast/mod.rs:963`（`WindowFrameUnits::Session`）、`:3410`（`EmitMode`）、`:3927`（`AsOf`）
- `src/frontend/src/binder/relation/window_table_function.rs:28`（`WindowTableFunctionKind`）、`src/frontend/src/binder/relation/watermark.rs:47`（`is_watermark_func`）、`src/frontend/src/binder/relation/gap_fill.rs`、`src/frontend/src/binder/relation/match_recognize.rs`（含 `:1025` `MAX_PERMUTE_VARS`）、`src/frontend/src/binder/relation/table_function.rs`
- `src/frontend/src/binder/select.rs:38,44`（`distinct`/`having` 字段）、`src/frontend/src/binder/set_expr.rs:64`（`Union/Except/Intersect`）
- `src/frontend/src/handler/alter_watermark.rs:25`、`src/frontend/src/handler/create_source.rs:683`（`bind_source_watermark`）、`src/frontend/src/handler/create_table.rs:783`（append-only 水位约束）、`src/frontend/src/handler/create_mv.rs:142`（`emit_on_window_close`）
- `src/frontend/src/optimizer/heuristic_optimizer.rs:38`、`src/frontend/src/optimizer/logical_optimization.rs`（规则阶段）、`src/frontend/src/optimizer/rule/distinct_agg_rule.rs:29`、`src/frontend/src/optimizer/rule/stream/`（`SplitNowAndRule` 等）
- `src/frontend/src/optimizer/plan_node/logical_join.rs:1101,1113,1142,1650`（join 分派与限制）、`src/frontend/src/optimizer/plan_node/stream_watermark_filter.rs:32,40`、`src/frontend/src/optimizer/plan_node/generic/hop_window.rs:34`
- `src/frontend/src/optimizer/property/stream_kind.rs:22`、`src/frontend/src/optimizer/property/watermark_columns.rs:25`
- `src/frontend/src/stream_fragmenter/mod.rs:168`（`build_graph`）
- `src/stream/src/executor/watermark_filter.rs`（`:357` 过滤谓词、`:135/:329-348` idle 全局水位刷新）、`src/stream/src/executor/changelog.rs:235`（`ChangeLogMode`）、`src/stream/src/executor/aggregate/{hash_agg,distinct}.rs`、`src/stream/src/executor/match_recognize/nfa.rs`、`src/stream/src/executor/temporal_join.rs`、`src/stream/src/executor/backfill/snapshot_backfill/executor.rs`
- `src/expr/core/src/aggregate/mod.rs:136`（`build_retractable`）、`src/expr/core/src/window_function/kind.rs:27`（`WindowFuncKind`）
- `proto/expr.proto:499`（`AggCall.Kind` 聚合集）
- `src/connector/src/sink/mod.rs:262`（`sink_is_exactly_once`）、`src/meta/src/barrier/command.rs:819`（`BarrierKind`）
- `e2e_test/streaming/time_window_join.slt:23-27`（窗口 join 形态）、`e2e_test/streaming/watermark.slt`（水位 DDL 校验）、`e2e_test/nexmark/create_sources.slt:35`（`WATERMARK FOR ... AS ... - INTERVAL '4' SECOND`）、`e2e_test/batch/over_window/session/mod.slt.part:1`（SESSION 仅 batch）

**对照基线**：`docs-for-ai/03-modules/nop-stream-sql.md`（nop 已交付/不支持清单、D1/D6/D9 语义）。

**未在本仓库验证**：RisingWave 官方文档中的 session window / 维表 join 产品表述（仅以源码与 e2e 为准）；`CHANGELOG` 级别的版本演进时间线。

## Open Questions

- [ ] nop 若引入"水位表达式承载 allowedLateness"，与既有 `<window>/<strategy>.allowedLateness`（D9）如何归一，避免双声明源。
- [ ] `HOP`（步进滑窗）是否值得进 nop 的纳入面——它与既有 TUMBLE 展开机制同源，成本可能低于预期。
- [ ] `StreamKind` 式 changelog 标注是否要在 D1 裁定前先落地（仅标注不实现），以免后续升级触碰全部算子。
