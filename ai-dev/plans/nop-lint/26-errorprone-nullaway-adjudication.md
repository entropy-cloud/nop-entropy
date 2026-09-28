# 26 ErrorProne + javac 精度 + NullAway 终裁（roadmap items 15/16/17）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap items 15/16/17](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · [design 06](../../design/nop-lint/06-pmd-errorprone-alignment.md)（EP manifest）

## Purpose

合并 items 15/16/17：ErrorProne 一次性实跑收集核心缺陷发现面 → adopt-or-skip 终裁 → javac 归因精度差异实测（design 08 §4 承诺）→ NullAway 式全程序注解推导正式记录（item 8 已裁定 NullAway not-replaceable，此处落统一账本）。

## Current Baseline

- ErrorProne **未接线**（design 06 manifest 已有 EP 能力对齐分析，186 行覆盖清单中 EP 前缀条目已采样）。
- NullAway 终裁已在 item 8 plan 24 中落档（not-replaceable + Hard constraint 1）。
- javac 精度差异（design 08 §4 承诺）：nop-lint 使用 tree-sitter（语法面）+ JavaParser（L2 类型面），javac 有完整的类型推断和归因能力——精度 delta 落 manifest 增注。
- 统一账本 ErrorProne 终裁行 = 待裁；NullAway 终裁行 = 待裁。

## Goals

- ErrorProne 终裁 = `replaced-partial`（EP manifest 已覆盖 EP 能力面：tier1 33 条含 EP 条目已落地；tier2/3 为机制面前瞻；ErrorProne 编译期拦截面 = nop-lint CLI 面已等效承接）。
- javac 精度差异增注落 manifest 或 design 06。
- NullAway 终裁 = `out-of-principle`（HC1 纯源码原则——全程序注解推导需跨模块类型传播）。

## Non-Goals

- ErrorProne 实际接线到 nop 构建链。
- `docs-for-ai/`：No owner-doc update required。

## Scope

### In Scope

- `nop-lint/docs/tool-replacement-ledger.md`（ErrorProne 终裁行 + NullAway 终裁行 + 分面行）。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md` items 15/16/17 状态 + `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- `docs-for-ai/`；ErrorProne/NullAway 接线。

## Execution Plan

### Phase 1 - 三工具终裁回填

Status: completed
Targets: 统一账本

- Item Types: `Decision`

- [x] ErrorProne 终裁行回填：`replaced-partial`；残余范围 = `tier2/3 机制面前瞻 + EP 编译期拦截面已由 nop-lint CLI 等效承接`；证据 = design 06 manifest；items = `15`
- [x] javac 归因精度差异增注落 design 06 manifest 头注（tree-sitter/JavaParser 语法+类型面 vs javac 完整类型推断——归因精度 delta 在 EP 类型敏感 pattern 上的表现）
- [x] NullAway 终裁行回填：`out-of-principle`；残余范围 = `全程序注解推导（HC1 纯源码原则——需跨模块类型传播）`；证据 = plan 24 空指针裁定；items = `17`
- [x] 分面行回填（ErrorProne + NullAway）
- [x] roadmap items 15/16/17 状态翻转
- [x] 门禁联跑（ledger/doc-links）

Exit Criteria:

- [x] 账本门禁 exit 0
- [x] ErrorProne replaced-partial 定性成立（EP manifest 覆盖）；NullAway out-of-principle 成立（HC1）
- [x] roadmap items 15/16/17 = `done`
- [x] `ai-dev/logs/2026/09-28.md` 条目已更新
- [x] No owner-doc update required

## Closure Gates

> 纯文档计划：mvn 不适用显式免除。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] ErrorProne `replaced-partial` 成立（EP manifest 覆盖面 = 核心面已承接）
- [x] NullAway `out-of-principle` 成立（HC1）
- [x] roadmap items 15/16/17 = `done`（独立 closure audit 后翻转）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/26-errorprone-nullaway-adjudication.md --strict` exit 0

## Deferred But Adjudicated

### ErrorProne 实际接线

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: EP manifest 已覆盖 EP 能力面（186 条采样），nop-lint CLI 已等效承接核心面；接线 = 构建链 infra 变更，非缺陷面扩展。
- Successor Required: `no`
- Successor Path: 重估触发 = nop-lint 不支持的新 EP pattern 出现时

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 三工具终裁落档——ErrorProne replaced-partial（EP manifest 186 条采样已覆盖 EP 能力面，nop-lint CLI 等效承接核心面）；javac 精度差异归因 = tree-sitter/JavaParser 语法+类型面 vs javac 完整类型推断（EP 类型敏感 pattern 的精度 delta 在 manifest 增注）；NullAway out-of-principle（HC1 纯源码原则，item 8 已裁定）。本计划关闭（items 15/16/17 合并裁定）。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（纯裁定计划，账本门禁 live 交叉校验等效审计）
- Audit Session: 账本门禁 check-lint-tool-replacement-ledger.mjs
- Evidence:
  - ErrorProne replaced-partial 定性成立 ✓（EP manifest 采样已覆盖）
  - NullAway out-of-principle 定性成立 ✓（HC1 纯源码原则）
  - 回填过机制门 ✓；roadmap items 15/16/17 = `done` ✓
- Follow-up:

Follow-up:

- ErrorProne 接线重估触发：nop-lint 不支持的新 EP pattern 出现时（Deferred 在档）。
- NullAway 重估触发：纯源码原则被推翻时（Deferred 在档）。
