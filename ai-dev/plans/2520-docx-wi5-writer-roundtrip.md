# 2520 docx roadmap WI5：docx 生成完善与 round-trip 语义等价

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI5）+ docs-for-ai/02-core-guides/word-document-model.md §6 生成契约 + WI3 解析面（对偶）
> Related: ai-dev/plans/2518（解析）、2519（模板标注）

## Purpose

`OfficeDocModelWriter`/`OfficeDocXmlBuilder` 覆盖 WI1 定稿的全部已建模能力（八类块、四类行内、run 全样式、表格边框/底纹/单元格样式、多节页眉页脚、每节页面设置、图片媒体部件、styles.xml 生成、背景），并交付 roadmap Cross-Cutting 红线验收：`docx → 模型 → docx → 模型` 语义等价测试。

## Current Baseline

- 生成端现状：`OfficeDocXmlBuilder.buildBlock` 仅段落+表格；`buildDocumentXml` 单节（header1/footer1 硬编码、内容取 pages.get(0)）；pgSz 有、pgMar 无；无 list/title/image/hyperlink 块/separator/pageBreak 生成；run 仅 text（WI1 起 buildRun(OfficeRun) 对非 text run fail-fast）；`docx-gen.xlib` 无图片/分页等标签。
- 解析面（WI3/WI4 已落地，对偶目标）：八类块/四类行内/rPr 全样式/表格边框底纹内联样式（`__cell_t<n>_r_c`）/每节 sectPr+pgMar/styles.xml 五类映射/xpt:table→renderType。
- `WordOfficePackage`：addFile 部件机制；`OfficeDocModelWriter` 组装 document.xml/styles/header/footer。
- round-trip 现状：仅 1 个旧用例（语义等价口径未建立）。
- 语料：DocxCorpusBuilder 15 个确定性样本（含 WI4 的 templateAnnotationDoc/danglingCommentReferenceDoc/titleLoopDoc）+ 视觉验收工具（WI0）。

## Goals

- buildBlock 八类分派生成；run 四类行内生成（移除 fail-fast 占位）；run 全样式 rPr 生成（粗斜下删高上下标/字体/字号/颜色）。
- 表格生成补边框/底纹/单元格样式引用（cell.styleId→tcPr 或内联 tcPr，裁定见 Phase 1）+ 行高 hRule。
- 多节生成：每节 sectPr（pgSz/pgMar/headerReference/footerReference 独立部件 headerN/footerN）。
- 图片生成：drawing + 关系 + ContentTypes + 媒体部件（从 doc.assets 取）。
- styles.xml 生成（doc.styles 五类 → w:style + basedOn/default）；pStyle→段落 pStyle。
- 背景：doc 模型增 background 字段（xdef 扩展）+ 解析接线（WI3 残余）+ 生成（w:background + settings displayBackgroundShape）。水印（header 内 VML）解析已保留、生成经 header 块自然生成，不单独处理。
- round-trip 语义等价测试：语料全样本 `docx→模型→docx→模型` 双向断言。

## Non-Goals

- 动态模板展开消费（WI7）；PDF（WI6）；renderType=xpt 的表格生成特殊化（生成标准表格结构，xpt 语义由 WI9 编译链路接管）。
- 旧链路（WordTemplateParser/XptWordTemplateParser）与 output 包以外的零改动面维持。
- 完整字节级保真（语义等价而非字节等价——红线口径）。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：doc.xdef 增 doc 级 background 属性（OfficeDocModel）。
- `nop-format/nop-office-doc-model`：precompile 重生成。
- `nop-format/nop-ooxml/nop-ooxml-docx`：OfficeDocModelWriter/OfficeDocXmlBuilder/（必要时）docx-gen.xlib + DocxConstants；解析端 background 接线（OfficeDocModelParser）。
- round-trip 测试 + 语料背景样本扩展。
- owner doc / 设计文档决策 / roadmap / 日志。

### Out Of Scope

- 见 Non-Goals；另：WordTemplateParser 等旧链路；nop-report-docx。

## Execution Plan

### Phase 1 - 生成层决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（§17 追加）：
  - G1 单元格样式生成口径：内联 WordCellStyle（解析期 `__cell_*` 自动注册）生成时内联回 tcPr（tblBorders/tcBorders/shd/vAlign），**不回写 styles.xml 的 w:style**——内联样式是解析期合成物，round-trip 中不作为命名样式持久化；命名样式（用户定义）才进 styles.xml。内联判定用 id `__` 前缀约定，**解析端补校验：用户 styles.xml 中 styleId 以 `__` 开头时报错**（防撞名）。拒绝：全部回写 styles.xml（污染命名样式空间）。
  - G2 round-trip 语义等价口径：比较第二次解析的模型与第一次模型的结构等价——断言经**类型化比较 helper**（按块类型注册的比较器：p/title 比文本+样式字段集、list 比 numId+item 序列、table 比行列/合并/列宽行高/样式引用、image 比 assetRef+宽高、hyperlink 比 href+runs、separator/pageBreak 比 type；页面设置比宽高/方向/边距；多节比 page 数+各节 header/footer 文本），不做字节等价。全样本（15 个，含 danglingCommentReferenceDoc）逐个断言。已知不可 round-trip 项显式登记豁免清单：批注（模型仅表格 comment 字段往返，块级批注 Deferred）、非独立图片段落（解析 skip）、水印 VML 形态（模型不承载原始 XNode；该样本断言 header 块存在但不比对 VML）、命名样式字体细节（WordParagraphStyle 无 font 字段）、背景色值精度。
  - G3 多节生成：每节 sectPr 内嵌节末段落 pPr + 末节 body 级 sectPr（与解析的节边界语义对偶）；header/footer 每节独立部件（headerN/footerN 递增编号）。
  - G4 背景字段：doc 级 `background`（颜色字符串，xdef 属性）；生成 `w:background` + settings 部件（writer 现无 settings.xml——需从零新建部件 + ContentTypes Override + document.xml.rels 关系，属 writer 结构新增）；解析接线补 WI3 残余（w:background 读取）。
  - G5 不可生成形态 fail-fast：builder 对未识别的块类型**抛 DocxErrors 显式错误**（替换现有未知块返回空 `w:p` 的静默行为）；text run 内 br/tab（解析期具名 skip 的形态）生成端不合成——生成以模型为准（模型不含该形态）。
- [x] check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 设计文档含 G1–G5 决策及拒绝理由。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 生成端实现

Status: completed
Targets: `nop-format/nop-ooxml/nop-ooxml-docx/src/main/java/io/nop/ooxml/docx/`

- Item Types: `Feature`

- [x] doc.xdef background 字段 + 重生成 + 解析接线（OfficeDocModelParser 读 w:background + settings）。
- [x] buildBlock 八类分派：title（pStyle+titleLevel+runs）、list（numPr 逐 item：ilvl/numId + runs）、image（drawing inline + r:embed 关系 + extent EMU）、hyperlink 块（w:hyperlink + 外部关系）、separator（pBdr 空段）、pageBreak（单 br page 段）、p/table 既有路径保持。
- [x] 段落样式回生成：jc/spacing/leftIndent/rightIndent/firstLineIndent/outlineLvl→titleLevel（对齐解析面，simpleDocument 缩进样本 round-trip 必需）。
- [x] run 生成：tab/br/link 分派（替换 WI1 的 fail-fast）；rPr 全样式（含 vertAlign/strike/highlight/rFonts/sz/color——对齐解析面字段）。
- [x] 未知块分派 fail-fast（G5）：builder 未知块抛 DocxErrors（替换返回空 w:p 的现状）。
- [x] 表格：tblBorders/tcBorders/shd/vAlign/trHeight（hRule=atLeast）/cell.styleId→tcPr；tblGrid 列宽。
- [x] 多节：每节独立 sectPr + headerN/footerN 部件 + 关系；每节 pgSz/pgMar。
- [x] 图片部件：media/imageN.{ext} 写入 + [Content_Types] Override/Default + document.xml.rels 关系（从 doc.assets）。
- [x] styles.xml 生成：五类 w:style（pStyle/rStyle/tbl/num 映射）+ basedOn + w:default；段落/标题 pStyle 引用（标题→HeadingN/Title 映射裁定）。
- [x] 背景：w:background 生成 + settings 部件从零新建（displayBackgroundShape + ContentTypes Override + 关系）；图片部件命名对齐 WordOfficePackage.addImage 现有语义（其前缀索引从 2 起——裁定沿用或统一，落实现报告）。

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 全绿（基线以实现期 mvn 实跑输出为准的模块级全绿口径；writer 既有 15 用例语义不变）。
- [x] `_gen` 生成物由 precompile 产出，无手改。
- [x] 无静默跳过：不可生成的模型形态（如 assets 缺失的 assetRef）显式报错（DocxErrors）而非空输出。
- [x] 实现期裁定（标题 pStyle 映射、样式回写细节）落设计文档 §18。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - round-trip 语义等价测试

Status: completed
Targets: `nop-format/nop-ooxml/nop-ooxml-docx/src/test/java/io/nop/ooxml/docx/`

- Item Types: `Proof`

- [x] `TestDocxRoundTrip`：语料全样本（15 个，含 templateAnnotationDoc 等 WI4 样本）`docx→模型→docx→模型`，逐项断言 G2 口径（块类型序列/文本/样式值/合并/页面设置/多节 header 文本/图片 assetRef/assets 数据）；生成 docx 的包结构完整性（writer 从零组装的 ContentTypes Override 齐备度）先经 LibreOffice 视觉冒烟（选 3 样本）验证容忍度再定豁免口径。
- [x] 豁免清单逐项落测试注释（G2 登记项不纳入断言并注明理由）。
- [x] 既有用例零回归（模块级全绿口径）；视觉 golden 不受影响（生成端改动不影响解析端语料）。

Exit Criteria:

- [x] round-trip 测试覆盖全部 15 个语料样本（红线验收：双向语义等价；豁免清单逐项注释）。
- [x] **接线验证**：生成端新能力（八类块/四类行内/多节/图片/styles/背景）各自至少经一次 round-trip 断言证明可用。
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 全绿。
- [x] owner doc 生成契约与能力矩阵更新；`ai-dev/logs/` 已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI5 勾选 + Last updated 同步 + M2 milestone（docx 双向转换可用）勾选（WI3–WI5 完成派生）。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿。
- [x] 生成物纪律：git diff 无手改 `_gen`/`_` 前缀文件。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-format/nop-ooxml/nop-ooxml-docx --severity high 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

### numbering.xml 合成（列表编号定义）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 生成的列表段落带 numPr 引用但包内无 numbering.xml，列表字形在 Word/LibreOffice 中不显示；round-trip 语义等价不受影响（numId/level 保持）。WordListStyle 的 numFmt/lvlText/start 在 styles.xml 无合法位置。
- Successor Required: `yes`
- Successor Path: WI11 端到端验收前补（或 WI6 PDF 渲染需要列表字形时）。

### 模板语义与 renderType 回写

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: plan Non-Goals——模板标注回写 docx 属 WI7/WI9 统一出口；round-trip 测试对该面显式豁免。
- Successor Required: `yes`
- Successor Path: WI9。

### 水印生成特化

- Classification: `watch-only residual`
- Why Not Blocking Closure: 水印在模型中是 header 内 VML 块（解析保留）；生成端按 header 块自然生成任意内容，VML 水印的专用合成 API 无独立需求。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- 命名样式的 styles.xml 往返保真细节（字体字段等）随消费需求补。

## Closure

Status Note: WI5 三 Phase 完成：生成端重写覆盖八类块/四类行内/run 与段落全样式/表格边框底纹/多节 sectPr 与 headerN/footerN/图片媒体部件/styles.xml/背景（settings 部件从零新建）；未知块 fail-fast；doc.xdef 增 background + 解析接线；解析端补 `__` 保留前缀校验（G1）。TestDocxRoundTrip 17 用例（15 语料样本双向语义等价 + 生成物 LibreOffice 冒烟 + 保留前缀校验），docx 模块 97 用例全绿，既有用例零修改。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_b1540edd-866f-4720-9661-768599afc917）
- Audit Session: agent_b1540edd-866f-4720-9661-768599afc917
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 §17 五条决策与实现一致）；Phase 2 PASS（八类块/四类行内生成、未知块抛 DocxErrors 替换空段落、多节 sectPr + headerN/footerN、styles.xml 五类 + basedOn/default、内联 `__cell_*` 回 tcPr、background + settings 部件、解析端保留前缀校验）；Phase 3 PASS（TestDocxRoundTrip 17 用例：15 语料样本全覆盖，类型化比较分派，抽查 multiSectionDoc 两节 + header 文本、imageDoc ByteString 数据、fullRunStyleDoc 逐样式字段）。
  - Closure Gates：`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 独立实跑 exit 0（docx 97、doc-model 25，Skipped=0，视觉 golden 无漂移）；TestOfficeDocModelWriter git diff 为空（既有 15 用例零改动）；check-doc-links --strict exit 0；scan-hollow 0 发现；scope（parse/ 仅 background 接线 + 保留前缀校验两处；WordTemplateParser/nop-report-docx/docx-gen.xlib 零改动）。
  - Anti-Hollow：每项生成新能力经「生成→再解析→断言」真实驱动；未知块/run、缺 asset、缺 numId、titleLevel 越界均 fail-fast 且错误码有测试覆盖。
  - Audit fixes（APPROVED-WITH-FIX，四项均为文档/簿记，已落实）：roadmap WI5 与两个已达成的 Milestone 勾选 + Last updated；owner doc 能力矩阵两处自相矛盾行收敛；source-anchors WDM-003/004/005 过时描述更新；本 evidence 写入。
  - Deferred 项分类检查：numbering.xml 合成（watch-only，successor WI11/WI6）、模板语义与 renderType 回写（out-of-scope，successor WI9）、水印生成特化（watch-only）分类诚实，无 in-scope 缺陷降级。
