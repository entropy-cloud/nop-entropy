# MG Guard 与知识沉淀（G.1 失败模式 → lessons / G.2 重复审计维度 → skills / G.3 docs-for-ai 与 design 文档收口）

> Plan Status: active
> Last Reviewed: 2026-08-05
> Draft Review: 2 轮独立子 agent 对抗性审查通过（第 1 轮 0 Blocker + 0 Major + 4 Minor 全部修复；第 2 轮复审 4 项修复全部 PASS，0 Blocker / 0 Major 残留，裁定可执行）。Session: ses_02f71cb81ffeIKd5aMFU0BCS6v / ses_02f6b5cd1ffe0vPaw7gLFVV20v。
> Mission: nop-metadata-audit-remediation
> Work Item: MG（G.1 新失败模式提升为 lessons；G.2 重复审计维度提升为 skills 新提示；G.3 更新 docs-for-ai/ 和 design 文档）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MG 里程碑）、`ai-dev/lessons/README.md`（编号规则 + 索引）、`ai-dev/skills/`（已有提示清单）、`ai-dev/design/nop-metadata/`（17+ 篇设计文档）、MV 验证产出（plan-2026-08-05-1408-2 输入清单）
> Related: 执行顺序 `{3}` of 3 — 启动门禁：MV done（roadmap V.1/V.2/V.3 → done，plan-2026-08-05-1408-2 completed）；本 plan 为**知识沉淀计划**（产出 = lessons/skills/docs 更新，无产品行为代码变更），是 nop-metadata-audit-remediation 路线图的收尾里程碑。

## Purpose

执行 roadmap MG 里程碑三项收口工作：G.1 把本路线图（M0.4 → MA1-MA7 → MR1-MR4 → MV）中新暴露的失败模式沉淀为 `ai-dev/lessons/` 新条目；G.2 把跨多轮审计反复使用的维度/流程提升为 `ai-dev/skills/` 新提示（或更新既有提示）；G.3 在全部修复落地后对 `docs-for-ai/` 与 `ai-dev/design/nop-metadata/` 做最终一致性收口（含 MR3 修复后 design 文档中可能出现的描述陈旧）。本 plan 使路线图产生的知识可复用，防止同类缺陷在后续模块审计中复发。

## Current Baseline

经 2026-08-05 live repo 核对：

- roadmap MG 行（G.1/G.2/G.3）状态全部 `todo`；**Deps：G.1-G.3 依赖 MV done（本 plan 启动时核查 roadmap V.1/V.2/V.3 → done + plan-2026-08-05-1408-2 completed，不满足则不启动并上报）**；MR1-MR4 全部 completed（启动时核查，draft 期为预期态）
- **`ai-dev/lessons/` 现状**：8 条教训（01-batch-memory-accumulation … 08-tool-executor-security-boundary），编号 `NN-简短标识.md`，索引表维护于 README.md；下一编号 = 09
- **`ai-dev/skills/` 现状**：24 项（deep-audit-prompts.md / orm-model-audit-prompt.md / cross-module-dependency-audit-prompt.md / design-doc-audit-prompt.md / open-ended-adversarial-review-prompt.md / unit-test-antipatterns.md / closure-audit-prompt.md / audit-remediation-roadmap-authoring-prompt.md / audit-remediation-verification-prompt.md / plan-reviewer-prompt.md / plan-closure-audit-prompt.md 等）——G.2 以**更新既有提示为主**（deep-audit-prompts.md 新增维度行、orm-model-audit-prompt.md 补 UK 发射核查），新增独立提示文件仅当维度确无归属
- **`ai-dev/design/nop-metadata/` 现状**：17+ 篇（00-vision / 01-architecture-baseline / 02-dto-module-restructure-decision / 02-gap-analysis / 03-version-management / 04-data-governance / 05-metadata-import / 06-data-quality-extended / 07-ai-integration / 08-reconciliation / 09-gap-analysis-extended / 10-event-model / 11-enterprise-semantic-layer / 12-data-contract-and-governance-workflow / aggctx-and-bizmodel-split / aggregation-processor-split / api-dto-spec / nop-metadata-roadmap / README）；MR3 R3.7 已批量修复 P2-MA5-101..184 文档 drift（25 项），MA5.1 报告 P3 级陈旧（记号/计数/行号级）未处理（roadmap 规则 1 维持 deferred）——G.3 的 design 收口以**修复落地后的新 drift** 与 P3 级陈旧中影响理解的部分为对象
- **`docs-for-ai/` 现状**：`03-modules/nop-metadata.md` + `01-repo-map/module-groups.md` + `04-reference/source-anchors.md` 已在 MR3 R3.7 同步（P2-MA5-201..205 修复）；G.3 复核 MR4 裁决是否引入新文档需求（预期：无——MR4 为裁决文档，未改变契约，除非提升项修复落地）
- **本路线图可沉淀的失败模式候选（G.1 输入，MV 后按实际验证补充）**：MA4.5 版权头 154 文件批量剥离；P2-MA5-401 getProp 恒 null 致"补日志≠修根因"（R2.11 只补 LOG.warn 未修根因，R3.6 才根因修复）；P2-MA6.6-001 DDL 零 UK 发射（`uniqueKey.constraint` 属性门——静态 DDL 供给部署失去全部 UK 保护且构建不报错）；MA7.3-01 计数勘误（grep 口径 69 vs 实际 36）；xwf listener 驳回即通过/发起人自批回归（R2.1 引入、R3.4 修复）；queryAggregation 11 参签名契约测试钉死 vs @RequestBean 规范张力；data-auth 双开关默认 false 下的旁路面归因（MA3.3 复核实证）；overclaimed-closure 已有教训 05 覆盖（不重复）
- **验证基线注意**：本 plan 为纯文档/知识沉淀计划，无代码变更；docs-for-ai/ 修改后必须跑 `node ai-dev/tools/check-doc-links.mjs --strict`（AGENTS.md 硬性要求）

## Goals

- G.1：新增 lessons 条目（编号 09+，按 README 规则 + 索引表追加），覆盖本路线图新暴露且未被既有 8 条覆盖的失败模式（候选：DDL 零 UK 发射静默缺失 / 补日志≠修根因 / 审计 grep 口径勘误 / xwf listener 驳回语义回归 等，以 MV 后实际结论为准），每条含触发场景 + 判定标准 + 预防措施
- G.2：更新 `ai-dev/skills/`（deep-audit-prompts.md 新增维度行 / orm-model-audit-prompt.md 补 UK 发射与 model-first 复核 / 或新增独立提示），覆盖本路线图反复使用且可复用的审计维度（DDL 生成产物审计、model-first 落地核对、xwf 流程语义审计、审计计数口径），复用面在 2+ 轮审计以上
- G.3：`docs-for-ai/` 与 `ai-dev/design/nop-metadata/` 与全部修复落地后的 live baseline 一致（复核 MR4 裁决影响 + MR3 修复后新 drift + 影响理解的 P3 级陈旧）；`check-doc-links.mjs --strict` exit 0
- roadmap G.1/G.2/G.3 → done，路线图收尾

## Non-Goals

- 不进行任何代码变更（本计划为知识沉淀；发现 live defect 时记录上报，不在此修复）
- 不重做 MR3 R3.7 已修复的文档 drift（25 项 P2-MA5-101..184 + 5 项 P2-MA5-201..205）
- 不新增无复用价值的提示文件（G.2 优先更新既有提示；新增仅当维度确无归属）
- 不回写已 completed 历史计划（Minimum Rule #20）
- 不处理 P3 级全量文档陈旧（只处理影响理解的项，其余维持 deferred 并记录）

## Scope

### In Scope

- G.1：lessons 新条目（编号 09+，按 MV 输入清单逐项核对既有教训覆盖后新增）+ README 索引更新
- G.2：skills 更新（deep-audit-prompts.md / orm-model-audit-prompt.md 等既有提示补充本路线图验证有效的维度；必要时新增独立提示）
- G.3：docs-for-ai/（03-modules/nop-metadata.md + module-groups.md + source-anchors.md，若 MR4 裁决有契约影响则同步）+ ai-dev/design/nop-metadata/（修复落地后的新 drift + 影响理解的 P3 级陈旧）+ check-doc-links exit 0
- roadmap G.1/G.2/G.3 → done + daily log 收口

### Out Of Scope

- 产品行为代码变更、新审计、P3 全量处理、历史计划回写

## Execution Plan

### Workstream 1 - G.1 新失败模式提升为 lessons

Status: planned
Targets: `ai-dev/lessons/`（新增 `09-*.md` 起条目）+ `ai-dev/lessons/README.md`（索引）

- Item Types: `Decision | Follow-up`

- [ ] 逐项核对 MV 输入清单中的失败模式候选与既有 8 条 lessons 的覆盖关系（如 overclaimed-closure 已由 05 覆盖 → 不重复；补日志≠修根因与 05 相关但焦点不同 → 评估独立条目）——覆盖矩阵记录
- [ ] 为确认未被覆盖的失败模式新增 lessons 条目（编号 09+，格式**参照既有条目（如 05-overclaimed-closure-fix-status-drift.md）的多节结构：触发场景 + 根因 + 正确做法 + 判定规则**，含本路线图修复实例作证据，不设死字数约束）——条目清单记录
- [ ] `ai-dev/lessons/README.md` 索引表追加新条目行——执行结果记录

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [ ] 新条目均基于本路线图实际发生的失败模式（有 live 证据引用：finding ID / plan / 报告），无泛泛而谈
- [ ] 无重复条目（既有 8 条覆盖的失败模式未重复新增；覆盖矩阵可追溯）
- [ ] README 索引与新增文件一致
- [ ] `No new test required`: 纯知识沉淀（无代码变更）
- [ ] 文档变化：`ai-dev/lessons/` 更新即本工作项本身（docs-for-ai 不变，`No owner-doc update required`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Workstream 2 - G.2 重复审计维度提升为 skills 新提示

Status: planned
Targets: `ai-dev/skills/`（deep-audit-prompts.md / orm-model-audit-prompt.md 等既有提示 + 必要的新增提示文件）

- Item Types: `Decision | Follow-up`

- [ ] 盘点本路线图（MA1-MA7 + MR1-MR4 + MV）中反复使用且可复用的审计维度/流程：DDL 生成产物审计（UK 发射核查、deploy/sql 三方言 DDL 断言）、model-first 落地核对（_gen/ 与源模型一致性、禁止手编生成产物）、xwf 流程语义审计（listener 结束判定、approve/reject 单一事实源）、审计计数口径（grep 含容器标签 vs 元素计数）、裁决通道记录（MA2.1/MR2/MR3 裁决先例防默认修 P2）——维度清单记录
- [ ] 逐项定位归属提示文件：deep-audit-prompts.md（21 维度表补行）/ orm-model-audit-prompt.md（补 UK 发射核查项）/ closure-audit-prompt.md（补 anti-hollow 调用链追踪）/ 新增独立提示（仅当维度确无归属且复用面 ≥2 轮）——归属矩阵记录
- [ ] 更新归属提示文件（新增检查项/维度行，标注来源 = 本路线图实例），新增提示文件按既有命名/格式（若新增）——执行结果记录

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [ ] 每个沉淀维度均有对应归属（既有提示更新或新增文件），可追溯
- [ ] 更新的提示内容包含本路线图的实际检查手段（如 `rg` 证据命令、断言测试模式），非空泛建议
- [ ] 新增文件（如适用时）命名/格式与既有 skills 一致
- [ ] `No new test required`: 纯知识沉淀（无代码变更）
- [ ] 文档变化：`ai-dev/skills/` 更新即本工作项本身（docs-for-ai 不变，`No owner-doc update required`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Workstream 3 - G.3 更新 docs-for-ai/ 和 design 文档

Status: planned
Targets: `docs-for-ai/03-modules/nop-metadata.md` + `docs-for-ai/01-repo-map/module-groups.md` + `docs-for-ai/04-reference/source-anchors.md`（若需）+ `ai-dev/design/nop-metadata/`（修复落地后的新 drift）

- Item Types: `Fix | Decision | Proof`

- [ ] 复核 MR4 裁决对 docs-for-ai/ 的契约影响（MR4 为裁决文档，预期无契约变化；若 MR4 提升项修复改变了 GraphQL/API 面则必须同步 owner doc）——影响评估记录
- [ ] 核对 MR3/MR4 修复落地后 `ai-dev/design/nop-metadata/` 的新 drift（如 R3.19 UK 修复后模型描述、R3.8 AggregationRowDTO 移除后 dto 描述、R3.15 改名后组件名）与影响理解的 P3 级陈旧（MA5.1 报告口径），修复为 live baseline——drift 清单与修复记录
- [ ] 复核 docs-for-ai 三文件与 live baseline 一致性（MR3 R3.7 后新增变化：修复计数、UK 现状、命名）——核对记录
- [ ] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（修改 docs-for-ai/ 后必跑，AGENTS.md 硬性要求）——exit code 记录
- [ ] 更新 roadmap G.3 行 → done——执行结果记录

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [ ] design 文档与修复落地后的 live baseline 一致（新增 drift 已修复；P3 级陈旧逐项裁决：修复 or 维持 deferred + 记录）
- [ ] docs-for-ai 三文件复核一致（MR4 契约影响评估结论记录：无影响或已同步）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `No new test required`: 纯文档更新（修复文档内容与 live 代码一致性）
- [ ] 文档变化：docs-for-ai/ + design 文档更新即本工作项本身
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Workstream 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：本 plan 不涉及任何代码变更（仅修改 `docs/`、`ai-dev/` 下文件），`./mvnw test`、`./mvnw lint` 等构建验证条目直接从 Closure Gates 中删除，不需要执行（guide 模板明确允许）。

- [ ] G.1 lessons 新增条目（无重复、有证据）完成
- [ ] G.2 skills 维度沉淀完成（归属可追溯）
- [ ] G.3 docs-for-ai/ + design 文档与 live baseline 一致（check-doc-links --strict exit 0）
- [ ] 不存在被静默降级到 deferred / follow-up 的文档缺口（G.3 逐项裁决记录）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（写入本 plan Closure 段）
- [ ] **Anti-Hollow Check**：closure audit 验证 lessons/skills 内容有 live 证据支撑（非空泛模板），docs 与 live 代码一致（抽查）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（closure 时必跑）

（纯文档计划：`./mvnw test` 等构建验证条目按 guide 模板"纯文档计划"条款直接删除，不执行）

## Deferred But Adjudicated

### 影响理解的 P3 级文档陈旧中维持 deferred 的项（G.3 裁决后填写）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: <<如 MA5.1 报告 P3 级纯记号/计数陈旧不影响理解，维持 deferred 并记录>>
- Successor Required: `no`（后续治理批次）
- Successor Path: —

## Non-Blocking Follow-ups

- MV 验证中记录的 pre-existing 失败（132b60979 xview.xdef 回归 / rocksdb flaky）处置归对应任务（本路线图已记录，不重复）
- 本路线图 P2-MA3-03 / P2-MA7.2-02 / P2-MA7.5-05 等 MR4 终局裁决的 successor（专门 data-auth / 多 schema / 调度可靠性 plan）不在本计划范围，维持 roadmap 登记

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - 每条 Workstream Exit Criteria 的验证结果（PASS/FAIL + 文件路径）
  - lessons 条目清单 + 覆盖矩阵结论
  - skills 归属矩阵结论
  - docs 一致性抽查结果 + `check-doc-links.mjs --strict` 退出码
  - Anti-Hollow 检查结果（lessons/skills 有 live 证据、docs 与代码一致）
  - `check-plan-checklist.mjs --strict` 退出码

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work（路线图 MR4/MV/MG 全部 done，nop-metadata-audit-remediation 收尾）>>

## Optional Sections

- `## Risks And Rollback`：G.2 新增提示可能与其他既有提示重复——以归属矩阵先行核对，重复则并入既有提示不新增文件；G.3 修改 docs-for-ai/ 后必须跑 check-doc-links（硬门禁），未跑不 closure
- `## Outdated Note`：lessons/skills/docs 内容以本路线图（MR1-MR4 全部落地后）的 live baseline 为准
