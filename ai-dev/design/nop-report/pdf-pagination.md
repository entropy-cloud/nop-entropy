# nop-report PDF 分页体系设计

> Status: active
> Last Reviewed: 2026-09-05
> Source: plan 2260 Phase 4（来源分析 `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md` F3/F4/F11/F13）

## 决策

### D1 重复标题行/列的承载方式

采用**方案 (b)：缺省行为 + 全局配置**，不引入模板级声明：

- `nop.report.pdf.repeat-header-rows`（默认 1）：行分页续页重复的表头行数，0 关闭。
- `nop.report.pdf.repeat-key-columns`（默认 1）：列拆分页重复的关键列数，0 关闭。
- 语义：表格第 1 行（行带拆分的续页）与第 1 列（列拆分的侧页）自动重复。首行/首列是绝大多数报表的表头/关键列位；覆盖非 0 配置可关闭。
- **拒绝项**：
  - *模板级声明（等价 Excel Print_Titles defined name）*——需要改动 `nop-ooxml-xlsx` 解析器与 xpt 模型（当时 Out of Scope），且用户需学习新配置面；作为后续增强（Successor）。
  - *表头智能探测（自动识别表头块行数）*——启发式不可靠，宁缺毋滥。

### D2 分页与重复的协作

- 两遍拆分：先按完整打印区拆分，单页能容纳则不重复（避免"恰好一页"的表被误拆）；需要分页时按扣除重复块后的打印区重新拆分，保证续页内容不溢出页底。
- 续页渲染顺序：先绘制重复表头块，原点下移其高度；再绘制重复关键列块，原点右移其宽度；最后绘制本页区域内容。重复块与区域内容在同一变换空间内，共享页面设置（缩放/居中）。
- 列拆分页的合并单元格被拆分边界裁剪：接受裁剪视觉（与 Excel 打印一致），不做跨页合并重建。

### D3 默认页码

- `nop.report.pdf.default-page-footer`（默认 true）：文档页数 >1 且页面未显式配置页脚时，页底居中绘制"第 x / y 页"（全局页码）。
- 在全部页面渲染完成后统一绘制（需要总页数），使用 `PDPageContentStream.APPEND` 追加内容流。

### D4 图片页面裁剪

图片绘制裁剪到页面矩形内（`saveGraphicsState + addRect + clip + drawImage + restoreGraphicsState`），修复锚点越界图片（如二维码越过页顶）被裁切后仍绘制的问题。

## 已知限制（后续增强方向）

- 每工作表独立页码（当前为全局页码）。
- 页眉页脚占位符（`&P/&N/&D/&A`）求值——当前页脚为字面文本。
- 模板级 Print_Titles 声明（见 D1 拒绝项）。
- 重复块的"左上角块"（行带×列带内部页的角落区域）不绘制。

## 涉及实现

- `nop-report-pdf/.../renderer/PdfSheetRenderer.java`（两遍拆分、重复块渲染、页脚标记）
- `nop-report-pdf/.../renderer/PdfRenderer.java`（页面跟踪、`drawDefaultPageNumbers`）
- `nop-report-pdf/.../renderer/PdfReportRenderer.java`（保存前绘制页码）
- `nop-report-pdf/.../utils/PdfImageHelper.java`（图片页面裁剪）
- 配置：`nop.report.pdf.repeat-header-rows`、`repeat-key-columns`、`default-page-footer`

## 验证

- `TestPdfRenderDefects`：F3（列拆分关键列重复）、F4（续页表头重复）红转绿；F6 回归钉（重复表头按需多次绘制，其余行只绘制一次）
- `TestPdfSheetRenderFixes`：单页小表不受影响（无重复、打印区平移不变）
- `TestPdfExportAudit`：20 模板批量导出复检
