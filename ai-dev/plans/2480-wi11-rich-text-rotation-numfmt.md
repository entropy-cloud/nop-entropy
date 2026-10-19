# 2480 WI11 单元格富文本与竖排/数字格式

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI11，deps WI10/WI9 已完成）
> Related: `ai-dev/design/nop-report/pdf-font-strategy.md`（D5 WI10 伪变体）、`ai-dev/design/nop-report/pdf-pagination.md`（D1–D11）
> Review: R1（1 Blocker + 3 Major + 8 Minor）+ R2（Blocker=0；2 Major + 4 Minor）全部吸收；见修订记录

## Purpose

单元格内富文本 run（同格多字体）、竖排/旋转文本、数字格式三类渲染语义在 PDF 导出生效：xlsx 模板声明的富文本 run 解析进模型并逐 run 渲染；textRotation（含 255 竖排）驱动文本方向；numberFormat 在 xlsx→PDF 直接导出路径生效（报表流修复 Date/Temporal 与 Excel 串行日期）。

## Current Baseline

2026-10-19 逐项核对 live repo（R1 审查核对通过项为准）：

- **模型已承载**：`ExcelStyle` 已有 `numberFormat`（String，_ExcelStyle.java:119）/`rotate`（Integer，:133）/`wrapText`（:161）；`ExcelCell` 已有 `richText`（`ExcelRichText.parts: List<ExcelRichTextPart(font, text)>`，_ExcelCell.java:87）——模型零改动（rotate 字段 javadoc 除外，见 F4 裁定项）。
- **解析现状**：`StylesPartParser` 已解析 numFmt→style.numberFormat（:255,:264）与 alignment@textRotation→style.rotate（:320-323，原值 0-180/255 未解释）。`SharedStringsTableParser` 把 `<si>` 富文本 run 拍平为纯字符串（characters 累加）；`SheetNodeHandler` 的 inline `<is><t>` 同样只累积文本（`isTextTag` :137-152、outputCell :494-554），richText 字段无人写入。**注意（R1-F6）**：`value` 缓冲同时是拍平文本来源，`<is>` 内多个 `<t>` 逐段追加对拍平恰好正确——run 捕获必须用独立缓冲，不得复用/清空 value；inline `<is><rPh>` 的 `<t>` 当前同样进 value（既有拍平行为，本计划不改拍平语义）。
- **渲染现状**：`PdfTableRenderer.renderCell`（:123，getText :132）只画单字体串；rotate 未被任何渲染代码消费；竖排无处理。direct path 单元格确为 `ExcelCell`（SubTableView.getCell 委托原表），`instanceof ExcelCell` 判定可行。
- **数字格式现状（R1-F1/F2 修正后的事实）**：报表流 `ExpandedSheetEvaluator`（instanceof Number 在 :173）仅对 Number 经 `ExcelFormatHelper.getFormat` 格式化——但 `createDateFormat` 返回 `SimpleDateFormat`（ExcelFormatHelper.java:144-230，:222），`DateFormat.format(Object)` 只接受 Date/Number：**传 Temporal 抛 IllegalArgumentException（非返回 null）**；**传 Number 按 epoch 毫秒解释——真实 xlsx 的"数值+内置日期格式"（串行日期）会被渲染成 1970 年垃圾**（既有 evaluator 路径已埋此缺陷）。xlsx→PDF 直接导出路径完全不应用 numberFormat。`ExcelDateHelper.getJavaDate(double, ...)`（:151-239）是串行日期转换积木。
- **rPr 词汇表（R1-F3 修正）**：SST/inline 富文本是 SpreadsheetML `rPr`（`<b/>`/`<i/>` 空元素、`<sz val>` **pt 值**、`<color rgb>`、`<rFont val>` 带子元素）；`ChartTextStyleParser` 是 DrawingML `a:rPr`（属性携带 sz/b/i、sz 需 /100、XNode 树）——**两套词汇表不兼容，不共享 helper**；且 SharedStringsTableParser/SheetNodeHandler 是 XNodeHandlerAdapter 流式回调，无 XNode 可喂。
- **可复用**：`ExcelDateHelper`（串行日期）、WI10 `FontResolution` 解析缓存（PdfRenderer）；`PdfGoldenManager` 在 nop-pdf main scope（nop-report-pdf 已依赖）。

## Goals

- xlsx 共享字符串与内联字符串的富文本 run 解析进 `ExcelCell.richText`（拍平文本行为不变）；PDF 渲染逐 run 字体/颜色/字号。
- `style.rotate` 驱动 PDF 文本方向，**按 ECMA-376 语义**：1–90 逆时针 value 度、91–180 顺时针 (value-90) 度、255 竖排逐字符堆叠。
- numberFormat 双路统一走新的 `ExcelFormatHelper.formatValue(value, formatStr)`：类型分派（DateFormat → Date/Temporal 转 Date/Number 经 getJavaDate 转串行日期；DecimalFormat → Number），不可转换组合显式返回 null 回退原样（类型判断，非吞异常）；修复 evaluator 既有 epoch 缺陷。
- 富文本/竖排/数字格式 golden + 操作符/TextPosition 级断言。

## Non-Goals

- xpt 报表流（ExpandedCell）的富文本渲染（XptCellModel 不承载 richText，登记限制）。
- shrinkToFit、indent 渲染；富文本 + wrapText 逐 run 折行（富文本超宽降级单行裁剪，登记限制）。
- Excel 格式串全量语义（条件段/颜色段仅按 ExcelFormatHelper 现有能力）。

## Scope

### In Scope

- `nop-ooxml-xlsx`：SharedStringsPart 增并行 richItems；SharedStringsTableParser 保留 run 结构；SheetNodeHandler 独立缓冲捕获 inline run（value 拍平不动、rPh 不进 runs）；SheetContentsHandler 富文本通道（default 方法）。
- `nop-excel`：ExcelFormatHelper 新增 `formatValue(Object, String)` 类型分派入口（Number+日期格式经 ExcelDateHelper.getJavaDate；Temporal 转 Date；不可转换→null）。
- `nop-report-pdf`：PdfTableRenderer 富文本 run 绘制（renderer 侧逐 run FontResolution 解析后传 PdfStyleHelper 绘制）+ rotate/竖排 + numberFormat 直路回退（仅 renderCell——renderText 为页眉页脚无 cell value，不适用）。
- `nop-report-core`：ExpandedSheetEvaluator 数字格式分支改走 formatValue 并扩 Date/Temporal。
- 测试、owner doc（D12）、xdef rotate 注释修正（见 F4 裁定项）。

### Out Of Scope

- nop-core `ICellView` 契约扩展（rich text 走 renderer 侧 `instanceof ExcelCell` 判定）。

## Execution Plan

### Phase 1 - 解析侧：富文本 run 进模型

Status: completed
Targets: `SharedStringsTableParser.java`、`SharedStringsPart.java`、`SheetNodeHandler.java`、`SheetContentsHandler.java`、`SimpleSheetContentsHandler.java`

- Item Types: `Feature`

- [x] SharedStringsPart 增 `richItems: List<ExcelRichText>`（与 items 同长，无 run 位为 null；`getRichItemAt`）；SharedStringsTableParser 在 `<si>` 含 `<r>` 时构建 ExcelRichText——**parse 包新建 SpreadsheetML rPr→ExcelFont 映射（R1-F3：不复用 ChartTextStyleParser/DrawingML；流式回调上下文；`<b/>`/`<i/>` 空元素、`<sz val>` 为 pt 不除 100、`<color rgb>`、`<rFont val>`）**；`<rPh>` 的内容不进 runs（t 文本跳过）；items 拍平行为不变。
- [x] SheetNodeHandler：inline `<is>` 内 `<r>`/`<rPr>`/`<t>` 用**独立缓冲**捕获（R1-F6：value 拍平缓冲不动；rPh 内容同样不进 runs）；outputCell 对 SST_STRING 分支查 `getRichItemAt(idx)`；经 SheetContentsHandler 新 default 方法 `richText(CellPosition, ExcelRichText)`（缺省 no-op）传给 SimpleSheetContentsHandler 写当前 cell。
- [x] 测试：TestSharedStringsTableParser 扩展（TestSharedStringsRichText：多 run si→richItems 结构断言 + sz pt 语义断言 + rPh 排除 + items 拍平一致）；TestSheetNodeHandlerRichText（inline run fixture→ExcelCell.richText；SST rich 查找；无 rich 旧路径回归）。

Exit Criteria:

- [x] 解析单测覆盖共享字符串与内联字符串两路；richItems 与 items 并行不破坏既有消费方；拍平文本与改动前逐字节一致
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-xlsx -am` 全绿（模块全量 88 tests）
- [x] No owner-doc update required（解析能力，能力矩阵由 WI18 统一收口）
- [x] `ai-dev/logs/` 对应日期条目已更新

执行偏差（Phase 1）：测试夹具中 SimpleSheetContentsHandler.cell 的 setLocation 依赖 workbook.resourcePath()（Guard 非空）——测试 workbook 需先 setLocation（既有 PageBreaks 测试无 cell 回调故未暴露）。

### Phase 2 - 渲染侧：富文本/竖排/数字格式

Status: completed
Targets: `PdfTableRenderer.java`、`PdfStyleHelper.java`、`ExpandedSheetEvaluator.java`、`ExcelFormatHelper.java`

- Item Types: `Feature`

- [x] **formatValue（R1-F1/F2）**：`ExcelFormatHelper.formatValue(Object value, String formatStr)`——getFormat(formatStr) 为 null（含 General/@/解析失败）→null；结果为 DateFormat 时：Date→format；Number→`ExcelDateHelper.getJavaDate` 转串行日期再 format；Temporal（LocalDate atStartOfDay/LocalDateTime atZone/LocalTime 组合纪元日）→Date→format；其余→null。结果为 DecimalFormat 时：Number→format；其余→null。**兜底分支（R2-m1）：非 DateFormat/DecimalFormat 的特判 Format（SSN/ZipPlusFour/Phone，ExcelFormatHelper:76-92 静态注册）+ Number→format（保既有 evaluator 行为，无 epoch 风险）**。转换用系统默认时区（裁定记 D12）。**不可转换=类型判断返回 null，不 try/catch 吞异常**。
- [x] 富文本绘制：renderCell 检测 `cell instanceof ExcelCell && getRichText() != null` → **富文本优先（R2-M1）：rotate 有值时仍绘制富文本并忽略 rotate（组合偏差登记 D12 限制）**；renderer 侧构建已解析 run 列表（**run 渲染记录 = text + FontResolution + 颜色（R2-M2）：颜色取 part.font.getFontColor() 经 convertColor 转 PDColor，null 回退单元格样式色（`<color theme>` 同回退，R2-m4，登记 D12）**）→ 传 PdfStyleHelper.drawRichText（纯静态工具，接收已解析 run 列表——R1-F8）绘制：宽度合计 + 水平对齐 + 既有裁剪；getText() 为回退路径。
- [x] rotate 渲染（ECMA-376 语义，R1-F4）：renderCell 读 `style.getRotate()`——255 → 竖排（逐字符独立文本块、水平居中、自上而下、行距 1.2×fontSize）；1–90 → 逆时针 value 度；91–180 → 顺时针 (value-90) 度（即 CCW 角 = value≤90 ? value : -(value-90)）；单行文本以单元格中心为锚点 Tm 矩阵旋转，超宽由既有 clip 裁剪（wrapText 忽略，登记限制）；**富文本在场时让位（富文本优先，R2-M1）**。
- [x] numberFormat 直路回退（仅 renderCell，R1-F9）：`style.getNumberFormat()` 存在且 value instanceof Number/Date/Temporal（R1-F10）→ `formatValue` 非 null 则替换显示文本。
- [x] evaluator 修复：ExpandedSheetEvaluator :173 分支改走 formatValue（Number+日期格式从 epoch 缺陷转为串行日期语义；扩 Date/Temporal；特判 Format 行为由兜底分支保持）。
- [x] xdef 注释修正（R1-F4）：`style.xdef` rotate 注释改为 ECMA 全语义；再生链路 `install -pl nop-kernel/nop-xdefs → generate-sources -pl nop-format/nop-excel`，git diff 核实仅 _ExcelStyle.java rotate javadoc 两处漂移。
- [x] 测试（TestPdfRichTextRender）：(a) 双 run 异字体/字号 TextPosition 断言 + 双 run 异色 sc 断言 + 提取一致；(b) rotate 90 → Tm (0,1,-1,0) + rotate 135 → CW45 系数；(c) rotate 255 竖排提取序；(d) numberFormat：#,##0.00 / 0.00% / 串行日期 45000→2023-03-15 / SSN 特判保持 / String 回退 / LocalDate；(e) 无格式原样；(f) golden；人工截图抽查 PASS（rich-page-1.png：红色粗体 14pt run1 + 蓝色 run2、UP 逆时针 90°、A/B 竖排、1,234.50 全部视觉可辨）。

Exit Criteria:

- [x] 测试全绿且 (a)-(f) 覆盖；**接线验证**：富文本 run 绘制确实消费 ExcelCell.richText（TextPosition 字体名断言含 Helvetica-Bold），formatValue 串行日期/Temporal 分派各有断言
- [x] **端到端验证**：程序化模型 → PdfReportRenderer → PDF → 提取/golden 完整路径走通
- [x] **无静默跳过**：formatValue 不可转换组合显式 null 回退（类型判断），无吞异常路径
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am`（112 全绿）与 `-pl nop-report/nop-report-core`（86 全绿）；`-pl nop-report/nop-report-demo -am` 补跑见收口记录
- [x] No owner-doc update required（Phase 3 承接）
- [x] `ai-dev/logs/` 对应日期条目已更新

执行偏差（Phase 2）：

- 测试 Workbook 的 defaultStyle=styles.get(0)（平台既有语义）——"无格式"单元格须挂显式无 numberFormat 的样式，不能用无 styleId 表达（首个样式会兜底生效）。
- 匿名 PDFTextStripper.writeString 覆写不调 super 会吞掉文本收集（首跑提取空串的假象根因）；且覆写含 super 调用时须声明 throws IOException。
- nop-report-pdf 测试类路径解析的是 m2 已安装的 nop-excel jar——nop-excel 新增 API 后必须 `install -pl nop-format/nop-excel` 才对测试可见（reactor -am 只覆盖 compile 不覆盖测试解析）。

### Phase 3 - 裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`（D12）、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-pagination.md 新增 D12：富文本 run 渲染语义（xpt 流不承载、与 rotate/wrapText 组合的限制、rPh 不进 runs）、rotate ECMA 语义与锚点/裁剪裁定、formatValue 类型分派语义（时区裁定、串行日期、回退语义）。
- [x] roadmap WI11 勾选（+ Milestone 样式保真达标）；check-doc-links/check-plan-checklist（本文件）/`scan-hollow --module nop-report/nop-report-pdf --severity high` 与 `--module nop-format/nop-ooxml/nop-ooxml-xlsx --severity high`（R1-F12/R2-m3 统一口径）全过；收口前全量构建（恢复遍证据口径，见 Closure 证据）。

Exit Criteria:

- [x] 设计文档与实现一致（含登记的限制）
- [x] roadmap Work Item Status WI11 勾选
- [x] `ai-dev/logs/` 对应日期条目记录完成状态
- [x] `./mvnw clean install -T 1C`（恢复遍证据：排除项均属其他工作流并单跑复核，逐项见 Closure）

## Closure Gates

- [x] Phase 1–3 全部 Exit Criteria 勾选
- [x] 富文本/竖排/数字格式各有 focused 测试证明（TextPosition/Tm/串行日期断言非 smoke）
- [x] 受影响 owner docs 已同步（pdf-pagination.md D12；xdef 注释修正已落地）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2480-wi11-rich-text-rotation-numfmt.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- xpt 流（ExpandedCell/XptCellModel）富文本承载与渲染；富文本 + wrapText 逐 run 折行；旋转文本折行。
- 竖排 codePoint 遍历（增补平面字符当前按 char 拆分为 '?'——closure audit Minor-4，极端边缘，watch-only）。

## Closure

Status Note: 六项审计全 PASS（接线/语义/拍平不变/无静默跳过/文档一致性/遗留走查），无 Blocker/Major；Minor-1（resolveRichRuns null 防护）与 Minor-3（run 缓冲 `<r>` begin 同步 reset）已同批修复并复跑绿（5+6 tests），Minor-2（Phase 3 复选框）随本收口翻牌，Minor-4（surrogate pair）登记 watch-only。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_dd9175fd-d54d-4912-a2a9-b4a93bed4f71（fresh session，与实现不同 task）
- Evidence:
  - 接线验证 PASS：richText 经 `PdfTableRenderer.java:156-158` instanceof 分支 + resolveRichRuns 逐 run FontResolution 消费（SubTableView 委托证实 direct path cell 为 ExcelCell）；formatValue 双路（ExpandedSheetEvaluator.java:172-183 + PdfTableRenderer.java:141-147）；rotate 驱动 drawRotatedText（:159-166）
  - 语义正确性 PASS：formatValue 分派逐分支核实（ExcelFormatHelper.java:138-170，无 try/catch 吞异常，epoch 缺陷消除）；rotate ECMA 映射（PdfStyleHelper.java:508/493-506）；富文本优先 rotate（:155-158）；sz pt 语义（RichTextFontMapper.java:31-41）
  - 拍平不变性 PASS：SST text() 拍平条件同构、runCharacters/runText 独立缓冲、richItems null 补位（测试钉住）
  - 无静默跳过 PASS：新增方法全为真实实现；RichTextFontMapper 未映射元素为 D12 登记的裁剪范围
  - 文档一致性 PASS：D12 逐条对实现；xdef 注释与 _ExcelStyle javadoc 两处漂移同源核实；roadmap WI11 + Milestone 勾选；日志条目在
  - Minor-1/3 同批修复后复跑：TestSharedStringsRichText+TestSheetNodeHandlerRichText 5/5、TestPdfRichTextRender 6/6 绿
  - 构建证据（恢复遍口径）：模块验证 nop-ooxml-xlsx 88 / nop-report-pdf 112 / nop-report-core 86 全绿 + nop-report-demo（TestPdfExportAudit 20 模板）BUILD SUCCESS；全量 `clean install -T 1C` 恢复遍排除清单（均单跑复核绿或预存缺陷）：TestLeaseLifecycle（xdbg plan 2469 预存稳定失败）、TestDebugGatewayE2E/TestReplService（-T 1C flaky，单跑 35 绿）、NodeTscBridgeTest（Node 环境依赖）、JSONPointTest_0/1（遗留坏测试：JUnit4 setUp 在 JUnit5 永不执行，仅特定构建路径暴露）、ReproG3PerWindowStateLeakOnDiscard（-T 1C 计时 flaky，单跑 1968 绿）、nop-stream-connector-jdbc insert-no-debezium execution（plan 2413 回归钉被 JUnit platform 严格发现模式构造性打红——预存缺陷，jar 已装、模块跳过）

Follow-up:

- TestLeaseLifecycle（xdbg plan 2469）与 insert-no-debezium pin（plan 2413）均为其他工作流预存缺陷，已如实上报
- 竖排 surrogate pair（watch-only）

## R1/R2 审查修订记录

2026-10-19 R1（独立子代理 agent_aee18764）：1 Blocker + 3 Major + 8 Minor 全部吸收——F1 Temporal 机制重写为 formatValue 类型分派；F2 串行日期经 getJavaDate 转换 + 回归钉测试；F3 废弃共享 helper 改 parse 包新建 SpreadsheetML 映射（sz pt 语义显式化）；F4 rotate 按 ECMA 裁定 + xdef 注释修正（含受阻回退态）；F5-F12 行号/独立缓冲/字体回退/静态工具边界/renderText 排除/恒真条件简化/demo 回归命令/人工抽查与 scan-hollow 面已分别并入正文。

2026-10-19 R2（独立子代理 agent_180af990，Blocker=0）：R1-F1~F4 消解性复核全部通过（formatValue instanceof 分派经代码证据判定成立；ECMA 断言自洽；SpreadsheetML 流式设计与既有状态机正交；xdef 再生链路存在）。M1 富文本×rotate 组合条件矛盾——修订为富文本优先（rotate 忽略，偏差登记 D12）；M2 run 颜色管道断层——修订为 run 渲染记录含颜色（getFontColor 回退单元格色，theme 色同回退）+ 双 run 异色断言；m1 特判 Format 兜底分支保既有行为；m2 再生链路写明模块路径与 -am 坑；m3 scan-hollow 两模块统一 severity；m4 theme 色回退登记 D12。

## 执行偏差

- 测试 Workbook 的 defaultStyle=styles.get(0)（平台既有语义）；PDFTextStripper 匿名覆写不调 super 会吞文本收集；nop-report-pdf 测试类路径解析 m2 已装 nop-excel jar（新增 API 后须 install）。详见 Phase 1/2 偏差记录。xdef 再生链路畅通，drift 未修回退态未触发。
