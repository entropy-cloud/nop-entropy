# sql-subset-and-semantics——D1/D3/D4/D5/D6/D15 六条裁定落档

> Status: active
> 裁定日期：D3 = 2026-09-30（owner 已裁，本档补录）；D1/D4/D5/D6/D15 = 2026-10-02
> 落档日期：2026-10-02
> 负责人：仓库 owner（委托链：2026-10-02 执行指令「执行 nop-stream-sql-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」委托 ZCode 代理按 roadmap 建议项执行；D3 为 owner 于 2026-09-30 直接裁定，原文见 roadmap 前置裁定表 D3 行与 `ai-dev/logs/2026/09-30.md`）
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI0b 行、前置裁定表、§3.3、§3.4、Risks re-scope 表）
> 承载 plan: ai-dev/plans/nop-stream-sql/02-wi0b-subset-and-semantics-decisions.md

## 1. D1 结果表语义 = (a) 终值语义降级

| 项 | 内容 |
|---|---|
| 选项 | (a) 终值语义降级：`<reduce>` last-value-wins 加模型语义标注；(b) 算子内模拟 retract 加 upsert sink；(c) `StreamRecord` 加 RowKind |
| 结论 | **(a)**。模型与文档必须显式标注 **last-value-wins 终值语义：非 append-only、非 retract**（引擎无逐条追加式聚合输出面） |
| 理由 | `StreamRecord` 仅 value/timestamp/hasTimestamp 三字段（`StreamRecord.java:38-48`），(c) 触及元素模型契约；`AccumulationMode.ACCUMULATING_AND_RETRACTING` 为 spec-only 且 `WindowOperator.java:449` 对其开期 fail-fast；`SinkConsistencyCapability.UPSERT_BY_KEY` 全仓零调用方，(b) 需首次接线撤回输出路径，范围显著变大；`<reduce>` 语义即逐条 emit 当前归约值（`StreamReduceOperator.java:87-106`，last-value-wins），(a) 直接采用它并显式标注 |
| 受影响 WI | WI11 落为 `<reduce>` 映射加 last-value-wins 语义标注（**不得写成 append-only**）；WI12/WI13 保持终值输出；R1 关闭 |

### 对 D9-D12 的输入约束（WI0d 裁定输入）

1. D1=(a) 关闭 retract 路线 → D10「(c) 路线下必须放行」分支失效，放行 ACCUMULATING 的前提消失。
2. `ACCUMULATING_AND_RETRACTING` 维持 spec-only fail-fast（`WindowOperator.java:449` 既有门禁不因 (a) 改变）。
3. D12 的 per-transform parallelism 与 2PC 门禁不因 (a) 改变（productization item 29 既有约束原样保持）。

## 2. D3 EQL 窗口语法补全范围 = 语法全集直增（补录）

| 项 | 内容 |
|---|---|
| 选项 | 按方言裁剪语法 / 全集直增 |
| 结论 | **语法全集直增，方言差异不进 grammar**；能力经 dialect `<features>` 下发，缺省不启用，翻译期未启用抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` |
| 裁定日期 | 2026-09-30（owner 直接裁定）；本节为补录落档 |
| 理由 | D3 分层原则落地机制（roadmap §3.3）：grammar 全集直增单一口径；能力开关下沉 dialect（`dialect.xdef:45-58` 24 能力位、`default.dialect.xml:15-23` 集中缺省）；翻译期裁决点全仓仅一处（`EqlTransformVisitor.java:1477-1483`，错误码 `OrmEqlErrors.java:191`） |
| 受影响 WI | WI1（grammar 全集直增）、WI2（能力位）、WI3（启用矩阵实跑） |

## 3. D4 流时间窗口语法 = TUMBLE/HOP/SESSION 伪表函数

| 项 | 内容 |
|---|---|
| 选项 | `TUMBLE(t, INTERVAL)` 伪表函数 / `WINDOW` 子句 / stream-only 标记 |
| 结论 | **伪表函数**。语法落 nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4 的 `sqlTableSource`/`sqlSingleTableSource` 表源分支（新增伪表函数变体；该文件 `:144-148 sqlTableSource` 两分支、`:150 sqlSingleTableSource`，`//groupWindow : regularFunction;` 残迹 `:191-192`、TUMBLE 注释残迹 `:216-218` 是历史意图证据）；AST 节点落 `model/ast/io/nop/orm/eql/ast/EqlAST.xjava` |
| 流目标映射 | 窗口 assigner 参数化（WI10 承接声明面，WI17 编译器消费）；时间列 t 的绑定立场见 D7 §5 时间列绑定 |
| RDBMS 目标 | T1/T2/T3 三档：T1 直通（仅流目标，产物标注 stream-only）；T2 近似（**全部语义不等价，产物必须标注**；仓库内唯一可证映射 `oracle.dialect.xml:184-186` date→trunc；PG date_trunc / TimescaleDB time_bucket 可用性属外部事实，**未在本仓库验证**，各方言映射写法以 WI3 实跑产出为准）；T3 拒绝并给替代建议 |
| 受影响 WI | WI1（若伪表函数语法随窗口 grammar 一并直增）、WI2/WI3（方言映射）、WI10/WI17（流映射）、WI16（三档策略确认） |

## 4. D5 全局 ORDER BY 与 LIMIT = keyed TopN 放行（2026-10-08 修订）、全局排序维持排除

| 项 | 内容 |
|---|---|
| 选项 | 排除 / keyed 缓冲近似 |
| 结论（首裁 2026-10-02） | **首版排除**，进不支持清单 |
| 结论（修订 2026-10-08） | **keyed TopN 放行，全局排序维持排除**。放行的语义形态 = 每键分组内取前 N（keyed TopN 近似）；语法载体归 WI40 承载 plan 实测裁定（survey 参考形态 `WHERE rn <= N` 基于 OVER ROW_NUMBER 构件，非锁定语法；已知边界两处由 WI40 处置——子查询表源现 fail-fast，同层 WHERE 引用窗口输出的 QUALIFY 类语义未核实）。无 GROUP BY 键的全局 ORDER BY / 全局 LIMIT 维持排除。实施归本 roadmap WI40 |
| 首裁理由两支的修订处置 | (1) 「引擎无 sort/TopN 设施」：全局分支仍成立（维持排除）；keyed 分支已失效——sql roadmap WI12 已构件级交付 PerKeyOrderedBuffer 每键有序缓冲 + ROW_NUMBER frame（OverWindowOperator，现仅测试实例化、尚无生产接线）。(2) 「keyed 缓冲近似引入全量状态与正确性边界问题」：keyed 形态下状态有界——每键缓冲受 watermark 推进清理，状态规模 O(每键窗口内元素数) 而非全局全量；正确性边界由 WI40 实施钉语义测试（乱序/迟到/键内并列秩）承担 |
| 边界注记 | 本裁定不预授实施所需的模型级结构变更：若 WI40 落地所需声明结构构成注册表结构变更/新条目类型/条目集变更，须另走 vision §六#1 补记；由既有已授权模型类型承载的声明形态不在此列。**放行≠已实施**：全局 ORDER BY / LIMIT 编译期 fail-fast（StreamSqlCompiler compileQuery，现 :248-253，pin 码 `nop.err.eql.dialect-not-support-feature`）维持现状，直至 WI40 交付 |
| 负责人 / 日期 | 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成……」）；2026-10-08 |
| 不支持清单草案（首裁原件，保留） | 全局 ORDER BY / LIMIT；非等值 / 范围 join；CDC retract 全链路；Flink SQL 方言兼容；DATE/TIMESTAMP/DECIMAL 列绑定（见 D7 §5）；CEP 级复杂编排 |
| 受影响 WI | WI40（keyed TopN 实施 + §4a#1 修订兑现）、WI16（清单行交叉注记归其承载 plan 维护） |

## 5. D6 分析窗口流上执行语义 = 事件时间

| 项 | 内容 |
|---|---|
| 选项 | 事件时间 / processing-time 近似 |
| 结论 | **事件时间**。分析窗口（OVER）与双流 join（WI13）共用 watermark 与每 key 有序缓冲设施（WI12 建构件、WI13 复用） |
| 理由 | processing-time 近似使两目标（RDBMS 翻译对照 + 流执行）结果不可对照，破坏 WI20 双目标一致性验证；事件时间与既有 watermark 体系一致（roadmap §3.2 水位合并路径已有多通道合并证据） |
| Q2 归属 | 窗口 frame 在事件时间下的「重开/修正」表达：D1=(a) 无 RowKind，不引入修正语义；frame 重开按事件时间重算语义处理，WI12 设计时落实，不另开裁定 |
| 受影响 WI | WI12（每 key 有序缓冲 + TestAnalysisWindowEventTime）、WI13（join 共用设施） |

## 6. D15 多库实跑降级档 = 允许降级 + 强制标注

| 项 | 内容 |
|---|---|
| 选项 | opt-in 实跑为硬门 / 允许 H2 实跑加其余方言快照比对降级 |
| 结论 | **允许降级，但必须在交付说明逐方言标注未实测，禁止无标注收口** |
| 协议 | 默认 H2 实跑（不依赖 Docker）；`-Dnop.test.docker.enabled=true` 可用时实跑 PG / Oracle / MySQL / MariaDB / MSSQL（5 个既有 Testcontainers 方言测试，注解见 `TestPostgreDialect.java:16` 等）；Docker 不可用时其余方言以 SQL 快照比对（golden）降级，交付说明逐方言标注「未实测」 |
| Q1 | CI 是否纳入该 flag 保持未决（本裁定不裁）；WI3/WI5/WI20 按 opt-in 执行 |
| 受影响 WI | WI3、WI5、WI20 |

## 4a. D5 不支持清单（WI16 定稿）

WI0b 落档的清单草案经 WI16 对照 live 实现定稿。每项附 fail-fast 形态与代码指认——清单不是愿望列表，每一项都有当前的显式失败路径：

| # | 不支持项 | 当前 fail-fast 形态 | 指认 |
|---|---|---|---|
| 1 | 全局 ORDER BY / LIMIT（D5；**keyed TopN 已放行 2026-10-08，实施归 WI40——放行≠已实施**） | 全局排序（无 GROUP BY 键）维持排除：编译器不支持清单对 SQL 文本报 `nop.err.eql.dialect-not-support-feature`（StreamSqlCompiler compileQuery，现 :248-253）；引擎无全局 sort/TopN 设施。keyed TopN（每键取前 N）经 D5 2026-10-08 修订放行，实施与 §4a#1 本行最终形态归 WI40 交付 | sql-subset-and-semantics §4（2026-10-08 修订）；roadmap §3.1 |
| 2 | 非等值 / 范围 join（FU-1） | join 声明面仅等值键集（leftKeyExprs/rightKeyExprs 数量相等校验，`nop.err.stream.invalid-arg`） | StreamModelDslBuilder.validateJoinDeclarations；TestParameterizedJoinModel.keyCountMismatchFailsFast |
| 3 | retract / CDC 全链路（D1=(a) 终值语义降级） | 模型语义标注 last-value-wins，非 append-only 非 retract；`<reduce>` 逐条 emit 当前归约值 | sql-subset-and-semantics §1；StreamReduceOperator |
| 4 | Flink SQL 方言兼容 | 非目标（roadmap Non-Goals），编译器不解析 Flink 方言 | roadmap Purpose |
| 5 | DATE / TIMESTAMP / DECIMAL 列绑定（D7） | 九受管类型闭集严格解析，集合外类型名 `nop.err.stream.invalid-arg` | StreamSchemaRegistry.resolveManagedType；TestStreamSchemaConsumer |
| 6 | ~~DISTINCT 聚合~~（**WI17 supersede，2026-10-08**：DISTINCT 聚合已支持——`DistinctAggregateWrapper` 按求值后参数值去重，merge 重放对侧未见过的新值保证恰一次计数；restore 经 `SqlAccShapeUtil` 形状归一化。仍排除：SELECT DISTINCT 非聚合 dedup、UNION DISTINCT） | 原 `withDistinct(true).create` fail-fast 钉码已移除 | TestStreamSqlAggregations.distinctFiltersDuplicates / distinctMergeReplaysOnlyUnseenValues / distinctCompositeAccumulatorSurvivesSnapshotCopyAndContinues、TestStreamSqlCompiler.distinctAggregateShape |
| 7 | 标量子集外表达式（正则函数调用 / CASE / CAST / IN 子查询 / 参数标记 / null AST） | StreamSqlExprCompiler 分派默认分支抛 `nop.err.stream.invalid-arg`（含 AST 节点类型） | TestStreamSqlExprCompiler.outOfSubsetFailsFastWithStreamSideCode |
| 8 | FULL 窗口 join（窗口补齐语义） | join 声明面校验：窗口 join 的 joinType 限 INNER/LEFT，`nop.err.stream.invalid-arg`（FULL 非 window 补齐属 WI13 评估项，非窗口 FULL join 不受限） | TestParameterizedJoinModel.fullWindowJoinFailsFast |
| 9 | CEP 级复杂编排 | 不在本 roadmap（productization roadmap 边界） | roadmap Rules |

## 4b. 纳入面总表（WI16 定稿）

| 能力 | 纳入形态 | 依据 |
|---|---|---|
| SELECT 投影 | WI9 标量子集：列引用（含限定名）/字面量/算术（+ - * / %，除法恒 double）/比较/AND-OR-NOT/IS NULL/BETWEEN/IN 值列表 | StreamSqlExprCompiler；TestStreamSqlExprCompiler |
| WHERE | 既有 filter 通路（xpl-fn 内联）+ WI9 编译器形态（同标量子集） | stream.xdef filter；WI9 |
| 聚合 | sum/count/avg/min/max 五 id（与 BaseRule.g4 五关键字对齐）；COUNT(*) 无参形态；DISTINCT 聚合支持（WI17 supersede 4a#6——参数值域去重，COUNT(*) DISTINCT 以整记录为去重域）；精确去重的内存代价 = 每组去重值数量（窗口聚合随窗清除，持续聚合随作业生命周期） | StreamSqlAggregations.builtinIds 钉子；DistinctAggregateWrapper |
| HAVING | GROUP BY + 聚合路径编译为 aggregate 后 filter（windowed + continuous 两路径，AggregateRef 解析 `event[N]` 槽位）；canonical 形态（聚合仅出现在 HAVING 不在 SELECT）支持；无 GROUP BY 的 HAVING 与 join + HAVING typed 拒绝；全局聚合 + HAVING 维持排除 | StreamSqlCompiler.insertHavingFilter；TestStreamSqlCompiler.havingFilterShapeWindowed / havingOnlyAggregateCanonicalShape / havingOnJoinFailsFast / havingWithoutGroupByFailsFast |
| GROUP BY | keyBy（keyExpr）+ 窗口聚合（WI8c aggregatorRef）或持续聚合（keyBy + reduce，D1=(a) last-value-wins 终值语义——非 append-only 非 retract，中间归约值逐条 emit；标注见 StreamReduceOperator/AdvancedTransforms.buildReduce javadoc） | stream.xdef；TestContinuousGroupBy |
| 流时间窗口 | 伪表函数三变体（D4 形态全集）：`TUMBLE(t, INTERVAL)`（WI17，2026-10-03）+ **`HOP(t, slide, size)`（Flink TVF 参数序）与 `SESSION(t, gap)`（WI26，2026-10-08）**——grammar 规则↔AST 类一一对应（sqlHopTableSource/sqlSessionTableSource，prop label 与 AST prop 恒等）；间隔三原则（常量/正值/短单位）对 HOP 双 interval 分别适用，本仓裁定 slide<=size（sql 层+DSL 层双守卫，引擎不校验）；流目标映射：TUMBLE/HOP/SESSION→keyBy+window+aggregate（strategy windowFnId=tumbling/sliding/session-event-time，HOP 增 slide 属性）/无伪表函数有 GROUP BY→keyBy+reduce 持续聚合（D1 last-value-wins）/全局聚合 fail-fast/HOP 与 SESSION 的 plain 管道声明 fail-fast；窗口列命名契约 window_start/window_end（保留名 typed 拒绝，暴露归 FU-E2）；ORM 通道 resolve fail-fast `nop.err.eql.table-source-not-resolved`（stream-only，T1 直通），toSQL 渲染逐字回显；RDBMS 目标 T1/T2/T3 三档（见 4c） | sql-subset-and-semantics §3；TestEqlTumbleGrammarParse；TestEqlHopSessionGrammarParse；TestStreamSqlCompiler；TestSqlHopSessionDeclaration |
| `<sql>` 声明面 | 顶层 `<sql sinkBean>` 元素（sql 文本 + 内嵌 schema + sinkBean）——模型级生成器而非 transform：builder.build() 前经 ISqlStreamCompiler SPI（flow 定义 / nop-stream-sql 实现）编译为完整流模型 XML 回读替换；`<sql>` 必须是模型唯一内容（并存 fail-fast）；无 provider fail-fast 点名 nop-stream-sql | stream.xdef；TestSqlModelExpansion（flow fake 展开/无 provider）；TestSqlModelDeclarationE2E（sql 真实接线反空壳） |
| 双流等值 join | WI8d 声明面（joins/joinRef，恰两条上游边）+ WI13 运行时；HASH 边禁入 | validateJoinDeclarations；join-operator.md |
| 静态维表 lookup | process 桥接形态：ITableLookup 注入（应用实现包 IJdbcTemplate/IBatchLoader） | ITableLookup；WI14 |
| union 多流 | DataStream.union（WI6）；重复声明边=重复合流 | multi-input-model.md |
| 窗口声明 | windowingStrategies（WI10 duration 参数化 + D9 allowedLateness 放行 + WI38 accumulationMode 接线——D10 2026-10-08 放行 ACCUMULATING 声明，DISCARDING/ACCUMULATING 双档声明即所得，A&R 双层拒绝 + WI37 emit 两档——`<sql>/<strategy emit="UPDATE|CLOSE">`，UPDATE 逐记录发射隐含 ACCUMULATING，仅窗口聚合查询合法，其余路径与 join 引用 fail-fast + WI25 时间列 lateness——`<field lateness>` Q1 裁定列级形态，仅伪表函数（TUMBLE/HOP/SESSION，WI26 推广）时间列生效映射 strategy allowedLateness，非时间列/负值/无 strategy 路径 fail-fast，查询级水位延迟声明转 FU-E6） | window-failfast-decisions |

## 4c. W2 三档确认（D4 落点复核）

| 档 | 语义 | WI16 确认 |
|---|---|---|
| T1 直通 | 仅流目标，产物标注 stream-only | 确认：TUMBLE/HOP/SESSION 在 RDBMS 无原生对应（外部事实未在本仓库验证），直通产物必须带 stream-only 标注 |
| T2 近似 | RDBMS 侧映射近似函数，**语义不等价必须标注** | 确认：仓库内唯一可证映射为 oracle date→trunc；各方言映射写法以 WI3 实跑矩阵为准（sql-window-dialect-matrix.md） |
| T3 拒绝 | 无近似即拒绝并给替代建议 | 确认：拒绝时给 T1 降级建议（stream-only 执行） |

## 4d. SQL 链路错误码表（WI16 定稿，零新增码）

全部复用既有 `nop.err.*` 码；按链路阶段分组，每项与钉码测试对账：

**声明面（DSL 构造期，StreamModelDslBuilder / AdvancedTransforms）**

| 码 | 触发 | 钉码测试 |
|---|---|---|
| nop.err.stream.invalid-arg | aggregate bean/aggregatorRef 并存或双缺；join 上游非恰两条边；joinType 缺失；键集缺失/数量不等；timeout 依赖窗口或格式非法；union 无上游 | TestAdvancedTransforms（恰一两用例）、TestParameterizedJoinModel（六用例）、TestAdvancedTransforms.unionWithoutUpstreamFailsFast |
| nop.err.stream.ref-unknown | 未知 aggregatorRef/joinRef/windowStrategyRef | TestAdvancedTransforms.aggregateUnknownAggregatorRefFailsFast、TestParameterizedJoinModel（两用例） |
| nop.err.stream.upstream-type | aggregate 上游非 WindowedStream；union 上游 KeyedStream/WindowedStream | TestAdvancedTransforms（既有 + unionRejectsKeyedUpstream） |
| nop.err.stream.edge-hash-redundant | HASH 边入 keyBy/union/join | TestAdvancedTransforms.hashEdgeIntoUnionFailsFast、TestParameterizedJoinModel.hashEdgeIntoJoinFailsFast |
| nop.err.stream.not-implemented | join 运行时占位（WI13 交付前）；sideOutput | TestParameterizedJoinModel.selfJoinTopologyWalksValidationToRuntimePlaceholder |

**编译期（SQL 文本 → AST → 求值器/声明面）**

| 码 | 触发 | 钉码测试 |
|---|---|---|
| nop.err.stream.invalid-arg | 标量子集外表达式（含 null AST）；聚合 fnId 未知；expr 编译失败；参数个数/类型不符；SELECT DISTINCT / UNION DISTINCT（WI17 supersede 后 DISTINCT 聚合不再触发本码）；HAVING 无 GROUP BY 或 join + HAVING（WI17 新增守卫）；schema 受管类型名未知；多入边 gate 配置冲突；无 resolver provider | TestStreamSqlExprCompiler.outOfSubsetFailsFastWithStreamSideCode、TestStreamSqlAggregations、TestStreamSchemaConsumer、TestMultiEdgeGateConfigConsistency、TestAdvancedTransforms.aggregateRefWithoutProviderFailsFastNamingDependency、TestStreamAggregatorFunctionResolver（unknownFnIdFailsFast/exprCompileFailureFailsFast/missingArgumentOnNonCountFailsFast/sumOfNonNumericColumnFailsFastWithSchema）、TestStreamSqlCompiler（distinctSelectFailsFast/unionDistinctFailsFastBagUnionOnly/havingOnJoinFailsFast/havingWithoutGroupByFailsFast） |

**运行期（方言能力与执行）**

| 码 | 触发 | 钉码测试 |
|---|---|---|
| nop.err.eql.dialect-not-support-feature | 方言未启用窗口能力位（既有消费点） | TestEqlCompileSql（既有断言） |
| nop.err.stream.invalid-state / job-execute-failed 等 | 既有执行期错误面（不在 SQL 子集新增范围） | 既有测试 |

## 4e. ASOF 时间就近 join 维持排除裁定（WI0b，2026-10-08）

| 项 | 内容 |
|---|---|
| 选项 | 认定「时间就近匹配」单列于非等值 join 排除之外 / 维持排除 |
| 结论 | **维持排除**。「时间就近匹配」（ASOF join）仍归入非等值/范围 join 排除，不单列放行 |
| 引用边界母条款 | vision §四 Non-Goals「双流 Join」排除行——「非等值/范围/广播 join」；本档 §4a#2（非等值 / 范围 join，FU-1）为其流侧清单条目 |
| 理由 | 放行需新增第二 join 类型（`EquiJoinOperator` 之外的时间就近匹配算子形态）与有序状态设施；真实需求未出现——比照 multi-engine roadmap WI0 裁定 R6 判例「roadmap 立项属缺口登记不构成真实用户需求」。survey B7（Feldera LEFT ASOF + MATCH_CONDITION 仅 `>=` 的限制式 fail-fast 模板）保留为未来放行时的形态素材 |
| 重开触发条件 | 真实用户需求出现，且 owner 认定「就近匹配≠范围 join」单列放行；届时实施另立（Follow-up Backlog FU-E4 承接，不预设 WI） |
| 负责人 / 日期 | 仓库 owner（委托链：2026-10-08 执行指令，同 §4 D5 修订行）；2026-10-08 |

## 7. 裁定一致性备注

- 六条裁定的 roadmap 行由承载 plan Phase 2 同步「已裁定 owner + 日期」；本档为落档权威文本。
- 本档不裁 D7/D8/D13（归 sql-compiler-contract.md，WI0c）、D14（已裁定，归 sql-landing-decision.md，WI8a）、D9-D12（归 window-failfast-decisions.md，WI0d）。
