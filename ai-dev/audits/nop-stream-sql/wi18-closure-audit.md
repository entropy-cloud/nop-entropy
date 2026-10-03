# WI18 Closure Audit——26-wi18-sql-entry-and-docs.md

- Audit 日期：2026-10-03（一轮 audit FAIL + 二轮复核 FAIL + 三轮点核 PASS，见文末「二轮轻量复核」「三轮点核」章节）
- Auditor：独立子 agent（fresh session，与实现者非同一 session/task_id；全部结论来自 live repo 实读/实跑，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：HEAD `e4ae2a2d6a`（分支 add-stream-sql，审计起止工作树均 clean；WI18 提交 7 文件 +261/-0——测试 + classpath 资源 + 文档三件套 + 日志 + plan，零生产代码改动）
- **一审裁定（单轮）：FAIL——1 Major + 3 Minor，全部集中于唯一交付文档 `docs-for-ai/03-modules/nop-stream-sql.md` 的内容失实。测试与登记面全部成立：TestStreamSqlEntryE2E 资源驱动区分真实、判别力有反事实锚、隔离与全量实跑绿（71/71）；`<sql>` xdef 元素在案且 D8 五选项裁定核对成立；INDEX.md 与 source-anchors 三锚点登记齐备且指向真实文件；双门禁 exit 0；git/plan/日志三方一致。但用户文档逐条对照 live 出现一处错误码契约失实（§1/§4 两处把 SPI 缺失写成 `nop.err.stream.not-implemented`，live 代码与 flow 钉码测试均为 `nop.err.stream.invalid-arg`）——按本次审计授权「文档错误即 FAIL 项」，阻断 roadmap 翻转；修正全部为同一文件内的一至数行编辑，修复后提请第二轮轻量复核即可解锁。**
- **二轮裁定（已被三轮取代）：FAIL（维持）——二轮确认 MAJ-1/MIN-1/MIN-3 三处修复到位且与 live 一致（全文无 `not-implemented` 残留；双门禁复跑绿：doc-links --strict 0 errors、sql 模块全量 71/71），但 MIN-2 漏改：修复 diff 四 hunk 均不含 §2，L36 括注「（逐项有 fail-fast，§4a）」原样保留，retract/CDC（设计文档 §4a#3 为语义降级）与 Flink 方言（§4a#4 为非目标）仍被 blanket 概括为 fail-fast 项。修复为 L36 一行内编辑，改后提请第三轮点核（仅核该行 + doc-links）即可解锁。**
- **最终裁定（三轮点核后，2026-10-03）：PASS——三轮点核确认 MIN-2 修复到位：`docs-for-ai/03-modules/nop-stream-sql.md` §2 L36 括注「（逐项有 fail-fast，§4a）」已删除，改为如实分层「多数项构建期显式 fail-fast（八项）+ retract/CDC 单列 D1 语义降级（非 fail-fast 项）+ Flink SQL 方言单列非目标（编译器不解析）」，与设计文档 `sql-subset-and-semantics.md` §4a 形态列逐项相符；`check-doc-links --strict` exit 0（0 errors）。一审 MAJ-1/MIN-1/MIN-3 修复维持到位，全文无 `逐项有`/`not-implemented` 残留。WI18 解锁，收口动作清单见文末「三轮点核」章节。**

---

## 0. 裁定摘要

WI18 的证据面（测试）与登记面（路由/锚点）成立：TestStreamSqlEntryE2E 以 classpath `.stream.xml` 资源经 `DslModelParser.parseFromResource → StreamModelDslBuilder.build → execute → sink` 端到端跑通（隔离实跑 1/1 绿、全量 71/71 绿），与 TestSqlModelDeclarationE2E 的内联字符串形态区分真实（解析入口不同：`parseFromResource` vs `XNode.parse`）；断言判别力有反事实锚（冷 sink 违反即 8 行 vs 4 行断言红；缺列别名即 `a=null` 断言红）。`<sql>` 元素在 base stream.xdef（`sinkBean="!string"` + schemas + source），D8 五选项裁定与实现一致。但内容面抽查发现 §1/§4 的 SPI 缺失错误码与 live 相反（Major），§3 的 INTERVAL 单位词表与 grammar 相反（Minor）、§2 blanket「逐项有 fail-fast」对 §4a #3/#4 行过度概括（Minor）、§5 编程入口签名简化失实（Minor）。四项全部落在唯一交付文档内，属文档内容错误而非行为缺陷，修复成本低；按审计授权判定 FAIL，翻转待修复后复核。

## 1. TestStreamSqlEntryE2E（审计项 1）——PASS

- **存在且隔离实跑绿**：`nop-stream/nop-stream-sql/src/test/java/io/nop/stream/sql/compile/TestStreamSqlEntryE2E.java`（1 个 `@Test`）。隔离实跑：`./mvnw test -pl nop-stream/nop-stream-sql -am -Dtest=TestStreamSqlEntryE2E -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 1, Failures: 0, Errors: 0`，exit 0（`_tmp/audit-wi18/test-entry-isolated3.log`）。首次以 `-DfailIfNoTests=false` 跑因该参数对上游模块不生效而失败（nop-api-core 无匹配测试报错），属审计侧调用参数问题非产品问题，已换正确参数复跑。
- **资源驱动区分真实（非同形换名）**：EntryE2E 从真实 classpath 资源 `_vfs/nop/stream/sql/test/sql-entry.stream.xml`（13 行用户形态模型文件，含 schema 三列 + CDATA SQL）经 `new DslModelParser().parseFromResource(new ClassPathResource(...))` 解析；TestSqlModelDeclarationE2E 经 `XNode.parse(modelXml)` 内联字符串解析。两条不同解析通路，容器名亦区分（`sql-entry-e2e` vs `sql-declaration-e2e`）——「资源文件驱动 = 用户真实形态」的声称成立。
- **判别力反事实**：
  - 冷 sink：`SqlTestSink` 为实例级 `synchronizedList`（非 static），bean 默认 scope 每容器单例，测试类各建新容器 → 每类冷实例。反事实成立——若 sink 带宿留数据，排序后行数 8 ≠ 4，`assertEquals` 红。
  - 错列/缺别名：行映射读 `m.get("item")/m.get("total")`，编译产物列名不符即 `a=null ≠ a=4` 断言红；行集全等断言（排序后恰 `[a=4, b=2, b=4, c=1]`）同时判别行缺失/多行/总值错。
  - 入口断言：`assertNotNull(model.getSql())` 钉资源文件确实携带 `<sql>`（xdef 解析成功），防止空模型假绿。
- 全量内该类同样 1/1 绿（`_tmp/audit-wi18/test-sql-full.log` :505175）。

## 2. 入口具名可调用（审计项 2）——PASS

- **xdef 元素在案**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef` :106-111——顶层 `<sql sinkBean="!string" xdef:name="StreamSqlModel">`，`<schemas>/<field name type/>` + `<source>string</source>`，带 WI17 裁定注释（模型级生成器/唯一内容/sinkBean 派生 sink）。
- **D8 选项核对**：`ai-dev/design/nop-stream/sql-compiler-contract.md` §2.1 五选项全集——`<sql>` xdef 元素胜出（roadmap 建议项）；`.sql` 文件加 bean / Java API / CLI / GraphQL 四项拒绝，理由逐条在案。落地形态与 §2.2「WI17 落地形态」补记一致（`<source>` CDATA 而非属性内嵌/文件引用的早期表述已被该补记显式修订）。
- roadmap WI18 完成判定「D8 选定入口具名可调用；TestStreamSqlEntryE2E 从 SQL 文本到 sink 输出端到端跑通」——SQL 文本经入口编译、执行、落 sink 断言，全链实证 ✓。

## 3. 文档面逐条对照 live（审计项 3）——FAIL（发现集中区）

`docs-for-ai/03-modules/nop-stream-sql.md`（51 行）存在且结构完整；登记面成立（见 §3b）。逐条对照结果：

**与 live 一致项（抽查核过）**：
- 受管类型九名闭集（doc §1 L27）↔ `StreamSqlCompiler.MANAGED_TYPES`（:89-90，`string/int/bigint/smallint/tinyint/float/double/boolean/bytes`）逐名一致；集合外 `nop.err.stream.invalid-arg` fail-fast ✓（`validateSchema` :957-964）。
- sinkBean 契约、`<sql>` 唯一内容并存 fail-fast（doc §1 L29）↔ `StreamModelDslBuilder.expandSqlModel` :262-272 ✓；FROM 表名即 bean 名（r2 B2）✓（`sql-compile.beans.xml` 按表名注册 stub 闭合两端）。
- 展开机制：builder 首步经 `ISqlStreamCompiler` SPI 编译替换父模型（doc §1 L30 前半）↔ `expandSqlModel()` 为 `build()` 预处理、回读后全量替换 + `setSql(null)` ✓；app-beans 自动注册 ✓（`app-sql-compiler.beans.xml` + `StreamSqlCompilerProvider`）。
- 纳入面（doc §2）：五聚合 + `COUNT(*)` 无参形态（`StreamSqlAggregations` 仅 count `allowNoArg=true`）✓；GROUP BY/TUMBLE/双流四型等值 join/UNION ALL ✓。
- 不支持清单逐项（ORDER BY/LIMIT→`nop.err.eql.dialect-not-support-feature`（:232/:235）；非等值 join（:521-526）；DATE/TIMESTAMP/DECIMAL→invalid-arg；DISTINCT 聚合（:651）；子集外表达式 default-reject；FULL 窗口 join（:449 TUMBLE+join fail-fast，非窗口 FULL 不受限）；HAVING/CTE/INTERSECT/EXCEPT/SELECT DISTINCT/SELECT */全局聚合（:275-284）default-reject）——与 live 逐一对应 ✓。
- last-value-wins 终值语义标注（doc §3 L40）↔ D1 口径与 `SqlRowAggregateOps`/`StreamReduceOperator` javadoc ✓；join 语义（doc §3 L42：匹配即时发射/outer 补齐在 watermark/寿命止于 watermark 有界状态）↔ `EquiJoinOperator` javadoc :35-45 与实现（MapState 缓冲 + `processWatermark` 补齐修剪）✓。
- 编程入口存在性（doc §5）：`StreamSqlCompiler`（compile 包）/`StreamSqlExprCompiler`/`StreamSqlAggregations`（eval 包，五聚合 id 目录）类均真实存在 ✓。
- 错误码表其余项（编译期 invalid-arg、dialect-not-support-feature、invalid-interval-value、声明面 ref-unknown/invalid-arg）与 live 一致 ✓；零新增码成立。

**发现（4 项，全部在本文件内，行号为 live 行号）**：

- **MAJ-1（错误码契约失实，两处）**：doc §1 L30 与 §4 L46 声称 classpath 无 nop-stream-sql（SPI 缺失）时 fail-fast 码为 **`nop.err.stream.not-implemented`**。live 相反：`StreamModelDslBuilder.noSqlProvider`（:316-323）抛 **`ERR_STREAM_INVALID_ARG`（`nop.err.stream.invalid-arg`）**——容器未初始化与 `tryGetBeanByType` 为 null 两分支同码（:262-268 区段均为 INVALID_ARG；builder 内 NOT_IMPLEMENTED 各使用点 :388-424/:822-842/:927-947 全部属其他不支持注册表/sink-source 配置，无一在 `<sql>` 通路）。反证闭合：flow 钉码测试 `TestSqlModelExpansion.noProviderFailsFastNamingDependency` :122 显式 `assertEquals("nop.err.stream.invalid-arg", ...)`。即：错误文案点名 nop-stream-sql 正确，错误码被文档写反。用户按文档配告警/重试/异常分流将以 `not-implemented` 匹配而永不命中。**本轮 FAIL 的直接原因。**
- **MIN-1（INTERVAL 单位词表失实）**：doc §3 L41「间隔支持 ms/s/m/min 等字面单位」。live：SQL INTERVAL 单位为 `SqlIntervalUnit` 枚举词 `MICROSECOND/SECOND/MINUTE/HOUR/DAY/WEEK`（`EqlParseHelper.intervalDurationMillis` :83-101——MICROSECOND 整毫秒倍数限定，SECOND/MINUTE/HOUR/DAY/WEEK 固定换算，MONTH/QUARTER/YEAR fail-fast）。`ms/s/m/min` 均非合法单位词——按文档书写 `INTERVAL 5 min` 将直接 parse 失败。与设计文档 §4b「固定单位 SECOND/MINUTE/HOUR/DAY/WEEK + MICROSECOND 整毫秒限定」亦不一致。
- **MIN-2（blanket 声明过度概括）**：doc §2 L36「不支持（逐项有 fail-fast，§4a）」。其引用的设计文档 §4a 自身对 #3 retract/CDC 的「形态」为语义降级标注（D1 终值语义、无 retract 发射路径）、#4 Flink 方言为「非目标，编译器不解析」——两者均非显式 fail-fast。清单本身与 live 相符，仅括注以偏概全。
- **MIN-3（编程入口签名简化失实）**：doc §5 L50 写 `StreamSqlCompiler.compile(sqlText)`。live 唯一签名 `compile(SourceLocation loc, String sql, Map<String,String> schema, String sinkBean)`（:111-112，四参，`sql`/`sinkBean` 空白即 fail-fast、schema 受管闭集校验）——模块内编程入口文档按现文调用不可编译。

**OBS-1（不构成发现）**：doc 头部自称上位文档 `03-modules/nop-stream-user-guide.md`，但 user-guide 与 owner doc `nop-stream.md` 均无指向本文件的回链。plan In Scope 仅要求 INDEX + source-anchors 登记（已成立），不构成违规；建议归 WI24 docs 收口顺带补一行路由。

### 3b. 登记面（审计项 3b）——PASS

- `docs-for-ai/INDEX.md` :139 路由行在 nop-stream 路由表组内，描述与文档内容相符 ✓。
- `docs-for-ai/04-reference/source-anchors.md` :244-246 `STRM-SQL-001/002/003` 三锚点指向的 `StreamSqlCompiler.java`/`StreamSqlExprCompiler.java`/`EquiJoinOperator.java` 均真实存在，描述与实现相符 ✓。
- docs-for-ai→ai-dev 边界：本文件零 ai-dev 引用（grep 证）；`check-doc-links --strict` 全仓 0 错误 ✓。

## 4. 实跑（审计项 4）——PASS（计数以实跑为准）

| 范围 | 实跑结果 | 声称 | 判定 |
|---|---|---|---|
| TestStreamSqlEntryE2E 隔离 | 1/1，0F/0E，exit 0 | 绿 | ✓ |
| nop-stream-sql 全量（`-pl nop-stream/nop-stream-sql -am`） | **71/71，0F/0E**，exit 0（6 E2E + TestStreamSqlCompiler 31 + eval 三类 34） | 71 绿 | ✓ |
| 上游 reactor（core/flow/runtime/eql/orm 链） | 全绿 exit 0（runtime 1232 含 10 skip 既有） | — | ✓ |

注：审计任务简报预估「71+1=72」，实际 **71** 与 plan/日志声称一致——WI17 收口时的反写 ON 测试钉已计入其 70 基线（wi17 audit 报告 69 为收口修复前读数），WI18 +1 入口 E2E = 71。声称与 live 相符，非退化。日志存 `_tmp/audit-wi18/`（gitignored）。

## 5. 门禁（审计项 5）——PASS

- `node ai-dev/tools/check-doc-links.mjs --strict` → **exit 0**（0 errors；3 warnings 为 nop-bytecode 旧计划既存，与 WI18 提交零关联，WI13 日志已备案同一组 warnings）。
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/26-wi18-sql-entry-and-docs.md --strict` → **exit 0**（非 completed 计划 11 项未勾仅警告——即 Phase 2 待审计态，符合时点）。

## 6. git 纪律与 plan/日志一致性（审计项 6）——PASS

- **working tree clean**（审计起止 `git status --porcelain` 空）；WI18 提交 `e4ae2a2d6a` 7 文件 +261/-0 与 plan In Scope 逐一对应（测试 + 资源 + 文档 + INDEX + anchors + 日志 + plan 自身），零生成物触碰、零探针残留（`_tmp/audit-wi18/` gitignored）。
- **plan 26 live 状态**：Phase 1 四项 + Exit Criteria 四项全勾、Status: completed；Phase 2 三项与 Closure Gates 5 项未勾（唯一例外「sql 模块全量零退化（71 绿）」已勾）+ Closure 占位符——与「待独立审计」时点严格一致；`check-plan-checklist --strict` exit 0 ✓。
- **roadmap 基线**：WI18 行（:269）`: todo` ✓（翻转前正确状态）；状态 token 计数 done=25/todo=15，与 WI17 收口日志「done=25，剩余 WI18 WI19 WI20 WI22 WI23 WI24」相符。
- **日志一致性**：`ai-dev/logs/2026/10-03.md` WI18 三段条目（L3-7）逐句对照 live 相符——含「71 绿（+1 入口 E2E）」计数勾稽（实测 71 ✓）与「ai-dev 边界违规 3 处即改即绿」的执行记录（现文件零 ai-dev 引用印证）。

## 7. 发现分级与处置路径

1 项 Major + 3 项 Minor + 1 项观察，全部为 `docs-for-ai/03-modules/nop-stream-sql.md` 单文件内容修正，无代码改动：

- **MAJ-1（必修，阻断翻转）**：L30 与 L46 的 `nop.err.stream.not-implemented` → 改为 **`nop.err.stream.invalid-arg`**（保留「错误文案点名模块」表述——live 消息确含 `io.github.entropy-cloud:nop-stream-sql` 坐标）。
- **MIN-1（建议随修复一并改）**：L41「间隔支持 ms/s/m/min 等字面单位」→ 改为「固定单位 `SECOND/MINUTE/HOUR/DAY/WEEK` + `MICROSECOND`（须整毫秒倍数）」。
- **MIN-2**：L36 括注「逐项有 fail-fast，§4a」→ 改为如实分层（如「多数项有显式 fail-fast；retract/CDC 为 D1 终值语义降级、Flink 方言为非目标——见 §4a 形态列」）。
- **MIN-3**：L50 签名改为 `StreamSqlCompiler.compile(loc, sqlText, schema, sinkBean)`（并注明 schema/sinkBean 必填语义）。
- **OBS-1**：`nop-stream-user-guide.md`/`nop-stream.md` 回链一行，归 WI24 收口，不阻断。

## 8. 结论与翻转解锁条件

**最终裁定：FAIL。** WI18 完成判定的三个成分中：入口具名可调用 ✓、TestStreamSqlEntryE2E 端到端 ✓、文档写入并登记 ✓（三处登记齐备）——但「用户文档」作为本 WI 唯一内容交付物存在错误码契约失实（Major）与三处内容失实/过度概括（Minor），按「文档错误即 FAIL 项」授权判 FAIL。roadmap WI18 翻转（`todo` → `done`）与 plan 26 → `completed` 在修复并复核前不得执行。

**修复后解锁动作清单（由实现侧执行，随后提请第二轮轻量复核——范围仅限本文档 diff）**：

1. 按第 7 节修复 `docs-for-ai/03-modules/nop-stream-sql.md` 四处（MAJ-1 两行 + MIN-1/2/3 各一处）；同文件内编辑，`check-doc-links --strict` 复跑须 exit 0。
2. 第二轮复核（fresh session）：仅核四点修复与 live 一致 + 门禁双 0，无需重跑全量（纯文档 diff；如求完备可复跑 sql 模块 71 绿）。
3. 复核 PASS 后执行 Phase 2 原清单：roadmap WI18 `todo` → `done`（括注单层一对：承载 plan + 两轮 audit）；解析器断言 items=31/milestones=7/WI18=done/done=26。
4. plan 26：Phase 2 勾选、Plan Status → `completed`、Closure Status Note/Reviewer/Evidence 回填（Evidence 引用本报告 + 第二轮复核记录）、Closure Gates 逐项勾选；`check-plan-checklist --strict` 与 `check-doc-links --strict` 双 exit 0。
5. OBS-1 回链归 WI24，不随本翻转。

---

### 附：审计探针与证据清单（`_tmp/audit-wi18/`，gitignored，保留供第二轮复核引用）

- `test-entry-isolated.log`（首跑，审计侧参数失误 exit 1，备案）、`test-entry-isolated2.log`（surefire:test 单目标 jacoco agent 缺失 fork 崩溃，调用方式伪影，备案）、`test-entry-isolated3.log`（**有效隔离证据**：`Running io.nop.stream.sql.compile.TestStreamSqlEntryE2E` → `Tests run: 1, Failures: 0, Errors: 0`，exit 0）、`test-sql-full.log`（sql 模块全量 71/71 + 上游 reactor，exit 0）。
- 关键实读锚：`stream.xdef` :106-111；`StreamSqlCompiler.java` :89-90（MANAGED_TYPES）/:111（签名）/:232-284/:449/:521-526/:651/:957-964（fail-fast 面）；`StreamModelDslBuilder.java` :262-323（expandSqlModel + noSqlProvider 用 INVALID_ARG）/:388-424（NOT_IMPLEMENTED 属其他注册表）；`TestSqlModelExpansion.java` :122（invalid-arg 钉码反证）；`EqlParseHelper.java` :68-101 + `SqlIntervalUnit.java`（单位枚举）；`EquiJoinOperator.java` :35-45（join 语义）；`TestSqlModelDeclarationE2E.java`（内联形态对照）；`SqlTestSink.java`（实例级冷 sink）。

---

## 二轮轻量复核（2026-10-03，fresh session，独立 auditor）——FAIL（MIN-2 未修复）

- **范围**：仅一审四发现（MAJ-1/MIN-1/MIN-2/MIN-3）的修复核验 + 文档与 live 抽查 + 双门禁复跑；不重审一审已 PASS 项。
- **修复载体**：工作树未提交 diff（HEAD `e4ae2a2d6a` → 工作树），`docs-for-ai/03-modules/nop-stream-sql.md` 单文件 4 hunk（L30/L41/L46/L50）。修复时点除该文档外工作树无其他改动（`git status` 仅本文档报告文件新增）。

### R2-1. 逐项裁定

| 发现 | 裁定 | live 依据 |
|---|---|---|
| MAJ-1 | **修复到位 ✓** | 文档 L30/L46 两处均改为 `nop.err.stream.invalid-arg` 并保留「文案点名 nop-stream-sql 模块」。live：`StreamModelDslBuilder.noSqlProvider` 抛 `ERR_STREAM_INVALID_ARG`，detail 含 `io.github.entropy-cloud:nop-stream-sql`（容器未初始化与 `tryGetBeanByType` 为 null 两分支同码）；反证闭合 `TestSqlModelExpansion.noProviderFailsFastNamingDependency`（:113-124）`assertEquals("nop.err.stream.invalid-arg", ...)` + message contains "nop-stream-sql"。全文 grep `not-implemented` 零残留（exit 1） |
| MIN-1 | **修复到位 ✓** | 文档 L41 改「间隔单位为 SECOND/MINUTE/HOUR/DAY/WEEK 与 MICROSECOND（整毫秒倍数），非正时长、非字面量与日历单位（MONTH/QUARTER/YEAR）fail-fast」。live：`EqlParseHelper.intervalDurationMillis`（:68-101）switch 逐支一致——MICROSECOND 须 `value % 1000 == 0`、五固定单位固定换算、default（MONTH/QUARTER/YEAR）fail-fast；非字面量/`value <= 0`/unit null 均 `invalidInterval`（`ERR_EQL_INVALID_INTERVAL_VALUE`，:25 导入在案）。`SqlIntervalUnit` 枚举全集（九值）下该合法子集核对成立 |
| MIN-2 | **未修复 ✗（本轮唯一阻断项）** | `git diff` 四 hunk 均不含 §2；`git show HEAD:` L36 与工作树 L36 **逐字相同**——括注「（逐项有 fail-fast，§4a）」原样保留，retract/CDC 与 Flink 方言仍在该 blanket 之下。设计文档 `ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4a 实读：#3 retract/CDC 形态列为「模型语义标注 last-value-wins，非 append-only 非 retract」（语义降级，无失败路径）、#4 Flink 方言「非目标（roadmap Non-Goals），编译器不解析」——两者均非显式 fail-fast，与一审裁定一致 |
| MIN-3 | **修复到位 ✓** | 文档 L50 改 `StreamSqlCompiler.compile(loc, sql, schema, sinkBean)`（四参必填：源位置/SQL 文本/schema 字段面/sink bean 名）。live：`StreamSqlCompiler.java` :111-112 `public static String compile(SourceLocation loc, String sql, Map<String, String> schema, String sinkBean)`，全类唯一 public static 方法（无重载）；`sql`/`sinkBean` 空白即 fail-fast（:113-118）实读在案 |

### R2-2. 抽查（无新失实）

- **sinkBean 契约**：`<sql>` 唯一内容并存 fail-fast（`expandSqlModel` :258-272，`ERR_STREAM_INVALID_ARG` "must be the only content"）；产物 sink 由 sinkBean 派生（compile javadoc + 产物自含 `<sink>`）✓
- **FROM 表名即 bean**：`sourceNode` = `mk(loc, "source", "id", id, "bean", beanName)`（:861-863），TUMBLE/base 两通路均传表名（:342/:385）；测试 stub `sql-compile.beans.xml` 按表名注册 SourceFunction 闭合两端（r2 B2）✓
- **九受管类型**：`MANAGED_TYPES`（:89-90）九名与文档 L27 逐字一致；集合外 `validateSchema` invalidArg ✓
- **UNION bag**：`compileUnion`（:193-208）仅放行 `UNION_ALL`（bag union 不去重注释在案），UNION DISTINCT `invalidArg` fail-fast ✓
- **§4 错误码表其余项**：`CompileErrors.invalidArg`→`ERR_STREAM_INVALID_ARG`、ORDER BY/LIMIT `dialectNotSupport`→`ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`、间隔 `invalidInterval`→`ERR_EQL_INVALID_INTERVAL_VALUE` 实读一致；§5 eval 两类（`StreamSqlExprCompiler`/`StreamSqlAggregations`）真实存在 ✓
- 修复 diff 未触及其他区域（4 hunk 之外零改动），无连带失实

**OBS-2（不构成发现，不阻断）**：L50「四参必填」中 schema 一项略严于 live——compile javadoc 明言 schema "may be null/empty (no schema declared)"、`validateSchema` 对 null 直接放行（:957-959）。「必填」按「调用点必须实参（无重载/无默认参）」读法成立且方向保守（从严标注不会致用户误操作失败）；建议归 WI24 docs 顺带精确为「schema 可空（不声明即无列面）」。

### R2-3. 双门禁复跑

| 门禁 | 实跑结果 | 判定 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | **0 errors**（3 warnings 为 nop-bytecode 旧计划既存，与一审同一组），exit 0 | ✓ |
| `./mvnw test -pl nop-stream/nop-stream-sql -am` | sql 模块 **71/71，0F/0E**（6 E2E + TestStreamSqlCompiler 31 + eval 三类 34：ExprCompiler 13/Aggregations 11/AggregatorFunctionResolver 10），上游 reactor 全绿（runtime 1232 含 10 skip 既存），exit 0 | ✓ |

日志存 `_tmp/audit-wi18-r2/test-sql-full.log`（gitignored）。

### R2-4. 二轮结论与解锁路径

**二轮裁定：FAIL（维持，1 Minor 未修复）。** MAJ-1（一审阻断项）与 MIN-1/MIN-3 修复到位且与 live 一致，双门禁绿；但 MIN-2 的 §2 括注原样未动，retract/CDC（语义降级）与 Flink 方言（非目标）仍被「逐项有 fail-fast」错误概括——文档失实仍在，roadmap WI18 翻转与 plan 26 → `completed` 继续阻断。

**最小成本解锁路径（由实现侧执行）**：

1. **改一行**：L36 括注「（逐项有 fail-fast，§4a）」→ 如实分层（一审 §7 MIN-2 给出的措辞即可，例：「多数项有显式 fail-fast；retract/CDC 为 D1 终值语义降级、Flink 方言为非目标——见 §4a 形态列」）。可顺带（可选，不强制）按 OBS-2 精确 schema 可空语义。
2. `check-doc-links --strict` 复跑 exit 0（一行文字改动，预期无链接影响）。
3. **第三轮点核（fresh session）**：仅核该行措辞与设计文档 §4a 分层一致 + doc-links 0 errors，无需重跑测试（纯文字 diff，本轮 71 绿已在案）；PASS 后按一审第 8 节动作清单第 3-5 条执行 roadmap 翻转、plan 26 收口与提交（提交须包含本审计报告文件——当前为 untracked）。

---

## 三轮点核（2026-10-03，fresh session，独立 auditor，与前两轮均非同一 session）——PASS

- **范围**：仅二轮唯一阻断项 MIN-2 的修复措辞（doc §2 L36）与设计文档 §4a 形态列的一致性 + 门禁 `check-doc-links --strict`；不重审一/二轮已 PASS 项，不重跑测试（二轮 71 绿已在案，本轮修复为纯文字 diff）。
- **修复载体**：工作树未提交 diff（HEAD `e4ae2a2d6a` → 工作树），`docs-for-ai/03-modules/nop-stream-sql.md` 单文件 5 hunk（L30/L36/L41/L46/L50 = 一审四修复 + 本轮 MIN-2，`git diff --stat` 5 insertions/5 deletions，无其他区域改动）；工作树另仅本审计报告文件 untracked。

### R3-1. L36 分层措辞对照 §4a（MIN-2）——修复到位 ✓

现文（工作树 L36）：「**不支持**——多数项构建期显式 fail-fast：全局 ORDER BY/LIMIT（`nop.err.eql.dialect-not-support-feature`）；非等值/范围 join；DATE/TIMESTAMP/DECIMAL 列绑定；DISTINCT 聚合；子集外表达式（CASE/CAST/正则/IN 子查询等）；FULL 窗口 join；HAVING/CTE/INTERSECT/EXCEPT（default-reject）；全局聚合（无 GROUP BY 的聚合）。两项性质不同：retract/CDC 为 D1 裁定的语义降级（last-value-wins 终值语义，非 fail-fast 项）；Flink SQL 方言为非目标（编译器不解析）。语义边界：UNION 为 bag union 不去重（UNION DISTINCT fail-fast）。」

逐项对照 `ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4a「当前 fail-fast 形态」列：

| §4a # | 不支持项 | §4a 形态 | doc L36 归层 | 判定 |
|---|---|---|---|---|
| 1 | 全局 ORDER BY / LIMIT | fail-fast `nop.err.eql.dialect-not-support-feature` 或对应 stream 码 | fail-fast 层，且括注同码（一审 live 核过 `StreamSqlCompiler` :232/:235） | ✓ |
| 2 | 非等值 / 范围 join | fail-fast invalid-arg（validateJoinDeclarations） | fail-fast 层 | ✓ |
| 5 | DATE / TIMESTAMP / DECIMAL 列绑定 | fail-fast invalid-arg（受管类型闭集） | fail-fast 层 | ✓ |
| 6 | DISTINCT 聚合 | fail-fast invalid-arg | fail-fast 层 | ✓ |
| 7 | 标量子集外表达式（正则/CASE/CAST/IN 子查询等） | fail-fast（分派 default 分支） | fail-fast 层 | ✓ |
| 8 | FULL 窗口 join | fail-fast invalid-arg | fail-fast 层 | ✓ |
| 3 | retract / CDC | **D1=(a) 终值语义降级**——模型语义标注 last-value-wins，非 append-only 非 retract，无失败路径 | 单列「D1 裁定的语义降级（last-value-wins 终值语义，**非 fail-fast 项**）」 | ✓ |
| 4 | Flink SQL 方言兼容 | **非目标**（roadmap Non-Goals），编译器不解析 | 单列「非目标（编译器不解析）」 | ✓ |
| 9 | CEP 级复杂编排 | 不在本 roadmap（边界项，非 SQL 特性） | 未列——§4a 自身归 roadmap 边界而非编译器行为，doc 不列不构成失实 | ✓（不适用） |

- 原 blanket 括注「（逐项有 fail-fast，§4a）」已删除，`grep -rn "逐项有" docs-for-ai/` 零命中（exit 1）——blanket 概括不复存在；「多数项」限定与 fail-fast 层实际成员数相符（§4a 九项中六项 + doc 另两项经一/二轮 live 核实的 default-reject/全局聚合/UNION DISTINCT 注，retract/CDC 与 Flink 方言两项明确排除在外），措辞如实。
- fail-fast 层内额外两项（HAVING/CTE/INTERSECT/EXCEPT default-reject、全局聚合）一审已对 live 核过（`StreamSqlCompiler` :275-284）；UNION bag 边界注二轮已对 live 核过（`compileUnion` :193-208 仅放行 UNION_ALL）——本轮改写未引入任何新的未验证声明。
- 附带反证：`grep -n "not-implemented" docs-for-ai/03-modules/nop-stream-sql.md` 零命中（exit 1），一审 MAJ-1 修复维持无回退。

### R3-2. 门禁

| 门禁 | 实跑结果 | 判定 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | **0 errors，exit 0**（3398 files / 39873 refs；3 warnings 为 nop-bytecode 旧计划既存，三轮同一组，与 WI18 零关联） | ✓ |

### R3-3. 三轮裁定：PASS

MIN-2 修复到位：L36 分层措辞与设计文档 §4a 形态列逐项如实对应，retract/CDC 与 Flink 方言不再被概括为 fail-fast 项。一审 MAJ-1/MIN-1/MIN-3 修复与二轮复核结论维持到位，双门禁绿（本轮 doc-links exit 0；测试面二轮 71/71 已在案，纯文字 diff 无需重跑）。三轮点核无新发现。**WI18 解锁：roadmap WI18 翻转与 plan 26 收口可以执行。**

**收口动作清单（实现侧按序执行，均有一/二/三轮在案依据）**：

1. **roadmap 翻转**：`ai-dev/backlog/nop-stream-sql-roadmap.md` WI18 行 `: todo` → `: done`（括注单层一对：承载 plan + 本审计报告三轮记录），同步更新文末解析器断言 items=31 / milestones=7 / WI18=done / done=26。
2. **plan 26 收口**：`ai-dev/plans/nop-stream-sql/26-wi18-sql-entry-and-docs.md`——Phase 2 三项勾选、Closure Gates 逐项勾选、Plan Status → `completed`、Closure Status Note/Reviewer/Evidence 回填（Evidence 引用本报告，含三轮点核章节）；随后 `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/26-wi18-sql-entry-and-docs.md --strict` 与 `node ai-dev/tools/check-doc-links.mjs --strict` 双 exit 0 复跑确认。
3. **日志**：`ai-dev/logs/2026/10-03.md` 追加三轮点核 PASS 与 WI18 收口条目。
4. **提交（单 commit）**：须包含 (a) `docs-for-ai/03-modules/nop-stream-sql.md`（5 hunk 修复，当前为工作树未提交 diff）；(b) roadmap；(c) plan 26；(d) **本审计报告文件 `ai-dev/audits/nop-stream-sql/wi18-closure-audit.md`（当前 untracked，漏提则三轮审计证据缺失、文本一致性断裂）**；(e) 当日日志。提交后 `git status --porcelain` 须为空。
5. OBS-1（user-guide/nop-stream.md 回链）与 OBS-2（schema 可空语义精确化）均归 WI24 docs 收口，不随本翻转、不阻断。
