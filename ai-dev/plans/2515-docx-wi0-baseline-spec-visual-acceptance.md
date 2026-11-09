# 2515 docx roadmap WI0：基线梳理、规格固化与 docx 视觉验收工具

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI0）+ ai-dev/design/word-editor/online-word-model-design.md
> Related: ai-dev/backlog/nop-pdf-report-export-roadmap.md（WI0 视觉验收工具已落地，本计划复用）

## Purpose

完成 docx 文档模型 roadmap 的 WI0：把 Word 文档模型的使用契约固化为 docs-for-ai owner doc，建立 docx 复杂样本语料与 docx→PDF→PNG 视觉验收工具链（复用 nop-pdf verify 工具），整理参考实现索引。本计划是后续 WI1–WI11 的完成门基座。

## Current Baseline

- 主设计输入 `ai-dev/design/word-editor/online-word-model-design.md`（454 行）已存在，含模型分层、模板语义内嵌原则、springreport 三层对比结论（§13）。
- 模型模块 `nop-format/nop-office-doc-model`（33 main 文件）：OfficeDocModel/OfficeDocPageModel/OfficeParagraphModel/OfficeRunModel/WordTable 及 8 个内嵌模板模型；块级类型用空标记接口 `OfficeBlock` + `type` 属性字符串表达，doc.xdef body 仅允许 `<p>`/`<table>` 两种块。
- 共享叶子模型 `nop-format/nop-office-model`：OfficeFont/WordRunStyle/WordParagraphStyle/WordCellStyle/WordTableStyle/OfficeBorder + 颜色/对齐枚举已存在。
- docx 读写 `nop-format/nop-ooxml/nop-ooxml-docx`：OfficeDocModelParser 解析仅覆盖 `w:p`/`w:tbl`（hyperlink 解包为 runs）；OfficeDocXmlBuilder 生成仅支持段落+表格；测试资源 docx 9 个（simple/tpl/loop/br/el/header-footer/background/link-expr/payment）。
- PDF 视觉验收工具为 **main 代码**：`nop-format/nop-pdf/src/main/java/io/nop/pdf/verify/`（PdfGoldenManager/PdfImageRenderer/ImageDiff/RegionProbe），可直接加 Maven 依赖复用；golden 目录约定 `<repo-root>/_tmp/pdf-golden/<fixture>/`，更新模式 `-Dnop.pdf.golden.update=true`。
- `docs-for-ai/` 无 office/word/docx owner doc；`docs-for-ai/03-modules/nop-report.md:58` 已预留「Word 通用文档模型归 docx 文档模型 roadmap」的路由位。
- 参考仓库：`~/sources/springreport`（SpringReport + ui-vue2/ui-vue3）已下载；`~/sources/pdfbox` 已下载。poi/docx4j/mammoth 未本地下载。
- LibreOffice 已于本计划执行前经 brew 安装（26.8.2.2，`/opt/homebrew/bin/soffice`）；工具对 soffice 缺失环境仍需具名裁定降级路径（assumeTrue + 日志），保证 CI/其他机器可跑。

## Goals

- `docs-for-ai/02-core-guides/word-document-model.md` owner doc 落地：模型能力矩阵、docx 读写契约、模板语义内嵌规则、范围硬边界；INDEX.md 与 source-anchors.md 路由收口；设计文档与 owner doc 的 SSOT 关系收敛（设计文档保留决策理由，能力矩阵/契约以 owner doc 为准）。
- docx 复杂样本语料：程序合成（test 工具代码），覆盖 roadmap 全部块级/行内类型与多节页眉页脚，作为 WI3/WI5/WI11 的对版语料；不提交大体积 docx 入库。
- docx 视觉验收工具：test scope 的 docx→PDF（LibreOffice headless）→PNG（PdfImageRenderer）→golden/diff（PdfGoldenManager/ImageDiff/RegionProbe）链路 + 验收口径说明。
- 参考实现索引：springreport（本地代码实证）/ apache-poi / docx4j / mammoth 能力对版矩阵，落 `ai-dev/analysis/2026-11/`。

## Non-Goals

- 任何模型/生成器功能改动：不改 doc.xdef/word-table.xdef、OfficeDocModelParser/OfficeDocXmlBuilder、OfficeDocModel 系列类（WI1–WI5 承载）。
- 模型→PDF 渲染器（WI6）；docx→PDF 仅经外部 LibreOffice 桥做验收对照，不进产品代码。
- 前端设计器/编辑器 UI；PPT；springreport 代码或其闭源依赖的引入。
- poi/docx4j/mammoth 源码仓库的本地下载（索引基于官方文档事实 + springreport 本地实证即可）。

## Scope

### In Scope

- 新增 `docs-for-ai/02-core-guides/word-document-model.md`；更新 `docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`；`ai-dev/design/word-editor/online-word-model-design.md` 顶部收敛指针 + 去重。
- `nop-ooxml-docx` test scope：docx 语料合成工具 + 复杂样本（程序生成到 target 目录）。
- `nop-ooxml-docx` test scope：DocxVisualVerifier（soffice 桥 + nop-pdf verify 复用）+ 使用该工具的冒烟测试。
- `ai-dev/analysis/2026-11/2026-11-10-docx-reference-impl-index.md` 参考索引与能力对版矩阵。
- `ai-dev/analysis/2026-11/` 视觉验收口径说明（工具使用、DPI/容差裁定、soffice 缺失降级裁定）。
- roadmap `Work Item Status` WI0 勾选 + `Last updated` 行同步（WI0 单 WI 完成不派生任何 ★ Milestone 勾选）。

### Out Of Scope

- 见 Non-Goals；另：不修改 nop-pdf verify 工具本身；不动 `docs/` 历史目录。

## Execution Plan

### Phase 1 - Owner doc 固化与路由收口

Status: completed
Targets: `docs-for-ai/02-core-guides/word-document-model.md`, `docs-for-ai/INDEX.md`, `docs-for-ai/04-reference/source-anchors.md`, `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 撰写 `docs-for-ai/02-core-guides/word-document-model.md`：模型分层（静态结构 vs 内嵌模板 model）、能力矩阵（现状已支持/roadmap 将补齐 两列标注）、docx 解析契约、docx 生成契约、模板语义内嵌规则（doc/page/paragraph/run/table/row/cell.model，对齐 workbook.xdef 做法）、范围硬边界（复用 nop-ooxml-docx/nop-report-docx、不用 ExcelStyle 当 Word 样式、PDF 不换引擎、不引入 springreport 代码）、与 nop-excel/nop-report 的模块边界。规范性文档：无日期/无 plan 引用/无状态历史。
- [x] SSOT 收敛：`ai-dev/design/word-editor/online-word-model-design.md` 顶部加指向 owner doc 的权威声明，仅删除 owner doc 明确拥有的「能力矩阵/读写契约」重复块；设计文档 §10 的实现层设计（生成链路、内嵌方式示意）保留为决策理由并链接 owner doc，不做盲目去重。
- [x] `docs-for-ai/INDEX.md` 快速路由表增加 Word 文档模型条目。
- [x] `docs-for-ai/04-reference/source-anchors.md` 增加 owner doc 源码锚点（OfficeDocModel 系列类、doc.xdef、OfficeDocModelParser/Writer 路径）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

- [x] owner doc 存在且四类内容齐备（能力矩阵/读写契约/内嵌规则/边界），能力矩阵中「现状已支持」列每项有源码锚点支撑且与 live code 一致。
- [x] owner doc 无与设计文档重复的完整段落（grep 抽查能力矩阵只出现一处）。
- [x] INDEX.md / source-anchors.md 更新且 check-doc-links --strict 退出码 0。
- [x] No owner-doc update required 之外的文档裁定：本 Phase 即 owner doc 更新本身。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - docx 复杂样本语料（程序合成）

Status: completed
Targets: `nop-format/nop-ooxml/nop-ooxml-docx/src/test/java/io/nop/ooxml/docx/corpus/`

- Item Types: `Proof`

- [x] 新增 test 工具 `DocxCorpusBuilder`（或同名职责类组）：以最小 OOXML 部件手写 + zip 打包方式程序合成 docx，不依赖 OfficeDocModelWriter（避免被被测能力自我循环验证）；生成目录 `target/docx-corpus/`。
- [x] 语料覆盖矩阵（每类至少 1 样本）：标题/多级列表、图片（含关系/ContentTypes/媒体部件）、独立超链接块、tab/br 行内、分页 pageBreak、分隔线 separator、多节（section）页眉页脚、横纵合并/列宽/行高/边框/底纹表格、run 全样式（粗斜下删高上下标字体字号颜色）、页面设置（宽高/方向/页边距）、背景/水印。
- [x] 确定性生成契约：builder 输出不含时间戳/随机 ID/系统路径（图片与关系 ID 用固定序号），同一 builder 方法多次调用产物字节一致；后续 WI（WI3/WI5/WI11）的测试以调用 `DocxCorpusBuilder` 方法作为语料入口（每次测试内重新生成），不依赖 target 目录残留文件。
- [x] 合成冒烟测试：zip 部件结构断言（[Content_Types].xml、document.xml、关系部件存在性）——证明语料是合法 docx 包；docx→PDF 转换验证统一由 Phase 3 的 DocxVisualVerifier 冒烟承载（避免 soffice 桥接逻辑在 Phase 2 重复实现）。
- [x] 不提交生成 docx 入库（target 目录 + .gitignore 约定确认）。

Exit Criteria:

- [x] 语料覆盖矩阵逐项有对应样本生成代码与断言（测试名可索引）；确定性契约以「同一 builder 方法连续两次生成字节一致」断言落测试。
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿。
- [x] 无新增入库 docx 大文件（git status 核对）。
- [x] No owner-doc update required（纯测试基建）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - docx→PDF→PNG 视觉验收工具与口径

Status: completed
Targets: `nop-format/nop-ooxml/nop-ooxml-docx/src/test/java/io/nop/ooxml/docx/verify/`, `ai-dev/analysis/2026-11/`

- Item Types: `Decision + Proof`

- [x] 新增 test 工具 `DocxVisualVerifier`：docx → soffice headless 转 PDF（`--convert-to pdf`，超时与进程清理，`-env:UserInstallation` 隔离 profile 避免并发/首启冲突，soffice 路径可配且做 PATH/常见安装位置发现）→ PdfImageRenderer.renderPage（DPI 口径 110，对齐 nop-pdf）→ golden 比对走 `new PdfGoldenManager(Path)` 显式目录 `_tmp/docx-golden/`（不使用 forDefaultDir，避免落入 pdf-golden 与 PDF golden 混淆；ImageDiff 容差裁定）→ 返回逐页比对结果。
- [x] 更新模式归属：`DocxVisualVerifier` 自持属性 `nop.docx.golden.update`（不与 `nop.pdf.golden.update` 共用），由包装层裁定调 `update()` 或 `compare()`；PdfGoldenManager 本体零改动。
- [x] soffice 缺失降级裁定：soffice 不可用时测试以 `assumeTrue` + 明确日志跳过（具名裁定，非静默）。
- [x] 用 Phase 2 语料跑一轮视觉冒烟：至少 3 个样本（simple 基线、多节页眉页脚、合并表格）产出 golden 并做像素断言（RegionProbe 抽样）。
- [x] 验收口径说明落 `ai-dev/analysis/2026-11/2026-11-10-docx-visual-acceptance-tooling.md`：DPI/容差/golden 目录约定、更新模式归属、soffice 依赖与 CI 裁定、与 nop-pdf WI0 口径的复用关系（时效性文档，带 Status）。

Exit Criteria:

- [x] DocxVisualVerifier 存在且被冒烟测试实际调用（接线验证：测试从 docx 入口到 golden 比对出口完整跑通）。
- [x] 本机 soffice 可用时冒烟测试真实通过（非 skip）；golden 生成于 `_tmp/docx-golden/`。
- [x] 口径说明文档存在且与工具实际参数一致（DPI、容差数值 grep 一致）。
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿。
- [x] No owner-doc update required（工具为 test 基建；WI3/WI5 消费时再入 owner doc）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 参考实现索引与能力对版矩阵

Status: completed
Targets: `ai-dev/analysis/2026-11/2026-11-10-docx-reference-impl-index.md`

- Item Types: `Decision`

- [x] 撰写参考索引：springreport（本地 `~/sources/springreport` 代码实证，逐项给 file:line）、apache-poi（XWPF 能力，官方文档事实）、docx4j、mammoth；每项记录：定位、docx 结构表达能力、模板能力、Word→PDF 路线、对本 roadmap 的参考价值与禁用项（闭源/付费依赖）。
- [x] 能力对版矩阵：roadmap 范围内的块级/行内/样式/模板/PDF 能力 × 4 个参考实现，标注「吸收 / 仅对版 / 禁用」。
- [x] 文档带日期与 Status: open（时效性，落 ai-dev/analysis）。

Exit Criteria:

- [x] 索引文档存在，springreport 条目每项有本地 file:line 证据；poi/docx4j/mammoth 条目有官方来源 URL。
- [x] 矩阵覆盖 roadmap Purpose 列出的全部能力项（块级 8 类、行内 4 类、样式 4 类、模板标注、PDF、图表/码）。
- [x] check-doc-links --strict 退出码 0。
- [x] No owner-doc update required（索引为时效性分析，不入 docs-for-ai）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI0 已勾选且 `Last updated` 行已同步（无 Milestone 勾选——WI0 不构成任何 ★ Milestone 的完成条件）。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿（触及多文档模块则升级口径）。
- [x] check-doc-links --strict 退出码 0。
- [x] 无被静默降级的 in-scope 项；soffice 缺失降级为具名裁定（assumeTrue + 日志）而非静默跳过。
- [x] 独立子 agent closure audit 完成且 evidence 写入本 plan Closure 段。
- [x] Anti-Hollow Check：DocxVisualVerifier 与 DocxCorpusBuilder 被冒烟测试运行时真实调用（非仅存在）；无空方法体/no-op 正常实现。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2515-docx-wi0-baseline-spec-visual-acceptance.md --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ooxml-docx --severity high` 退出码 0。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

### poi/docx4j/mammoth 源码本地下载

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 参考索引只需能力事实与定位，官方文档 + springreport 本地实证已满足索引用途；本地下载仅在后续 WI 需要逐行对版时再启动。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- LibreOffice 版本升级引起的 golden 漂移监控（golden 不入库，漂移在具名裁定下 --update 重生成）。

## Closure

Status Note: WI0 四个 Phase 全部完成并经独立子 agent closure audit（APPROVED-WITH-FIX，三项 fix 已全部落实）：owner doc 落地并通过 SSOT 路由收口；11 个确定性合成语料样本 + 4 用例全绿；DocxVisualVerifier 视觉链路实跑（3 用例非 skip，golden 4 张）；参考索引含逐项 file:line 实证与官方 URL；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx` 50/50 绿；check-doc-links --strict 与 scan-hollow 均退出码 0。审计修正记录：FIX-A 能力矩阵行高改为已支持（WordTableParser trHeight 解析 + OfficeDocXmlBuilder 生成）、模板标注名对齐为 c:if/c:for；FIX-B 参考索引补 4 个官方 URL；FIX-C 本 Closure 段与定向 commit。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_73eed2ee-c784-4286-8d50-5f30ef15c86e）
- Audit Session: agent_73eed2ee-c784-4286-8d50-5f30ef15c86e
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（owner doc 五类内容齐备；INDEX.md:79、source-anchors.md WDM-001..006；SSOT 声明在设计文档顶部）；Phase 2 PASS（11 样本方法 + testDeterministicByteIdenticalOutput 落测；50 测试绿）；Phase 3 PASS（调用链 TestDocxVisualVerifier→convertToPdf→PdfImageRenderer.renderAll(DPI 110)→verifyGolden→golden.compare/update + RegionProbe 实证；new PdfGoldenManager(Path) 显式 _tmp/docx-golden/；nop.docx.golden.update 自持；口径文档三处 grep 一致）；Phase 4 PASS（springreport DocTpl.java:80 / DocTplSettings.java:90-92 抽查属实；矩阵覆盖全能力行；官方 URL 已补）。
  - Closure Gates：check-doc-links --strict 退出码 0；scan-hollow --module nop-format/nop-ooxml/nop-ooxml-docx --severity high 退出码 0（0 发现）；check-plan-checklist --strict 退出码 0；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx` 50/50 0 失败 0 skip；roadmap WI0 勾选 + Last updated 同步 + 无 Milestone 错误勾选；日志 ai-dev/logs/2026/11-10.md 有条目；生产源码零改动。
  - Anti-Hollow 检查：DocxVisualVerifier/DocxCorpusBuilder 被冒烟测试运行时真实调用（3 视觉用例实跑非 skip，golden PNG 实际产出）；无空方法体/no-op 正常实现（soffice 缺失走具名 assumeTrue，转换失败 fail-fast 抛 IllegalStateException）。
  - Deferred 项分类检查：poi/docx4j/mammoth 本地下载为 Non-Goals 开篇声明的 out-of-scope improvement，非 in-scope live defect 降级。
