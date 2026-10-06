# 外部典型流处理样例的 nop-stream XDSL 可移植性分析

> Status: resolved
> Date: 2026-10-06
> Scope: `nop-stream` 声明式 DSL（`stream.xdef` + `StreamModelDslBuilder` 真实执行链）vs Apache Flink / SeaTunnel / TDengine / Hazelcast(Jet) 官方与社区的 12 个复杂流处理样例
> Conclusion: nop-stream XDSL 对「事件时间 + 窗口聚合 + CEP + keyed-state + 多 sink」这一主干样例族（Flink TopSpeed 除外、TDengine 标准聚合、Hazelcast session/sliding、Flink WindowJoin、SeaTunnel 多表路由）**基本可移植**；但对「窗口触发控制（trigger/evictor/punctuation/early-results）、迟到数据 side output、维表 lookup join、异步 I/O、重算回补」这五类语义**DSL 层不可表达**（不是缺功能而是 builder 构建期 fail-fast 或 schema 无声明面）——其中多数只差一个声明面接线，但 windowFnId 白名单与 trigger fail-fast 是移植复杂窗口样例的两道硬门。

## Context

- 决策点：平台已在五方对比中确认 nop-stream 的语义深度，但尚未回答「把外部成熟项目的复杂流作业移植到 nop-stream XDSL 上，**声明式入口到底能覆盖多少**」。若 XDSL 表达力弱于 Java API，则推荐路径是 Java 而非 XDSL，影响产品化叙事。
- 做法：先**逐行核实 XDSL 真实执行能力**（`stream.xdef` schema + `StreamModelDslBuilder`/`AdvancedTransforms` 的 fail-fast 判定即真实契约），再选 12 个外部复杂样例逐个映射。
- 关键判定原则：区分三档——① DSL 可表达（声明即可跑通）；② DSL 不可表达、但**核心已有 Java API 可绕过**（需写 bean 或改用 Java）；③ 两端都缺（需 roadmap 补）。

## 一、XDSL 真实表达能力（代码即契约）

### 1.1 已实现的面（DSL 直接可跑）

| 能力 | 声明形态 | 代码锚点 |
|---|---|---|
| Source/Sink | `bean` 或内联 xpl（`(ctx)=>void` / `(event)=>void`） | `StreamModelDslBuilder` buildSource/BuildSink |
| map/flatMap/filter/reduce | `bean` 或内联 xpl（`(event)=>any` / `(event,out)=>void` / `(event)=>boolean` / `(a,b)=>any`） | buildMap/Filter/FlatMap :981-999；`XplReduceFunction` |
| keyBy | `keyExpr`（XLang 表达式） | `StreamKeyByModel` |
| 事件时间 | `timestampsAndWatermarks`：`watermarkStrategyBean` 或 xpl `timestampAssigner`+`watermarkGenerator`（至少声明其一，否则 R5 FLOW-4 fail-fast）；`watermarkInterval`（含 0=逐事件）已接线 | buildTimestampsAndWatermarks :827-865；F-04a |
| 窗口 | `windowingStrategies` 注册 + `window strategyRef`；参数化 `windowFnId="tumbling-event-time"` + `duration`（ms/s/m）；legacy 白名单 `global/tumbling-global/tumbling-event-time-1s/5s`；**其它 windowFnId（sliding/session 等）必须以 bean 名解析** | resolveWindowAssigner :257-300；parseDurationMillis |
| 窗口算子 | `aggregate`（bean 与 `aggregatorRef` 恰一；aggregatorRef 经 SPI 提供 sum/count/avg/min/max 内建目录）、`reduce`（bean/xpl，Keyed 或 Windowed 上）、`process`（bean） | buildAggregate :357；resolveAggregatorRef :397 |
| allowedLateness | strategy 级 + window 节点级子元素（D9 已放行，传到算子） | buildWindow :204 |
| 等值 join | `<joins>` 注册 `joinSpec`（INNER/LEFT/RIGHT/FULL + 双侧 XLang keyExprs）；window join 需参数化 duration + timeout（INNER/LEFT）；恰两条入边（可自连接） | buildJoin :492-548 |
| union | 多入边合流（保序确定性 merge） | buildUnion :634-690 |
| CEP | `<patterns>` 声明式（`single/group/where/until/within/gapWithin/followKind/afterMatchSkip*` 量词）；`cep patternRef` + `bean` 选函数；缺水位供给时 WARN | buildCep :751-808；`pattern.xdef` |
| custom 算子 | `customType` 解析 `OneInputStreamOperator` bean | buildCustom :697-748 |
| 边 | `partition=FORWARD/HASH`+`keyExpr`（HASH 冗余/入 union 皆 fail-fast）；`per-transform parallelism`（window 为虚拟元素不可声明） | validateEdgeDeclarations :673-700；buildWindow :181 |
| 声明式 SQL | `<sql>` 顶层生成器：SELECT/WHERE/GROUP BY/5 聚合/TUMBLE/等值 join/UNION ALL，编译为完整流模型 | `nop-stream-sql.md` §1；`StreamSqlCompiler` |

### 1.2 DSL 不可表达的面（构建期 fail-fast 或 schema 无声明）

| 能力 | 现状 | 代码锚点 |
|---|---|---|
| **窗口 trigger** | strategy 与 window 节点的 `triggerId` 一律 `ERR_STREAM_WINDOW_ATTR_UNSUPPORTED` reject——**XDSL 无法为任何窗口指定触发器**，只能用 assigner 默认 | failFastOnUnsupportedWindowStrategy :222-240；failFastOnUnsupportedWindowNodeAttrs :246-255 |
| **evictor** | `stream.xdef` 无 evictor 声明属性（3 个 evictor 仅 Java API + 单测） | stream.xdef :49-52；user-guide 触发表 |
| accumulateMode | 非 DISCARDING reject | :233-239 |
| **side output** | `<sideOutput>` 节点恒 fail-fast（core 有 `getSideOutput` 视图，但图级无路由；仅 Java API `sink/print/collect` 消费） | buildSideOutput :810-824 |
| **side input / broadcast** | `<sideInputs>` 注册表整体 fail-fast（无执行消费方），`broadcast` 属性无从生效 | failFastOnUnsupportedRegistries :425-427 |
| 边级流控 | `flowControlPolicy/queueCapacity/receiveWindow/packetSize` 四属性一律 reject（`FlowControlPolicy` 枚举仅 Java API） | validateEdgeDeclarations :673-700；firstDeclaredFlowControlAttr :836-848 |
| 无消费方注册表 | `<streams>`（命名流）、`<environments>`、`<requirements>`、`<checkpointParticipants>`、`<coders>`、onStart/onEnd/onError 回调——全部 fail-fast | failFastOnUnsupportedRegistries :419-460 |
| source/sink params | `<params>`、maxParallelism、outputType/inputType、consistencyCapability——全部 fail-fast | :933-960, :1038-1060 |
| custom 体内逻辑 | `<custom>` 的 `<params>` 与 `<source>` 体 reject（只认 customType bean） | :707-727 |
| windowFnId 白名单 | sliding/session/global-with-duration 等**不能**用参数化 id 声明——必须注册 `WindowAssigner` bean 再以 bean 名引用 | resolveWindowAssigner :257-300 |

## 二、12 个外部复杂样例逐个映射

图例：✅ DSL 可表达｜⚠️ 核心已有但 DSL 层不可表达（须 bean 或 Java）｜❌ 两端缺

### Flink

1. **StateMachineExample**（`~/sources/flink/flink-examples/.../statemachine/`，250 LOC，1368 LOC 包）— per-key DFA + `ValueState` + checkpoint 2000ms + Kafka/生成源二选一 + 限速 `RateLimiterStrategy` + 双 sink。**⚠️→✅ 主体可移植**：keyed-state 用 `process` bean（DFA 全在 bean 内），select sink 用 `sink bean`，水位 `watermarkStrategyBean`；**限速 DSL 无面**（roadmap P0-5 未落），需 bean 内部自行限速或不用。→ **可移植**（限速降级为 bean 内实现）。
2. **TopSpeedWindowing**（200 LOC）— `GlobalWindows` + `TimeEvictor.of(10s)` + `DeltaTrigger.of(50meters)` + `maxBy(1)`，每跨 50m delta 发一条。**⚠️ 触发/驱逐是核心**：XDSL 既无 trigger 声明（fail-fast）也无 evictor 面 → **该样例的核心语义 DSL 不可表达**，须整体退到 Java API（`GlobalWindows.of().triggering(DeltaTrigger).evicting(TimeEvictor)`）。这是 12 例中窗口语义最重的一个，也是最清楚的「DSL 表达力硬边界」。
3. **SideOutputExample**（215 LOC）— `ProcessFunction` 分词，>5 字符走 `OutputTag` 侧输出，其余进 5s 滚动窗口 sum，双 `FileSink`。**❌→⚠️ 侧输出是主结构**：`<sideOutput>` 恒 fail-fast → 须 Java API 从 `getSideOutput(tag).sink(...)` 消费；窗口聚合部分本身 ✅。→ **半移植**（核心分流须 Java）。
4. **WindowJoin**（172 LOC）— `grades` ⋈ `salaries` 事件时间 5s 窗口等值 join。**✅ 可移植**：`<joinSpec joinType=INNER windowStrategyRef=tumbling-event-time+duration timeout=...>` + 双边 `keyExpr`，是 DSL 已落地的 join 声明面的教科书用例。
5. **CountNewsClicks**（DSv2，351 LOC，样例集最大）— 1h 内点击计数，`ListState` 缓冲预发布点击 + `registerTimer(publishTime+1h)` 事件时间定时器 + 水位 idleness 30s。**⚠️ 主体可移植但核心在 bean**：pending 缓冲 + timer 全在 `process` bean 内（DSL 的 `process` 是 bean-only，状态与 timer 不属声明面）；水位 idleness 靠 `watermarkStrategyBean`。→ **可移植**（逻辑落 bean，DSL 只负责拓扑）。
6. **CountSales**（DSv2，277 LOC）— source 声明/生成水位，下游 `ProcessFunction` 显式**处理**水位对象。**⚠️ 半移植**：nop-stream 水位不是可被用户函数消费的一等数据（`StreamElement` 家族在 core 内部），无「用户函数 handle watermark」声明面 → 须 bean 或 Java。
7. **AsyncIOExample**（120 LOC）— `AsyncDataStream.unorderedWait(capacity=20, timeout=10s)`。**❌ DSL 与 Java 均无通用异步算子**（vision §四异步算子 Non-Goal；仅 roadmap WI11 收窄为维表 lookup 异步，且未落）→ **不可移植**。
8. **Table API 集**（`flink-examples-table/.../basics/`）— UpdatingTopCityExample（LATERAL…ORDER BY…LIMIT 2 + RowKind 物化 retraction，194 LOC）、TemporalJoinSQLExample（`FOR SYSTEM_TIME AS OF` 时态 join，196 LOC）、StreamWindowSQLExample（`TUMBLE` + `COUNT(DISTINCT)`，99 LOC）。**❌→⚠️**：`ORDER BY/LIMIT`、retraction/RowKind、`COUNT(DISTINCT)`、`FOR SYSTEM_TIME` 时态 join 均在 `nop-stream-sql` 不支持清单（default-reject / fail-fast）；纯 `TUMBLE + sum/count` 部分 ✅。→ **聚合子集可移植，retraction/时态 join/TopN 不可**。

### SeaTunnel

9. **sql_transform_multi_table.conf**（155 LOC HOCON）— 1 源 3 表，Sql transform 按 `table_match_regex` 路由 + per-table override 规则优先级，1 sink 多表断言。**⚠️ 语义倒挂**：nop-stream `<sql>` 是**顶层唯一内容**（与其他 transforms 并存即 fail-fast），不支持 transform 级 SQL、不支持多表流；多表能力需等 roadmap WI7 的 CatalogTable/RowKind（未落）。→ **不可移植**（等价能力缺失，非接线问题）。
10. **embedding_transform.conf**（375 LOC）— 1 源 → 4 个并行 Embedding transform（LLM embedding，各 api_key）→ 4 sink。**✅ 拓扑可移植**：XDSL 支持一分多（同一 source 挂 4 条 transform 链各接 sink，`fraud-s1-cdc.stream.xml` 已用此形）；但**异步 LLM 调用无异步算子面**（❌ AsyncIO 同因），每个 embedding 只能退化为同步 `process` bean。→ **拓扑可移植、外部异步不可**。
11. **starrocks-to-assert-multipletable.conf**（380 LOC，最大 HOCON）— 1 源（2 表 14 列 schema + scan_filter）→ 2 个独立 sink 块（粗粒度表名断言 + 细粒度 row_rules/field_rules）。**⚠️** 多 sink ✅；多表 + 类型投影 ❌（同 #9 CatalogTable 未落）；类型化断言属测试面非运行时。→ **多 sink 可移植，多表不可**。
    - 附：SeaTunnel transform 插件目录（`seatunnel-transforms-v2/.../transform/`：sql/filter/rename/replace/rowkind/jsonpath/regexextract/split/metadata/python/nlpmodel…）**无窗口、无水位**——SeaTunnel 本身是数据集成引擎，窗口类样例天然落在 Flink/TDengine/Hazelcast 侧，不构成 nop-stream 窗口缺口。

### TDengine

12. **avg_current_stream**（官方文档，`docs.tdengine.com/quick-start/stream-processing/`）— `CREATE STREAM … INTERVAL(1m) SLIDING(1m) … PARTITION BY tbname,groupId STREAM_OPTIONS(FILL_HISTORY_FIRST | MAX_DELAY(3s)) … AS SELECT AVG(current) … FROM %%trows`。**⚠️ 四要素拆解**：`INTERVAL+SLIDING` 滑动窗口 → DSL 参数化仅 `tumbling-event-time`，sliding 须 bean ⚠️；`PARTITION BY` 多表分区 → ❌（CatalogTable 未落）；`MAX_DELAY(3s)` 窗口开窗即出 → 无 trigger 面 ❌（近 `ContinuousProcessingTimeTrigger`/early-results 同族）；`FILL_HISTORY_FIRST` 历史回补 → 无 backfill 面 ❌（roadmap WI10 未落）。→ **单表 tumbling 版可移植，整例不可**。
13. **Trigger 家族总览**（`stream-processing/syntax/` + `best-practices/`）— PERIOD/SLIDING/INTERVAL/SESSION/STATE_WINDOW/EVENT_WINDOW/COUNT_WINDOW/嵌套 WINDOW + 17 项 `STREAM_OPTIONS`（`WATERMARK(d)`、`TRUE_FOR(d AND/OR COUNT …)`、`EVENT_TYPE(WINDOW_OPEN|CLOSE|IDLE|RESUME)`、`IGNORE_DISORDER`/`DELETE_RECALC`、`EXPIRED_TIME`、`PRE_FILTER`、`ROLLUP BY`、`NOTIFY`…）。**⚠️ 系统性缺口**：nop-stream XDSL 的窗口控制面只有「assigner + allowedLateness」，TDengine 的整个 `STREAM_OPTIONS`/触发词表（17 选项 × 触发类型）在声明层**没有对应物**；其中 `WATERMARK(d)` ≈ 现有水位策略 ✅、`IGNORE_DISORDER` ≈ 迟到丢弃（无面，⚠️），其余 ❌ 或 roadmap 候选项（窗口事件化 WI8、重算 WI10）。→ **触发/迟到策略族是最大单点缺口**。

### Hazelcast

14. **TopNStocks**（`hazelcast-jet/examples/sliding-windows/.../TopNStocks.java`，~100 LOC）— 两级窗口：sliding(10s,1s) 线性趋势 → 二级 tumbling(1s) 内 `allOf(topN(5,升), topN(5,降))` 复合聚合。**⚠️ 两处硬门**：sliding 须 WindowAssigner bean；`allOf` 复合聚合 + topN 累加器不在 `aggregators` 内建目录（仅 sum/count/avg/min/max）→ 须 bean 聚合器（⚠️ 可绕）。两级窗口链本身 ✅（`window→aggregate→keyBy→window→aggregate`）。→ **可移植但须 2 个 bean**。
15. **TransactionTracking**（`examples/pattern-matching/...`，~93 LOC）— `mapStateful(TTL=2000ms)` START/END 配对 + **过期回调发 TIMED_OUT**。**✅/⚠️**：TTL 过期回调 ≈ CEP `within` 语义或 `process` bean 内 timer（DSL 侧 CEP `within` ✅ 覆盖超时），过期回调需 bean → **可移植**（CEP 或 process bean）。
16. **Enrichment**（`examples/enrichment/...`，283 LOC，Hazelcast 最大例）— 一个 trade 流 4 种维表富集：`mapUsingIMap`×2、`mapUsingReplicatedMap`×2、**`hashJoin2`**（trade 流同时 join 两个批文件源，`joinMapEntries`）、`enrichUsingGrpc`（异步 gRPC，**unverified**）。**⚠️/❌ 混合**：`mapUsingIMap/ReplicatedMap` ≈ 静态维表 lookup join → DSL 已有（`keyBy+process` bean 或 WI14 形态）✅/⚠️；N 路批源 hashJoin（同时 join 两源）→ DSL join 恰两入边 ❌（需两次 join 或 union 变通）；异步 gRPC ❌（同 AsyncIO）。→ **单源富集可移植，N 路 hashJoin 与异步不可**。
17. **SessionWindow**（~97 LOC）— `window(session(5_000))` + `allOf(summingLong, mapping(toSet))` 复合聚合。**⚠️**：session window 须 bean（`EventTimeSessionWindows` 实现 `MergingWindowAssigner`，但 XDSL 无合并接线）；复合聚合须 bean。→ **可移植但须 2 个 bean**。
18. **TradingVolumeOverTime**（`examples/early-window-results/...`，~76 LOC）— `tumbling(2000).setEarlyResultsPeriod(20)`，窗口未闭合即每 20ms 发射部分结果（乱序 5s）。**❌ XDSL 无 early-results/trigger 面** → 须 Java（近 `ContinuousProcessingTimeTrigger`，仅 API）。与 TDengine MAX_DELAY、Flink DeltaTrigger 同族问题——**三项目同一问题、三种机制，nop-stream 三者皆无声明面**。
19. **AccessLogStreamAnalyzer**（`examples/files/...`，~180 LOC）— `Sources.fileWatcher` tail 文件 + 正则解析 + filter + `addTimestamps` + sliding(10s,1s) + `counting()`。**⚠️**：file-following 源 —— nop file source 是有界 split（恢复语义 at-least-once，边界光标），**无持续 tail 监听**（Flink 该例亦注明 Windows WatchService 不触发）；sliding 须 bean。→ **不可移植**（源形态缺）。
20. **Hazelcast SQL 流**（`docs.hazelcast.com/hazelcast/5.7/sql/querying-streams` + 两 tutorial）— `IMPOSE_ORDER`（水位声明，迟到超 lag **丢弃**）、`TUMBLE`、`HOP`（=sliding）、stream-stream `BETWEEN` 定时 join、`CREATE JOB … OPTIONS('processingGuarantee'='exactlyOnce')` + `SINK INTO`、Kafka×IMap join。**⚠️/❌**：`IMPOSE_ORDER` ≈ 水位策略 bean ✅；`TUMBLE` ✅（SQL 面）；`HOP` ❌（sliding 不在 SQL 面亦不在参数化 DSL）；`BETWEEN` 时序 join ❌（join 仅等值）；`CREATE JOB` 生命周期与 `SINK INTO` 物化视图 ❌（无声明面）；exactly-once 语义本身 ✅（`<checkpoint processingGuarantee>`）。

## 三、跨项目能力对照（移植视角）

| 能力 | Flink | SeaTunnel | TDengine | Hazelcast | nop-stream XDSL |
|---|---|---|---|---|---|
| Tumbling 窗口 | ✅ | ❌ | ✅ | ✅ | ✅（参数化 + 白名单） |
| Sliding/Hopping | ✅ | ❌ | ✅ | ✅ | ❌（须 bean） |
| Session 窗口 | ✅ | ❌ | ✅ | ✅ | ❌（须 bean，无合并接线） |
| 水位 | ✅ 一等 | ❌ | ✅ | ✅ | ✅（bean 或 xpl assigner/generator） |
| 自定义 trigger | ✅ | ❌ | ✅ `TRUE_FOR`/`EVENT_WINDOW` | ❌（early-results） | ❌（XDSL 一律 reject） |
| Evictor | ✅ | ❌ | ❌ | ❌ | ❌（schema 无面） |
| 早出结果 / 延迟出数 | ✅ DeltaTrigger | ❌ | ✅ MAX_DELAY | ✅ early-results | ❌ |
| 迟到处理 | ✅ side output | ❌ | ✅ IGNORE/RECALC | ⚠️ 仅丢弃 | ⚠️ 仅 allowedLateness（无 side output 面） |
| CEP | ⚠️ 手写 DFA | ❌ | ✅ EVENT_WINDOW | ✅ TTL 状态 | ✅ 声明式 `<patterns>`（含量词/within/skip） |
| keyed state + timer | ✅ | ❌ | ❌ | ✅ TTL | ⚠️ 只能在 process/CEP bean 内，DSL 不表达 |
| 窗口等值 join | ✅ | ❌ | ❌ | ❌ | ✅ `<joinSpec>` |
| 维表 lookup join | ✅ | ✅ Sql | ❌ | ✅ ×3 | ⚠️ keyBy+process bean（WI14 形态） |
| N 路 / 时序 join | ✅ connect | ❌ | ❌ | ✅ BETWEEN | ❌（恰两入边、仅等值） |
| 异步 I/O | ✅ | ✅ | ❌ | ✅ gRPC | ❌（vision Non-Goal） |
| 广播 / 分叉 | ✅ | ✅ fan-out | ✅ PARTITION BY | ✅ fork | ⚠️ 分叉 ✅（多出边）、broadcast state ❌ |
| 回补 / 重放 | ✅ | ❌ | ✅ FILL_HISTORY | ❌ | ⚠️（恢复即重放；无显式回补作业，WI10 未落） |
| TopN / 排名 | ✅ | ❌ | ✅ SQL LIMIT | ✅ topN | ⚠️ 复合累加器 bean |
| DISTINCT 计数 | ✅ | ❌ | ✅ | ✅ | ❌（SQL 不支持；≈ CountTrigger+evictor 近似） |
| 限速 | ✅ | ✅ rows/s | ❌ | ✅ | ❌（roadmap P0-5 未落） |
| 多表 / 分区键流 | ✅ | ✅ | ✅ PARTITION BY | ❌ | ❌（CatalogTable 未落，WI7） |
| exactly-once | ✅ | ✅ | ⚠️ 依重算 | ✅ | ✅（`<checkpoint>` + 能力校验） |
| 多 sink | ✅ | ✅ | ✅ | ✅ | ✅（fraud-s1 四链四 sink） |

## Conclusion

- **可移植且已有 DSL 声明面**（移植成本≈改写 XML）：Flink WindowJoin(#4)、Hazelcast TransactionTracking(#15) 的 CEP/TLL 面、SeaTunnel 多 sink/fan-out(#10 拓扑)、Hazelcast SQL 的 TUMBLE+水位+exactly-once(#20 子集)、Flink StateMachine/CountNewsClicks(#1/#5) 的拓扑（逻辑落 process bean）、fraud-s1 同族四 CEP 链。
- **核心语义仅 Java API 可达**（DSL 是硬门）：窗口 trigger 全族（TopSpeed #2、TradingVolume #18、TDengine MAX_DELAY/TRUE_FOR #13）、evictor、side output 分流（#3）、水位一等对象处理（#6）、side input/broadcast（FailFast）。
- **两端皆缺**（需 roadmap 补齐才能移植）：异步 I/O（#7/#10/#16）、N 路/时序 join（#16/#20）、持续 tail 源（#19）、回补作业（#12 FILL_HISTORY）、TopN/复合累加器内建（#14/#17）、DISTINCT（SQL）、限速（#1）、多表流（#9/#11/#12）。
- 结论对产品化的含义：XDSL 目前是「**拓扑声明面强、窗口控制面与生态算子弱**」的引擎——复杂拓扑（多分支/多 sink/CEP/join）声明式覆盖良好，但把「窗口怎么触发、什么时候出、迟到怎么办、外部要不要异步」这些**最难运维**的语义推回 Java bean。若要抬高 XDSL 覆盖度，优先级最高的三件事是：① `windowingStrategies` 增 trigger/evictor 声明面（解除 #2/#18/#13 硬门）；② `<sideOutput>` 图级路由（解 #3，也是 roadmap WI8 的前置）；③ sliding/session 参数化（解 #14/#17/#19 的窗口面）。三者均在本次 roadmap（`nop-stream-multi-engine-comparison-roadmap.md`）的既有 WI 语义增强范围内，非新增范围。
- 后续工作：本报告不直接产出 plan；若采纳上述三件事，建议在 `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` 的 M2（结构前置与流语义增强）下新增 WI（窗口控制面声明化 / sideOutput 路由 / sliding-session 参数化），并把 SeaTunnel/TDengine/Hazelcast 样例作为验收用例。
- 被否决方案：把「用 XDSL 覆盖全部 12 例」作为验收标准——异步/N 路 join/多表流属 roadmap 未落项且部分受 vision Non-Goal 约束（异步算子），强求 XDSL 全覆盖会逼迫 DSL 违背既有裁定。

## Open Questions

- [ ] 窗口控制面（trigger/evictor）声明化是否值得进 M2？它会让 XDSL 与 Java API 的 Trigger 家族（8 trigger + 3 evictor，均有单测）首次对齐，但窗口语义面扩大也会带来新的 fail-fast 面（trigger 与 CEP/allowedLateness 组合合法性）。
- [ ] sliding/session 参数化 vs 强制 bean：bean 路径已能覆盖（注册 `WindowAssigner` bean），参数化的增量价值（少写 bean）是否足以抵过新增声明面 + 组合校验成本？
- [ ] COUNT(DISTINCT) 走 SQL 面补齐，还是文档化为「≈ `countWindow` + evictor 近似」？（SQL 不支持清单含 DISTINCT，近似已在 user-guide 触发表列出。）
- [ ] file tailing 源（#19）是否属 nop 场景需求？现 file source 为有界 split + at-least-once 恢复，若无日志 tail 诉求可不补。
- [ ] 维表 lookup join 的推荐 XDSL 形态最终是 `keyBy+process` bean（现状）还是新增声明面（WI11 增量）——影响 #1/#16/#20 富集类样例的移植成本。

## References

- `ai-dev/analysis/2026-10/2026-10-06a-nop-stream-vs-flink-tdengine-hazelcast-seatunnel-comparison.md`（五方能力对比，本文的缺口面前置）
- `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md`（后续 WI 承载；WI7 CatalogTable / WI8 窗口事件化 / WI10 重算 / WI11 lookup 异步）
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`、`pattern.xdef`（XDSL schema 合同）
- `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java`、`AdvancedTransforms.java`（XDSL→DataStream 真实执行链与 fail-fast 契约，即本文 1.1/1.2 全部代码锚点）
- `docs-for-ai/03-modules/nop-stream-user-guide.md`（XDSL 章节 + 触发语义映射表）、`03-modules/nop-stream-sql.md`（SQL 纳入面/不支持清单）、`03-modules/nop-stream-connectors.md`（连接器能力矩阵）
- `nop-stream/nop-stream-fraud-example/src/main/resources/_vfs/nop/stream/demo/fraud-s1-cdc.stream.xml`（已落地的多 CEP 链 + 多 sink 声明实例）、`nop-stream/quickstart/template/.../topology3-cep-pattern.stream.xml`
- 外部样例源：`~/sources/flink/flink-examples/flink-examples-streaming/src/main/java/org/apache/flink/streaming/examples/{statemachine,windowing/TopSpeedWindowing,sideoutput,join,dsv2,async}`、`~/sources/flink/flink-examples/flink-examples-table/.../basics/`、`~/sources/seatunnel/seatunnel-e2e/.../{sql_transform_multi_table.conf,embedding_transform.conf,connector-starrocks-e2e/starrocks-to-assert-with-multipletable.conf}`、TDengine 官方文档 `docs.tdengine.com/quick-start/stream-processing/` + `stream-processing/syntax/`，Hazelcast Jet `github.com/hazelcast/hazelcast-jet/tree/master/examples`（TopNStocks / TransactionTracking / Enrichment / SessionWindow / TradingVolumeOverTime / AccessLogStreamAnalyzer）+ `docs.hazelcast.com/hazelcast/5.7/sql/querying-streams`