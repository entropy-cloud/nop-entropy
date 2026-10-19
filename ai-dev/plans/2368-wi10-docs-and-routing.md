# 2368 WI10 文档与路由收口

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI10（M5）；依赖 WI0–WI9 全部完成
> Related: plan 2364–2367（已收口）

## Purpose

落地 roadmap WI10：docs-for-ai owner doc（图表静态渲染：模块边界、字体配置、option/ChartModel 双入口、opt-in 接入）+ docs-for-ai/INDEX.md 路由 + 01-repo-map/module-groups.md 登记 nop-canvas2d/nop-chart-echarts/nop-chart-report + 04-reference source-anchors；当日 ai-dev/logs/ 状态一致。收口后 roadmap 三个里程碑勾选（引擎可用基线已勾、JFreeChart 退役完成、17 图型对版通过）。

## Current Baseline

- WI0–WI9 已完成并提交（最新 d8a3ba4f5e）；模块全景：nop-canvas2d（Java2D Canvas2D shim）、nop-chart-echarts（GraalJS 引擎）、nop-chart-export（壳契约 + ChartModel→option 适配器）、nop-chart-report（opt-in provider，SPI 消费方）。
- docs-for-ai 惯例：owner doc 放 02-core-guides/（报表类先例 02-core-guides/reporting-and-notification-integration.md 与 03-runbooks/generate-report.md）；INDEX.md 按「任务 → 文档」路由表组织；module-groups.md 为模块分组表；04-reference/source-anchors.md 为「锚 ID → 源文件 → 说明」三列表。
- JFreeChart 已退役（nop-format/nop-dependencies 无 jfreechart）；xlsx 原生图表路径（ExpandedSheetChartGenerator→DrawingChartBuilder）未变，owner doc 需写明双路径边界。
- 字体配置面：EchartsEngine.configure(TextMeasurer, ...) / FontMapper 可注入（nop-canvas2d CanvasFontMapper）；缺字体显式报错。

## Goals

- 新 owner doc docs-for-ai/02-core-guides/chart-static-render.md：模块边界（四模块 + 依赖方向 + GraalJS 隔离）、字体配置、option 直通（EChartsRenderer.renderPng）与 ChartModel 入口（ChartExporter）双入口、opt-in 接入（nop-chart-report 依赖即启用；provider 缺席静默跳过）、对版基线与已知样式差异（引用 WI9 报告）。
- INDEX.md 增路由行（使用图表静态渲染 → owner doc）；module-groups.md 登记三新模块；source-anchors.md 增 CANVAS/CHART 锚。
- roadmap WI10 勾选 + 里程碑「JFreeChart 退役完成」「17 图型对版通过」勾选；当日 log 状态一致。

## Non-Goals

- 历史文档回写；docs/ 旧目录；性能文档。

## Execution Plan

### Phase 1 - owner doc + 路由 + 登记

Status: completed
Targets: docs-for-ai/02-core-guides/chart-static-render.md、docs-for-ai/INDEX.md、docs-for-ai/01-repo-map/module-groups.md、docs-for-ai/04-reference/source-anchors.md、ai-dev/backlog/echarts-server-render-roadmap.md

- Item Types: `Proof`

- [x] owner doc chart-static-render.md（内容面见 Goals，含 17/17 对版结论引用 + xlsx 原生路径零改动边界）
- [x] INDEX.md 路由行 + module-groups.md 三模块登记 + source-anchors.md 锚（IChartImageProvider/ChartExporter/EChartsRenderer/ExcelChartImageProvider/Java2DCanvas 等关键面）
- [x] roadmap WI10 勾选 + 两里程碑勾选
- [x] check-doc-links --strict 退出码 0；check-import-order 不适用（纯文档）；当日 log 条目

Exit Criteria:

- [x] owner doc 内容与 live 代码一致（模块名/类名/坐标真实存在，抽查核对）
- [x] INDEX.md/module-groups.md/source-anchors.md 更新且相互引用路径有效
- [x] roadmap WI10 + 里程碑勾选，全文件状态一致（WI0–WI10 全 [x]，三里程碑勾选）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 当日条目与 plan/roadmap 状态一致

## Closure Gates

- [x] WI10 scope 全落地（owner doc/INDEX/module-groups/source-anchors/log）
- [x] 纯文档计划：构建验证门按 guide 豁免（无代码变更；check-doc-links 为唯一工具门）
- [x] roadmap 全文件终检：WI0–WI10 全勾 + 三里程碑 + Purpose 硬边界无过期表述
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2368-wi10-docs-and-routing.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

-（暂无）

## Closure

Status Note: WI10 全部交付——owner doc（chart-static-render.md：模块边界/字体配置/双入口/opt-in/对版结论与已知差异）+ INDEX 路由 + module-groups 五行登记 + source-anchors 四锚 + roadmap 终态（WI0–WI10 全勾、三里程碑勾选）。closure audit（agent_f2d7481e）实证修正 1 处依赖链失实表述（nop-canvas2d 不依赖 nop-excel）并采纳 roadmap OptionNormalizer 陈旧类名修正；顺带修复 plan 2352 的 6 处断链（日志目录惯例迁移所致，doc-links 门禁要求，已在 log 记录）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_f2d7481e-e3d2-446c-8715-8e9157550310（与实现者不同会话）
- Evidence:
  - owner doc 内容 vs live 代码逐项核对 PASS（四模块 pom/职责表、双入口 API 签名、适配语义 D28-D40 抽查、字体配置、opt-in autoconfig 链、xlsx 双路径、17/17 对版结论与已知差异五项逐一对齐验收报告）；审计修正 1 处依赖链失实（canvas2d 仅依赖 nop-api-core）后 PASS
  - INDEX/module-groups（5 行）/source-anchors（CANVAS-001/ECHARTS-001/CHART-001/CHART-002）全部指向真实路径（审计者逐一验证）
  - roadmap 终检：WI0–WI10 全 [x]、三里程碑勾选、header 终态一致；OptionNormalizer→EChartsRenderer.normalize 陈旧类名修正（audit 建议）
  - 域边界：owner doc 零 markdown 链接、ai-dev 引用均为纯文本指引（先例核对）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；check-plan-checklist --strict 退出码 0（本证据回填后复验）；纯文档计划构建门按 guide 豁免（记录）
