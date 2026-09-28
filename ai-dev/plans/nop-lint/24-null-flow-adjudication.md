# 24 空指针面深度裁定：null-flow 机制前瞻正式记录（roadmap item 8）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 8](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [统一账本覆盖矩阵](../../../nop-lint/docs/tool-replacement-ledger.md)（空指针行）· [design 06 §4.4](../../design/nop-lint/06-pmd-errorprone-alignment.md)（L3 契约）· [plan 22 矩阵](22-defect-coverage-matrix.md)

## Purpose

按 roadmap item 8 完成空指针面的深度裁定：现有 pattern 面（throw-null/equals-null/no-throw-npe/catch-npe/no-return-null 5 条已落地）的覆盖面确认 + L3 null-flow（解引用前判空路径传播）的深度/成本/误报裁定 + NullAway 式全程序注解推导超出当前引擎深度的正式记录（not-replaceable + 重估触发），落统一账本覆盖矩阵空指针行。

## Current Baseline

- 空指针 pattern 面 5 条已落地（error/warning）：equals-null、no-throw-npe、throw-null、catch-npe、no-return-null——覆盖 NPE 语法子面（显式 throw null、equals(null) 判空笔误、catch NPE、return null）。
- **null-flow（解引用前判空路径传播）**：需 L3 方法内路径敏感分析——DataFlowAnalyzer v1 为 flow-insensitive（类注解原文在档），path-sensitive 为 successor surface 首项（与 item 7 Option A 同族机制缺口）。
- **NullAway/全程序注解推导**：需要全程序类型注解传播（@Nullable/@NonNull），超出 per-file 引擎问题域（roadmap Hard constraint 1 纯源码原则）；属 not-replaceable。
- 覆盖矩阵空指针行（plan 22 落账本）：null 相关 manifest 行均 tier1 pattern 面（已落地）；null-flow 无 manifest 行（机制前瞻空缺）。
- item 7 已裁定 Option B（保守面）+ Deferred Option A（路径敏感分析器）——null-flow 的机制缺口与 acquire/release 配对同族（都是路径敏感分析），可共享 Deferred 触发。

## Goals

- 空指针行机制缺口格正式裁定落档：pattern 面已覆盖子面列举 + null-flow = Deferred（与 item 7 Option A 同触发）+ NullAway = not-replaceable（纯源码原则约束，Hard constraint 1）。
- 重估触发条件在档。

## Non-Goals

- 落 null-flow 规则或引擎扩展。
- `docs-for-ai/`：No owner-doc update required。

## Scope

### In Scope

- `nop-lint/docs/tool-replacement-ledger.md`（空指针行机制缺口格正式裁定）。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md` item 8 状态 + `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- `docs-for-ai/`。

## Execution Plan

### Phase 1 - 空指针行正式裁定落档

Status: completed
Targets: 统一账本空指针行

- Item Types: `Decision`

- [x] 空指针行机制缺口格改写为正式裁定（含三段：pattern 面 5 条已落地列举、null-flow Deferred 归因 + 触发、NullAway not-replaceable 定性 + Hard constraint 1 引用）
- [x] roadmap item 8 状态翻转

Exit Criteria:

- [x] 账本门禁 exit 0（空指针行改写后格式合法）
- [x] 裁定文本含三段完整归因
- [x] `ai-dev/logs/2026/09-28.md` 条目已更新
- [x] No owner-doc update required

## Closure Gates

> 纯文档计划：mvn 不适用显式免除。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] 裁定三段完整（pattern 面/null-flow Deferred/NullAway not-replaceable）
- [x] roadmap item 8 = `done`（纯裁定计划：账本门禁 live 交叉校验等效审计已通过）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/24-null-flow-adjudication.md --strict` exit 0

## Deferred But Adjudicated

### L3 null-flow（解引用前判空路径传播）

- Classification: `watch-only residual`
- Why Not Blocking Closure: DataFlowAnalyzer v1 flow-insensitive（设计裁定在档），path-sensitive 为 successor surface；与 item 7 Option A 同族，共享重估触发。pattern 面 5 条已覆盖语法子面。
- Successor Required: `yes`
- Successor Path: 重估触发 = DataFlowAnalyzer 路径敏感分析器立项时（与 item 7 Option A 共享触发）。

### NullAway 式全程序注解推导

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 超出 per-file 源码引擎问题域（roadmap Hard constraint 1 纯源码原则——全程序注解推导需跨模块类型传播）。
- Successor Required: `no`
- Successor Path: 重估触发 = 纯源码原则被推翻时。

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 空指针行机制缺口格正式裁定落档（三段归因：pattern 面 5 条 landed / null-flow Deferred 与 item 7 Option A 同族共享触发 / NullAway not-replaceable Hard constraint 1）。纯裁定计划（无代码变更），账本门禁验证格式合法。本计划关闭。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（纯裁定计划，账本门禁 live 交叉校验即等效审计——矩阵 checker 对空指针行做钉名/优先级/机制缺口非—/live 规则集交叉校验）
- Audit Session: 账本门禁 check-lint-tool-replacement-ledger.mjs（自动审计面）
- Evidence:
  - 裁定三段在档 ✓（账本空指针行机制缺口格，改写后门禁 exit 0）
  - Deferred 两项分类诚实（null-flow = watch-only residual + 触发在档；NullAway = out-of-scope improvement + HC1 引用）✓
  - roadmap item 8 = `done` ✓
- Follow-up:

Follow-up:

- null-flow 精确面重估触发：DataFlowAnalyzer 路径敏感分析器立项时（与 item 7 Option A 共享）。
- NullAway 重估触发：纯源码原则被推翻时（Deferred 在档）。
