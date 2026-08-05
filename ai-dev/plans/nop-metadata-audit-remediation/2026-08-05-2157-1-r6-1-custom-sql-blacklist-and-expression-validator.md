# R6-1 custom_sql 黑名单补全 + ExpressionMeasureValidator 死条目处理（P2-10/P2-13）

> Plan Status: completed
> Last Reviewed: 2026-08-05
> Draft Review: R1 `ses_02d9759daffedwfCZ7icUUHprD`（1 Blocker：测试入口矛盾——已统一为 judge() 公开入口 + null conn + TableReference 先例；2 Major：SCRIPT 向量被 CREATE 掩盖——已改 SCRIPT TO 向量、INTO 裁定与断言耦合——已改断言对裁定中立 + TRANSACTION 独立向量；4 Minor 已修）；R2 `ses_02d8c03d5ffe7LVYf5B0kdv6dh`（1 Major：INTO 断言集合 {OUTFILE,DUMPFILE} 与 INTO 加入时首命中矛盾——已统一为 {INTO,OUTFILE,DUMPFILE} 集合中立断言；4 Minor 已修：judge 9 参完整形如先例/ref+entityType 具体值/测试文件包路径/负例行号）；R3 `ses_02d83dcb6ffeDMbyqaA4CmXUXs`（1 Major 残留：:92 断言集合未含 INTO——已改为 {INTO,OUTFILE,DUMPFILE} 并说明 scanBlacklist token 流顺序首命中机制；Minor :26 行号已同步）。R3 明确"完成该行修改后即可直接执行"——修改已按原样落地，consensus 达成。
> Mission: nop-metadata-audit-remediation
> Work Item: MR6（R6.1）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR6 段 R6.1 行 + Follow-up Backlog P2-10/P2-13）、`ai-dev/audits/arm-index-nop-metadata.md`（§P2 MR6 裁决记录）
> Related: 执行顺序 `{1}` of 3 — R6.2（`2026-08-05-2157-2`）、R6.3（`2026-08-05-2157-3`）为同批后继；三计划文件域互不重叠（R6.1：quality/field；R6.2：quality/dispatch + connection；R6.3：entity + model），可独立执行；Deps 门禁（R6.0 done）已解除

## Purpose

按 MR6 R6.1 行收口两项 Backlog finding（2026-08-05 两轮审计登记，R6.0 live 复核提级）：

1. **P2-10**：custom_sql 沙箱黑名单（`MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS`）缺 6 个已知危险关键字，分词后成单 token 且不命中即原样执行——已确认的 SQL 注入绕过面，补全 + 绕过回归测试。
2. **P2-13**：`ExpressionMeasureValidator` 两个黑名单条目因分词机制永远无法命中（死条目），误导维护者以为该面已覆盖——修正（拆分为可命中条目）或删除并显式记录，回归测试。

两项均为安全类提级修复，目标状态：黑名单与分词机制一致、无死条目、绕过向量全部 fail-closed、回归测试钉死。

## Current Baseline

2026-08-05 live repo 核对（R6.0 裁决记录 + 本次复核）：

- **P2-10 绕过面（confirmed）**：`MetaQualityRuleExecutor.java:67-74` `CUSTOM_SQL_FORBIDDEN_WORDS` 当前含：UNION / LOAD_FILE / CALL / EXEC / EXECUTE / SHUTDOWN / DROP / TRUNCATE / ALTER / CREATE / GRANT / REVOKE / INFORMATION_SCHEMA / COPY / PG_READ_FILE / PG_LS_DIR / SYS_EXEC（17 条）。**缺**：`PG_READ_BINARY_FILE` / `RUNSCRIPT` / `PG_LS_LOGDIR` / `PG_LS_WALDIR` / `PG_STAT_FILE` / `SCRIPT`（6 条，审计报告 + R6.0 记录逐条列出）。分词实现 `tokenizeSqlForSandbox`（:359-374）按 `[^A-Za-z0-9_]+` 切分且**下划线保留**——上述 6 名均为单 token（如 `PG_READ_BINARY_FILE`），当前集合不命中即原样进入 PreparedStatement 执行（`judgeCustomSql` :260-309 直接执行 `sqlExpression`）；代码注释 :323 自认"需阶段性审查更新"。`;` 与 `/*!` 前置拒绝（:331-337）不覆盖该面。
- **P2-13 死条目（confirmed）**：`ExpressionMeasureValidator.java:63` `KEYWORD_BLACKLIST` 含 `"SET TRANSACTION"`（双 token 条目）——`scanBlacklist`（:469-489）对单 IDENTIFIER token 文本查 `KEYWORD_BLACKLIST` 集合（`KEYWORD_BLACKLIST.contains(upper)`，:474），tokenizer（:404-432 标识符分支）把 `SET` 与 `TRANSACTION` 切为两个独立 IDENTIFIER token，双 token 条目永不命中；`:79` `FUNCTION_BLACKLIST` 含 `"INTO OUTFILE"` / `"INTO DUMPFILE"`（双 token 条目）——FUNCTION_CALL token 需"word 紧跟 `(`"（:404-418）才产生，`INTO OUTFILE` 永远不满足，同样永不命中。
- **既有回归测试**：`TestMetaQualityRuleExecutorCustomSqlSandbox.java`（12 个 @Test，含 COPY/PG_READ_FILE/PG_LS_DIR/SYS_EXEC 正例 `testMissingKeywordsNowBlocked` :150-163）；`TestExpressionMeasureValidator.java`（24 个 @Test，含 DROP/SLEEP/PG_SLEEP/BENCHMARK/INSERT 正例与字符串字面量误伤负例 :156-163、标识符嵌入负例 :167-173）。沙箱测试入口事实：`validateCustomSqlSandbox` 为 **package-private**（:326），既有测试经 `testSandbox` 反射 helper（:209-230）调用；仓库另有 `TestMetaTableProfilerSecurity:207-220` 先例——`new MetaQualityRuleExecutor()` + 内联 `TableReference(Kind.EXTERNAL, ...)` + `judge(null, ref, ...)` 公开入口（null Connection 在校验后、触连前抛错前可达）。
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → nop-metadata 子树 **895 tests / 0 failures / 0 errors / 0 skipped**（service 894 + web 1，R6.0 收口口径）。

## Goals

- `CUSTOM_SQL_FORBIDDEN_WORDS` 补全 6 个缺项（PG_READ_BINARY_FILE / RUNSCRIPT / PG_LS_LOGDIR / PG_LS_WALDIR / PG_STAT_FILE / SCRIPT），与分词机制匹配，绕过向量全部 fail-closed
- `ExpressionMeasureValidator` 死条目修正：`SET TRANSACTION` 与 `INTO OUTFILE`/`INTO DUMPFILE` 不再以"永不命中"形式存在——拆分可命中单 token 条目或按声明原因删除（二选一，见 Phase 2 Decision），并补对应回归测试
- 两文件注释中的安全边界声明与实现一致（消除"黑名单覆盖了未覆盖的面"的误导）
- arm-index §P2 对应行终态（fixed）+ roadmap R6.1 行 → done

## Non-Goals

- 不扩展 custom_sql 黑名单到未来方言新关键字（:323 注释声明的边界保持；R6.1 只补已确认 6 项）
- 不修改 `judgeCustomSql` 执行语义、参数绑定通道、错误码体系（`ERR_QUALITY_CUSTOM_SQL_BLOCKED` 保持）
- 不重构 `ExpressionMeasureValidator` 分词器（只修正黑名单条目与其匹配机制的一致性）
- 不触碰 R6.2/R6.3 文件域（webhook 重定向、rawJdbcUrl、upsert/守卫）
- 不涉及 ORM 模型 / 生成管线（无 Protected Area 变更）

## Scope

### In Scope

- `MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS` 补 6 项 + 注释同步（Fix）
- `ExpressionMeasureValidator` 死条目修正（Decision + Fix）
- 两个测试文件的绕过回归测试（正例拒绝 + 不误伤既有负例）（Fix）
- arm-index §P2 + roadmap R6.1 行终态更新（Fix）
- `ai-dev/logs/2026/08-05.md`（或执行当日日志）更新（Follow-up）

### Out Of Scope

- P2-11/P2-12（R6.2）、AR-07/AR-08（R6.3）、P2-06/07/09（R6.4）、P2-01/02/04（R6.5）、R6.6 批量
- 黑名单"周期性审查"机制的建立（R6.6/Non-Blocking Follow-ups 候选）

## Execution Plan

### Phase 1 - custom_sql 黑名单补全 + 绕过回归测试

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java` + `TestMetaQualityRuleExecutorCustomSqlSandbox.java`

- Item Types: `Fix | Proof`

- [x] `CUSTOM_SQL_FORBIDDEN_WORDS`（:67-74）补 6 项：`PG_READ_BINARY_FILE` / `RUNSCRIPT` / `PG_LS_LOGDIR` / `PG_LS_WALDIR` / `PG_STAT_FILE` / `SCRIPT`（与既有 17 项同集合风格）
- [x] 类 javadoc / 注释（:57-66、:323）同步：黑名单条目与分词机制的覆盖语义一致，移除"阶段性审查"含糊表述中与已补面冲突的部分（保留未来新关键字声明）
- [x] **回归测试（Fix，Test-Mandated Feature Rule）**：`TestMetaQualityRuleExecutorCustomSqlSandbox` 补 6 个绕过正例——`SELECT pg_read_binary_file('/etc/passwd')`、`RUNSCRIPT FROM '/tmp/evil.sql'`、`SELECT PG_LS_LOGDIR()`、`SELECT PG_LS_WALDIR()`、`SELECT PG_STAT_FILE('/etc/passwd')`、`SCRIPT TO '/tmp/backup.sql'`（**不选 `SCRIPT 'CREATE TABLE ...'`——字符串内 CREATE 已触发既有黑名单，无法唯一钉住 SCRIPT 条目**）——全部断言抛 `ERR_QUALITY_CUSTOM_SQL_BLOCKED`；入口统一走 **`judge()` 公开入口**（沿 `TestMetaTableProfilerSecurity:207-220` 先例：`new MetaQualityRuleExecutor()` + 内联 `TableReference(Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", ...)` + 完整 9 参调用 `judge(null, ref, null, "custom_sql", "table", null, sql, null, null)`——**第 3 参 schemaPattern=null、第 4 参 ruleType="custom_sql"**，注意先例中参数顺序：conn, ref, schemaPattern, ruleType, entityType, paramsJson, sqlExpression, threshold, productName），null Connection——`judgeCustomSql` 在沙箱校验（:274）后、触连（:280 querySingleValue）前抛错，null conn 可达错误码；不依赖反射 helper（避免与既有测试入口模式冲突；既有 12 个用例保持原样不回归）
- [x] 端到端接线验证：断言测试确实经 `judge` → `judgeCustomSql` → `validateCustomSqlSandbox` 调用链（null conn 场景下错误码从沙箱校验分支抛出，非直接构造错误码），并保留既有反射路径用例作为入口覆盖

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 6 个缺项进入黑名单集合，`grep` 可复核（`CUSTOM_SQL_FORBIDDEN_WORDS` 含 23 项）
- [x] **端到端验证**：6 个绕过向量从 `judge` 入口（null conn）到 `ERR_QUALITY_CUSTOM_SQL_BLOCKED` 抛错链路全部成立（测试断言错误码 + 入口路径，`SCRIPT TO` 向量唯一钉住 SCRIPT 条目）
- [x] **无静默跳过**：补全后无已知缺项静默执行；新增条目在未命中时仍走既有显式抛错分支（无 catch-and-continue 引入）
- [x] 既有 12 个沙箱用例不回归；`./mvnw test -pl nop-metadata -T 1C` 相关测试类全绿
- [x] `No owner-doc update required`（docs-for-ai 无 custom_sql 黑名单清单细节章节，已核实；行为为安全加固收敛）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ExpressionMeasureValidator 死条目修正 + 回归测试

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/field/ExpressionMeasureValidator.java` + `TestExpressionMeasureValidator.java`

- Item Types: `Decision | Fix | Proof`

- [x] **死条目处置裁定（Decision）**：`"SET TRANSACTION"`（KEYWORD_BLACKLIST :63）与 `"INTO OUTFILE"`/`"INTO DUMPFILE"`（FUNCTION_BLACKLIST :79）二选一：
  - 路径 A（推荐）：拆分为可命中的单 token 条目——`SET` / `TRANSACTION` 加入 `KEYWORD_BLACKLIST`，`OUTFILE` / `DUMPFILE` 加入 `KEYWORD_BLACKLIST`（INTO 单独加入的误伤面执行时按 tokenizer 语义复核：若 `INTO` 单独加入无真实误伤面则一并加入并记录；**无论 INTO 是否加入，回归断言须对裁定中立——断言错误码 `ERR_AGGR_EXPRESSION_UNSAFE` + reason 命中目标集合 {SET, TRANSACTION} / {INTO, OUTFILE, DUMPFILE} 之一，不钉死具体关键字**，避免 INTO 加入后首命中顺序改变导致断言失败——scanBlacklist 按 token 流顺序遍历，INTO 加入时 `x INTO OUTFILE` 首命中 INTO，断言集合必须含 INTO）。安全方向：表达式上下文（SELECT 片段）中 SET/TRANSACTION/OUTFILE/DUMPFILE 均非合法列运算关键字，over-block 符合 validator"拒绝比放行安全"既定哲学（:34-36 注释）。
  - 路径 B（删除）：删除 3 个死条目并显式记录"该面由什么机制覆盖"——需核查下游 SQL 构造消费点（fragment 被包进聚合函数/HAVING 子句，无法构成语句级构造）：`AggregationHelper.aggSqlOf`（:60-74，调用点 :848）、`ExternalAggregationProcessor:102`、`EntityAggregationProcessor:251`、`EntityEntityJoinAggregationProcessor:160`、`MetaAggregationExecutor:223`（HAVING 替换）——核实后若确实无语句级构造路径，可在 Plan/arm-index 记录 watch-only 理由。
  - 裁定约束：不允许"删除且不记录理由"或"保留死条目"两种模糊态；裁定结果写入本 plan + arm-index §P2
- [x] 按裁定落地（Fix）：KEYWORD_BLACKLIST/FUNCTION_BLACKLIST 条目修正，注释（:52-68、:70-84）与实现一致
- [x] **回归测试（Fix）**：`TestExpressionMeasureValidator` 补——路径 A：`SET TRANSACTION ISOLATION LEVEL ...` 类表达式断言抛 `ERR_AGGR_EXPRESSION_UNSAFE`（reason 含 SET 或 TRANSACTION）、`x INTO OUTFILE '/tmp/f'` 断言抛 unsafe（**reason 命中集合 {INTO, OUTFILE, DUMPFILE} 之一**——INTO 未加入时 OUTFILE 首命中、INTO 加入时 INTO 首命中，断言对两种落点均成立）、**独立 `TRANSACTION ISOLATION ...`（不带 SET）向量唯一钉住 TRANSACTION 条目**（防 SET 先抛导致 TRANSACTION 从未被直接命中）；路径 B：显式断言"该面无测试覆盖"的替代验证（如对应负例不存在的说明注释 + watch-only 记录）。同时保留既有 24 个用例不回归（尤其 `testStringLiteralContainingKeywordNotFalsePositive` :156-163、`testIdentifierEmbeddingKeywordNotFalsePositive` :167-173 的负例——新增单 token 条目不得误伤字符串字面量/标识符嵌入场景）
- [x] 区分性断言：拒绝向量断言错误码 `ERR_AGGR_EXPRESSION_UNSAFE` + reason 命中目标集合（{SET, TRANSACTION} / {INTO, OUTFILE, DUMPFILE}，不钉死具体成员）；负例断言通过（不抛）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 死条目处置裁定已记录（路径 A 或 B + 理由），无"死条目留存"状态
- [x] **端到端验证**：`validateStatic` 入口 → tokenizer → `scanBlacklist` → 抛错链路对新条目向量成立（经公开入口断言，TRANSACTION 有独立向量钉死）
- [x] **无静默跳过**：无死条目继续误导（grep 黑名单集合中无多 token 残留；若走路径 B，删除理由已记录）；无 catch-and-continue 引入
- [x] 既有 24 个 validator 用例不回归；`./mvnw test -pl nop-metadata -T 1C` 相关测试类全绿
- [x] `No owner-doc update required`（docs-for-ai 无表达式黑名单条目清单细节章节）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 收口（arm-index 终态 + closure audit）

Status: completed
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Proof`

- [x] arm-index §P2 对应行（P2-10/P2-13）终态 = fixed + 本 plan 引用 + 修复摘要 + 测试证据
- [x] roadmap MR6 R6.1 行 → done（注明 plan 引用 + 测试计数）
- [x] 独立子 agent closure audit（fresh session）逐项核对 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（涉及 arm-index/roadmap 变更后）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] arm-index + roadmap 终态一致可追溯（P2-10/P2-13 两行 fixed）
- [x] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）
- [x] 无静默降级：两项安全 finding 为 fixed，无 live defect 被降级
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] P2-10：custom_sql 黑名单 6 缺项补全，绕过向量全部 fail-closed（回归测试钉死）
- [x] P2-13：ExpressionMeasureValidator 死条目处置完毕（修正或删除 + 显式理由），无死条目残留
- [x] 两文件注释与实现一致（无"声称覆盖但实际未覆盖"的误导）
- [x] 必要 focused verification 已完成（两测试文件全绿 + 既有用例不回归）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）黑名单条目在运行时确实被 `judgeCustomSql` 调用链使用（非仅集合存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）

## Deferred But Adjudicated

### 黑名单周期性审查机制（:323 注释声明的"future SQL 方言新关键字"边界）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 本 plan 只补已确认的 6 个缺项；未来方言新增关键字属持续演进面，非当前 live defect（P2-10 的绕过面已随 6 项补全关闭）；注释保留阶段性审查声明即可
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- ExpressionMeasureValidator 若走路径 B（删除死条目），其 watch-only 理由记录进 arm-index 供后续审计引用
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）

## Closure

Status Note: 两个安全 finding（P2-10/P2-13）均修复且经独立子 agent closure audit 逐项核验通过；黑名单与分词机制一致、无死条目、绕过向量全部 fail-closed、回归测试钉死；roadmap R6.1 → done、arm-index §P2 两行 fixed。
Completed: 2026-08-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，read-only，未修改任何文件）
- Audit Session: `ses_02d721c49ffeA1BMapA91K9Wvb`（auditor 自报 `ses_closure_audit_r61_ds04_20260805`）
- Evidence:
  - **Phase 1 Exit Criteria（6/6 PASS）**：
    - 23 项黑名单：`MetaQualityRuleExecutor.java:69-77` 计数 23，含 6 新增（:76-77）
    - 端到端：judge(:110) → judgeCustomSql(:152/:261-312，sqlHash :274-275) → validateCustomSqlSandbox(:331-354，:346 抛) → customSqlBlocked(:356-361 带 sqlHash param)；judge 与抛错间无 catch；`testR61NewKeywordsBlockedViaJudgeEntry`（测试 :186-211，9 参 judge 公开入口 :219 + 8 参 TableReference ctor 对齐）断言 ERR_QUALITY_CUSTOM_SQL_BLOCKED + sqlHash param 接线证明；6 向量全部在位
    - 无静默跳过：无 catch-and-continue，diff 仅集合条目 + 注释（源文件 hunks @55-58/@71-76/@320-328），validateCustomSqlSandbox 方法体零改动
    - 既有 12 用例零删除行（纯 +48）；surefire 13 run / 0 failures，报告时间戳（23:30）晚于最后源码编辑（23:20）
  - **Phase 2 Exit Criteria（6/6 PASS）**：
    - 路径 A 裁定落地：KEYWORD_BLACKLIST 含 SET/TRANSACTION(:69) + INTO/OUTFILE/DUMPFILE(:71)；FUNCTION_BLACKLIST(:88-95) 无 INTO OUTFILE/DUMPFILE；两集合无多 token 残留（含空格引号串仅存于 javadoc/消息串）
    - 端到端：validateStatic(:134) → tokenize(:150) → scanBlacklist(:152/:485-497) 抛 ERR_AGGR_EXPRESSION_UNSAFE；testR61SetTransactionBlocked(:291-300，reason 中立)/testR61TransactionAloneBlocked(:307-316，独立钉 TRANSACTION)/testR61IntoOutfileBlocked(:323-332，reason ∈ {INTO,OUTFILE,DUMPFILE} 中立)/负例 2（:336-342/:346-351）
    - 既有 24 用例零删除（纯 +71）；surefire 29 run / 0 failures
  - **Phase 3 / Closure Gates（8/8 PASS）**：arm-index :10 R6.1 note + P2-10 行 :17 / P2-13 行 :18 均 fixed（plan 引用 + 修复摘要 + 测试证据）；roadmap R6.1 行 :223 done + header v18；无静默降级（Deferred 段仅预声明 watch-only residual，两个 finding 均 fixed）；注释与实现一致（两文件 javadoc 声明面与集合逐一相符）；Anti-Hollow（a）CUSTOM_SQL_FORBIDDEN_WORDS 被 validateCustomSqlSandbox :344-348 消费 / KEYWORD+FUNCTION_BLACKLIST 被 scanBlacklist :485/:492 消费（b）diff 纯条目+注释+测试，零方法体改动，scan-hollow 0 findings
  - 工具退出码：`check-doc-links.mjs --strict` exit 0（0 errors）；`scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0（0 发现）；surefire 独立聚合 **901 run / 0 failures / 0 errors / 0 skipped**（97 报告文件；基线 895 + 6 新增 = 901 相符）
  - Anti-Hollow 检查结果：judge → judgeCustomSql → validateCustomSqlSandbox 调用链从公开入口到抛错完整连通（sqlHash param 接线断言实证）；validateStatic → scanBlacklist 链同；无空方法体/静默跳过/no-op；`scan-hollow-implementations.mjs` exit 0
  - Deferred 项分类检查：无 in-scope live defect 被降级（P2-10/P2-13 均 fixed；周期性审查 residual 为预声明非阻塞项，Successor Required: no）
- 非阻塞观察（auditor 记录）：日志条目尾部"Phase 3 继续执行中"为临时表述，已随本收口更新（终态记录于 arm-index/roadmap）；无功能性问题

Follow-up:

- no remaining plan-owned work（P2-10/P2-13 均 fixed；周期性审查 residual 已在 Deferred But Adjudicated 预声明，Successor Required: no）
