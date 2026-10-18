# 2464 WI1 模板级分页声明（Print_Titles）

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI1，deps WI0 已完成）
> Related: `ai-dev/plans/2463-wi0-pdf-visual-acceptance-toolchain.md`（视觉验收工具）、`ai-dev/design/nop-report/pdf-pagination.md`（分页 D1–D4）

## Purpose

使报表模板声明的重复标题行/列（Excel Print_Titles definedNames 语义）在 PDF 分页导出生效，并优先于全局配置缺省（`nop.report.pdf.repeat-header-rows`/`repeat-key-columns`），使模板声明与 Excel 打印语义一致。

## Current Baseline

2026-10-19 逐项核对 live repo：

- 渲染端重复机制只读全局配置：`nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/renderer/PdfSheetRenderer.java:91-101` 的 `repeatHeaderRows`/`repeatKeyColumns` 仅读 `ReportPdfConfigs.CFG_PDF_REPEAT_HEADER_ROWS`（默认 1）/`CFG_PDF_REPEAT_KEY_COLUMNS`（默认 1），不读模板。
- 模型无承载字段：`ExcelPageSetup` 由 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/excel/page-setup.xdef` 经 `nop-format/nop-excel/precompile/gen-excel-xdsl.xgen` 生成（`_ExcelPageSetup.java` 为生成物，禁止手改）；现无 repeat 类字段。
- 解析器不读 definedNames：`nop-format/nop-ooxml/nop-ooxml-xlsx` 全模块无 definedName 处理；`WorkbookPart`（`io/nop/ooxml/xlsx/model/WorkbookPart.java`）持有 workbook.xml 的 XNode（`getNode()`），可直接读 `definedNames/definedName`。
- 解析入口：`ExcelWorkbookParser.parseSheet(workbook, sheetRef, workbookFile)` 逐 sheet 调用；`sheetRef` 在 `workbookFile.getSheets()` 中的下标即 OOXML `localSheetId` 语义；sheet 内容经 `SimpleSheetContentsHandler.pageSetup` → `sheet.setPageSetup`。
- 模板链路：报表模板经 `ExcelWorkbookParser` 加载（`nop-report-core/.../util/ExcelReportHelper.java:92`）；`ExpandedSheet.java:49` 复制源 sheet 的 pageSetup，模型字段可自动流至渲染端。
- 渲染测试模式：编程构建 `ExcelWorkbook` → `new PdfReportRenderer(workbook, null).generateToStream(bos, null)` → PDF bytes（`TestPdfSheetRenderFixes.java:113-117,131-136`）。
- WI0 工具就位：`io.nop.pdf.verify`（nop-pdf main scope）；nop-report 侧尚未声明 test-scope 依赖。
- 错误码惯例：`XlsxErrors`/`ReportPdfErrors` 用 `ErrorCode.define("nop.err...", "desc", ARG...)`；`node ai-dev/tools/check-error-param-consistency.mjs` 当前 exit 0。

## Goals

- `ExcelPageSetup` 模型承载 `repeatTitleRows`/`repeatTitleCols`（归一化 1-based 闭区间字符串，如 `"1:2"`/`"A:B"`）。
- nop-ooxml-xlsx 解析 workbook.xml `definedNames` 中 `_xlnm.Print_Titles`（localSheetId 匹配）→ 写入该 sheet 的 pageSetup；非法形态显式报错。
- `PdfSheetRenderer` 优先消费模型声明（前缀形态），缺失时回退全局配置；非前缀形态渲染端 fail-loud。
- 解析单测 + PDF 续页/列拆分页断言 + WI0 视觉验收（golden + 区域采样）。

## Non-Goals

- Print_Titles 写回 xlsx 输出（`ExcelWriteSupport` 工作簿级 definedNames 输出）：模板→PDF 链路不需要；记入 Non-Blocking Follow-ups。
- 非前缀 Print_Titles（如 `$3:$4`）的 PDF 渲染：解析层保真存储，渲染层显式报错（不静默降级）；如后续需要，属渲染器拆分点控制的独立增强。
- fit 缩放（WI2）、分页边界健壮性（WI3）、页眉页脚（WI4）。
- `_gen` 生成物手改（一律经 xdef + precompile xgen 重新生成）。

## Scope

### In Scope

- `page-setup.xdef` 增加两个可选 string 属性 + 重编译再生 `_ExcelPageSetup`。
- `ExcelWorkbookParser` definedNames 解析 + `XlsxErrors` 新错误码。
- `ExcelPrintHelper`（nop-excel print 包）title range 解析/归一化 helper。
- `PdfSheetRenderer` 模型优先消费 + `ReportPdfErrors` 新错误码。
- nop-report-pdf pom 新增 nop-pdf test-scope 依赖（WI0 follow-up 接线）。
- 解析单测（新增极小 .xlsx fixture）+ 渲染断言测试（含视觉验收）。
- `ai-dev/design/nop-report/pdf-pagination.md` 新增 Print_Titles 语义裁定；roadmap/日志收口。

### Out Of Scope

- docs-for-ai 用户文档的 PDF 能力矩阵（WI18 统一收口）。
- xlsx 写回 definedNames。

## Execution Plan

### Phase 1 - 模型字段与解析器

Status: completed
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/excel/page-setup.xdef`、`nop-format/nop-excel`、`nop-format/nop-ooxml/nop-ooxml-xlsx`

- Item Types: `Feature`

- [x] `page-setup.xdef` 增加可选属性 `repeatTitleRows="string"`、`repeatTitleCols="string"`；重新生成 `_ExcelPageSetup`（重编译 nop-excel，验证 `_gen` 文件按 xdef 再生，不手改生成物）。
- [x] `ExcelPrintHelper` 增加静态 helper：`parseTitleRange(String)` → 1-based 闭区间 `int[]{first,last}`（接受 `"1:2"`/`"A:B"`，拒绝空段/倒序/非数字母形态，English 报错）；`isPrefixRange(int[])` 判定 first==1。在 nop-excel `src/test` 新增 helper 单测（合法/非法形态全覆盖）。
- [x] `ExcelWorkbookParser.parseSheet`：从 `workbookFile.getNode()` 读 `definedNames/definedName`；`name="_xlnm.Print_Titles"` 且 `localSheetId` == `workbookFile.getSheets().indexOf(sheetRef)`（完整列表下标，**不得用已解析 sheet 计数**——sheetFilter 过滤/空名返回 null 不影响下标）时，解析 value（逗号分隔多段；每段剥 sheet 名前缀（含单引号形式）与 `$`；纯数字段为行段、纯字母段为列段；同型多段报错；单元格区域形态如 `$A$1:$B$2` 报错）；归一化写入 pageSetup（不存在则创建）。
- [x] `XlsxErrors` 新增 `ERR_XLSX_INVALID_PRINT_TITLES`（desc 中文 + ARG 占位一致，沿同文件先例；注意 check-error-param-consistency.mjs 不扫描 XlsxErrors/ReportPdfErrors，desc/ARG 对齐靠 define() 惯例自证）。
- [x] 解析单测：新增极小 .xlsx fixture（含 Print_Titles definedNames，行/列/行+列/引号 sheet 名四形态）+ 无 definedNames 回归 + 非法形态断言报错；断言 `ExcelSheet.getPageSetup().getRepeatTitleRows/Cols()` 归一化值。

Exit Criteria:

- [x] `_ExcelPageSetup` 由 xdef 再生（diff 仅新增两字段），无手改生成物
- [x] 解析单测全绿：四形态归一化正确、非法形态抛 `NopException(ERR_XLSX_INVALID_PRINT_TITLES)`、无 definedNames 模板行为不变
- [x] **无静默跳过**：非法 Print_Titles 形态解析期显式报错，不静默忽略
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-xlsx -am` 全绿
- [x] `No owner-doc update required`（本 Phase 纯解析与模型字段；Print_Titles 语义裁定统一在 Phase 3 落 pdf-pagination.md）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 渲染消费与视觉验收

Status: completed
Targets: `nop-report/nop-report-pdf`

- Item Types: `Feature | Proof`

- [x] `PdfSheetRenderer`：`renderSheet(IExcelSheet)` 入口处一次性解析 titles（`resolveRepeatTitles`：pageSetup 字段存在→`ExcelPrintHelper.parseTitleRange` 解析，**非前缀形态无条件抛 `NopException(ERR_PDF_UNSUPPORTED_PRINT_TITLES)`，与是否实际分页无关**（裁定：非前缀是模板级不支持形态，导出即报错，不在单页/fit 分支静默通过）；字段缺失→回退全局配置），解析结果以参数贯穿拆分预留（:65-66）、dx/dy 占位（:82/:87）、region 渲染（:115-116）全部消费点；`ReportPdfErrors` 新码 desc 沿同文件中文先例。
- [x] nop-report-pdf pom 增加 `nop-pdf` test-scope 依赖（WI0 verify 工具接入）。
- [x] 渲染测试（nop-report-pdf test，编程模板：2 标题行 + 40 数据行跨页 + 8 关键列场景跨列页）：(a) 续页文本断言：每页均含标题行文本（PDFTextStripper）；(b) 模型优先：全局配置设 0 时模型声明仍生效；全局缺省在无模型声明时保持现行为；(c) 非前缀 titles 抛 ERR_PDF_UNSUPPORTED_PRINT_TITLES；(d) 视觉验收：PDF→PNG（verify 工具 110dpi）+ golden 生命周期 + 续页标题行区域采样断言。

Exit Criteria:

- [x] 渲染测试全绿且覆盖 (a)–(d) 四类断言
- [x] **视觉优先红线**：`_tmp/` 下有实际 PDF + 逐页 PNG；golden diff 与区域采样断言通过；扰动探针验证断言会红
- [x] **接线验证**：模型声明经 `ExpandedSheet` pageSetup 复制链路在渲染端真实消费（测试覆盖模型优先于配置的行为差异，非仅字段存在）
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 设计裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、`ai-dev/backlog/nop-pdf-report-export-roadmap.md`、`ai-dev/logs/`

- Item Types: `Decision | Proof`

- [x] `pdf-pagination.md` 新增 Print_Titles 裁定：模型字段语义（归一化字符串）、解析归一化规则、渲染前缀限制与 fail-loud、模型优先于全局配置的优先级、写回 xlsx 不做。
- [x] roadmap WI1 勾选；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

- [x] 设计文档含上述裁定且与实现一致
- [x] roadmap Work Item Status 中 WI1 已勾选
- [x] `ai-dev/logs/2026/10-19.md` 已记录完成状态
- [x] check-doc-links 退出码 0

## Closure Gates

- [x] Phase 1–3 全部 Exit Criteria 勾选
- [x] 模板声明优先于全局配置的行为有 focused 测试证明（非仅字段存在）
- [x] `./mvnw test -pl nop-format/nop-excel,nop-format/nop-ooxml/nop-ooxml-xlsx,nop-report/nop-report-pdf -am` 全绿（触及多模块，收口前跑 `./mvnw clean install -T 1C`——全量构建结果见下方补记）
- [x] 受影响 owner docs 已同步（`pdf-pagination.md`；docs-for-ai 能力矩阵归 WI18）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2464-wi1-print-titles.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-error-param-consistency.mjs` 退出码 0（注：该脚本不扫描 XlsxErrors/ReportPdfErrors，此项为全局回归确认；新码 desc/ARG 对齐由代码审查保证）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- xlsx 写回路径（ExcelWriteSupport 工作簿级 definedNames 输出）不实现；模板→PDF 链路无依赖。WI18 收口时复核是否立项。
- 非前缀 Print_Titles 的渲染支持（需拆分点控制增强），出现真实需求时另立 plan。

## Closure

Status Note: WI1 三 Phase 完成：page-setup.xdef 模型字段（再生 _ExcelPageSetup，无手改）、ExcelWorkbookParser Print_Titles 解析（六类非法形态 fail-loud）、PdfSheetRenderer 模型优先消费（非前缀渲染入口即抛、回退配置保持旧行为）、视觉验收（golden+区域采样+人工抽查 page-2.png 续页标题重复正确）。独立子 agent closure audit PASS（无 Blocker/Major）。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_ac7f17a1-aeb4-437a-906b-ec985a9d667c）
- Audit Session: agent_ac7f17a1-aeb4-437a-906b-ec985a9d667c（2026-10-19）
- Evidence:
  - Phase 1：xdef 再生 diff 仅生成器风格新增两字段；解析单测 4+9 全绿（calc.xlsx 端到端 "9:9"）；六类非法形态显式 throw（ExcelWorkbookParser.java:145-236 逐条核对）
  - Phase 2：渲染测试 4/4 绿；(b) 模型优先为真实证明（config 默认=1，模型不生效则 TITLE-TWO 续页缺席必红）；接线链 ExpandedSheet.java:49 复制 pageSetup 实证；非前缀在 renderSheet 入口即抛（单页/fit 分支同样覆盖）；golden 接受 PASS/BASELINE_CREATED、拒绝 FAIL/UPDATED
  - Phase 3：pdf-pagination.md D1/D2 与实现逐条一致；roadmap WI1 勾选；日志含偏差与验证
  - Anti-Hollow 四链：解析链/渲染链（grep 无 repeatHeaderRows 残留调用点，回退 clamp 与旧逻辑逐字等价）/模型流/xdef→_gen 全 PASS
  - 工具（审计实跑）：check-doc-links --strict exit 0；scan-hollow --module nop-report-pdf --severity high exit 0；check-error-param-consistency exit 0；三模块全量测试 BUILD SUCCESS
  - 全量构建（Minor-5 补跑）：`./mvnw clean install -T 1C` 首遍全部触及模块 SUCCESS，唯 nop-stream-runtime TestStreamConnectorRegistryE2E 因 -T 1C 资源竞争 60s 超时（与本计划无关的时序 flaky，单测重跑 1.3s 通过）；`./mvnw install -T 1C -rf :nop-stream-runtime` 恢复遍完成剩余模块
  - Deferred 项分类检查：Follow-ups（xlsx 写回、非前缀渲染）为真新增强非 in-scope defect；Deferred=无 成立
  - 审计附带 Minor（均 watch-only 不阻塞）：Minor-1 fixture 复用 calc.xlsx 而非新建（覆盖等价，已在此记录偏差）；Minor-4 非数字 localSheetId 静默跳过（schema 非法输入可接受）

Follow-up:

- xlsx 写回路径（工作簿级 definedNames 序列化）——出现真实需求时立项
- 非前缀 Print_Titles 渲染（需拆分点控制增强）——出现真实需求时另立 plan
