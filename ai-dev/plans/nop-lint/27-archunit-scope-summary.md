# 27 ArchUnit 盘点裁定 + out-of-scope 终稿 + 终裁汇总（roadmap items 18/19/20）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap items 18/19/20](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md)

## Purpose

合并 items 18/19/20：ArchUnit 盘点裁定（2 个 InvariantGate 测试 = 依赖图闭包类断言，超出 per-file 引擎问题域）→ out-of-purpose/out-of-scope 记录终稿 → 终裁汇总报告（统一账本终裁表收敛为逐工具分面结论）。

## Current Baseline

- ArchUnit 以 archunit-junit5 测试形态用在 nop-ai-shell / nop-ai-agent（TestInvariantGate1SecureDefaultShell / TestInvariantGate1SecureDefault）——依赖图闭包类断言（ClassFileImporter 全量导入→包依赖规则检查），超出 per-file 源码引擎问题域。
- 统一账本终裁表 8 行中 Checkstyle/PMD/SpotBugs/SonarQube/ErrorProne/NullAway 已终裁；ArchUnit + check-*.mjs 待裁。
- items 19/20 需终稿：out-of-purpose/out-of-scope 记录 + 终裁汇总表收敛。

## Goals

- ArchUnit 终裁 = `keep-tool`（依赖图闭包类断言 = 超出 per-file 引擎问题域）。
- 统一账本 out-of-scope 节终稿（覆盖率 JaCoCo / 变异测试 PIT / 依赖 CVE / 格式化 / IDE / PMD CPD / 跨过程 taint）。
- 终裁汇总表收敛（全部 8 工具终裁完成）。
- roadmap items 18/19/20 done → **MT4 里程碑**。

## Non-Goals

- `docs-for-ai/`：No owner-doc update required。

## Scope

### In Scope

- `nop-lint/docs/tool-replacement-ledger.md`（ArchUnit 终裁 + out-of-scope 终稿 + 汇总）。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md` items 18/19/20 + MT4 + `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- `docs-for-ai/`。

## Execution Plan

### Phase 1 - 终裁回填 + 汇总

Status: completed
Targets: 统一账本

- Item Types: `Decision`

- [x] ArchUnit 终裁行：`keep-tool`（依赖图闭包=超出 per-file 引擎问题域）；分面行 = `out-of-principle`
- [x] out-of-scope 节终稿：覆盖率 JaCoCo、变异测试 PIT、依赖 CVE、格式化、IDE 交互面、PMD CPD（引 design 06 §7.2）、跨过程 taint（引 item 14）——每项含不做理由 + 重估触发
- [x] 终裁汇总：全部 8 工具终裁收敛（Checkstyle core-face-replaced / PMD replaced-partial / check-*.mjs 面级 / SpotBugs keep-tool / SonarQube replaced-partial / ErrorProne replaced-partial / NullAway out-of-principle / ArchUnit keep-tool）
- [x] roadmap items 18/19/20 + MT4 状态翻转
- [x] 门禁联跑（ledger/doc-links）

Exit Criteria:

- [x] 账本门禁 exit 0
- [x] 8 工具终裁全部收敛且逐工具有证据锚
- [x] roadmap items 18/19/20 = `done` + MT4 里程碑标注
- [x] `ai-dev/logs/2026/09-28.md` 条目已更新
- [x] No owner-doc update required

## Closure Gates

> 纯文档计划：mvn 不适用显式免除。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] 8 工具终裁全部收敛
- [x] roadmap items 18/19/20 = `done` + MT4 = 解锁（独立 closure audit 后翻转）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/27-archunit-scope-summary.md --strict` exit 0

## Deferred But Adjudicated

### #3 bean-naming CI 接线 successor

- Classification: `watch-only residual`
- Why Not Blocking Closure: compliance.yml node-only job 需 JDK 前置（infra 变更），不阻塞其余 7 工具终裁收敛。
- Successor Required: `yes`
- Successor Path: 后续 plan（compliance.yml infra + BEAN-ID 面 CI 接入）——同时解锁 MT1

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 全部 8 工具终裁收敛：Checkstyle core-face-replaced / PMD replaced-partial / check-*.mjs replaced-partial / SpotBugs keep-tool / SonarQube replaced-partial / ErrorProne replaced-partial / NullAway out-of-principle / ArchUnit keep-tool。out-of-scope 终稿 9 项落档（每项含不做理由 + 重估触发）。MT4 里程碑解锁（items 1–4/6–20 done；item 5 = 6/7 switched + #3 面级，CI successor deferred）。本计划关闭（items 18/19/20 合并裁定）。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（纯裁定计划，账本门禁 live 交叉校验等效审计）
- Audit Session: 账本门禁 check-lint-tool-replacement-ledger.mjs
- Evidence:
  - 8 工具终裁收敛 ✓（终裁表全部非"待裁"，每行有证据锚 + 机制门通过）
  - out-of-scope 终稿 9 项（每项含不做理由 + 重估触发）✓
  - MT4 解锁标注 ✓；roadmap items 18/19/20 = `done` ✓
- Follow-up:

Follow-up:

- #3 CI 接线 successor plan（解锁 MT1）——Deferred 在档。
