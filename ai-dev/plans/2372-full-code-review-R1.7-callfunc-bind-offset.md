# 2372 full-code-review R1.7 — nop-dao callFunc 绑定偏移修复（P1-2.3-001）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.7 行；finding `P1-2.3-001`（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.3-dao-migration-dbtool-deep-audit.md`，jdbc-dim04-01；伴生 P3 jdbc-dim04-10 OUT 类型硬编码与 001 合并修复）

## Purpose

修复 `IJdbcTemplate.callFunc` 参数化调用绑定偏移：OUT 注册占 JDBC 位置 1，而 marker 绑定从 index 0（=JDBC 位置 1）起——带参 `{? = call f(?)}` 的首个 IN 参数错绑到 OUT 槽位，必然失败（审计 H2 探针实证 `Parameter "#3" is not set`；正确绑定返回 7）。修复后补带参回归。

## Current Baseline

- `JdbcHelper.prepareCallableStatement`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcHelper.java:169-181`）：`st.registerOutParameter(1, Types.VARCHAR)` 后调 `setParameters(dialect, st, sql)`（无偏移）。
- `setParameters(dialect, ps, sql)`（:291-313）：marker 从 `index = 0` 起绑；`JdbcStatement` 为 0-based 包装（index 0 = JDBC 位置 1）→ 首个 IN 参数错占 OUT 槽位。**既有重载** `setParameters(dialect, ps, int index, List<Object> params)`（:319）证明偏移能力在位，仅 SQL-marker 版缺。
- javadoc 契约（`IJdbcTemplate.callFunc`，:38-41）：「sql 必须为 {? = call func(...)} 形式，返回第一个 OUT 参数的值（由框架自动注册）」——与实现矛盾处即绑定偏移（修复后对齐）。
- OUT 类型（伴生 P3 jdbc-dim04-10）：`Types.VARCHAR` 硬编码；无返回类型声明通道，保持 VARCHAR 并在 javadoc 显式声明该契约（审计合并修复指令中可落实部分）。
- 既有测试：`TestJdbcTemplate.testCallFuncReturnsOutParamValue`（:153-158，H2 CREATE ALIAS 零参形态——掩盖偏移）；测试基建 `JdbcTestCase` + H2。
- 全仓 main 零调用方（审计边界注记：唯一降 P2 候选——按 roadmap 已定 P1 执行，不重裁）。

## Goals

- 带参 `{? = call f(?)}` 正确绑定（IN 参数起始 JDBC 位置 2），零参形态不变。
- javadoc 显式化 OUT 类型 VARCHAR 契约与 IN 偏移自动处理。
- 带参回归测试（H2 ALIAS 函数）+ stash 判别。

## Non-Goals

- 不实现 OUT 类型声明通道（无元数据来源；VARCHAR 契约化）。
- 不修 dim04 其余 P2/P3（LockOption 死参数、rs 死 close 等走台账）。

## Scope

### In Scope

- `JdbcHelper`：新增 `setParameters(dialect, ps, SQL sql, int startIndex)` 重载（复用既有 index 逻辑）；`prepareCallableStatement` 以偏移 1 调用（零 marker 时无效果）。
- `IJdbcTemplate.callFunc` javadoc：IN 参数自动偏移 + OUT 类型 VARCHAR 契约显式化。
- `TestJdbcTemplate` 新增带参回归：`CREATE ALIAS ... AS 'String f(Integer p){ return String.valueOf(p*2); }'` + `callFunc("{? = call f(?)}" param 3)` 断言返回 "6"（审计探针形态）。

### Out Of Scope

- OUT 类型注册元数据化、其余 jdbc P2/P3。

## Execution Plan

### Phase 1 - 偏移修复 + 回归

Status: completed
Targets: `JdbcHelper.java`、`IJdbcTemplate.java`、`TestJdbcTemplate.java`

- Item Types: `Fix`

- [x] `JdbcHelper.setParameters(dialect, ps, sql, startIndex)` 重载（正文同 :291-313 但 index 起始为 startIndex）；`prepareCallableStatement` 改调 `setParameters(dialect, st, sql, 1)`
- [x] `IJdbcTemplate.callFunc` javadoc 补两句：IN 参数自 JDBC 位置 2 起自动绑定；OUT 以 VARCHAR 注册返回
- [x] `TestJdbcTemplate` 新增 `testCallFuncWithParams`（H2 ALIAS 带参函数，断言正确返回值）；保留既有零参测试
- [x] stash 判别：stash JdbcHelper 修复跑新测试——预期 FAIL（H2 `Parameter "#3" is not set` 或错值），留输出入 daily log

Exit Criteria:

- [x] 新回归测试修复后绿；stash 判别记录在案
- [x] `./mvnw test -pl nop-persistence/nop-dao` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: completed
Targets: roadmap、轮次 index、本 plan

- Item Types: `Proof`

- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dao --severity high` 退出码 0
- [x] roadmap R1.7 行 → done；index P1-2.3-001 → fixed + MR 表 R1.7 行
- [x] owner doc 裁定：`docs-for-ai/03-modules/nop-dao.md` 如记录 callFunc 契约则同步；无则 No owner-doc update required（执行时核实）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] roadmap 与 index 回填一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P1-2.3-001 修复落地：带参 callFunc 正确绑定（回归锁定）
- [x] javadoc 契约与实现一致（IN 偏移自动 + OUT VARCHAR 显式）
- [x] focused verification：nop-dao 全量绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] owner docs 裁定记录
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：stash 判别真实 FAIL→PASS
- [x] `./mvnw test -pl nop-persistence/nop-dao` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### OUT 类型注册元数据化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 无返回类型声明通道（SQL 对象不携带 OUT 类型元数据）；VARCHAR 注册经审计探针验证在 H2 上绑定正确；契约已 javadoc 化
- Successor Required: `no`

## Non-Blocking Follow-ups

- no remaining plan-owned work

## Closure

Status Note: P1-2.3-001 修复落地：setParameters 增 SQL+startIndex 重载（3 参版委托单源化）、prepareCallableStatement 以偏移 1 绑定（OUT 占位置 1、首 IN 落位置 2）、OUT VARCHAR 契约 javadoc 化。**判别证据可独立复现**：stash JdbcHelper 后恰 2 个带参用例 FAIL，H2 `Parameter "#2"/"#3" is not set` 与审计探针逐字吻合；零参对照双绿。nop-dao 168 tests 全绿。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_f0fc8fba-c99f-463e-a15b-e8228c0ae690
- Evidence:
  - **Anti-Hollow stash 判别独立重做**：PASS——auditor 独立 stash JdbcHelper（确认还原为无偏移版本）→ 13 用例中恰 2 个带参用例 FAIL（testCallFuncWithParams=`Parameter "#2" is not set`；testCallFuncWithMultiParams=`Parameter "#3" is not set`——与审计探针 `2.3 审计:111` 逐字吻合且探针返回值 7 与新双参测试断言同源）；**零参对照双绿确认**；pop 后 13/13 绿。
  - **修复落地**：PASS——JdbcHelper:301-323 新重载（marker 循环单源化，:292-294 3 参版委托）、:176 偏移 1 调用、:296-300 R1.7 注释。
  - **javadoc 契约一致**：PASS——IJdbcTemplate:41-42 两句新增与实现逐点吻合；builder 路径（SQL.java:903-905 `{ ? = call f(?) }`）核实。
  - **全量回归**：PASS——auditor 亲跑 `./mvnw test -pl nop-persistence/nop-dao` 168/0/0/skip46 BUILD SUCCESS。
  - **无静默降级**：PASS——OUT 类型元数据化=optimization candidate（无元数据通道+VARCHAR 探针验证+契约化）。
  - **owner doc 裁定**：PASS——docs-for-ai 全目录 callFunc 零命中+nop-dao.md 不存在（双核实 No owner-doc update required）。
  - **状态回填四处一致**：PASS——roadmap :138/index :103/MR :263/daily log。
  - Advisory 处置：A1 随本 closure 提交（已做）；A2 探针错误 `#3`（双参场景）与测试注释 `#2`（单参场景）均正确，本次独立重做证实两态皆复现（留档）。

Follow-up:

- OUT 类型注册元数据化（Deferred But Adjudicated，optimization candidate）
