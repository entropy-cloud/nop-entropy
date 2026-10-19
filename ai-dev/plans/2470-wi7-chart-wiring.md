# 2470 WI7 PDF 图表嵌入接线（桩 provider）

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI7，deps WI0 + 图表 provider）
> Related: `ai-dev/plans/2468-wi6-image-fidelity.md`（图片绘制路径）、`ai-dev/backlog/echarts-server-render-roadmap.md`（全部 WI 未启动）

## Purpose

报表图表进入 PDF：定义图表图片 provider SPI 并以桩实现打通 `PdfSheetRenderer.renderCharts` 管线（锚点→坐标→裁剪→绘制），使真实 echarts provider（echarts-server-render-roadmap 交付）可即插即用。

## Current Baseline

2026-10-19 逐项核对 live repo：

- PDF 渲染器**不渲染任何图表**：`PdfSheetRenderer` 只有 renderImages/renderTable，无 charts 消费。
- 模型链路已通：`IExcelSheet.getCharts()`（List<ExcelChartModel>）；`ExpandedSheet` 持有 charts 并暴露（ExpandedSheet.java:34,97）；`ExcelChartModel` 含 anchor（ExcelClientAnchor）+ testExpr。
- echarts-server-render-roadmap 全部 WI 未启动（todo），无真实图片 provider——按本 roadmap WI7 预案以桩 provider 打通管线。
- 图片绘制路径（WI6）：`PdfImageHelper.drawImage`（旋转/占位/裁剪）+ `PdfRenderer.getImage` 缓存可复用。
- 无任何 chart 相关 PDF 测试。

## Goals

- `IChartImageProvider` SPI（renderChart → PNG 字节）+ 静态注册器（默认桩 provider：程序合成占位图）。
- `PdfSheetRenderer.renderCharts`：镜像 renderImages（锚点→坐标→region 过滤→裁剪→`PdfImageHelper.drawImage`）。
- 测试：桩 provider 被调用（接线验证）、锚点几何、region 过滤、无 provider 注册时静默跳过、golden。

## Non-Goals

- 真实 echarts 渲染（echarts-server-render-roadmap 全权）；SVG 矢量路径（PDFBox 3 原生不支持 SVG，真实 provider 交付时自行栅格化为 PNG/SVG→位图，SPI 只约定 PNG 字节）。
- 图表数据模型/ChartModel 定义解析（charts 数据由模板侧承载，本计划只消费 anchor 几何）。

## Scope

### In Scope

- nop-report-pdf：`IChartImageProvider`、`ChartImageProviders`（注册器 + 默认桩）、`PdfSheetRenderer.renderCharts`。
- 测试：TestPdfChartRender（桩接线/几何/过滤/skip/golden）。
- `pdf-pagination.md` D9 裁定；roadmap/日志收口。

### Out Of Scope

- echarts 真实 provider、SVG、图表引擎本身；WI18 能力矩阵。

## Execution Plan

### Phase 1 - SPI + 桩 + renderCharts

Status: completed
Targets: `nop-report/nop-report-pdf`

- Item Types: `Feature`

- [x] `IChartImageProvider`（renderChart(ExcelChartModel, IExcelSheet, widthPt, heightPt) → ByteString PNG；返回 null 表示无法渲染）+ `ChartImageProviders`（static set/get；null → 默认桩：合成灰底蓝框 PNG + 深色角标图案，非空即可视觉辨识）。测试注册 provider 必须 try/finally 恢复（reuseForks 共享 JVM，防跨类泄漏）。
- [x] `PdfSheetRenderer.renderCharts`：在 renderImages 之后调用；镜像其结构——anchor 判空（缺 anchor 的 chart 防御跳过）、anchor 起点在 region 内、几何按 ExcelImage.calcSize 公式本地计算（nop-report-pdf 单模块内，经临时 ExcelImage 承载 provider PNG 复用 PdfImageHelper.drawImage）、页裁剪；provider 返回 null → 跳过并 LOG.warn（**不做无痕静默**，对齐 WI6 缺图先例）。
- [x] 测试：TestPdfChartRender——(a) 接线验证：自定义探测 provider 记录调用参数（chart/sheet/宽高）且渲染后图片出现（桩图案采样）；(b) 锚点几何：图表出现在锚点区域（几何内采样非白、区域外白）；(c) region 过滤：跨页图表只在锚点所在页/带绘制；(d) provider 返回 null 静默；(e) golden（桩图表页）。

Exit Criteria:

- [x] 测试全绿且 (a)-(e) 覆盖；**接线验证**：provider 真实被渲染管线调用（探测 provider 记录）
- [x] **视觉优先红线**：golden + 人工抽查
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] owner-doc 更新（pdf-pagination.md D9）由 Phase 2 承接
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-pagination.md 新增 D9：图表接线语义（SPI 形态、桩默认、PNG-only 裁定、SVG 由 provider 栅格化、真实 provider 即插即用路径、完成判定延后说明——WI7 勾选仅代表 PDF 侧管线打通，视觉对版待 echarts provider）。
- [x] roadmap WI7 勾选（**附注：PDF 侧接线完成，逐图视觉对版延后至 echarts provider**——按 roadmap"完成判定延后"预案如实标注）；check-doc-links/check-plan-checklist/scan-hollow 全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI7 勾选（含延后附注）
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] provider 接线有 focused 测试证明（探测 provider）
- [x] 受影响 owner docs 已同步（pdf-pagination.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2470-wi7-chart-wiring.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 真实 echarts 图片 provider

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: echarts-server-render-roadmap 全权负责（全部 WI 未启动）；本 plan 只交付 PDF 侧 SPI + 桩管线，roadmap WI7 预案明示"provider 未就绪时只完成 PDF 侧接线与桩验证，完成判定延后"。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/echarts-server-render-roadmap.md`（交付 provider 后在 renderCharts 即插即用）

### SVG 矢量图表路径

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: PDFBox 3 原生不支持 SVG；真实 provider 可自行栅格化为高 DPI PNG 满足 SPI。
- Successor Required: `no`
- Successor Path: 无（SPI 已覆盖栅格化产物）。

## Non-Blocking Follow-ups

- ExcelChartModel 增加 print 语义（出现需求时）。

## Closure

Status Note: WI7 完成：IChartImageProvider SPI + ChartImageProviders 注册器（默认桩）+ PdfSheetRenderer.renderCharts（锚点几何/region 过滤/WARN 跳过）+ 探测 provider 接线验证 + golden。roadmap WI7 勾选附"PDF 侧接线完成、视觉对版延后至 echarts provider"附注。独立子 agent closure audit 见下方 Evidence。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_6b581ebe-4e6c-4dc8-8b27-14ed9aabe10d，closure audit；agent_386b88ce-208b-4b95-9648-4401a16c355b 为执行期 draft 审查）
- Audit Session: agent_6b581ebe-4e6c-4dc8-8b27-14ed9aabe10d（2026-10-19）
- Evidence:
  - Phase 1/2 全部 Exit Criteria 经 live 代码、独立复跑测试（5/5）、工具三件套核实：renderCharts 真实被 renderSheet:384-385 调用；防御齐全（判空/region/WARN/临时 ExcelImage）；provider 探针真实断言
  - y 偏移疑点诚实性核实：git 证实 renderImages 同款偏移逻辑先于 WI7 存在（疑点非本计划引入），四处登记齐全，Non-Blocking 定级成立
  - 工具（审计实跑）：check-doc-links --strict exit 0；scan-hollow nop-report-pdf exit 0；check-plan-checklist exit 0
  - 审计 Process 项（已处置）：审计证据回填本段；交付物提交（紧随本收口执行）
- Deferred 项分类检查：echarts provider（successor ownership）、SVG（out-of-scope）诚实；无 in-scope live defect 被降级

Follow-up:

- 多页图片/图表 y 定位偏移疑点复核（复现：row45 图片探针，见 plan 偏差节）
- 真实 echarts provider（echarts-server-render-roadmap）

Follow-up:

- 多页图片/图表 y 定位偏移疑点复核（复现：DebugImageOffset 式 row45 图片探针，附 plan 偏差节）
- 真实 echarts provider（echarts-server-render-roadmap）

### 实现期关键偏差记录

- renderCharts 几何未用临时 ExcelImage.calcSize 而是内联同公式（锚点两角 + 单元格位置），经临时 ExcelImage 承载 PNG 复用 PdfImageHelper。
- 编程模板注意：sheet.getCharts() 缺省为不可变空 KeyedList（须 setCharts 注入且 chart 需要 name 键）；charts 须在 workbook.addSheet 冻结前加入。
- **发现多页图片/图表 y 定位偏移疑点**：region≠null 时图片/图表渲染位置较"锚点行×行高"推算值偏下（DebugImageOffset 复现：row 45 图片实测 top-based 220..298 vs 推算 180..260，含重复行场景）——图片路径（renderImages）同样存在，非本计划引入；因涉及重复行/预留几何的完整推导，登记 Non-Blocking Follow-up（含复现方式），不影响"图表出现在锚点页"的本计划交付判定。
