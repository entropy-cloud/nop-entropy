# 2518 docx roadmap WI3：docx 解析完善

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI3）+ docs-for-ai/02-core-guides/word-document-model.md §5 解析契约 + WI0 语料（DocxCorpusBuilder 11 样本）
> Related: ai-dev/plans/2516-docx-wi1-structure-model.md、2517-docx-wi2-style-system.md

## Purpose

docx 全结构可靠解析为 WI1 定稿的模型：八类块、四类行内 run、run 全样式、列表编号、图片（关系/绘图锚点/媒体部件）、超链接块、表格细节（边框/底纹/单元格横向对齐）、多节页眉页脚、页面设置（page 级宽高/边距）、背景/水印、命名样式（styles.xml→styles 容器）；残缺 docx 容错。含解析单测 + WI0 语料对版。

## Current Baseline

- `OfficeDocModelParser`（845 行）：parseBlocks 只分派 `w:p`/`w:tbl`/`w:hyperlink`（hyperlink 解包为内部 runs）；段落内只处理 w:r 与 w:hyperlink；页眉页脚经 `w:headerReference`/`w:footerReference` 收集相关部件但**单节**（collectSectPrNodes 全部收集、parseRelatedParts 遍历所有 sectPr 引用堆到一个 page）；applyPageSize/applyPageOrientation 只读最后一个 sectPr 且写到 doc 级；无 pgMar；无 list/title/image/separator/pageBreak 块；run 解析无 tab/br（丢失）。
- `WordTableParser` 已支持：横纵合并/列宽/行高（trHeight）/单元格值/批注；缺边框/底纹/单元格横向对齐。
- 模型侧已定稿（WI1/WI2）：八类块 + runs 包装节点四类行内 + page 级 width/height/margins + doc 级 assets/styles（五类样式 + basedOn 继承 + OfficeStyleResolver）。
- WI0 语料 `DocxCorpusBuilder` 11 个确定性样本覆盖全部目标结构；`DocxVisualVerifier` 可证样本 LibreOffice 可打开。
- styles.xml 解析：现有 `WordStylesPart`（OOXML 包层）有样式继承 resolveInheritance——但只服务 WordTemplate 旧链路，未接入 doc 模型。

## Goals

- parseBlocks 分派八类块：`w:p`→段落（含 runs 包装节点语义）、`w:tbl`→表格、标题（pStyle Heading*/Title→titleLevel 识别为 OfficeTitleModel）、`w:numPr` 列表（连续段落聚合为 OfficeListModel，numId+level）、`w:drawing`→OfficeImageModel（关系解析+媒体部件→assets）、块级 hyperlink→OfficeHyperlinkModel（保留嵌套 runs）、page break 段落（`w:br type="page"` 独立段或 run）→OfficePageBreakModel、pBdr 分隔线段落→OfficeSeparatorModel。
- run 解析产出四类行内（text/tab/br/link），run 全样式（粗斜下删高上下标/字体/字号/颜色/高亮）→ WordRunStyle/OfficeFont。
- 多节：每个 sectPr → 一个 OfficeDocPageModel（内嵌 sectPr 的段落为节边界），各节独立 header/footer 引用；page 级 width/height/orientation/pgMar→OfficePageMargins。
- styles.xml → doc 模型 styles 容器（五类映射 + basedOn/default + 命名样式挂接：段落 pStyle→styleId 引用）。
- 背景（w:background + settings displayBackgroundShape→doc 属性）与页眉 VML 水印（识别保留在 header 块中即可）。
- 容错：残缺部件/缺失关系降级为空结构 + 日志，不抛原始 OOXML 异常；未知块/run 子元素跳过并计数（汇总日志一次）。
- 单测：每类结构解析断言 + WI0 语料 11 样本全量对版 + 容错用例。

## Non-Goals

- 生成端（OfficeDocXmlBuilder/Writer）与 docx-gen.xlib 零改动（WI5）。
- 模板标注新语义（WI4）——现有 if:/for:/expr: 语义保持不变。
- 表格展开、PDF、图表绑定。

## Scope

### In Scope

- `nop-format/nop-ooxml/nop-ooxml-docx`：OfficeDocModelParser 扩展 + WordTableParser 补边框/底纹/横向对齐 +（如需）新 parse helper 类（parse 子包内）。
- 测试：新增解析测试类组 + 语料对版测试；`_tmp` 视觉冒烟保持。
- owner doc 能力矩阵更新（解析列翻转）；设计文档解析层决策（列表聚合规则、标题识别映射、多节边界语义）。

### Out Of Scope

- 生成端改动；WordTemplate/XptWordTemplateParser 旧链路行为变化；WordStylesPart 旧链路改动（新增 styles.xml→doc 模型解析路径并行，不改旧类）。

## Execution Plan

### Phase 1 - 解析层决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（§15 追加）：
  - P1 标题识别与 pStyle 引用：`w:pStyle` val 为 Heading1-9/Title 的段落 → OfficeTitleModel（titleLevel 1-9；Title→1）；正文样式段落仍为 OfficeParagraphModel。**pStyle 引用统一走 `paragraph.style.id`**（WordParagraphStyle.id 已承载 pStyleId，单一事实源）；删除 parser 既有遗留行为 `para.setType(pStyleId)`（OfficeDocModelParser L209-213 附近）——type 是 xdef 块分派属性（值恒为 p），写 pStyle id 会破坏 xdef round-trip。拒绝：pStyle 挂 type 字段（与 xdef 分派冲突）；保持纯 p+styleId 让消费方判断（编辑器语义需要块级标题类型）。
  - P2 列表聚合：连续带 `w:numPr` 的块级段落（相同 numId）聚合为单个 OfficeListModel（item.level 取 ilvl）；列表中断（非 numPr 段落或 numId 变化）即闭合。**模板语义迁移**：被聚合段落上的块级 binding（c:for/c:if 经 analyzeBlockTemplates 提取）迁移到 `OfficeListTemplateModel`（testExpr/beginLoop 等字段已在 xdef），applyBlockTemplateBinding 扩展支持 OfficeListModel 分派（现仅 Paragraph/WordTable）。拒绝：每段独立 list 块——语义碎片化。
  - P3 多节边界：`w:p` 的 pPr 内嵌 `w:sectPr` = 节结束标记；每个 sectPr 生成一个 OfficeDocPageModel（body 为该节累积块；末尾 body 级 sectPr 归最后一节）；各节 header/footerReference 独立解析。
  - P4 图片解析：`w:drawing` inline/anchor → OfficeImageModel{assetRef→doc.assets 既有 OfficeImageAsset 容器（name=媒体部件名, ext, data=媒体部件字节，经 OfficePackage.getFile 取部件）}，width/height 经 EMU→pt（÷12700，与既有 twipsToPoints 并列补 helper）；缺关系/缺部件降级为占位 assetRef 保留 + 日志。
  - P5 styles.xml：新解析路径直接解析 `pkg.getFile("word/styles.xml")` 的 XNode（不经 WordStylesPart——其 StyleInfo 字段集不足以承载五类样式映射），样式映射 pStyle 段落类→paragraphStyle、rStyle→runStyle、tbl→tableStyle、num→listStyle；basedOn→basedOn、w:default="1"→default；OfficeStyleResolver 直接消费；run 级内联 rPr 覆盖样式（内联优先）。
  - P7 批注范围裁定：表格批注（WordTableCell.comment）已有；**块级批注（comments.xml + w:commentRangeStart/Reference）本计划 out of scope**——模型层无块级批注承载字段，需先扩 xdef，登记 Deferred（successor WI5 扩模型时一并接线）；WordCommentsPart 旧链路不动。
  - P6 容错口径：缺失部件/关系→空结构 + warn 日志（一次性汇总计数）；XML 结构损坏→DocxErrors 显式错误码；未知子元素→跳过计数。
- [x] check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 设计文档含 P1–P6 决策及拒绝理由。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 解析器扩展实现

Status: completed
Targets: `nop-format/nop-ooxml/nop-ooxml-docx/src/main/java/io/nop/ooxml/docx/parse/`

- Item Types: `Feature`

- [x] 块分派扩展（P1/P2/P3/P4）：parseBlocks 八类分派；列表聚合器；多节切分重构（collectSectPrNodes→节边界模型）；图片解析（drawing/关系/媒体部件→assets）。
- [x] run 解析扩展：w:tab/w:br（含 page 型→独立判断）/hyperlink 保留嵌套（OfficeLinkModel + 块级 OfficeHyperlinkModel）；rPr 全样式映射（含 vertAlign superscript/subscript→OfficeFont.verticalAlign、strike→strikeout、highlight→highlightColor、rFonts/sz/color→OfficeFont）。
- [x] 页面设置：每节 pgSz（page 级 width/height/orientation）+ pgMar→OfficePageMargins；doc 级 width/height 保持首节回退值。
- [x] styles.xml → styles 容器解析（P5）；段落 pStyle → paragraph.style.id 挂接（P1 裁定：WordParagraphStyle.id 承载 pStyleId，单一事实源），并删除 parser 遗留的 para.setType(pStyleId) 赋值。
- [x] 背景/水印：w:background 颜色 → doc 模型扩展属性（xdef 若无字段则登记为 follow-up，不阻塞）；水印保留在 header 块中。
- [x] WordTableParser 补：tblBorders/tcBorders→WordCellStyle 四向 border（样式挂 cell.styleId 或内联 style——实现期按模型字段裁定）、tcPr shd→backgroundColor、vAlign 已有、横向对齐（w:jc in tcPr）落 WordCellStyle 或 cell 扩展（实现期裁定）。
- [x] 容错（P6）：缺失部件/关系降级 + 聚合日志；DocxErrors 新错误码按 error-handling.md 两档口径。

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿（既有 50 用例回归 + 新测试）。
- [x] 真实回归面 `TestOfficeDocModelParser`（19 用例）零回归——重点：`testParseHeaderFooter`（pages.size()==1 + header/footer 非空，P3 多节切分重写其路径）、`testMapPageIfToTestExpr`（page 级 binding，analyzeBlockTemplates 迁移路径）；TestWordTemplate 9 用例走 WordTemplateParser 旧链路，仅作旧链路不受影响的旁证。
- [x] 解析层每项新能力无静默跳过：缺失依赖降级路径有日志断言或显式错误码（抽查 3 条降级路径）。
- [x] 实现期裁定（表格样式挂接、背景字段）落设计文档 §15（pStyle 映射已由 P1 定死，非实现期项）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 语料对版与容错测试

Status: completed
Targets: `nop-format/nop-ooxml/nop-ooxml-docx/src/test/java/io/nop/ooxml/docx/`

- Item Types: `Proof`

- [x] `TestDocxCorpusParsing`：WI0 语料 11 样本逐个解析断言（每样本至少 3 条结构断言：块类型序列/关键属性/run 类型与样式值；多节样本断言 page 数与各节 header 内容；图片样本断言 assets 与 assetRef；列表样本断言聚合与 level；合并表格样本断言边框/底纹/合并）。
- [x] 容错用例：损坏 zip、缺失 document.xml、悬空关系（hyperlink 引用不存在 rel）、截断 XML——断言降级或显式错误码（不抛原始异常）。
- [x] 既有资源 docx（test-simple/test-header-footer/payment 等 9 个）解析回归断言保持。
- [x] 视觉冒烟保持全绿（3 样本 soffice 链路不受解析改动影响——改动的只是模型产出端）。

Exit Criteria:

- [x] 11 语料样本全量解析断言通过（接线验证：真实 docx → 新解析路径 → 类型化模型全链路）。multiSectionDoc fallback（无 headerReference 扫描）未补专门断言——已裁定为 Deferred watch-only（见 Deferred 段），本条不覆盖该子项。
- [x] 容错 4 用例通过且无静默跳过（每条断言日志/错误码证据）。
- [x] 既有 50 用例 + 视觉冒烟全绿。
- [x] owner doc 能力矩阵解析列更新（未支持→已支持翻转）；`ai-dev/logs/` 已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI3 勾选 + Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-format/nop-ooxml/nop-ooxml-docx --severity high 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

### 列表块 c:for 循环语义承载

- Classification: `watch-only residual`
- Why Not Blocking Closure: OfficeListTemplateModel 无 loop 字段（xdef 未定义），c:for 的循环信息在列表块上被丢弃——承接需先扩 list 模板模型（模型变更属 WI4/WI7 边界）；c:if 迁移已落地；解析不报错不静默损坏（语义如实降级并在设计文档 §15.2 登记偏差）。
- Successor Required: `yes`
- Successor Path: WI4 模板标注解析扩 OfficeListTemplateModel loop 字段并接线。

### multiSectionDoc fallback（无 headerReference）断言

- Classification: `watch-only residual`
- Why Not Blocking Closure: 语料样本均带 headerReference，fallback 扫描路径无专门断言；该路径由既有单节样本（test-header-footer.docx）行为覆盖 + 具名 skip 日志保障，不影响解析正确性主证据链。
- Successor Required: `no`
- Successor Path: —

### 块级批注（comments.xml + commentRangeStart/Reference 挂接）

- Classification: `out-of-scope improvement`（roadmap WI3「批注」项的部分残余；表格批注 WordTableCell.comment 已覆盖表格面）
- Why Not Blocking Closure: 模型层无块级批注承载字段，接线需先扩 xdef（属模型变更，与 WI3 解析纯扩展边界冲突）；WordCommentsPart 旧链路不受影响。
- Successor Required: `yes`
- Successor Path: WI5 生成完善扩模型时一并补块级批注字段与双向接线。

### doc 背景色的模型字段

- Classification: `watch-only residual`
- Why Not Blocking Closure: WI1 模型未定义背景字段；解析侧将 w:background 落模型需先扩 xdef（属模型变更）。水印（页眉 VML）已保留，纯背景色对 round-trip 影响小。
- Successor Required: `yes`
- Successor Path: WI5（生成完善）若需背景字段则一并扩 xdef + 解析接线。

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: WI3 三 Phase 完成：parseBlocks 八类块分派 + 列表聚合（c:if 迁移；c:for 无承载如实登记 Deferred）+ 多节切分（每 sectPr 一 page）+ run 四类行内分派 + rPr 全样式 + styles.xml 五类映射（basedOn/default）+ 图片/assets + 表格边框/底纹内联样式 + 容错三错误码 + 聚合 skip 日志；pStyle→style.id（删除遗留 setType 赋值）。测试 68/68 全绿（新增 18 语料解析用例 + 容错用例；1 处既有断言勘误 test-tpl.docx 实为两节）。独立 closure audit 待记录。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_db2a519a-3939-48ca-b24b-7f990bf905ab）
- Audit Session: agent_db2a519a-3939-48ca-b24b-7f990bf905ab
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 §15 含 P1–P7 + 实现偏差登记）；Phase 2 PASS（八类块分派/列表聚合/多节切分（每 sectPr 一 page，test-tpl.docx 两节勘误经解包核验）/run 四类 + rPr 全样式/styles.xml 直解五类映射/容错三错误码 + 聚合 skip）；Phase 3 PASS（TestDocxCorpusParsing 19 用例——11 语料样本 + 容错 + link/行高/mixed-run；既有 19+9+3+15 零回归）。
  - Closure Gates：`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 实跑 exit 0（docx 69 + office-doc-model 25 全绿）；check-doc-links --strict exit 0；scan-hollow exit 0；scope 纪律（output//xlib/xdef/WordTemplateParser 零改动）。
  - Anti-Hollow：11 语料样本全链路驱动新解析路径；容错与 skip 路径具名断言（悬空关系 skips + mixed-run w:br skip 含 "w:br" 文本断言）；无静默跳过（未知元素/未分派 br/tab 均入 skip ledger）。
  - Deferred 项分类检查：四项 Deferred（c:for 列表循环/multiSection fallback 断言/块级批注/背景色字段）均为 watch-only/out-of-scope 且带 successor，非 in-scope live defect 降级。
  - Audit fixes（APPROVED-WITH-FIX 三项已落实）：① parseRun br+文本混合 run 静默丢文本回归——加 w:t 防护 + 具名 skip + mixedBreakDoc 语料回归用例（审计发现的产品级缺陷，根因=br 分派无文本保护）；② 单元格样式 id 跨节碰撞——tableIndex 局部计数改 doc 级 globalTableSeq（KeyedList 同 id 静默覆盖 → 样式错误归属）；③ 文档矛盾两处——owner doc 页面宽高行 stale 改为每节独立口径、plan Phase 3 fallback 断言勾选与 Deferred 承认矛盾改口径收敛。Minor 建议（容错断言具名化/packge-invalid 覆盖/styles.xml 四类映射断言）随修：悬空关系 skip 具名化（mixed-run 用例含 skip 文本断言）；styles/cell 映射断言已在语料用例部分覆盖，残余登记为 watch-only。
