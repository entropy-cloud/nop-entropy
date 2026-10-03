# 30 WI23 示例与 quickstart

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI23 行）
> Related: `ai-dev/plans/nop-stream-sql/26-wi18-sql-entry-and-docs.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

roadmap 明文类名 TestStreamSqlQuickstart 从 .sql 文件跑到 sink 并纳入 quickstart verify.sh；fraud-example 纳入与否记录裁定。

## Current Baseline（2026-10-03 实测）

- WI17/18 交付 `<sql>` 声明面 + ISqlStreamCompiler SPI + TestSqlModelDeclarationE2E/TestStreamSqlEntryE2E（资源驱动端到端）。
- quickstart verify.sh 机制在案（`docs-for-ai` quickstart 模块惯例——须实测具体路径与脚本形态）。
- fraud-example（nop-stream-fraud-example）为既有示例模块——`<sql>` 纳入与否未裁。

## Goals

- `TestStreamSqlQuickstart`（sql 模块）：从 `.sql` 文件（classpath 资源）跑到 sink 端到端（resource 驱动 + `<sql>` 模型展开 + execute 断言）。
- verify.sh 纳入：quickstart 的 verify.sh 脚本纳入该用例（实测 quickstart 位置与脚本形态后落最简集成）。
- fraud-example 裁定记录：基于实测（fraud-example 是否有 SQL 化收益与 verify.sh 纳入点），单一结论写入 plan Closure 与 roadmap 括注。

## Non-Goals

- 新 SQL 能力；fraud-example 大规模改写（仅裁定是否纳入）。

## Scope

### In Scope

- `nop-stream/nop-stream-sql`：TestStreamSqlQuickstart + .sql 资源
- quickstart verify.sh（位置实测后定）
- 日志

### Out Of Scope

- WI24 收口；新能力。

## Execution Plan

### Phase 1 - Quickstart 用例与脚本纳入

Status: completed
Targets: `nop-stream/nop-stream-sql`、quickstart

- Item Types: `Proof`

- [x] TestStreamSqlQuickstart（sql 模块）：.sql 文件资源 → `<sql>` 模型构建 → execute → sink 断言
- [x] quickstart verify.sh 纳入（实测形态后落最简集成——如 verify.sh 调 `./mvnw test -Dtest=TestStreamSqlQuickstart -pl ...`）
- [x] fraud-example 纳入裁定记录（plan Closure + roadmap 括注）
- [x] 日志

Exit Criteria:

- [x] TestStreamSqlQuickstart 实跑绿（.sql 文件驱动端到端）
- [x] verify.sh 纳入落档（step 4：`-pl nop-stream/nop-stream-sql -Dtest=TestStreamSqlQuickstart`）
- [x] sql 模块全量零退化（79 绿）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）；证据落 ai-dev/audits/nop-stream-sql/wi23-closure-audit.md
- [ ] audit 通过后 roadmap WI23 `todo` → `done`；解析器断言 items=31/milestones=7/WI23=done
- [ ] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0

Exit Criteria:

- [ ] 独立 audit 证据落档两处
- [ ] roadmap WI23 = done + 解析器断言成立
- [ ] 双门禁退出码 0

## Closure Gates

- [ ] TestStreamSqlQuickstart 齐备且实跑绿
- [ ] verify.sh 纳入落档
- [ ] fraud-example 裁定记录
- [x] sql 模块全量零退化（79 绿）
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/30-wi23-quickstart.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Current Baseline

（见上。）

## Closure

Status Note: <<完成时填写>>
Completed:

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<验证结果>>

Follow-up:

- <<no remaining plan-owned work 或列出>>
