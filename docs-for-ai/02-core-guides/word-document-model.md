# Word 文档模型（OfficeDocModel）

> **定位**：本文件是 Nop 平台通用 Word 文档结构的 owner doc——模型分层、能力矩阵、docx 解析/生成契约、模板语义内嵌规则与范围硬边界的单一事实源。

## 1. 定位与目标

`OfficeDocModel` 是与 `nop-excel` 的 `ExcelWorkbook` 同构的通用 Word 文档中间结构：docx 解析为模型、模型生成 docx、模型渲染 PDF、模型承载模板语义。它是 Word 静态编辑与动态模板生成的统一中间层。

## 2. 模块边界

| 职责 | 模块 | 约束 |
| --- | --- | --- |
| 文档结构模型 + 内嵌模板模型 | `nop-format/nop-office-doc-model`（`io.nop.office.doc.model`） | 模型独立建设；不依赖 nop-report/nop-excel 业务模型 |
| 共享叶子模型（字体/样式/边框/颜色/对齐枚举） | `nop-format/nop-office-model`（`io.nop.office.model`） | Excel/Word 共用；只沉淀真正共享的叶子模型 |
| docx 包读写（OOXML 编解码） | `nop-format/nop-ooxml/nop-ooxml-docx`（`io.nop.ooxml.docx`） | 不重写 OOXML 编解码；docx 读写只在此模块 |
| 动态表格展开 | `nop-report/nop-report-docx`（`io.nop.report.docx`） | 复用 NopReport 展开；不在文档模型内造第二套表格展开 |

依赖方向单向：`nop-office-doc-model` → `nop-office-model`；`nop-ooxml-docx` → 两者；`nop-report-docx` → 文档模型。**禁止反向依赖**，模型层不得 import 报表或 Excel 业务类。

## 3. 模型分层

静态结构与模板语义分层承载：

- **静态结构**：`OfficeDocModel`（页面宽高 + `pages`）→ `OfficeDocPageModel`（`header`/`body`/`footer` 块列表 + `orientation`）→ 块（`OfficeBlock` 标记接口，经 doc.xdef 的 `xdef:bean-tag-prop="type"` 按标签分派）→ 行内 `OfficeRunModel`。
- **内嵌模板语义**：各层节点的 `model` 子节点——`doc.model → OfficeDocTemplateModel`、`page.model → OfficeDocPageTemplateModel`、`p.model → OfficeParagraphTemplateModel`、`r.model → OfficeRunTemplateModel`、`table/col/row/cell.model → WordTable*TemplateModel`。字段含 `testExpr`/`loopItemsName`/`valueExpr`/`formatExpr`/`linkExpr`/`templateExpr`/`beforeRender`/`afterRender`/`beforeGen`/`afterGen` 等（以 `doc.xdef` 与生成类为准）。
- **便捷方法**：各节点提供 `makeModel()`/`clone()`/`freeze()`，与 `ExcelSheet`/`ExcelCell` 体验一致。
- **产品管理字段不入模型**：模板名称、分类、发布状态、权限、预览缓存留在模型之外的应用层。

## 4. 能力矩阵（当前实现状态）

下表为 live code 的当前事实（锚点见 `04-reference/source-anchors.md` WDM 条目）。演进状态不在此记录。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 页面宽高/方向 | 解析+生成已支持；模型支持 page 级覆盖 | doc 级 `pgSz` 的 w/h/orient（解析取最后一个 `w:sectPr`）；page 级 width/height 字段模型已定稿、读写未接线 |
| 页边距 | 模型已定稿，解析/生成未接线 | `OfficePageMargins`（page 级，twips 口径）；`pgMar` 解析与生成未实现 |
| 页眉/页脚 | 单节已支持 | 解析经 `w:headerReference`/`w:footerReference` 收集相关部件；生成仅写 `header1.xml`/`footer1.xml`，内容取 `pages.get(0)` |
| 多节 section | 模型已定稿，读写未支持 | `pages` 列表 + page 级宽高/边距；解析只创建单个 page；生成只写首节 |
| 块级：段落/表格 | 已支持 | doc.xdef body 八类块分派（p/table/title/list/image/hyperlink/separator/pageBreak）中的两类，解析/生成已接线 |
| 块级：标题/列表/图片/超链接块/分隔线/分页 | 模型已定稿，解析/生成未接线 | `OfficeTitleModel`/`OfficeListModel`/`OfficeImageModel`/`OfficeHyperlinkModel`/`OfficeSeparatorModel`/`OfficePageBreakModel`（doc.xdef 八类块分派）；`parseBlocks` 只分派 `w:p`/`w:tbl`/`w:hyperlink`，hyperlink 仅解包为内部 runs |
| 行内：文本 run | 已支持 | `OfficeRunModel.t` |
| 行内：tab/br/hyperlink 独立类型 | 模型已定稿，解析/生成未接线 | `OfficeRun` 标记接口 + runs 包装节点分派 `OfficeTabModel`/`OfficeBreakModel`/`OfficeLinkModel`（递归嵌套 runs） |
| 图片资产 | 模型已定稿，解析/生成未接线 | doc 级 `assets:OfficeImageAsset{name,ext,data}`（`base64-bytes`/ByteString） |
| run 样式 | 部分已支持 | `WordRunStyle`（`OfficeFont` 粗/斜/删/下划线/上下标/字体/字号/颜色 + `highlightColor`）；上下标/删除线/字符间距等以 `OfficeFont`/`WordRunStyle` 字段为准 |
| 段落样式 | 部分已支持 | `WordParagraphStyle`（`align`/`firstLineIndent`/`leftIndent`/行距/`titleLevel` 0-8 等） |
| 表格：横纵合并/列宽/行高 | 已支持 | `WordTableCell.mergeAcross/mergeDown`、`WordTableColumnConfig`；行高经 `w:trHeight` 解析（`WordTableParser`）与生成（`OfficeDocXmlBuilder`） |
| 表格：边框/底纹/单元格背景 | 未支持 | |
| 图片/绘图/媒体部件 | 未支持 | |
| 背景/水印 | 未支持 | |
| 批注 | 部分 | `WordTableCell.comment` 承载单元格批注字符串 |
| 命名样式/默认样式/基样式继承 | 模型已定稿，styles.xml 解析/生成未接线 | doc 级 `styles` 容器五类样式分派（run/paragraph/cell/table/list）；`basedOn` 链合并 + `default` 回退经 `OfficeStyleResolver`（样式往返测试覆盖）；`WordListStyle` 经 `OfficeListModel.styleId` 引用 |
| 模板标注：`c:if`/`c:for` 段落标记 | 已支持 | 解析映射为 `testExpr`/loop 字段并含嵌套合并（`OfficeDocModelParser` test/for 表达式编译路径） |
| 模板标注：`expr:`/`xpl:` 超链接 | 已支持 | `WordTemplateParser`/`WordHyperlinkTransformer` |
| 模板标注：`xpt:table` 批注 | 经 `nop-report-docx` | `XptWordTemplateParser`/`XptWordTableParser` |
| 生成端块分派 | 仅段落+表格 | `OfficeDocXmlBuilder.buildBlock` 只支持 `OfficeParagraphModel` + `WordTable` |
| PDF 输出 | 无 | 模型→PDF 渲染器不存在；不经 Aspose/OnlyOffice |

## 5. docx 解析契约

- 入口：`OfficeDocModelParser`（`parse/`）从 `WordOfficePackage` 读 OOXML 主文档部件（document part），产出 `OfficeDocModel`。
- 块分派只认 `w:p`/`w:tbl`/`w:hyperlink`；`w:sectPr` 用于页眉页脚部件引用与页面设置收集。
- 模板语义在解析期编译：段落 `test`/`for` 标注编译为 `IEvalPredicate`/loop 绑定并内嵌到对应层 `model`；超链接 `expr:`/`xpl:` 语义由 `WordTemplateParser` 承载。
- 解析必须对残缺/非标准 docx 容错（缺失部件降级为空结构，不抛原始 OOXML 异常给上层）；错误码走 `DocxErrors`。

## 6. docx 生成契约

- 入口：`OfficeDocModelWriter` + `OfficeDocXmlBuilder`（`output/`），产出 `WordOfficePackage` → 标准 `.docx`。
- 生成块分派当前仅支持段落与表格；run 样式/对齐/间距/字体/下划线/高亮、表格横纵合并/列宽、页面设置（宽高/方向）、单节页眉页脚已覆盖。
- 扩展块类型（图片/列表/分页等）必须走 `docx-gen.xlib` 标签库扩展 + `buildBlock` 分派扩展，不绕开 builder。
- round-trip 红线：任何读写改动以 `docx → 模型 → docx → 模型` 语义等价断言验收（文本/结构/样式关键属性一致，非字节一致）。

## 7. 模板语义内嵌规则

模板语义**内嵌在文档模型节点上**，不引入外挂 settings/JSON 配置对象（对齐 `workbook.xdef` 在 workbook/sheet/row/cell 内嵌 `<model>` 的做法）：

1. `doc.model` 承载文档级模板行为（importLibs、beforeGen/afterGen、dump 等）。
2. `page.model` 承载节/页面级 testExpr、循环与生命周期钩子。
3. `run.model` 承载 `expr`/`tpl_expr`/样式表达式；`table/row/cell.model` 对齐 `excel-table.xdef` 的 XptRowModel/XptCellModel 做法。
4. 保留高级逃生口：节点级扩展属性、内嵌原始 `xpl` 片段、对无法结构化表达的 OOXML 细节保留原始 `XNode`。
5. 图片替换走「图片占位 + 绑定定义 + 运行时替换」；动态表格复用 `nop-report-docx`，不在文档模型内重建展开算法。

## 8. 范围硬边界

- **复用既有基础设施**：docx 读写走 `nop-ooxml-docx`；动态表格走 `nop-report-docx`；不新建第二套 Word 模板引擎。
- **不把 ExcelStyle 当 Word 样式**：Word 样式独立四类（`WordRunStyle`/`WordParagraphStyle`/`WordCellStyle`/`WordTableStyle`），共享叶子沉淀到 `nop-office-model`。
- **PDF 引擎不换**：模型→PDF 经 nop-pdf（PDFBox）；不引入 Aspose-Words/OnlyOffice。
- **不引入 springreport 代码或其闭源付费依赖**：springreport（Apache-2.0）只作能力对版参考。
- **不做前端设计器/编辑器 UI**；不做 PPT/大屏。
- **不提交大体积 docx 语料入库**：测试语料程序合成或小样本。

## 相关文档

- `03-modules/nop-report.md`（报表侧 Word 表格路径的路由位）
- `02-core-guides/reporting-and-notification-integration.md`
- 源码锚点：`04-reference/source-anchors.md` WDM 条目
