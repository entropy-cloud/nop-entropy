# nop-pdf 版面感知页文本抽取

> Status: active
> Last Reviewed: 2026-10-19
> Source: plan 2485（`ai-dev/backlog/nop-pdf-report-export-roadmap.md` WI16）

## 决策

### D1 双模式空白契约（R1-M1）

- **strip 模式**（pdfio pageText :67-92 直译）：控制字符剥离（\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F）、`[ \t]+`→单空格、trim、maxChars。空白归一**仅此模式**。
- **layout 模式**（-layout 等价）：PDFTextStripper setSortByPosition(true) + writeString 内跨调用 x 间隙重建多空格（阈值 12pt、比例 gap/6、cap 48）。**保留重建的空白 run，不做归一**——headingize v1 leader 与多列排版依赖此形态。
- **已知限制**（R2-Major-2 实证）：字面空格字形 run 保留；**纯定位间隙坍缩为单空格**（PDFTextStripper writeLine 词间只写单分隔符）——无点导符且无字面多空格、仅靠定位排版的目录行在 toc-regex 路径系统性漏检，挂 WI16 对版 follow-up。

### D2 扫描件判定（R1-M3 裁定）

- 谓词：**rawCharCount==0**（锚前原始文本 trim 后长度）→ 扫描件硬错误（或 SKIP 标记）。
- 简化裁定：hasContents 区分被移除——真实 PDF 每页均有内容流，无内容流空白页极为罕见；保守行为（空白页也标 scanned）可接受。
- "图片占面"不入谓词——面积覆盖启发式移交 WI17 普查。
- vector-only 页（图表页无文字）同样判 scanned——命名偏差与后果记于此。
- scanPolicy=SKIP 跳过硬错误并在结果集标记 scannedPages（不静默）。

### D3 乱码双路信号（R1-M2 实证）

- **unmapped-glyph 事件计数**（主信号）：PDFTextStripper 子类 override showGlyph 统计 font.toUnicode(code)==null 事件。PDFBox 从不合成 U+FFFD（字节码实证：LegacyPDFStreamEngine 简单字体回退原始码点、Type0 静默丢弃）——主力乱码场景表现为字符静默消失。
- **U+FFFD 字面计数**（兜底）：外部工具产出的含 U+FFFD 文本经 writeString 统计。
- 信号定义即 WI17 机械统计的上游契约。

### D4 锚语义

- 页锚 `<a id="pdf-page-N"></a>` **独立成行前置**（保 WI15 行级回填语义）；由使用方/WI16 产出（nop-pdf 消费不生成——但本模块的 extractPageTexts 是产出方）。
- charCount 基于锚前原始文本（锚拼接后恒>0 会导致扫描判定失效——执行偏差记录）。
- `setLineSeparator("\n")` 显式固定（R1-MN1 平台一致性：Windows lineSeparator 含 \r\n）。

### D5 与 WI15 的关系

- `io.nop.pdf.toc.SortedPageTexts`（WI15）= sorted stripper 保留空白（无 layout 重建），定位间隙坍缩限制由本计划 layout 模式承接。
- toc 路径切换消费 layout 文本的时机：WI18 验收时组合裁定（当前 toc 消费 SortedPageTexts 形态）。

## 涉及实现

- `nop-pdf/.../text/`（PageTextOptions/PageTextResult/PageTextExtractResult/LayoutTextStripper/StripTextStripper/PageTextExtractor）
- `PdfDoc.java` extractPageTexts 门面
- PdfErrors 增 ERR_PDF_SCANNED_PAGE/ERR_PDF_GARBLED_TEXT

## 验证

- `TestPageTextExtraction`（9）：strip 锚+归一/maxChars/layout ≥2 空格/扫描硬错误+SKIP 标记/空白页/unmapped 计数/fffd policy/门面/锚独立成行
