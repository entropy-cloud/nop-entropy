# Arroyo streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: 最值得借鉴两点——① 表级 `WATERMARK FOR <col> [AS <expr>]` DDL 约束 + 默认 5 分钟 idle 的水印声明形态（可直接映射到 nop 的 `<sql>` 内嵌 schema/时间列约定）；② 用一条内部"retract 标记列"（`_updating_meta`）把无窗口持续聚合升级为 Debezium changelog 输出，并在 sink 处 fail-fast 校验 sink 是否支持 updating（对应 nop 从 D1 last-value-wins 走向可选 changelog 输出的最小改造路径）。

## Context

Arroyo 是用 Rust 编写的分布式流处理引擎，SQL 是其唯一一等入口（pipeline = 一段 DDL + 查询 SQL）。它被列入本调研，是因为它与 nop-stream-sql 同属"轻量 SQL→流管线"路线：不复刻 Flink 全栈、直接给出窗口/水位/changelog 语义的最小闭环。本分析依据本地源码 `/Users/abc/sources/streaming-sql/arroyo`（Rust workspace，`crates/` 下 24 个 crate）。

## 项目概览

- **定位**：stateful streaming SQL engine，"ask complex questions of high-volume real-time data with subsecond results"（`README.md` Features 段）。
- **语言**：Rust（workspace `Cargo.toml`），WebUI 为 TS/React（`webui/`）。
- **许可证**：Apache-2.0 + MIT 双许可（仓库根 `LICENSE-APACHE` / `LICENSE-MIT`，README badge 明示 dual-licensed）。
- **演进状态**：活跃维护，单二进制发行（`README.md` Installing 段：`brew install arroyosystems/tap/arroyo`）；DataFusion 依赖固定在自维护 fork 分支 `48.0.1/arroyo`（`Cargo.toml:121-123`，版本号见 `Cargo.toml:51`）。
- **SQL 能力入口**：
  - 解析/规划 crate：`crates/arroyo-planner/src/lib.rs`（`parse_sql` at :834，`parse_and_get_arrow_program` at :838）。
  - API 层调用点：`crates/arroyo-api/src/pipelines.rs:185`（`arroyo_planner::parse_and_get_program`）。
  - 运行期执行：`crates/arroyo-worker/src/arrow/`（各算子实现）。

## 十维度分析

### 1. SQL 前端：复用 sqlparser-rs + DataFusion，自研流式改写层

- Parser 不是自研：`Parser::parse_sql(&ArroyoDialect {}, sql)`（`crates/arroyo-planner/src/lib.rs:834-836`），`ArroyoDialect` 来自 sqlparser-rs（`use sqlparser::dialect::ArroyoDialect` at `crates/arroyo-planner/src/lib.rs:84`）。
- Planner/优化器完全复用 DataFusion 48.0.1 fork（`Cargo.toml:51` 版本、`Cargo.toml:121-123` git 分支 `48.0.1/arroyo`）；`SessionStateBuilder::new()...with_default_features()` + 关闭并发 repartition（`crates/arroyo-planner/src/lib.rs:844-860`）。
- 函数注册：DataFusion 默认标量/聚合/窗口函数 + JSON 函数 + 额外 `var_samp`（`crates/arroyo-planner/src/lib.rs:133-150`），即聚合集基本等同 DataFusion 全集。
- 流式语义不是靠改 DataFusion，而是在其 LogicalPlan 之上插入**自定义 Extension 节点**：`crates/arroyo-planner/src/extension/` 下 `aggregate/join/lookup/window_fn/watermark_node/sink/debezium/projection/key_calculation/remote_table/updating_aggregate` 等节点类型，统一实现 `ArroyoExtension` trait（`crates/arroyo-planner/src/extension/mod.rs`）。
- 错误面向用户：`PlannerError` 携带 `SqlDiagnostic` 行列 span（`crates/arroyo-planner/src/lib.rs:570-610`）。

### 2. Table & Changelog 模型

- DDL 形态是标准 `CREATE TABLE ... WITH (connector=..., type='source'|'sink'|'lookup', format=...)`：语句入口 `Table::try_from_statement`（`crates/arroyo-planner/src/tables.rs:777-790`，匹配 `Statement::CreateTable`）；`connector` 缺省为 `memory`（`crates/arroyo-planner/src/tables.rs:814-815`）。
- 表类型 5 个变体：`LookupTable / ConnectorTable / MemoryTable / TableFromQuery / PreviewSink`（`crates/arroyo-planner/src/tables.rs:576-588`）；插入侧 match 分支与 `CREATE TABLE AS` 重复插入拒绝见 `crates/arroyo-planner/src/lib.rs:944-956`。
- 主键声明：列级 `PRIMARY KEY` 唯一约束解析（`crates/arroyo-planner/src/tables.rs:798-812`）。
- **changelog 模型**：计划里带 `_updating_meta` 隐式列即表示"这是可撤回（retract）流"（常量 `UPDATING_META_FIELD` 定义于 `crates/arroyo-rpc/src/lib.rs:356`）。sink 侧规则：
  - sink 配置为 updating（`format = 'debezium_json'`）→ 自动插入 `ToDebeziumExtension` 展开成 `before/after/op` 结构（`crates/arroyo-planner/src/extension/sink.rs:47-64`；结构定义 `extension/debezium.rs:40-60`）。
  - 输入带 retract 而 sink 不支持 → **编译期报错**并提示改用 `debezium_json`（`crates/arroyo-planner/src/extension/sink.rs:60-62`）。
- 无 `CREATE MATERIALIZED VIEW`（全仓 `materialized` 仅出现在注释 `plan/mod.rs:364`）；需要持久快照时用 `CREATE TABLE ... AS` 内存表。

### 3. 时间语义：DDL 约束声明 watermark + 默认 idle

- **声明方式**：列约束 `WATERMARK FOR <col> [AS <expr>]`（测试样例 `crates/arroyo-planner/src/test/queries/watermarks.sql`）。约束解析：每表最多一个（`crates/arroyo-planner/src/tables.rs:869-879`，"Only one WATERMARK FOR constraint is allowed per table"）。
- 类型校验严格：列必须存在（`crates/arroyo-planner/src/tables.rs:381`）、类型必须 `TIMESTAMP`（`crates/arroyo-planner/src/tables.rs:388`）、表达式结果类型必须 `TIMESTAMP` 且 `NOT NULL`（`crates/arroyo-planner/src/tables.rs:404-409`）。
- 表达式形式的 watermark 会物化成虚拟列 `__watermark`（`crates/arroyo-planner/src/tables.rs:413-416`）；无表达式则直接用该时间列（`crates/arroyo-planner/src/tables.rs:418`）。
- 兼容旧形态：`WITH (watermark_field=...)` 与 `WITH (event_time_field=...)` 均已标 `deprecated` 并打 warn（`crates/arroyo-planner/src/tables.rs:361-367`）。
- **idle**：默认 `DEFAULT_IDLE_TIME = 5min`（`crates/arroyo-planner/src/lib.rs:93`），落到表配置（`crates/arroyo-planner/src/tables.rs:215`、`crates/arroyo-planner/src/tables.rs:424`），运行期在 `WatermarkGenerator` 中判定分区空闲并跳过水位推进（`crates/arroyo-worker/src/arrow/watermark_generator.rs:217-226`，`idle` 标志回退见 :179-194）。
- 时间列的内部载体是每行附加的 `__arroyo_internal_ts`（`crates/arroyo-rpc/src/lib.rs:341`，legacy 名 `_timestamp` at :345）。
- late data：watermark 之前的记录由算子丢弃（`DropLateItems` 语义在 Arroyo 中体现为 `WatermarkGenerator` 只发单调水位；未见单独 late-buffer 算子 —— **未在本仓库验证**）。

### 4. 窗口与聚合

- **窗口全集**：`Tumbling{width}` / `Sliding{width, slide}` / `Instant` / `Session{gap}` 四种（`crates/arroyo-datastream/src/lib.rs:55-60`）。
- **声明形态**：窗口是 SELECT/GROUP BY 里的**伪表函数**，不是 DDL：
  - `tumble(INTERVAL ...)`、`hop(slide, width)`、`session(gap)`（`find_window`，`crates/arroyo-planner/src/lib.rs:644-686`）。
  - 约束：`hop` 的 width 必须是 slide 的整数倍（`crates/arroyo-planner/src/lib.rs:655-658`）；`hop` 在 slide==width 时退化为 tumble（`crates/arroyo-planner/src/lib.rs:660-663`）；interval 只接受日及以下单位，月份报 `not_impl`（`get_duration`，`crates/arroyo-planner/src/lib.rs:622-643`）。
- 用法示例：`SELECT ... count(*) ... , tumble(INTERVAL '1' second) as window FROM nexmark GROUP BY 1,2`（`crates/arroyo-planner/src/test/queries/basic_tumble_aggregate.sql`）。
- **聚合**：DataFusion 默认聚合全集（`SUM/COUNT/AVG/MIN/MAX/...` + `var_samp`，注册见 `crates/arroyo-planner/src/lib.rs:142-147`）；`COUNT(DISTINCT ...)` 有测试覆盖（`crates/arroyo-planner/src/test/queries/windowed_inner_join.sql` 用 `COUNT(distinct driver_id)`）。
- 限制：一个 GROUP BY 只能出现一个窗口表达式（`plan/aggregate.rs:144-147`）；GROUP BY 窗口必须与输入窗口一致（`plan/aggregate.rs:175-186`）；session 窗口不能嵌套重入（`plan/aggregate.rs:190-196`）。
- **分析窗口（OVER）**：`WindowFunctionRewriter` 要求"输入已开窗"且 `partition_by` 里恰好含一个窗口列、不支持 session（`plan/window_fn.rs:54-57`、`:67`、`:96`）；典型用法 TopN（`crates/arroyo-planner/src/test/queries/window_function.sql`：`ROW_NUMBER() OVER (PARTITION BY window ORDER BY count DESC)` 外层过滤 `row_num <= 5`）。frame 边界的细粒度限制（RANGE/ROWS 偏移量）**未在本仓库逐条验证**。
- 无 cumulate 窗口（`grep cumulate` 无窗口命中）。

### 5. Join

- **双流等值 join 必须"同窗口"**：左右两侧窗口类型必须相同，否则 `NotImplemented`（`crates/arroyo-planner/src/plan/join.rs:31-65`）；session 窗口禁止参与 join（`crates/arroyo-planner/src/plan/join.rs:57-63`）。
- **无窗口时只支持 INNER**：非 inner + 无窗口 → `can't handle non-inner joins without windows`（`crates/arroyo-planner/src/plan/join.rs:36-41`）；混合"有/无窗口"两侧也直接拒绝（`:43-51`）。
- **条件必须是 `ON` 等值**：`can't handle join constraint other than ON`（`crates/arroyo-planner/src/plan/join.rs:338`）；updating 流参与 join 要求 equi 条件（`:343`）；`_updating_meta` 列出现在任一侧 → 拒绝（`crates/arroyo-planner/src/plan/join.rs:73-85`）。
- **lookup join（维表）**：`CREATE TEMPORARY TABLE ... WITH(type='lookup')`（`crates/arroyo-planner/src/tables.rs:289-297`）；执行节点 `LookupSource`/`LookupJoin`（`extension/lookup.rs:19`、`:65`）；校验：lookup 表必须直接出现在 join 右侧且为 INNER/LEFT（`crates/arroyo-planner/src/plan/join.rs:248`）、右条件必须是 PRIMARY KEY 列（`crates/arroyo-planner/src/plan/join.rs:282`）、非列引用拒绝（`:288`）；支持缓存参数 `'lookup.cache.max_bytes'` / `'lookup.cache.ttl'`（`crates/arroyo-planner/src/test/queries/lookup_join.sql`）。
- **子查询**：标量子查询可用；还有 async UDF 子查询形态（`crates/arroyo-planner/src/test/queries/async_udf_subquery.sql`）。相关子查询/IN 子查询的流式语义**未在本仓库验证**。
- 无 interval/temporal/asof join 专用算子（`OperatorName` 枚举仅 `Join/InstantJoin/LookupJoin`，`crates/arroyo-datastream/src/logical.rs:28-44`）；窗口 join 实际是"按窗口分组后 join"（测试 `crates/arroyo-planner/src/test/queries/windowed_inner_join.sql`）。

### 6. CEP / 模式匹配：无

全仓 `grep MATCH_RECOGNIZE / match_recognize` 无命中，`OperatorName` 也无 pattern 相关算子。Arroyo 不提供 CEP。

### 7. 状态与容错

- **状态后端**：不是 RocksDB，是 **Parquet 对象存储** —— `pub type StateBackend = parquet::ParquetBackend;`（`crates/arroyo-state/src/lib.rs:54`）；两类状态表 `GlobalKeyedTable` / `ExpiringTimeKeyTable`（`crates/arroyo-state/src/tables/`，实现见 `crates/arroyo-state/src/tables/global_keyed_map.rs`、`crates/arroyo-state/src/tables/expiring_time_key_map.rs`）。全仓 grep `rocksdb` 无命中。
- **TTL / 有界状态**：更新聚合与窗口状态按 TTL 过期；SQL 级可调 `SET updating_ttl = ...`（只允许该变量，`crates/arroyo-planner/src/lib.rs:807-831`），默认 24h（`PlanningOptions::default`，`crates/arroyo-planner/src/lib.rs:96-104`）。
- **checkpoint / exactly-once**：barrier 式两阶段提交。`CheckpointMessage`（epoch + watermark + then_stop，`crates/arroyo-state/src/lib.rs:40-45`）→ `CheckpointState` 收集各算子状态，随后进入 `CommittingState` 提交（`crates/arroyo-worker/src/job_controller/checkpoint_state.rs:36-44`、`into_commit` at :475；`crates/arroyo-worker/src/job_controller/committing_state.rs`）。README Features 明示 "State checkpointing for fault-tolerance"（`README.md:48`）。
- **输出语义**：窗口聚合是**终值追加**（窗口触发一次）；无窗口持续聚合走 `UpdatingAggregateExtension`，产出带 `is_retract` 标志的行（`extension/updating_aggregate.rs:39`、`:131`），最终由 sink 转成 Debezium `before/after/op`（见维度 2）。即：**Arroyo 有显式的 retract/upsert changelog 输出，且与 sink 能力绑定校验**。

### 8. 声明形态与可嵌入性

- 一个 pipeline = 一段 SQL 文本（多个 `CREATE TABLE` + 若干 `INSERT INTO <sink> SELECT ...`，或裸 `SELECT ...` 落 preview sink：`crates/arroyo-planner/src/lib.rs:946-964`）。
- 编译产物是 `CompiledSql { program: LogicalProgram, connection_ids }`（`crates/arroyo-planner/src/lib.rs:100-103`），由 API 层持久化并调度（`crates/arroyo-api/src/pipelines.rs:185`、`register_schemas` at :262、算子数上限校验 at :329）。
- **可嵌入性**：`arroyo-planner` 是独立 crate，`parse_and_get_program` / `parse_and_get_arrow_program` 均为 `pub`（`crates/arroyo-planner/src/lib.rs:572`、`:838`），理论上可作为库嵌入；但连接器（`arroyo-connectors`）、状态（`arroyo-state`）、执行（`arroyo-worker`）默认按服务形态组合，嵌入需自备整套 runtime。
- UDF：Rust UDF 编译成 dylib（`arroyo-udf/arroyo-udf-host`、`arroyo-compiler-service`），Python UDF（`arroyo-udf/arroyo-udf-python`），二者进入 `ProgramConfig{udf_dylibs, python_udfs}`（`crates/arroyo-planner/src/lib.rs:980-983`）。

### 9. 架构要点（3 个值得记的设计）

1. **"DataFusion 计划 + 流式 Extension 节点"的双层结构**：SQL → DataFusion LogicalPlan → `rewrite_plan`（`crates/arroyo-planner/src/lib.rs:700` 起）注入 `AggregateExtension/JoinExtension/LookupJoin/WindowFunctionExtension/WatermarkNode...` → `PlanToGraphVisitor` 落成 `LogicalProgram`（petgraph 图，`crates/arroyo-planner/src/lib.rs:969-984`）→ `ChainingOptimizer` 合并相邻算子（`crates/arroyo-planner/src/lib.rs:986-988`）。新增流式算子 = 新增一个 Extension 节点 + 一个 `OperatorName` 枚举值（`crates/arroyo-datastream/src/logical.rs:28-44`），proto config 序列化在 `crates/arroyo-planner/src/physical.rs` 的 codec 中。
2. **窗口被"降维"成隐式列 + 键计算节点**：`TUMBLE(...)` 不是真实表，而是产出 `window{start,end}` 结构列（`schemas::window_arrow_struct`），配合 `_key_N` 投影节点（`crates/arroyo-planner/src/extension/key_calculation.rs`、`crates/arroyo-planner/src/plan/join.rs:87-127` 的 `create_join_key_plan`）把 join/聚合统一成按键分发（`LogicalEdgeType::Forward/Shuffle/LeftJoin/RightJoin`，`crates/arroyo-datastream/src/logical.rs:47-52`）。
3. **changelog 是"sink 能力协商"而不是全局开关**：retract 标记列随计划流动，只有 sink 声明 `debezium_json` 时才展开成 `before/after/op`（`crates/arroyo-planner/src/extension/sink.rs:47-64`），否则编译期 fail-fast。这让同一查询既可接 append sink，也可接 CDC sink，代价只是 DDL 一行 `format`。

### 10. 与 nop-stream-sql 的对照与可借鉴点

- **水位声明**：Arroyo 的 `WATERMARK FOR <col> [AS <expr>]` 是**表级约束**，nop 现在只有时间列（`TUMBLE(t, ...)` 的 `t` + bigint epoch-millis）。可借鉴：把"时间列 + 允许迟到 + 是否 idle"收敛为 schema 上的一个显式声明节点，类型校验（必须 NOT NULL 时间类型、每表唯一）可原样搬。
- **changelog 输出**：nop 当前 D1 = last-value-wins 终值语义、无 RowKind。Arroyo 证明了**不需要 RowKind 全套**：只要在计划里带一个"可撤回标记列"，并在 sink 出口按 sink 能力 fail-fast / 转换格式，就能提供 opt-in 的 retract 输出。
- **窗口表达形态**：Arroyo 的 `tumble()/hop()/session()` 作为 SELECT 项 + `GROUP BY window` 与 nop 的 `TUMBLE(t, INTERVAL)` 伪表函数几乎同构，可对照其"一个 GROUP BY 只允许一个窗口表达式"与"窗口函数必须 partition by 窗口"两条校验，作为 nop 未来放开 `OVER` 约束时的护栏设计参考。
- **join 约束的报错质量**：`plan/join.rs` 把"无窗口非 INNER / 混合窗口 / session / 非 ON 条件 / updating 流"逐条变成带原因的 `NotImplemented`，比单一 "not supported" 更利于 nop 的 fail-fast 诊断。
- **反向借鉴（不要做）**：Arroyo 没有 ORDER BY/LIMIT、递归 CTE、全局去重（`plan/mod.rs:353`、`:394`、`:418`），与 nop 的 fail-fast 面高度重合，说明"流式 SQL 收窄子集"是行业普遍取舍，不必为补齐这些而引入代价优化器。

## 对照 nop-stream-sql 已交付子集

| nop 基线能力 | Arroyo 对应 | 差异 / 可借鉴 |
| --- | --- | --- |
| 入口：`.stream.xml` 顶层 `<sql sinkBean>`，EQL 语法与 ORM 共用 parser | 单段 SQL 文本（`parse_sql` → `ArroyoDialect`），DDL+DML 混排 | nop 的 XML/DSL 入口更利于与既有 bean 体系集成；可借鉴 Arroyo 的**行列表达式诊断**（`PlannerError` span），补到 EQL 编译报错 |
| 内嵌 schema，9 受管类型闭集 | `CREATE TABLE` 列声明（DataFusion/Arrow 类型体系，含 TIMESTAMP/DECIMAL） | Arroyo 类型面宽得多；nop 的 9 类闭集 + fail-fast 更简单，可保留，但**时间列必须是独立显式声明**（见下） |
| FROM 表名 = 数据源 bean 名 | `connector` WITH 选项 + connection profile | nop 更简；Arroyo 的 `type='source'/'sink'/'lookup'` 三态与 nop 的 sinkBean/lookup 语义可对齐 |
| SELECT / WHERE / 五聚合 / GROUP BY | 同，且聚合集 = DataFusion 全集（`crates/arroyo-planner/src/lib.rs:142-147`） | nop 五聚合刻意收窄；可参考 Arroyo 将"聚合函数"做成注册表（nop 已有三注册表之一 aggregators），扩函数即扩能力 |
| `TUMBLE(t, INTERVAL)` 伪表函数（SECOND..WEEK，bigint epoch-millis） | `tumble/hop/session` 伪表函数（`crates/arroyo-planner/src/lib.rs:644-686`），窗口列是 struct | **可直接借鉴**：Arroyo 支持 `hop(slide,width)` 与 `session(gap)`，nop 目前只有 TUMBLE；且 Arroyo 用"窗口类型枚举 + 一个窗口表达式"约束（`plan/aggregate.rs:144-147`）值得抄 |
| 双流等值 join（INNER/LEFT/RIGHT/FULL，window join 补齐于 watermark） | 双流等值 join，**要求同窗口**，非窗口仅 INNER（`crates/arroyo-planner/src/plan/join.rs:31-65`）；**不支持 FULL**（仅 DataFusion `JoinType` 通过，未见 FULL 专门路径 —— 未验证） | nop 的 FULL window join 是 fail-fast 项，Arroyo 同样不支持非 INNER 的无窗口 join —— 佐证该 fail-fast 合理 |
| 静态维表 lookup join（ITableLookup→IJdbcTemplate/IBatchLoader） | lookup join：右侧必须 PRIMARY KEY、仅 INNER/LEFT、可带 TTL 缓存（`crates/arroyo-planner/src/plan/join.rs:248`、`crates/arroyo-planner/src/test/queries/lookup.sql`） | **可借鉴**：`lookup.cache.ttl/max_bytes` 的声明位放 WITH 选项，nop 可在 `.stream.xml` 的 lookup 配置上加同名语义 |
| UNION ALL（bag 语义） | `LogicalPlan::Union` 支持且需物化输入（`plan/mod.rs:354-374`） | 对齐；nop 的 UNION DISTINCT fail-fast 可参照 Arroyo（其 Distinct 节点被放行 `plan/mod.rs:406`，但流式去重代价高——nop 拒绝更保守） |
| 无窗口持续 GROUP BY | `UpdatingAggregateExtension` + retract 标记（`crates/arroyo-planner/src/extension/updating_aggregate.rs`） | **差异最大**：nop 是 last-value-wins，Arroyo 是 retract/changelog。这是 nop 最该评估的一项（见 Conclusion ②） |
| 分析窗口 OVER（ROW_NUMBER, FRAME_SLIDING_AGG） | `OVER` 要求输入已开窗 + 恰好一个窗口 partition（`plan/window_fn.rs:54-96`） | 与 nop 的 `FRAME_SLIDING_AGG` 思路一致；Arroyo 的"必须已开窗"护栏可直接抄 |
| 不支持：全局 ORDER BY/LIMIT、非等值 join、DISTINCT 聚合、HAVING/CTE、全局聚合、FULL window join、UNION DISTINCT、Flink 方言 | 全局 ORDER BY/LIMIT 同样拒绝（`plan/mod.rs:353`、`:394`）；递归 CTE 拒绝（`:418`）；非等值 join 拒绝（`crates/arroyo-planner/src/plan/join.rs:338`） | **结论**：nop 的 fail-fast 面与 Arroyo 基本一致，可作为"业界基线"写进 nop 的能力白皮书；差异仅在 nop 额外拒绝 HAVING/CTE/DISTINCT 聚合 |
| 语义：D1 last-value-wins、D6 事件时间、D9 allowedLateness 已放行 | D6 等价（`WATERMARK FOR` + `WatermarkGenerator`）；D9 等价（watermark 表达式偏移）；**D1 不同**（Arroyo 有 retract）；D10/D11：Arroyo 无 accumulationMode/trigger 概念 | nop 的 D9 可对标 `WATERMARK FOR ... AS <expr>` 的"事件时间 - 常量"；D10/D11 在 Arroyo 处根本不存在（窗口固定触发），可继续 fail-fast |
| 架构：EQL AST→StreamSqlCompiler→流模型 XML；三注册表；无代价优化器 | DataFusion 全量优化器 + Extension 节点 + ChainingOptimizer | Arroyo 证明"复用成熟 optimizer + 插流式节点"可行；nop 保持"无代价优化器"仍更契合 XML/DSL 双目标翻译 |

## 引用出处

以下路径若无前缀均相对本地源码根 `/Users/abc/sources/streaming-sql/arroyo`（已在写毕后逐条验证存在；行号来自实际读取内容）。

- `Cargo.toml`（仓库根）:51（datafusion 48.0.1）、:121-123（fork 分支 48.0.1/arroyo）
- `README.md` :48（state checkpointing）
- `LICENSE-APACHE`、`LICENSE-MIT`
- `crates/arroyo-planner/src/lib.rs` :84, :93, :96-100, :107-111, :133-150, :563-566, :568, :577-610, :622-686, :694, :785-831, :834-860, :944-993
- `crates/arroyo-planner/src/tables.rs` :77, :215, :241, :289-297, :576-588, :777-790, :798-812, :814-815, :858-879, :1033-1050
- `crates/arroyo-planner/src/plan/join.rs` :31-65, :73-85, :87-127, :202-301, :338-343
- `crates/arroyo-planner/src/plan/mod.rs` :353, :354-374, :394, :406, :418
- `crates/arroyo-planner/src/plan/aggregate.rs` :144-196
- `crates/arroyo-planner/src/plan/window_fn.rs` :54-57, :67, :96
- `crates/arroyo-planner/src/extension/sink.rs` :47-64
- `crates/arroyo-planner/src/extension/debezium.rs` :59-69
- `crates/arroyo-planner/src/extension/updating_aggregate.rs` :39, :131
- `crates/arroyo-planner/src/extension/lookup.rs` :19, :66
- `crates/arroyo-planner/src/extension/aggregate.rs` :41
- `crates/arroyo-planner/src/test/queries/watermarks.sql`
- `crates/arroyo-planner/src/test/queries/basic_tumble_aggregate.sql`
- `crates/arroyo-planner/src/test/queries/windowed_inner_join.sql`
- `crates/arroyo-planner/src/test/queries/window_function.sql`
- `crates/arroyo-planner/src/test/queries/lookup_join.sql`
- `crates/arroyo-planner/src/test/queries/async_udf_subquery.sql`
- `crates/arroyo-datastream/src/lib.rs` :55-60
- `crates/arroyo-datastream/src/logical.rs` :28-52
- `crates/arroyo-state/src/lib.rs` :40-54
- `crates/arroyo-state/src/tables/expiring_time_key_map.rs` :1-35
- `crates/arroyo-worker/src/arrow/watermark_generator.rs` :37-57, :179-226
- `crates/arroyo-worker/src/job_controller/checkpoint_state.rs` :36-44, :475-479
- `crates/arroyo-worker/src/job_controller/committing_state.rs`
- `crates/arroyo-rpc/src/lib.rs` :333, :341, :345, :356
- `crates/arroyo-api/src/pipelines.rs` :61, :185, :262, :329
- `crates/arroyo-api/src/udfs.rs` :50, :192-247
- 未验证项（已标注）：late data 丢弃的独立算子、`OVER` frame 边界细则、相关子查询流式语义、FULL join 路径。
