# 2260 nop-report PDF 导出显示质量与自动分页完善

> Plan Status: completed
> Last Reviewed: 2026-09-05
> Source: `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md`（视觉审计报告，含 F1-F14 缺陷清单与根因候选）
> Related: `docs-for-ai/03-runbooks/generate-report.md`

## Purpose

把 nop-report 的 PDF 导出从"简单单页表格可用"提升到"生产级中文报表可用"：修复视觉审计发现的全部 P0/P1 缺陷（F1-F7）与 P2 缺陷（F8-F13），使复杂报表（列宽超页、行数超页、套打、无样式单元格）的 PDF 产出可读、可打印、可回归验证。

## Current Baseline

- PDF 渲染管线：`nop-report-pdf` 基于 PDFBox 3.0.3 直接渲染展开后的工作簿模型（非 xlsx 转换），与 HTML/XLSX 共享展开逻辑。
- 分页骨架已存在：`PdfSheetRenderer` 调用 `TableSplitHelper.splitTable` 按打印区宽高切分 region，每个 region 独立成页；`fitToWidth/fitToHeight` 缩放、水平/垂直居中、页眉页脚（模板显式配置时）已实现。
- 已有回归套件：`TestPdfSheetRenderFixes`（内容流断言）与 `TestPdfReportRenderer`，本计划的渲染断言在其上叠加，不另起炉灶。
- 审计工具已入库：`TestPdfExportAudit`（nop-report-demo）批量导出 20 个 demo 模板 + 合成 200 行表为 PDF/PNG，是本计划的验收基线。
- 视觉审计确认的缺陷（编号沿用审计报告，含基线修订）：
  - F1 CJK 字体缺失阻断导出（P0）——未修复。
  - F2 套打底图未按 print=false 排除——未修复；runbook `generate-report.md` 声称已排除，与 live code 不一致。
  - F3 列拆分页无关键列重复——未修复。
  - F4 续页无重复表头/无页码——未修复。
  - F5 wrapText 多行文本叠压——未修复（根因候选：PDType0Font capHeight=0 致 lineHeight=0）。
  - F6 分页边界行文字重影——未修复（根因候选在 region 切分/clip，与 Phase 4 分页改动有耦合）。
  - F7 无样式单元格 NPE——**基线修订：提交 e2b02af86e 已修复**（`getFontSize` 空值拆箱防护 + `getDefaultFont` 未初始化防护）。Phase 1 需以复现测试复验并钉住回归，不再要求红测试。
  - F8 defaultFont 不走 CJK 资源——未修复。
  - F9 Integer 值列未渲染——未修复，根因待确认。
  - F10 文本无单元格裁剪——未修复。
  - F11 图片无页面边界裁剪——未修复。
  - F12 字体无 bold/italic 变体——未修复；**基线修订：per-document 字体缓存已存在**（`PdfRenderer.fontCache` 自初始提交即有），不存在"逐次加载"问题；注意 `PDType0Font` 绑定 PDDocument，**严禁跨文档缓存字体实例**。
- runbook 与实现的 F2 不一致需在本计划中修正。

## Goals

- 仓库内外的中文报表模板不做任何手工配置即可导出 PDF（字体自动发现或内置回退；环境前提见 Phase 2 Decision）。
- 宽表、长表的分页产出可读：续页重复表头行、列拆分页重复关键列、无文字叠压/重影。
- 套打 PDF 严格遵循 print=false 排除背景图，与 XLSX/HTML 行为一致。
- 全部缺陷有回归测试锚定（渲染断言，不依赖人眼），`TestPdfExportAudit` 升级为带断言的端到端验收。

## Non-Goals

- 不重写 PDF 渲染器为其他方案（如 xlsx→PDF 转换、openpdf 等）。
- 不做 HTML/XLSX 渲染器的改动（除非共享模型需要新增 printTitles 字段）。
- 不做图表渲染、水印、加密、PDF/A 等导出增强。
- 不优化大数据量导出性能（534 页 28s 已可接受），仅要求不回归。

## Scope

### In Scope

- `nop-report/nop-report-pdf`（字体、文本渲染、分页、图片、样式渲染）
- `nop-format/nop-excel` 的 `ExcelSheet`/`ExcelPageSetup` 模型（如需承载重复标题行/列配置）
- `nop-kernel/nop-core` 的 `TableSplitHelper`（切分边界行为修复，如根因落在此）
- `docs-for-ai/03-runbooks/generate-report.md`（F2 文档不一致修正 + 新能力文档化）
- 审计测试 `TestPdfExportAudit` 升级与新增渲染断言测试

### Out Of Scope

- `nop-report-core` 展开引擎（ExpandedSheetGenerator 等）
- `nop-report-ext`（条码/二维码扩展）
- 报表设计器/UI

## Execution Plan

### Phase 1 - 根因确认与渲染断言基线

Status: completed
Targets: `nop-report/nop-report-demo/src/test`、`nop-report/nop-report-pdf/src/test`

- Item Types: `Proof | Fix`

- [x] 在既有 `TestPdfSheetRenderFixes`/`TestPdfReportRenderer` 基础上叠加断言夹具，不另起炉灶（新增 `TestPdfRenderDefects`，复用其 PDFStreamParser/夹具模式）
- [x] F5/F6/F9 复现测试（**修订：复现结果证伪了三者的原始缺陷解读**——F5 行距正常、F6 按绘制操作计数无重影、F9 Integer 正常渲染；真实机理为 F10 无裁剪溢出。三者转为出生即绿的回归钉，证伪结论已回写审计报告）
- [x] F7 复验：写无样式单元格的复现测试，验证提交 e2b02af86e 的修复在当前 HEAD 成立；该测试为出生即绿的回归钉（Proof），不得虚报为红测试
- [x] 为 F2（print=false）、F3（列拆分）、F4（续页表头）写基于 PDFBox 文本提取/页面内容（含 XObject 资源检查）的断言测试（红测试，修复后翻转）
- [x] 将 `TestPdfExportAudit` 的 `exportAll` 增加机器可读结果与失败清单断言（Phase 1 断言标准 = 注入 CJK 字体后全绿；Phase 2 落地后由 Phase 6 增加无注入用例）
- [x] 确认 F9 根因结论并回写审计报告（F14 颜色比对归 Phase 5，随套打一致性一并复核）

Exit Criteria:

- [x] （修订）F2/F3/F4 红测试失败形态明确（image present / key column 不重复 / 续页无表头）；F5/F6/F7/F9 为出生即绿回归钉，证伪与复验结论已回写审计报告
- [x] F2/F3/F4 断言可运行并钉住当前行为
- [x] F9 根因结论已回写 `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md`（F14 归 Phase 5）
- [x] `ai-dev/logs/` 对应日期条目已更新（随 Phase 提交统一写入）

### Phase 2 - 字体体系（F1/F8/F12 变体策略）

Status: completed
Targets: `nop-report/nop-report-pdf`（font 包及取字体路径）、`ai-dev/design/nop-report/`、`docs-for-ai/03-runbooks/generate-report.md`

- Item Types: `Fix | Decision`

- [x] Decision（写入 `ai-dev/design/nop-report/pdf-font-strategy.md`，目录不存在则创建）：①CJK 字体来源策略（配置目录 + 系统字体目录自动发现 / 内置小体积子集字体 / 两者结合），含拒绝项理由；②**环境前提裁定**——无系统 CJK 字体的环境（精简 CI 容器）下测试的通过标准：内置子集字体、或 CI 镜像安装 CJK 字体（如 fonts-wqy-microhei），前提必须写入 Exit Criteria 与 owner doc，禁止依赖"开发机恰好有字体"的隐式前提；③**回退与快败的精确边界**——字体名缺失/未知回退 base-14（保持 Arial 等常见字体模板现状可用），仅当字符在最终选中字体中无法编码时按字符回退 CJK 字体、无回退能力时报错；④bold/italic 呈现策略
- [x] 字体解析增加字形覆盖检查与按字符回退：base-14 字体无法编码的字符回退到 CJK 字体，而不是运行时崩溃
- [x] null/未知字体名的默认字体解析走已配置的默认字体资源（当前硬编码 Helvetica），彻底消除 F8
- [x] 字体加载失败时抛出带可操作指引的错误码（指明如何配置字体），错误码按仓库惯例在 i18n yaml 注册并通过 `check-error-param-consistency.mjs` 校验
- [x] 明确保持 per-document 字体复用现状（`PdfRenderer.fontCache`），**禁止跨文档缓存 `PDType0Font` 实例**（绑定 PDDocument，跨文档复用产出损坏 PDF）——以测试或代码注释钉住该约束
- [x] 新增/更新测试：按 Phase 2 Decision 裁定的环境前提下，中文模板 PDF 导出成功且文本可提取（`PDFTextStripper` 断言包含中文内容，防字形丢失）
- [x] Owner doc：`generate-report.md` 新增字体配置/自动发现说明（当前对字体零文档；字体配置是新的用户可见配置面）

Exit Criteria:

- [x] `TestPdfExportAudit` 在 Decision 裁定的环境前提下不注入 VFS 字体即 20 个模板全部导出成功
- [x] 中文文本经 `PDFTextStripper` 提取断言通过（防字形丢失）
- [x] 字体来源策略、环境前提、回退边界三个 Decision 已记录到 `ai-dev/design/nop-report/pdf-font-strategy.md`
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `docs-for-ai/03-runbooks/generate-report.md` 字体节已更新；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文本渲染正确性（F5/F6/F9/F10）

Status: completed
Targets: `nop-report/nop-report-pdf`（PdfStyleHelper/PdfTableRenderer 及 utils 包）

- Item Types: `Fix`

- [x] F5 复现证伪（行距正常）——无需修复行距；真实机理 F10 溢出由本 Phase 裁剪修复覆盖
- [x] F6 复现证伪（按 showText 操作计数每行只绘制一次）——回归钉保持绿
- [x] F9 复现证伪（Integer 正常渲染）——回归钉保持绿
- [x] 单元格文本绘制增加矩形裁剪（saveGraphicsState+re+W n+restoreGraphicsState），溢出消失；控制字符（\r\n\t）绘制前归一化为空格（10-导出Excel公式暴露的新缺陷）
- [x] 回归：Phase 1 全部断言测试保持绿；行距微调 1.2→1.3 缓解视觉压线

Exit Criteria:

- [x] F5/F6/F9 复现证伪（修订：无需修复，回归钉保持绿）；F10 裁剪修复并有内容流级断言锚定
- [x] synthetic-long-table 坐标级断言通过（F6 op-count）；分页页面视觉以 Phase 4/6 复核为准
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] No owner-doc update required（本 Phase 纯缺陷修复，无新配置面/契约变化）
- [x] `ai-dev/logs/` 对应日期条目已更新（随 Phase 2-6 统一提交）

### Phase 4 - 分页体系（F3/F4/F11/F13）

Status: completed
Targets: `nop-report/nop-report-pdf`、`nop-format/nop-excel` 模型、`nop-kernel/nop-core` TableSplitHelper、`ai-dev/design/nop-report/`、`docs-for-ai/03-runbooks/generate-report.md`

- Item Types: `Fix | Decision`

- [x] Decision（`ai-dev/design/nop-report/pdf-pagination.md`）：选定路线 (b) 缺省行为 + 全局配置（`repeat-header-rows`/`repeat-key-columns` 默认 1，0 关闭）；路线 (a) 模板级 Print_Titles 声明记为后续增强（需 ooxml 解析器支持）
- [x] 纵向分页续页重复表头行
- [x] 横向列拆分页重复左侧关键列；跨拆分边界合并单元格按裁剪呈现（与 Excel 打印一致）
- [x] 两遍拆分策略：单页能容纳不重复不拆分；需分页时按扣除重复块后的打印区重拆，页尾不溢出
- [x] 默认页脚页码（第 x / y 页），多页且未配置页脚时启用，`default-page-footer` 可关闭；占位符求值记入 Non-Blocking（现状为字面文本）
- [x] 图片绘制按页面边界裁剪
- [x] 回归：01-p2 关键列重复可辨、synthetic 续页有表头有页码；页数小幅增加（07: 2→3、13: 3→4、性能 534→548）为重复块预留空间的预期代价，判定标准为"拆分页可读"

Exit Criteria:

- [x] F3/F4 断言测试转绿（续页含表头、列拆分页含关键列）
- [x] 端到端验证：20 个真实 xpt 模板 + 合成表从模板加载→展开→分页渲染→导出全部成功（TestPdfExportAudit 21 用例全绿，Anti-Hollow）
- [x] 分页 Decision 已记录到 `ai-dev/design/nop-report/pdf-pagination.md`，路线 (b) 无需修订 Scope
- [x] `docs-for-ai/03-runbooks/generate-report.md` 已更新分页/打印能力说明；`check-doc-links.mjs --strict` 退出码 0
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新（随 Phase 2-6 统一提交）

### Phase 5 - 套打一致性与样式保真（F2/F14）

Status: completed
Targets: `nop-report/nop-report-pdf`、`docs-for-ai/03-runbooks/generate-report.md`

- Item Types: `Fix`

- [x] `renderImages` 按 print 标志排除背景图（与 HTML 渲染器对齐），F2 断言测试转绿；09-套打-p1 复核只含打印数据
- [x] F14 证伪：模板本身即 FF0E6089 深蓝字/FFC3E7F8 浅蓝底，模型/HTML/PDF 三方一致，渲染忠实，无需修复
- [x] runbook 套打描述与实现重新核对一致

Exit Criteria:

- [x] F2 断言测试转绿；09-套打 PNG 复核仅含数据
- [x] F14 比对结论（证伪）已记入分析报告复审节
- [x] `docs-for-ai/03-runbooks/generate-report.md` 与 live behavior 一致；链接检查通过
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新（随 Phase 2-6 统一提交）

### Phase 6 - 端到端验收与收口

Status: completed
Targets: `nop-report/nop-report-demo/src/test`、`ai-dev/analysis/`、`docs-for-ai/`

- Item Types: `Proof`

- [x] `TestPdfExportAudit` 升级为端到端验收：新增 `TestPdfExportAuditNoFontInjection`（无 VFS 字体注入，依赖系统字体发现）20 模板全部导出成功；注入路径的 `exportAll` 已带失败清单断言
- [x] 关键页面 PNG 视觉复核完成（01-p2 关键列重复、synthetic-p2 表头+页码、09-套打仅含数据），before/after 对照表写入 `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md` 复审节
- [x] P3 观感项裁定：日期断行/空展开行边框/行高不齐 为模板设计或低影响观感问题，移入 Non-Blocking Follow-ups；synthetic 文字贴近属数据侧未设列宽
- [x] 全模块回归：`./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-core,nop-report/nop-report-demo,nop-format/nop-excel -am` BUILD SUCCESS（快照基线因 CSS 修复同步更新，diff 均为单行 CSS 修复）

Exit Criteria:

- [x] 端到端验收测试通过（入口：xpt 模板路径；出口：PDF 文件内容断言）
- [x] before/after 视觉对照结论已写入 analysis
- [x] P3 项全部裁定（修复或有理由的 deferred）
- [x] 全模块回归 BUILD SUCCESS
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 审计报告 F1-F6、F8-F13 全部修复（F7 复验钉住、F14 证伪），无 in-scope live defect 被降级
- [x] 端到端验收（Phase 6）通过：Decision 环境前提下 20 模板 + 合成表全绿
- [x] 红测试→修复→绿证据完整（F2/F3/F4 红证据留存于 Phase 1 提交与测试日志；F5/F6/F9 复现证伪有回归钉）
- [x] `ai-dev/design/nop-report/` 包含字体策略（pdf-font-strategy.md）与分页模型（pdf-pagination.md）两份 Decision 记录
- [x] `docs-for-ai/03-runbooks/generate-report.md` 与实现一致（字体配置 + 套打 + 分页三处）
- [x] `./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-core,nop-report/nop-report-demo,nop-format/nop-excel -am` BUILD SUCCESS
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2260-nop-report-pdf-quality.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report --severity high` 退出码 0
- [x] 独立子 agent closure audit 已完成并将证据写入本节

## Deferred But Adjudicated

### 字体伪粗体（fake bold）精细渲染

- Classification: `optimization candidate`
- Why Not Blocking Closure: Phase 2 保证 bold 语义有确定呈现策略（不劣于现状），笔画的伪粗体渲染属视觉增强，不影响可读性与可用性
- Successor Required: no
- Successor Path: 后续视觉打磨任务

### 大数据量导出性能优化

- Classification: `watch-only residual`
- Why Not Blocking Closure: 534 页 28s 实测可接受，且列拆分修复后页数将下降
- Successor Required: no
- Successor Path: 无

## Non-Blocking Follow-ups

- 页眉页脚富文本（多字体混排、图片页眉）与占位符（&P/&N/&D/&A）求值支持
- `expandInplaceCount` 等展开特性在 PDF 路径的专项视觉验证
- FontManager 字体别名机制（addFontAlias）的公开配置化
- P3 观感项（01 日期断行、08 空展开行边框、07 行高不齐）——属模板设计或低影响观感，随模板优化处理
- 模板级 Print_Titles 声明（需 nop-ooxml-xlsx 解析器支持 definedNames）
- 内置 CJK 子集字体（无系统字体环境的开箱即用方案）

## Closure

Status Note: 全部 6 个 Phase 落地。视觉审计的 7 项 P0/P1 中 4 项修复（F1 字体/F2 套打/F3 列拆分关键列/F4 续页表头页码）、3 项复现证伪并转为回归钉（F5/F6/F9，真实根源 F10 已修复）；F7 复验、F8/F10/F11/F12/F13 修复、F14 证伪。新增 3 个测试类 20 个用例（含无字体注入端到端验收），nop-report-pdf 23 测试全绿，全量回归 BUILD SUCCESS。
Completed: 2026-09-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（closure auditor，task: "plan 2260 closure audit"）
- Evidence:
  - Phase 1-6 Exit Criteria 逐条 PASS（源码级核对：FontManager 字体发现/回退、PdfRenderer.fontForText、PdfStyleHelper 裁剪、PdfSheetRenderer 两遍拆分+重复块+isPrint 过滤、PdfReportRenderer→drawDefaultPageNumbers 接线连通）
  - 实跑 `./mvnw test -pl nop-report/nop-report-pdf -am` 退出码 0（23 tests, 0 failures）
  - `check-plan-checklist.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs --module nop-report --severity high` 退出码 0（0 findings）；`check-doc-links.mjs --strict` 退出码 0
  - Anti-Hollow：三条调用链（默认页码、套打过滤、字形回退）源码追踪连通；端到端产物 `_tmp/report-pdf-audit-noinject/` 20 PDF 与测试时间戳吻合
  - Deferred 分类检查：F5/F6/F9/F14 为证伪（有出生即绿回归钉），无 in-scope live defect 被降级
  - 审计提出的 3 项收尾（提交 Phase 3-6 改动、补写执行日志、移除 TestDiag.java）已在最终提交中落实

Follow-up:

- 见 Non-Blocking Follow-ups（伪粗体渲染、页眉页脚占位符、模板级 Print_Titles、内置 CJK 子集字体、P3 观感项）

## Post-Closure Review（独立代码审查修复，2026-09-06）

3 个独立子代理对全部变动代码审查，发现 2 Blocker + 4 Major + 若干 Minor，全部修复并验证：

- **[Blocker] 图片裁剪坐标系错误**：clip 矩形误在平移后的用户空间定义，导致所有 print=true 图片从 PDF 消失。修复：applyPageSetup 返回设备页矩形在当前用户空间的裁剪矩形（含缩放/居中逆运算），drawImage 纯用户空间裁剪。视觉验证：11-条码二维码 条码/QR 全部重现且不再越顶。
- **[Blocker] TTC 加载从未生效**：TrueTypeFont.getOriginalData() 返回整个 TTC 文件字节，PDType0Font.load 按 sfnt 解析 "ttcf" 头必失败——Linux 生产环境 CJK 字体几乎全是 .ttc。修复：解析 TTC 目录取全部子字体 offset 最小值切出独立 sfnt 字节段（ttcFirstFontOffset），尾部残留字节被 sfnt 表目录解析忽略。
- **[Major] drawText 顶层归一化破坏 wrapText 显式换行**：\n 被变空格导致多行文本重排。修复：移除顶层归一化，保留两个子方法在绘制粒度的归一化（孤立即\r 仍被行级处理覆盖）。新增显式换行多行回归测试。
- **[Major] 行列双向续页表头错位+角块缺失**：repeatRows 块未随 dx 右移、无角块。修复：四块网格布局（角块原点/表头行块 x+dx/关键列块 y-dy/内容 (dx,-dy)），新增 testFourBlockGridBothDimensionsRepeat。
- **[Major] 默认页码无字体环境硬失败**：无 CJK 回退字体时降级为英文页码 "Page x / y"（LOG.warn），不再抛错。
- **[Minor]**：fit缩放/居中把重复块占位(dx/dy)计入；unencodableChars 改 WeakHashMap；ERR_PDF_FALLBACK_FONT_NOT_FOUND 真正抛出（无任何字体来源时）；ChartDataFilter.sortDataSetByValue 搬运 bubbleSizes/heatmapValues。
- **[测试加固]** FontManager.resetForTesting()（仅测试用）+ 三个字体相关测试类强制重置/前提断言（消除单例跨类缓存污染导致的"绿不可解释"）；TestPdfFontResolution 无CJK字体环境 SKIP 化（Assumptions）；F3/F4 断言增强（等值/全部续页）；TestRecordRegionBoundary 流式事件精确计数；NoFontInjection 增加 CJK 文本提取断言。
- 审查遗留 P3（不阻塞，记录跟进）：MultiLineConfigParser 空三引号块；TableBlock.resetCellBlockIndex 合并格锚点去重；SubBinaryDataReader detach/duplicate 窗口语义；streaming continuation 异常路径 subInput 释放；F10 裁剪断言可升级 q/Q 跟踪。
