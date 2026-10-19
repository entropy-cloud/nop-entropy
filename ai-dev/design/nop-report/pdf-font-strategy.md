# nop-report PDF 字体策略

> Status: active
> Last Reviewed: 2026-10-19
> Source: plan 2260 Phase 2（来源分析 `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md` F1/F8/F12）；WI10 伪变体合成（`ai-dev/backlog/nop-pdf-report-export-roadmap.md`）

## 决策

### D1 字体来源与解析顺序

PDF 渲染时按以下顺序解析字体名：

1. **base-14 标准字体**（含 Times New Roman 等别名）——零成本、零依赖。
2. **VFS 字体目录** `/fonts/<name>.ttf|.otf`（既有机制）与 `/fonts/default.ttf`（默认字体资源）。
3. **字体目录自动发现**：`nop.report.pdf.font-dirs` 配置目录（逗号分隔）+ 系统字体目录（Windows `%WINDIR%/Fonts`；macOS `/System/Library/Fonts`、`/System/Library/Fonts/Supplemental`、`/Library/Fonts`；Linux `/usr/share/fonts`、`/usr/local/share/fonts`、`~/.fonts`，递归深度 4）。按文件名（去扩展名、去空格、小写）匹配；`.ttc` 集合取第一个字体。
4. **默认字体**：存在 `/fonts/default.ttf` 时默认字体（含 null 字体名）优先加载它（通常为 CJK 字体）；否则回退 base-14 Helvetica。

拒绝项：
- **仓库内置完整 CJK 字体**——体积（20MB+）不可接受；如需开箱即用可后续内置子集字体（仅含常用汉字的精简 TTF），属后续优化。
- **运行时按字符拼合多字体渲染（tex 风格）**——实现复杂度高；按"整段文本回退"已满足可读性。

### D2 字形回退与失败边界

- **绘制前编码检查**：`FontManager.canEncode(font, text)` 逐码点调用 `PDFont.encode`（负结果按字体+码点缓存）。
- **回退链**：`PdfRenderer.fontForText(text, font)`——当前字体不可编码时切换 CJK 回退字体；回退字体解析顺序：`nop.report.pdf.fallback-font` 配置名 → `/fonts/default.ttf` → 字体目录中按 CJK 字体名优先排序探测（`中` 字探测）。解析结果按文件缓存（JVM 级）。
- **快速失败边界**：所有可用字体都无法编码文本时抛 `nop.err.report.pdf.font-missing-glyph`（携带字体名与文本采样、字体配置指引）；回退字体不存在时抛 `nop.err.report.pdf.fallback-font-not-found`。**字体名缺失/未知不报错**（回退 base-14），仅字符无法编码时才回退/报错——保证 Arial 等常见字体名的模板行为不变。

### D3 环境前提

- 开发机（macOS/Windows/主流 Linux 桌面）系统自带 CJK 字体，自动发现即开箱可用。
- **精简 CI 容器无系统字体**：镜像需安装 CJK 字体包（如 Debian `fonts-wqy-microhei`），或在测试 classpath 的 `_vfs/fonts/default.ttf` 提供字体。字体相关测试依赖此前提（已在 `TestPdfFontResolution` 类注释与本文档声明）。

### D4 已知限制

- **ToUnicode 映射怪癖**：部分系统字体（如 mac 自带字体）的 ToUnicode 表把"文"映射为康熙部首形 U+2F8C——渲染显示正确，但文本复制/搜索得到兼容字符。属字体文件特性，不修复。
- **PDType0Font 与 PDDocument 绑定**：字体实例严禁跨文档缓存（`PdfRenderer.fontCache` 按文档持有；FontManager 只缓存字体文件路径，不缓存字体实例）。

### D5 伪粗体/伪斜体合成（WI10）

- **FontResolution**：`FontManager.resolveFont(fontName, bold, italic, doc)` 返回 `FontResolution(font, syntheticBold, syntheticItalic)`——变体命中（systemFonts 变体键 / VFS 变体资源含 `-Oblique` 命名 / 目录发现变体键）→ 标志 false；回退 base/默认/回退字体 → 被请求的维度标志 true。判定按解析链实际命中分支（`findDiscoveredFont` 会剥后缀回退 base，故以变体键精确命中为准）。
- **伪粗体**：`RenderingMode.FILL_STROKE`，描边宽度 `max(0.3, fontSize/30)` pt；描边色随字体色（同时 `setStrokingColor(textColor)` + 空 dash 重置——`drawBorderLine` 在 alpha=255 时不 save/restore，不重置会继承前格边框的红色虚线）；绘制后还原 `FILL`。
- **伪斜体**：文本矩阵 shear（`Tm` c 系数 0.25，y-up 右倾），unwrapped 逐文本、wrapped 逐行设置；常规文本走 `Td` 不受影响。
- **变体发现**：`getFontFullName` 探测 `<name>-Bold|-BoldItalic|-Italic`；italic 请求补 `-Oblique`（italic-only）/`-BoldOblique`（bold+italic）命名；**bold-only 请求不探测 `-Oblique`**（regular oblique 不得当 bold 变体）。
- **字形回退交互**：`fontForText` 切换回退字体后，synthetic 标志按请求维度重置为 true（回退字体无变体）。
- **ToUnicode**：`PDType0Font.load` 自动生成 ToUnicode CMap，复制/搜索按原文提取（`TestPdfFontFidelity` 钉住）；系统字体怪癖见 D4。

## 涉及实现

- `nop-report-pdf/.../font/FontManager.java`（目录发现/TTC/编码检查/回退解析/FontResolution/-Oblique 发现）
- `nop-report-pdf/.../renderer/PdfRenderer.java`（默认字体、`fontForText` 字形回退、回退字体缓存、FontResolution 解析缓存）
- `nop-report-pdf/.../renderer/PdfTableRenderer.java`、`PdfSheetRenderer.java`（绘制前字形回退调用点 + 合成标志透传）
- `nop-report-pdf/.../utils/PdfStyleHelper.java`（drawText unwrapped/wrapped 两路伪粗体/伪斜体应用）
- 错误码：`ReportPdfErrors.ERR_PDF_FONT_MISSING_GLYPH` / `ERR_PDF_FALLBACK_FONT_NOT_FOUND`
- 配置：`nop.report.pdf.font-dirs`、`nop.report.pdf.fallback-font`（`ReportPdfConfigs`）

## 验证

- `TestPdfFontResolution`：编码判定、目录发现、具名 CJK 字体/base-14/null 字体名三种输入渲染中文并提取
- `TestPdfFontFidelity`：FontResolution 标志判定（变体命中 vs 回退）、-Oblique 发现（VFS 注入）、伪粗体 `Tr 2` + 描边色/dash 重置守卫、真实变体无合成操作符、伪斜体 `Tm 0.25`、wrapped 路径合成、ToUnicode CJK 提取、golden
- `TestPdfRenderDefects`（VFS 注入路径）与 `TestPdfExportAudit`（20 模板批量导出）
