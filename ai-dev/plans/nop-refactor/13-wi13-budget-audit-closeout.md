# 13 WI13 复杂度预算审计 + 收口——计数口径钉死 + docs 新建 + 能力目录发布

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M3 WI13 原文 + Cross-Cutting）；`ai-dev/design/nop-refactor/00-vision.md` §三.9（预算锚点 21,927 行/6 模块与计数口径）；`ai-dev/design/nop-refactor/01-architecture-baseline.md`（全部落地增注）
> Related: 全部前置 plan（01–12，completed）
> Review: R1(2026-09-26, fresh session agent_3177d8b6): 需修 1M+2m all fixed——nop-lint 侧改增量归属分解（零修改预判与 git 证据矛盾，实测 HEAD 22,543 vs 锚点 21,927=+616）；命令补 -not -path；锚点快照语义入 plan。实测 nop-refactor=4,010 行/33 文件远低于锚点。放行执行。

## Purpose

执行 roadmap WI13（Item Type: Proof）：复杂度预算审计收口——按钉死口径（main Java = src/main/java 全量、排除生成物与测试）实测 nop-refactor-core/java/graphql 三模块行数，对照 nop-lint 族上界锚点 21,927 行记录（超线则裁功能并回写 design）；docs-for-ai 模块文档新建；design 增注对齐 live 复核；能力目录（操作清单 × verification 契约）发布。M1+M2 全部 WI（01–12）已 completed，本 plan 是 roadmap 的最后一个 WI——完成后 roadmap 全勾。

## Current Baseline

（live 已核对，2026-09-26）

- **WI1–WI12 全部 completed**（roadmap 全勾，提交链 0b288247aa → 34ae9f0d09）：M0 依赖门与裁定、M1 codemod 面（WI3–WI8）、M2 rename 面（WI9–WI12）、四 action GraphQL 契约齐备。
- **增量实测快照**（R1 实测）：nop-refactor 三模块 main Java = **4,010 行 / 33 文件**（core 1,848 / java 1,619 / graphql 543）——远低于锚点，未超线；nop-lint 族 HEAD = 22,543 行（锚点 +616 漂移，归属分解归本 plan 实测步骤）。
- **docs-for-ai 现状**：无 nop-refactor 模块文档；`docs-for-ai/01-repo-map/module-groups.md` nop-refactor 行已在 WI9 增补三子模块。
- **design 增注现状**：00-vision 未动（原则层）；01-architecture-baseline 已有 WI6/WI7/WI8/WI9 P1+P2/WI10/WI11/WI12 落地增注与补记——对齐复核 = 增注与 live 无漂移的抽查证明。

## Goals

- **预算审计记录**：钉死口径实测（`find nop-refactor -path "*/src/main/java/*" -name "*.java" -not -path "*/target/*" | xargs wc -l`）× 三模块分列 + **nop-lint 侧增量行单列归属分解**（R1 修正：nop-lint 族 HEAD 实测 22,543 行 vs 锚点 21,927 = +616 漂移——归属分解为 nop-lint 自身 quality 增量 vs WI3–WI7 能力归属增量，git numstat 逐提交佐证）；对照结论落 `ai-dev/analysis/`。**锚点快照语义**：21,927 = 2026-09-25 vision 实测冻结快照，非 HEAD 恒等式——超线判定 = 能力归属增量是否击穿预算余量，而非 nop-lint 自身演化。
- **docs-for-ai 模块文档新建**：`docs-for-ai/03-modules/nop-refactor.md`——三件套"改"面定位、模块拓扑（core/java/graphql + 依赖方向）、四 action 使用契约（输入/载荷/verification 面/symbolIntact 语义）、runbook 指针；INDEX.md 路由增补。
- **design 对齐复核**：01-architecture-baseline 全部落地增注逐条与 live 抽查对照（每增注至少一条 live 证据），漂移即回写。
- **能力目录发布**：操作清单（Refactor__previewRewrite/applyRewrite/previewRename/applyRename + CLI preview/apply）× verification 契约（parseOk/errorNodeCount/residualDiagnostics/symbolIntact 语义 + nonApplied 四态）落 docs 模块文档内（单文档承载，不另立目录文件）。
- **roadmap 收口**：WI13 勾选 = roadmap 全部 WI 完成；记录终态（全部落地、无显式裁移项——Deferred 项已各归其主）。

## Non-Goals

- 不做任何 main Java 行为变更；不新增能力；不重写历史 plan。
- docs/（历史遗留目录）零触碰；nop-lint/nop-treesitter/nop-code 零修改。

## Scope

### In Scope

- `ai-dev/analysis/2026-09/2026-09-26-wi13-budget-audit.md`（实测数字 + 锚点对照 + 结论）
- `docs-for-ai/03-modules/nop-refactor.md` 新建（含能力目录节）+ `docs-for-ai/INDEX.md` 路由增补
- design 01 增注对齐复核记录（落 analysis 文档内）
- roadmap WI13 勾选 + 终态记录；ai-dev/logs 条目

### Out Of Scope

- 一切 main Java 变更；docs/ 目录；历史 plan 回写（规则 20）。

## Execution Plan

### Phase 1 - 实测审计 + docs 新建 + 对齐复核（Proof）

Status: planned
Targets: `ai-dev/analysis/2026-09/`、`docs-for-ai/03-modules/`、`docs-for-ai/INDEX.md`、roadmap

- Item Types: `Proof`

- [ ] 预算实测：钉死口径命令 + 三模块分列行数 + nop-lint 侧增量归属（git diff 佐证零行为面修改）+ 锚点对照结论
- [ ] `ai-dev/analysis/2026-09/2026-09-26-wi13-budget-audit.md` 落档（含 design 01 增注对齐复核抽查表——每条增注一条 live 证据）
- [ ] `docs-for-ai/03-modules/nop-refactor.md` 新建（定位/拓扑/四 action 契约/verification 载荷语义/nonApplied 四态/symbolIntact 语义/runbook 指针 + 能力目录节）+ INDEX.md 增补
- [ ] roadmap WI13 勾选 + 终态记录（全部 WI 落地、Deferred 项各归其主）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 实测命令可复跑且数字与 analysis 文档一致（repo-observable）
- [ ] **文档准确性**：docs 模块文档的四 action 契约与 live @BizModel 注解逐条对应；verification 载荷字段与 RefactorResult/Verification record 逐字段一致
- [ ] design 01 增注对齐复核完成，漂移为零（或漂移已回写）
- [ ] check-doc-links --strict 退出 0（docs 新建后全量复跑）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 in-scope 项完成，无残留未勾选 checklist
- [ ] 预算审计结论成立：实测数字复跑一致、锚点对照明确（超线裁功能/未超线记录余量）
- [ ] docs-for-ai 模块文档成立：四 action 契约与 live 逐条对应（文档准确性 Exit 项）
- [ ] 零行为红线：全部模块 main Java 零修改（本 plan 纯 Proof/docs）
- [ ] owner docs 已同步：docs-for-ai 新建 + INDEX 增补 + design 增注对齐
- [ ] **Anti-Hollow Check**：closure audit 已验证文档与 live 的一致性非复述（抽查对照表实证）
- [ ] `./mvnw test -pl nop-refactor/nop-refactor-core,nop-refactor/nop-refactor-java,nop-refactor/nop-refactor-graphql -am` 全绿（收口基线证明）
- [ ] vision 原则 1–9 回扣核对（closure audit 执行）：原则 9（预算收口）为终点核对项
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/13-wi13-budget-audit-closeout.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

（无——roadmap 终态后各 follow-up 已归后续 roadmap/design 层）

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
