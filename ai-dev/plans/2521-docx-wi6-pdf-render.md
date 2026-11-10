# 2521 docx roadmap WI6：OfficeDocModel → PDF 渲染

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI6）+ ai-dev/backlog/nop-pdf-report-export-roadmap.md（已全部完成，提供字体/图片/边框/验收工具）+ docs-for-ai/02-core-guides/word-document-model.md
> Related: ai-dev/plans/2520-docx-wi5-writer-roundtrip.md

## Purpose

同一 OfficeDocModel 直接渲染 PDF（PDFBox，不经 Aspose/OnlyOffice/LibreOffice），使 Word 文档具备与 nop-report 一致的 PDF 导出。交付流式排版渲染器 + PDF 渲染断言（文本抽取 + 像素探针：线宽/颜色/布局）；golden 仅作本机辅助回归。

## Current Baseline

- 模型与 docx 读写已闭环（WI1–WI5）：八类块、四类行内、run/段落样式、表格（合并/列宽/行高/边框底纹内联样式）、每节页面设置、页眉页脚、图片 assets、命名样式 + OfficeStyleResolver、背景。
- PDF 基础设施（live code）：
  - `nop-format/nop-pdf`：PdfDoc（装配/水印）、verify 包（PdfImageRenderer/RegionProbe/PdfGoldenManager/ImageDiff，位于 nop-pdf 的 main 源码；nop-report-pdf 对 nop-pdf 为 **test scope** 依赖，仅测试期可用）；**无字体管理、无换行/排版 API**。
  - `nop-report/nop-report-pdf`（`io.nop.report.pdf`）：FontManager（五级解析 + CJK 回退 + TTC + synthetic bold/italic）、PdfRenderer（fontForText 字形回退、缺字报错）、PdfPageRenderer、PdfStyleHelper（drawWrappedText/drawRichText/drawBorder/setCellBackground）、TextWrapHelper（按词/按字符/强制折行 + 块高）、PdfImageHelper（byte[] 重载）；错误码类 `ReportPdfErrors`；PdfSheetRenderer/PdfTableRenderer 与 Excel 模型强耦合不可复用。TextWrapHelper 为纯测量静态方法（可供 layout 复用，但只支持单字体整串）；PdfStyleHelper 的绘制方法绑定 contentStream（仅 paint 层可用）。FontManager.resolveFont(name,bold,italic,PDDocument) 返回的 PDFont **绑定具体 PDDocument**。**该模块依赖 nop-report-core → Excel 全链**。
  - 现有能力是「矩形内定位绘制」；**无跨页流式排版、无下划线/删除线/高亮/上下标绘制**。
  - 测试字体：nop-report-pdf test resources 的 `/fonts/default.ttf`（思源宋体，CJK 跨平台确定性）；PDFBox 3.0.8。
- 依赖现状：nop-office-doc-model 仅依赖 nop-office-model + nop-xlang；nop-ooxml-docx 对 nop-pdf 仅 test scope。
- 已知模型缺口影响渲染：numbering 定义未建模（列表字形只有 numId/level + 可选 WordListStyle）；水印 VML 不承载；与文字混排的图片未建模。

## Goals

- 渲染器：OfficeDocModel → PDF（byte[]/流）；流式排版（段落按行、表格按行跨页拆分）；每节页面尺寸/方向/边距；页眉页脚每页重复；背景色。
- 块覆盖：段落、标题、列表（缩进 + 编号/项目符号字形）、表格（列宽/行高自适应/合并/边框/底纹/垂直对齐）、图片、超链接（文本样式 + link annotation）、分隔线、分页符。
- 行内覆盖：多样式 run 混排（字体/字号/颜色/粗/斜/下划线/删除线/高亮/上下标）、tab、br、link；CJK 换行与字体回退。
- 命名样式经 OfficeStyleResolver 解析 + 内联样式覆盖。
- 验收：PDF 文本抽取断言 + RegionProbe 像素断言（线宽/颜色/位置）；golden 作本机辅助回归；与 LibreOffice 渲染的同语料对照作为布局合理性旁证（非逐像素）。

## Non-Goals

- 与 Word/LibreOffice 逐像素一致的排版保真（目标是结构语义一致、样式贴近）。
- 复杂排版特性：分栏、文本框/浮动图片环绕、脚注尾注、目录域、域代码、修订、连字符断词、孤行寡行控制、表格跨页重复表头（首版不做，登记）。
- 模板动态展开（WI7）、图表渲染（WI8）。
- docx 直转 PDF 的便捷入口以外的产品化（统一出口属 WI9）。

## Scope

### In Scope

- 渲染器代码 + 模块依赖调整（落位见 Phase 1 R1）。
- 新写：流式排版引擎（行盒/块盒/分页）、run 装饰绘制、Word 表格排版、页眉页脚布局。
- 复用：FontManager / fontForText 回退 / TextWrapHelper / PdfStyleHelper 边框与背景 / PdfImageHelper / PdfPageRenderer / nop-pdf verify。
- 测试 + owner doc + 设计文档决策 + roadmap/日志。

### Out Of Scope

- 见 Non-Goals；另：nop-report-pdf 既有 Excel 渲染路径行为零改动（既有渲染测试零回归）。

## Execution Plan

### Phase 1 - 渲染层决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（设计文档新增「PDF 渲染层决策」一节，插在 springreport 对比结论之前）：
  - R1 **渲染器落位**：落 `nop-report/nop-report-pdf` 模块新包 `io.nop.report.pdf.doc`，该模块新增对 `nop-office-doc-model` 的 compile 依赖（doc-model 仅依赖 office-model + xlang，无循环；依赖方向 report-pdf → doc-model 符合 roadmap Cross-Cutting 6）。理由：FontManager/PdfRenderer/PdfStyleHelper/TextWrapHelper/PdfImageHelper/PdfPageRenderer 与测试字体均在该模块，同模块直接复用，零下沉重构、零新模块。拒绝的替代：(a) 新模块 nop-format/nop-office-doc-pdf + 把字体/换行通用件下沉到 nop-pdf——需移动已完成 PDF roadmap 的公共类（包名变更或兼容委托层），跨 roadmap 重构风险与工作量大，可作为后续独立治理项；(b) 新模块依赖 nop-report-pdf——仅多一层模块壳，无实质隔离；(c) 新模块重写精简字体/换行——重复实现，违背 roadmap「复用」硬边界。已知代价：纯 Word→PDF 使用方会传递引入 Excel 报表链。
  - R2 排版模型：两阶段——layout（模型 → 行盒/块盒 → 分页，纯计算可单测）+ paint（按页绘制）。**layout 与 paint 共享同一个 PDDocument**：字体在渲染会话开始时经 FontManager 按 (fontName,bold,italic) 解析一次并缓存于会话，layout 用其测量、paint 用其绘制（PDFont 绑定 PDDocument，不得每页/每阶段重新加载）。多 run 混排的换行按 run 分段逐字符/逐词测量（TextWrapHelper 仅支持单字体整串，混排换行新写）。段落按行拆分跨页；表格按行拆分跨页（单行不拆，行高超一页时强制截断并具名告警）；图片/分隔线整体不拆。
  - R3 单位：模型 pt 为准（解析端已转 pt）；页面原点左上、绘制时转 PDF 坐标。
  - R4 字体：run 字体名经 FontManager 解析，缺字经 fontForText 回退；粗斜优先真变体否则 synthetic；缺字无回退时沿用既有错误码语义（不静默替换）。
  - R5 列表字形：有 WordListStyle（numFmt/lvlText/start）则按其生成；否则缺省 level 0 用 "•"、数字编号按出现顺序——numbering 定义未建模的降级口径，具名登记。
  - R6 不支持形态：混排图片/水印 VML（模型不承载）无从渲染；模型内未识别块类型显式报错（与 docx 生成端 G5 一致）。
  - R7 验收口径：每个能力至少一条「文本抽取 + 像素探针」断言——这是入库可复现的验收主体；golden 沿用 PDF 验收工具既有约定（`_tmp/pdf-golden/` 机器本地快照、不入库），只作本机回归辅助，不作为验收依据；LibreOffice 对照仅作布局合理性旁证（页数一致、关键文本所在页一致），不做像素比对。测试只调用 FontManager 解析，不修改其全局别名/发现状态（单例，防污染既有测试）。
- [x] check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 设计文档含 R1–R7 决策及拒绝理由。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2a - 排版骨架与文本流

Status: completed
Targets: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/doc/`, `nop-report/nop-report-pdf/pom.xml`

- Item Types: `Feature`

- [x] pom：nop-report-pdf 增 nop-office-doc-model compile 依赖 + nop-ooxml-docx **test** 依赖（核对无循环：`./mvnw -pl nop-report/nop-report-pdf -am validate`）。
- [x] 渲染会话与字体缓存（R2：单 PDDocument、(name,bold,italic) 解析一次）；缺字经既有回退与错误码语义。
- [x] layout 骨架：行内排版（多样式 run 混排测量换行、tab、br、CJK 逐字符）、段落块（对齐/左右缩进/首行缩进/段前后距/行距）、标题块、分页器（版心高度、段落按行拆）、单节页面几何（宽高/方向/边距）。
- [x] paint：文本与 run 装饰（粗/斜含 synthetic、颜色、下划线/删除线/高亮/上下标——新写）、背景色、页眉页脚每页绘制（页眉页脚内容限段落/标题块）。
- [x] 入口 `OfficeDocPdfRenderer`（模型 → OutputStream/byte[]）；命名样式经 OfficeStyleResolver + 内联覆盖。
- [x] 未识别块/本阶段未实现的块类型显式报错（`ReportPdfErrors`），不静默跳过。

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿（既有 Excel 渲染测试零回归）。
- [x] layout 单测（不依赖绘制）：给定宽度的多 run 段落换行行数与每行文本、长段落分页后每页行数、CJK 换行。
- [x] 渲染断言：文本抽取顺序正确（含 CJK）；多页（页数 > 1）；mediaBox 等于页面设置；粗体/颜色/下划线/删除线/高亮的像素探针；背景色探针；每页页眉页脚文本可抽取。
- [x] 无静默跳过：表格/图片等未实现块在本阶段显式报错并有测试。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2b - 表格、图片、列表、链接与多节

Status: completed
Targets: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/doc/`

- Item Types: `Feature`

- [x] 表格：列宽分配（模型列宽，缺省均分版心）、单元格内容排版求行高（取模型行高与内容高较大者）、横纵合并、垂直对齐、边框（复用 PdfStyleHelper 边框绘制）/底纹、按行跨页拆分（单行超页截断 + 具名告警）；单元格样式经 cell.styleId 解析。
- [x] 图片（assets 数据 → PdfImageHelper；超版心等比缩放；整体不拆页）、分隔线、分页符。
- [x] 列表（缩进 + 字形 R5）；超链接块与行内 link（文本样式 + link annotation URI）。
- [x] 多节：每节独立页面几何与页眉页脚；页眉页脚内容支持表格/图片块。
- [x] 移除 2a 的「未实现块」报错分支，仅保留真正未识别类型的报错（R6）。

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿。
- [x] 渲染断言：表格文本顺序与合并单元格位置、边框线宽与颜色探针、单元格底纹探针、行数足够多时跨页（第二页含后续行文本）；图片区域非白像素与尺寸；分页符强制换页；列表字形文本；link annotation 存在且 URI 正确；多节每节 mediaBox 与页眉文本不同。
- [x] 无静默跳过：未识别块报错有测试；行高截断/列表字形缺省有具名记录。
- [x] 实现期裁定落设计文档「PDF 渲染层决策」一节。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 端到端与对照验收

Status: completed
Targets: `nop-report/nop-report-pdf/src/test/java/io/nop/report/pdf/doc/`

- Item Types: `Proof`

- [x] 端到端（接线验证）：模型 → OfficeDocModelWriter → docx 字节 → OfficeDocModelParser → 渲染 PDF，断言与直接渲染原模型的文本抽取结果一致（自造语料闭环；DocxCorpusBuilder 位于 nop-ooxml-docx 的 test 源码、无 test-jar，跨模块不可用）。
- [x] LibreOffice 对照旁证（assumeTrue soffice，2–3 个样本）：同一 docx 的 LibreOffice PDF 与本渲染器 PDF 页数一致 + 关键文本所在页一致。
- [x] 本机 golden 辅助回归（`_tmp/pdf-golden/wi6-doc-*`，3–5 个代表样本，PASS 或 BASELINE_CREATED 即过）。
- [x] owner doc：能力矩阵 PDF 行 + 「PDF 渲染契约」小节（入口、覆盖面、降级与不支持项、依赖传递说明）；source-anchors 增锚点。

Exit Criteria:

- [x] **端到端验证**：docx 字节 → 模型 → PDF 全链路测试通过。
- [x] Cross-Cutting 3（实际 PDF + 逐页 PNG + 线宽/颜色/布局断言）由 2a/2b 的像素探针断言 + 本阶段对照满足。
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿。
- [x] owner doc / source-anchors 已更新；`ai-dev/logs/` 已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI6 勾选 + M3 milestone（Word 文档 PDF 输出可用）+ Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 绿；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿（上游无回归）。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-report/nop-report-pdf --severity high 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

### 复杂排版特性（分栏/浮动环绕/脚注/目录域/表格跨页重复表头/孤行控制）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Non-Goals 明示；首版目标为结构语义一致的流式文档 PDF 输出。
- Successor Required: `no`
- Successor Path: —（按需求另立项）

### 字体/换行通用件下沉 nop-pdf

- Classification: `optimization candidate`
- Why Not Blocking Closure: R1 裁定同模块复用；下沉属跨 roadmap 治理重构，不影响功能交付。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- numbering 定义建模后列表字形精确化。

## Closure

Status Note: WI6 四 Phase 完成：渲染器落 nop-report-pdf 新包 io.nop.report.pdf.doc（R1）；渲染会话单 PDDocument + 字体会话级缓存（测试锁定 resolveCount==1）；layout（混合样式断行 CJK 逐字符/拉丁按词/36pt 制表位）+ paint（run 装饰新写：下划线/删除线/高亮/上下标/合成粗斜）；分页器（段落按行、表格按行、行截断具名告警）；表格合并/边框/底纹、图片、分隔线、分页符、列表字形（WordListStyle 缺省口径）、超链接 annotation、多节几何。33 个新测试（2a 16 / 2b 14 / 3 端到端 3）+ 既有 110 零回归：nop-report-pdf 143、docx 98 全绿。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_22a1fa39-8eca-438f-9830-4eb0484b9cb7）
- Audit Session: agent_22a1fa39-8eca-438f-9830-4eb0484b9cb7
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 PDF 渲染层决策七条与实现一致）；Phase 2a PASS（pom 依赖无循环 validate exit 0；DocRenderSession 单 PDDocument + 字体 (name,bold,italic) 缓存，测试锁定 fontResolveCount==1；DocLineBreaker 混排断行 CJK 逐字符/拉丁按词/36pt 制表位；paint run 装饰真实绘制——高亮矩形/合成粗 FILL_STROKE/斜 Matrix shear/下划线删除线 stroke；layout 单测换行行数与分页行数断言真实；未实现块 2a 阶段显式报错后按 2b 移除）；Phase 2b PASS（表格跨页第二页含后续行文本断言、合并/边框/底纹探针、图片非白像素、分页符、列表字形含 WordListStyle upperRoman start=3、link annotation URI、多节 mediaBox/页眉差异；行截断 doc-table-row-truncated 具名告警）；Phase 3 PASS（writer→docx→parser→render 端到端文本一致；soffice 对照 assumeTrue 页数+关键文本页；golden PASS/BASELINE_CREATED）。
  - Closure Gates：`./mvnw test -pl nop-report/nop-report-pdf -am` 独立实跑 exit 0（143 用例，0 skipped）；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` exit 0（101 用例，含本会话新增 WI5 缺陷回归用例）；check-doc-links --strict exit 0；scan-hollow 0 发现；scope（既有 Excel 渲染路径/TestPdf*/nop-format/nop-pdf 零改动）。
  - Anti-Hollow：渲染由文本抽取 + RegionProbe 像素探针 + PDAnnotationLink 断言真实驱动；无空方法体/no-op。
  - 附带修复（renderer 实现期发现的上游 WI5 缺陷）：OfficeDocXmlBuilder.buildStyleNode 迭代中 detach 致命名样式含字体时 ConcurrentModificationException——new ArrayList 拷贝修复 + testNamedRunStyleWithFontWritesWithoutFailure 回归（bold/Calibri 往返断言）。
  - Deferred 项分类检查：复杂排版特性与字体下沉均为 out-of-scope/optimization，非 in-scope 缺陷降级。
