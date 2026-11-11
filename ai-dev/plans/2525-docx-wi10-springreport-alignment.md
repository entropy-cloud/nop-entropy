# 2525 docx roadmap WI10：springreport 能力对齐裁定

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI10）+ ai-dev/analysis/2026-11/2026-11-10-docx-reference-impl-index.md（WI0 对版矩阵）
> Related: ai-dev/plans/2515–2524

## Purpose

对照 springreport 三层能力逐项裁定归属（模型内 vs 外部），产出对齐结论文档；确认必要补齐（预期为零——逐项裁定显示核心能力已在 WI7/WI8 吸收）。

## Current Baseline

- WI0 对版矩阵已列 16 项能力归属（吸收/仅对版/禁用）与 springreport file:line 证据。
- WI1–WI9 落地后逐项现状（live code）：charts → WI8 图片绑定（echarts opt-in）；codes → WI8 codeBinding（qr/code128）；vertical/horizontal/group 扩展 → WI7 xpt 标记 `^/>/@`；watermark → 模型不承载 VML（WI5 生成不保真）；firstpageHeaderFooterShow（首页不同页眉页脚）→ 模型无字段（OOXML titlePg 语义未建模）；paramMerge → 报表运行控制语义，模型外。
- springreport 本地仓库 `~/sources/springreport` 可证。

## Goals

- 逐项裁定文档（6 项：firstpageHeaderFooterShow/watermark/charts/codes/扩展策略/paramMerge）落 `ai-dev/design/word-editor/springreport-alignment.md`：每项给 springreport 证据（file:line）、Nop 现状（live 锚点）、归属裁定（已进模型/留外部/延期补齐）与理由；拒绝复制其代码/依赖。
- owner doc 能力矩阵同步（首页页眉页脚/水印归属行）。

## Non-Goals

- firstpageHeaderFooterShow（titlePg）与水印的模型补齐实现（裁定为延期项——需求驱动，非本计划；登记 Deferred + successor 归属）。
- springreport 代码/依赖引入。

## Scope

### In Scope

- `ai-dev/design/word-editor/springreport-alignment.md` 新文档；owner doc 矩阵行更新；roadmap/日志。

### Out Of Scope

- 任何代码/xdef 变更。

## Execution Plan

### Phase 1 - 逐项裁定与文档

Status: completed
Targets: `ai-dev/design/word-editor/springreport-alignment.md`, `docs-for-ai/02-core-guides/word-document-model.md`

- Item Types: `Decision`

- [x] 裁定文档：6 项逐项（springreport 证据 / Nop 现状锚点 / 归属 / 理由 / Deferred 归属）；charts/codes/扩展策略三项标注「已进模型（WI7/WI8 锚点）」；firstpageHeaderFooterShow 裁定「应进模型，延期补齐（Deferred，successor 按需求立项）」；watermark 裁定「留外部/模型不承载 VML」（与 WI5 口径一致）；paramMerge 裁定「留外部（报表运行控制，应用层）」。
- [x] owner doc 矩阵增「首页不同页眉页脚（titlePg）」行（未支持 + Deferred 归属）与水印行口径核对。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

- [x] 裁定文档 6 项齐备且每项有 file:line 双侧证据。
- [x] owner doc 矩阵同步。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI10 勾选 + Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] 纯文档计划：无构建验证门（guide 允许）。
- [x] check-doc-links --strict 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

### 首页不同页眉页脚（titlePg）模型承载

- Classification: `watch-only residual`
- Why Not Blocking Closure: 模型变更（page 增 first header/footer + 解析/生成/PDF 三面接线）工作量大且无当前需求方；归属裁定已明确（应进模型）。
- Successor Required: `yes`
- Successor Path: 需求出现时立项（模型 + 三面接线）。

### 水印（VML）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 模型不承载原始 VML（WI5 口径）；生成/渲染场景按需求另议。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 六项逐项裁定落 ai-dev/design/word-editor/springreport-alignment.md（每项双侧 file:line 证据；锚点经本计划实跑复核）：charts/codes/扩展策略已进模型（WI8/WI7 锚点）；firstpageHeaderFooterShow 裁定应进模型延期补齐（Deferred + successor）；watermark/paramMerge 留外部；必要补齐为零。owner doc 矩阵增 titlePg 行 + 水印行归属口径（跨目录引用按 check-doc-links 边界规则改为文字指向）。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_c2ba0a24-18d8-4b43-a55e-4ab41c4a39a1）
- Audit Session: agent_c2ba0a24-18d8-4b43-a55e-4ab41c4a39a1
- Evidence: 六项裁定双侧锚点经本计划实跑复核（springreport DocTpl.java:80/52、DocTplSettings.java:90-92、ReportWordUtil.java:200-213 属实）；owner doc 矩阵 titlePg/水印行与裁定一致；check-doc-links --strict 0 error；check-plan-checklist --strict 0（evidence 本条写入后）；纯文档计划无构建门。Deferred 两项（titlePg 三面接线/水印）带理由非降级。
