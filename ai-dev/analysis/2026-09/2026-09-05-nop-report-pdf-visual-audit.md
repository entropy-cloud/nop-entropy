# nop-report PDF 导出视觉质量审计报告

> Status: resolved
> Date: 2026-09-05
> Scope: `nop-report/nop-report-pdf`（PDFBox 直接渲染路径），模板样本为 `nop-report-demo/_vfs/nop/report/demo/` 全部 20 个 `.xpt.xlsx`（base 01-18 + ext + performance）+ 1 个程序合成的 200 行分页用例
> Conclusion: PDF 导出管线对**简单表格**（03/04/05/07/08 等单页宽度内的报表）渲染质量良好（边框/背景/合并单元格/两级表头/链接样式均正确）；但存在 **7 项 P0/P1 级缺陷**——CJK 字体缺失导致默认中文报表完全无法导出、套打底图未按 print=false 排除、列拆分页无关键列重复、续页无重复表头、多行文本叠压、分页边界行文字重影、无样式单元格 NPE——以及一批 P2/P3 显示质量问题。修复计划见 `ai-dev/plans/2260-nop-report-pdf-quality.md`
> Note: F7/F12 两项经计划评审后基线修订（F7 已由提交 e2b02af86e 修复、F12 缓存已存在），见对应条目的"基线修订"标注；修复排期以 plan 2260 为准

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

#### F5 [P1→降级] wrapText 多行文本叠压（基线修订：主因证伪为 F10 溢出）

- 现象：02-p2 说明块整段文字叠成一团；03-p2 表头叠压；测试同比环比末页说明块文字叠压。
- **Phase 1 复现结论（plan 2260）**：`PDFTextStripper` 逐字形 y 坐标断言证明 wrapText 行距正常（ArialUnicode capHeight=750 → lineHeight≈9pt，无 0 行距问题）；最初的 capHeight=0 假设不成立。02-p2 的"叠压"实为 **F10 无裁剪溢出 + 窄列拆分**的组合表现：说明单元格被列拆分裁成极窄 → 每行只容 1 字 → 60 行文本从单元格向下无界延伸，盖住整页并与其他单元格文字交错（提取证据："多EN行D"交错）。视觉上的"密排叠压"= 9pt 行距配 10pt 字形的轻微压线 + 溢出交叠。
- 处置：F5 不再作为独立缺陷修复；由 Phase 3 的 F10（单元格裁剪 + 行为修复）覆盖。回归钉：`TestPdfRenderDefects.testF5_wrapTextLinesHaveDistinctY`（出生即绿）。

#### F6 [P1→降级] 分页边界行文字重影（基线修订：按绘制操作计数证伪）

- 现象：synthetic-long-table p1 底部（P34-P45）与 p2 顶部（P46-P48）行文字纵向重影。
- **Phase 1 复现结论（plan 2260）**：在与审计完全一致的条件下（4 列 CJK、200 行、A4），按 showText **绘制操作计数**（`countDrawOps`）证明每个行标签（P30-P48）只绘制一次。原判断依据是 110dpi PNG 的视觉印象，实际为 F10 文本溢出交叠（名称列文本溢入数量列与数值叠加）+ 栅格化的视觉伪影。注意：提取文本计数法在此不可用（多列文本重叠时提取交错，"测试项目名称第30号"会被数字打断无法连续匹配）——`countDrawOps` 是重影检测的正确工具。
- 处置：F6 不作为独立缺陷；回归钉 `testF6_eachRowTextDrawnExactlyOnce`/`testF6_auditScenarioEachRowDrawnOnce`（出生即绿）；Phase 6 视觉复核若再现伪影将附栅格化说明。

#### F7 [P1] 无样式/无字体单元格直接 NPE（基线修订：当日已修复）

- 现象：合成表初版（单元格无 styleId、style 无 font）抛 `NullPointerException: PdfTableRenderer.getStyleFont(...) is null`。
- **基线修订（审计评审后确认）**：提交 `e2b02af86e`（同日"fix(report-pdf): 修复渲染坐标换算与资源释放缺陷"）已包含 `getFontSize` 空值拆箱防护与 `getDefaultFont` 未初始化防护，该 NPE 在当前 HEAD 不可复现。修复计划 2260 Phase 1 以复现测试复验并钉住回归（Proof），不再要求红测试。
- 影响：任何未完整配置样式的工作簿（程序构建、导入模板）PDF 导出即崩（修复前）。
- 遗留：`TestPdfExportAudit` 中的绕过注释应随复验测试清理。

### P2 缺陷

| # | 现象 | 根因候选 |
|---|------|----------|
| F8 | null 字体名永远走 Helvetica（注入 `/fonts/default.ttf` 也不经过它）——`PdfRenderer.getFont(null)`→`getDefaultFont()` 硬编码 base-14 | `FontManager.init` 的 defaultFont 不尝试 defaultFontResource |
| F9 | ~~Integer 值列未渲染~~（**Phase 1 证伪**：合成 4 列表按绘制操作断言 Integer/Double/String 全部正常渲染，`testF9_integerValueRendered` 出生即绿；审计 PNG 中"数量列空缺"为溢出交叠 + 110dpi 下的视觉误读） | 无缺陷；保留回归钉 |
| F10 | 无裁剪：长文本溢出单元格绘制（02-p2 标题在窄条内换行溢出、07 数据行文本压线） | drawUnwrappedText 无 clip |
| F11 | 图片越界不裁剪（11-p1 第一行二维码越过页顶被切） | renderImages 无页面边界 clip |
| F12 | TTF 无 bold/italic 变体（伪粗体缺失）。~~字体加载无 per-document 缓存~~（基线修订：`PdfRenderer.fontCache` 自初始提交即存在，逐次加载不成立；注意 `PDType0Font` 绑定 PDDocument，禁止跨文档缓存字体实例） | FontManager 设计 |
| F13 | 无默认页眉页脚/页码（依赖模板显式配置） | 同 F4 |
| F14 | 区块标题文字对比度可疑（01 区块标题深蓝字配浅蓝底）——需与模板原样式比对确认是颜色读取错误还是模板本身如此 | 待比对 |

### P3 观感问题

- 日期时间在窄列断行生硬（01-p1 "9:30-17:00" 断成 "17\n:00"）。
- 展开空行残留可见边框（08-p1 表尾空行）。
- 数据行行高不一致（07-p1 1001/1002 两行高度不等）。
- 性能模板 534 页 = 行分页 × 列分页乘积（宽表列拆分页翻倍且不可读；F3 修复后列拆分页因重复关键列变为可读，页数本身不会减少——判定标准是"拆分页可读"而非页数）。

### 复审节：plan 2260 修复后 before/after（2026-09-05）

全部缺陷修复完成后重跑 `TestPdfExportAudit`（21 用例全绿），逐项视觉复检：

| 缺陷 | 修复前（审计） | 修复后（复审） |
|------|---------------|---------------|
| F1 字体 | 17/20 模板导出崩溃 | **无字体注入导出全绿**（系统字体发现+字形回退），`TestPdfExportAuditNoFontInjection` 验证 |
| F2 套打底图 | PDF 含背景花纹图 | PDF 只含打印数据（09-套打-p1 复核），与 XLSX/HTML 行为一致 |
| F3 列拆分 | 01/03/16 的第 2 页为孤立窄条不可读 | 关键列重复生效：01-p2 左侧出现标签列，行归属可辨 |
| F4 续页无表头 | 续页无表头无页码 | 表头行重复 + 页码"第 x / y 页"（synthetic p2 复核） |
| F5 多行叠压 | 说明块文字叠成一团 | **证伪为 F10 溢出**：裁剪后溢出消失，行距本就正常 |
| F6 边界重影 | 边界行文字重影 | **证伪**：按 showText 操作计数每行仅绘制一次，原印象为溢出交叠+栅格化伪影 |
| F7 无样式 NPE | NPE | e2b02af86e 已修复，回归钉验证 |
| F10 文本溢出 | 长文本溢入相邻单元格 | 单元格矩形裁剪生效（内容流含 re+W n 裁剪） |
| F11 图片越界 | 二维码越过页顶被裁切后仍绘制 | 页面矩形裁剪生效 |
| F13 无页码 | 全文档无页码 | 多页文档默认页码 |
| F14 标题颜色 | 疑似颜色读取错误 | **证伪**：模板本身即 FF0E6089 深蓝字/FFC3E7F8 浅蓝底，渲染忠实（HTML/PDF/模型三方一致） |

修复过程中新发现并修复：**控制字符编码崩溃**（含 `\r\n` 的多行单元格文本在快速失败路径暴露，U+000D 无字形——`fontForText` 诊断增强定位；绘制层归一化为空格 + 编码检查跳过控制字符）。10-导出Excel公式 模板由 FAIL 转绿。

P3 项裁定：日期断行（01）/空展开行边框（08）/行高不齐（07）保留为模板设计或低影响观感问题，移入 plan 2260 Non-Blocking Follow-ups；列宽不足导致的文字贴近（synthetic）属数据侧未设列宽，非渲染缺陷。

代价说明：重复标题块预留空间使部分模板页数小幅增加（07: 2→3、13: 3→4、测试同比环比 534→548），换取续页/侧页可读性；单页内放得下的报表不受影响（两遍拆分策略）。

### 审计工具资产

- `nop-report-demo/src/test/java/io/nop/report/demo/TestPdfExportAudit.java`：20 模板 + 合成长表批量导出、逐页转 PNG、失败汇总；CJK 字体注入演示（`VirtualFileSystem.updateInMemoryLayer`）。
- 产出目录 `_tmp/report-pdf-audit/`（603 文件）供人工复核。
- 该测试同时是修复计划的**验收基线**：每个缺陷修复后对应页面应发生可断言的变化（配合 PDFBox 文本级断言 + 像素采样断言）。

## Conclusion

- ~~nop-report 的 PDF 导出在"单页、简单表格"场景已可用……~~ **（已由 plan 2260 修复收敛，见上方复审节）**：修复后全部 21 个审计用例导出成功且视觉复检通过，中文报表 PDF 导出与自动分页达到生产可用状态；无字体注入场景依赖系统字体自动发现（CI 环境前提见 `ai-dev/design/nop-report/pdf-font-strategy.md` D3）。
- 后续工作：缺陷修复已由 `ai-dev/plans/2260-nop-report-pdf-quality.md` 落地；审计测试保留为回归基线；Non-Blocking 项见该计划。

## Open Questions

- [x] F9（Integer 列缺失）：Phase 1 复现证伪，Integer/Double/String 均正常渲染（回归钉 `testF9_integerValueRendered`）
- [x] F14（区块标题颜色）：证伪——模板本身 FF0E6089 深蓝字/FFC3E7F8 浅蓝底，渲染忠实
- [x] 页眉页脚 `&P/&N/&D/&A` 占位符：当前为字面文本不支持占位符求值；默认页码（第x/共y页）已实现，占位符求值列入 plan 2260 Non-Blocking Follow-ups
- [x] `fitToWidthAndHeight` 单页压缩：维持现状（模板显式配置的语义），最小字号阈值列入 Non-Blocking Follow-ups

## References

- 审计产出：`_tmp/report-pdf-audit/`（PNG/PDF）
- 审计测试：`nop-report/nop-report-demo/src/test/java/io/nop/report/demo/TestPdfExportAudit.java`
- 渲染器源码：`nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/renderer/`（PdfSheetRenderer/PdfTableRenderer/PdfStyleHelper）、`font/FontManager.java`
- 分页：`nop-kernel/nop-core/.../model/table/utils/TableSplitHelper.java`
- Runbook：`docs-for-ai/03-runbooks/generate-report.md`
