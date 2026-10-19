# nop-report PDF 分页体系设计

> Status: active
> Last Reviewed: 2026-10-19
> Source: plan 2260 Phase 4（来源分析 `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md` F3/F4/F11/F13）；plan 2464（WI1）；plan 2465（WI2）；plan 2466（WI3）；plan 2467（WI4）；plan 2468（WI6）；plan 2469（WI5）；plan 2470（WI7）；plan 2471（WI8 边框）

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

### D6 分页边界语义（WI3）

- **手动分页符**：复用既有 `ExcelSheet.pageBreaks`（`ExcelPageBreaks.rows/cols` int-list，此前为无解析无消费的死字段）作为唯一表示（SSOT），语义定为 **0-based 索引、break 发生在该行/列之后**（该行/列为页内最后一行/列，对齐 OOXML brk@id）。`SheetNodeHandler` 解析 `<rowBreaks>/<colBreaks>/<brk>`（brk 无 id 忽略）写入该字段；`TableSplitHelper` 新增 break 感知重载（旧签名委托空集，行为零变化）；渲染器过滤越界与末位 no-op 后传入两遍拆分。
- **fit×breaks**：fit scale 按无 break 的表尺寸计算（与 breaks 无关），手动 break 优先强制提前分页——实际页数可超过 fit 目标 N。这是与真实 Excel（fit 模式忽略手动 break）的有意分歧：break 是用户显式意图，优先于 fit 估算。
- **超页行**（单行高于页内容区）：独立成页，页外内容靠 PDF MediaBox 裁切（页边距区域可见渗漏——表格绘制无显式 clip，clip rect 仅用于图片）；前后行不丢（`row > startRow` 守卫 + startRow 正确推进）。与 Excel 打印行为一致，不做行内拆分。
- **合并单元格跨拆分边界**：D2 的裁剪视觉裁定扩展到行拆分方向——`getSubTable(region).clip()` 裁剪跨边界 span，锚点内容落在锚点所在页，无异常不丢行。
- **空白尾行尾列裁剪**：渲染入口截断尾部连续"无值且无样式"的行/列（style-only 行保留——模板尾部边框行属内容）；仅尾部截断不改变保留行/列下标，区域坐标与图片全表锚点坐标基不变；全空表回退原表。
- **重复块边界**：拆分点落在重复前缀内部时按既有 `k=min(repeatRows, firstRowIndex)` clamp，不产生内容丢失。
- **防御**：`TableSplitHelper` 对 0 行/0 列表直接返回空（0 列表在列分页分支曾死循环——startCol 永不推进，回归钉 `testZeroColumnTableTerminates`）。

### D7 页眉页脚占位符与作用域（WI4）

- **模型**：`ExcelPageSetup` 六段（header/footer + firstHeader/firstFooter/evenHeader/evenFooter，均 ExcelHeaderFooter）+ `differentFirst`/`differentOddEven` 开关（OOXML headerFooter 元素属性的模型级映射，SAX 解析不经 xdef）。模型 left/center/right String **保留原始 &-代码不剥离**（保真无损；WI5 再评估结构化）。
- **解析归属**：&-语法解析器 `ExcelHeaderFooterCodes` 在 nop-ooxml-xlsx（OOXML 语法知识）；负责 &L/&C/&R 位置分段（缺省段 center）、`&&` 转义、字体 `&"name"`/字号 `&nn`（1-2 位数字）识别。`SimpleSheetContentsHandler.headerFooter` 覆写把六元素装入模型——**修复 xlsx 模板 headerFooter 在主解析路径被整段丢弃的缺陷**（回归钉：test-pdf.xpt.xlsx oddFooter）。
- **渲染分流**：段文本含任何 &-代码（含仅字体代码）→ 延迟求值绘制（否则 & 代码字面打印）；纯字面文本 → 即时绘制（原路径）。
- **延迟绘制机制**：`PdfRenderer` 维护延迟任务列表（页号/L-C-R/原始段/矩形/样式/sheet 名），`PdfReportRenderer` 在内容渲染完成后、默认页码前调用 `drawDeferredHeaderFooters`（此时 &N 可得）；显式页脚（含延迟）的页照常抑制默认页码。字体段渲染：段内代码切分为 run（`HeaderFooterRuns`），逐 run 求值占位符后按 L/C/R 定位顺序绘制；同段多字体代码取首个（WI5 精修多段混排）。
- **页码作用域**：`nop.report.pdf.page-number-scope`（global 缺省/sheet；非法值 fail-loud `nop.err.report.pdf.invalid-page-number-scope`）。global：&P/&N=全文档序号/总页数；sheet：各 sheet 内序号/页数（`PdfRenderer` 记录 sheet 页区间）。&D/&T/&A/&F/&Z 不受作用域影响。
- **变体回退**：differentFirst 首页用 first*（缺失回退 odd）；differentOddEven 偶数页用 even*（缺失回退 odd）；odd 缺失 → 该页无页眉/脚（默认页码抑制语义与 Excel 空白页脚一致）。
- **&D/&T 格式**：yyyy/M/d 与 HH:mm:ss（固定格式，D7 定版）。&F/&Z 取模板资源路径（workbook location；无则空串）。&G 图片段解析保留、渲染属 WI5。
- **&G 图片（WI5）**：`ExcelHeaderFooter.imageName` 引用 `sheet/images` 具名图片（缺省第一张）；HF 素材图应 `print=false`（避免同时作为 sheet 定位图片渲染）；fit 双轴 `scale=min(高比, 宽比)` 居中；名字找不到或无图 WARN（`nop.report.pdf.header-image-missing`，log marker 非 ErrorCode）跳过。
- **分区裁剪（WI5）**：延迟路径 run 序列绘制裁剪到分区矩形（save/clip/restore，与 run 矩形、图片内部裁剪交集语义安全）——长文本溢出不越界相邻分区；&G 图片绘制在 restore 之后（不受分区 clip 约束），几何包含由 fit 双轴缩放保证（rotateDegree≠0 的 logo 无 clip 约束，当前无消费者）。
- **多字体取字**：同段多字体代码取首个字体；`&"name,style"` 的 style 装饰被丢弃（firstToken），粗斜体变体属 WI10。

### D8 图片嵌入语义（WI6）

- **尺寸换算**：OOXML 锚点偏移 EMU→pt 在 DrawingParser 解析期完成（`UnitsHelper.emuToPoints`）；渲染几何 = 锚点两角 + dx/dy（pt），测试以锚点几何钉住（`testImageAtAnchoredGeometry`）。
- **rotateDegree**：绕图片中心顺时针旋转（PDF 行向量约定下显式构造合并矩阵：a=w·cosθ、b=w·sinθ、c=-h·sinθ、d=h·cosθ、e/f 含中心平移；θ=Math.toRadians(-rotateDegree)）。rotateDegree=0 走原 drawImage 路径零开销。
- **noChangeAspect**：为 Excel UI 语义（界面缩放锁定比例），渲染以锚点几何为准——本裁定显式覆盖 roadmap"noChangeAspect 等比"措辞。
- **alpha 透明**：PDFBox `createFromByteArray` 对 PNG 保留 alpha 通道，原生渲染（测试钉住：透明半区透出底层填充色）。
- **占位策略**：缺图（data null/empty）与损坏图统一绘制浅灰占位块 + WARN（`nop.report.pdf.image-missing-placeholder`/render error）——不再静默跳过；分页裁剪跳过（region 不含锚点）与套打 `print=false` 保持静默（合法语义）。
- **缓存**：`PdfRenderer.getImage` 按图片字节 per-document 复用 PDImageXObject（同字节同实例）。
- **限制**：oneCellAnchor/absoluteAnchor（无 `to` 节点）解析后 col2=row2=0 → calcSize 宽高为负、渲染错位——仓库无此类模板（watch-only，出现需求时解析 `<ext>` 扩展几何）。

### D9 图表接线（WI7）

- **SPI**：`IChartImageProvider.renderChart(chart, sheet, widthPt, heightPt) → PNG 字节`（SVG 由 provider 自行栅格化，PDFBox 3 原生不支持 SVG）；`ChartImageProviders` 静态注册器，缺省内置桩（灰底蓝框渐变图案）。真实 echarts provider（echarts-server-render-roadmap 交付）setProvider 即插即用。
- **renderCharts**：镜像 renderImages——anchor 判空、region 过滤（锚点起点在当前 region 才绘制）、几何按锚点两角计算、provider 返回 null → WARN（`nop.report.pdf.chart-provider-null`）跳过（不做无痕静默）；绘制经临时 ExcelImage 复用 PdfImageHelper（裁剪/占位/缓存）。
- **完成判定**：本 WI 勾选代表 PDF 侧管线打通（桩验证）；图表与真实渲染的逐图视觉对版延后至 echarts provider 交付。
- **已知疑点**：多页（region≠null）图片/图表的 y 定位与"锚点行×行高"推算存在约 40pt 级偏移疑点（重复行+预留几何推导未完，图片路径同样存在）——登记 follow-up，锚点页存在性已测试钉住。

### D10 边框线宽/线型（WI8）

- **线宽来源**：`OfficeBorderStyle.weight` 优先（xlsx 解析期自 OfficeLineStyle 规范 WEIGHT 表写入：thin=1/medium=2/thick=3pt，medium 变体虚线=2）；程序化样式 weight=0 → 回退类型规范 weight；type 为 null → 1（顺带修复旧 switch 的 null NPE）。**替换旧硬编码映射**（HAIR 0.5/SINGLE 1/MEDIUM 1.5/THICK 2/DOUBLE 1.5——MEDIUM/THICK 与 Excel 语义不符）。
- **HAIR**：模型权重 1（int 表），渲染 1pt；Excel 0.25pt 视觉校准留后续（Follow-up）。
- **虚线节距**：随线宽比例化（DASHED {3w}、DOTTED/HAIR {w}、DASH_DOT {3w,w,w,w}、DASH_DOT_DOT 类推，w=max(0.5, lineWidth)）；DOUBLE 双线偏移 ±lineWidth（随宽度比例化）。
- **缩放**：PDF lineWidth 为 user space 值，随 CTM 等比缩放（ISO 32000-1 §8.4.3.2）——WI2 缩放管线无需补偿，测试钉住（user space w 不变 + 设备像素宽随 scale 减小）。

### D3 默认页码

- `nop.report.pdf.default-page-footer`（默认 true）：文档页数 >1 且页面未显式配置页脚时，页底居中绘制"第 x / y 页"（全局页码）。
- 在全部页面渲染完成后统一绘制（需要总页数），使用 `PDPageContentStream.APPEND` 追加内容流。

### D4 图片页面裁剪

图片绘制裁剪到页面矩形内（`saveGraphicsState + addRect + clip + drawImage + restoreGraphicsState`），修复锚点越界图片（如二维码越过页顶）被裁切后仍绘制的问题。

## 已知限制（后续增强方向）

- &B/&I 粗斜体变体渲染（代码已解析剥离，样式属 WI10 字体保真）。
- OOXML VML 头部图片（legacyDrawing）解析——当前 &G 经模型 imageName 引用（见 D7）。
- 非前缀 Print_Titles 的渲染（见 D1 渲染限制）。
- pageBreaks/Print_Titles/pageSetup fit/headerFooter 写回 xlsx 输出（见 D1/D6）。

## 涉及实现

- `nop-ooxml-xlsx/.../parse/ExcelWorkbookParser.java`（Print_Titles definedNames 解析归一化）
- `nop-ooxml-xlsx/.../parse/SheetNodeHandler.java`（pageSetup scale/fit 属性 + pageSetUpPr fitToPage 门控 + rowBreaks/colBreaks/brk 解析）
- `nop-excel/.../print/ExcelPrintHelper.java`（title range 解析/前缀判定）
- `nop-excel` 模型：`ExcelPageSetup.repeatTitleRows/repeatTitleCols`、`fitToWidth/fitToHeight`（page-setup.xdef）；`ExcelPageBreaks`（workbook.xdef，既有字段启用）
- `nop-report-pdf/.../renderer/PdfSheetRenderer.java`（两遍拆分、模型优先解析、重复块渲染、手动分页符接入、空白尾裁剪、页脚标记）
- `nop-core/.../utils/TableSplitHelper.java`（break 感知拆分重载 + 0 行/0 列防御）
- `nop-ooxml-xlsx/.../parse/ExcelHeaderFooterCodes.java`（&-语法解析）、`SimpleSheetContentsHandler`（headerFooter 装模，修复丢弃缺陷）
- `nop-report-pdf/.../utils/HeaderFooterRuns.java`/`HeaderFooterEvaluator.java`（run 切分 + 占位符求值）、`PdfRenderer`（延迟绘制 + sheet 页区间）、配置 `nop.report.pdf.page-number-scope`
- `nop-report-pdf/.../renderer/PdfRenderer.java`（页面跟踪、`drawDefaultPageNumbers`）
- `nop-report-pdf/.../renderer/PdfReportRenderer.java`（保存前绘制页码）
- `nop-report-pdf/.../utils/PdfImageHelper.java`（图片页面裁剪、rotateDegree 旋转、占位统一）
- `nop-report-pdf/.../utils/PdfStyleHelper.java`（weight 驱动边框线宽、虚线节距比例化 WI8）
- `nop-report-pdf/.../utils/IChartImageProvider.java`/`ChartImageProviders.java`、`PdfSheetRenderer.renderCharts`（图表接线 WI7）
- 配置：`nop.report.pdf.repeat-header-rows`、`repeat-key-columns`、`default-page-footer`、`min-font-size-pt`

## 验证

- `TestExcelWorkbookParserPrintTitles`：Print_Titles 四形态归一化、非法形态报错、localSheetId 语义、calc.xlsx 端到端
- `TestExcelPrintHelperTitleRange`：range 解析/前缀判定
- `TestPdfPrintTitlesRender`：续页标题重复与数据完整性、模型优先于配置缺省、非前缀 fail-loud、golden+区域采样视觉验收
- `TestPdfFitScaleRender`：fit 2×1 目标页数（恰 2 页+关键列重复）、scale 页容量修正（scale=50 页数减半）、最小字号收敛（页数超目标）、fit×重复块、golden
- `TestSheetNodeHandlerPageSetup`：fitToPage 门控四形态
- `TestSheetNodeHandlerPageBreaks`：rowBreaks/colBreaks 解析
- `TestTableSplitHelperBreaks`：break 拆分（行/列/越界/末位/容量叠加/行-only/0 列防御）
- `TestPdfPageBoundaries`：手动分页符精确分页、fit×breaks、超页行、合并单元格跨页、空白裁剪、style-only 不裁、重复块边界、golden
- `TestExcelHeaderFooterCodes`：&-语法分段/转义/字体段/占位符判定；`TestTemplateHeaderFooterParse`：模板 headerFooter 装模回归
- `TestPdfHeaderFooterRender`：&P/&N 全局与 sheet 作用域（双 sheet）、首页/奇偶变体、&A/&D/&T、字体代码不字面、无效 scope fail-loud、golden
- `TestPdfImageRender`：锚点几何、rotateDegree 四象限、alpha、缺图/损坏占位、print=false、越界裁剪、缓存复用、多图回归、golden
- `TestPdfChartRender`：provider 接线探测、锚点几何、region 过滤、null 跳过、默认桩、golden
- `TestPdfBorderFidelity`：w 操作符精确线宽（1/2/3pt）、类型权重回退、像素严格序、DOUBLE 外扩、虚线节距比例、缩放 CTM、golden
- `TestPdfRenderDefects`：F3（列拆分关键列重复）、F4（续页表头重复）红转绿；F6 回归钉（重复表头按需多次绘制，其余行只绘制一次）
- `TestPdfSheetRenderFixes`：单页小表不受影响（无重复、打印区平移不变）
- `TestPdfExportAudit`：20 模板批量导出复检
