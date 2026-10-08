# Esper streaming SQL 能力分析

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: Esper 最值得借鉴的两点——(1) 把 changelog（ISTREAM/RSTREAM/IRSTREAM + newEvents/oldEvents 回调）做成语言层与运行时回调的一等公民，给 nop D1「last-value-wins」留出一条可分阶段补齐 retract 的路径；(2) 把「发射节奏」写进语言（OUTPUT ... EVERY/WHEN/TERMINATED），补上 nop 缺的显式 emit 控制面。

## Context

Esper 是 Java 生态最老牌的 CEP / streaming SQL / 事件序列分析组件，同时是「查询语言 → 编译产物 → 运行时」全链路可嵌入库形态的标杆。它被列入本调研有两个原因：一是 nop-stream-cep 需要一个成熟 CEP 语言做能力对照（pattern / MATCH_RECOGNIZE / after-match skip）；二是 nop-stream-sql 需要知道一个成熟流查询语言在窗口、聚合、join、changelog、发射控制上的完整面，用以校验自己 fail-fast 子集的取舍是否合理。所依据的本地源码：`/Users/abc/sources/streaming-sql/esper`（shallow clone，master = 9.0.0）。

## 项目概览

- **定位**：CEP + streaming SQL + event series analysis 的嵌入式组件，提供 Java（Esper）与 .NET（NEsper）两套实现；无自带 UI/平台，纯库形态。
- **语言 / 运行基线**：Java；9.0.0 起要求 Java 17（`README.md:17`），ANTLR 4.13.1（`changelog.txt:8`）。
- **许可证**：GPL v2（`README.md:7`、`esper-license.txt`），另有 EsperTech 商业授权。对 nop 选型是硬约束：GPL 传染性使它只能作为「参考实现」而非依赖。
- **演进状态**：活跃维护（`changelog.txt` 逐版 issue 清单，9.0.0 修复至 FD260），Maven 父版本 9.0.0（`compiler/pom.xml:6`）。
- **模块划分**：
  - `common/` —— 共享层：SODA 对象模型（`common/client/soda/`，208 个类）、表达式/聚合/视图的 forge 与 codegen、编译期配置；
  - `compiler/` —— 语法与编译：`compiler/grammar/EsperEPL2Grammar.g`（ANTLR4，1629 行）、`compiler/internal/parse/`（AST listener）、`compiler/internal/generated/`（生成的 Lexer/Parser）、`compiler/internal/compiler/`（Janino 字节码编译）；
  - `runtime/` —— 运行时：`runtime/client/`（EPRuntime / EPDeploymentService / EPEventService / EPStageService …）；
  - `esperio/` —— 接入适配：kafka / amqp / csv / db / http / socket / springjms；
  - `examples/`、`regression-lib/`、`regression-run/` —— 示例与回归。
- **SQL/规则能力入口**：`compiler/grammar/EsperEPL2Grammar.g`（文本 EPL）→ `compiler/internal/parse/EPLTreeWalkerListener.java:80`（AST → 对象模型）→ `common/client/soda/EPStatementObjectModel.java:40`（可序列化的语句对象模型）→ `compiler/`（生成类 + 字节码）→ `runtime/client/EPDeploymentService.java:22` + `EPRuntime`（部署与事件投递）。

## 十维度分析

### 1. SQL/规则前端：EPL parser 与语义入口

- 单一 ANTLR4 语法文件 `compiler/grammar/EsperEPL2Grammar.g`（1629 行），顶层规则 `eplExpression`（`:296`）是一个大选择器，覆盖 `selectExpr` / `createWindowExpr` / `createIndexExpr` / `createVariableExpr` / `createTableExpr` / `createSchemaExpr` / `createContextExpr` / `createExpressionExpr` / `createClassExpr` / `onExpr` / `updateExpr` / `createDataflow` / `fafDelete` / `fafUpdate` / `fafInsert`。也就是说**规则前端不是「只有 SELECT」，而是 DDL + DML + DQL 同一语法入口**。
- `selectExpr`（`:316`）的子句序列是：`(INTO intoTableExpr)? (INSERT insertIntoExpr)? SELECT selectClause (FROM fromClause)? matchRecog? (WHERE ...)? (GROUP BY ...)? (HAVING ...)? (OUTPUT outputLimit)? (ORDER BY ...)? (ROW_LIMIT ...)?`——**有 HAVING / ORDER BY / ROW_LIMIT / 输出限流**，远宽于 nop-stream-sql 的子集。
- 双前端：文本 EPL 与 SODA 对象模型互转（`common/client/soda/` 208 个类，含 `OnSelectClause`、`OnDeleteClause`、`OnMergeClause`、`CreateWindowClause` 等），`EPStatementObjectModel implements Serializable`（`:40`）——可编程构造语句、可序列化、可往返渲染。
- 表达式是独立的规则族（`expression` 系列），支持 `ScriptExpression`（脚本）、`ExpressionDeclaration`（`CREATE EXPRESSION` 声明式宏）、`TableAccessExpression`（表访问）、`SubqueryExpression`（标量子查询）。

### 2. Table & Changelog 模型

- **named window**：`createWindowExpr`（`:404`）——`CREATE WINDOW w [viewExpressions] [RETAINUNION|RETAININTERSECTION] AS (SELECT ... FROM ...) [INSERT (WHERE ...)]`，即「窗口 = 命名的关系对象 + 数据窗口视图」，且可对窗口再写 `on-select` / `on-delete` / `on-update` / `on-merge`（`onExpr` `:329`）。
- **table**：`createTableExpr`（`:421`）`CREATE TABLE n (col type ...)`，带主键的表对象，可经 `intoTableExpr`（`:534`，`INTO table`）写入、经 `TableAccessExpression` 在表达式里读取——这是**有状态关系表**语义。
- **Changelog（关键差异点）**：
  - `insertIntoExpr`（`:539`）带 `(I|R|IR)STREAM? INTO ...` 前缀；`selectClause`（`:576`）同样可带 `RSTREAM` / `ISTREAM` / `IRSTREAM` 修饰，声明该语句产出「仅插入流 / 仅删除流 / 插入+删除流」。
  - 运行时回调 `UpdateListener.update(EventBean[] newEvents, EventBean[] oldEvents, ...)`（`runtime/client/UpdateListener.java:39`）——javadoc 明确「newEvents 为插入、oldEvents 为删除、两者都非空即修改通知」。
  - 这是**完整的 retract/changelog 模型**，与 nop-stream 的 D1 last-value-wins 终值语义形成直接对照。

### 3. 时间语义

- 时间源：`ConfigurationRuntimeTimeSource` + `TimeSourceType`（`MILLI` = `System.currentTimeMillis`，`NANO` = 墙钟校准的 `nanoTime`）。
- **时钟可控**：`EPEventServiceTimeControl.advanceTime(long)`（`runtime/client/EPEventServiceTimeControl.java:32`）/ `advanceTimeSpan` / `clockExternal()`；javadoc 明确「外部时钟需先关闭 internal timer，且**时间不得倒退**（除非测试并丢弃结果）」。即：**没有 watermark、没有全局乱序重排契约**，事件按到达序处理，时间只由时钟推进。
- 时间量词语法 `timePeriod`（`:1108`）支持 `year/month/week/day/hour/minute/second/millisecond/microsecond` 复合层级，比 nop 的 `INTERVAL n UNIT`（整毫秒倍数、非日历单位）表达力强，但也带来日历语义（DST）复杂度。
- 窗口内按事件时间排序的补丁手段：`ext:time_order` 视图（`ViewEnum.java:105`）在窗口内部按事件时间缓冲排序——**局部乱序容忍 ≠ 全局 out-of-order 处理**。
- `ext:timetolive`（`ViewEnum.java:110`）按事件时间做 TTL。

### 4. 窗口与聚合

- **视图注册表**（`common/internal/view/core/ViewEnum.java:45-180`）是 Esper 的「窗口类型目录」，与 nop 的参数化算子三注册表同构：
  - `win:` length / time / keepall / time_batch / time_length_batch / length_batch / time_accum / firsttime / firstlength / ext_timed / ext_timed_batch / expr / expr_batch
  - `ext:` sort / rank / time_order / timetolive
  - `std:` unique / firstunique / lastevent / firstevent / size / merge / groupwin（`GROUP_PROPERTY` + `GROUP_MERGE` `:165/:170`）
  - `stat:` uni / weighted_avg / linest / correl（统计视图族）
- 窗口作为**视图链**挂在 FROM 项上：`streamExpression`（`:592` 区域）`eventFilter viewExpressions`——`.win:time(10 sec).ext:sort(...)` 可串联。
- **聚合函数集**（`common/internal/epl/expression/agg/method/`）：`ExprSumNode`（`"sum"` `:43`）、`ExprCountNode`、`ExprAvgNode`、`ExprMinMaxAggrNode`、`ExprMedianNode`（`"median"`）、`ExprStddevNode`（`"stddev"` `:55`）、`ExprAvedevNode`、`ExprNthAggNode`、`ExprRateAggNode`、`ExprCountEverNode`、`ExprFirstLastEverNode`、`ExprLeavingAggNode`；另有 `agg/access/` 下的访问型聚合（sorted / linear / count-min-sketch）与 `agg/multifunc` 多重聚合。远超 nop 的五聚合。
- GROUP BY 支持 `CUBE` / `ROLLUP` / `GROUPING SETS`（`groupByListExpr` `:623`）；有 `HAVING`（`:646`）、`ORDER BY`（`:638`）。
- **输出限流** `outputLimit`（`:651`）：`EVERY timePeriod | EVERY n EVENTS | AT crontab | WHEN expr | WHEN TERMINATED (AND expr) (THEN onSet)`，并可与 `ALL/FIRST/LAST/SNAPSHOT` 组合；`outputLimitAfter`（`:675`）提供 `AFTER timePeriod | AFTER n EVENTS`。**这是「发射节奏」进入语言层**，nop 目前没有对应物（窗口按窗 fire、持续聚合逐条 emit）。

### 5. Join/关联

- `fromClause`（`:550`）= `streamExpression (regularJoin | outerJoinList)`；`regularJoin`（`:553`）是逗号连接；`outerJoin`（`:559`）支持 `LEFT|RIGHT|FULL [OUTER]` 与 `INNER`，`ON outerJoinIdentPair`（`:567`）其中 `outerJoinIdentPair : chainable EQUALS chainable`（`:569`）——**join 键仅等值（多个 AND 等值对）**，非等值/范围 join 不在 ON 里（但 WHERE 可写任意谓词）。
- **外部维表/方法 join**：
  - `databaseJoinExpression`（`:601`）：`sql:conn['SELECT ...'] [METADATASQL '...']` —— 外部关系库查询作为 FROM 项，等值绑定；
  - `methodJoinExpression`（`:606`）：`name:clazz(args)` —— 方法调用作为 FROM 项；
  - 子查询 join：`SubqueryExpression` / `TableAccessExpression`。
- **流-流 join**：以「多 FROM 项 + 其数据窗口视图」表达，窗口界定匹配寿命；**没有 watermark 驱动的 outer 补齐约定**（与 nop 的「window join 在 watermark 补齐」不同）。

### 6. CEP/模式匹配（对照 nop-stream-cep）

- **原生 pattern**：`patternExpression`（`:897`）→ `followedByExpression`（`:903`，含 `FOLLOWED_BY` `->` 与 `FOLLOWMAX_BEGIN` 即 `[n:>m]->`）→ `orExpression` / `andExpression` / `matchUntilExpression`（`[a:b] UNTIL x`，`:909`）→ `qualifyExpression`（`EVERY` / `NOT` / `EVERY DISTINCT` + `matchUntilRange`，`:912`）→ `guardPostFix`（`WHERE` / `WHILE` guard，`:914`）→ `atomicExpression` = `observerExpression | patternFilterExpression`（`:920`），observer 形如 `timer:at(...)` / `timer:interval(...)`（`:922`）。
- `matchUntilRange`（`:929`）`[a:b]` 是**范围重复匹配**；`patternInclusionExpression`（`:599`）`PATTERN[...]` 可直接作为 FROM 项（模式当流）。
- **SQL 标准 MATCH_RECOGNIZE**（`:700-733`）：`PARTITION BY`（`:703`）、`MEASURES`（`:705`）、`ALL MATCHES`（`:709`）、`PATTERN(...)`（`:711`，支持 `PERMUTE` `:725`、`{m,n}` 重复 `:729`、`* / + / ?` 与 reluctant `??`）、`AFTER ... SKIP`（`:713`，5 个关键字位）、`DEFINE`（`:731`）。运行期另有 `ConfigurationRuntimeMatchRecognize` 调节 skip 策略。
- **对照 nop-stream-cep**（`ai-dev/design/nop-stream/cep-design.md`）：nop 有 `Pattern.begin().where().followedBy().times().within()` Builder + NFA + SharedBuffer + `AfterMatchSkipStrategy`（NoSkip / SkipPastLast / SkipToFirst / SkipToLast）+ `FollowKind`（STRICT / SKIP_TILL_NEXT / SKIP_TILL_ANY）。**Esper 独有**：MATCH_RECOGNIZE 标准形式、`[a:b]` 范围匹配、`EVERY DISTINCT`、observer（timer/crontab）、`ON ... MERGE`。**nop 独有**：与 Flink CEP 对齐的 NFA/SharedBuffer 实现细节与 checkpoint 键类治理。两者的 after-match skip 语义等价（Esper 的 `AFTER ... SKIP` ↔ nop 的 SkipTo* 系列）。

### 7. 状态与容错

- **单进程内存模型**：状态载体是 named window / table / variable / context partition；**没有 checkpoint、没有 exactly-once、没有分布式快照**。
- 编译产物可序列化与分发：`EPCompiled implements Serializable` 持 `Map<String, byte[]> classes`（`common/client/EPCompiled.java:21/:30`），配合 `EPDeploymentService`（`:22`）的 deployment version 语义做热部署/回滚；**但运行时状态不随产物迁移**。
- `EPStageService`（`runtime/client/EPStageService.java:16`）—— stage 是「独立控制事件与时间的部署子集」，用于蓝绿式的部署分组（仍非状态持久化）。
- 容错靠外部重放 + 重新部署；`esperio-kafka` / `esperio-amqp` 等只是接入适配，不提供消费位点管理。下游要自行保证幂等（listener 逐条回调）。
- 并发/隔离手段：`ConfigurationRuntimeExecution`（prioritized / precedence / fairlock / disableLocking / filterServiceProfile）、context partition（`createContextExpr` `:491`：keyed / hash / 类别 / pattern 起止 / crontab 分段）。

### 8. 声明形态与可嵌入性

- **纯库形态（可嵌入）**：`EPRuntimeProvider` / `EPCompilerProvider` 经 ServiceLoader 取得，配置对象 `Configuration`（compiler 侧 + runtime 侧分离）在宿主进程内直接使用；无服务端、无 UI、无外部协调组件。
- 编译面与运行面严格分离：`compiler` 产出 `EPCompiled`，`runtime` 只部署与投递——**编译可在构建期做、运行期只加载**。
- 另有 `createDataflow`（`:466` 区域）数据流图 DSL 与 `EPFireAndForgetService`（`runtime/client/EPFireAndForgetService.java:25`）「发后即忘」查询接口，扩展了嵌入用法。
- 对 nop 的意义：nop-stream-sql 同样是「构建期编译、运行期执行」，Esper 证明这条路线在编译产物（可序列化字节码集合）+ 运行时部署对象（EPDeployment/EPStatement）两层分离上的成熟形态。

### 9. 架构要点（值得记的设计）

1. **EPL → AST → SODA → forge → Janino 字节码**：`CompilerAbstractionJanino`（`compiler/internal/compiler/abstraction/CompilerAbstractionJanino.java:22`）对每个生成的 `CodegenClass` 调 `JaninoCompiler.compile(...)`（`:35`）产出字节码，最终 `EPCompiled` 是 `Map<String, byte[]>`；表达式与聚合也走 codegen（`ExprNodeUtilityCodegen`）。**收益**：编译一次、序列化、跨进程/JVM 加载；**代价**：编译慢、产物与版本强耦合（serde 插件 `ConfigurationCompilerPlugInSerde` 就是为此存在）。
2. **「视图链」把 SQL 语义拆成算子管道**：`streamExpression = 预过滤 + viewExpressions 串联`（`:611`），每个视图一个 `ViewFactory/Forge`，由 `ViewEnum` 注册表解析。**这与 nop 的「参数化算子面三注册表（schemas/aggregators/joins）」同构**，差别在 Esper 把窗口类型也做成了可插拔注册表（`ConfigurationCompilerPlugInView` `ConfigurationCompiler.java:260 addPlugInView`）。
3. **插件面广度**：`ConfigurationCompilerPlugInView / PlugInPatternObject / PlugInAggregationFunction / PlugInAggregationMultiFunction / PlugInSingleRowFunction / PlugInDateTimeMethod / PlugInEnumMethod / PlugInVirtualDataWindow`（`ConfigurationCompiler.java:28/38/43`）+ 运行期 `ConfigurationRuntimePluginLoader`——**规则前端的可扩展性靠注册表而非 grammar 改动**，是 nop 三注册表路线的成熟参照。
4. **输出限流作为语言层特性**（`outputLimit` `:651`）：把「何时发射」从算子实现里提到 SQL 子句，避免每个算子各造一套 emit 逻辑。

### 10. 与 nop-stream/nop-stream-sql 的对照与可借鉴点

- **最值得借鉴 A（changelog 分阶段补齐）**：Esper 证明 retract 可以只做在「输出面」——`ISTREAM/RSTREAM/IRSTREAM` 是语句级修饰，`UpdateListener` 的 `oldEvents` 是回调参数，两者都不需要改窗口算子内部。nop 的 D1 裁定是「last-value-wins 终值语义」，属刻意降级；若将来要补 retract，Esper 的分层（先输出面 oldEvents → 再窗口 retract）是最低侵入路径，可直接作为 roadmap 论据。
- **最值得借鉴 B（发射控制面）**：`OUTPUT ... EVERY/WHEN/TERMINATED + FIRST/LAST/SNAPSHOT` 与 Nussknacker 的 `emitWhen` 触发器同源。nop 目前「窗口按窗 fire、持续聚合逐条 emit」，缺显式 emit 控制；这条在不改语义（D1/D6）的前提下可独立增量交付。
- **对照性结论（非借鉴）**：Esper 是**全量支持**（HAVING/ORDER BY/CUBE/非窗口 join/全局聚合都支持），nop 是**构建期 fail-fast 窄子集**——两者取舍不同层级，Esper 的存在说明「窄子集 + fail-fast」不是能力上限问题，而是双目标（RDBMS/流）一致性与实现成本的选择；`EsperEPL2Grammar.g` 是「如果要放开子集，需要补哪些规则」的清单来源。
- **GPL 红线**：Esper 只能作为语义与架构参考，不能作为依赖引入（`README.md:7`）。

## 对照 nop-stream-sql 已交付子集

**入口形态**

| nop-stream-sql | Esper | 差异 / 可借鉴 |
|---|---|---|
| `.stream.xml` 顶层 `<sql sinkBean>`，EQL 语法与 ORM 共用 parser | 独立 ANTLR4 EPL grammar + SODA 双前端 | nop 用「共享 parser + 双目标（RDBMS/流）」换取单口径；Esper 用「专用 grammar + 对象模型往返」换取表达力。nop 的 EQL 双目标是刻意设计，Esper 的 SODA 往返（`EPStatementObjectModel` 可序列化）值得参考——**流模型 XML 若也暴露可编程对象模型，工具链（校验/重写/迁移）会更容易**。 |
| 内嵌 schema：9 受管类型闭集，DATE/TIMESTAMP/DECIMAL fail-fast | `CREATE TABLE` / `CREATE SCHEMA` / `CREATE WINDOW ... (col type)`，类型由 Java 类型系统决定 | Esper 类型面几乎不设限；nop 的 9 类闭集是 D7 决策，Esper 证明「类型面放开」需要一整套自动推导，闭集是低成本正确解。 |
| FROM 表名 = 数据源 bean 名 | FROM = 事件类型过滤 `eventFilterExpression`（`:936`）/ named window / table / sql: / method: | Esper 的 FROM 项种类是 nop「数据源 bean 名」的超集；nop 若要支持「FROM 已有窗口/中间结果」，Esper 的 `insert into` + 命名对象是现成模型。 |

**支持清单逐条**

| nop 支持项 | Esper 对应 | 差异 / 可借鉴 |
|---|---|---|
| SELECT 投影 | `selectClause`（`:576`） | Esper 额外支持 `DISTINCT`、`ISTREAM/RSTREAM` 修饰、`@annotation` 投影项 |
| WHERE | `whereClause`（`:553` 区域） | 一致 |
| 五聚合 sum/count/avg/min/max，COUNT(*) | `ExprSumNode`/`ExprCountNode`/`ExprAvgNode`/`ExprMinMaxAggrNode` + median/stddev/avedev/nth/rate/count-when/first-ever 等 | Esper 聚合面宽 3~4 倍，且有**插件聚合**（`ConfigurationCompilerPlugInAggregationFunction`）。**可借鉴**：nop 的 `aggregators` 注册表应预留插件位（外部注册的聚合 id），而非只有内置五聚合。 |
| GROUP BY | `groupByListExpr`（`:623`）含 CUBE/ROLLUP/GROUPING SETS | Esper 更强；nop 的 GROUP BY 是无窗口持续聚合，Esper 的分组由 `groupwin` 视图 + 聚合共同定义 |
| TUMBLE(t, INTERVAL) 伪表函数 | `win:time` / `win:time_batch` / `win:time_length_batch` 等视图（`ViewEnum.java:50/60/65`） | Esper 用「视图」而非「表函数」表达窗口；nop 的 TUMBLE 伪表函数对 RDBMS 目标有回显边界（W2/T1）。**可借鉴**：Esper 视图式窗口天然只属于流目标，不会污染 SQL 通道——若 nop 后续要扩窗口类型（滑动/会话），视图式注册比继续扩 TUMBLE 家族更干净。 |
| 双流等值 join（INNER/LEFT/RIGHT/FULL，watermark 补齐） | `outerJoin`（`:559`）等值 ON，无 watermark 补齐约定 | **nop 的 watermark 驱动 outer 补齐优于 Esper**（Esper 靠窗口长度隐式界定）；Esper 的 ON 仅等值（`:569`）与 nop 的「非等值 fail-fast」一致——**两者都把非等值 join 排除在 join 语法外**，佐证 nop 的 fail-fast 是行业常态而非过度保守。 |
| UNION ALL（bag 语义） | 无直接 UNION 语句；用 `insert into` + 多语句汇流 | Esper 走「多语句 + 命名流汇流」，nop 走集合运算符——nop 对用户更接近 SQL 直觉。 |
| 静态维表 lookup join（ITableLookup→IJdbcTemplate/IBatchLoader） | `sql:conn['SELECT ...']`（`:601`）+ `method:` join（`:606`） | Esper 把外部查询做成 FROM 项且**不区分静态/动态**；nop 明确「静态维表」边界。**可借鉴**：Esper 的 `METADATASQL`（元数据随查询下发）提示了批量 loader 的查询改写空间。 |
| 无窗口持续 GROUP BY | 持续查询 + `outputLimit` 控制发射 | Esper 同样持续，但多一层 emit 控制（见上） |
| 分析窗口 OVER（ROW_NUMBER, FRAME_SLIDING_AGG） | `ext:rank` 视图（`:75`）+ `nth_value` 类访问聚合 | Esper 用视图表达 Top-N；nop 用 SQL OVER——nop 更贴 SQL 用户心智 |

**不支持清单逐条**

| nop 不支持（fail-fast） | Esper 状态 | 结论 |
|---|---|---|
| 全局 ORDER BY/LIMIT | 支持（`orderByListExpr` `:638`、`rowLimit` `:677`） | Esper 无流式语义压力（输出限流另有 `outputLimit`）；nop fail-fast 合理，但 `ROW_LIMIT` 在**窗口内**（Top-N）是有意义的对照项——nop 已用 OVER ROW_NUMBER 覆盖。 |
| 非等值/范围 join | 不支持（`outerJoinIdentPair : chainable EQUALS chainable` `:569`） | **一致**，nop 的 fail-fast 与 Esper 的语法限制同源。 |
| DATE/TIMESTAMP/DECIMAL 列绑定 | 支持（Java 类型系统） | Esper 无闭集约束；nop 的 9 类闭集是刻意收口，不构成「应该放开」的证据。 |
| CASE/CAST/IN 子查询等标量子集外表达式 | 全部支持（SODA 里 `CaseWhenThenExpression`/`CastExpression`/`SubqueryInExpression`） | Esper 是全集；nop 是窄集。**可借鉴**：`docs/` 中的子集外清单可直接对照 `common/client/soda/` 类名做优先级排序（CASE/CAST 属最常用）。 |
| DISTINCT 聚合 | 部分支持（`EVERY DISTINCT` 在 pattern 层；聚合层用 access 聚合实现 distinct count） | nop fail-fast 不意外；若要补，Esper 的 `agg/access` 分层（state + method + accessor）是实现模板。 |
| HAVING/CTE/INTERSECT/EXCEPT | HAVING 支持（`:646`）；CTE/INTERSECT/EXCEPT 未在 grammar 中出现 | Esper 也没有 CTE/INTERSECT/EXCEPT——**nop 的 default-reject 不落后**。 |
| 全局聚合（无 GROUP BY） | 支持（`AggregationServiceGroupAll`） | Esper 支持；nop fail-fast。**可借鉴**：全局聚合 = 单 key 分组，实现成本低于表面，可作为 P2 项。 |
| FULL window join | `outerJoin` 支持 `FULL OUTER`（`:563`） | Esper 语法上支持；nop 对**窗口** FULL join fail-fast（水位线两侧补齐在流模型下复杂）——Esper 没有 watermark 概念所以「看起来支持」，实际由窗口长度隐式界定，**不构成 nop 应放开的证据**。 |
| UNION DISTINCT | 无 UNION 语句 | 不适用 |
| Flink SQL 方言 | 非目标（自有 EPL） | Esper 自造方言反而是其生态锁定来源；nop 选择「EQL 共享 parser」避免了第二方言 |
| CEP 级复杂编排不进 SQL 面 | Esper **把 pattern/MATCH_RECOGNIZE 放在 SQL 里**（`:700`/`:897`） | **这是最有价值的对照**：nop 把 CEP 归 `nop-stream-cep`（独立 Pattern API + NFA），Esper 混在 EPL 里。Esper 的教训是：pattern 与 SELECT 的组合会把 grammar 与语义复杂度乘起来（`matchRecog?` 插在 FROM 与 WHERE 之间，`PATTERN[...]` 又是 FROM 项）。nop 的**模块分层（SQL 不含 CEP）更利于 fail-fast 与双目标一致性**——这是本篇对 nop 最重要的反向确认。 |

**语义标注（D1/D6/D9/D10/D11）**

- D1（last-value-wins 终值语义，无 RowKind/无 retract）：**Esper 与 nop 相反**——Esper 有显式 `newEvents/oldEvents`（`UpdateListener.java:39`）。差异根源是输出模型：Esper 是「回调通知」，nop 是「算子发射记录」。借鉴路径见 §10-A。
- D6（事件时间）：Esper 无 watermark、时钟倒退禁止（`EPEventServiceTimeControl.java:32` 注释）；nop 有显式事件时间 + watermark。**nop 更强**。
- D9（allowedLateness 已放行）：Esper 无对应概念（迟到事件只能靠 `ext:time_order` 局部缓冲）。**nop 更强**。
- D10/D11（保持 fail-fast）：Esper 无对应约束可参考，维持 fail-fast。
- retract/CDC 全链路为 D1 裁定的不支持项：Esper 是「语言层支持、状态层不持久」——即 **Esper 的 retract 也只在单进程内存内自洽**，跨进程重放仍需下游幂等。这不改变 D1 裁定，但可作为「retract 增量路径」的论据。

**架构对照**

- nop：EQL AST → `StreamSqlCompiler`（nop-stream-sql，经 `ISqlStreamCompiler` SPI）→ 流模型 XML；三注册表（schemas/aggregators/joins）；双目标（RDBMS 方言 / 流管线）。
- Esper：EPL → AST → SODA → forge → Janino 字节码 → `EPCompiled(byte[])` → `EPDeployment` → 运行时视图链。
- 关键差异：nop 的中间表示是**声明式 XML 模型**（可读、可 diff、可被其他构建器消费），Esper 的中间表示是**字节码**（快、不可读、与版本耦合）。**nop 的选择更利于低代码场景**（模型可被 designer 直接编辑）；Esper 的收益（编译产物可分发）nop 通过「构建期编译 + 运行期加载 XML 模型」已获得等价性，无须迁移到字节码。

## 引用出处

以下路径均已在本地源码库 `/Users/abc/sources/streaming-sql/esper` 逐条验证存在；行号取自实际读取内容。

- `/Users/abc/sources/streaming-sql/esper/README.md:3`（定位）、`:7`（GPL v2）、`:17`（9.0.0 需 Java 17）
- `/Users/abc/sources/streaming-sql/esper/changelog.txt:4`（Changes in version 9.0.0）、`:8`（ANTLR 4.13.1）
- `/Users/abc/sources/streaming-sql/esper/compiler/pom.xml:6`（parent version 9.0.0）
- `/Users/abc/sources/streaming-sql/esper/compiler/grammar/EsperEPL2Grammar.g:296`（eplExpression）、`:316`（selectExpr）、`:329`（onExpr）、`:404`（createWindowExpr）、`:413`（createIndexExpr）、`:421`（createTableExpr）、`:491`（createContextExpr）、`:534`（intoTableExpr）、`:539`（insertIntoExpr）、`:550`（fromClause）、`:553`（regularJoin）、`:559`（outerJoin）、`:567`（outerJoinIdent）、`:569`（outerJoinIdentPair）、`:576`（selectClause）、`:599`（patternInclusionExpression）、`:601`（databaseJoinExpression）、`:606`（methodJoinExpression）、`:611`（viewExpressions）、`:623`（groupByListExpr）、`:638`（orderByListExpr）、`:646`（havingClause）、`:651`（outputLimit）、`:675`（outputLimitAfter）、`:677`（rowLimit）、`:700`（matchRecog）、`:703`（matchRecogPartitionBy）、`:705`（matchRecogMeasures）、`:709`（matchRecogMatchesSelection）、`:711`（matchRecogPattern）、`:713`（matchRecogMatchesAfterSkip）、`:717`（matchRecogPatternAlteration）、`:725`（matchRecogPatternPermute）、`:729`（matchRecogPatternRepeat）、`:731`（matchRecogDefine）、`:897`（patternExpression）、`:903`（followedByExpression）、`:909`（matchUntilExpression）、`:912`（qualifyExpression）、`:914`（guardPostFix）、`:920`（atomicExpression）、`:922`（observerExpression）、`:929`（matchUntilRange）、`:936`（eventFilterExpression）、`:1108`（timePeriod）
- `/Users/abc/sources/streaming-sql/esper/compiler/src/main/java/com/espertech/esper/compiler/internal/parse/EPLTreeWalkerListener.java:80`
- `/Users/abc/sources/streaming-sql/esper/compiler/src/main/java/com/espertech/esper/compiler/internal/compiler/abstraction/CompilerAbstractionJanino.java:22`、`:35`
- `/Users/abc/sources/streaming-sql/esper/common/src/main/java/com/espertech/esper/common/client/EPCompiled.java:21`、`:30`
- `/Users/abc/sources/streaming-sql/esper/common/src/main/java/com/espertech/esper/common/client/soda/EPStatementObjectModel.java:40`
- `/Users/abc/sources/streaming-sql/esper/common/src/main/java/com/espertech/esper/common/client/configuration/compiler/ConfigurationCompiler.java:28`（plugInViews）、`:38`（plugInPatternObjects）、`:43`（plugInAggregationFunctions）、`:260`（addPlugInView）
- `/Users/abc/sources/streaming-sql/esper/common/src/main/java/com/espertech/esper/common/client/configuration/runtime/ConfigurationRuntimeTimeSource.java`（MILLI/NANO 默认）
- `/Users/abc/sources/streaming-sql/esper/common/src/main/java/com/espertech/esper/common/client/util/TimeSourceType.java`（MILLI/NANO 枚举）
- `/Users/abc/sources/streaming-sql/esper/common/src/main/java/com/espertech/esper/common/internal/view/core/ViewEnum.java:45`（length）… `:180`（expr），含 `:75`（ext:sort）、`:80`（ext:rank）、`:105`（ext:time_order）、`:110`（ext:timetolive）、`:165`（std:merge）、`:170`（std:groupwin）
- `/Users/abc/sources/streaming-sql/esper/common/src/main/java/com/espertech/esper/common/internal/view/timewin/TimeWindowViewForge.java:52`（getName = "time"）
- `/Users/abc/sources/streaming-sql/esper/common/src/main/java/com/espertech/esper/common/internal/epl/expression/agg/method/ExprSumNode.java:43`（"sum"）、`ExprCountNode.java:72`（"count"）、`ExprAvgNode.java:48`（"avg"）、`ExprMedianNode.java:44`（"median"）、`ExprStddevNode.java:55`（"stddev"）
- `/Users/abc/sources/streaming-sql/esper/runtime/src/main/java/com/espertech/esper/runtime/client/UpdateListener.java:39`（update(newEvents, oldEvents, ...)）
- `/Users/abc/sources/streaming-sql/esper/runtime/src/main/java/com/espertech/esper/runtime/client/EPEventServiceTimeControl.java:32`（advanceTime）、`:49`（advanceTimeSpan）
- `/Users/abc/sources/streaming-sql/esper/runtime/src/main/java/com/espertech/esper/runtime/client/EPStageService.java:16`（stages 语义）
- `/Users/abc/sources/streaming-sql/esper/runtime/src/main/java/com/espertech/esper/runtime/client/EPDeploymentService.java:22`
- `/Users/abc/sources/streaming-sql/esper/runtime/src/main/java/com/espertech/esper/runtime/client/EPFireAndForgetService.java:25`
- `/Users/abc/sources/streaming-sql/esper/runtime/src/main/java/com/espertech/esper/runtime/client/EPRuntime.java:36-108`（各服务入口）
- `/Users/abc/sources/streaming-sql/esper/esperio/`（esperio-kafka / amqp / csv / db / http / socket / springjms 目录存在）

未在本仓库验证：Esper 官方文档中的语义描述（如 out-of-order 的推荐处理、HA/集群能力）——本地 clone 的 `doc/` 仅含 javadoc 资源，无正文文档；相关判断基于源码与 javadoc 注释。历史上的 esperha（HA）模块在本仓库中不存在，故「无分布式容错」的结论仅由「源码中无 checkpoint/state persist 模块」推得。
