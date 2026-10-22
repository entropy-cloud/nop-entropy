# 2488 WI0 能力矩阵与契约固化（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI0；`ai-dev/analysis/2026-10/2026-10-17-nop-report-feature-gap-vs-report-engines.md`
> Related: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（已完成，工具链与文档口径的来源）

## Purpose

落实 report roadmap WI0：把能力矩阵与参考索引收敛为 owner doc 增补，落三份设计契约草案，建立 HTML 打印/交互的视觉与行为验收基线。本计划只做文档与验收口径，不改任何引擎代码。

## Current Baseline

- `docs-for-ai/03-modules/nop-report.md` 已存在，功能概览已标注权限「预留未接线」，但无能力矩阵、无参数/缓存/交互契约、无已知限制清单。
- `ai-dev/design/nop-report/` 已有 5 份 PDF 侧设计文档（pagination/font/metadata/text-extraction/toc-structure），无参数模型、权限接线、HTML 分页契约。
- PDF 视觉验收工具链已落地：`nop-format/nop-pdf/src/main/java/io/nop/pdf/verify/`（`PdfGoldenManager`/`ImageDiff`/`RegionProbe`/`PdfImageRenderer`），并有验收口径裁定文档 `ai-dev/analysis/2026-10/2026-10-10-wi9-17-chart-visual-acceptance.md`。
- `nop-entropy-e2e/`（Playwright）存在，可承载浏览器侧截图验收。
- 能力矩阵与 25 维对照、P0/P1/P2 清单在 `ai-dev/analysis/2026-10/2026-10-17-nop-report-feature-gap-vs-report-engines.md`（时效性文档，open 状态）。
- 参考素材清单在 `~/sources/report-engines/README.md`（仓库外，owner doc 只能链接说明，不能承诺链接可达性）。

## Goals

- owner doc（`docs-for-ai/03-modules/nop-report.md`）**接管「nop-report 当前能力面 + 已知限制」的 canonical ownership**（规范性现状描述，不复制 25 维跨引擎对照表）；同时承载参数/权限/缓存/交互契约入口（链接 design 草案，每项 ≤3 行摘要 + 「以 design 为准」）与范围边界；勘误「报表结果文件缓存」表述（实体存在、链路未强制使用）。
- `ai-dev/design/nop-report/` 新增三份契约草案：`parameter-model.md`、`auth-wiring.md`、`html-pagination.md`（Decision 性质：方案、边界、拒绝的替代项）。
- 建立验收基线文档 `ai-dev/design/nop-report/html-acceptance-baseline.md`：HTML 打印/交互的视觉与行为验收口径——写成「HTML 打印/交互能力落地后应满足的契约/需求规格」，不带 Proposed vs Current 演进叙事；视觉侧 PNG/golden/diff **复用 `io.nop.pdf.verify` 的工具口径**（渲染 DPI/容差/区域采样的同一裁定），不建立 nop-report → nop-pdf 的测试依赖；行为侧用 headless 浏览器（Playwright）截图 + DOM 断言。供 WI4/WI5 及后续 WI 的 Exit Criteria 引用。

## Non-Goals

- 任何 `nop-report` / `nop-excel` 代码或模型变更（WI1–WI15 承载）。
- PDF 能力（姊妹 roadmap 已完成）、图表 provider、Word 文档模型、nop-datav D5/D6。
- 把 `~/sources/report-engines` 内容复制进仓库（只收敛参考索引为链接/摘要）。
- 为 HTML 验收基线新建 golden 快照与 diff 断言代码（WI4/WI5 执行时落地；本计划只固化口径与工具选型）。

## Scope

### In Scope

- `docs-for-ai/03-modules/nop-report.md` 增补（canonical ownership 移交：当前能力面 + 已知限制）
- `ai-dev/design/nop-report/parameter-model.md`、`auth-wiring.md`、`html-pagination.md`、`html-acceptance-baseline.md` 新建
- `ai-dev/design/nop-report/README.md` 新建（guide 要求子系统目录必备 README：结构/阅读顺序/职责边界/AGE 声明；当前该目录缺失）
- `ai-dev/design/README.md` 的 `nop-report/` 行同步（登记新增 4 份文档）
- analysis 文档 `2026-10-17-nop-report-feature-gap-vs-report-engines.md` 的 reconcile-on-touch：Status 由 open 收敛，25 维跨引擎对照矩阵保留为时效性证据并标注「当前能力面以 owner doc 为准」
- roadmap Work Item Status 中 WI0 勾选 + Milestone「契约与验收基线就绪」派生勾选
- `ai-dev/logs/2026/10-22.md` 记录

### Out Of Scope

- `docs-for-ai/INDEX.md` 路由变更（INDEX 仅路由 owner doc `03-modules/nop-report.md`，该行已存在；design/nop-report 由 `ai-dev/design/README.md` 索引，本计划已列入 In Scope）
- `docs-for-ai/04-reference/source-anchors.md`（无新代码锚点）

## Execution Plan

### Phase 1 - 设计契约草案与验收基线

Status: completed
Targets: `ai-dev/design/nop-report/`

- Item Types: `Decision`

- [x] 新建 `parameter-model.md`：参数内嵌 XPT 模型（不外挂 JSON）的契约——声明、取值 `${param.xxx}`、候选来源（SQL/字典/静态）、控件元数据、缓存键参与；拒绝替代方案（外挂 settings、仅 IEvalScope 硬塞）及理由
- [x] 新建 `auth-wiring.md`：渲染入口 + 取数标签统一授权检查的契约——消费 `NopReportDefinitionAuth`/`NopReportDatasourceAuth` + nop-auth 角色，未授权 `nop.err.report.*` 快速失败；拒绝替代方案（仅前端隐藏、仅 CRUD 层校验）及理由
- [x] 新建 `html-pagination.md`：`@page`/分页 CSS + repeat-header-rows/repeat-key-columns 语义的 HTML 等价契约，浏览器打印语境边界（PDF 归姊妹 roadmap）
- [x] 新建 `html-acceptance-baseline.md`：验收口径——视觉（headless 浏览器分页截图 + golden/PNG diff，复用 `io.nop.pdf.verify` 工具口径）+ 行为（DOM 断言超链接/排序/折叠）；定义后续 WI Exit Criteria 引用方式

Exit Criteria:

- [x] 四份文档存在且遵循 `ai-dev/design/00-design-writing-guide.md`（Decision 性质，无演进叙事、无日期/plan 状态叙事）；`ai-dev/design/nop-report/README.md` 存在且登记全部文档
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Owner doc 增补与 roadmap 状态收敛

Status: completed
Targets: `docs-for-ai/03-modules/nop-report.md`、`ai-dev/backlog/nop-report-capability-roadmap.md`

- Item Types: `Decision + Proof`

- [x] owner doc 增补并接管 canonical ownership：当前能力面（✅/🟡/❌ 现状，**不含逐引擎对照列**）、参数/权限/缓存/交互契约入口（链接 design 草案，每项 ≤3 行摘要 + 「以 design 为准」）、范围边界（六条姊妹 roadmap 不做清单）、已知限制清单；勘误「报表结果文件缓存」为「实体存在、引擎链路未接线」
- [x] analysis 文档头部收敛：Status 改为收敛说明（记录 owner doc 已接管当前能力面，矩阵保留为对照证据），链接 owner doc
- [x] `ai-dev/design/README.md` nop-report 行登记 4 份新文档
- [x] roadmap 勾选 WI0、派生勾选 Milestone「契约与验收基线就绪」、更新 Last updated
- [x] 当日 `ai-dev/logs/2026/10-22.md` 补 WI0 收口记录

Exit Criteria:

- [x] owner doc 与 live repo 一致（权限未接线、缓存未接线的表述与代码现状一致）
- [x] roadmap 状态块 WI0 已勾选
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯文档计划（仅 `docs-for-ai/`、`ai-dev/` 文件变更），构建验证按 guide 豁免。

- [x] 三份设计契约草案 + 验收基线落地且与 live repo 一致——抽查口径分两档：现状引用（类名/实体名/配置项，如 `NopReportDefinitionAuth`、`nop.err.report.pdf.*`）必须真实存在；目标态引用（如 WI1 将新增的 `nop.err.report.*` 授权错误码族）只要求文档显式标注为 WI1+ 交付目标，不要求当前存在
- [x] owner doc 增补完成且 canonical ownership 移交干净：owner doc 无逐引擎对照列、契约入口为 ≤3 行摘要 + 链接；analysis Status 已收敛且指向 owner doc（无双源）
- [x] roadmap WI0 与 Milestone 勾选，状态一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `ai-dev/logs/2026/10-22.md` 收口记录已写

## Closure

Status Note: 纯文档计划（设计契约草案 + 验收基线 + owner doc 能力面收敛），全部 Phase Exit Criteria 与 Closure Gates 经独立子 agent closure audit 逐条 PASS（2026-10-22）。WI1–WI15 属 roadmap 后续编排，非本 plan 遗留。
Completed: 2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 general-purpose 子 agent（closure audit 专程新会话，agent_e738d8d2-8587-4a61-a058-59e2410f04ba）
- Evidence:
  - Phase 1 Exit Criteria：三 PASS——四份契约草案 + 子系统 README 存在且 grep 零命中日期/WI/Proposed/analysis 引用；check-doc-links --strict 退 0（审计实跑）；logs 10-22 条目在位
  - Phase 2 Exit Criteria：四 PASS——实体存在（nop-report-dao entity/）且 core 渲染/取数路径 grep DefinitionAuth/DatasourceAuth/NopReportResultFile 均 0 命中（「未接线」表述与代码一致）；renderType 注册属实（report-defaults / report-pdf-defaults beans.xml）；roadmap WI0 + Milestone 已勾选；logs 在位
  - Closure Gates：全部 PASS——现状引用抽查实存（ReportPdfConfigs.repeat-header-rows 默认1、ExcelImage.print 消费点、XptWorkbookModel、xpt-rt.xlib 四标签）；目标态引用（nop.err.report.* 授权码族）已按两档口径标注为交付目标；canonical ownership 移交干净（owner doc 无逐引擎对照列、analysis Status converged 指回 owner doc）
  - check-plan-checklist --strict：退出码 0（收口时实跑）
  - scan-hollow-implementations：豁免——纯文档计划，无受影响代码模块
  - Deferred 项分类检查：无 deferred 项；follow-up = no remaining plan-owned work

Follow-up:

- no remaining plan-owned work
