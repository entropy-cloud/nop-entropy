# 2026-10-20 页眉页脚 &N 末位数字右缘被裁（runRect 紧宽 × 2pt 起笔内缩）

## Problem

- wi4 golden（page-numbers-page-3.png）页脚 "3 / 3" 的末位 "3" 右半缺失，垂直硬切边；首位 "3" 完整。
- 影响面：nop-report-pdf 页眉页脚延迟绘制路径（`drawDeferredHeaderFooters`）中所有"末字符带墨迹且 run 宽≈测量 advance"的 run；字面量并入前 run（如 "&P / " 宽敞）时前序 run 不显现。
- 独立视觉审计（ai-dev/testing/2026-10/2026-10-20-pdf-fidelity-visual-audit.md）watch-only 项，经像素测量升格确认。

## Diagnostic Method

- 6x 放大 golden 页脚：末位 "3" 右半被垂直切边，首位完整——排除渲染抖动。
- jshell+ImageIO 墨迹列分布：首位 "3" 8 列、斜杠 4 列、末位 "3" 仅 5 列（应 8 列）。
- 代码定位：`PdfRenderer.drawDeferredHeaderFooters` 给每个 run 构造 `runRect`（宽 = `getStringWidth/1000*size` 测量 advance），`PdfStyleHelper.drawUnwrappedText` 内 `calculateTextPosition` 默认左对齐**+2pt 起笔内缩**且 clip 到同一 rect。
- 定量吻合：10pt Helvetica 数字 advance 5.56pt，clip 窗 [x, x+5.56]，墨迹 [x+2.49, x+7.07] → 可见 3.07pt——实测 2.95pt（像素舍入内）。
- 被排除假设：外层分区 clip 切边（分区宽 174pt 远大于文本）；字体测量/绘制不一致（同 font 同 size）；非延迟路径与 PdfDoc.appendPageNumbers（stampText 居中绘制无紧 clip，不受影响）。

## Root Cause

- 单元格渲染惯例（左对齐 2pt 内边距）泄漏到 HF run 布局：runRect 是紧贴测量宽度的排版盒而非单元格，+2pt 内缩后字形右缘必然越过自身 clip 右缘（当右 bearing < 2pt，常规字号恒成立）。

## Fix

- `drawDeferredHeaderFooters` 的 runRect 宽度 +2pt：默认左对齐下笔位不变（x = cellX+2 与原一致），仅裁剪窗扩入 run 间空白；外层分区 clip 继续兜底 WI5"不越界绘制到相邻分区"语义。

## Tests

- `TestPdfHeaderFooterRender.testTrailingPageNumberGlyphNotClipped`：渲染 3 页取第 3 页（"3 / 3" 首末同字形），页脚带墨迹列聚类断言 3 簇且末簇 ≥ 首簇。红绿验证：无修复时 [8,4,5] FAIL；修复后 PASS。
- golden 重建：`-Dnop.pdf.golden.update=true` + 删除旧 golden 一次性 BASELINE_CREATED 重建（update 模式逐页比较遇 UPDATED 即断言失败的迭代问题由此规避）；干净复跑 10/10 绿。

## Affected Files

- `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/renderer/PdfRenderer.java`（runRect +2pt）
- `nop-report/nop-report-pdf/src/test/java/io/nop/report/pdf/renderer/TestPdfHeaderFooterRender.java`（回归测试）
- `_tmp/pdf-golden/wi4-header-footer/*.png`（6 张 golden 按修复后渲染重建，_tmp 不入库）

## Notes For Future Refactors

- `calculateTextPosition` 的 ±2pt 边距是**单元格**约定；任何把"测量宽度紧矩形"当作绘制盒传入 `drawUnwrappedText` 的调用方都会吃到同样的裁切，需自行加宽或改用无内缩通道。
- golden 失配修复后用"删除目录 + update 模式重建"一步到位；逐页 update 会因 UPDATED 状态断言失败而半途而废。
