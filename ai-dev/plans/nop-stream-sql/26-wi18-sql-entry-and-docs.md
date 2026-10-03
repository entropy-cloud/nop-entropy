# 26 WI18 用户接口面与用户文档

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI18 行）、`ai-dev/design/nop-stream/sql-compiler-contract.md` §2（D8=`<sql>` 元素）
> Related: `ai-dev/plans/nop-stream-sql/25-wi17-sql-compiler.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

D8 选定入口（`<sql>` 模型元素）具名可调用并有端到端证据；用户文档落 docs-for-ai 并登记路由。

## Current Baseline

- WI17 已交付：`<sql>` xdef 顶层元素（sql 文本 + 内嵌 schema + sinkBean 属性）；builder expandSqlModel 预处理展开（ISqlStreamCompiler SPI，nop-stream-sql app-beans 自动注册）；TestSqlModelDeclarationE2E（sql 模块）已从 `<sql>` 声明模型 execute 到 sink——入口已具名可调用，缺 roadmap 明文类名的 TestStreamSqlEntryE2E 与文档面。
- docs-for-ai/03-modules/ 目录在案；docs-for-ai/INDEX.md 与 04-reference/source-anchors.md 为路由登记面。

## Current Baseline（2026-10-03 实测）

- WI17 已交付：`<sql>` xdef 顶层元素（sql 文本 + 内嵌 schema + sinkBean 属性）；builder expandSqlModel 预处理展开（ISqlStreamCompiler SPI，nop-stream-sql app-beans 自动注册）；`TestSqlModelDeclarationE2E`（sql 模块）已从 `<sql>` 声明模型 execute 到 sink（[a=4,b=2,b=4,c=1]）——**入口已具名可调用，缺 roadmap 明文类名的 TestStreamSqlEntryE2E 与文档面**。
- docs-for-ai/03-modules/ 目录在案（模块文档惯例参照既有文件）；docs-for-ai/INDEX.md 与 04-reference/source-anchors.md 为路由登记面。

## Goals

- roadmap 明文类名 `TestStreamSqlEntryE2E` 落地（nop-stream-sql）：从 `<sql>` 模型文件（classpath 资源）到 sink 输出的端到端——与 TestSqlModelDeclarationE2E 区分为**资源文件驱动**（用户真实形态：一个 .xml 文件）而非内联字符串。
- 用户文档 `docs-for-ai/03-modules/nop-stream-sql.md`：SQL 面使用指南（`<sql>` 元素形态、schema 声明、sinkBean、纳入面/不支持清单指引、错误码、与 WI9 求值器/编译器入口的关系）。
- INDEX.md 与 source-anchors.md 登记。

## Non-Goals

- WI19 Delta 验证；WI20 双目标一致性；quickstart（WI23）。

## Scope

### In Scope

- `nop-stream/nop-stream-sql`：TestStreamSqlEntryE2E + classpath 模型资源
- `docs-for-ai/03-modules/nop-stream-sql.md` + INDEX.md + source-anchors.md
- 日志

### Out Of Scope

- WI19/20/23；新增语言能力（一切语义归 WI17 已交付面）。

## Execution Plan

### Phase 1 - 入口 E2E 与文档

Status: completed
Targets: `nop-stream/nop-stream-sql`、`docs-for-ai`

- Item Types: `Proof`

- [x] `TestStreamSqlEntryE2E`（sql 模块）：classpath `<sql>` 模型资源（含 schema + sinkBean）→ DslModelParser → StreamModelDslBuilder.build → execute → sink 断言（资源驱动用户形态；区分 TestSqlModelDeclarationE2E 的内联形态）
- [x] `docs-for-ai/03-modules/nop-stream-sql.md`：`<sql>` 元素完整使用说明（形态/schema/受管类型/sinkBean/纳入面与不支持清单/错误码/执行语义标注 D1 last-value-wins）
- [x] docs-for-ai/INDEX.md 登记 + 04-reference/source-anchors.md 锚点
- [x] 日志

Exit Criteria:

- [x] TestStreamSqlEntryE2E 实跑绿（资源驱动、端到端）
- [x] check-doc-links --strict 0（初跑 3 处 ai-dev 边界违规即改即绿）
- [x] sql 模块全量零退化（71 绿）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session）；证据落 ai-dev/audits/nop-stream-sql/wi18-closure-audit.md
- [x] audit 通过后 roadmap WI18 `todo` → `done`；解析器断言 items=31/milestones=7/WI18=done
- [x] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI18 = done + 解析器断言成立
- [x] 双门禁退出码 0

## Closure Gates

- [x] TestStreamSqlEntryE2E 齐备且实跑绿（资源驱动端到端）
- [x] docs-for-ai 三处登记齐备且链接零断
- [x] sql 模块全量零退化（71 绿）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/26-wi18-sql-entry-and-docs.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

执行记录（Phase 2）：三轮独立 audit——一审 FAIL（MAJ-1 错误码写反 + 3 Minor 文档失实）、二审 FAIL（MIN-2 静默未改：替换串不匹配且无断言）、三轮点核 PASS；4 处文档失实全部修正。

## Closure

Status Note: D8 入口 `<sql>` 具名可调用并有资源文件驱动端到端证据；用户文档三件套登记齐备且与 live 逐项相符（三轮 audit 追平）；sql 模块 72 绿。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent 三轮（fresh session，相互非同一 session）
- Evidence: ai-dev/audits/nop-stream-sql/wi18-closure-audit.md——一审 FAIL（MAJ-1+3 Minor）→修复→二审 FAIL（MIN-2 静默未改）→修复→三轮点核 PASS（与 §4a 形态列逐项相符、doc-links 0、check-plan-checklist 0）

Follow-up:

- OBS-1（user-guide/owner doc 回链）与 OBS-2（compile schema 可空措辞精确化）归 WI24 收口顺带
