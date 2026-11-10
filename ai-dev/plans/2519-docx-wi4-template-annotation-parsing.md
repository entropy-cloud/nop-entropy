# 2519 docx roadmap WI4：模板标注解析

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI4）+ docs-for-ai/02-core-guides/word-document-model.md §7 + 设计文档 §15（WI3 残余）
> Related: ai-dev/plans/2518-docx-wi3-parser-completion.md

## Purpose

docx 内模板标注到内嵌 model 的解析闭环：`c:if`/`c:for` 段落标记（含 WI3 残余的列表块 loop 承载）、`expr:`/`xpl:`/`tpl_expr:` 超链接语义核对补全、`xpt:table` 批注 → table.model 内嵌标记，与 `XplGenConfig`/`XptWordTemplateModel` 语义对齐。

## Current Baseline

- 已落地（WI3 及之前，live code）：段落/页面级 `c:if`（test→testExpr 含嵌套合并）与 `c:for`（items→loopItemsName，page.model）；超链接 `expr:`/`xpl:`/`tpl_expr:` → OfficeRunTemplateModel（applyHyperlinkModel，WordTemplateParser 链路 + WI3 OfficeLinkModel 保留）；列表块 c:if 已迁移 OfficeListTemplateModel。
- **缺口 1**：块级 `c:for` 循环信息无承载——OfficeParagraphTemplateModel/OfficeTitleTemplateModel/OfficeListTemplateModel 均无 loop 字段（xdef 未定义），段落级单块 c:for 被消费后静默丢弃、列表块显式丢弃（WI3 Deferred successor 本计划）；BlockTemplateBinding 字段全集 = testExpr/visibleExpr/beginLoop/loopVarName/loopIndexName/loopItemsName（**无 endLoop**；beginLoop 语义为「复杂 items 表达式编译出的值 action」非循环体）。
- **缺口 2**：`xpt:table` 批注仅旧链路识别——XptWordTemplateParser（nop-report-docx）经 `doc.forEachNode` 对**所有层级** w:tbl（含单元格内嵌套表）识别并替换为 GenXptTable gen 标签；OfficeDocModelParser 不识别，WordTable.model 上无标记；WordTableParser 无批注解析路径（WordTableCell.comment 字段无人填充）。
- **缺口 3**：`linkExpr`（run/link model 的 xdef 字段）在解析链路无生产者——applyHyperlinkModel 只 set valueExpr（expr:）与 templateExpr（tpl_expr:/xpl:）。
- WordTableTemplateModel 已有 renderType/dataExpr/expandExpr/styleIdExpr/testExpr/beforeRender/afterRender（word-table.xdef）。
- 既有测试：TestOfficeDocModelParser testMapPageIfToTestExpr（page binding）、TestWordTemplate 9 用例（旧链路旁证）、TestDocxCorpusParsing 19 用例。

## Goals

- 三类块级模板模型（OfficeParagraphTemplateModel/OfficeTitleTemplateModel/OfficeListTemplateModel）增 loop 承载（xdef + codegen）：loopVarName/loopIndexName/loopItemsName 属性 + beginLoop 子节点（无 endLoop——binding 无来源）；applyBlockTemplateBinding/applyTitleTemplateBinding/applyListTemplateBinding 迁移 loop 字段，消除段落级与列表级 c:for 的静默丢弃。
- 识别 `xpt:table` 批注表格 → 顶层 `table.model.renderType="xpt"` 内嵌标记：识别落在 **WordTableParser 层**，经批注解析回调（comments part 查找）；标注文本其余部分不解析——完整 XPT 转换属 WI9 统一出口。嵌套表不在模型承载粒度内（见 T2）。
- 超链接模板语义覆盖核对：`expr:`→valueExpr、`tpl_expr:`/`xpl:`→templateExpr 三形态在 OfficeDocModelParser 链路的测试覆盖补全；`linkExpr` 无生产者如实登记 Deferred（不强行造语义）。
- 标注解析单测（新语料样本 + 断言）。

## Non-Goals

- 完整 XptTableModel 转换/编译（WI9）；动态展开消费（WI7）；生成端（WI5）；生成端渲染 renderType 的消费（WI7）。
- 旧链路（WordTemplateParser/XptWordTemplateParser）行为零改动。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：doc.xdef 中 OfficeListTemplateModel 节点扩展（loop 属性与子节点，对齐 page.model 形态）。
- `nop-format/nop-office-doc-model`：precompile 重生成。
- `nop-format/nop-ooxml/nop-ooxml-docx`：OfficeDocModelParser（applyListTemplateBinding loop 迁移 + xpt:table 识别）+ 测试 + 语料新样本。
- owner doc / 设计文档决策记录 / roadmap 勾选。

### Out Of Scope

- 见 Non-Goals；另：nop-report-docx 零改动。

## Execution Plan

### Phase 1 - 决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（§16 追加）：
  - T1 块级 loop 承载：paragraph/title/list 三类块级模板模型均增 loopVarName/loopIndexName/loopItemsName 属性 + beginLoop 子节点（items 表达式值 action；**无 endLoop**——BlockTemplateBinding 无该字段，end 标记仅用于配对）；三个 apply*TemplateBinding 迁移 loop 字段。语义边界裁定：binding 落在单个段落节点（beginBlock==endBlock）；列表场景下 c:for 所在 item 聚合进 OfficeListModel 后，loop 落 list.model 表示**重复整个列表块**（与既有 c:if 合并行为一致；list 含多 item 而 c:for 仅在中间 item 的细粒度「重复该 item」语义不支持，登记 Deferred）。此为 WI3 Deferred（c:for 列表循环）的 successor 落地，同时修复段落级单块 c:for 静默丢弃。
  - T2 xpt:table 模型化边界：识别落 **WordTableParser 层**，仅对 doc 模型中的顶层 WordTable 挂标记——WordTableParser.parseCell 把单元格内嵌套 `w:tbl` 经 WordXmlHelper.getText 拍平为文本，模型链路不存在嵌套 WordTable 对象可挂 renderType（旧链路 forEachNode 能标记嵌套表是因为直接在 XNode 层替换；此识别面差距由模型扁平化所致，登记 Deferred）。WordTableParser 增批注解析回调（`Function<String,String>` commentId→批注文本，由 BlockBuilder 以 `pkg.getComments()` 注入；未注入时不识别，行为不变），表格首行首单元格 commentReference 批注含标记 → `table.makeModel().setRenderType("xpt")`。标记字面量 `"xpt:table"` 在 DocxConstants 侧定义（nop-ooxml-docx 不可反向依赖 nop-report-docx 的 ReportDocxConstants.VAR_XPT_TABLE）。降级口径：`pkg.getComments()==null` 静默通过（与旧链路一致，不污染 skips）；仅「首单元格存在 commentReference 但 id 解析不到批注」记 skip。批注其余文本不解析（dataExpr 填充与消费属 WI7/WI9）。
  - T3 超链接语义：`expr:`→valueExpr、`tpl_expr:`/`xpl:`→templateExpr 三形态断言覆盖 OfficeDocModelParser 产出的 run model 字段；`linkExpr` 在解析链路无生产者（applyHyperlinkModel 不 set）——裁定不强行造语义，登记 Deferred（需求来源出现时再定义标注形态）。模型链路对 `w:commentRangeStart/End`/`w:commentReference` 内联标签作为已知可忽略元素处理（不记未知元素 skip；旧链路以 c:ignore 处理）。
- [x] check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 设计文档含 T1–T3 决策及拒绝理由。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现

Status: completed
Targets: `nop-kernel/nop-xdefs/.../office/doc.xdef`, `nop-format/nop-office-doc-model`, `nop-format/nop-ooxml/nop-ooxml-docx`

- Item Types: `Feature`

- [x] doc.xdef：OfficeParagraphTemplateModel/OfficeTitleTemplateModel/OfficeListTemplateModel 三者扩 loop 字段（loopVarName/loopIndexName/loopItemsName 属性 + beginLoop 子节点）；`./mvnw install -pl nop-kernel/nop-xdefs -DskipTests` + `./mvnw generate-sources -pl nop-format/nop-office-doc-model -am` 重生成。
- [x] applyBlockTemplateBinding（段落）/applyTitleTemplateBinding/applyListTemplateBinding：迁移 binding.loopVarName/loopIndexName/loopItemsName/beginLoop 到对应 model。
- [x] WordTableParser 增批注解析回调 + parseTable 识别 xpt:table（顶层表）→ makeModel().setRenderType("xpt")；BlockBuilder.addTable 注入 `pkg.getComments()` 查找；DocxConstants 增标记字面量；降级口径按 T2（comments==null 静默；commentReference 悬空记 skip）。
- [x] parseInlineContent 将 `w:commentRangeStart`/`w:commentRangeEnd`/`w:commentReference` 列入已知可忽略内联元素（不记未知元素 skip）。
- [x] 移除 applyListTemplateBinding 中「c:for 无承载」的注释与偏差说明（设计文档 §15.2 偏差登记改为已落地）。

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 全绿（既有 94 用例零回归）。
- [x] `_gen` 生成物由 precompile 产出，无手改。
- [x] 无静默跳过：commentReference 悬空路径有 skip 记录；段落级/列表级 c:for 不再被丢弃（loop 字段落 model）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 标注解析测试

Status: completed
Targets: `nop-format/nop-ooxml/nop-ooxml-docx/src/test/java/io/nop/ooxml/docx/`

- Item Types: `Proof`

- [x] 语料新样本 `templateAnnotationDoc`（DocxCorpusBuilder）：含 c:if 段落、c:for 普通段落（loop 落 paragraph.model）、c:for 列表项（loop 落 list.model）、xpt:table 批注表格（顶层；另含一个「批注仅在嵌套表内」的外层表作负向样本）、expr:/xpl:/tpl_expr: 超链接各一。模板超链接合成形态对齐真实 docx：关系 `Target="xpl:%3c...%3e"`（percent-encode）/`Target="expr:..."` + `TargetMode="External"`（实测 test-tpl.docx/test-link-expr.docx）。builder 扩展 comments 部件：word/comments.xml + Content-Type `application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml` + 关系 + document.xml 内 commentRangeStart/End + commentReference（固定 id、确定性）。
- [x] `TestTemplateAnnotationParsing`（或扩展 TestDocxCorpusParsing）：断言段落 testExpr 落 paragraph.model、段落 loop 字段落 paragraph.model、列表 loop 字段落 list.model、顶层表 table.model.renderType="xpt"、嵌套表内批注不使外层表被标记（负向断言）、run model 的 valueExpr（expr:）/templateExpr（tpl_expr:/xpl:）三形态；断言批注内联标签不产生未知元素 skip。
- [x] 容错：commentReference 悬空（id 无对应批注）→ 不标记 + skip 断言；无 comments part 的普通表格文档 → 不标记且 skips 不被污染（断言既有样本 skips 行为不变）。

Exit Criteria:

- [x] 标注解析测试覆盖 c:if/c:for/三形态超链接/xpt:table 四类标注（接线验证：真实 docx → parser → 内嵌 model 字段断言）。
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 全绿。
- [x] owner doc 能力矩阵模板标注行更新；`ai-dev/logs/` 已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI4 勾选 + Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿。
- [x] 生成物纪律：git diff 无手改 `_gen`/`_` 前缀文件。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-format/nop-ooxml/nop-ooxml-docx --severity high 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

### linkExpr 标注形态

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: run/link model 的 linkExpr 字段在 xdef 存在但解析链路无生产者，也无既有 docx 标注形态定义；强行造语义违背「对齐既有模板机制」原则。
- Successor Required: `no`
- Successor Path: —（需求来源出现时再立项）

### 列表内细粒度「重复单个 item」的 c:for 语义

- Classification: `watch-only residual`
- Why Not Blocking Closure: binding 落单段落节点，聚合后 loop 语义=重复整个列表块（与 c:if 合并行为一致）；仅重复中间某个 item 的语义需要 item 级模板模型，属模型扩展。
- Successor Required: `no`
- Successor Path: —（WI7 动态展开落地时按需求评估）

### 表级 c:for loop 语义（行循环）

- Classification: `watch-only residual`（audit 发现的 WI3 同族静默丢弃；本次已加具名 skip，loop 语义本身未承载）
- Why Not Blocking Closure: WordTableTemplateModel 无 loop 字段（row-loop 展开语义属 nop-report-docx 的 XPT 链路，统一出口在 WI9）；现在锚定表格行的 c:for 会具名 skip，不再静默。
- Successor Required: `yes`
- Successor Path: WI9 统一出口（xpt:table → XPT 编译链路时按 renderType="xpt" 接管行循环）。

### 嵌套表的 xpt:table 识别

- Classification: `watch-only residual`
- Why Not Blocking Closure: doc 模型把单元格内嵌套表拍平为文本，无嵌套 WordTable 对象承载 renderType；旧链路（XNode 层）仍可处理嵌套 xpt 表，模型链路的识别面差距不影响顶层表主路径。
- Successor Required: `yes`
- Successor Path: WI7/WI9（动态展开/编译链路需要嵌套表时再扩单元格内容模型）。

### xpt:table 批注的完整语义解析（数据集名等）

- Classification: `watch-only residual`
- Why Not Blocking Closure: renderType="xpt" 标记已闭环「识别+内嵌」；批注文本的进一步解析（数据集名→dataExpr）无消费方（WI7 数据绑定/WI9 编译链路落地时按需定义格式）。
- Successor Required: `yes`
- Successor Path: WI7/WI9。

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: WI4 三 Phase 完成：三类块级模板模型增 loop 承载并迁移 c:for（修复段落级与列表级静默丢弃）；xpt:table 顶层表识别落 WordTableParser 层（renderType=xpt，自有常量，悬空引用记 skip）；批注内联标签列为已知可忽略、纯批注标记 run 跳过；TestTemplateAnnotationParsing 9 用例值级断言（表达式实际求值）。docx 79 + doc-model 25 全绿，既有断言零修改，视觉 golden 无漂移。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_696ff6f2-d598-4b08-8bd1-8422da375edd）
- Audit Session: agent_696ff6f2-d598-4b08-8bd1-8422da375edd
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 §16 三条决策与实现一致）；Phase 2 PASS（xdef 三 model loop 字段 + 三个 `_gen` diff 纯 codegen 输出；applyLoopBinding 三路径迁移与 page 级一致；WordTableParser 识别落点/排除嵌套子树/未注入行为不变/comments==null 静默/悬空记 skip，与旧链路 isXptTable 取法对照一致；DocxConstants 自有常量无反向依赖）；Phase 3 PASS（TestTemplateAnnotationParsing 10 用例值级断言，负向断言有区分力，语料形态与实解包 test-tpl.docx 一致）。
  - Closure Gates：`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 实跑 exit 0（docx 81 + doc-model 25，Skipped=0，既有断言零修改）；check-doc-links --strict exit 0；scan-hollow 0 发现；scope 纪律（output//xlib/WordTemplateParser/nop-report-docx 零改动）；roadmap WI4 勾选 + Last updated；日志条目一致。
  - Anti-Hollow：标注解析被语料测试真实驱动（表达式实际求值：testExpr 正反求值、beginLoop 求值截断列表、valueExpr/templateExpr 渲染输出）；无空方法体/no-op。
  - Audit fixes（APPROVED-WITH-FIX 已落实）：① 必修——表级 c:for（begin/end 锚定表格行归并为 w:tbl）的 loop/visibleExpr 静默丢弃：WordTable 分支补具名 recordSkip + Deferred 登记（successor WI9，row-loop 属 XPT 链路语义）；② 建议随修——xpl 完整标签 run 补 templateExpr 求值形态断言、新增 titleLoopDoc 语料 + title loop 用例（复合 items 表达式→beginLoop 值 action 的语义在测试中固化）。审计建议中「多 binding loop 覆盖/混合态记 skip」「空批注文案」「块级 commentRangeStart 口径」为非阻塞 watch-only，登记于本段不另开 Deferred 项。
  - Deferred 项分类检查：五项 Deferred（linkExpr/列表粒度/嵌套表识别/批注完整语义/表级 c:for loop）均真实 out-of-scope 且带 successor，无 in-scope live defect 降级。
