# 20 PMD 工具级终裁回填：replaced-partial（roadmap item 4b）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 4](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [plan 19](19-pmd-facet-adjudication.md) · [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · [checkstyle-pmd-migration 账本](../../../nop-lint/docs/checkstyle-pmd-migration.md)

## Purpose

pmd 7 行收口完成（5 landed 对照零 diff + 1 deferred 机制缺口 + 1 out-of-purpose，plan 19）但判据未全达（deferred 行在）→ pmd 配置段**不处置**（保留 report-only 接线，服务 deferred 面与既有报告消费）。本计划完成 item 4 的收口动作：PMD 工具级终裁 `replaced-partial` 回填统一账本 + roadmap item 4 关闭。

## Current Baseline

- pmd 9 行终态：landed 7（EmptyCatchBlock/HardCodedCryptoKey 2 存量 + 5 新）/ out-of-purpose 1（JumbledIncrementer）/ deferred 1（ImplicitSwitchFallThrough，机制缺口：dataflow + falls-through 注释豁免，重估触发在档）。对照零 diff 在档（migration doc §4.3）。
- 判据（migration doc §3）：全部行 landed/out-of-purpose → 可移除；deferred 行在 → **pmd-ruleset.xml 不可移除**（与 checkstyle 侧已移除形成对比）。
- 终裁词表适配：`replaced-partial` = "核心面部分承接；未承接部分的归因（机制缺口 vs 原则外）逐行记录"——PMD 现态恰为此（核心面 5 规则承接对照零 diff；ImplicitSwitchFallThrough = 机制缺口逐行在档；JumbledIncrementer = 原则外/风格归档）。
- 统一账本回填机制门（plan 15）：终裁行须证据锚 + 残余范围非 —；分面行 token ∈ 四值词表。
- 测试/门禁基线全绿（plan 19 后）。

## Goals

- 统一账本：PMD 终裁行 = `replaced-partial`（残余范围 = ImplicitSwitchFallThrough deferred 面 + report-only 接线保留），证据锚 migration doc §2/§4.3，items = 4；分面行 = `core / out-of-purpose`。
- roadmap item 4 → `done`（独立 closure audit 后）；在用工具清单 PMD 行注记更新。

## Non-Goals

- pmd-ruleset.xml / pmd 插件处置（判据未达成；ImplicitSwitchFallThrough 重估落地后再议——归后续 plan）。
- `docs-for-ai/`：No owner-doc update required。

## Scope

### In Scope

- `nop-lint/docs/tool-replacement-ledger.md`（PMD 终裁行 + 分面行 + 终裁表标题"现全部待裁"失真修正——plan 18 后即已失真）。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md`（item 4 状态 + 工具清单行）。
- `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- pmd 配置处置、ImplicitSwitchFallThrough 重估实施（Deferred 在档）。

## Execution Plan

### Phase 1 - 终裁回填与 item 4 关闭

Status: completed
Targets: 统一账本、roadmap

- Item Types: `Decision`

- [x] PMD 终裁行回填（过账本机制门）：`replaced-partial`；残余范围 = `ImplicitSwitchFallThrough deferred 面（机制缺口）+ report-only 接线保留`；证据 = `[行级账本 §2/§4.3](./checkstyle-pmd-migration.md)`；items = `4`
- [x] PMD 分面行回填（四格钉死，含 deferred 行）：分面标注 = `core / out-of-purpose`（core 7 行承接 + 风格 1 行归档 + deferred 1 行机制缺口未入库）；依据/证据 = `[行级账本 §2/§4.3](./checkstyle-pmd-migration.md)`；重估触发 = `ImplicitSwitchFallThrough 表达力具备时（机制缺口）；风格面入 mandate 或对照被推翻`；状态列 = `replaced-partial`（镜像 Checkstyle 行先例）
- [x] roadmap：在用工具清单 PMD 行注记（report-only 保留，replaced-partial）并更正漂移行号（`pom.xml:460` 起，plan 18 移除 checkstyle 块后上移；SpotBugs/Sonar 行号漂移不在本计划面，记录不改）；item 4 状态翻转（done 待 audit）
- [x] 门禁联跑（ledger/mapping/doc-links）

Exit Criteria:

- [x] 账本门禁 exit 0（回填机制门通过）；mapping/doc-links exit 0
- [x] roadmap item 4 状态与工具清单行更新在档
- [x] `ai-dev/logs/2026/09-28.md` 条目已更新
- [x] No owner-doc update required

## Closure Gates

> 纯文档计划：构建验证不适用，显式免除；门禁验证 = ledger/mapping/doc-links 三查。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] PMD 终裁 `replaced-partial` 成立且与逐行收口事实一致（7/9 承接、机制缺口/风格归档归因逐行在档）
- [x] roadmap item 4 = `done`（独立 closure audit 后翻转）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/20-pmd-verdict-backfill.md --strict` exit 0

## Deferred But Adjudicated

（无——ImplicitSwitchFallThrough 的 Deferred 已在 plan 19/账本承载，本计划不重复登记）

## Non-Blocking Follow-ups

- ImplicitSwitchFallThrough 精确面重估（Deferred 在档）：落地后 pmd 判据可重开，配置段处置归后续 plan。

## Closure

Status Note: PMD 终裁 `replaced-partial` 回填完成且过账本回填机制门；终裁定性经审查逐字核证（7/8 核心行承接对照零 diff、deferred 机制缺口归因在档、out-of-purpose 风格行不入核心面计数、配置段保留依据三处在档）；roadmap item 4 关闭。本计划关闭。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_5ca3c725-f212-4e38-a249-bd1aca10a37c（fresh-session closure audit，APPROVE）
- Audit Session: agent_5ca3c725-f212-4e38-a249-bd1aca10a37c
- Audit 补记: plan 审查轮（agent_32fc5cb4）1 Major + 3 Minor 已在执行前消解；本 audit 覆盖执行后状态
- Evidence:
  - 单一疑点裁定（replaced-partial vs keep-tool/core-face-replaced）经审查逐字核证成立：keep-tool 需核心面大头未承接（实际 1/8）；core-face-replaced 需接线处置（deferred 在、接线保留）；replaced-partial 恰配
  - 回填列值过机制门：账本门禁 exit 0（终裁词表/证据锚 `](./` /残余范围非 —/items=4/分面 token）
  - 审查 Major-1（分面行四格钉死含 deferred 行与状态列）已按修订执行；Minor-1（roadmap PMD 行号 487→460 更正，SpotBugs/Sonar 漂移记录不改）与 Minor-2（终裁表标题失真修正）与 Minor-3（Purpose 措辞对齐）均消解
  - 门禁：ledger/mapping/doc-links exit 0；check-plan-checklist --strict exit 0（回写后）
  - audit 强制回写项执行：Closure Gates 三项勾选（含 L70 本 audit）、本 Evidence 段补记 fresh-session audit、日志 line 15 补"（回写后）"限定——audit 报告的 4 项前置全部完成
  - audit non-blocking 备注（§3:55 旧措辞"keep-pmd 7 行占多数"与分面行计数压缩措辞）记录在案，随该文档下次触碰修正
- Follow-up:

Follow-up:

- ImplicitSwitchFallThrough 精确面重估（Deferred 在档）：落地后 pmd 判据重开，配置段处置归后续 plan。
