# 2498 WI10 非 PDF 导出格式扩展（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI10

## Purpose

新增 CSV/ODS/RTF/PNG 渲染类型，统一注册为 renderType 走 `IReportEngine`；平台无 POI/ODF 库（自完备约束），ODS 以 Flat ODF（.fods XML）、RTF 以基础 RTF 标记自研实现。

## Current Baseline

- renderType 经 beans `nopReportRenderer_*` collect 注册（`report-defaults.beans.xml`：xlsx/html/shtml；pdf 模块注册 pdf）。
- 展开结果 `IExcelSheetGenerator` → 各渲染器消费 expanded 模型；`IExcelTable` 行/列/单元格可遍历（HtmlTableOutput 读 `getText()` 先例）。
- 平台无 POI/ODF/RTF 库依赖（自完备设计约束禁引入大型库）。
- xlsx 渲染器已保留图表（ExpandedSheetChartGenerator）；透视（pivot）无支持。
- PNG 内核：nop-report-pdf 已有 compile 依赖 PDFBox（`PDFRenderer`），直接实现逐页渲染（`io.nop.pdf.verify.PdfImageRenderer` 在 nop-format/nop-pdf 且仅 test-scope，不复用）；`PdfReportRenderer implements IBinaryTemplateOutput` 可流式衔接。
- service `NopReportCacheBizModel.mimeType()` 需扩展 csv/fods/rtf/png 映射（In Scope 补 nop-report-service）。

## Goals

- `nop-report/nop-report-core`：CSV 渲染器（`renderType=csv`，RFC4180 转义，首 sheet）、**Flat ODF 渲染器（`renderType=fods`，扩展名 .fods，LibreOffice de facto 兼容——非 ODF 1.2 规范性打包，登记已知限制）**、RTF 渲染器（`rtf`，基础表格：全表统一细边框、unicode 转义）；均注册 beans。**取值/合并裁定**：单元格取 `getText()`（显示文本，与 HTML 一致）；CSV/RTF 合并区域被覆盖单元格输出空；fods 保留 `table:number-columns-spanned/rows-spanned`。png/fods 常量均放 `XptConstants`。
- `nop-report/nop-report-pdf`：PNG 渲染器（`png`，**PDFBox PDFRenderer 直接实现**（~30 行逐页 renderImage），不提升 nop-pdf 依赖、不复用 verify 工具链类；多页输出 zip）。
- 错误处理两档惯例；每格式测试（内容断言：CSV 行列/转义、ODS XML 可解析 + 单元格值、RTF 含文本、PNG 字节头 + 尺寸）。
- xlsx 图表保留既有能力回归断言；**透视（pivot）还原裁定 residual**（登记 design/roadmap）。
- owner doc、roadmap、logs 收口。

## Non-Goals

- 数据条等条件格式进 ODS/RTF；RTF 富样式保真；xlsx 原生透视表写出。

## Scope

### In Scope

- `nop-report/nop-report-core`（csv/fods/rtf 渲染器 + beans + 测试）、`nop-report/nop-report-pdf`（png 渲染器 + beans + 测试）、`nop-report/nop-report-service`（mimeType 扩展）
- design（并入 owner doc 或 `export-formats.md` 新建，执行时定一处）、roadmap、logs

### Out Of Scope

- pdf 高级项（姊妹 roadmap）；透视表写出；BI 发布。

## Execution Plan

### Phase 1 - 四个渲染器实现与测试

Status: completed
Targets: nop-report-core、nop-report-pdf

- Item Types: `Feature`

- [x] csv/fods/rtf 渲染器（getText() 取值；CSV RFC4180、fods XML 转义 + span 保留、RTF 统一细边框 + unicode 转义、合并覆盖格空）+ beans 注册 + XptConstants 常量
- [x] xlsx 图表保留回归测试（承载：nop-report-core 既有 xlsx 渲染测试扩展断言）——列入 Exit Criteria
- [x] png 渲染器（pdf 模块；PDFBox 逐页 PNG → zip，JdkZipTool）+ beans 注册 + 测试（zip 内页数/尺寸）


Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-core` 与 `-pl nop-report/nop-report-pdf` 绿
- [x] xlsx 图表保留回归测试在 Exit 内绿（断言图表部件存在）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 文档收口

Status: completed
Targets: docs

- Item Types: `Decision`

- [x] design/owner doc：renderType 清单（csv/fods/rtf/png）+ fods 非规范性打包与 RTF 基础表格已知限制 + 透视 residual；roadmap WI10 勾选；logs 收口

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 端到端：四 renderType 经 IReportEngine 渲染落盘（测试）
- [x] 无静默跳过（渲染异常按既有 NopException 惯例）
- [x] owner doc/design 同步（透视 residual）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录
- [x] `check-plan-checklist.mjs --strict` 收口运行

## Deferred But Adjudicated

### xlsx 原生透视表还原

- Classification: `watch-only residual`
- Why Not Blocking Closure: 平台无透视模型（展开引擎无 pivot 语义）；图表保留已由既有能力覆盖
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- CSV 多 sheet 导出（多文件 zip）；ODS 样式丰富

## Closure

Status Note: WI10 四种导出格式（csv/fods/rtf/png）全部交付并通过独立 closure audit；透视还原为 watch-only residual（见 Deferred But Adjudicated）。非阻塞 follow-up：CSV 多 sheet、ODS 样式。

Completed: 2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor subagent（新会话，ZCode，model bigmodel-individual-coding-plan/GLM-5.3-Flash），2026-10-22
- Evidence（逐条）:
  1. core PASS：`CsvReportRendererFactory`（RFC4180 引号/双引号转义、`\r\n`）、`FodsReportRendererFactory`（`table:number-columns-spanned/rows-spanned` 保留）、`RtfReportRendererFactory`（`\brdrw10` 四边细边框 + `\uN?` unicode 转义）、公共基类 `SingleSheetTableRenderer`（首 sheet、getText() 口径）均位于 `nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/renderer/`；`XptConstants` L79–L82 含 `RENDER_TYPE_CSV/FODS/RTF/PNG`。
  2. beans PASS：`report-defaults.beans.xml` 注册 `nopReportRenderer_csv/fods/rtf`（`ioc:collect-beans name-prefix="nopReportRenderer_"` L8）；`report-pdf-defaults.beans.xml` 注册 `nopReportRenderer_png`；bean id 后缀与常量取值逐一一致。
  3. 测试 PASS（本地实跑）：`./mvnw test -pl nop-report/nop-report-core -Dtest=TestExportFormats` → Tests run: 3, Failures: 0（testCsvEscaping/testFodsXml/testRtfText，经 `ReportEngine.getRendererForXptModel` 渲染并断言内容）；`./mvnw test -pl nop-report/nop-report-pdf -Dtest=TestPngExport` → Tests run: 1, Failures: 0（zip 字节头/页内尺寸断言）。
  4. png PASS：`PngReportRenderer`（PDFBox `PDFRenderer.renderImageWithDPI` 逐页 → zip，异常 `NopException.adapt`）+ `PngReportRendererFactory`；无静默跳过。
  5. service PASS：`NopReportCacheBizModel.mimeType()` 扩展 csv=text/csv、fods=application/vnd.oasis.opendocument.spreadsheet、rtf=application/rtf、png=application/zip；`./mvnw test -pl nop-report/nop-report-service -am` 退出码 0（连带 core/pdf 全模块测试绿）。
  6. 文档 PASS：owner doc `docs-for-ai/03-modules/nop-report.md` L19 renderType 清单（csv/fods/rtf/png + 透视还原 residual）与 L21 缺失项；roadmap `ai-dev/backlog/nop-report-capability-roadmap.md` L73 WI10 [x]；`ai-dev/logs/2026/10-22.md` 含 plan 2498 WI10 条目。
  7. 门禁 PASS（实跑）：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）；`check-plan-checklist.mjs --strict` 收口运行，8 项 Closure Gates 勾选后无错误。
  8. 文本一致性 PASS：Phase 1/Phase 2 Status: completed 与勾选项与 live repo 实况相符（实现者已勾选，audit 独立复核）。
  - Minor（不阻塞）：TestExportFormats/TestPngExport 经 ReportEngine 渲染但 renderer 以手工注入方式装配，未走 IoC collect 容器链路（bean 注册为静态核验）；plan L37 内联代码 `export-formats.md` 触发 1 条 doc-link warning（0 errors，退 0）。
