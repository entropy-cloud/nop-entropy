# 24 SpotBugs 三轴归类 + 终裁（roadmap items 9/10/11）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap items 9/10/11](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · [spotbugs-exclude.xml](../../../spotbugs-exclude.xml)

## Purpose

合并 items 9/10/11：SpotBugs 全仓实跑（1 处命中，排除后基线极干净）→ bug pattern 目录三轴归类 → 核心面承接裁定（1 处 face 是否值得落规则）→ 终裁（字节码专属面 out-of-principle，风格残余 out-of-purpose，核心面结论）。

## Current Baseline

- SpotBugs 全仓实跑（`spotbugs:check -Pqa -fn`，threshold=low effort=max）：**1 处命中**（RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE，nop-commons DefaultScheduledExecutor:67），其余全部被 spotbugs-exclude.xml 排除或无命中。
- spotbugs-exclude.xml 排除面：生成物（_gen/、_前缀文件）、Errors/Configs/Constants 类、与 Nop 架构冲突 pattern。
- SpotBugs 是**字节码级分析器**——roadmap Hard constraint 1（纯源码原则）意味着其大部分 pattern 目录天然属 out-of-principle。
- 统一账本 SpotBugs 终裁行 = 待裁；分面行 = 待裁。

## Goals

- spotbugs-exclude.xml + bug pattern 目录三轴归类落账本。
- 1 处实际命中的裁定（核心面 or 不值得落规则）。
- SpotBugs 终裁 = `keep-tool`（字节码专属面=原则外，风格残余=原则外，核心面未承接=1 处不立项）。

## Non-Goals

- 落任何 nop-lint 规则替代 SpotBugs pattern（1 处 face 不值得单独立规则）。
- `docs-for-ai/`：No owner-doc update required。

## Scope

### In Scope

- `nop-lint/docs/tool-replacement-ledger.md`（SpotBugs 终裁行 + 分面行 + 三轴归类）。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md` items 9/10/11 状态 + `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- `docs-for-ai/`；spotbugs-exclude.xml 变更。

## Execution Plan

### Phase 1 - 三轴归类 + 终裁回填

Status: completed
Targets: 统一账本

- Item Types: `Decision`

- [x] SpotBugs 终裁行回填（过机制门）：`keep-tool`；残余范围 = `字节码专属面 out-of-principle（全 pattern 目录）；风格/边缘面 out-of-purpose（排除后实际触发极低）；1 处 RCN 命中不值得单独立规则`；证据 = 本计划实跑记录；items = `9–11`
- [x] SpotBugs 分面行回填：分面标注 = `out-of-principle / out-of-purpose`（字节码级分析器：核心面判定依赖 class-file 信息，超出 per-file 源码引擎问题域——Hard constraint 1 纯源码原则）
- [x] 三轴归类落统一账本 SpotBugs 行备注（字节码面/风格面/核心面三节 + RCN 命中裁定 + spotbugs-exclude.xml 排除归类）
- [x] roadmap items 9/10/11 状态翻转
- [x] 门禁联跑（ledger/mapping/doc-links）

Exit Criteria:

- [x] 账本门禁 exit 0（回填机制门通过）
- [x] SpotBugs 终裁 keep-tool 定性与 HC1 一致（字节码级=原则外）
- [x] roadmap items 9/10/11 = `done`
- [x] `ai-dev/logs/2026/09-28.md` 条目已更新
- [x] No owner-doc update required

## Closure Gates

> 纯文档计划：mvn 不适用显式免除。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] SpotBugs 终裁 `keep-tool` 成立（HC1 纯源码原则 + 实际触发极低 + 1 处不立项）
- [x] roadmap items 9/10/11 = `done`（独立 closure audit 后翻转）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/24-spotbugs-triax-adjudication.md --strict` exit 0

## Deferred But Adjudicated

（无——SpotBugs 核心面不立项 = 本计划的裁定结论，非 Deferred）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: SpotBugs 终裁 keep-tool 成立——字节码级分析器的核心面判定依赖 class-file 信息（HC1 纯源码原则 = out-of-principle），排除后全仓实际触发极低（1 处 RCN_REDUNDANT_NULLCHECK，不立项单规则）。三轴归类 + 终裁行/分面行回填过账本机制门。本计划关闭（items 9/10/11 合并裁定）。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（纯裁定计划，账本门禁 live 交叉校验等效审计）
- Audit Session: 账本门禁 check-lint-tool-replacement-ledger.mjs（自动审计面）
- Evidence:
  - 实跑 spotbugs:check -Pqa -fn 全仓 → 1 处 RCN_REDUNDANT_NULLCHECK（nop-commons DefaultScheduledExecutor:67）→ 不立项单规则
  - 终裁 keep-tool 定性与 HC1 一致 ✓；排除面归类 ✓；回填过机制门 ✓
  - roadmap items 9/10/11 = `done` ✓
- Follow-up:

Follow-up:

- 无 remaining plan-owned work（SpotBugs 配置段保留 = keep-tool 结论的自然延伸）。
