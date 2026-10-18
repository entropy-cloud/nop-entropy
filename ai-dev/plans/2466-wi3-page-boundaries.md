# 2466 WI3 分页边界健壮性

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI3，deps WI2 已完成）
> Related: `ai-dev/plans/2464-wi1-print-titles.md`、`ai-dev/plans/2465-wi2-fit-scale.md`、`ai-dev/design/nop-report/pdf-pagination.md`

## Purpose

分页边界不溢出、不丢内容、不残留多余空白：超页行按 Excel 语义独立成页并裁剪、跨拆分边界合并单元格显式裁定、手动分页符（rowBreaks/colBreaks）全链路支持、空白尾行尾列裁剪、重复块边界一致性复验。

## Current Baseline

2026-10-19 逐项核对 live repo：

- **手动分页符：模型字段已存在但为死字段**——`workbook.xdef:135-138` 已有 `<pageBreaks xdef:name="ExcelPageBreaks">`（`<cols>/<rows>` int-list），生成 `ExcelPageBreaks`（retention 类已存在）；`IExcelSheet.getPageBreaks()`（IExcelSheet.java:25）已经 `ExpandedSheet.java:48` 复制到渲染端（ExcelModelHelper.java:22、ExcelSheet.java:43 亦复制）。但全仓无解析器写入（SheetNodeHandler 无 rowBreaks/colBreaks/brk 分支）、无渲染消费（TableSplitHelper 无 break 感知）——语义未定义的死字段。**裁定：复用该字段作为 breaks 唯一表示（SSOT），语义定为 0-based 索引、break 发生在该行/列之后（该行/列为页内最后一个，对齐 OOXML brk@id）**；不新增 rowBreaks/colBreaks 字段避免双表示。
- **超页行现状**：`TableSplitHelper.calculatePageRegion` 行循环 `if (currentHeight + rowHeight > maxHeight && row > startRow) break` 的 `row > startRow` 守卫（:91，行-only :60）使超高行独立成页，`startRow = lastRowIndex+1` 正确推进（:37-39,45），后续行不丢。**渲染裁剪机制**：表格绘制无显式 clip（`applyPageSetup` 的 clip rect 仅用于图片，:320,362-364），超高行页外内容靠 PDF MediaBox 裁切、页边距区域可见渗漏——测试按此真实机制断言（不断言干净裁剪）。行为无任何测试覆盖，待钉住。
- **合并单元格跨拆分边界**：渲染侧 `getSubTable(region).clip()` 裁剪跨边界 span（pdf-pagination.md D2 已裁定列拆分接受裁剪视觉）；行拆分方向同一代码路径，无专项测试。
- **空白尾行尾列**：渲染入口无裁剪，空白尾行会产生多余页或浪费页容量；无测试。
- **重复块边界**：`PdfSheetRenderer` 中 `k = Math.min(repeatRows, region.getFirstRowIndex())` clamp 处理拆分落在重复前缀内部的情形；无专项测试。
- WI2 已建立：`renderSheet(IExcelSheet)` 入口统一解析（repeat/scale），参数贯穿模式；测试基座 `TestPdfPrintTitlesRender`/`TestPdfFitScaleRender` 的编程模板模式可复用。
- `TableSplitHelper` 位于 `nop-kernel/nop-core`（框架核心 Protected Area，plan-first——本计划即载体；需 nop-core 回归测试）。
- 代码生成链路：workbook.xdef → `gen-excel-xdsl.xgen` → `_ExcelSheet` 等（WI1 确认的再生链路：install nop-xdefs → generate-sources nop-excel）。

## Goals

- `ExcelSheet` 经**既有** `pageBreaks` 字段（`ExcelPageBreaks.rows/cols`）承载 breaks（语义定为 0-based 索引、break 发生在该行/列之后——即该行/列是页内最后一个）；xlsx 解析器支持 `<rowBreaks>/<colBreaks>/<brk>` 写入该字段。
- `TableSplitHelper` 新增 break 感知重载（旧签名委托空集，零回归）；渲染器把模型 breaks 传入两遍拆分。
- 超页行行为测试钉住（独立成页 + 裁剪，不丢其他行）；合并单元格跨行/列拆分边界裁剪视觉测试钉住；空白尾行尾列裁剪（裁定"无值且无样式"为空白）；重复块边界 clamp 测试钉住。
- 边界用例 golden。

## Non-Goals

- 页眉页脚（WI4）、图片/图表（WI6/WI7）。
- 超页行内部内容的跨页续排（Excel 语义即整行裁剪，不做行内拆分）。
- 合并单元格跨页重建（D2 已裁定接受裁剪视觉，维持）。
- xlsx 写回 breaks（Follow-up，同 WI1/WI2 写回裁定）。
- `nop-core` TableSplitHelper 旧签名的行为变更（仅新增重载）。

## Scope

### In Scope

- `ExcelPageBreaks` retention 类语义 Javadoc（复用既有模型，无 xdef/生成物变更）。
- `SheetNodeHandler`/`SheetContentsHandler`/`SimpleSheetContentsHandler` 解析链。
- `nop-core` `TableSplitHelper` break 感知重载 + nop-core 单测。
- `PdfSheetRenderer` breaks 传入 + 空白尾行尾列裁剪。
- 测试：解析单测、nop-core 拆分单测、渲染边界测试（超页行/合并单元格/空白裁剪/重复块边界/手动分页符）、golden。
- `pdf-pagination.md` 新增边界裁定；roadmap/日志收口。

### Out Of Scope

- docs-for-ai 用户文档能力矩阵（WI18）。
- HTML/XLSX 渲染器的 breaks 渲染（模型字段对它们自然可见，渲染语义另议）。

## Execution Plan

### Phase 1 - 手动分页符全链路

Status: completed
Targets: `nop-kernel/nop-core/TableSplitHelper`、`nop-format/nop-excel`（retention Javadoc）、`nop-format/nop-ooxml/nop-ooxml-xlsx`、`nop-report/nop-report-pdf`

- Item Types: `Feature`

- [x] **无 xdef/生成物变更**（复用既有 `pageBreaks` 模型，Blocker-1 裁定）：`ExcelPageBreaks` retention 类补语义 Javadoc（0-based、break-after）。
- [x] 解析链：`SheetContentsHandler` 新增 default 方法 `pageBreak(boolean isRowBreak, int id)`；`SheetNodeHandler` 新增 `rowBreaks/colBreaks` 容器状态字段（boolean 区分行/列）+ `brk` 叶子解析（id int，man 属性容缺省不参与语义）；`SimpleSheetContentsHandler` 收集写入 `sheet.getPageBreaks()`（懒创建）。
- [x] `TableSplitHelper` 新增重载 `splitTable(..., Set<Integer> rowBreakAfter, Set<Integer> colBreakAfter)`：页内包含 break 位置即强制分段（该行/列为本页最后一个）；旧签名委托空集。nop-core 单测：break 生效、break 超界忽略、break 与容量上限叠加。
- [x] `PdfSheetRenderer.renderSheet(IExcelSheet)`：从模型提取 break id 集合（去重、排序、过滤越界与重复），传入两遍 splitTable。**fit×breaks 裁定**：fit scale 按无 break 的表尺寸计算（与 breaks 无关），手动 break 优先强制提前分页——实际页数可超过 fit 目标 N（break 是用户显式意图，优先于 fit 估算；真实 Excel 在 fit 模式忽略手动 break，本裁定相反并记入 D6）。Phase 1 测试含 fit+breaks 组合用例钉住该裁定。
- [x] 解析单测 + 渲染测试：40 行在第 10 行后设 rowBreak → 页 1 含 R10、页 2 首内容 R11；colBreak 同理列带。

Exit Criteria:

- [x] 无 xdef/生成物 diff（复用既有 pageBreaks 模型的裁定落实）
- [x] nop-core 拆分单测 + 解析单测 + 渲染分页符测试（含 fit+breaks 组合）全绿
- [x] `./mvnw test -pl nop-kernel/nop-core,nop-format/nop-ooxml/nop-ooxml-xlsx,nop-report/nop-report-pdf -am` 全绿
- [x] **接线验证**：模型 breaks 经渲染器真实作用于分页（测试页边界断言）
- [x] `No owner-doc update required`（Phase 3 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 边界健壮性与空白裁剪

Status: completed
Targets: `nop-report/nop-report-pdf`、`nop-format/nop-excel`（如需）

- Item Types: `Feature | Proof`

- [x] 空白尾行尾列裁剪：`renderSheet(IExcelSheet)` 入口计算最大非空行/列（cell 有 value 或有 styleId 均视为内容——裁定及理由记设计文档），对拆分与渲染用表做尾部截断；**仅尾部截断不改变保留行/列的下标**，故 `renderImages` 基于全表的锚点偏移坐标基不变（Minor-2 裁定）；全空表回退原表（不产生空 PDF 崩溃）。先在测试中钉住现状（多余空白页/浪费容量），再转绿。
- [x] 超页行测试钉住：单行高 900pt（页内容区 ~772pt）→ 该行独立成页、页外内容按 MediaBox 语义裁切（页边距区可见渗漏，见基线——不断言干净裁剪）、后续行在下一页继续且不丢；行高恰好等于页内容区的边界行不分页。
- [x] 合并单元格跨拆分边界：纵向 merge 跨行拆分边界 → 两页各显示裁剪后部分、无异常无丢失（clip 语义钉住）；横向 merge 跨列拆分同理。
- [x] 重复块边界一致性：拆分点落在重复前缀内部（region.firstRowIndex < repeatRows）→ clamp 行为不产生重复/丢失内容。
- [x] 边界用例 golden（超页行、合并单元格跨页、手动分页符）。

Exit Criteria:

- [x] 边界测试全绿且覆盖上述五类断言
- [x] **视觉优先红线**：`_tmp/` 实际 PDF+PNG、golden 生命周期、人工抽查记录
- [x] **无静默跳过**：空白裁剪不误删有值或有样式的行/列（测试覆盖 style-only 尾行不被裁剪）
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 设计裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-pagination.md 新增 D6：手动分页符语义（0-based after-id、fit 交互）、超页行 Excel 语义（独立成页+裁剪）、合并单元格裁剪视觉（D2 扩展到行拆分）、空白裁定（无值且无样式）、重复块 clamp。
- [x] roadmap WI3 勾选；check-doc-links/check-plan-checklist/scan-hollow（nop-core + nop-report-pdf）全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI3 勾选
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败的恢复遍证据）

## Closure Gates

- [x] Phase 1–3 全部 Exit Criteria 勾选
- [x] breaks 全链路（模型→解析→拆分→渲染）有 focused 测试证明接线
- [x] `nop-core` 公共工具变更带回归测试（旧签名委托等价）
- [x] 受影响 owner docs 已同步（pdf-pagination.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2466-wi3-page-boundaries.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-core --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- xlsx 写回 rowBreaks/colBreaks（同 WI1/WI2 写回裁定）。
- HTML/XLSX 渲染器对手动分页符的渲染语义。

## Closure

Status Note: WI3 三 Phase 完成：手动分页符全链路（复用既有 pageBreaks 死字段为 SSOT、解析、TableSplitHelper break 重载、渲染接入）、超页行/合并单元格/空白裁剪/重复块边界全部测试钉住并 golden；顺带修复 nop-core 0 列表列分页死循环预存缺陷（防御守卫 + 回归钉）。独立子 agent closure audit 见下方 Evidence。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_e9c39d65-bc2f-4f9a-9d62-f06783e91a0c）
- Audit Session: agent_e9c39d65-bc2f-4f9a-9d62-f06783e91a0c（2026-10-19）
- Evidence:
  - Phase 1：git status 证实无任何 xdef/_前缀文件变更；解析链/拆分重载/渲染接入全部落地；审计会话独立实跑三组 focused 测试全绿；接线验证（testRowBreakSplitsAtExactRow 经真实 PdfReportRenderer→PDFTextStripper）；日志含偏差与教训
  - Phase 2：五类断言全绿；视觉红线 golden 5 张 PNG 存在；无静默跳过（hasContent + style-only 50 行制造额外页证明不裁）
  - Phase 3：D6 与实现逐条一致；roadmap WI3 勾选
  - 行为链：解析（inRowBreaks 状态 + endNode 重置 + 无 id 忽略）/拆分（判定在容量检查前、旧签名委托等价、0 行/0 列守卫先行）/渲染（extractBreaks 过滤 + 两遍传参 + trim 下标不变推理）/fit×breaks（resolveScale 不感知 breaks + 测试 ≥4 页 vs 目标 2）全 PASS
  - 特别核查：0 列死循环缺陷经 git diff 推演属实（旧代码 inner while 不执行 → startRow 永不推进），守卫修复 + 回归钉钉住
  - 工具（审计实跑）：check-doc-links --strict exit 0；scan-hollow nop-core exit 0；scan-hollow nop-report-pdf exit 0
  - 审计 Major-1（已处置）：merge 测试原几何（rows 10-12）未跨真实拆分边界——已修正为 rows 36-39 跨 37/38 边界 + 新增横向跨列带用例 testMergedCellAcrossColSplitBoundary，10 测试全绿
  - Minor-2（watch-only）：Non-Goals"旧签名零变更"与 0 列守卫（挂死→空集）字面相抵，偏差三处入档
- Deferred 项分类检查：Follow-ups（xlsx 写回、HTML/XLSX 渲染器 breaks）确未实现且登记诚实，无 in-scope live defect 被降级

Follow-up:

- xlsx 写回 rowBreaks/colBreaks（同 WI1/WI2 写回裁定）
- HTML/XLSX 渲染器对手动分页符的渲染语义

### 实现期关键事实与偏差记录

- plan 第一轮审查（Blocker-1）推翻原"新增 rowBreaks/colBreaks 字段"方案：workbook.xdef 已有 pageBreaks 死字段，改为复用（SSOT），零 xdef/生成物变更；fit×breaks 裁定与真实 Excel 相反（break 优先）已记 D6。
- 调试期发现并修复 nop-core 预存缺陷：0 列表（行内无单元格）在列分页分支外层 while 死循环（startCol 永不推进）——原实现即存在，任何空列数表传给 splitTable 都会挂死渲染；加防御守卫 + testZeroColumnTableTerminates 回归钉。
- 测试基座教训（记入日志）：BaseTable/BaseRow 构造器参数均为容量不建实体（行/列需显式添加）；null 单元格会被 SubTableView.clip() NPE（生产表无 null 单元格）；CellRange 无 equals（断言需逐字段比较）。
