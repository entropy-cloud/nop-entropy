# WI20 Closure Audit——28-wi20-dual-target-consistency.md

- Audit 日期：2026-10-03（单轮独立 audit）
- Auditor：独立子 agent（fresh session，与实现者非同一 session/task_id；全部结论来自 live repo 实读/实跑与自建探针，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：HEAD `199bcf4f61`（分支 add-stream-sql，审计起止工作树 clean；WI20 提交 8 文件 +524/-1——三测试类 + AstToEql/AstToSql 两生成器 + docs-for-ai 标注 + plan + 日志；零 `_`-前缀生成物触碰，两生成器为手写源文件非模板产物）
- **最终裁定：PASS——2 Minor 收口必改项（同一根因的 D1 数值叙事失实：plan/日志/测试 javadoc 三处「a=4、三次 emit」与 live 实测「终态 a=-1、a 四次 emit」不符；加一条 plan 未记三测试类拆分的措辞对齐。均随 Phase 2 收口一并执行，不阻断翻转）。** roadmap WI20 完成判定成分全部实证成立：同一查询两路 golden（过滤/持续聚合双路隔离实跑绿 + 独立探针双侧对拍）、AstToSqlGenerator 产出 RDBMS SQL 并 H2 实跑对照（探针复现）、其余方言按 D15（标注在案、未实跑）、W2 与 D1 语义差异显式标注（docs-for-ai 标注与 live 一致）。

---

## 0. 裁定摘要

WI20 的交付物成立且判别力充分。三个测试类隔离实跑 1+1+2 全绿；审计侧自建两组独立探针（不经由被审计测试本身）在 live 上钉死双侧事实：RDBMS 侧——过滤查询渲染为干净 RDBMS SQL 并在 H2 七记录 fixture 上返回恰 5 行（`a=1,b=2,a=3,b=4,c=1`，NULL 与 -5 被三值逻辑排除）、聚合查询 H2 终态 `a=-1,b=6,c=1`、TUMBLE 查询渲染**原样回显** `TUMBLE(orders.ts, INTERVAL  5  SECOND)` 且两 resolve 访问器均抛 `nop.err.eql.table-source-not-resolved`；流路侧——持续聚合 emit 轨迹 `a=1 b=2 a=1 a=4 b=6 a=-1 c=1`（a 共 **4** 次 emit，null 记录重发运行值，终态 a=-1 与 H2 全等）。两通道 `visitSqlTumbleTableSource` 渲染补齐为 loud 语义（verbatim 回显而非静默丢/拒绝），其反事实基线（visitor 默认实现只 visitChildren 不打印 = 静默丢表源）经代码实读确认。发现的 2 Minor 均为**叙述文本与 live 数值的不符**（非测试断言、非产品行为缺陷）：plan Goals 的「H2 分组和（a=4,b=6,c=1）」、baseline 的「a 三次 emit」、测试 javadoc 的「emitted three times (1, then null-skip, then 4)」、当日日志的「[a=4,b=6,c=1] 两侧全等 + key a 运行值三次 emit」——live 实测为 `a=-1`（1+3-5）与 4 次 emit（1,1,4,-1）。按 guide 规则 18/19（文本一致性），这些被勾选/将被勾选为收口判据的文本须在翻转前改齐；不改则 `completed` 态的 plan 将断言一组不存在的数值。

## 1. 三个测试类隔离实跑与判别力反事实（审计项 1）——PASS

**隔离实跑**（各一次独立 `./mvnw test -pl nop-stream/nop-stream-sql -am -Dtest=<类名> -Dsurefire.failIfNoSpecifiedTests=false`，exit 0）：

| 测试类 | 实跑 | 判定 |
|---|---|---|
| `TestDualTargetConsistency` | Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 | ✓ |
| `TestDualTargetFilterConsistency` | Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 | ✓ |
| `TestDualTargetTumbleStreamOnly` | Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 | ✓ |

首跑曾以 `-DfailIfNoTests=false` 全军覆没（Maven 4 / surefire 3.2.2 要求 `surefire.failIfNoSpecifiedTests`，测试未执行即构建失败）——修正旗标后重跑，上述为有效证据（`_tmp/audit-wi20/iso-*.log`）。

### 1a. TestDualTargetConsistency（持续聚合双路）——PASS（独立探针双侧钉死）

- 断言两件：`emits.get("a") > 1`（D1 非 append-only 实证）+ `assertEquals(h2GroupSums(), finals)`（终态 per-group 全等，expected 由 live H2 实算）。
- **审计侧独立探针**（`_tmp/audit-wi20/ProbeStream.java`，自建 javac/java 经 `dependency:build-classpath` 类路径直跑，复刻测试的 IoC 装配后打印全量 sink 轨迹）：emit 轨迹 **`a=1 b=2 a=1 a=4 b=6 a=-1 c=1`，emits = {a=4, b=2, c=1}**——与 WI17 既有钉 `TestContinuousGroupByQueryE2E.java:82` 的序列逐元素一致（该测试注释明文「a:null-skipped→1」：null 记录**重发**运行值）。
- **判别力反事实**：若 `<reduce>` 语义被改为组终态一次性 emit（append-only 化），a 的 emit 计数从实测 4 跌到 1，`emits.get("a") > 1` 立即红；若持续聚合键分取回归 WI17 已修的 `event[0]` 初版缺陷（键按裸行分组），finals 与 H2 分组和必不等，`assertEquals` 红。终态可比命题（D1 非 append-only → 仅终态可比）由「multi-emit 断言 + 终态全等断言」双钉，无假绿路径。
- **接线核验（Anti-Hollow）**：`sql-compile.beans.xml` 注册 `orders`→`OrdersSourceFunction` 与 `testSink`→`SqlTestSink`（实例级 `synchronizedList` 冷 sink）；测试路径 `StreamSqlCompiler.compile → DslModelParser → StreamModelDslBuilder.build → env.execute → sink.getCollected()` 为从 SQL 文本入口到 sink 出口的端到端连通路径，非组件级空转。

### 1b. TestDualTargetFilterConsistency（过滤投影精确集相等）——PASS

- 断言两件：流路行集（排序后）== live H2 行集（排序后）；且硬钉精确集 `["a=1","a=3","b=2","b=4","c=1"]`。
- **审计侧独立探针**（`_tmp/audit-wi20/Probe.java` 的 H2 腿）：渲染 SQL `select item, amount from orders where amount > 0` 在 H2 七记录 fixture 上返回恰 `a=1,b=2,a=3,b=4,c=1` 五行——NULL 行与 -5 行双侧同排除（三值逻辑），与测试第二断言的精确集逐行一致。
- **判别力反事实**：若编译产物的三值守卫缺失/退化（NULL 行混入流路集）或 WHERE 未生效（-5 行在场），「对 live H2 行集相等」与「硬钉精确集」两条断言各自独立必红；无假绿路径。

### 1c. TestDualTargetTumbleStreamOnly（W2/T1 边界）——PASS

- 断言三面：流编译产物含 `tumbling-event-time` + `timestampsAndWatermarks`（window+assigner 在场，`:53-56`）；SQL 通道渲染含 `TUMBLE(orders.ts` + `INTERVAL`（loud 回显，`:69-72`）；`getSourceSelect()`/`getResolvedTableMeta()` 均 `assertThrows(NopException)`（语义 resolve 仍拒绝，`:80-85`）。
- **审计侧独立探针**（`_tmp/audit-wi20/Probe.java` TUMBLE 腿）：渲染文本 `from TUMBLE(orders.ts, INTERVAL  5  SECOND)` 原样回显（verbatim，非静默丢、非拒绝）；两访问器实测均抛 **`nop.err.eql.table-source-not-resolved`**。
- **判别力反事实**：(i) 若 `AstToSqlGenerator.visitSqlTumbleTableSource` 缺席（WI17 前状态——`EqlASTVisitor.java:471-476` 默认实现只 `visitChildren(node.getDecorators()/getInterval()/getAlias())` 不打印任何文本，即**静默丢表源**），FROM 子句渲染缺 TUMBLE 文本，第一条 assertTrue 红——该测试恰好钉住 WI20 打印补齐义务（m1）的落地；(ii) 若 stream-only fail-fast wrapper 被移除或改为静默返回，assertThrows 报 "expected NopException to be thrown, nothing was thrown" 红；(iii) 若流路窗口映射回归（TUMBLE 退化为 plain group-by），`tumbling-event-time` 在场断言红。

## 2. H2 fixture 与流 stub fixture 逐条对照（审计项 2）——PASS

`OrdersSourceFunction.FIXED_DATA`（`OrdersSourceFunction.java:32-43`）与三个测试中两处 H2 INSERT（`TestDualTargetConsistency.java:81-83`、`TestDualTargetFilterConsistency.java:69-71`，第三类按 T1 无 H2 对照，设计正确）逐条对照：

| # | FIXED_DATA | H2 INSERT | 一致 |
|---|---|---|---|
| 1 | (1000, a, 1) | (1000,'a',1) | ✓ |
| 2 | (2000, b, 2) | (2000,'b',2) | ✓ |
| 3 | (3000, a, null) | (3000,'a',NULL) | ✓ |
| 4 | (4000, a, 3) | (4000,'a',3) | ✓ |
| 5 | (11000, b, 4) | (11000,'b',4) | ✓ |
| 6 | (12000, a, -5) | (12000,'a',-5) | ✓ |
| 7 | (17000, c, 1) | (17000,'c',1) | ✓ |

七记录同序同值，含 NULL 与负数，三值逻辑天然对拍成立。

## 3. toSQL 渲染补齐（WI20 在案义务）与窗口通道回归（审计项 3）——PASS

- **loud 渲染语义核验**：`AstToSqlGenerator.java:230-241` 与 `AstToEqlGenerator.java:645-656` 的 `visitSqlTumbleTableSource` 均为 verbatim 打印（`TUMBLE(` + tableName + `.` + timeColumn + `, INTERVAL ` + expr + ` ` + unit.name() + `)` + alias），非静默丢、非拒绝——§1c 探针实证渲染文本原样回显。interval 经显式 `expr + unit` 渲染（interval 为字段非子节点，与日志「渲染 INTERVAL 需显式 expr+unit」记述相符）。语义 resolve 拒绝（WI17 wrapper）不被本次改动波及——探针实证两访问器仍抛 `nop.err.eql.table-source-not-resolved`。
- **反事实基线在案**：`EqlASTVisitor.java:133-135` dispatch 至默认 `visitSqlTumbleTableSource`（`:471-476`）只 visit children 不打印——生成器缺省即静默丢表源，本次补齐正是把两通道从该基线拉到 loud，测试钉住了这一差异。
- **W1/W2 窗口通道回归**：nop-orm-eql 全量实跑 **131/131，0F/0E/0S**，exit 0；其中 `TestEqlTumbleGrammarParse` **15/15 绿**（WI17 grammar 钉 + WI17 收口补的 plain TUMBLE interval 校验钉），窗口通道零退化。

## 4. 实跑与门禁（审计项 4/5）——PASS（计数以实跑为准）

| 范围 | 实跑结果 | 判定 |
|---|---|---|
| 三测试类隔离（各一次独立 mvn） | 1 + 1 + 2，全 0F/0E，exit 0 | ✓ |
| nop-stream-sql 全量（`-pl ... -am`） | **78/78，0F/0E/0S**，exit 0 | ✓ |
| nop-orm-eql 全量（`-pl ... -am`） | **131/131，0F/0E/0S**，exit 0 | ✓ |
| `node ai-dev/tools/check-doc-links.mjs --strict` | **No errors found，exit 0**（标注落档后跑） | ✓ |
| `node ai-dev/tools/check-plan-checklist.mjs .../28-...md --strict` | exit 0（1 条 non-completed 计划未勾项警告 = Phase 2 待审计态，符合时点） | ✓ |
| 共享解析器（`tools/mission-driver/src/roadmap-check.mjs` parseRoadmapMarkdown） | **items=31 / milestones=7 / done=27 / WI20=todo**，剩余 WI20 WI22 WI23 WI24 | ✓ |

- **计数勾稽与陈旧报告甄别**：sql 模块 surefire-reports 目录混有历史陈旧报告（`TestSpikeXplCapabilities` 07:02、`TempDeltaProbe`/`TempGenCompiled` 08:51-52、三个 10-02 探针类——均为已删除的 dev/spike 测试；未 `clean` 的累加），含 1 个 error 的陈旧记录。按文件时间戳（09:37 当前全量跑）甄别后**当前实跑恰 78 = 日志所称基线**，与 plan Exit Criteria「78 绿」相符；84/1-error 为陈旧混入，非本次跑结果。eql 131 与预期一致。
- **D15 口径核验**：默认 H2 实跑成立（两类用 H2 内存库实跑对照）；其余方言 opt-in 未跑且已在测试 javadoc（两处「D15 scope」段）与 docs-for-ai 标注中说明——「交付说明标注」义务满足。
- **文档语义边界标注与 live 一致**：`docs-for-ai/03-modules/nop-stream-sql.md` §2 标注句（WI20 提交唯一 docs 改动）逐点对照 live——「SQL 通道按原样回显 TUMBLE 语法（真实 RDBMS 解析即失败，预期边界而非缺陷）」= 探针实证的 verbatim 回显；「双目标一致性仅对非 TUMBLE 查询承诺精确集相等，聚合查询仅终态可比——D1 last-value-wins，流目标发射运行值」= §1a/§1b 实测。无 ai-dev 路径引用（doc-links 0 佐证无边界违规）。

## 5. git 纪律与 plan/日志一致性（审计项 6）——PASS（附 MIN-1/MIN-2/OBS）

- **working tree clean**：审计起止 `git status --porcelain` 空（探针与日志全部落在 gitignored 的 `_tmp/audit-wi20/`，`.gitignore:120`）；WI20 提交 `199bcf4f61` 8 文件与 plan In Scope 逐一对应（三测试类 + 两生成器 + docs 标注 + plan 自身 + 日志），零生成物、零探针入库。
- **plan 28 live 状态**：Plan Status `active`；Phase 1 `completed`、三 item + Exit Criteria 四项全勾且逐项与 live 相符（三查询测试在案且绿 / H2 设施七记录一致 / docs 标注在案 / 78 绿实测 / doc-links 0 实测 / 日志在案）；Phase 2 `planned`、三项与 Exit Criteria 未勾、Closure 占位符原样——与「Phase 1 完成、待独立审计」时点精确一致。
- **roadmap 基线**：WI20 行 `: todo`（翻转前正确状态，完成判定原文与本审计授权一致），解析器断言 31/7/27 见 §4。
- **日志一致性**：`ai-dev/logs/2026/10-03.md:3-8` WI20 条目的结构性内容与 live 相符（三测试类构成、执行即补 visitSqlTumbleTableSource 的 m1 记述、「sql 78 绿（+4）、eql 131 绿、doc-links 0」实测相符）——但终态数值与 emit 次数两处失实（MIN-1）。

### 发现（2 Minor 收口必改 + 2 观察不阻断）

- **MIN-1（D1 数值叙事三处失实，同根因，翻转前必改——guide 规则 18/19 文本一致性）**：live 实测（双侧探针钉死）为 **H2 终态 `a=-1,b=6,c=1`、流路 a 共 4 次 emit（值 1,1,4,-1，null 记录重发运行值）**；而 (a) plan 28 Goals 查询 2「H2 分组和（**a=4**,**b=6**,**c=1**）」、(b) plan 28 baseline「持续聚合运行序列（**a 三次 emit**……）」、(c) `TestDualTargetConsistency` javadoc「key 'a' is emitted **three times** (**1, then null-skip, then 4**)」、(d) 日志 10-03「终态 per-group **[a=4,b=6,c=1]** 两侧全等 + key a 运行值**三次 emit**」——a=4 系误取窗口内/中间运行值（1+3，漏算 12000 的 -5 记录），「三次」漏计 null 记录的重发。**测试断言与产品行为均不受影响**（断言不硬编码 4，双 live 相等成立；javadoc 的语义命题「multi-emit 非 append-only」为真），但按 guide 规则 19，「D1 标注」作为被勾选的收口判据承载了失实数值，若不改，`completed` 态的 plan/日志/javadoc 将断言一组 live 不存在的数值。改法：四处统一改为「终态 [a=-1,b=6,c=1]、a 四次 emit（1,1,4,-1，null 记录重发运行值）」；javadoc 属测试文件注释，建议随收口 commit 一并修正后复跑隔离测试确认仍绿。
- **MIN-2（plan 未记三测试类拆分，随收口顺带改齐）**：plan Goals/Phase 1 item 1 将三查询都挂在 `TestDualTargetConsistency` 单类名下；live 为三测试类（`TestDualTargetFilterConsistency` javadoc :45-46 与日志均记录拆分原因——本地 runner 一 JVM 一 execute 限制、plan 13 先例）。三个类对 plan 三查询的覆盖一一对应且判别力不减，但 plan 文本按现文与 live 结构不符。改法：plan Goals 或 Phase 1 item 1 补一句「三查询拆为三测试类（一 execute 一类限制，plan 13 先例）」。
- **OBS-1**：plan Closure Gates 第 1 条「三查询双路测试齐备且实跑绿」与第 3 条「docs-for-ai 标注落档」未勾，而 Phase 1 Exit Criteria 同事实已勾——系收口时点设计（closure gates 随 Phase 2 翻转勾选），非矛盾；收口时按改后文本勾选即可。
- **OBS-2**：sql 模块 `target/surefire-reports` 混有已删除 spike/probe 测试的陈旧报告（含 1 error 记录）——不计数入 live、不入库，仅提示后续实跑计数须按时间戳甄别或先 `clean`。

## 6. 结论与翻转解锁条件

**最终裁定：PASS。** WI20 完成判定成分全部实证：同一查询两路 golden 成立（过滤精确集相等 + 聚合终态相等 + D1 multi-emit 实证，隔离实跑绿 + 独立探针双侧复现）；AstToSqlGenerator 产出 RDBMS SQL 并在 H2 实跑对照成立（探针复现渲染文本与 H2 行集）；其余方言按 D15 标注未跑；W2/T1 stream-only 与 D1 非 append-only 语义差异显式标注在案且与 live 一致；toSQL 两通道 TUMBLE loud 渲染补齐成立且窗口通道回归零退化。2 Minor 均为文本层失实/缺记，随 Phase 2 收口执行，不阻断翻转。

**收口动作清单（实现侧按序执行）**：

1. **数值叙事对齐（MIN-1）**：plan 28 Goals 查询 2 的「（a=4,b=6,c=1）」改「（a=-1,b=6,c=1）」、baseline「a 三次 emit」改「a 四次 emit（1,1,4,-1，null 记录重发运行值）」；`TestDualTargetConsistency` javadoc「three times (1, then null-skip, then 4)」改「four times (1, 1, 4, -1 — the null record re-emits the running value)」；日志 10-03 WI20 条目两处数值同步改齐。纯文字改动，javadoc 改后复跑 `TestDualTargetConsistency` 隔离测试确认仍绿，`check-doc-links --strict` 复跑须 exit 0。
2. **拆分说明补记（MIN-2）**：plan 28（Goals 或 Phase 1 item 1）补一句三测试类拆分及原因（一 execute 一类限制、plan 13 先例）。
3. **Phase 2 勾选**：独立 audit 项勾选并落 `ai-dev/audits/nop-stream-sql/wi20-closure-audit.md`（本报告）；其余两项随翻转执行。
4. **roadmap 翻转**：WI20 行 `: todo` → `: done`（括注单层一对：承载 plan 28 + 本审计报告）；共享解析器断言复跑须 items=31 / milestones=7 / WI20=done / done=28（M5 仍 todo——WI22/WI23 未齐）。
5. **plan 28 收口**：Plan Status → `completed`；Phase 2 三项与 Closure Gates 六项逐项勾选（第 1 条按 MIN-1 改后文本勾选）；Closure Status Note / Completed / Reviewer / Evidence 回填（Evidence 引用本报告与 `_tmp/audit-wi20/` 探针清单）；随后 `check-plan-checklist --strict` 与 `check-doc-links --strict` 双 exit 0 复跑确认。
6. **日志**：`ai-dev/logs/2026/10-03.md` 追加 WI20 closure audit PASS 与收口翻转条目（含 MIN-1/MIN-2 修正记录）。
7. **提交（单 commit）**：须包含 (a) plan 28（修正 + Phase 2 勾选 + Closure 回填）；(b) roadmap WI20 翻转；(c) **本审计报告 `ai-dev/audits/nop-stream-sql/wi20-closure-audit.md`（漏提则审计证据缺失、文本一致性断裂）**；(d) `TestDualTargetConsistency.java` javadoc 修正；(e) 当日日志。提交后 `git status --porcelain` 须为空。
8. 后续：WI22（指标与不变量钉入，deps 已齐）与 WI23（等 WI18 ✓）可并行 → WI24 收口；M5 随 Phase 5 全齐翻转。

---

### 附：审计探针与证据清单（`_tmp/audit-wi20/`，gitignored，保留供收口复核引用）

- `iso-TestDualTargetConsistency.log` / `iso-TestDualTargetFilterConsistency.log` / `iso-TestDualTargetTumbleStreamOnly.log`（三隔离跑，exit 0；有效计数见 surefire-reports 同名 .txt：1/1/2 全 0F/0E）、`full-stream-sql.log`（sql 全量 exit 0）、`full-eql.log`（eql 全量 exit 0）、`doc-links.log`（No errors found，exit 0）、`plan-checklist.log`（exit 0，1 警告符合时点）、`cp-build.log` + `cp.txt`（探针类路径）、`Probe.java` + `probe-out.txt`（RDBMS 侧独立探针：三查询渲染文本 + TUMBLE resolve 拒绝码 `nop.err.eql.table-source-not-resolved` ×2 + H2 过滤五行/聚合终态 a=-1,b=6,c=1）、`ProbeStream.java` + `probe-stream-out.txt`（流路侧独立探针：emit 轨迹 `a=1 b=2 a=1 a=4 b=6 a=-1 c=1`、emits {a=4,b=2,c=1}）。
- 关键实读锚：`TestDualTargetConsistency.java` :77-84/:113-145（fixture + 双断言）；`TestDualTargetFilterConsistency.java` :92-128（双断言 + 拆分 javadoc :45-46）；`TestDualTargetTumbleStreamOnly.java` :48-86（三面断言）；`OrdersSourceFunction.java` :32-43（七记录定数）；`AstToSqlGenerator.java` :230-241 / `AstToEqlGenerator.java` :645-656（loud 渲染）；`EqlASTVisitor.java` :471-476（visitor 默认静默丢基线）；`SqlTestSink.java`（实例级冷 sink）；`TestContinuousGroupByQueryE2E.java` :80-83（WI17 钉序列含 a:-1）；`sql-compile.beans.xml`（orders/testSink 接线）；docs-for-ai/03-modules/nop-stream-sql.md §2 标注句（WI20 提交 diff 核对）；roadmap :271（WI20 行）；plan 28 全文。
