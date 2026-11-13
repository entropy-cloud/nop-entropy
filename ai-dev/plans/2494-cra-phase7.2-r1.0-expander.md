# 2494 CRA Phase 7.2 — R1.0 展开器：向 roadmap 追加 Phase 8 修复批次工作项行

> Plan Status: completed
> Last Reviewed: 2026-10-24
> Source: roadmap 工作项 7.2；R\*.0 展开机制沿 `ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md`（「R\*.0 的 plan 产物 = 向 roadmap 追加具体修复工作项行」）
> Related: plan 2493（7.1 done @ 5e589173f9）；消费产物 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md`（74 批次记录，SSOT）
> Draft Review: R1 agent_da0ca710（0 Blocker、5 Major[M1 §一偏离未显式裁定/M2 Deps 单元格文法未定死/M3 联动改写 advisory+Non-Goals 矛盾/M4 Owner Doc 混合格无规则/M5 输入 Status 行失效未声明]+3 Minor——全量采纳，修订后 active）

## Purpose

把 7.1 提案的 74 批次记录（R1-00 P0 确认 + R1×9 + R2×21 + R3×10 + R4×33 + R-G 治理批）追加为 roadmap `ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` Phase 8 表的具体工作项行（每行引用 finding ID 清单作为 Deps），使 Mission 循环可按行起草修复 plan。

## Current Baseline

- 前置：7.1 done @ 5e589173f9（closure audit PASS with corrections）；batch-proposal.md 74 批次记录含八字段 schema（批号/标题/finding ID 清单/模块+验证命令/Owner Doc 锚点/Protected Area 授权来源/依赖与并行标注/exit criteria）与 roadmap Phase 8 六列映射说明（§一）。
- roadmap Phase 8 现状：仅占位行「| 8.1+ | （动态追加）…」；排序原则注释块保留。
- finding ID 清单 SSOT = batch-proposal.md（roadmap 行内不复制全量清单——link, don't copy；行内以「锚点+项数」引用，禁止第二事实源）。
- 依赖序：batch-proposal §四.4 硬依赖 14 条+软依赖约 30 条全部指向更小批号；可并行批次已标注。**依赖边唯一清单 = §二 各批行「依赖/可并行」列的跨批项**（§四.4 仅为摘要，不作核对基准）。
- **输入状态声明（M5）**：batch-proposal.md 头部 Status 行「open……待独立对抗审查后交 7.2」为 2493 定稿前遗留措辞——其定稿事实以 plan 2493 closure 记录（对抗审查 8 维全过、closure audit PASS with corrections @ 5e589173f9）为准；本 plan 不修改该文件。

## 单元格文法裁定（M1/M2/M4/m1——执行与 closure audit 的唯一渲染基准）

- **对 §一 六列映射的两处有意覆盖（M1 显式裁定）**：①「finding ID 清单→Deps 全列分号分隔」按 AGENTS.md SSOT 规则 1 收敛为「锚点+项数」引用（74 批约 660 个 ID 内联必成第二事实源）；②缺口声明同时入 Owner Doc 单元格与批 exit criteria（E5），roadmap 单元格为承载位之一。本裁定为 roadmap 侧唯一口径。
- **Deps 列文法**：`batch-proposal.md §二 <批号>（<N> 项）；硬依赖 8.<x>（<批号>）[；软依赖 8.<y>（<批号>）]`。§二 批行内的正向对齐/联动注记（如 R1-08「R4-24 设计联动」）**一律不写入 Deps**（留在各批修复 plan 消费 §二 原行）；「7.2 + 前一批」占位依赖不再重复（roadmap Rule「7.2 完成前不得开始 Phase 8」承载）。
- **Owner Doc 列文法（M4）**：§二 批行 Owner Doc 锚点单元格**原文照抄**（其中已含 fallback 锚点与缺口声明）；仅当单元格无任何 `docs-for-ai/` 路径时整格填 `—（<缺口声明原文>）`。
- **R-G 行模板（m1）**：`| 8.74 | R-G 治理批（产出物：触发条款登记册/接线评审 checklist/可观测性补齐/死代码清理/owner doc 补齐/Gradle 通道决定） | todo | 见批 exit criteria（E5 缺口声明） | batch-proposal.md §五（14 项） | none（治理轨，产出物见 Work Item） |`。
- **抽验基准（M1）**：Deps 列核对 = 锚点批号存在于 §二 + 项数等于 §二 该批 finding 清单成员数 + 硬依赖边与 §二 该批行依赖列一致（§二.1–§二.4 + §五 共 74 为校验全集）。

## Goals

- roadmap Phase 8 表追加 74 行工作项（编号 8.1–8.74，按 §二.1–§二.4 + §五 文档顺序线性编号：8.1=R1-00 … 8.42–8.73=R4-01~33、8.74=R-G），替换占位行。
- 每行六列齐备，渲染唯一基准 = 本文「单元格文法裁定」节（Deps/Owner Doc/R-G 行文法与抽验基准）。
- 展开后机械校验（脚本钉死）：行数=74、批号集合=§二.1–4+§五 全集、Deps 三要素核对、硬依赖边全部指向更小编号、门禁退出码 0。

## Non-Goals

- 不执行任何修复；不修改 batch-proposal/cra-index/审计目录（只读输入）。
- 不把 finding ID 全量清单复制进 roadmap（SSOT 引用原则）。
- 不改动 roadmap 的依赖图 mermaid（粗粒度视图不因具体化失真，展开 74 节点反而违背 roadmap Rules「粗粒度分解」定位）——**例外：Work Item Status 块注释、头部 Last updated 日期、Phase 8 标题注记按 Phase 1 强制联动改写（M3）**。

## Scope

### In Scope

- `ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` Phase 8 表（占位行 → 74 具体行）+ 三处联动改写（M3）
- 本 plan 自身状态维护与 daily log

### Out Of Scope

- 修复执行、修复 plan 起草（Phase 8 各行各自立项）
- batch-proposal.md 及其他任何文件（其 Status:open 遗留措辞按 Current Baseline 声明处置，不改动）

## Execution Plan

### Phase 1 - 展开与校验

Status: completed
Targets: roadmap Phase 8 表

- Item Types: `Feature`

- [x] 由 batch-proposal.md §二.1–§二.4 + §五 共 74 批次记录按「单元格文法裁定」生成 74 行（8.1=R1-00、8.2–8.9=R1-01~08、8.10–8.30=R2-01~21、8.31–8.40=R3-01~10、8.41–8.73=R4-01~33、8.74=R-G），原子替换占位行「8.1+」
- [x] **机械校验脚本（m2，钉死）**：延用 `_tmp/check-batch-coverage.mjs` 模式新写 `check-roadmap-phase8.mjs`（脚本与输出落 `_tmp/`）——核对：行数=74、批号集合=§二.1–4+§五 全集、Deps 三要素（锚点存在/项数=§二 成员数/硬依赖边=§二 该批行跨批项且指向更小编号；正向注记不校验）、Status 全 todo、六列无缺项；输出快照计入 closure evidence（`_tmp/check-roadmap-phase8-output-2026-10-24.txt`，全项 PASS）
- [x] **联动改写（M3，强制）**：①Work Item Status 块注释「Phase 8 修复批次工作项由 7.2 展开器动态追加」→「Phase 8 修复批次工作项已由 7.2 展开器追加（plan 2494，2026-10-24，74 行）」；②roadmap 头部 `Last updated` → 执行日；③Phase 8 标题「### Phase 8 — 分批修复（7.2 后动态生成）」→「### Phase 8 — 分批修复（由 7.2 展开器生成，plan 2494）」
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] roadmap Phase 8 表含 74 行 todo 工作项，占位行已替换；抽 5 行按「单元格文法裁定」抽验基准核对一致（closure audit 独立解析器全量 74 行核验零违规）
- [x] 机械校验脚本全项 PASS（输出快照在案）；硬依赖边全部指向更小编号
- [x] 三处联动改写完成（状态块注释/Last updated/Phase 8 标题）
- [x] No owner-doc update required（roadmap 本身即本工作项产物；batch-proposal 为其 SSOT 输入不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - closure audit 与收口

Status: completed
Targets: roadmap、daily log、本 plan

- Item Types: `Proof`

- [x] 独立子代理 closure audit（行数/批号集合/六列完备性[按单元格文法裁定]/依赖序[仅跨批硬边]/与 batch-proposal 一致性抽验/门禁）
- [x] roadmap 7.2 → done；daily log 记录；checklist 全勾 + `check-plan-checklist.mjs --strict` 退出码 0

Exit Criteria:

- [x] closure audit PASS 记录在本 plan Closure 段
- [x] 两个门禁退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] roadmap Phase 8 表 74 行与 batch-proposal §二 一一对应（批号集合相等）
- [x] 每行六列齐备、Status 全 `todo`、Deps 引用成立且依赖序正确
- [x] 独立子 agent closure-audit 完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 本计划零产品代码变更（纯文档计划，豁免构建/测试门禁）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 本计划收口 CRA Phase 7.2 R1.0 展开器：74 批次工作项行（8.1–8.74）按「单元格文法裁定」追加 roadmap Phase 8 表并原子替换占位行，三处联动改写完成；机械校验脚本全项 PASS，独立 closure audit 全量核验零违规。零产品代码变更。roadmap 7.2 → done——**Phase 7 全部完成，Phase 8 分批修复（74 批）就绪**。
Completed: 2026-10-24

Closure Audit Evidence:

- Reviewer / Agent: agent_e47d1b8a（独立 closure audit 子代理，fresh session）
- Evidence:
  - 判定 **PASS with corrections**（2 项收口动作已完成：Major-1 daily log 7.2 条目已补、Minor-1 脚本输出快照已录 `_tmp/check-roadmap-phase8-output-2026-10-24.txt` + 本段引用）
  - 行数/集合：独立解析器核验 74 行、8.1–8.74 连续、批号集合=§二.1–4+§五 全集相等、占位行已替换
  - 单元格文法：抽 8 行逐列人工比对+全量 74 行机械核验违规 0；8.74=R-G 行与 m1 模板逐字符一致
  - 依赖边：审计员人工枚举期望边表（硬 14+软 35）与 roadmap 全量比对一致、全部指向更小 8.N、正向注记零混入
  - 联动改写：三处在位；git diff 四 hunk（+77/−4）确认依赖图 mermaid 与排序原则注释块未动
  - `node _tmp/check-roadmap-phase8.mjs` EXIT=0（复跑两次）；`node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2494-cra-phase7.2-r1.0-expander.md --strict` EXIT=0（收口后复核）
  - Deferred 项分类检查：无 deferred 条目

Follow-up:

- no remaining plan-owned work（Phase 8 各批修复由各行工作项各自立项，非本 plan 所有）
