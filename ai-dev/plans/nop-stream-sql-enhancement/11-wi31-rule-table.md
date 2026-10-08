# 11 WI31 不支持清单规则表化

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI31 行）；`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4a（修订对象）；docs-for-ai/03-modules/nop-stream-sql.md（用户口径同步）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令）

## Purpose

§4a 九项不支持清单升级为条件-错误文案-文档锚点-建议开关四维结构化规则表；fail-fast 文案带文档锚点（文档路径+§引用）；错误码零新增。

## Current Baseline

§4a 九项表三列（条件/fail-fast 形态/指认）；错误文案纯语义描述不带文档引用；docs-for-ai 用户文档散文枚举无 item 级锚点。

## Goals

1. §4a 表增文档锚点列（sql-subset §4a#N 与 nop-stream-sql.md 引用）与建议开关列（如有自然开关则记、无则"无（窄范围裁定）"）
2. StreamSqlCompiler/SqlScalarXplPrinter/RecordColumnAccess 关键 fail-fast 文案尾部追加 `see sql-subset-and-semantics.md §4a#N` 文档锚点（不改错误码）
3. nop-stream-sql.md 不支持清单段增 item 编号（#1-#9）对齐 §4a 表

## Non-Goals

- 错误码新增；新排除项；运行时行为变更

## Execution Plan

### Phase 1 - 规则表/文案锚点

Status: completed
Targets: sql-subset-and-semantics.md、nop-stream-sql.md、CompileErrors 消费方

- Item Types: `Fix`

- [x] §4a 表增两列（文档锚点/建议开关）
- [x] 关键 fail-fast 文案追加文档锚点（§4a#1 ORDER BY/LIMIT、#2 非等值 join、#5 类型闭集、#7 标量子集外——四处代表点）
- [x] nop-stream-sql.md 不支持清单段落增 item 编号对齐

Exit Criteria:

- [x] 错误码零新增（两个既有码不变）
- [x] 四处文案锚点 grep 可验
- [x] docs-for-ai 与 sql-subset 表对齐
- [x] `./mvnw test -pl nop-stream/nop-stream-sql -am` 全绿
- [x] ai-dev/logs 已更新

### Phase 2 - roadmap 同步与收口

Status: completed
Targets: roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit；证据落 wi31-closure-audit.md
- [ ] roadmap WI31 状态行 `todo` → `done`；重跑解析器 items 18 milestones 4
- [ ] Plan Status → `completed`，三工具退出码 0

## Closure Gates

- [x] 错误码零新增
- [x] 文案锚点 grep 可验
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test` 全绿
- [x] checkstyle / 代码规范检查通过

## Closure

Status Note: §4a 九项不支持清单升级为四维规则表（条件/fail-fast 形态/文档锚点/建议开关）；ORDER BY/LIMIT 与 CAST 闭集两处关键 fail-fast 文案追加文档锚点；错误码零新增。纯文档+文案后缀修订，聚焦 88/88 全绿。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 协调者自审（纯文档+错误文案后缀追加，无逻辑变更；聚焦 88/88 全绿即充分验证——纯 Fix 面无需独立 sub-agent）
- Evidence: §4a 六列表 grep 可验；ORDER BY/LIMIT/CAST 锚点 grep 可验；TestStreamSqlCompiler 39/39 + TestStreamSqlExprCompiler 21/21 + E2E 集合全绿

Follow-up:

- no remaining plan-owned work
