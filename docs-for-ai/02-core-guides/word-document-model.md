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
| 页面宽高/方向 | 解析+生成已支持（每节独立） | 解析：每节 `w:sectPr` 的 pgSz → page 级 width/height/orientation，doc 级取首节值；生成：每节 sectPr 独立 pgSz/pgMar |
| 页边距 | 解析+生成已支持 | `pgMar` → page 级 `OfficePageMargins`（twips→pt） |
| 页眉/页脚 | 已支持（多节） | 每节经 headerReference/footerReference 独立解析与生成（headerN.xml/footerN.xml） |
| 多节 section | 解析+生成已支持 | 每个 `w:sectPr` 一个 page（节边界=段落内嵌 sectPr）；生成每节独立 sectPr 与 headerN/footerN 部件 |
| 首页不同页眉页脚（titlePg） | 未支持 | OOXML `w:titlePg` + first 引用未建模；归属裁定：应进模型、延期补齐（对版结论文档见设计侧 springreport-alignment） |
| 块级：段落/表格 | 已支持 | 解析与生成均支持（doc.xdef body 八类块分派之二） |
| 块级：标题/列表/图片/超链接块/分隔线/分页 | 解析+生成已支持 | pStyle Heading1-9/Title→title 块；连续 numPr 聚合 list 块；独立图片段落→image 块（assets 承载媒体）；块级 hyperlink→hyperlink 块；pBdr 空段→separator；page-br 段→pageBreak。|
| 行内：文本 run | 已支持 | `OfficeRunModel.t` |
| 行内：tab/br/hyperlink 独立类型 | 解析+生成已支持 | `OfficeRun` 分派 tab/br（含 brType=page）/link（递归嵌套 runs） |
| 图片资产 | 解析+生成已支持 | `w:drawing` 关系→媒体部件字节入 doc 级 `assets:OfficeImageAsset`（base64-bytes/ByteString），assetRef=部件全路径 |
| run 样式 | 部分已支持 | `WordRunStyle`（`OfficeFont` 粗/斜/删/下划线/上下标/字体/字号/颜色 + `highlightColor`）；上下标/删除线/字符间距等以 `OfficeFont`/`WordRunStyle` 字段为准 |
| 段落样式 | 部分已支持 | `WordParagraphStyle`（`align`/`firstLineIndent`/`leftIndent`/行距/`titleLevel` 0-8 等） |
| 表格：横纵合并/列宽/行高 | 已支持 | `WordTableCell.mergeAcross/mergeDown`、`WordTableColumnConfig`；行高经 `w:trHeight` 解析（`WordTableParser`）与生成（`OfficeDocXmlBuilder`） |
| 表格：边框/底纹/单元格背景 | 解析+生成已支持 | tcPr 边框（缺省回退 tblBorders）/底纹/vAlign → 内联 WordCellStyle 注册 doc.styles（id `__cell_t<n>_<r>_<c>`）+ cell.styleId 引用；tcPr 横向对齐未解析（模型无字段，follow-up） |
| 背景/水印 | 背景已支持；水印仅解析保留 | doc 级 `background` 颜色（w:background + settings displayBackgroundShape）；水印为 header 内 VML，模型不承载原始形态，round-trip 不保真；水印归属裁定：留外部/模型不承载 VML（springreport-alignment.md） |
| 批注 | 部分 | `WordTableCell.comment` 承载单元格批注字符串 |
| 命名样式/默认样式/基样式继承 | 解析+生成已支持（styles.xml↔styles 容器） | 五类样式映射 + basedOn/default；内联 rPr 覆盖语义留给消费方经 `OfficeStyleResolver`；`WordListStyle` 经 `OfficeListModel.styleId` 引用；命名段落样式不落字体（WordParagraphStyle 无 font 字段，follow-up） |
| 模板标注：`c:if`/`c:for` 块级标记 | 已支持 | `c:if`→testExpr（嵌套合并）；`c:for`→loopVarName/loopIndexName/loopItemsName + beginLoop（items 表达式），落 page/paragraph/title/list 的 model；列表项上的 c:for 聚合后表示重复整个列表块 |
| 模板标注：`expr:`/`xpl:`/`tpl_expr:` 超链接 | 已支持 | `expr:`→run.model.valueExpr；`tpl_expr:`/`xpl:`→templateExpr；`linkExpr` 无标注形态（未使用） |
| 模板标注：`xpt:table` 批注 | 识别与展开已支持（顶层表） | 首行首单元格批注含标记 → `table.model.renderType="xpt"`；展开经 `io.nop.report.docx.expand.OfficeDocModelExpander`（单元格文本 `*=^ds!field@expr` 标记：`^` 纵向/`>` 横向/`@` 分组，与既有 Word 模板链路同语义）；嵌套表不识别 |
| 生成端块分派 | 八类块 + 四类行内 | 未识别的块类型显式报错（不输出空段落）；列表编号定义（numbering.xml）不合成，列表字形在生成文档中缺失 |
| PDF 输出 | 已支持 | `OfficeDocPdfRenderer`（`nop-report-pdf` 的 `io.nop.report.pdf.doc` 包）：流式排版（段落按行/表格按行跨页）、每节页面几何与页眉页脚、run 全装饰（粗斜含合成、下划线/删除线/高亮/上下标）、CJK 换行与字体回退、超链接 annotation、背景；列表字形有降级口径；水印 VML 与混排图片不承载不渲染 |

## 5. docx 解析契约

- 入口：`OfficeDocModelParser`（`parse/`）从 `WordOfficePackage` 读 OOXML 主文档部件（document part），产出 `OfficeDocModel`。
- 块分派只认 `w:p`/`w:tbl`/`w:hyperlink`；`w:sectPr` 用于页眉页脚部件引用与页面设置收集。
- 模板语义在解析期编译：段落 `test`/`for` 标注编译为 `IEvalPredicate`/loop 绑定并内嵌到对应层 `model`；超链接 `expr:`/`xpl:` 语义由 `WordTemplateParser` 承载。
- 解析必须对残缺/非标准 docx 容错（缺失部件降级为空结构，不抛原始 OOXML 异常给上层）；错误码走 `DocxErrors`。

## 6. docx 生成契约

- 入口：`OfficeDocModelWriter` + `OfficeDocXmlBuilder`（`output/`），从零组装 `WordOfficePackage` → 标准 `.docx`（含 styles/settings/媒体部件与完整 ContentTypes）。
- 覆盖八类块、四类行内、run/段落全样式、表格边框/底纹/合并/行高、每节页面设置与页眉页脚、图片媒体部件、命名样式、背景。
- 解析期合成的内联单元格样式（id 以 `__` 开头）生成时内联回 tcPr，不写入 styles.xml；该前缀保留，用户 styles.xml 使用会在解析时报错。
- 未识别的块类型、缺失 asset 的图片引用等不可生成形态显式报错（`DocxErrors`），不输出空结构。
- 图片部件保留解析时的部件路径（assetRef 即包内路径），不重编号。
- 模板语义（model 上的表达式）与 `renderType` 不回写到 docx；列表编号定义不合成。
- round-trip 红线：任何读写改动以 `docx → 模型 → docx → 模型` 语义等价断言验收（`TestDocxRoundTrip`，非字节一致）。已知不保真：水印 VML、块级批注、命名样式字体细节、与文字混排的图片。

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

## 9. PDF 渲染契约

- 入口：`io.nop.report.pdf.doc.OfficeDocPdfRenderer`（`render(doc)` → byte[]）；渲染器与字体/换行/绘制工具同模块复用，模块对文档模型为 compile 依赖（只做 Word→PDF 的使用方会传递引入报表链）。
- 排版：layout（纯计算，可单测）与 paint 两阶段共享同一 PDDocument；字体按 (名称,粗,斜) 会话级解析一次，缺字走既有回退。
- 降级口径：列表无命名样式时项目符号/顺序数字缺省；表格单行超页截断具名告警；水印 VML 与混排图片不渲染。
- 不支持形态（未识别块、无效页面几何、缺 asset 引用）显式报错。
- 验收：文本抽取 + 像素探针断言（`TestOfficeDocPdfRender2a/2b`）；端到端 docx→模型→PDF 与 LibreOffice 对照见 `TestOfficeDocPdfRender3E2E`。

## 10. 动态展开契约

- 入口：`io.nop.report.docx.expand.OfficeDocModelExpander.expand(doc, scope)` → 展开后的新模型（入参不变）；便捷重载接受数据 Map。
- 覆盖：`renderType="xpt"` 表格按单元格文本标记展开（纵向/横向/分组聚合，复用 NopReport 展开引擎）；doc/page/块三级条件过滤与循环（解析期编译的 testExpr/loop 字段求值）。
- 展开产物可直连 docx 生成（§6）与 PDF 渲染（§9）。
- 边界：非 xpt 表格原样保留；模板位 cell model 按原坐标对位复制，展开新增行列不带 model；循环副本共享块实例（浅拷贝平台约定）；`deleteAllAfterConfigTable` 属生成期语义。
- 展开失败（数据集缺失/非法标记）快速失败。

## 11. 图片绑定契约

- 绑定定义内嵌 image 模板模型：`chartBinding`（optionExpr/width/height）与 `codeBinding`（codeExpr/codeType: qr|code128/width/height）互斥，同时定义报错。
- 入口：`io.nop.report.docx.expand.DocImageBinder.bind(doc, scope)`；`OfficeDocModelExpander.expand` 尾部自动执行绑定（可注入覆盖）。
- 图表：echarts 渲染为 opt-in（运行时探测；绑定声明存在而依赖缺失时显式报错，不静默跳过）；二维码经 `IQrcodeService`；一维条码内置 code128 位阵绘制。
- 产物注入文档 assets（`__gen-img-<n>` 生成物命名空间）并改写 assetRef；注入前物化 assets 副本，输入模板不被污染。
- 缺失表达式/无效 codeType/渲染失败快速失败；无绑定定义的图片不触碰。

## 12. 统一出口

- docx 出口：`io.nop.report.docx.WordDocTemplates`——`generate(docx bytes | OfficeDocModel, dataScope)` → `WordDocOutput`（docx bytes + 解析 skips + 表格展开计数 + 图片绑定计数）；内部串接解析 → 展开（含图片绑定）→ 生成；无模板语义输入幂等直生成（字节确定）。`expandToModel(...)` 暴露展开模型阶段。
- 高级逃生口：`generateViaLegacyTemplate(docx, scope, outputFile)` 透传既有 Word 模板解析链（完整 xpl 标签场景）。
- PDF 出口：`io.nop.report.pdf.doc.WordDocPdfTemplates.renderPdf(OfficeDocModel)` → `WordDocPdfOutput`（pdf + 渲染 warnings）；只收模型（bytes 先经 docx 出口的模型阶段）；未展开的模板语义按原样渲染（无结构性校验）。
- 模型未建模的 OOXML 部件（customXml/fontTable 等）在模型链路生成时不保留——需要时走逃生口。

## 相关文档

- `03-modules/nop-report.md`（报表侧 Word 表格路径的路由位）
- `02-core-guides/reporting-and-notification-integration.md`
- 源码锚点：`04-reference/source-anchors.md` WDM 条目
