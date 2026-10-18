# 2467 WI4 页眉页脚占位符与作用域

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI4，deps WI0 已完成）
> Related: `ai-dev/plans/2464-wi1-print-titles.md`、`ai-dev/design/nop-report/pdf-pagination.md`

## Purpose

页眉页脚从字面文本升级为 Excel 语义：&-占位符求值（&P/&N/&D/&T/&A/&F/&Z）、每工作表 vs 全局页码作用域、首页/奇偶页不同、字体样式保真；并修复 xlsx 解析路径丢弃 headerFooter 的缺陷。

## Current Baseline

2026-10-19 逐项核对 live repo：

- **模型**：`ExcelHeaderFooter`（workbook.xdef 内联 xdef:name）只有 left/center/right + 对应 Expr + style；`ExcelPageSetup` 只有 header/footer 两个引用，**无 firstHeader/firstFooter/evenHeader/evenFooter、无 differentFirst/differentOddEven**。
- **解析缺陷**：`SheetNodeHandler` 解析 oddHeader/evenHeader/firstHeader/oddFooter/evenFooter/firstFooter 元素（:201-205,446-451）并回调 `output.headerFooter(text, isHeader, tagName)`，但 **`SimpleSheetContentsHandler` 未覆写该回调（default no-op）——xlsx 模板的页眉页脚在主解析路径被整段丢弃**。实证：`nop-report/nop-report-pdf/src/test/resources/_vfs/test/test-pdf.xpt.xlsx` 的 sheet1.xml 含 `<headerFooter><oddFooter>&C&"SimSun,Plain"&9 第1页/共3页</oddFooter></headerFooter>`（含 Excel 字体代码 &"font"、字号代码 &9、位置代码 &C），解析后不进入模型。`headerFooter` 元素上的 differentFirst/differentOddEven 属性亦未解析。
- **渲染**：`PdfSheetRenderer.renderHeaderFooter`（:484-508）每页在内容渲染时绘制 header（打印区上方）/footer（下方）；`renderHeaderFooterContent`（:510-530）绘制 left/center/right 三段字面文本，**样式已生效**（renderText → hf.getStyle() → getFont/getFontSize/drawText）。
- **&N 总页数障碍**：HF 在每页内容渲染时绘制，总页数未知——含 &N 的 HF 需延迟到全部页渲染完成后绘制（与 `PdfRenderer.drawDefaultPageNumbers` 同机制：`PDPageContentStream.APPEND` + `pagesWithFooter` 集合）。
- **默认页码**：`drawDefaultPageNumbers`（PdfRenderer:148-186）全局页码（"第 x / y 页"），仅对未显式配置页脚的页；无作用域配置（`nop.report.pdf.page-number-scope` 不存在）。
- **Excel & 语法对版基准**：`&L/&C/&R` 位置分段；`&P` 页码、`&N` 总页、`&D` 日期、`&T` 时间、`&A` 工作表名、`&F` 文件名、`&Z` 路径、`&&` 转义、`&"font"` 字体、`&nn` 字号、`&B` 粗体、`&G` 图片（WI5）。
- 测试基座：WI1-WI3 的编程模板模式 + WI0 verify 工具。

## Goals

- 模型支持六段（odd/first/even × header/footer）+ differentFirst/differentOddEven 开关；xlsx 解析路径把 headerFooter 完整装入模型（含 &L/&C/&R 位置分段拆分）。
- Excel &-语法解析器：位置分段 + 字体/字号段 + 值占位符提取（&G 图片段解析留 WI5，本计划解析为占位段但不渲染图片）。
- 延迟绘制管线：含占位符的 HF 在全部页渲染完成后求值绘制（&N 可得）；无占位符的 HF 维持现路径即时绘制（行为不变）。
- 页码作用域配置 `nop.report.pdf.page-number-scope`（global 默认/sheet）作用于 &P/&N；&A/&F/&Z 求值（&F/&Z 取模板资源名，缺省空串）。
- 首页/奇偶页不同选择逻辑（differentFirst/differentOddEven + 变体缺省回退 odd）。
- 占位符求值单测 + 变体选择单测 + 多页渲染断言（含双 sheet 作用域差异）+ golden。

## Non-Goals

- &G 图片渲染、多字体混排精修、分区对齐/越界裁剪保真（WI5——本计划字体/字号段渲染仅做单段字体切换的基础支持，图片段显式跳过）。
- HF Expr 变体（leftExpr 等已存在，语义不变不动）。
- HTML/XLSX 渲染器的 HF 渲染语义。
- xlsx 写回 headerFooter（Follow-up 同前）。

## Scope

### In Scope

- `workbook.xdef` page-setup：新增 firstHeader/firstFooter/evenHeader/evenFooter 引用 + differentFirst/differentOddEven 属性 → 再生 `_ExcelPageSetup`。
- `SimpleSheetContentsHandler.headerFooter` 覆写：&L/&C/&R 分段解析（新 `ExcelHeaderFooterCodes` 解析器，nop-ooxml-xlsx parse 包）→ ExcelHeaderFooter 装入 pageSetup 六段 + different 开关（headerFooter 元素属性解析）。
- nop-report-pdf：`HeaderFooterEvaluator`（占位符求值，含 scope）+ `ReportPdfConfigs.CFG_PDF_PAGE_NUMBER_SCOPE` + `PdfRenderer` 延迟绘制任务列表 + `PdfSheetRenderer` 变体选择与延迟/即时分流。
- 测试：语法解析、变体选择、占位符求值（含 scope 双 sheet）、渲染断言 + golden；解析回归（test-pdf.xpt.xlsx 的 oddFooter 进入模型）。
- `pdf-pagination.md` D7 裁定；roadmap/日志收口。

### Out Of Scope

- WI5/WI18 范围项（见 Non-Goals）。
- docs-for-ai 能力矩阵（WI18）。

## Execution Plan

### Phase 1 - 模型与解析链

Status: completed
Targets: `nop-kernel/nop-xdefs/.../page-setup.xdef`、`workbook.xdef`、`nop-format/nop-ooxml/nop-ooxml-xlsx`

- Item Types: `Feature | Fix`

- [x] **Fix**：`SimpleSheetContentsHandler` 覆写 `headerFooter(text, isHeader, tagName)`——用 `ExcelHeaderFooterCodes` 解析器把 &L/&C/&R 分段装入 ExcelHeaderFooter，按 tagName（odd/first/even × Header/Footer）写入 pageSetup 六段（pageSetup 缺失则创建）；odd 为基准段。
- [x] `ExcelHeaderFooterCodes`（nop-ooxml-xlsx parse 包）：Excel &-语法解析——位置分段（缺省段归属：无位置代码的文本归 center）、`&&` 转义、字体/字号段识别（`&"name"`/`&nn`）。**存储裁定（Major-2）**：模型 `left/center/right` String **保留原始 &-代码不剥离**（保真无损、模型不膨胀；WI5 富文本再评估结构化）；渲染端复用本解析器做段级字体/字号切换。同段多字体代码取首个字体（WI5 精修多段混排）。值占位符原样保留文本（渲染期求值）。单测覆盖位置分段/转义/字体段/混合形态（对版：`&C&"SimSun,Plain"&9 第1页/共3页` → center 段保留原始代码、位置归属 center）。
- [x] `page-setup.xdef`：+`differentFirst="boolean"`、`differentOddEven="boolean"` 属性 + firstHeader/firstFooter/evenHeader/evenFooter 引用（`xdef:ref="ExcelHeaderFooter"`，内联类型可被 ref 有先例 footer）；install nop-xdefs → 再生 nop-excel。注意：differentFirst/differentOddEven 是 OOXML headerFooter 元素的属性（SAX 解析，不经任何 xdef），映射到 pageSetup 模型属模型级映射。
- [x] `SheetNodeHandler`：解析 `<headerFooter differentFirst differentOddEven>` 容器属性，经新增 default 回调（如 `headerFooterFlags(boolean differentFirst, boolean differentOddEven)`）发出（现有回调签名不带容器属性；XlsxToRecordOutput 未覆写不受破坏）；`SimpleSheetContentsHandler` 写入模型。
- [x] 解析回归测试：test-pdf.xpt.xlsx 的 oddFooter 装入模型（center 段非空 + 字体段信息）；六元素 + 双开关组合单测。

Exit Criteria:

- [x] 再生 diff 仅新增六段/两开关，无手改生成物
- [x] 解析测试全绿：test-pdf.xpt.xlsx oddFooter 进入模型（缺陷转绿）、六元素+开关组合正确、&-语法分段/转义/字体段正确
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-xlsx,nop-format/nop-excel -am` 全绿
- [x] `No owner-doc update required`（Phase 3 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 占位符求值、作用域与延迟绘制

Status: completed
Targets: `nop-report/nop-report-pdf`

- Item Types: `Feature`

- [x] `HeaderFooterEvaluator`（nop-report-pdf utils）：对段文本求值 `&P/&N/&D/&T/&A/&F/&Z`（`&&`→`&`）；&P/&N 受 scope 影响（global=全文档序号/总页数，sheet=当前 sheet 内序号/该 sheet 页数——需 PdfRenderer 按 sheet 记录页区间）；&D/&T 用渲染时刻日期时间（格式裁定记 D7）；&A=sheet 名；&F/&Z=模板资源名/路径（workbook location，缺省空串）；未知/未支持代码（&G 等）原样保留或显式跳过（裁定记 D7）。
- [x] `ReportPdfConfigs.CFG_PDF_PAGE_NUMBER_SCOPE`（String，默认 "global"，非法值报错）。
- [x] 延迟绘制：`PdfRenderer` 新增延迟 HF 任务列表（page index、分区矩形、段列表、样式）；`PdfSheetRenderer.renderHeaderFooter` 分流——**文本含任何 &-代码（含仅字体代码，如 `&"SimSun,Plain"&9 第1页`）→ 延迟求值绘制（字面打印 & 代码是缺陷形态）；纯字面文本 → 即时绘制（现状不变）**；`PdfReportRenderer` 在 drawDefaultPageNumbers 前统一求值绘制；`markFooterDrawn` 语义不变（延迟页脚的页同样标记，避免叠加默认页码）。
- [x] 变体选择：每页按 differentFirst/differentOddEven 选 first/even/odd 段（变体缺失回退 odd；odd 也缺失 → 该页无页眉/脚，默认页码抑制语义与 Excel 空白页脚一致——记 D7）；选择逻辑独立方法 + 单测。
- [x] 渲染测试：多页 &P/&N 全局与 sheet 两种 scope 的页码序列（双 sheet：sheet1 3 页 + sheet2 2 页，global=&P 1..5、sheet=&P 1..3/1..2；scope 配置覆盖用 `AppConfig.getConfigProvider().updateConfigValue` + try/finally 恢复——repo 先例 TestFluxYamlPages）；首页不同（firstFooter 只出现在页 1）；奇偶不同（even 页脚出现在偶数页）；&A 渲染存在性；&D/&T 用固定格式（yyyy/M/d、HH:mm:ss）regex 断言避免跨午夜 flake；样式保真（字体段渲染后文本尺寸断言）；golden。

Exit Criteria:

- [x] 渲染测试全绿且覆盖 scope 双 sheet、first/even 变体、占位符求值
- [x] **接线验证**：延迟绘制任务真实求值绘制（&N 在多页文档渲染正确，证明总页数延迟可得）
- [x] **视觉优先红线**：golden + 人工抽查记录
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 设计裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-pagination.md 新增 D7：&-语法解析归属（nop-ooxml-xlsx 存模型、渲染期求值）、延迟绘制机制、scope 语义、变体回退规则、&D/&T 格式、&F/&Z 取值、未知代码策略、Excel fit 忽略 breaks 等既有裁定汇集。
- [x] roadmap WI4 勾选；check-doc-links/check-plan-checklist/scan-hollow 全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI4 勾选
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败恢复遍证据）

## Closure Gates

- [x] Phase 1–3 全部 Exit Criteria 勾选
- [x] headerFooter 解析缺陷修复有回归测试钉住（test-pdf.xpt.xlsx）
- [x] 占位符求值/作用域/变体有 focused 测试证明
- [x] 受影响 owner docs 已同步（pdf-pagination.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2467-wi4-header-footer.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- xlsx 写回 headerFooter 六段与开关。
- &G 图片渲染（WI5 正主）。

## Closure

Status Note: WI4 三 Phase 完成：六段模型 + differentFirst/differentOddEven 开关（再生 _ExcelPageSetup）、&-语法解析器（ExcelHeaderFooterCodes）、xlsx 模板 headerFooter 丢弃缺陷修复（test-pdf.xpt.xlsx 回归钉）、占位符求值（&P/&N/&D/&T/&A/&F/&Z + scope global/sheet + 非法值 fail-loud）、延迟绘制管线（&N 可得 + 字体段 run 渲染）、首页/奇偶变体回退。视觉验收人工抽查 page-numbers-page-2.png（页脚 "2 / 3" 求值正确）。独立子 agent closure audit 见下方 Evidence。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_1fc031fd-b419-476e-a475-e5b8aae3debb）
- Audit Session: agent_1fc031fd-b419-476e-a475-e5b8aae3debb（2026-10-19）
- Evidence:
  - Phase 1：再生 diff +176/-0；test-pdf.xpt.xlsx oddFooter 装模回归（center 逐字符一致）；&-语法 7 测试；三模块 -am 全绿（审计实跑）
  - Phase 2：渲染 9/9（scope 双 sheet 3+2、first/even、&P/&N/&A/&D/&T regex、字体代码不字面、无效 scope fail-loud、golden）；接线验证（page2 页脚 "2/3" 证明 &N 延迟可得）；视觉红线（golden 5 张 + 人工抽查）
  - Phase 3：D7 与实现一致；roadmap WI4 勾选
  - 行为链：解析路由（odd/even/first × header/footer）/语法解析器/延迟绘制（scope + ContentStream 关闭 + L/C/R 定位 + markFooterDrawn 抑制默认页码）/变体优先序/配置 fail-loud 全 PASS
  - 审计 Major-1（已处置）：generator 分支页区间只记一个 sheet——markSheetRangeStart/End 移入 renderSheet（try/finally 配对，两分支统一按 sheet 粒度记录），编译+HF 测试复跑绿
  - 审计 Major-2（已处置）：补六元素+双开关 SAX 链组合测试 testSixElementsAndFlagsViaSaxChain（&amp; 转义形态，6 段路由 + 双开关断言）
  - 审计 Minor-1（已处置）：pdf-pagination.md"已知限制"两条失真 bullets（每 sheet 独立页码/占位符字面）已更新为现状
  - 审计 Minor-2（已处置）：nop-cli-errors.i18n.yaml 登记 ERR_PDF_INVALID_PAGE_NUMBER_SCOPE
  - 工具：check-doc-links --strict exit 0（审计实跑）；scan-hollow nop-report-pdf exit 0（审计实跑）；check-error-param-consistency 本轮 exit 1——4 个 nop-metadata variable-form 预存发现（本任务未触及 nop-metadata，i18n 增删对照验证非本改动引起；摘要 Missing=0/Unresolved=0），已记录
- Deferred 项分类检查：Follow-ups（xlsx 写回、&G/WI5）确未实现且登记诚实，无 in-scope live defect 被降级

Follow-up:

- xlsx 写回 headerFooter 六段与开关
- &G 图片渲染与多字体混排精修（WI5 正主）

### 实现期关键偏差记录

- 分流条件按审查 Major-3 落实为"含任何 &-代码 → 延迟"（仅字体代码的文本不再字面打印）。
- 字体段存储按审查 Major-2 裁定为模型 String 保留原始 &-代码，渲染端 HeaderFooterRuns 切 run（同段多字体取首个）。
- 调试教训：延迟绘制初版未关闭 PDPageContentStream 导致 save 抛 "open stream writer"（try-with-resources 修复）；surefire fork 的 CWD 是模块目录，文件调试须确保父目录存在。
