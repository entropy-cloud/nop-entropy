# 2485 WI16 版面感知文本抽取 + 页锚 + 扫描/乱码检测

> Plan Status: active
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI16，deps WI0）
> Related: card-kb 语义基准 `~/workspace/card-knowledge-base/tools/analyzed-pipeline/src/core/pdfio.mjs`（pageText/stripControlChars）；WI15 移交 follow-up（定位间隙坍缩已知限制的对版 owner）
> Review: R1 对抗性审查完成（5 Major + 4 Minor 吸收；R1-M5 关于 i18n 未登记为误报——nop.err.pdf 四码已 zh-CN 登记于 nop-cli-errors.i18n.yaml:1117-1120 且 WI13 audit 逐字核实；正确性要求已并入）；见修订记录

## Purpose

页级文本抽取保住版面与页码锚点；扫描件（无文本层）显式硬错误；乱码可量化。

## Current Baseline

2026-10-19 核对：

- `PdfDoc.getPageText/getAllText`：基础 PDFTextStripper 文本（无页锚/无版面模式/无扫描检测/无乱码指标）。
- `extract/parser/ExtractStripper`（extends PDFTextStripper，带 IExtractStripperCallback + ResourceParseConfig + 页图片导出）：面向 resource 抽取管线，非通用页文本 API。
- card-kb pageText 语义（pdfio.mjs :67-92）：逐 TextChunk `str + (hasEOL ? '\n' : ' ')`；stripControlChars（\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F）；`[ \t]+`→单空格；trim；maxChars 截断；坏页→空串（nop-pdf 侧扫描检测需把"空文本层"升级为硬错误——roadmap 红线）。注意 pdfio 归一只作用于 strip 形态（layout 形态多空格保留，R1-M1 裁定）。
- `pdftotext -layout` 等价：PDFBox 无内建 -layout；可行机制 = PDFTextStripper 子类 override writeString(String, List<TextPosition>)，按 y 聚类行、行内按 TextPosition x 间隙→比例空格填充（R1-MN3：参考 ExtractStripper 的 TextPosition 处理模式，不复用其渲染基建）。**R1-M2 实证（字节码）**：PDFBox 3.0.8 从不合成 U+FFFD——LegacyPDFStreamEngine showGlyph 在 toUnicode==null 时回退原始码点（简单字体）或静默丢弃（Type0）；主力乱码场景表现为字符静默消失。乱码信号定为双路：**unmapped-glyph 计数**（override showGlyph 统计 toUnicode==null 事件）+ U+FFFD 字面计数兜底（该信号定义为 WI17 机械统计的上游契约）。
- **WI15 移交（R1-M4 归属）**：`io/nop/pdf/toc/SortedPageTexts`（sorted stripper，定位间隙坍缩单空格）——定位排版目录行在 toc-regex 路径系统性漏检的已知限制由本计划的 layout 模式承接；本计划 layout 落地后补"layout 文本喂 TOC_ENTRY_RE 可检出定位排版目录行"对版测试，toc 路径切换消费 layout 文本的时机裁定见 Scope。
- i18n 登记面（R1-M5 修正）：`nop.err.pdf.*` 四码已 zh-CN 登记于 `nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/zh-CN/nop-cli-errors.i18n.yaml:1117-1120`（WI13 audit 逐字核实）；新码加入同一文件；`check-error-param-consistency.mjs --module nop-format/nop-pdf` 为有效 gate（实跑 exit 0 先例）。

## Goals

- `PdfDoc.extractPageTexts(options)` 双模式：strip 模式（pdfio 直译：控制字符剥离、`[ \t]+`→单空格、trim、maxChars 上限）；layout 模式（位置驱动空格重建，**保留重建的多空格/多列间距**——空白归一仅 strip 模式，R1-M1）。两模式共有：`\n` 行分隔（显式 setLineSeparator("\n")）、页锚 `<a id="pdf-page-N"></a>` 前置独立成行。
- 扫描判定谓词定版（R1-M3）：页 trim 后字符数==0 且 `hasContents()` → `ERR_PDF_SCANNED_PAGE` 硬错误；"图片占面"不入谓词（面积覆盖启发式移交 WI17 普查，stats 记 scannedPages）；vector-only 页按"无文本层"同样硬错（裁定与后果记 design）。
- 乱码检测双路（R1-M2）：unmapped-glyph 计数（override showGlyph 统计 toUnicode==null 事件——PDFBox 原生可观测点）+ U+FFFD 字面计数兜底；每页/全文档质量指标，超阈值按 options 告警或抛错。**信号定义即 WI17 机械统计的上游契约**。

## Non-Goals

- OCR（扫描件由使用方处理，nop-pdf 只报错）；图片内文字；RTL 特殊排版。

## Scope

### In Scope

- `io.nop.pdf.text`（新包）或 PdfDoc 扩展：PageTextOptions（layoutMode/maxChars/perPage/scanPolicy）、PageTextResult（pageNo/text/charCount/fffdCount）、TextQualityStats。
- 版面模式：基于 TextPosition 的 -layout 等价实现（词间距=坐标间隙阈值、行按 y 聚类、列按 x 间隔），复用/参考 ExtractStripper 位置处理。
- 扫描/乱码检测与错误码（PdfErrors 增 ERR_PDF_SCANNED_PAGE 等 + i18n 登记）。
- 测试：合成 PDF（正常文本页/多列页/控制字符页/空文本层页（仅图片）/U+FFFD 页）逐用例断言 + 错误路径 fail-loud。

### Out Of Scope

- OCR；逐字符字体信息导出；表格结构重建（tabula 已有）。

## Execution Plan

### Phase 1 - 抽取/锚点/检测

Status: completed
Targets: `io/nop/pdf/text/*`（新增）、`PdfDoc.java`、`PdfErrors`、i18n、测试

- Item Types: `Feature`

- [ ] 页文本抽取（strip 模式）：pdfio pageText 语义直译（显式 `setLineSeparator("\n")`——R1-MN1 平台一致性；控制字符剥离字符集对齐；`[ \t]+`→单空格；trim；maxChars）；页锚拼接输出（`<a id="pdf-page-N"></a>` **独立成行**前置于每页——R1-MN2，保 WI15 行级回填语义）。**空白归一仅 strip 模式**（R1-M1）。
- [ ] layout 模式：TextPosition 坐标驱动（y 聚类成行、x 间隙按字号比例重建多空格——保留重建 run，不做归一）；多列页文本序保持视觉序；测试断言 layout 输出含 ≥2 连续空格（R1-M1）。
- [ ] 扫描检测谓词（R1-M3 定版）：页 trim 后 charCount==0 且 `hasContents()` → ERR_PDF_SCANNED_PAGE（.param 页码）；scanPolicy=skip 跳过并在结果集标记 scannedPages（不静默）；"图片占面"不入谓词，stats 字段移交 WI17。
- [ ] 乱码双路（R1-M2）：PDFTextStripper 子类 override showGlyph 统计 toUnicode==null 事件（unmappedCount）+ U+FFFD 字面计数；options.fffdPolicy（off/warn/error）。
- [ ] PdfErrors 新码（ERR_PDF_SCANNED_PAGE/ERR_PDF_GARBLED_TEXT）+ i18n zh-CN 登记（同 :1117-1120 文件）+ `check-error-param-consistency.mjs --module nop-format/nop-pdf` exit 0。
- [ ] 测试（上列全覆盖 + R1-MN4：scanPolicy=skip 的 scannedPages 标记断言、fffdPolicy warn/error 分支、混合多页（文本+空白+图片页）聚合 stats 用例、无 ToUnicode 合成字体页 unmappedCount>0、layout 输出 ≥2 连续空格、锚独立成行断言）。

Exit Criteria:

- [ ] 测试全绿；**端到端验证**：合成多页 PDF→extractPageTexts→页锚/文本/统计落盘重读一致；扫描页 fail-loud 断言
- [ ] `./mvnw test -pl nop-format/nop-pdf -am` 全绿
- [ ] No owner-doc update required（能力矩阵 WI18 收口）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: completed
Targets: roadmap、日志

- Item Types: `Proof`

- [ ] roadmap WI16 勾选；check 工具全过；全量构建（恢复遍口径）。

Exit Criteria:

- [x] roadmap Work Item Status WI16 勾选
- [ ] `ai-dev/logs/` 记录完成状态
- [ ] `./mvnw clean install -T 1C`（或恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 版面/锚点/检测有 focused 测试（非 smoke）
- [x] 受影响 owner docs：No owner-doc update required（WI18 收口）——pdf-text-extraction.md 已落档
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2485-wi16-layout-text-page-anchors.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- RTL/竖排版面；阅读顺序（reading order）推断。

## R1 审查修订记录

2026-10-19 R1（独立子代理 agent_4e0a505d，5 Major + 4 Minor）：M1 空白契约按模式定版（归一仅 strip；layout 保留重建 run + ≥2 连续空格断言）；M2 乱码信号改双路（unmapped-glyph 事件计数为主——PDFBox 字节码实证从不合成 U+FFFD，字面 U+FFFD 计数为兜底；信号定义即 WI17 上游契约）；M3 扫描谓词定版（charCount==0 && hasContents()；图片占面移出谓词移交 WI17；vector-only 页同样硬错的裁定入 design）；M4 WI15 移交承接（baseline 记录 + layout 喂 TOC_ENTRY_RE 对版测试 + toc 切换时机 follow-up）；M5 i18n 事实修正（四码已登记 :1117-1120，R1-M5-1/3 误报；新码同文件登记 + consistency gate 保留）；MN1 显式 setLineSeparator("\n")；MN2 锚独立成行；MN3 新包裁定 + ExtractStripper 仅参考模式；MN4 测试补 scanPolicy/fffdPolicy/混合多页/unmapped 用例。

## Closure

Status Note: 扫描/乱码/版面/锚点四通路测试钉住（含 unmapped 计数连通性与 layout ≥2 空格断言）；模块全量 164 绿；构建恢复遍证据见 Closure。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: <<待独立子 agent 审计后回填>>

Follow-up:

- WI16 页文本契约与 toc 路径切换消费 layout 文本的时机：WI18 验收时组合裁定
- 真实 unmapped 场景（无 ToUnicode Type0 字体）端到端测试需自定义字体——watch-only
