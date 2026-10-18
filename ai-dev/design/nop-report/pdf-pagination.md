# nop-report PDF 分页体系设计

> Status: active
> Last Reviewed: 2026-10-19
> Source: plan 2260 Phase 4（来源分析 `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md` F3/F4/F11/F13）；plan 2464（WI1 Print_Titles）；plan 2465（WI2 fit 缩放）

## 决策

### D1 重复标题行/列的承载方式

采用**两层声明，模型优先**：

- **模板级声明（优先）**：`ExcelPageSetup.repeatTitleRows`/`repeatTitleCols`（归一化 1-based 闭区间字符串，如 `"1:2"`/`"A:B"`），来自 xlsx workbook.xml 的 `_xlnm.Print_Titles` definedName（`localSheetId` 定位 sheet；行+列同设时 Excel 写出逗号分隔两段、各带 sheet 前缀，解析时剥前缀与 `$`，纯数字段为行、纯字母段为列）。解析器（nop-ooxml-xlsx `ExcelWorkbookParser`）对非法形态（单元格区域如 `$A$1:$B$2`、倒序、同型多段、缺失 localSheetId、重复定义）显式报错，不静默忽略。
- **全局配置缺省（回退）**：模型无声明时沿用 `nop.report.pdf.repeat-header-rows`（默认 1）/`nop.report.pdf.repeat-key-columns`（默认 1），0 关闭。
- **渲染限制（fail-loud）**：PDF 分页重复仅支持前缀声明（从首行/首列开始）；非前缀（如 `"3:4"`）在渲染入口即抛 `nop.err.report.pdf.unsupported-print-titles`，与是否实际分页无关。理由：非前缀重复要求拆分点控制增强（Excel 语义下标题块不可被页边界切开），属独立增强，出现真实需求时另立项。
- **拒绝项**：表头智能探测（自动识别表头块行数）——启发式不可靠，宁缺毋滥。
- Print_Titles 写回 xlsx 输出（工作簿级 definedNames 序列化）暂不实现，模板→PDF 链路无依赖。

### D2 分页与重复的协作

- 两遍拆分：先按完整打印区拆分，单页能容纳则不重复（避免"恰好一页"的表被误拆）；需要分页时按扣除重复块后的打印区重新拆分，保证续页内容不溢出页底。
- 重复声明的解析在 `renderSheet(IExcelSheet)` 入口一次性完成，拆分预留与续页/侧页/角块渲染使用同一解析结果。
- 续页渲染顺序：先绘制重复表头块，原点下移其高度；再绘制重复关键列块，原点右移其宽度；最后绘制本页区域内容。重复块与区域内容在同一变换空间内，共享页面设置（缩放/居中）。
- 行列同时拆分的内页绘制四块网格：角块（标题行×关键列）+ 标题行块 + 关键列块 + 内容块。
- 列拆分页的合并单元格被拆分边界裁剪：接受裁剪视觉（与 Excel 打印一致），不做跨页合并重建。

### D5 fit 缩放目标页数语义（WI2）

- **模型**：`fitToWidth`/`fitToHeight` 为**目标页数 int**（0/缺省=该维不约束），对齐 OOXML 页数语义（旧的 boolean 语义废弃——变更时仓库内无任何模板/代码使用，无破坏面）。任一维 >0 即启用 fit 模式，fit 优先于 `scale` 百分比（OOXML 中二者由 fitToPage 门控互斥）。
- **OOXML 解析**：`SheetNodeHandler` 解析 `pageSetup@scale/fitToWidth/fitToHeight`，以 `sheetPr>pageSetUpPr@fitToPage` 跨元素门控（handler 状态字段，SAX 序 sheetPr 先于 pageSetup）——仅 fitToPage=1 时 fit 属性生效（缺省 1×1 页），防止真实 Excel 文件的默认 `fitToWidth="1"` 误启用 fit。
- **缩放管线**：`renderSheet(IExcelSheet)` 入口一次解析 effective scale（fit 覆盖 scale，含最小字号收敛），分页容量与绘制共用同一 scale——修复了旧实现按未缩放打印区拆页导致 scale/fit 缩放下每页欠填的缺陷。fit 缩放后 regions==1 自然单页，无需旧的"双 true 单页快路径"。
- **容量口径**：与两遍拆分行为一致——分页时每页内容容量为 `(print - repeat)/scale`（重复标题块在所有页预留），故 fit 目标 N 页的约束为 `scale <= N*(print-repeat)/table`。fit 只缩小不放大（Excel 语义）；`1e-9` 相对容差消除"恰好填满"的浮点边界抖动。
- **最小可读字号**：`nop.report.pdf.min-font-size-pt`（默认 6pt，0 关闭）。缩放导致表格最小字号（口径与渲染同源：`PdfStyleHelper.normalizeFontSize`，缺省 10、`<=0` 回退 10）低于阈值时 WARN（`nop.report.pdf.min-font-size-clamped`）并把 scale 收敛到 `threshold/minFont`——目标页数让位于可读性。
- **列分页**：fitToWidth 语义下恒启用列分页（N 页宽目标正是靠列分页达成；宽度不约束时同样需要），旧的 `fitToWidth=false` 禁用分支随 boolean 语义废弃。
- fit×重复块：fit 缩放分页下续页/侧页仍按 D1/D2 重复标题块（`TestPdfFitScaleRender` 复验）。

### D3 默认页码

- `nop.report.pdf.default-page-footer`（默认 true）：文档页数 >1 且页面未显式配置页脚时，页底居中绘制"第 x / y 页"（全局页码）。
- 在全部页面渲染完成后统一绘制（需要总页数），使用 `PDPageContentStream.APPEND` 追加内容流。

### D4 图片页面裁剪

图片绘制裁剪到页面矩形内（`saveGraphicsState + addRect + clip + drawImage + restoreGraphicsState`），修复锚点越界图片（如二维码越过页顶）被裁切后仍绘制的问题。

## 已知限制（后续增强方向）

- 每工作表独立页码（当前为全局页码）。
- 页眉页脚占位符（`&P/&N/&D/&A`）求值——当前页脚为字面文本。
- 非前缀 Print_Titles 的渲染（见 D1 渲染限制）。
- Print_Titles 写回 xlsx 输出（见 D1）。

## 涉及实现

- `nop-ooxml-xlsx/.../parse/ExcelWorkbookParser.java`（Print_Titles definedNames 解析归一化）
- `nop-ooxml-xlsx/.../parse/SheetNodeHandler.java`（pageSetup scale/fit 属性 + pageSetUpPr fitToPage 门控）
- `nop-excel/.../print/ExcelPrintHelper.java`（title range 解析/前缀判定）
- `nop-excel` 模型：`ExcelPageSetup.repeatTitleRows/repeatTitleCols`、`fitToWidth/fitToHeight`（page-setup.xdef）
- `nop-report-pdf/.../renderer/PdfSheetRenderer.java`（两遍拆分、模型优先解析、重复块渲染、页脚标记）
- `nop-report-pdf/.../renderer/PdfRenderer.java`（页面跟踪、`drawDefaultPageNumbers`）
- `nop-report-pdf/.../renderer/PdfReportRenderer.java`（保存前绘制页码）
- `nop-report-pdf/.../utils/PdfImageHelper.java`（图片页面裁剪）
- 配置：`nop.report.pdf.repeat-header-rows`、`repeat-key-columns`、`default-page-footer`、`min-font-size-pt`

## 验证

- `TestExcelWorkbookParserPrintTitles`：Print_Titles 四形态归一化、非法形态报错、localSheetId 语义、calc.xlsx 端到端
- `TestExcelPrintHelperTitleRange`：range 解析/前缀判定
- `TestPdfPrintTitlesRender`：续页标题重复与数据完整性、模型优先于配置缺省、非前缀 fail-loud、golden+区域采样视觉验收
- `TestPdfFitScaleRender`：fit 2×1 目标页数（恰 2 页+关键列重复）、scale 页容量修正（scale=50 页数减半）、最小字号收敛（页数超目标）、fit×重复块、golden
- `TestSheetNodeHandlerPageSetup`：fitToPage 门控四形态
- `TestPdfRenderDefects`：F3（列拆分关键列重复）、F4（续页表头重复）红转绿；F6 回归钉（重复表头按需多次绘制，其余行只绘制一次）
- `TestPdfSheetRenderFixes`：单页小表不受影响（无重复、打印区平移不变）
- `TestPdfExportAudit`：20 模板批量导出复检
