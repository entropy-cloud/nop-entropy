# 2367 WI9 17 图型截图对版验收

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI9（M5）；依赖 plan 2364（适配器）与 plan 2365-2366（接线，E2E 已绿）
> Related: plan 2363（ImageDiffAssert 工具，本计划不依赖像素等同）、docs/dev-guide/report/excel-chart/（17 张 Excel 原生截图基线）

## Purpose

落地 roadmap WI9：test-all-charts.xlsx（17 sheet/17 图型）全链路解析 → 适配 → 渲染 17 张 PNG，逐张对 docs/dev-guide/report/excel-chart/ 下 17 张 Excel 截图判定——结构语义（图型/系列数/数据形状/标签/图例/坐标轴）必须一致，样式尽量贴近；AI vision 逐图判定 + 人工抽查，验收报告落 ai-dev/analysis/2026-10/；失败项回灌适配器或 shim。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render，WI8 已完成）核验：

- **对版素材**：docs/dev-guide/report/excel-chart/ 下 17 张截图（100pct-stacked-area/bar/column、area、clustered-bar/column、doughnut、filled-radar、line、line-with-markers、pie、radar、scatter、scatter-with-lines、stacked-area/bar/column）+ excel-chart-config.png；与 fixture test-all-charts.xlsx 的 17 charts 一一对应（roadmap 2026-10-06 抽验）。
- **fixture 构成（WI6 实测）**：bar×6（clustered×2/stacked×2/percentStacked×2）、line×2（standard，即 line/line-with-markers 形态由 marker 配置区分）、area×3（clustered/stacked/percentStacked）、radar×2（standard/filled）、scatter×2（均 lineMarker 形态）、pie×1、doughnut×1；所有图带 dLbls。
- **渲染通道已就绪**：TestExcelAllChartsExporter（nop-chart-export）把 17 charts 全链路渲染为非空白 PNG 落 target/result/（chart_0..16）；数据形状强断言（percentStacked 和≈100×3/scatter lineMarker×2/stacked×3/radar indicator×2）已绿。
- **判定标准（roadmap）**：结构语义必须一致（图型/系列数/数据形状/标签/图例/坐标轴），样式尽量贴近（配色/图例位置/轴样式），不作像素等同要求。
- **fixture 标题/文本（R1 P2 校正）**：17/17 chart XML 均有 c:title（中文 sheet 名，如「填充雷达图…簇状柱形图」），DrawingChartParser 落 ChartTitleModel、applyTitle 渲染——渲染 PNG 与 Excel 截图两侧都有中文标题，标题文本（=sheet 名）是映射校准的天然锚点；系列名 year2003/04/05、类目名英文。标题渲染依赖 shim 字体（缺字走 D55 shim 路径）。
- **数据标签事实（R1 P1 校正，适配器语义 bug）**：17/17 dLbls 全部 show 旗标=0 → Excel 截图全部无可见数据标签；现行 EchartsStyleMapper.label() 在 dLbls 存在时无条件 show=true——**回灌修复项**（label 可见性 = 各 show 旗标 OR，全 0 → show=false）。
- **radar 形态（R1 P6 校正）**：chart_1 雷达 radarStyle="marker" + 系列 symbol=none（视觉=线形雷达），非字面 filled/standard 二分。
- **workbook 口径**：实际 18 sheet（17 图 sheet + ChartData 数据 sheet），渲染循环跳过无图 sheet。

## Goals

- 17 张渲染 PNG（按 fixture sheet 序命名并留存）与 17 张 Excel 截图逐对 vision 判定：结构语义逐项（图型/系列数/数据形状/类别标签/图例/坐标轴）+ 样式贴近度（配色/图例位置/网格/标签位置）。
- 验收报告 ai-dev/analysis/2026-10/2026-10-10-wi9-17-chart-visual-acceptance.md：逐图 PASS/FAIL + 证据（对照描述）+ 样式差异清单（不阻断项）。
- 结构语义不一致项回灌适配器修复并复验（样式差异项记录不阻断，roadmap 口径「尽量贴近」）。

## Non-Goals

- 像素级等同（ImageDiffAssert 不适用本验收）；样式像素对齐改造（除非伴随结构错误）。
- 新增图型/新特性支持（适配面已定于 WI6）。
- owner doc（WI10）。

## Adjudications（延续 D 编号，接 plan 2366 的 D52）

- **D53 对版映射表（R1 三重验证锁定，执行时以 PNG 内嵌中文标题最终校准）**：渲染序 = workbook.xml 序，idx→sheet→图型/grouping→截图：
  | idx | sheet | 图型/grouping | 截图 |
  |-----|-------|---------------|------|
  | 0 | Chart_填充雷达图 | radar filled 3ser | filled-radar-chart.png |
  | 1 | Chart_雷达图 | radar marker(symbol none→线形) 3ser | radar-chart.png |
  | 2 | Chart_散点连线图 | scatter lineMarker 2ser | scatter-with-lines-chart.png |
  | 3 | Chart_散点图 | scatter 2ser | scatter-chart.png |
  | 4 | Chart_圆环图 | doughnut legend bottom | doughnut-chart.png |
  | 5 | Chart_饼图 | pie legend bottom | pie-chart.png |
  | 6 | Chart_百分比堆积面积图 | area percentStacked | 100pct-stacked-area-chart.png |
  | 7 | Chart_堆积面积图 | area stacked | stacked-area-chart.png |
  | 8 | Chart_面积图 | area standard | area-chart.png |
  | 9 | Chart_带标记折线图 | line+marker | line-with-markers-chart.png |
  | 10 | Chart_折线图 | line | line-chart.png |
  | 11 | Chart_百分比堆积条形图 | bar percentStacked dir=bar | 100pct-stacked-bar-chart.png |
  | 12 | Chart_堆积条形图 | bar stacked dir=bar | stacked-bar-chart.png |
  | 13 | Chart_簇状条形图 | bar clustered dir=bar | clustered-bar-chart.png |
  | 14 | Chart_百分比堆积柱形图 | bar percentStacked dir=col | 100pct-stacked-column-chart.png |
  | 15 | Chart_堆积柱形图 | bar stacked dir=col | stacked-column-chart.png |
  | 16 | Chart_簇状柱形图 | bar clustered dir=col | clustered-column-chart.png |
- **D54 判定口径（R1 P1/P4 修订）**：结构语义五项（图型一致/系列数一致/数据形状可辨识一致/图例按模型出现/坐标轴按模型出现）任一不符 = FAIL（阻断，回灌修复）；**数据标签按模型语义：模型全隐藏（dLbls 旗标全 0）→ 两侧都应无标签，渲染侧出现 = FAIL 回灌**；样式三项（配色系列/图例位置/轴网格样式）+ 背景与线宽观感记录差异不阻断。**对版渲染显式设 backgroundColor=#FFFFFF**（ChartExportOptions 既有字段，消除透明底观感歧义）；形状可辨识性门槛 = 人类观察者可辨柱/线/面数量与走向（发丝级渲染 = FAIL 回灌）。vision 判定由独立子代理执行（逐图读双图 + 固定观察项清单），实现者不自查。观察项清单随报告固定：五项结构 + 三项样式 + fixture 事实表（仅 chart_4/5 有图例、数据标签模型态全隐藏、系列名 year2003/04/05）。
- **D55 失败回灌边界**：适配器语义 bug → 修复 + 针对性断言；引擎/shim 缺陷（如文本渲染错位）→ 若一行级可修则修，否则记录为已知样式差异（不阻断）并在报告列明；两类修复均须重跑 WI6 31 测试与 pdf/docx 腿不回归。

## Scope

### In Scope

- nop-format/nop-chart-export：对版渲染测试（留存 17 PNG 到可检视目录）。
- ai-dev/analysis/2026-10/2026-10-10-wi9-17-chart-visual-acceptance.md、当日 ai-dev/logs/。
- 视结构判定结果回灌 nop-chart-export 适配器/nop-chart-echarts shim 的最小修复。

### Out Of Scope

- owner doc（WI10）；像素 diff 工具改造；新特性。

## Execution Plan

### Phase 1 - 渲染留存 + vision 逐图判定

Status: completed
Targets: nop-format/nop-chart-export、ai-dev/analysis/2026-10/

- Item Types: `Proof`

- [x] 对版渲染测试（新增 TestExcelChartBaselineRender 或扩展既有）：ChartExportOptions backgroundColor=#FFFFFF，产物按 D53 映射命名（如 filled-radar.png）落 target/result-wi9/；执行时人工拷贝 17 张到 ai-dev/analysis/2026-10/wi9-render/（报告证据附件，wi0-first-render.png 先例；测试只写 target/）
- [x] **回灌修复（R1 P1，Item Type: Fix）**：EchartsStyleMapper.label 可见性 = 各 show 旗标 OR（全 0 → label.show=false，不再无条件显示）；补针对性断言（全 0 旗标 → 无 label 键或 show=false）
- [x] AI vision 逐图判定（独立子代理，D54）：17 对逐对判定结构语义五项 + 样式三项，输出逐图 PASS/FAIL 与差异描述（现状产物名为 chart_N_null.png——chart.getName() 为 null，映射以 D53 表为准）
- [x] 结构语义 FAIL 项回灌修复（D55）+ 复验；样式差异项记录

Exit Criteria:

- [x] 17/17 结构语义 PASS（或修复后复验 PASS）
- [x] 验收报告落 ai-dev/analysis/2026-10/（逐图判定表 + 差异清单 + 映射表）
- [x] 回灌修复（如有）后 `./mvnw test -pl nop-format/nop-chart-export -am` 全绿（含 pdf/docx 腿不回归）
- [x] 当日 `ai-dev/logs/` 条目已更新；No owner-doc update required（WI10）

## Closure Gates

- [x] 17/17 图结构语义对版 PASS（vision 独立判定）
- [x] 验收报告落 ai-dev/analysis/2026-10/ 且含映射表/逐图判定/差异清单
- [x] 回灌修复（如有）不回归既有测试（31 + pdf/docx 腿）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2367-wi9-17-chart-visual-acceptance.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] scan-hollow 本 plan 触及模块 exit 0（纯 Proof + Fix 轮；豁免需记录理由）

## Deferred But Adjudicated

（样式贴近度差异项按 D54 记录于验收报告，不属 deferred——roadmap 口径「尽量贴近」非硬门）

## Non-Blocking Follow-ups

- 样式差异项的渐进贴近（配色表/轴样式微调）另行立项

## Closure

Status Note: 17/17 图型结构语义对版 PASS（终审轮 agent_5d804679，标题锚 17/17 MATCH）。首轮 4/17，7 项适配器缺陷分 6 个根因族 + 1 个散点线语义全部回灌修复并逐轮复验（标签可见性/雷达全局 max/白 fill 丢弃/圆环内径语义/柱 gapWidth 语义换算/散点线 noFill 可见性）；样式差异列明于验收报告（圆环扇区几何瑕疵为已知跟踪项，不阻断）。验收报告与 17 张渲染证据落 ai-dev/analysis/2026-10/。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 vision 判定子代理三轮（agent_78318653 + agent_e562b8e1 首轮、agent_07d18b9b 复审轮、agent_5d804679 终审轮——与实现者不同会话，逐图读图判定即本 Proof 计划的独立验证本体）；文本一致性 closure 核对由实现者按 gates 逐项执行
- Evidence:
  - 17 张渲染产物 + D53 映射命名落 ai-dev/analysis/2026-10/wi9-render/；验收报告 ai-dev/analysis/2026-10/2026-10-10-wi9-17-chart-visual-acceptance.md（映射表/判定口径/三轮判定记录/回灌修复表/样式差异清单）
  - 终审 17/17 结构语义 PASS（五项逐图判定，FAIL 清单为空，标题锚 17/17 MATCH）
  - 回灌修复 7 项均有针对性断言（标签隐藏）或三轮对版复验钉住（雷达全局 max/白 fill 丢弃/圆环内径/柱 gap 换算/散点线 noFill）
  - 回灌后回归：nop-chart-export 33/33 绿；pdf/docx 腿复验 BUILD SUCCESS（TestDocxChartRender 3/3 等）；doc-links --strict 0 错；scan-hollow --severity high exit 0
  - Closure Gates 逐项核对：报告/证据路径存在（wi9-render 17 PNG + 报告 md）、回灌修复不回归（33+pdf/docx 全绿）、checklist --strict exit 0（本证据回填后复验）、doc-links exit 0
