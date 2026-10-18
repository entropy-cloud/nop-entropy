# 2465 WI2 fit 缩放语义与页幅约束

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI2，deps WI1 已完成）
> Related: `ai-dev/plans/2464-wi1-print-titles.md`、`ai-dev/design/nop-report/pdf-pagination.md`

## Purpose

将 PDF 导出的 fit 缩放对齐 Excel 语义：`fitToWidth`×`fitToHeight` 为"N 页宽 × M 页高"目标页数（非布尔），自动缩小到目标页数并设最小可读字号阈值（低于阈值告警并收敛而非无限缩小）；修正 scale 百分比与分页几何的交互（当前按未缩放打印区拆页导致欠填）。

## Current Baseline

2026-10-19 逐项核对 live repo：

- 模型：`page-setup.xdef` 的 `fitToWidth`/`fitToHeight` 为 **boolean**；`ExcelPageSetup.isFitToWidthAndHeight()`（两者皆 true 判定）仅被 PdfSheetRenderer 消费。
- 渲染：`PdfSheetRenderer.renderSheet(IExcelSheet)` 中两者皆 true → `renderSheet(..., null)` 单页整表（无分页）；单一 true → `scale = printArea/table` 单方向缩放到一页。`scale` 属性（百分比）→ 固定缩放。
- **分页几何缺陷**：`TableSplitHelper.splitTable` 恒以**未缩放** printArea 计算页容量；`scale`<1 或 fit 缩放后每页实际可容纳 table 坐标内容应为 `printArea/scale`，当前导致每页欠填（fit 单方向 true 时同样）。
- 解析：`SheetNodeHandler` 的 pageSetup 分支（:313-321）仅解析 paperSize/orientation；`scale`/`fitToWidth`/`fitToHeight`/`sheetPr>pageSetUpPr@fitToPage` 均不解析，模型字段无 OOXML 生产者。
- 使用面：仓库内 grep `fitToWidth` 仅命中 xdef 与 PdfSheetRenderer——无任何模板/代码设置，boolean→int 语义切换无仓内破坏面。
- OOXML 语义（对版基准）：`fitToWidth`/`fitToHeight` 为**页数整数**（默认 1），仅当 `sheetPr>pageSetUpPr@fitToPage="1"` 时生效；`scale` 为百分比整数；fitToPage 与 scale 互斥（fit 优先）。
- WI1 已建立：renderSheet 入口一次性解析（`resolveRepeatTitles`）、参数贯穿模式；WI0 verify 工具在 nop-report-pdf test scope 可用。
- 配置惯例：`ReportPdfConfigs` 以 `varRef(s_loc, "nop.report.pdf.*", type, default)` 声明。

## Goals

- `fitToWidth`/`fitToHeight` 模型语义改为**目标页数 int**（0/缺省=该维不约束）；OOXML 解析补齐 `scale`/`fitToWidth`/`fitToHeight`（以 `fitToPage` 门控，保真 Excel 文件）。
- 渲染统一缩放管线：入口计算 effective scale（scale 属性为基础、fit 目标覆盖），分页容量 = `printArea/scale`（含重复块预留同除），单页快路径自然保留（缩放后 regions==1）。
- 最小可读字号阈值：新配置 `nop.report.pdf.min-font-size-pt`（默认 6pt）；缩放后表格最小字号低于阈值 → WARN 日志 + scale 收敛到 `threshold/minFont`（目标页数让位于可读性）。
- fit 缩放与重复块占位交互复验（fit 分页下续页标题仍重复）。
- 缩放用例 golden。

## Non-Goals

- 分页边界健壮性（超页行/合并单元格/手动分页符——WI3）。
- 页眉页脚（WI4）。
- xlsx 写回 pageSetup fit 属性（同 WI1 写回裁定，Follow-up）。
- 打印区域 print area definedNames（`_xlnm.Print_Area`，未在 WI2 范围）。

## Scope

### In Scope

- `page-setup.xdef` fitToWidth/fitToHeight boolean→int + `ExcelPageSetup` 语义 helper 适配（再生 `_ExcelPageSetup`）。
- `SheetNodeHandler` 解析 `scale`/`fitToWidth`/`fitToHeight`（pageSetUpPr@fitToPage 门控）。
- `PdfSheetRenderer` 统一缩放管线 + `ReportPdfConfigs` 新配置 + WARN 告警。
- 单测：解析（含 fitToPage 门控）、目标页数缩放、最小字号 clamp、scale 页容量修正、fit×重复块交互、golden 视觉验收。
- `ai-dev/design/nop-report/pdf-pagination.md` fit 语义裁定；roadmap/日志收口。

### Out Of Scope

- docs-for-ai 用户文档能力矩阵（WI18）。
- HTML/XLSX 渲染器的 fit 行为（共享模型字段变更它们自然可见，但渲染语义不改）。

## Execution Plan

### Phase 1 - 模型语义与解析

Status: completed
Targets: `nop-kernel/nop-xdefs/.../page-setup.xdef`、`nop-format/nop-excel`、`nop-format/nop-ooxml/nop-ooxml-xlsx`

- Item Types: `Feature`

- [x] xdef `fitToWidth`/`fitToHeight` boolean→int（注释：目标页数，0/缺省=不约束）；install nop-xdefs 后再生 `_ExcelPageSetup`（WI1 确认的再生链路）。
- [x] `ExcelPageSetup`：`isFitToWidthAndHeight()` 适配 int 语义（>0 判定）或以新 helper 替代并修正唯一消费点；保留方法签名兼容（检查调用面后裁定）。
- [x] `SheetNodeHandler`：解析 `pageSetup` 的 `scale`/`fitToWidth`/`fitToHeight`（int，缺省不设）；新增 `sheetPr>pageSetUpPr` 分支读取 `fitToPage` 并以 handler 状态字段跨元素门控（SAX 中 sheetPr 先于 pageSetup 出现）；**仅 fitToPage=true 时 fit 属性写入模型**（防真实 Excel 文件默认 fitToWidth="1" 误启用）。
- [x] 解析单测：程序化 sheet XML（fitToPage 开/关 × fit/scale 属性组合）断言模型值。

Exit Criteria:

- [x] `_ExcelPageSetup` 再生 diff 仅 fit 两字段类型变化，无手改生成物
- [x] 解析单测全绿：fitToPage 门控语义正确（关→fit 不生效，开→fit+scale 值就位）
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-xlsx,nop-format/nop-excel -am` 全绿
- [x] `No owner-doc update required`（Phase 3 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 统一缩放管线与页幅约束

Status: completed
Targets: `nop-report/nop-report-pdf`

- Item Types: `Feature | Proof`

- [x] **enableColumnPaging int 语义裁定**：现表达式 `!Boolean.FALSE.equals(getFitToWidth())` 在 int 再生后恒 true（静默退化）。新裁定：`fitToWidth==null||0`（宽度不约束）→ true；`fitToWidth=N>0` → 仍 true（列分页正是达成 N 页宽目标的机制），即恒为 true，删除该退化为恒真的表达式并以具名常量/注释固化语义。
- [x] `renderSheet(IExcelSheet)` 入口计算 effective scale：`scale` 属性为基础；fit 模式覆盖——`targetScale = min(约束维: (N*printW-(N-1)*repeatDx)/tableW, (M*printH-(M-1)*repeatDy)/tableH)`（repeat 项仅该维声明重复时计入）；单页快路径删除，统一走"缩放→分页"（缩放后 regions==1 自然单页）。
- [x] 最小可读字号：`ReportPdfConfigs` 新增 `nop.report.pdf.min-font-size-pt`（Float，默认 6）；扫描表格样式最小字号——口径必须与 `PdfStyleHelper.getFontSize` 唯一定版（其内联缺省 10 + `fontSize<=0→10` 回退；将该口径提取为共用静态方法，扫描与渲染同源）；`minFont*scale < threshold` → WARN（含目标/实际/阈值）+ scale=threshold/minFont 收敛。
- [x] 分页容量修正：splitTable 两遍调用的宽高均改 `printArea/scale`（重复块预留同除）；`applyPageSetup` 接收入口 scale 参数，删除内部重复计算。
- [x] 测试（TestPdfFitScaleRender）：(a) fit 2×1：宽表恰 2 页、两页横向铺满（区域采样/文本位置）、数据不丢（splitTable 贪心打包，测试用均匀列宽构造，注明不均匀列宽下目标页数为 best-effort）；(b) scale=50：页容量翻倍（同表页数少于 scale=100）；(c) 最小字号 clamp：极小纸张+阈值 → 行为效果断言页数超过目标页数（WARN 无日志捕获惯例，不做自动断言，人工观察）；（d) fit×重复块：fit 缩放分页下续页仍重复标题（WI1 断言复用）；(e) golden 视觉验收（fit 与 scale 用例）。

Exit Criteria:

- [x] 渲染测试全绿且覆盖 (a)–(e)
- [x] **视觉优先红线**：`_tmp/` 实际 PDF+PNG、golden 断言、人工抽查截图记录
- [x] **接线验证**：入口 scale 贯穿分页与绘制（测试 (b) 页容量差异证明 scale 真实作用于分页几何）
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 设计裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-pagination.md：fit 页数语义裁定（xdef 变更、fitToPage 门控、fit/scale 互斥优先级、最小字号收敛语义、repeat 项近似公式、旧 boolean 语义废弃说明）。
- [x] roadmap WI2 勾选；check-doc-links/check-plan-checklist/check-error-param-consistency/scan-hollow 全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI2 勾选
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败的恢复遍证据）

## Closure Gates

- [x] Phase 1–3 全部 Exit Criteria 勾选
- [x] fit 语义变更的行为有 focused 测试证明（页数目标、容量修正、clamp）
- [x] 受影响 owner docs 已同步（pdf-pagination.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2465-wi2-fit-scale.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- xlsx 写回 scale/fit 属性（同 WI1 写回裁定）。
- `_xlnm.Print_Area` definedNames 支持（打印区域模板声明）。

## Closure

Status Note: WI2 三 Phase 完成：fitToWidth/fitToHeight boolean→int 页数语义（再生 _ExcelPageSetup）+ SheetNodeHandler fitToPage 门控解析；渲染统一缩放管线（入口 effective scale 贯穿分页容量与绘制，fit 容量公式与两遍拆分预留语义一致：scale <= N*(print-repeat)/table，只缩小不放大，1e-9 浮点容差）；最小可读字号收敛（WARN + scale=threshold/minFont）；fit×重复块交互复验。视觉验收人工抽查 fit-2x1 两页（页 1 C0–C14 全行、页 2 重复关键列 C0 + C15–C29、页脚正确）。独立子 agent closure audit 见下。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_ff40660c-6eb3-429e-b8c9-e4c606135dde）
- Audit Session: agent_ff40660c-6eb3-429e-b8c9-e4c606135dde（2026-10-19）
- Evidence:
  - Phase 1：再生 diff 仅 Boolean→Integer（git 核对无手改）；fitToPage 门控 4 形态测试全绿
  - Phase 2：TestPdfFitScaleRender 5/5 实跑绿；接线验证 (b) 若 scale 不作用于分页 at100==at50 必红，实测 at50=2 且 at100>at50；回归绿（TestPdfPrintTitlesRender 4/4、TestPdfRenderDefects 12/12、TestPdfSheetRenderFixes 6/6）；golden PNG 时间戳与重建记录吻合
  - Phase 3：D5 与实现逐条一致（公式/门控/只缩小/列分页/配置默认值）；roadmap WI2 [x]；日志偏差记录与终态一致
  - 行为链：门控状态字段跨元素传递 PASS；容量公式推导与两遍拆分预留语义同口径 PASS；clamp 条件/收敛/口径同源 PASS；断言真实性（管线失效必红）PASS；旧恒真表达式已删（diff 证实）
  - 工具（审计实跑）：check-doc-links --strict exit 0；scan-hollow --module nop-report-pdf --severity high exit 0；check-error-param-consistency exit 0
  - 审计附带 Minor（不阻塞）：设计文档配置清单漏列 min-font-size-pt（已顺手补）；WARN 未做日志自动断言（仓库无捕获惯例，行为效果已由测试 (c) 证明）
- Deferred 项分类检查：Follow-ups（xlsx 写回、Print_Area）确未实现且登记诚实，无 in-scope live defect 被降级

Follow-up:

- xlsx 写回 scale/fit 属性（同 WI1 写回裁定）
- `_xlnm.Print_Area` definedNames 支持

### 实现期关键偏差记录

- 初版 fit 容量公式（N*printW-(N-1)*repeatDx）与两遍拆分"所有页统一预留"的行为不一致，导致 fit-N 多裂一页；改为 scale <= N*(print-repeat)/table 的口径统一公式（见 D5）。
- FIT_EPSILON 初版方向写反（放大 scale 反而缩小容量），修正为 scale*(1-1e-9)。
- golden 首轮基线由坏渲染创建，渲染修复后 diff 必红（工具按预期工作），按 D4 流程删除重建。
