# nop-stream-sql gap 分析与分级功能建议——streaming SQL 开源调研综合

> Status: resolved
> Date: 2026-10-07
> Scope: `nop-stream` / `nop-stream-sql` 功能完善建议；依据 `streaming-sql-survey/` 14 个开源项目调研
> Conclusion: 14 个开源项目的可借鉴点收敛为 **Tier A 不触门 15 项（其中 6 项已有 roadmap 归属）、Tier B 触门待 owner 裁定 7 项、Tier C 已裁排除 6 项**；最优先的三件事是 ①watermark/lateness 声明进 `<sql>` 面、②标量子集扩充（CASE/CAST，借 SeaTunnel 双引擎路径）、③HOP/SESSION 伪表函数补全（`window_start/window_end` 命名与 Feldera/Flink 对齐）。

## Context

nop-stream-sql 的窄范围 SQL 接口已于 2026-10-03 收口（`ai-dev/backlog/nop-stream-sql-roadmap.md` 31/31 WI done，D1-D15 全部落档）。本次调研不再论证"要不要 SQL"，而是对照 14 个 streaming SQL 开源项目（清单见 [README](./README.md)），回答：**已交付子集还有哪些功能可实现/完善**。

- 能力基线：`../../design/nop-stream/sql-subset-and-semantics.md` §4a 不支持清单 / §4b 纳入面总表；`../../../docs-for-ai/03-modules/nop-stream-sql.md` 用户口径。
- **分级标准** = sql roadmap Purpose 门控判据：一个建议受门控（Tier B），当且仅当它 (a) 在 `00-vision.md` §四 Non-Goals 表内新增/扩展能力；(b) 触发 §六 决策点（StreamModel 结构变更）；(c) 改写 §七 取舍；(d) 声称改变 §八 不变量；(e) 改变 §五 收敛路径阶段归属。
- **去重基准**（建议不得与在册工作项重复）：sql roadmap Follow-up FU-1..FU-10、`../../backlog/nop-stream-multi-engine-comparison-roadmap.md` WI0-WI19、`../../backlog/eql-cross-db-compat-roadmap.md` WI0-WI12、`../../backlog/nop-stream-production-roadmap.md`、`../../backlog/nop-stream-cdc-roadmap.md`、productization roadmap items、`../2026-09/2026-09-30-doris-nereids-optimizer-extraction-feasibility.md`（优化器不可移植裁定）。

## 一、能力对照矩阵（摘要）

完整依据在各项目分析（相对链接可点）；"nop 现状"列以 §4a/§4b 与用户文档为准。

| 能力 | 代表项目（出处） | nop-stream-sql 现状 | 结论 |
|---|---|---|---|
| SQL 前端 | Flink=FMPP/JavaCC 自造（[flink-sql](./2026-10-07-flink-sql-analysis.md)）；ksqlDB=ANTLR（[ksql](./2026-10-07-ksql-analysis.md)）；Arroyo=sqlparser-rs+DataFusion（[arroyo](./2026-10-07-arroyo-analysis.md)）；Hazelcast/Feldera=Calcite（[hazelcast](./2026-10-07-hazelcast-jet-sql-analysis.md)/[feldera](./2026-10-07-feldera-analysis.md)）；SeaTunnel=JSqlParser+Calcite 双引擎 SPI（[seatunnel](./2026-10-07-seatunnel-sql-analysis.md)） | EQL 与 ORM 共用单一 parser（D13 口径优势） | 保持；扩充表达式不必换 parser |
| changelog/retract | Flink ChangelogMode 推导程序（[flink-sql](./2026-10-07-flink-sql-analysis.md)）；ksqlDB 持续聚合默认 changelog + `EMIT FINAL`（[ksql](./2026-10-07-ksql-analysis.md)）；RisingWave StreamKind 规划期属性（[risingwave](./2026-10-07-risingwave-analysis.md)）；Feldera 输出档位 + `emit_final`（[feldera](./2026-10-07-feldera-analysis.md)）；Paimon changelog-producer 四档表属性（[paimon](./2026-10-07-paimon-analysis.md)）；Esper ISTREAM/RSTREAM（[esper](./2026-10-07-esper-analysis.md)）；Arroyo 内部 retract 标记列（[arroyo](./2026-10-07-arroyo-analysis.md)） | D1=(a) last-value-wins，无 retract/upsert 输出 | **Tier B-1**（改判须 owner 修订 D1）；WI7/WI17c 是既有路径 |
| watermark/lateness 声明 | Arroyo/RisingWave `WATERMARK FOR col AS col - INTERVAL`（[arroyo](./2026-10-07-arroyo-analysis.md)/[risingwave](./2026-10-07-risingwave-analysis.md)）；Hazelcast `IMPOSE_ORDER` lag 参数（[hazelcast](./2026-10-07-hazelcast-jet-sql-analysis.md)）；Feldera 列级 `LATENESS` 注解（[feldera](./2026-10-07-feldera-analysis.md)）；Paimon 水位序列化为表属性（[paimon](./2026-10-07-paimon-analysis.md)）；ksqlDB `GRACE PERIOD` 写窗口声明旁（[ksql](./2026-10-07-ksql-analysis.md)） | 时间列 bigint epoch-millis + 产物自动补 assigner；allowedLateness 只在 `<window>/<strategy>` 属性 | **Tier A-7**：lateness/水位策略上浮到 `<sql>`/schema 声明 |
| 窗口类型 | Flink TVF 四窗口 TUMBLE/HOP/CUMULATE/SESSION（[flink-sql](./2026-10-07-flink-sql-analysis.md)）；Feldera HOP/SESSION + 严格常量校验（[feldera](./2026-10-07-feldera-analysis.md)）；ksqlDB 三窗口 + named window（[ksql](./2026-10-07-ksql-analysis.md)） | 仅 `TUMBLE(t, INTERVAL)`（WI17）；core assigner 白名单 4 id | **Tier A-3**：HOP/SESSION 伪表函数补全 |
| emit/trigger | Kafka Streams `EmitStrategy{ON_WINDOW_UPDATE, ON_WINDOW_CLOSE}` 两档（[kafka-streams](./2026-10-07-kafka-streams-analysis.md)）；ksqlDB `EMIT CHANGES\|FINAL`（[ksql](./2026-10-07-ksql-analysis.md)）；Esper `OUTPUT EVERY/WHEN`（[esper](./2026-10-07-esper-analysis.md)） | D11 triggerId fail-fast | **Tier B-2**：两档 emit 是 D11 的降维解锁方案，放行须先修订裁定 |
| Join | Flink interval/temporal/lookup（[flink-sql](./2026-10-07-flink-sql-analysis.md)）；ksqlDB 非对称 `WITHIN (before, after)` + FK join 投影校验 + LookupConstraint 三档（[ksql](./2026-10-07-ksql-analysis.md)）；Kafka Streams join store retention=size+grace（[kafka-streams](./2026-10-07-kafka-streams-analysis.md)）；Feldera LEFT ASOF + `MATCH_CONDITION` 仅 `>=`（[feldera](./2026-10-07-feldera-analysis.md)） | 等值 join（hash/window merge）+ 静态维表 `ITableLookup` | 非对称窗口/构建期校验=**Tier A**；ASOF=**Tier B-7**（贴"非等值 join"排除边） |
| 标量表达式 | ksqlDB CASE/CAST/STRUCT；Flink 全集；SeaTunnel 双引擎（解释器→编译型Bindable）（[seatunnel](./2026-10-07-seatunnel-sql-analysis.md)） | 4a#7 子集外 fail-fast（无 CASE/CAST） | **Tier A-2**：借 SeaTunnel SQLEngine 路径分两步扩 |
| CEP SQL | Calcite `Match` 纯数据节点四层解耦（[calcite](./2026-10-07-calcite-analysis.md)）；Flink MATCH_RECOGNIZE（[flink-sql](./2026-10-07-flink-sql-analysis.md)） | 4a#9 排除；CEP 走 nop-stream-cep Pattern API | **Tier B-6**（远期）；Calcite 形态是提案素材 |
| 可查询性 | ksqlDB pull query（[ksql](./2026-10-07-ksql-analysis.md)） | 无 | 已有归属：multi-engine WI14 可查询状态轻量版 |
| 报错风格 | Feldera `RejectUnsupportedPlans` 带文档锚点（[feldera](./2026-10-07-feldera-analysis.md)）；Hazelcast 物理规则拦截"拒绝原因即文档句"（[hazelcast](./2026-10-07-hazelcast-jet-sql-analysis.md)）；ksqlDB validator 规则表（[ksql](./2026-10-07-ksql-analysis.md)） | `nop.err.*` 钉码 + §4d 错误码表（已是强项） | **Tier A-9**：结构化规则表 + 文档锚点锦上添花 |

## 二、Tier A 不触门——可按 plan-first 直接立项

门控分析统一口径：以下各项均为纳入面/声明面**加法**——不新增 Transformation 类型、不改 `StreamComponents` 注册表条目结构（仅字段级扩展）、不改 §四 Non-Goals、不改 §七取舍与 §八不变量，属 `sql-subset-and-semantics.md` §4b 同类的纳入面扩充。凡修订 §4a/§4b 清单或钉码测试的，随承载 plan 一并修订并保持 WI16 文档口径一致。

> 编号说明：本表「建议编号 WI25-WI36」为调研时的临时编号；正式立项 `../../backlog/nop-stream-sql-enhancement-roadmap.md` 时已重排，跨文档对照以该 roadmap Sources 的编号映射为准（A7→WI25、A3→WI26、A12→WI27、A5→WI28、A2→WI29、A11→WI30、A10→WI31、A4→WI32、A9→WI33、A8→WI34、A13→WI35、A14→WI36、A6→WI39、B3 前置 FU-8→WI38、B2→WI0a+WI37、B4→WI0b+WI40）。

| # | 建议 | 参考出处 | 去重结论 / 建议归属 | 优先级 |
|---|---|---|---|---|
| A1 | **DISTINCT 聚合 + HAVING**：`COUNT_DISTINCT` 是最省增量扩展（去重集带上限）；HAVING 可先编译成 post-filter 而非新算子 | [ksql](./2026-10-07-ksql-analysis.md) §对照3 | **已有归属：multi-engine WI17a**（其条款显式 supersede §4a#6 钉码与 WI16 清单） | P1（随 WI17） |
| A2 | **标量子集扩充 CASE/CAST（含 DECIMAL 可选重议）**：两步走——先在现有 EQL 求值器内放行 EQL 已支持的表达式；再评估 SeaTunnel 式 `SQLEngine` SPI 第二引擎（Calcite 编译型 Bindable）作为高保真档 | [seatunnel](./2026-10-07-seatunnel-sql-analysis.md) Conclusion/§9；[ksql](./2026-10-07-ksql-analysis.md) §对照2 | 新建议（建议编号 **WI25**）；CAST 类型系统与 `../../backlog/eql-cross-db-compat-roadmap.md` WI9a 协调（ORM 侧先落，流侧消费） | P1 |
| A3 | **HOP/SESSION 伪表函数补全**：D4 已裁伪表函数形态，grammar 仅落 TUMBLE；补 HOP（slide/size）与 SESSION（gap+key），校验照 Feldera"short interval/常量/正值"三原则；产物窗口列命名对齐 `window_start/window_end`（Feldera/Flink 同名） | [feldera](./2026-10-07-feldera-analysis.md) §对照；[flink-sql](./2026-10-07-flink-sql-analysis.md) §4 | 新建议（**WI26**）；与 FU-2（token 归位）无冲突 | P1 |
| A4 | **CTE（WITH）展开**：EQL parser 已产出 `SqlSelectWithCte`（`StreamSqlCompiler.java:180` 现 fail-fast），实现内联展开即可，无需动 grammar | [flink-sql](./2026-10-07-flink-sql-analysis.md)；[calcite](./2026-10-07-calcite-analysis.md) | 新建议（**WI27**） | P2 |
| A5 | **全局聚合放行**（无 GROUP BY 的 sum/count 等）：单并行度 reduce 或全局 key 实现；移出 §4b"全局聚合 fail-fast" | [flink-sql](./2026-10-07-flink-sql-analysis.md)；[ksql](./2026-10-07-ksql-analysis.md) | 新建议（**WI28**）；修订 §4a/§4b 对应行 | P2 |
| A6 | **维表 lookup 构建期增强**：①FK 投影校验（输出必须含 lookup key，防丢 join key）；②keyMapper 式 key 推导（用 value 表达式推导 lookup key，不止字段直连）；③（可选）join key 约束三档建模 | [ksql](./2026-10-07-ksql-analysis.md) §对照6（`JoinNode` 外键校验、LookupConstraint）；[kafka-streams](./2026-10-07-kafka-streams-analysis.md) §对照7 | 挂 **multi-engine WI11**（lookup 异步增量）一并交付，或其前置校验独立小项 | P1 |
| A7 | **watermark/lateness 上浮 `<sql>` 面**：schema 列级 `lateness` 属性或查询级 `watermark := col - INTERVAL` 声明，产物映射到既有 timestampsAndWatermarks + allowedLateness（D9 已放行的运行时管线零改动） | [arroyo](./2026-10-07-arroyo-analysis.md)；[risingwave](./2026-10-07-risingwave-analysis.md)；[feldera](./2026-10-07-feldera-analysis.md)（LATENESS 列注解）；[paimon](./2026-10-07-paimon-analysis.md)（水位序列化进表属性）；[hazelcast](./2026-10-07-hazelcast-jet-sql-analysis.md)（lag 参数） | 新建议（**WI29**）——本调研 P0 首选 | **P0** |
| A8 | **join store retention 公式**：窗口 join 状态保留期 = window size + grace，与 Kafka Streams `KStreamImplJoin` 同公式；核对 `EquiJoinOperator`/窗口 join 现有清理实现，缺则补 | [kafka-streams](./2026-10-07-kafka-streams-analysis.md) §对照5 | 新建议（**WI30**，先验证后实现） | P1 |
| A9 | **`<sql>` 展开产物算子命名稳定契约**：产物算子/store 名须由 sinkBean+查询确定性派生（跨版本状态连续），对齐 §八 1 稳定 operatorId；参考 KIP-1111"无显式名拒绝启动"的严格档 | [kafka-streams](./2026-10-07-kafka-streams-analysis.md) §对照13/Open Questions | 新建议（**WI31**，验证项优先） | P1 |
| A10 | **报错质量增强**：把 §4a 不支持清单做成"条件→错误文案→文档锚点→建议开关"结构化规则表；fail-fast 文案带文档锚点 | [feldera](./2026-10-07-feldera-analysis.md)（RejectUnsupportedPlans）；[ksql](./2026-10-07-ksql-analysis.md) §对照14；[hazelcast](./2026-10-07-hazelcast-jet-sql-analysis.md) §8 | 新建议（**WI32**，纯编译器/文档层） | P2 |
| A11 | **标量 UDF SPI**：ServiceLoader + open/close 生命周期 + rowContext，与既有 `IAggregatorFunctionResolver` 同构 | [seatunnel](./2026-10-07-seatunnel-sql-analysis.md) 次优先 | 新建议（**WI33**） | P2 |
| A12 | **非对称窗口 join**：`WITHIN (before, after)` 双 timeout；joins 注册表字段级扩展（leftTimeout/rightTimeout），不新增注册表条目 | [ksql](./2026-10-07-ksql-analysis.md) §对照5（`SqlBase.g4:256-262` 语法可直接参考） | 新建议（**WI34**；FULL window join 限制（4a#8）另行评估） | P2 |
| A13 | **空闲 source 水位推进**：idle source 的全局水位刷新，防慢源拖死窗口 fire | [risingwave](./2026-10-07-risingwave-analysis.md)（空闲 subtask 全局水位） | 与 production roadmap #21（Watermark 集成）核对归属后立项；不重复则新 **WI35** | P2 |
| A14 | **表达式规范化 IR**：行级算子规范成 expressions/predicates/projection 三元组（可序列化、可 diff），降低 `StreamSqlCompiler` 求值链复杂度 | [materialize](./2026-10-07-materialize-analysis.md) Conclusion（MapFilterProject） | 新建议（**WI36**，架构优化，可并入 A2 实施时顺手做） | P3 |
| A15 | **聚合 FILTER 子句**：`count(*) FILTER (WHERE ...)` 等，EQL 侧由 eql-cross-db WI7b 先行，流侧求值器跟进 | [feldera](./2026-10-07-feldera-analysis.md) §对照 | **已有归属：eql-cross-db WI7b**（流侧为其消费方） | P3 |

**nop 已领先、无须对齐的项**（避免反向收窄）：OVER 分析窗口算子（Kafka Streams/ksqlDB 均无）；UNION 多输入（ksqlDB 无 UNION）；9 受管类型闭集 + 构建期 fail-fast 契约（比运行时 schema 推导更早失败）；双目标（RDBMS 方言翻译 + 流编译）为 14 项目中独有；三注册表与 Flink OperatorTable/FunctionCatalog/hint 同构（[kafka-streams](./2026-10-07-kafka-streams-analysis.md) §对照3/9、[flink-sql](./2026-10-07-flink-sql-analysis.md) §对照）。

## 三、Tier B 触门——只提案，须 owner 裁定后方可立项

| # | 提案 | 命中门控条款 | 参考出处与最小路径 | 关联既有工作 |
|---|---|---|---|---|
| B1 | **changelog/retract 输出路径**（D1 从 (a) 改判或叠加档位） | 修订 D1 裁定（`sql-subset-and-semantics.md` §1）；§四 SQL API 行收窄范围内 | 分阶段最小路径：①先做 `emitFinal`/`appendOnly` 两档输出声明（Feldera 模式，不引入 RowKind）；②内部 retract 标记列（Arroyo `_updating_meta`）+ sink 侧 updating 能力 fail-fast 校验；③规划期 StreamKind 属性（RisingWave）；声明面可借 Paimon changelog-producer 四选一 + 代价表 | multi-engine **WI7**（CatalogTable/RowKind 元数据抽象）是前置；**WI17c**（changelog/retract 复审裁定）是既有入口 |
| B2 | **D11 解锁为两档 emit**：`emit="update\|close"`（ON_WINDOW_UPDATE / ON_WINDOW_CLOSE），不引入多档 trigger 体系 | 修订 `window-failfast-decisions.md` D11（"放行须先建 trigger 注册表并修订本裁定"） | [kafka-streams](./2026-10-07-kafka-streams-analysis.md)（EmitStrategy 两档即范围锁定）；[ksql](./2026-10-07-ksql-analysis.md)（EMIT CHANGES\|FINAL 语法位置）；需先裁定挂 `<sql>` 属性还是窗口/聚合节点（KS 是算子级、Suppressed 是输出端） | FU-4（reduce 语义 sink 能力标记）相关 |
| B3 | **D10 ACCUMULATING 放行** | 修订 `window-failfast-decisions.md` D10 | 运行时本已支持 ACCUMULATING（D10 裁定文本自证），闸门只在 build 期 fail-fast；**前置 = 修复 FU-8**（accumulationMode 从未接线，DISCARDING 声明恒 ACCUMULATING 的结构性缺口） | FU-8 |
| B4 | **D5 重裁：keyed TopN 近似**（`WHERE rn <= N` 基于 OVER ROW_NUMBER） | 修订 `sql-subset-and-semantics.md` §4/§4a#1（D5"首版排除"） | [ksql](./2026-10-07-ksql-analysis.md)（TOPK 近似）；[flink-sql](./2026-10-07-flink-sql-analysis.md)（TopN 模式）；WI12 的 PerKeyOrderedBuffer+ROW_NUMBER 已提供构件基础 | WI12 产物复用 |
| B5 | **DDL 与查询分层**（CREATE TABLE source 声明面） | 修订 D7（"拒绝 .sql DDL 文件、schema 内聚查询声明处"） | [nussknacker](./2026-10-07-nussknacker-analysis.md)（FlinkDdlParser 三类声明 + 幂等注册 + rowtime 唯一性校验是现成范式）；建议触发条件：multi-engine WI7 CatalogTable 落地后一并裁 | multi-engine WI7 |
| B6 | **CEP SQL 声明面**（MATCH_RECOGNIZE 类） | §四 4a#9"CEP 级复杂编排"排除项 | [calcite](./2026-10-07-calcite-analysis.md)（Match 纯数据节点，pattern/measures/after/subsets/interval 全为字段，语法/校验/物理/执行四层解耦——未来提案的最佳形态素材）；nop-stream-cep Pattern API 已有，桥接属新语义 | 无；远期 |
| B7 | **ASOF/时间就近 join** | 贴 §四"非等值/范围 join"排除边缘 | [feldera](./2026-10-07-feldera-analysis.md)（LEFT ASOF + `MATCH_CONDITION` 仅 `>=` 的限制式 fail-fast 模板）；若 owner 认定"就近匹配≠范围 join"可单列放行 | FU-1（排序设施）相关 |

## 四、Tier C 已裁排除——引用裁定关闭，不重复提案

| # | 项 | 关闭依据 |
|---|---|---|
| C1 | 非等值/范围 join | `00-vision.md` §四修订行 + FU-1（阻塞于 Q3 排序设施） |
| C2 | Flink SQL 方言兼容 | roadmap Non-Goals；§4a#4 |
| C3 | 代价优化器 | roadmap Non-Goals；`../2026-09/2026-09-30-doris-nereids-optimizer-extraction-feasibility.md` 口径 B/C（优化器语义层不可跨引擎移植，仅骨架约 15-20% 可用） |
| C4 | BroadcastState / broadcast join | `00-vision.md` §七 #G36 永久排除 |
| C5 | 完整 Flink SQL/Table 栈复刻 | D2 收窄裁定（`sql-vision-conflict-resolution.md` §3"保持排除"） |
| C6 | 异步算子（通用形态） | `00-vision.md` §四不变；注意 multi-engine WI11 lookup 异步是维表访问模式而非通用异步算子，不构成先例扩张 |

## 五、落地顺序建议

1. **P0**：A7（watermark/lateness 进 `<sql>` 面）——直接兑现 D9 已放行的运行时能力，声明面加法，用户价值/成本比最高。
2. **P1**：A2（CASE/CAST 两步走）、A3（HOP/SESSION + window_start/end 对齐）、A6（lookup 构建期校验，随 WI11）、A8/A9（retention 公式与命名契约，先验证后实现）。
3. **P2**：A4（CTE）、A5（全局聚合）、A10-A13。
4. **P3 / 待裁定**：B1-B7（B1/B2 建议在 multi-engine WI7 落地、WI17c 复审时一并推进；B4-B7 排队 owner 决策）。

## 引用出处

- 调研正文：本目录 14 篇 `2026-10-07-*-analysis.md`（每篇含其自身源码引用与验证记录）。
- 基线与裁定：`../../design/nop-stream/sql-subset-and-semantics.md`、`../../design/nop-stream/sql-compiler-contract.md`、`../../design/nop-stream/sql-landing-decision.md`、`../../design/nop-stream/window-failfast-decisions.md`、`../../design/nop-stream/sql-vision-conflict-resolution.md`。
- 去重对象：`../../backlog/nop-stream-sql-roadmap.md`（FU-1..FU-10）、`../../backlog/nop-stream-multi-engine-comparison-roadmap.md`（WI0-WI19）、`../../backlog/eql-cross-db-compat-roadmap.md`、`../../backlog/nop-stream-production-roadmap.md`、`../../backlog/nop-stream-cdc-roadmap.md`。
- 既有分析引用：`../nop-stream/08-gap-analysis.md`、`../nop-stream/10-flink2.3-quality-verdict.md`、`../2026-09/2026-09-30-doris-nereids-optimizer-extraction-feasibility.md`。
