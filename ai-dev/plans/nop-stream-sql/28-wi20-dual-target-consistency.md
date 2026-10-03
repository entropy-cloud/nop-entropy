# 28 WI20 双目标一致性验证

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI20 行）、`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4c（W2 三档）、D1/D15
> Related: `ai-dev/plans/nop-stream-sql/25-wi17-sql-compiler.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

同一查询两路 golden：编译到 nop-stream 执行断言结果集；经 AstToSqlGenerator 产出 RDBMS SQL 并在 H2 实跑对照。其余方言按 D15（默认 H2 实跑，多库 opt-in）。W2（TUMBLE）与 D1 非 append-only 语义差异显式标注。

## Current Baseline（2026-10-03 实测）

- WI17 交付 StreamSqlCompiler（流路）+ `EqlASTParser.parse(...).toSQL()`（RDBMS 通道，orm-eql 既有 API；TUMBLE 表源 ORM 通道 table-source-not-resolved fail-fast——round-trip 不对称在案）；nop-stream-sql pom 已有 H2 test 依赖（WI17 加入）。
- orders stub fixture 七记录定数（1000/a/1、2000/b/2、3000/a/null、4000/a/3、11000/b/4、12000/a/-5、17000/c/1）——含 NULL 与负数，三值逻辑天然对拍。三测试类拆分：一 execute 一类限制（plan 13 先例）——Consistency（聚合执行）/FilterConsistency（过滤执行）/TumbleStreamOnly（仅编译无 execute）。
- WI17 E2E 已钉流路行为：过滤查询 [a=1,a=3,b=2,b=4,c=1]、持续聚合运行序列（a 三次 emit，D1 非 append-only 实证）、TUMBLE 窗口聚合。

## Goals

- `TestDualTargetConsistency`（nop-stream-sql）三查询：
  1. **过滤投影查询**（SELECT item, amount FROM orders WHERE amount > 0）：流路 sink 结果集 == H2 实跑 toSQL() 结果集（含 NULL 三值逻辑对拍——NULL 行两侧都被排除）。
  2. **持续聚合查询**（SELECT item, sum(amount) AS total FROM orders GROUP BY item，无 WHERE——全部七记录参与）：H2 分组和（终态 a=-1,b=6,c=1——含 -5 记录）== 流路**终态** per-group 值；**D1 显式标注**——流路对 key a 发射四次运行值（1,1,4,-1，null 记录重发运行值；非 append-only 实证断言：a 的 emit 次数 > 1），仅终态可比。
  3. **TUMBLE 查询**（W2）：流路编译+执行成功；RDBMS 目标按 §4c **T1 直通标注 stream-only**（无 H2 对照——外部数据库无原生等价，未在本仓库验证；文档标注义务）。
- D15 口径：默认 H2 实跑；其余方言 opt-in 未跑——交付说明标注。

## Non-Goals

- 多库 opt-in 实跑（D15 保持）；AstToSqlGenerator 的 TUMBLE 渲染补齐（归 WI20 既有归属——本 WI 标注 T1 即可）；全量方言 golden。

## Scope

### In Scope

- `nop-stream/nop-stream-sql`：TestDualTargetConsistency（三查询双路对拍 + 标注）+ H2 JDBC 测试设施
- 日志；文档标注（docs-for-ai/03-modules/nop-stream-sql.md 补双目标语义边界一句）

### Out Of Scope

- WI23 quickstart；WI24 收口；多方言实跑。

## Execution Plan

### Phase 1 - 双路对拍

Status: completed
Targets: `nop-stream/nop-stream-sql`

- Item Types: `Proof`

- [x] TestDualTargetConsistency：查询 1 精确集相等（流路 sink vs H2 JDBC 行集）；查询 2 终态 per-group 相等 + a 多次 emit 断言（D1 非 append-only 实证）+ javadoc 标注；查询 3 流路可执行 + T1 stream-only 标注（无 H2 对照的 W2 语义边界 javadoc）
- [x] H2 设施：内存库建 orders 表 + 插入七记录定数（与 stub fixture 逐条一致）+ toSQL() 文本执行
- [x] 文档一句标注（双目标语义边界与 W2 T1）

Exit Criteria:

- [x] 三查询测试绿（查询 1 集相等含 NULL 排除对拍；TUMBLE 通道按 loud 渲染语义修正——W2/T1 原样回显，语义 resolve 仍拒绝）
- [x] sql 模块全量零退化（78 绿）
- [x] docs-for-ai 标注落档 + doc-links 0
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）；证据落 ai-dev/audits/nop-stream-sql/wi20-closure-audit.md
- [ ] audit 通过后 roadmap WI20 `todo` → `done`；解析器断言 items=31/milestones=7/WI20=done
- [ ] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0

Exit Criteria:

- [ ] 独立 audit 证据落档两处
- [ ] roadmap WI20 = done + 解析器断言成立
- [ ] 双门禁退出码 0

## Closure Gates

- [ ] 三查询双路测试齐备且实跑绿（集相等/终态+D1 标注/T1 标注）
- [x] sql 模块全量零退化（78 绿）
- [ ] docs-for-ai 标注落档
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/28-wi20-dual-target-consistency.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Current Baseline

（见上——双路对拍用例不存在，为本 WI 缺口。）

## Closure

Status Note: <<完成时填写>>
Completed:

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<验证结果>>

Follow-up:

- <<no remaining plan-owned work 或列出>>
