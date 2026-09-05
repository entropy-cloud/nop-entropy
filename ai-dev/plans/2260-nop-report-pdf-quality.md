# 2260 nop-report PDF 导出显示质量与自动分页完善

> Plan Status: draft
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

Status: planned
Targets: `nop-report/nop-report-demo/src/test`、`nop-report/nop-report-pdf/src/test`

- Item Types: `Proof | Fix`

- [ ] 在既有 `TestPdfSheetRenderFixes`/`TestPdfReportRenderer` 基础上叠加断言夹具，不另起炉灶
- [ ] 为 F5（wrapText 叠压）、F6（边界重影）、F9（Integer 列）各写一个失败先行（红）的 renderer 级复现测试，输出 PDF 并断言其错误形态（如文本坐标/行数/异常类型）
- [ ] F7 复验：写无样式单元格的复现测试，验证提交 e2b02af86e 的修复在当前 HEAD 成立；该测试为出生即绿的回归钉（Proof），不得虚报为红测试
- [ ] 为 F2（print=false）、F3（列拆分）、F4（续页表头）写基于 PDFBox 文本提取/页面内容（含 XObject 资源检查）的断言测试骨架（修复前断言当前错误行为并标注，修复后翻转）
- [ ] 将 `TestPdfExportAudit` 的 `exportAll` 增加机器可读结果与失败清单断言；**Phase 1 断言标准 = 注入 CJK 字体后全绿**（与审计现状一致），Phase 2 落地后翻转为"无注入全绿"，避免断言标准漂移
- [ ] 确认 F9 根因与 F14（区块标题颜色保真）结论，回写审计报告 Open Questions

Exit Criteria:

- [ ] F5/F6/F9 均有红测试且失败形态与审计报告描述一致；F7 有出生即绿的复验回归测试
- [ ] F2/F3/F4 断言骨架可运行并钉住当前行为
- [ ] F9/F14 根因结论已回写 `ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 字体体系（F1/F8/F12 变体策略）

Status: planned
Targets: `nop-report/nop-report-pdf`（font 包及取字体路径）、`ai-dev/design/nop-report/`、`docs-for-ai/03-runbooks/generate-report.md`

- Item Types: `Fix | Decision`

- [ ] Decision（写入 `ai-dev/design/nop-report/pdf-font-strategy.md`，目录不存在则创建）：①CJK 字体来源策略（配置目录 + 系统字体目录自动发现 / 内置小体积子集字体 / 两者结合），含拒绝项理由；②**环境前提裁定**——无系统 CJK 字体的环境（精简 CI 容器）下测试的通过标准：内置子集字体、或 CI 镜像安装 CJK 字体（如 fonts-wqy-microhei），前提必须写入 Exit Criteria 与 owner doc，禁止依赖"开发机恰好有字体"的隐式前提；③**回退与快败的精确边界**——字体名缺失/未知回退 base-14（保持 Arial 等常见字体模板现状可用），仅当字符在最终选中字体中无法编码时按字符回退 CJK 字体、无回退能力时报错；④bold/italic 呈现策略
- [ ] 字体解析增加字形覆盖检查与按字符回退：base-14 字体无法编码的字符回退到 CJK 字体，而不是运行时崩溃
- [ ] null/未知字体名的默认字体解析走已配置的默认字体资源（当前硬编码 Helvetica），彻底消除 F8
- [ ] 字体加载失败时抛出带可操作指引的错误码（指明如何配置字体），错误码按仓库惯例在 i18n yaml 注册并通过 `check-error-param-consistency.mjs` 校验
- [ ] 明确保持 per-document 字体复用现状（`PdfRenderer.fontCache`），**禁止跨文档缓存 `PDType0Font` 实例**（绑定 PDDocument，跨文档复用产出损坏 PDF）——以测试或代码注释钉住该约束
- [ ] 新增/更新测试：按 Phase 2 Decision 裁定的环境前提下，中文模板 PDF 导出成功且文本可提取（`PDFTextStripper` 断言包含中文内容，防字形丢失）
- [ ] Owner doc：`generate-report.md` 新增字体配置/自动发现说明（当前对字体零文档；字体配置是新的用户可见配置面）

Exit Criteria:

- [ ] `TestPdfExportAudit` 在 Decision 裁定的环境前提下不注入 VFS 字体即 20 个模板全部导出成功
- [ ] 中文文本经 `PDFTextStripper` 提取断言通过（防字形丢失）
- [ ] 字体来源策略、环境前提、回退边界三个 Decision 已记录到 `ai-dev/design/nop-report/pdf-font-strategy.md`
- [ ] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [ ] `docs-for-ai/03-runbooks/generate-report.md` 字体节已更新；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文本渲染正确性（F5/F6/F9/F10）

Status: planned
Targets: `nop-report/nop-report-pdf`（PdfStyleHelper/PdfTableRenderer 及 utils 包）

- Item Types: `Fix`

- [ ] 修复 wrapText 多行文本行距计算（叠压根因，Phase 1 红测试转绿）；多行文本不得溢出单元格底部无界绘制
- [ ] 修复分页边界行文字重影（边界行只属于一个 region；Phase 1 红测试转绿）。注：重影的最终视觉消除以 Phase 4/6 的分页改动后复核为准，本 Phase 以坐标级断言钉住"边界行只绘制一次"
- [ ] 修复 Integer 值列未渲染（Phase 1 红测试转绿）
- [ ] 单元格文本绘制增加矩形裁剪，长文本不再溢出到相邻单元格（视觉复核 02/07）
- [ ] 回归：Phase 1 全部断言测试转绿

Exit Criteria:

- [ ] Phase 1 F5/F6/F9 红测试转绿且无断言弱化；F7 复验测试保持绿
- [ ] synthetic-long-table 坐标级断言通过（边界行单次绘制、无叠压形态）；分页相关页面的最终视觉复核以 Phase 4/6 为准
- [ ] `./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-demo -am` 全绿
- [ ] No owner-doc update required（本 Phase 纯缺陷修复，无新配置面/契约变化）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 分页体系（F3/F4/F11/F13）

Status: planned
Targets: `nop-report/nop-report-pdf`、`nop-format/nop-excel` 模型、`nop-kernel/nop-core` TableSplitHelper、`ai-dev/design/nop-report/`、`docs-for-ai/03-runbooks/generate-report.md`

- Item Types: `Fix | Decision`

- [ ] Decision（写入 `ai-dev/design/nop-report/pdf-pagination.md`）：重复标题行/列（Excel printTitles 等价物）的承载方式，**须预写两条路线的 scope 边界并二选一**：(a) 模板级声明（等价 Excel Print_Titles defined name）——需将 `nop-format/nop-ooxml-xlsx` 解析器纳入 In Scope（当前 Out of Scope，须先修订 Scope 节）；(b) 编程式 API + 缺省行为（首区块头行/首列区自动重复）——不需要 Out-of-Scope 模块改动，真实模板的复核以缺省行为为准。选定后在执行前同步 Scope 节
- [ ] 纵向分页续页重复表头行（按 Decision 的配置或缺省行为）
- [ ] 横向列拆分页重复左侧关键列；合并单元格跨拆分边界的裁剪/重绘策略确定并实现（消除 03-p2 表头叠压）
- [ ] 标题等跨全宽合并单元格在列拆分各页上的呈现策略（每页完整呈现或仅首页），消除 01-p1 标题错位
- [ ] 默认页脚页码（第 x 页/共 y 页），模板未配置时启用、可关闭；页眉页脚占位符（页码/总页数/日期/表名）求值支持（含验证 `&P` 类占位符现状）
- [ ] 图片绘制按页面边界裁剪（11 二维码越顶问题）
- [ ] 回归：01/03/16/测试同比环比/synthetic 的分页产出复核——续页有表头、拆分页有关键列且可读；**页数判定标准**：以 Decision 后的预期为准（列拆分页数不会因重复关键列减少，判定标准是"拆分页可读"而非页数增减），具体数值阈值在 Decision 时钉入本 Phase 的验证记录

Exit Criteria:

- [ ] F3/F4 断言测试（Phase 1 骨架）转绿：续页文本包含表头行内容、列拆分页包含关键列内容（选用页面上唯一的表头/关键列字符串断言避免误判）
- [ ] 端到端验证：从 xpt 模板加载 → 展开 → 分页渲染 → PDF 文本/内容断言的完整路径测试通过（Anti-Hollow）
- [ ] 分页模型 Decision 已记录到 `ai-dev/design/nop-report/pdf-pagination.md`，且 Scope 节与 Decision 一致
- [ ] `docs-for-ai/03-runbooks/generate-report.md` 已更新分页/打印能力说明；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-demo,nop-format/nop-excel -am` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 套打一致性与样式保真（F2/F14）

Status: planned
Targets: `nop-report/nop-report-pdf`、`docs-for-ai/03-runbooks/generate-report.md`

- Item Types: `Fix`

- [ ] `renderImages` 按 print 标志排除背景图，与 XLSX/HTML 渲染器行为对齐（HTML 渲染器已有 `!image.isPrint()` 过滤先例，F2 断言测试转绿：套打 PDF 不含背景图、数据文字保留）
- [ ] 按 F14 结论修复区块标题颜色读取（如属渲染缺陷）；与 HTML 渲染器同模板输出比对关键颜色
- [ ] runbook 中 PDF 套打描述与实现重新核对一致（Owner doc 修正）

Exit Criteria:

- [ ] F2 断言测试转绿；09-套打 PNG 复核仅含数据
- [ ] 同一模板 HTML vs PDF 关键样式（区块标题颜色/背景）比对结论记入日志
- [ ] `docs-for-ai/03-runbooks/generate-report.md` 与 live behavior 一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 端到端验收与收口

Status: planned
Targets: `nop-report/nop-report-demo/src/test`、`ai-dev/analysis/`、`docs-for-ai/`

- Item Types: `Proof`

- [ ] `TestPdfExportAudit` 升级为端到端验收：按 Phase 2 Decision 的环境前提（无 VFS 字体注入）全模板导出成功 + 关键断言（中文可提取、分页页数符合 Decision 预期、无已知缺陷形态）
- [ ] 全部 21 个用例的最终 PNG 逐页视觉复核，与审计报告的 before/after 对照结论写入 `ai-dev/analysis/`（追加复审节）
- [ ] P3 观感项（日期断行、空展开行边框、行高不齐）复核并裁定：修复或移入 Deferred But Adjudicated
- [ ] 全模块回归：`./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-core,nop-report/nop-report-demo,nop-format/nop-excel -am`

Exit Criteria:

- [ ] 端到端验收测试通过（入口：xpt 模板路径；出口：PDF 文件内容断言）
- [ ] before/after 视觉对照结论已写入 analysis
- [ ] P3 项全部裁定（修复或有理由的 deferred）
- [ ] 全模块回归 BUILD SUCCESS
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 审计报告 F1-F6、F8-F13 全部修复（F7 复验钉住、F14 按结论修复或证伪），无 in-scope live defect 被降级
- [ ] 端到端验收（Phase 6）通过：Decision 环境前提下 20 模板 + 合成表全绿
- [ ] 红测试→修复→绿证据完整（各 Phase 断言测试历史可追溯；F7 为复验回归钉，不要求红证据）
- [ ] `ai-dev/design/nop-report/` 包含字体策略（pdf-font-strategy.md）与分页模型（pdf-pagination.md）两份 Decision 记录
- [ ] `docs-for-ai/03-runbooks/generate-report.md` 与实现一致（字体配置 + 套打 + 分页三处）
- [ ] `./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-core,nop-report/nop-report-demo,nop-format/nop-excel -am` BUILD SUCCESS
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2260-nop-report-pdf-quality.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report --severity high` 退出码 0
- [ ] 独立子 agent closure audit 已完成并将证据写入本节

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

- 页眉页脚富文本（多字体混排、图片页眉）支持
- `expandInplaceCount` 等展开特性在 PDF 路径的专项视觉验证
- FontManager 字体别名机制（addFontAlias）的公开配置化

## Closure

Status Note: （未关闭）
Completed: N/A

Closure Audit Evidence:

- Reviewer / Agent: N/A（未关闭）
- Evidence: N/A

Follow-up:

- 见 Non-Blocking Follow-ups
