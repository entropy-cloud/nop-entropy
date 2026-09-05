# nop-report PDF 导出视觉质量审计报告

> Status: resolved
> Date: 2026-09-05
> Scope: `nop-report/nop-report-pdf`（PDFBox 直接渲染路径），模板样本为 `nop-report-demo/_vfs/nop/report/demo/` 全部 20 个 `.xpt.xlsx`（base 01-18 + ext + performance）+ 1 个程序合成的 200 行分页用例
> Conclusion: PDF 导出管线对**简单表格**（03/04/05/07/08 等单页宽度内的报表）渲染质量良好（边框/背景/合并单元格/两级表头/链接样式均正确）；但存在 **7 项 P0/P1 级缺陷**——CJK 字体缺失导致默认中文报表完全无法导出、套打底图未按 print=false 排除、列拆分页无关键列重复、续页无重复表头、多行文本叠压、分页边界行文字重影、无样式单元格 NPE——以及一批 P2/P3 显示质量问题。修复计划见 `ai-dev/plans/2260-nop-report-pdf-quality.md`

## Context

- 用户要求：遍历系统现有报表模板 → 导出 PDF → AI 视觉识别显示效果 → 写分析报告 → 拟制完善计划（重点：PDF 显示效果与自动分页/拆分）。
- 审计方法：`TestPdfExportAudit`（nop-report-demo test 目录）批量渲染全部模板为 PDF，PDFBox `PDFRenderer` 逐页转 110dpi PNG（产出 `_tmp/report-pdf-audit/`，共 603 个文件），主会话逐张视觉检查 15+ 张关键页面，并对可疑缺陷回读渲染器源码定位根因候选。
- 字体前置处理：仓库未自带 CJK 字体。首轮导出 **17/20 模板直接失败**（`U+XXXX '.notdef' is not available in the font Helvetica, encoding: WinAnsiEncoding`）。为使审计能聚焦布局问题，测试通过 `VirtualFileSystem.updateInMemoryLayer` 注入系统 CJK 字体为 `/fonts/default.ttf`；**该字体缺失本身即为最重要的审计发现（P0）**。
- nop-report 无独立设计文档目录；PDF 渲染权威参考为 `docs-for-ai/03-runbooks/generate-report.md` 与 `nop-report-pdf` 源码。

## Analysis

### 渲染正常的能力面（视觉验证通过）

| 能力 | 证据页 |
|------|--------|
| 单页宽度内表格：边框、背景色、字体颜色、水平/垂直对齐 | 03-p1、05-p1 |
| 合并单元格（跨行"华东/2002"、跨列"合计"） | 04-p1、05-p1 |
| 两级表头（跨列主表头 + 子表头） | 03-p1、08-p1 |
| 交叉表双向展开 + 小计/合计 | 04-p1 |
| 超链接/排序标记样式（蓝色下划线、青色 ↑↓） | 07-p1 |
| 条码/二维码图片渲染 | 11-p1 |
| 套打数据文字对位（坐标换算准确） | 09-p1 |
| 大数据量端到端分页（534 页、28s 内完成全部 21 个用例） | 测试同比环比 |
| wrapText 多行文本在个别模板中正常（说明块） | 05-p1 |

### P0/P1 缺陷（按严重度排序）

#### F1 [P0] CJK 字体缺失 → 中文报表 PDF 导出默认完全不可用

- 现象：不注入字体时 17/20 模板抛 `IllegalArgumentException: U+96C6('.notdef') is not available in the font Helvetica`。
- 根因：`FontManager` 只认 base-14 字体与 VFS `/fonts/*.ttf|.otf`；仓库 `_vfs` 无任何字体文件；`getFont` 命中 base-14 名（或 null→Helvetica）后**直接返回，不检查字形覆盖**，PDFBox 在 `showText` 编码时才崩溃。
- 影响：所有中文模板的 PDF 导出开箱即坏；与 HTML/XLSX 渲染器（不依赖字体嵌入）形成能力落差。
- 修复方向：内置/自动发现 CJK 字体（系统字体目录扫描、`nop.report.pdf.font-dir` 配置、可插拔字体提供器 SPI）、字形覆盖检查 + 按字符回退、加载失败快速失败并给出可操作错误信息。

#### F2 [P1] 套打底图未按 `print=false` 排除（且文档与实现不一致）

- 现象：`09-套打-p1.png` 完整渲染了实习证明背景花纹图（该图 `fPrintsWithSheet="0"`，打印时应隐藏）。
- 根因：`PdfSheetRenderer.renderImages`（94-125 行）遍历 `sheet.getImages()` 时**没有任何 print 标志过滤**。
- 文档冲突：`docs-for-ai/03-runbooks/generate-report.md` 明确声称"PDF：PdfSheetRenderer.renderImages 按 print 标志排除背景图"——owner doc 与 live code 不一致。
- 修复方向：`renderImages` 增加 `image.isPrint()` 过滤；补 Owner doc 一致性核对。

#### F3 [P1] 列拆分页无关键列重复，产生不可读的孤立条带页

- 现象：01/02/03/06/16 等所有宽度超出打印区的报表，第 2 页（或末尾页）是只有右侧列的窄条（01-p2 的"张三/江苏省南京市/3.1"无任何行标题；03-p2 只有"其它资金"一列）。
- 根因：`PdfSheetRenderer.renderSheet` 用 `TableSplitHelper.splitTable(..., enableColumnPaging)` 按列切分后逐 region 独立成页，**无 Excel printTitles（左侧标题列重复）机制**。
- 连带效应：合并单元格（如跨全表宽的标题、跨列主表头）被切分边界截断后文字挤压叠印（03-p2 表头叠压）；标题单元格文字在 p1 上偏移到页右缘（01-p1 标题出现在右上角）。
- 修复方向：支持 `ExcelSheet` printTitles（重复标题行/列）模型字段；列拆分时默认重复左侧第一个字段列区；合并单元格跨拆分边界时的裁剪/重绘策略。

#### F4 [P1] 纵向续页无重复表头行、无页码

- 现象：synthetic-long-table p2-p5、测试同比环比的后续页均无表头行；全文档无页码。
- 根因：同 F3（splitTable 只做几何切分，无 printTitles 顶部行重复）；页眉页脚仅在模板显式配置 `pageSetup.header/footer` 时渲染，无默认页码 fallback，`&P` 等占位符支持情况未验证。
- 修复方向：printTitles 顶部行重复；默认页脚页码（可配置关闭）；页眉页脚变量（`&P/&N/&D`）求值。

#### F5 [P1] wrapText 多行文本叠压（多行画在同一 y）

- 现象：02-p2 说明块整段文字叠成一团；03-p2 表头叠压；测试同比环比末页说明块"1、环比"与上一行叠压。
- 根因候选（已定位到代码，需复现测试确认）：`PdfStyleHelper.drawWrappedText` 的 `lineHeight = font.getFontDescriptor().getCapHeight()/1000*fontSize*1.2`——PDType0Font（注入的 CJK TTF）的 FontDescriptor capHeight 可能为 0，lineHeight=0 时所有行画在同一 y。05-p1 说明块正常是因为该单元格未启用 wrapText（逐行独立单元格）。
- 修复方向：lineHeight 计算兜底（capHeight<=0 时用 fontSize*1.2）；多行文本绘制增加逐页回归测试。

#### F6 [P1] 分页边界行文字重影/叠压

- 现象：synthetic-long-table p1 底部（P34-P45）与 p2 顶部（P46-P48）行文字出现纵向重影，中段行正常。
- 根因候选：`TableSplitHelper.splitTable` 的 region 边界与 `getSubTable(region).clip()` 的行归属不一致，边界附近行被两个 region 重复绘制或 y 坐标计算错误；需写 200 行表的 renderer 级复现测试定位。
- 修复方向：切分算法与子表裁剪的单测锚定（边界行只能出现在一个 region）；渲染坐标以 region 内累计高度为准。

#### F7 [P1] 无样式/无字体单元格直接 NPE

- 现象：合成表初版（单元格无 styleId、style 无 font）抛 `NullPointerException: PdfTableRenderer.getStyleFont(...) is null`。
- 根因：`styleProvider.getDefaultFont()` 在 ForExcel 渲染路径（工作簿无默认字体配置）返回 null，`getFontSize` 直接解引用。
- 影响：任何未完整配置样式的工作簿（程序构建、导入模板）PDF 导出即崩。
- 修复方向：`getStyleFont/getDefaultFont` 空安全兜底（默认 10pt Helvetica/CJK 回退字体）。

### P2 缺陷

| # | 现象 | 根因候选 |
|---|------|----------|
| F8 | null 字体名永远走 Helvetica（注入 `/fonts/default.ttf` 也不经过它）——`PdfRenderer.getFont(null)`→`getDefaultFont()` 硬编码 base-14 | `FontManager.init` 的 defaultFont 不尝试 defaultFontResource |
| F9 | Integer 值列整列未渲染（synthetic 数量列 3,6,9... 全部缺失，Double 列正常）；`getText()=StringHelper.toString(getFormattedValue())` 静态读码未定位到差异，需复现测试 | 待根因确认（Phase 1 复现） |
| F10 | 无裁剪：长文本溢出单元格绘制（02-p2 标题在窄条内换行溢出、07 数据行文本压线） | drawUnwrappedText 无 clip |
| F11 | 图片越界不裁剪（11-p1 第一行二维码越过页顶被切） | renderImages 无页面边界 clip |
| F12 | 字体加载无 per-document 缓存（`FontManager.loadFont` 每次 getFont 都重新读资源加载）；TTF 无 bold/italic 变体（伪粗体缺失） | FontManager 设计 |
| F13 | 无默认页眉页脚/页码（依赖模板显式配置） | 同 F4 |
| F14 | 区块标题文字对比度可疑（01 区块标题深蓝字配浅蓝底）——需与模板原样式比对确认是颜色读取错误还是模板本身如此 | 待比对 |

### P3 观感问题

- 日期时间在窄列断行生硬（01-p1 "9:30-17:00" 断成 "17\n:00"）。
- 展开空行残留可见边框（08-p1 表尾空行）。
- 数据行行高不一致（07-p1 1001/1002 两行高度不等）。
- 性能模板 534 页 = 行分页 × 列分页乘积（宽表列拆分使页数翻倍，F3 修复后应减半）。

### 审计工具资产

- `nop-report-demo/src/test/java/io/nop/report/demo/TestPdfExportAudit.java`：20 模板 + 合成长表批量导出、逐页转 PNG、失败汇总；CJK 字体注入演示（`VirtualFileSystem.updateInMemoryLayer`）。
- 产出目录 `_tmp/report-pdf-audit/`（603 文件）供人工复核。
- 该测试同时是修复计划的**验收基线**：每个缺陷修复后对应页面应发生可断言的变化（配合 PDFBox 文本级断言 + 像素采样断言）。

## Conclusion

- nop-report 的 PDF 导出在"单页、简单表格"场景已可用（样式/合并/交叉表/图片渲染正确），但**生产级中文报表输出尚不可用**：F1 字体问题阻断导出，F2-F7 使分页打印场景（用户核心诉求）的产出不可读。
- 修复优先级：F1（字体）→ F7（NPE）→ F5/F6（叠压/重影，可信度阻断）→ F3/F4（printTitles 与页码）→ F2（套打）→ P2 项。
- 后续工作：完整缺陷清单与修复排期见 `ai-dev/plans/2260-nop-report-pdf-quality.md`；审计测试保留为回归基线。

## Open Questions

- [ ] F9（Integer 列缺失）根因：需在 Phase 1 用 `TestPdfExportAudit` 合成表写 renderer 级断言复现。
- [ ] F14（区块标题颜色）需与模板原 Excel 样式比对（可导出 HTML 渲染对照）。
- [ ] 页眉页脚 `&P/&N/&D/&A` 占位符当前是否支持（F4 修复时一并验证）。
- [ ] `fitToWidthAndHeight` 超大表单页压缩的可读性策略（是否需要最小字号阈值）。

## References

- 审计产出：`_tmp/report-pdf-audit/`（PNG/PDF）
- 审计测试：`nop-report/nop-report-demo/src/test/java/io/nop/report/demo/TestPdfExportAudit.java`
- 渲染器源码：`nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/renderer/`（PdfSheetRenderer/PdfTableRenderer/PdfStyleHelper）、`font/FontManager.java`
- 分页：`nop-kernel/nop-core/.../model/table/utils/TableSplitHelper.java`
- Runbook：`docs-for-ai/03-runbooks/generate-report.md`
