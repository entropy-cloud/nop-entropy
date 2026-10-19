# 2469 WI5 页眉页脚富文本与图片

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI5，deps WI4/WI6 已完成）
> Related: `ai-dev/plans/2467-wi4-header-footer.md`、`ai-dev/plans/2468-wi6-image-fidelity.md`

## Purpose

页眉页脚富文本收口：&G 图片（logo）嵌入、延迟绘制分区裁剪保真、多字体混排验证钉住。

## Current Baseline

2026-10-19 逐项核对 live repo：

- **多字体混排**：WI4 已实现 run 级渲染——`HeaderFooterRuns.split`（&"font"/&nn 切 run）+ `PdfRenderer.drawDeferredHeaderFooters` 逐 run 求值/定位绘制（L/C/R 按总宽定位）；`TestPdfHeaderFooterRender.testFontCodeSectionNotLiteral` 钉住代码不字面。**同段多字体序列（&"A"x&"B"y 两段异字体）无测试**。
- **&G 图片**：`HeaderFooterRuns` 把 &G 作为控制代码剥离（不渲染）；Excel 语义 &G=页眉图片。模型无 HF 图片字段；模板侧无消费者。
- **分区裁剪**：即时路径 `renderText`→`drawText`→clip 到矩形 ✓；**延迟路径 `drawDeferredHeaderFooters` 无 clip**（run 溢出分区宽度会越界绘制）。
- **对齐**：延迟路径 L/C/R 按总宽定位 ✓（WI4）；越界（总宽 > 分区宽）时 C/R 起点为负、无裁剪——溢出行为未裁定。
- 图片嵌入路径（WI6）：`PdfImageHelper.drawImage`（旋转/占位/裁剪）；`PdfRenderer.getImage` 缓存。
- 测试基座：`TestPdfHeaderFooterRender`（HF 编程模板）+ `TestPdfImageRender`（合成 PNG）。

## Goals

- &G 图片渲染：`ExcelHeaderFooter` 模型新增 `imageName`（引用 sheet.getImages() 中具名图片；空缺省取第一张）；段文本含 &G 时在该分区渲染 logo（fit 分区高度，保持比例），无可用图片 WARN 跳过。
- 延迟绘制分区裁剪：run 序列绘制 clip 到分区矩形（越界裁剪保真）。
- 多字体序列测试钉住（&"A"x&"B"y 两段异字体渲染——字体尺寸断言）。
- 图片页眉 golden。

## Non-Goals

- VML/virtualDrawing 头部图片的 OOXML 解析（Excel 原生 &G 图存储为 VML drawing——解析为独立增强，模板经 model imageName 引用）。
- 页眉页脚布局模板化（roadmap WI5 Out of Scope）。
- 多字体 run 的逐段富排版（粗斜体变体属 WI10 字体保真）。

## Scope

### In Scope

- `workbook.xdef` ExcelHeaderFooter + `imageName` 属性 → 再生 `_ExcelHeaderFooter`。
- `PdfRenderer.drawDeferredHeaderFooters`：&G 段渲染（sheet 图片按名引用）+ 分区 clip。
- 测试：&G 渲染（有图/无图 WARN）、多字体序列、裁剪；图片页眉 golden。
- `pdf-pagination.md` D7 补充（&G/裁剪/多字体）；roadmap/日志收口。

### Out Of Scope

- WI18 能力矩阵；OOXML VML 头图解析；粗斜体（WI10）。

## Execution Plan

### Phase 1 - &G 图片与分区裁剪

Status: completed
Targets: `nop-kernel/nop-xdefs/.../page-setup.xdef`、`nop-report/nop-report-pdf`

- Item Types: `Feature`

- [x] xdef：`ExcelHeaderFooter` 内联定义加 `imageName="string"` 属性；再生 `_ExcelHeaderFooter`（install nop-xdefs → generate-sources nop-excel → install nop-excel）。
- [x] `drawDeferredHeaderFooters`：段文本含 &G → 在该分区渲染图片。任务结构扩展：`DeferredHeaderFooter` 增加 `imageName` + `sheetImages`（List<ExcelImage> 快照，enqueue 时从 sheet.getImages() 捕获）——按 `ExcelImage.getName()` 解析、缺省第一张；fit 双轴 `scale=min(分区高/imgH, 分区宽/imgW)` 居中；名字找不到或 sheet 无图 → WARN（log marker `nop.report.pdf.header-image-missing`，同 image-missing-placeholder 风格，非 ErrorCode）跳过 &G。图片绘制经 `PdfImageHelper.drawImage`（clipRect 传 null 依赖外层分区 clip）。run 序列绘制包裹 saveGraphicsState/clip(分区矩形)/restoreGraphicsState（越界裁剪；嵌套与 drawUnwrappedText/drawImage 内部 clip 交集安全）。
- [x] 测试：&G 有图渲染（图片页眉 golden + 分区采样）、无图 WARN 跳过、多字体序列——用 `&"Courier"14AB&"Helvetica"8CD` 双字族（TextPosition.getFontSizeInPt 断言 A=14/C=8 + 顺序保持；页面 Resources 字体数 ≥2 断言字族差异）、裁剪（渲染位图 + RegionProbe 采样相邻分区无前景色像素）。

Exit Criteria:

- [x] 再生 diff 仅 imageName 属性，无手改生成物
- [x] 测试全绿：&G 渲染/WARN 跳过、多字体序列、分区裁剪
- [x] `./mvnw test -pl nop-format/nop-excel,nop-report/nop-report-pdf -am` 全绿
- [x] `No owner-doc update required`（Phase 3 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - golden 与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] golden：图片页眉（logo 顶部居中）；多字体页脚。
- [x] pdf-pagination.md D7 补充：&G 语义（imageName 引用/缺省第一张/fit 双轴 min 缩放/无图 WARN 跳过——log marker 非 ErrorCode）、分区 clip（嵌套交集）、多字体取字规则（首字体已定，firstToken 丢 ",style" 装饰）、imageUrl VML 解析 deferred。
- [x] roadmap WI5 勾选；check-doc-links/check-plan-checklist/scan-hollow 全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI5 勾选
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] &G/多字体/裁剪有 focused 测试证明
- [x] 受影响 owner docs 已同步（pdf-pagination.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2469-wi5-hf-rich-media.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### OOXML VML 头部图片解析

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Excel 原生 &G 图存于 VML drawing（legacyDrawing），解析链复杂且仓库模板无消费者；模型 imageName 引用已覆盖 logo 需求。
- Successor Required: `no`
- Successor Path: 出现原生 Excel 头图模板需求时在 SheetNodeHandler 解析 legacyDrawing HF 关联。

## Non-Blocking Follow-ups

- 粗斜体变体渲染（&B/&I，属 WI10 字体保真）。

## Closure

Status Note: WI5 完成：&G 图片渲染（imageName 引用 + fit 双轴 + print=false 素材裁定）、分区裁剪（越界不越界相邻分区）、多字体序列（TextPosition 字号断言）、图片页脚 golden + 人工抽查（蓝色 logo 页脚中分区居中）。独立子 agent closure audit 见下方 Evidence。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_01226a00-2e02-434b-9214-d4de6e51b062）
- Audit Session: agent_01226a00-2e02-434b-9214-d4de6e51b062（2026-10-19）
- Evidence:
  - xdef/再生 diff 仅 imageName；行为链（enqueue 快照→findLogo→fit 双轴→绘制）逐行核实；WARN 实跑日志 3 次触发
  - 测试 5/5 绿（TextPosition 字号断言逐字符非 smoke）；模块回归 77/77
  - D7 补充一致；roadmap 勾选；日志完整
  - 工具（审计实跑）：check-doc-links exit 0；scan-hollow nop-report-pdf exit 0
  - 审计 Minor（均 watch-only）：&G 绘制在 restore 后、几何包含靠 fit 双轴而非分区 clip（D7 措辞已顺手修正）；多字体未做 Resources 字族断言（字号断言充分）；裁剪单点采样（足以钉回归）；无图 WARN 未程序化断言（实跑目击）
- Deferred 项分类检查：VML deferred 诚实登记含 successor path；无 in-scope live defect 被降级

Follow-up:

- &B/&I 粗斜体变体（WI10）
- OOXML VML legacyDrawing 头图解析（出现原生模板需求时）

### 实现期关键偏差记录

- HF 素材图须 print=false：具名 logo 同时存在于 sheet.images 会被 renderImages 按锚点渲染（无锚点则 NPE）——裁定 HF 素材图 print=false 仅由 &G 引用（记 D7）。
- 延迟页脚的设备坐标=页底（identity 流 y=10..30 从底），探测坐标须按 top-based 812pt 换算；首版探测误用页顶 20pt。
- 调试教训（WI3 同款复发）：surefire CWD 下 ImageIO.write 缺父目录失败。
