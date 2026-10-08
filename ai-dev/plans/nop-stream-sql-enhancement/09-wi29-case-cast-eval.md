# 09 WI29 CASE 与 CAST 求值器放行

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI29 行）；`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4a#7（修订对象）；eql-cross-db WI9a（CAST 类型别名已落地——ORM 侧先行流侧消费）
> Related: `ai-dev/plans/nop-stream-sql/14-wi9-aggregate-eval.md`（WI9 求值器先例）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」）

## Purpose

在 StreamSqlExprCompiler 与 SqlScalarXplPrinter 内放行 EQL 已解析的 CASE（简单/搜索两形态）与 CAST 表达式（目标类型限九受管类型闭集）；§4a#7 修订与钉码同步。DECIMAL/DATE/TIMESTAMP **列绑定维持排除**（§4a#5 不动）；CAST 目标类型同样限闭集。第二步引擎 SPI 形态进 Follow-up Backlog 不预设。

## Current Baseline

2026-10-08 实测：StreamSqlExprCompiler.compileScalar switch :57-93（12 分支 + default `RecordColumnAccess.unsupported` :90-92）；resolveType :118-166（default UnknownTypeInformation）；SqlScalarXplPrinter.printTo :84-162（default :159-161，三元 null 守卫模板 printBinary :164-184）；CAST AST = SqlCastExpr（dataType: SqlTypeExpr + expr，_gen 双 mandatory）；CASE = SqlCaseExpr（caseWhens: List<SqlCaseWhenItem>{when mandatory, then 可空} + elseExpr + test 简单形式）；StdSqlType 已含 WI9a 别名（INT/TEXT/NUMBER/BOOL，stdNameMap :175-183）；§4a#7 钉码 outOfSubsetFailsFastWithStreamSideCode（CASE/CAST 落 default 文案）。

## Goals

1. **求值器**：compileScalar 增 SqlCastExpr 分支（CompileHelper.toTargetType(value, targetTypeName) 运行时转换——目标类型限九受管闭集，集合外类型名 typed fail-fast）与 SqlCaseExpr 分支（简单/搜索两形态：简单=test 值与 when 求值相等；搜索=when 表达式布尔求值；无匹配返 elseExpr 或 null）。
2. **打印器**：SqlScalarXplPrinter 增两分支——CAST 打印为运行时 helper 静态调用（新建 SqlCastHelper.cast(value, targetTypeName)，镜像 SqlRowAggregateOps 先例）；CASE 打印为嵌套三元 null 守卫链。
3. **类型推断**：resolveType 增两分支——CAST 返回目标类型名对应的 BasicTypeInfo；CASE 返回 then 分支类型（集合内一致，未知返 Unknown）。
4. **钉码/文档**：outOfSubset 钉码中 CASE/CAST 断言更新；新增 CASE/CAST 正向求值与打印钉码；§4a#7 修订（CASE/CAST 移出、保留正则/IN 子查询/参数标记）；§4b SELECT 投影行同步；nop-stream-sql.md 更新。

## Non-Goals

- DECIMAL/DATE/TIMESTAMP 列绑定（§4a#5 不动）；CAST 目标类型扩至闭集外；SqlRegularFunction（WI30 UDF 面）；IN 子查询/参数标记（维持排除）；引擎 SPI 形态（Follow-up Backlog）。

## Scope

### In Scope

- StreamSqlExprCompiler/SqlScalarXplPrinter/SqlCastHelper（新）/TestStreamSqlExprCompiler/TestStreamSqlCompiler（钉码更新）
- 文档：sql-subset-and-semantics §4a#7 与 §4b SELECT 行、nop-stream-sql.md；roadmap WI29 状态行；日志

### Out Of Scope

- §4a#5 列绑定；SqlRegularFunction；window expressions；ORM toSQL 通道（SQL 渲染归既有 WI20 面）

## Execution Plan

### Phase 1 - 求值器/打印器/钉码

Status: completed
Targets: `nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/`

- Item Types: `Feature`

- [x] SqlCastHelper（eval 包）：cast(Object, String typeName)——九受管类型闭集分发（string/valueOf、int/bigint/float/double 数值转换、boolean、bytes），null 透传 null，集合外类型名 typed fail-fast
- [x] StreamSqlExprCompiler：compileScalar 增 SqlCastExpr（子表达式求值 + SqlCastHelper.cast）与 SqlCaseExpr 分支（遍历 caseWhens：简单形态 testValue.equals(whenValue)、搜索形态 Boolean.TRUE.equals(whenEval)；命中返 then 求值；无命中返 else 或 null）；resolveType 增两分支
- [x] SqlScalarXplPrinter：增 SqlCastExpr 分支（打印 SqlCastHelper.cast(子表达式 xpl, "typeName") 静态调用 + null 守卫）与 SqlCaseExpr 分支（嵌套三元链，null 守卫）
- [x] 钉码：TestStreamSqlExprCompiler 增 CASE 简单/搜索/无匹配-else/null 传播、CAST 各类型/闭集外 fail-fast/null 透传；outOfSubsetFailsFastWithStreamSideCode 更新（SqlCaseExpr 不再命中 default）；TestStreamSqlCompiler 产物打印钉（CASE 三元链/CAST helper 调用出现在投影体）
- [x] 文档：§4a#7 修订（CASE/CAST 移出排除、保留其余）、§4b SELECT 投影行补 CASE/CAST、nop-stream-sql.md 纳入面更新
- [x] 全量回归：nop-stream-sql -am

Exit Criteria:

- [x] **端到端验证**（guide #22）：含 CASE/CAST 的查询经真实执行产生正确求值结果（E2E 或 compile+execute 同构断言）
- [x] **接线验证**（guide #23）：CASE/CAST 从 SQL 文本 → AST → compileScalar（求值）与 SqlScalarXplPrinter（产物 xpl）双通道连通——双向钉码
- [x] **无静默跳过**（guide #24）：CAST 闭集外类型名 typed fail-fast；CASE 无匹配且无 else 显式返 null（非静默吞）
- [x] 新功能测试覆盖（guide #25）：求值/打印/类型推断/闭集守卫/钉码更新五处
- [x] §4a#7 修订后其余排除项（正则/IN 子查询/参数标记）钉码保持
- [x] `./mvnw test -pl nop-stream/nop-stream-sql -am` 全绿
- [x] owner doc 更新：sql-subset §4a#7+§4b、nop-stream-sql.md
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: in progress
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 独立子 agent closure audit；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi29-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI29 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)`）；重跑解析器核对 items 18 milestones 4
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0，scan-hollow --severity high 退出码 0

## Closure Gates

- [x] 行为/契约结果已达成：CASE/CAST 求值与产物打印双通道可用，闭集外维持排除
- [x] 必要 focused verification 已完成（求值/打印/类型/守卫钉码）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：SQL→AST→求值/打印双通道连通（双向钉码真实执行）
- [x] `./mvnw test -pl nop-stream/nop-stream-sql -am` 全绿
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 引擎 SPI 形态（SeaTunnel 式第二引擎）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap WI29 行原文「第二步引擎 SPI 形态进 Follow-up Backlog 不预设」——FU-E1 已登记
- Successor Required: `no`
- Successor Path: roadmap Follow-up Backlog FU-E1

## Non-Blocking Follow-ups

- GROUP BY 可空键（含 cast 可空列/普通可空列）运行时 null-key 失败——引擎既有约束（reduce requires key context），非 CAST 特有；watch-only，真实需求出现时按 null-key 裁定（跳过/合成键）另立

## Closure

Status Note: CASE/CAST 求值器与产物打印双通道交付——CAST 目标限九受管闭集（EQL StdSqlType 名映射，DECIMAL 等维持排除）、CASE 简单/搜索两形态三值语义、import 前置机制（requiredImports 收集+prependImports）。closure audit R1 REJECTED（B1 CASE 打印空心——java.util.Objects 裸 FQN 不可执行实跑复现；M1 陈旧聚合钉码；M2 GROUP BY cast 可空键运行时失败；M3 §4b SELECT 行未补）——补救后复核 APPROVED。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）：agent_ab25779e-902e-46e5-b498-d274d69c8a34
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi29-closure-audit.md
- Evidence:
  - R1 REJECTED：B1 CASE 打印通道空心（java.util.Objects 裸 FQN XLang scope-var-is-undefined 实跑复现；字符串钉码 ≠ 语义落地）→ 修复：Objects import 前置 + 数值感知条件（instanceof Number → doubleValue 比较，fallback Objects.equals）与 eval 通道 valuesMatch 语义收敛；搜索式 Objects.equals(true, when)（XLang true 字面量，Boolean.TRUE 裸 FQN 同不可用已修）
  - M1 陈旧聚合钉码 exprCompileFailureFailsFast 改指 concat（CASE 已合法）
  - M2 GROUP BY cast 可空键运行时失败（cast(null)→null key→reduce requires key context）——裁定为引擎既有 null-key 约束（与 GROUP BY amount 含 null 同族，非 CAST 特有），记 plan watch-only
  - M3 §4b SELECT 投影行已补 CASE/CAST
  - E2E TestCaseCastE2E 真跑绿（7 行 sign/amt 精确断言，含 null 透传 Z/null）——B1 整改的执行级钉码
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/09-wi29-case-cast-eval.md --strict` 退出码 0（翻转后复跑确认）

Follow-up:

- no remaining plan-owned work；引擎 SPI 形态归 FU-E1
