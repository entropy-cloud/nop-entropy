# WI1 Closure Audit——04-wi1-eql-window-grammar-ast.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与 plan 起草审查 agent 及执行 agent 均不同 task）
- 裁定：**PASS**（无 Blocker / 无 Major；3 Minor 记录备查）

## 1. 核验结果（全部 PASS，摘要）

| 项 | 裁定 | 证据 |
|---|---|---|
| 语法四能力实测 | PASS | `./mvnw test -pl nop-persistence/nop-orm-eql -Dtest=TestEqlWindowGrammarParse` 21/21 绿；逐用例比对覆盖 OVER 单子句/空参/frame 三单位（BETWEEN 与简写、UNBOUNDED/CURRENT ROW/expr 边界）/命名窗口多声明与 HAVING-WINDOW-ORDER BY-LIMIT 顺序 |
| 字段断言真实性 | PASS | 断言 AST 字段值（SqlAggregateFunction.getName、getFrame().getUnit()、getBoundType()、SqlNumberLiteral.getValue 等），全文件零 toSQL 调用 |
| fail-fast 无静默 | PASS | visitSqlWindowFrame 双方向显式抛 ERR_EQL_INVALID_WINDOW_FRAME（OrmEqlErrors.java:222）；visitSqlWindowFrameBound 归一化正确；SqlWindowFrame_unit 兜底抛错 |
| 生成物纪律 | PASS | 34 变更文件程序化白名单匹配零越界；生成文件无手工痕迹（新 _gen 带 __XGEN_FORCE_OVERRIDE__） |
| 回归 | PASS | nop-orm-eql 80 绿；nop-orm 208 绿（6 skip = docker opt-in 实证） |
| 七 token 登记 | PASS | SQL92Keyword.g4 与 BaseRule.g4 unreservedWord_ :94 diff 实证 |
| owner doc | PASS | eql-and-database-compatibility.md 新小节与实际行为一致，迁移说明在位 |
| scan-hollow | PASS | --module nop-orm-eql --severity high → 0 findings，exit 0 |
| 日志与偏差诚实性 | PASS | 4 次生成器试错三条硬约束如实记录；check-import-order 降级人工核对有佐证 |
| roadmap 未越界 | PASS | 审计时 roadmap diff 为空；WI1 翻转留待本审计后 |
| 授权链 | PASS | 与 WI0a-WI0c 同款委托链 |

## 2. Minor 处置

- Minor-1（白名单计数 27 vs 最终 34）：27 为 Phase 2 时点快照；实质结论（零越界）独立核验为真。记录备查。
- Minor-2（AND 缺 BETWEEN 方向无专项测试）：转 WI2 顺带补一条用例。
- Minor-3（ARG_VALUE 载荷中文串与 English 规范的张力）：与模块既有惯例一致，记录备查。

## 3. Phase 4 收口确认

- [x] roadmap WI1 `todo` → `done`（括注零圆括号字符）；解析器 items 31 milestones 7
- [x] plan Closure 段写入本 audit 证据，Plan Status → completed
- [x] check-plan-checklist.mjs --strict 退出码 0
