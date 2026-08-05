# custom_sql 沙箱加固：DML/TCL 族拦截 + H2 文件读写族拦截（AR-04/AR-05）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Draft Review: R1 `ses_02c114f21ffewc1HT0ipT6ItL5`（1 Blocker：反例 `name='DELETE'` 与 tokenizer 不剥离字符串字面量的既有语义矛盾（javadoc :323-324 + `testUnionInsideStringLiteralBlockedFailClosed` 钉死）→ 反例改用无黑名单词形态、退出标准改为明确接受"关键字样字面量过度拦截 = 已文档化 fail-closed"；1 Major：H2 文件族缺 CSVREAD → 已补；4 Minor：R6.1 出处改为 `2026-08-05-2157-1`（原误引 0105-1 为 R6.4）、对齐范围扩为 KEYWORD_BLACKLIST 全量条目（含 RENAME/LOCK/UNLOCK）、grep 改 `rg -n`/`grep -rnE`、CTE-DML/FILE_READ 用例走 `judge()` 公开入口先例）。R2 `ses_02c05e700ffeHVZ7egbUCDitoU`（可执行，0 Blocker；1 Major：checkstyle 门禁在根 pom 仅存于 `-Pqa` profile → 已按 R6.1 惯例改 "checkstyle N/A"；4 Minor：javadoc :326-328 MERGE/REPLACE 未来时表述同步、INTO/OUTFILE/DUMPFILE 由既有序列覆盖的"对齐"措辞收敛、两个强制工具门禁入 Closure Gates、Phase 2 grep 扩至 DML/TCL 词——已并入）。consensus 达成。
> Source: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-04/AR-05）
> Related: 执行顺序 `{2}` of 3 — `{1}`（SSRF host 归一化）、`{3}`（查询/质量/导入正确性）无依赖关系，独立执行
> Mission: nop-metadata-audit-remediation

## Purpose

收口 custom_sql 质量规则沙箱的两个安全缺口：(a) 黑名单缺 DML/TCL 族（INSERT/UPDATE/DELETE/MERGE/REPLACE 等）——规则 SQL 可在外部数据源账户上修改数据（MySQL/PG 驱动"先执行后报错"语义使篡改生效）；(b) 黑名单缺 H2 文件读写族（FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/CSVREAD）——`SELECT FILE_READ('/etc/passwd')` 类规则可读应用宿主机任意文件并回显，与 javadoc "文件读写全覆盖" 声明直接矛盾。修复后沙箱**黑名单内容**与模块内兄弟校验器 `ExpressionMeasureValidator.KEYWORD_BLACKLIST` 对齐（注：两处 token 化机制不同——沙箱不剥离字符串字面量、兄弟校验器剥离——因此对齐的是**黑名单条目集合**，不是误伤语义）。

## Current Baseline

2026-08-06 live repo 核对：

- **MetaQualityRuleExecutor**（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java`）：`CUSTOM_SQL_FORBIDDEN_WORDS`（:69-77，token 级精确匹配）现含 DDL 族（DROP/TRUNCATE/ALTER/CREATE/GRANT/REVOKE）、过程调用族、PG 文件/目录族（PG_READ_FILE/PG_READ_BINARY_FILE/PG_LS_*/PG_STAT_FILE/COPY）、脚本导出族（RUNSCRIPT/SCRIPT/SYS_EXEC）、UNION/LOAD_FILE/INFORMATION_SCHEMA/SHUTDOWN——**无 INSERT/UPDATE/DELETE/MERGE/REPLACE，无 COMMIT/ROLLBACK/SAVEPOINT/SET/TRANSACTION**。`CUSTOM_SQL_FORBIDDEN_SEQUENCES`（多 token）覆盖 `INTO`+`OUTFILE` 等，无 `LOAD XML` 序列。`tokenizeSqlForSandbox`（:364-379）归一化：去注释、折叠空白、按非标识符字符分词——**不剥离字符串字面量**（`name='DELETE'` 中 `DELETE` 仍是 token；javadoc :323-324 明确"字符串字面量内含黑名单 token 同样被拒，属预期行为（宁可误拒不放过）"，测试 `testUnionInsideStringLiteralBlockedFailClosed` 钉死此语义）。
- **javadoc 声明**（:62-63）：声称 PostgreSQL 文件/目录访问族与 H2 RUNSCRIPT/SCRIPT 等"全覆盖"——实际缺 H2 `FILE_READ`（任意文件读，值直接作为规则结果回显）、`FILE_WRITE`、`BACKUP TO`、`CSVWRITE`、**`CSVREAD`**、`LOAD XML INFILE`。声明与实现不符。
- **执行路径**：`validateCustomSqlSandbox`（:331-354）token 校验后 `querySingleValue(conn, sql)`（:653-676）走 `conn.prepareStatement(sql).executeQuery()`——MySQL/PG 对 DML 先执行后报 "no result set"，数据修改已生效；质量规则在外部数据源账户上执行（javadoc 自述），只读账户之外即有篡改风险。
- **兄弟校验器基线**：`ExpressionMeasureValidator.KEYWORD_BLACKLIST`（`.../field/ExpressionMeasureValidator.java:63-76`）含 DML 族（INSERT/UPDATE/DELETE/MERGE/REPLACE）、TCL 族（COMMIT/ROLLBACK/SAVEPOINT/SET/TRANSACTION）、DDL 族（DROP/CREATE/ALTER/TRUNCATE/**RENAME**）、文件写入子句（INTO/OUTFILE/DUMPFILE）、**LOCK/UNLOCK** 等——本处遗漏与模块自身安全基线矛盾。
- **R6.1 修复面**（`2026-08-05-2157-1-r6-1-custom-sql-blacklist-and-expression-validator.md` 落地，closure 记录 23 词）：补 PG 文件/目录族与脚本导出族 6 项；DML 族与 H2 文件族未入。
- **测试现状**：`TestMetaQualityRuleExecutorCustomSqlSandbox`（`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestMetaQualityRuleExecutorCustomSqlSandbox.java`）覆盖既有黑名单条目（含 `testR61NewKeywordsBlockedViaJudgeEntry` 走 `judge()` 公开入口的先例、`testUnionInsideStringLiteralBlockedFailClosed` 钉死字面量误伤语义）；无 DELETE/UPDATE/CTE-DML/FILE_READ 用例。全仓 grep 无 FILE_READ/FILE_WRITE/CSVWRITE/CSVREAD 防护。
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → 923 tests / 0 failures（R6.6 收口口径；执行时以当前为准）。

## Goals

- custom_sql 沙箱拒绝全部 DML/TCL 语句族（INSERT/UPDATE/DELETE/MERGE/REPLACE + COMMIT/ROLLBACK/SAVEPOINT/SET/TRANSACTION + RENAME/LOCK/UNLOCK，**黑名单条目全量对齐 ExpressionMeasureValidator**），含 CTE 包装的 DML（`WITH ... AS (DELETE ...)`）
- 沙箱拒绝 H2 文件读写族（FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/**CSVREAD** 单 token + LOAD XML 序列）
- javadoc 安全声明与实现一致（修正"全覆盖"措辞或补全清单）
- 回归测试覆盖正反用例（合法 SELECT 不受影响；DML/文件族被拒且错误可诊断；**关键字样字符串字面量的过度拦截作为已文档化的 fail-closed 行为明确接受**，不尝试通过改 tokenizer 规避）

## Non-Goals

- 不改 custom_sql 的执行机制（仍走 querySingleValue，仅扩展校验层）
- 不处理其它方言/函数面（judgeRegex SKIP 判定为 P2-AR-11，已登记 backlog）
- 不修改 ExpressionMeasureValidator（仅对齐其基线）
- 不引入 SQL 解析器级校验（token 级黑名单维持模块既有架构）

## Scope

### In Scope

- `CUSTOM_SQL_FORBIDDEN_WORDS` 补 DML/TCL 族 + DDL-RENAME + LOCK/UNLOCK（INSERT/UPDATE/DELETE/MERGE/REPLACE/COMMIT/ROLLBACK/SAVEPOINT/SET/TRANSACTION/RENAME/LOCK/UNLOCK——**DML/TCL/RENAME/LOCK/UNLOCK 条目与 `ExpressionMeasureValidator.KEYWORD_BLACKLIST:63-76` 逐项对齐**；INTO/OUTFILE/DUMPFILE 已由既有序列（INTO+OUTFILE / INTO+DUMPFILE）覆盖，不重复加单 token）(Fix)
- `CUSTOM_SQL_FORBIDDEN_WORDS` 补 H2 文件族（FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/**CSVREAD**）(Fix)
- `CUSTOM_SQL_FORBIDDEN_SEQUENCES` 补 `LOAD XML` 序列（Fix）
- javadoc 安全声明修正（Fix）
- 回归测试：`TestMetaQualityRuleExecutorCustomSqlSandbox` 补正反用例（Fix）
- `ai-dev/logs/` 对应日期条目（Follow-up）

### Out Of Scope

- 其它 custom_sql 绕过面（注释/空白变体——既有 token 归一化已覆盖，审计确认无新绕过）
- 质量规则结果脱敏 / 日志降级（AR-16，P2 已登记 backlog）
- 方言级白名单重构

## Execution Plan

### Phase 1 - 黑名单扩展（DML/TCL + H2 文件族）+ 回归测试

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java` + `TestMetaQualityRuleExecutorCustomSqlSandbox.java`

- Item Types: `Fix | Proof`

- [x] **先写失败用例（Proof，red）**：`TestMetaQualityRuleExecutorCustomSqlSandbox` 补正反用例并确认正例当前失败（red）——修复前 focused 运行 **19 tests / 5 FAILURE**（testDmlStatementsBlocked / testCteWrappedDmlBlockedViaJudgeEntry / testTclAndRenameLockUnlockBlocked / testH2FileFamilyBlocked / testH2FileReadBlockedViaJudgeEntry 全红；keep-green 反例 + 既有 14 用例修复前已绿）——
  - **正（必须被拒）**：`DELETE FROM t`、`UPDATE t SET a=1`、`INSERT INTO t SELECT * FROM s`（外带写入形态）、`MERGE INTO t ...`、`REPLACE INTO t ...`、CTE 包装 DML（`WITH x AS (DELETE FROM t) SELECT 1`）、`SELECT FILE_READ('/etc/passwd')`、`SELECT FILE_WRITE('/tmp/x','data')`、`BACKUP TO '/tmp/b'`、`SELECT CSVWRITE(...)`、`SELECT * FROM CSVREAD('/etc/passwd')`、`LOAD XML INFILE '/tmp/x'`——CTE-DML 与 FILE_READ/CSVREAD 用例走 `judge()` 公开入口（沿 `testR61NewKeywordsBlockedViaJudgeEntry` 先例），sqlHash 参数提供接线证据
  - **反（必须放行）**：既有合法 SELECT 用例全部保持 green。**注意**：tokenizer 不剥离字符串字面量（javadoc :323-324 + `testUnionInsideStringLiteralBlockedFailClosed` 钉死的既有语义），因此反例不能用 `name='DELETE'` 这类"关键字样字面量"（修复后会被拒，属预期 fail-closed）——反例改用不含黑名单词的形态，如 `SELECT updated_at FROM t WHERE updated_at >= '2026-01-01'`、`SELECT COUNT(*) FROM ext_sql_t WHERE 1=0`（既有用例）
- [x] **补 DML/TCL + RENAME/LOCK/UNLOCK 族（Fix）**：`CUSTOM_SQL_FORBIDDEN_WORDS` 增加 INSERT/UPDATE/DELETE/MERGE/REPLACE/COMMIT/ROLLBACK/SAVEPOINT/SET/TRANSACTION/RENAME/LOCK/UNLOCK（逐项对照 `ExpressionMeasureValidator.KEYWORD_BLACKLIST:63-76` 全量条目；`SET`/`TRANSACTION` 加入后核对既有合法 SELECT 用例——既有测试 SQL（`SELECT COUNT(*) FROM ext_sql_t` / `SELECT id FROM ext_sql_t WHERE 1=0` 等）经核查不含上述词，不会回归）
- [x] **补 H2 文件族（Fix）**：增加 FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/CSVREAD；`CUSTOM_SQL_FORBIDDEN_SEQUENCES` 增加 `LOAD XML` 连续 token 序列
- [x] **javadoc 修正（Fix）**：更新类 javadoc 为与实际黑名单一致的清单表述（**两处**：:62-63 一带的"全覆盖"过度声明 → 逐项列出 DML/TCL 族 + PG 族 + H2 族 + 文件族；**:326-328 一带"如 MERGE/REPLACE 不在当前集合内"的未来时表述 → MERGE/REPLACE 入集后必须同步删除或改写**），同时保留/明确"字面量含关键字也误拒属预期行为"的既有说明（:323-324）
- [x] **判别性复核（Proof，green）**：全部正例被拒、反例放行；既有沙箱测试全绿（含 `testUnionInsideStringLiteralBlockedFailClosed` 不回归——它测的是既有 UNION 语义，本计划不动 tokenizer）；`TestMetaQualityRuleExecutorCustomSqlSandbox` 全量通过（focused 19/0，全量 949/0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 正反用例清单齐全且 red→green 有记录（先写用例、确认 red、修复后 green）
- [x] DML/TCL 族全部被拒（含 CTE-DML）；H2 文件族全部被拒（含 CSVREAD）；`LOAD XML` 被拒
- [x] 反例（合法 SELECT，不含黑名单词的形态）全绿；**关键字样字符串字面量的过度拦截按已文档化的 fail-closed 行为接受**（与 `testUnionInsideStringLiteralBlockedFailClosed` 语义一致，不通过改 tokenizer 规避）
- [x] javadoc 声明与实现一致（对照 live 黑名单逐项复核）
- [x] **无静默跳过**：被拒规则返回明确错误（既有拒绝语义 + ErrorCode），不是静默 SKIP
- [x] `No owner-doc update required`（沙箱行为收严属安全加固；如 `docs-for-ai/03-modules/nop-metadata.md` 质量规则章节描述了黑名单范围则同步一句，以 live 文档为准）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 全量回归 + 收口

Status: completed
Targets: 模块级验证

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）——**949 tests / 0 failures / 0 errors / 0 skipped**（基线 943 + 新增 6），重点核对 quality 域既有测试（`TestMetaQualityRuleExecutor*`、checkpoint/scorer 相关）无回归；两次 `-am` 偶遇 nop-stream 预存在 flaky（`TestRocksDBIncrementalRestoreAndBenchmark` 性能阈值 + `TestResultPartitionOverflowBypass` 线程时序断言），均单跑复绿（2/0、3/0），非本 plan 引入（仅触碰 nop-metadata 文件），按 R6.3 降级口径记录
- [x] `rg -n "FILE_READ|FILE_WRITE|CSVWRITE|CSVREAD|INSERT|UPDATE|DELETE|MERGE|REPLACE|COMMIT|ROLLBACK|SAVEPOINT|TRANSACTION|RENAME|LOCK|UNLOCK" nop-metadata/nop-metadata-service/src/main/java`（或 `grep -rnE`，macOS BSD grep 不支持 `\|` 转义分支）确认防护条目在生效位置——**至少确认 5 个代表词**（FILE_READ / CSVREAD / DELETE / MERGE / SET）在 `CUSTOM_SQL_FORBIDDEN_WORDS` 定义处（MetaQualityRuleExecutor.java:81-88）与 javadoc 两处（:63-69 黑名单 javadoc、:339-342 validateCustomSqlSandbox javadoc）三处一致（对照 live 代码复核接线）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 全量模块测试全绿（949/0）
- [x] 黑名单条目与 javadoc/测试三处一致（清单比对记录）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-04（custom_sql DML 篡改）已修复：DML/TCL 族被拒，含 CTE-DML
- [x] AR-05（H2 文件读写族）已修复：FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/CSVREAD/LOAD XML 被拒，javadoc 声明修正
- [x] 全部正反回归用例已落地且判别性有效（red 先于修复）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 无 owner-doc drift 残留（如模块文档描述黑名单则已同步）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）validateCustomSqlSandbox → 执行路径运行时调用链连通（被拒 SQL 确实无法到达 executeQuery），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"——根 pom 的 checkstyle 插件仅存在于 `-Pqa` profile）

## Deferred But Adjudicated

（无——本计划两项 finding 均为 confirmed live security defect，全部 in-scope 修复。）

## Non-Blocking Follow-ups

- AR-11~AR-23（2026-08-05-2157 审计 P2 批）已登记 `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md` `## Follow-up Backlog`，不属本计划范围

## Closure

Status Note: 两个 confirmed live security defect（AR-04 DML 篡改 / AR-05 H2 文件读写）全部 in-scope 修复落地：黑名单 18 词补入（DML 5 + TCL 5 + RENAME/LOCK/UNLOCK 3 + H2 文件族 5）+ LOAD XML 序列，javadoc 两处修正，6 个判别性测试 red→green 落地（CTE-DML/FILE_READ/CSVREAD 经 judge() 公开入口接线验证），全量 949/0 全绿，独立子 agent closure audit PASS。
Completed: 2026-08-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_02bc83e55ffe1zn9XNBx2AISEl`，独立 task_id，general agent）
- Evidence:
  - Phase 1 Exit Criteria（7 条）全部 PASS：正反用例 red→green 有记录（修复前 focused 19/5 全红实测）；DML/TCL 族（含 CTE-DML）、H2 文件族（含 CSVREAD）、LOAD XML 全部被拒（MetaQualityRuleExecutor.java:81-88 黑名单 + :95 LOAD XML 序列 + 测试 :238-383）；反例全绿且 fail-closed 语义保留（testUnionInsideStringLiteralBlockedFailClosed 不回归，tokenizer 未动）；javadoc 三处与 live 黑名单一致（:57-75 黑名单 javadoc 逐项列出、:338-343 validateCustomSqlSandbox javadoc 已无"MERGE/REPLACE 不在当前集合内"未来时表述、:335-336 fail-closed 说明保留）；无静默跳过（ERR_QUALITY_CUSTOM_SQL_BLOCKED + ruleKey/reason/sqlHash 参数，QualityErrors.java:11-15 三参一致，四个 throw 点 :352/:356/:361/:366 全真实）；`No owner-doc update required` 成立（docs-for-ai/03-modules/nop-metadata.md 仅 META-003 表格行 :158，无黑名单范围描述，grep 实证）；ai-dev/logs/2026/08-06.md 顶部条目已更新
  - Phase 2 Exit Criteria（3 条）全部 PASS：全量 `./mvnw test -pl nop-metadata -am -T 1C` → 949 tests / 0 failures / 0 errors / 0 skipped（基线 943 + 新增 6；两次 `-am` 偶遇 nop-stream 预存在 flaky——TestRocksDBIncrementalRestoreAndBenchmark 性能阈值 + TestResultPartitionOverflowBypass 线程时序断言——均单跑复绿 2/0、3/0，非本 plan 引入，按 R6.3 降级口径记录）；grep 实证 5 代表词（FILE_READ/CSVREAD/DELETE/MERGE/SET）在定义处（:81-88）与 javadoc 两处（:63-69、:339-342）三处一致；日志条目已更新
  - Closure Gates 全部 PASS：AR-04/AR-05 修复代码实证（独立审计第 2 项）；判别性有效（独立审计 fresh 重跑 focused 19/0）；无静默降级（Deferred 段为空，Follow-up 仅 AR-11~23 P2 backlog）；owner-doc 无 drift；独立子 agent closure audit READY_TO_CLOSE（12 项核查全 PASS，唯一 Minor 为执行收尾动作——commit/roadmap 翻转，已由执行者兑现）；Anti-Hollow（a）调用链连通——judge() → judgeCustomSql() :289 validateCustomSqlSandbox 先于 :295 querySingleValue（:670 executeQuery），被拒 SQL 无法到达执行，judgeCustomSql 仅 catch SQLException 不吞沙箱异常；（b）无空方法体/静默跳过/no-op；check-plan-checklist --strict 退出码 0；scan-hollow-implementations --module nop-metadata --severity high 退出码 0（0 findings）；`./mvnw compile -pl nop-metadata -am` 通过；`./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` BUILD SUCCESS；checkstyle N/A（根 pom checkstyle 插件仅存在于 `-Pqa` profile，历史惯例）
  - 工具退出码：check-plan-checklist --strict 0 / check-doc-links --strict 0（0 errors，12 warnings 均为其它历史 plan 预存在）/ scan-hollow-implementations 0
  - Deferred 项分类检查：无 in-scope live defect 被降级（Deferred But Adjudicated 段 = 无）

Follow-up:

- no remaining plan-owned work（关闭时确认）
