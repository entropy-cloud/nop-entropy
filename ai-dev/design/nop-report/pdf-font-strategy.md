# nop-report PDF 字体策略

> Status: active
> Last Reviewed: 2026-09-05
> Source: plan 2260 Phase 2（来源分析 `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md` F1/F8/F12）

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

- **bold/italic**：base-14 字体有真实变体；TTF/OTF 字体一律按 regular 渲染（伪粗体/斜体为后续优化，见 plan 2260 Deferred）。字体发现按 `<name>-Bold` 等后缀尝试后回退 regular。
- **ToUnicode 映射怪癖**：部分系统字体（如 mac 自带字体）的 ToUnicode 表把"文"映射为康熙部首形 U+2F8C——渲染显示正确，但文本复制/搜索得到兼容字符。属字体文件特性，不修复。
- **PDType0Font 与 PDDocument 绑定**：字体实例严禁跨文档缓存（`PdfRenderer.fontCache` 按文档持有；FontManager 只缓存字体文件路径，不缓存字体实例）。

## 涉及实现

- `nop-report-pdf/.../font/FontManager.java`（目录发现/TTC/编码检查/回退解析）
- `nop-report-pdf/.../renderer/PdfRenderer.java`（默认字体、`fontForText` 字形回退、回退字体缓存）
- `nop-report-pdf/.../renderer/PdfTableRenderer.java`、`PdfSheetRenderer.java`（绘制前字形回退调用点）
- 错误码：`ReportPdfErrors.ERR_PDF_FONT_MISSING_GLYPH` / `ERR_PDF_FALLBACK_FONT_NOT_FOUND`
- 配置：`nop.report.pdf.font-dirs`、`nop.report.pdf.fallback-font`（`ReportPdfConfigs`）

## 验证

- `TestPdfFontResolution`：编码判定、目录发现、具名 CJK 字体/base-14/null 字体名三种输入渲染中文并提取
- `TestPdfRenderDefects`（VFS 注入路径）与 `TestPdfExportAudit`（20 模板批量导出）
