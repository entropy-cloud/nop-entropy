# 在线 Word 编辑模型设计

> **权威声明**：本文档只保留设计决策理由与对比结论。模型能力矩阵、docx 解析/生成契约、模板语义内嵌规则与范围硬边界的**单一事实源**是 `docs-for-ai/02-core-guides/word-document-model.md`；两处冲突时以 owner doc 为准。

## 1. 目标

设计一个面向在线 Word 编辑器的简化模型，用于：

1. 表达页眉、正文、页脚三段结构
2. 表达段落、标题、列表、表格、图片、分页、分隔线、超链接等常见编辑能力
3. 支持模板变量、数据集绑定、动态表格、图表和二维码占位替换
4. 输出 `docx`
5. 支持从 `docx` 回读到简化模型

模型设计参考 `nop-excel` 的对象模型和 `ExcelTemplate` 的输出方式，但不直接照搬 Excel 语义。

## 2. 能力范围

在线 Word 编辑模型的能力矩阵（当前支持/未支持的逐项事实）以 `docs-for-ai/02-core-guides/word-document-model.md` §4 为准。设计意图层面的概括：模型应覆盖页面/节/页眉页脚、常见块级与行内编辑能力、run/段落/表格样式、模板变量与数据集绑定、图表和二维码占位、输出 docx 与 PDF。

## 3. 现有基础

仓库里已经有三类基础设施可以复用：

1. `nop-excel`：模型生成方式、样式列表、clone/freeze/init 模式
2. `nop-ooxml-docx`：`docx` package 抽象、模板解析与输出
3. `nop-report-docx`：Word 表格与报表展开算法的桥接

结论：

1. 静态 Word 文档结构需要新建 Word 模型
2. 动态表格不需要从零实现，应复用现有 `nop-report-docx`

## 4. 模型分层

这里需要补充一个关键约束：

1. 参考 Nop 已内置的 `WordTemplateParser` / `XptWordTemplateParser` 机制，以及 `workbook.xdef` 的设计方式，模板设计不能做成外挂的独立 settings 对象
2. Word 模型本身既要表达静态文档结构，也要表达模板生成语义
3. 因此模板语义应通过各层节点内嵌的 `model` 子节点来承载，而不是再设计一套 springreport 风格的外部 JSON 配置表

建议顶层结构：

```text
OfficeDocModel
  - props
  - styles
  - assets
  - pages
  - model
```

其中：

1. `pages/page/header/body/footer` 负责静态文档结构
2. `doc.model/page.model/block.model/run.model/table.model/cell.model` 负责模板设计
3. 产品管理类字段，例如模板名称、分类、发布状态，不进入文档模型本身

建议核心对象：

1. `WordDocument`
2. `WordDocTemplateModel`
3. `WordPageTemplateModel`
4. `WordStyles`
5. `WordSection`
6. `WordBlock`
7. `WordBlockTemplateModel`
8. `WordInline`
9. `WordRunTemplateModel`
10. `WordTable`
11. `WordTableTemplateModel`
12. `WordChartBinding`
13. `WordCodeBinding`

## 5. 内容结构

块级对象：

1. `ParagraphBlock`
2. `TitleBlock`
3. `ListBlock`
4. `TableBlock`
5. `ImageBlock`
6. `HyperlinkBlock`
7. `SeparatorBlock`
8. `PageBreakBlock`

行内对象：

1. `TextRun`
2. `TabRun`
3. `HyperlinkRun`
4. `SuperscriptRun`
5. `SubscriptRun`
6. `BreakRun`

## 6. 样式策略

不建议直接复用 `ExcelStyle` 作为 Word 样式。

推荐拆分为：

1. `WordRunStyle`
2. `WordParagraphStyle`
3. `WordCellStyle`
4. `WordTableStyle`

原因：

1. `ExcelStyle` 是单元格样式，不适合作为段落/文本/表格统一样式
2. Word 需要高亮、行距、标题级别、列表样式等 Excel 不具备的语义
3. 直接复用会把 Excel 模型污染成 Office 通用垃圾桶

## 7. 通用共享模型拆分

为了减少 Excel/Word 重复定义，推荐抽出共享基础模型层。

### 7.1 模块名和位置

推荐新增模块：

1. 模块名：`nop-office-model`
2. 位置：`nop-format/nop-office-model`
3. 主包名：`io.nop.office.model`

### 7.2 抽取原则

只抽“真正共享的叶子模型”，不把整个 Excel 模型上提。

当前优先抽取：

1. `OfficeFont`
2. 字体相关枚举
3. 颜色辅助类

后续可继续评估：

1. 通用边框样式
2. 通用对齐枚举
3. 页边距和页面方向

## 8. 本次实施的结构调整

本次已经按推荐方式完成第一步基础重构：

1. 新增模块 `nop-format/nop-office-model`
2. 新增共享字体模型 `io.nop.office.model.OfficeFont`
3. 新增字体相关枚举：
   - `OfficeFontFamily`
   - `OfficeFontUnderline`
   - `OfficeFontVerticalAlign`
4. 新增共享颜色辅助类 `OfficeColorHelper`
5. 新增元模型 `/nop/schema/office/font.xdef`
6. 新模块增加了 `precompile/gen-office-xdsl.xgen`
7. 新模块 `pom.xml` 已增加 `exec-maven-plugin`
8. `nop-format/pom.xml` 已注册 `nop-office-model`
9. `nop-bom/pom.xml` 已加入 `nop-office-model`
10. nop-excel/pom.xml 已增加对 `nop-office-model` 的依赖
11. `nop-excel/precompile/gen-excel-xdsl.xgen` 已移除对 `excel/font.xdef` 的直接生成

## 9. 元模型调整

共享字体元模型已从 Excel 语义抽出：

1. 新位置：`/nop/schema/office/font.xdef`
2. 新生成类：`io.nop.office.model.OfficeFont`

旧的 `/nop/schema/excel/font.xdef` 保留为兼容入口，但语义上已经切换为复用 Office 字体定义。

这一步的目的：

1. 新增 Word 模型时不再引入 `ExcelFont` 命名污染
2. 后续 Excel 与 Word 可以共用同一份字体元模型
3. 代码生成链路仍然通过 `precompile` 自动执行

## 10. `docx` 生成设计

参考 `ExcelTemplate`，建议新增：

1. `WordDocumentTemplate extends AbstractOfficeTemplate`
2. `WordDocumentParser`
3. `WordModelToDocxTransformer`

生成链路：

```text
WordDocument
  -> normalize/init
  -> resolve styles/assets/bindings
  -> render to WordOfficePackage
  -> zip to docx
```

动态表格策略：

1. 静态表格直接渲染 `<w:tbl>`
2. 动态表格复用 `nop-report-docx`
3. 图表和二维码沿用“图片占位 + 绑定定义 + 运行时替换”模式

### 10.1 与 Nop 内置 Word 模板机制对齐

当前 Nop 已经内置了两套非常关键的 Word 模板能力：

1. `WordTemplateParser`
2. `XptWordTemplateParser`

它们分别对应：

1. 通过 `expr:` / `xpl:` 超链接将 docx 编译为 XPL 模板
2. 通过 `xpt:table` 标注把 Word 表格接入 NopReport

因此新的 Word 模型设计不应该绕开这两套能力，而应该把它们模型化。

推荐映射关系：

1. `doc.model` 对应当前 `XplGenConfig` 的内嵌版，承载 `importLibs`、`beforeGen`、`afterGen`、`dump` 等能力
2. `page.model` 对应节/页面级的 `testExpr`、循环与生命周期钩子
3. `run.model` 对应当前超链接模板中的 `expr` / `tpl_expr` / 常见样式表达式
4. `image.model` 对应图片替换场景中的 `dataExpr`、`testExpr`
5. `table.model` 对应当前 `xpt:table` 标记与未来结构化表格模板能力
6. `row.model` / `cell.model` 对齐 `excel-table.xdef` 中 `XptRowModel` / `XptCellModel` 的做法，内嵌测试、取值、格式化、样式等规则

一句话：

**Word 模型不是替代 Nop 内置 WordTemplate 机制，而是把它结构化、类型化，并内嵌到文档模型中。**

### 10.2 参考 `workbook.xdef` 的内嵌方式

`workbook.xdef` 的关键经验不是“再加一个 settings 对象”，而是：

1. 在 workbook 顶层通过 `<model>` 表达工作簿级模板行为
2. 在 sheet 级通过 `<model>` 表达循环、测试、生命周期
3. 在 row/cell/image/chart 等节点上就近放置模板语义

Word 侧推荐采用相同策略。

示意结构如下：

```xml
<doc>
  <pages>...</pages>

  <model xdef:name="WordDocTemplateModel"
         loopVarName="var-name"
         loopIndexName="var-name"
         loopItemsName="var-name"
         dump="boolean"
         dumpFile="string">
    <importLibs>csv-set</importLibs>
    <testExpr>xpl-predicate</testExpr>
    <beginLoop>xpl</beginLoop>
    <endLoop>xpl</endLoop>
    <beforeGen>xpl</beforeGen>
    <afterGen>xpl</afterGen>
  </model>
</doc>
```

```xml
<table>
  <rows>...</rows>

  <model xdef:name="WordTableTemplateModel" renderType="string">
    <testExpr>xpl-predicate</testExpr>
    <styleIdExpr>report-expr</styleIdExpr>
    <expandExpr>xpl</expandExpr>
    <beforeRender>xpl</beforeRender>
    <afterRender>xpl</afterRender>
  </model>
</table>
```

```xml
<cell>
  <value>string</value>

  <model xdef:name="WordCellTemplateModel">
    <valueExpr>report-expr</valueExpr>
    <formatExpr>report-expr</formatExpr>
    <styleIdExpr>report-expr</styleIdExpr>
    <testExpr>xpl-predicate</testExpr>
  </model>
</cell>
```

这里的字段名不一定最终逐字照抄，但建模原则应该保持一致。

### 10.2.1 当前已落地的第一版结构

本轮已经把第一版模板模型直接落到 Word schema 和 Java 模型中：

1. `doc.model -> OfficeDocTemplateModel`
2. `page.model -> OfficeDocPageTemplateModel`
3. `p.model -> OfficeParagraphTemplateModel`
4. `r.model -> OfficeRunTemplateModel`
5. `table.model -> WordTableTemplateModel`
6. `col.model -> WordTableColumnTemplateModel`
7. `row.model -> WordTableRowTemplateModel`
8. `cell.model -> WordTableCellTemplateModel`

当前第一版字段范围是保守设计：

1. 文档级：`dump`、`dumpFile`、`normalizeQuote`、`deleteAllAfterConfigTable`、`importLibs`、`testExpr`、`beginLoop`、`endLoop`、`beforeGen`、`afterGen`
2. 页面级：`testExpr`、`beginLoop`、`endLoop`、`beforeRender`、`afterRender`
3. 段落级：`testExpr`、`visibleExpr`、`beforeRender`、`afterRender`
4. run 级：`testExpr`、`valueExpr`、`formatExpr`、`linkExpr`、`templateExpr`、`beforeRender`、`afterRender`
5. table 级：`testExpr`、`dataExpr`、`expandExpr`、`styleIdExpr`、`beforeRender`、`afterRender`
6. col/row/cell 级：宽度、高度、样式、可见性、取值、格式化、处理表达式等

同时补充了 `makeModel()` 便捷方法，和 `ExcelSheet/ExcelCell/ExcelRow` 的体验保持一致。

这意味着当前 Word 模型已经不只是“静态文档结构”，而是具备了与 `workbook.xdef` 同构的第一版模板承载能力。

### 10.3 保留高级逃生口

Nop 当前 Word 模板机制的优势在于它支持完整 `xpl` 标签，而不仅是简单表达式。

因此结构化模型除了提供常见的 `valueExpr/testExpr/styleIdExpr` 之外，还应该保留高级逃生口：

1. 支持节点级扩展属性
2. 必要时支持内嵌原始 `xpl` 片段
3. 对暂时无法结构化表达的 OOXML 细节允许保留原始 `XNode` 或等价扩展信息

否则会丢掉当前 `WordTemplateParser` 最大的表达能力优势。

## 11. 后续实施顺序

建议按下面顺序继续推进：

1. 在 `doc.xdef`、`word-table.xdef` 中增加 `model` 子节点，先补齐文档级、页面级、表格级模板模型
2. 将当前 `XplGenConfig` 语义迁移为 `doc.model` 的内嵌结构
3. 将 `xpt:table` 语义迁移为 `table.model` 的结构化表达
4. 继续扩展 paragraph/run/image/cell 级模板模型
5. 建立 `WordDocumentModel -> WordTemplate/XPL` 的编译链路
6. 打通静态 `docx` 输出与动态模板输出的统一出口
7. 实现 `docx template -> WordDocumentModel` 的回读映射，兼容现有超链接/注释标注方式

## 12. 结论

推荐路线是：

1. Word 模型独立建立
2. 共享基础模型逐步抽到 `nop-office-model`
3. 模板设计内嵌在 Word 模型本身，方式参考 `workbook.xdef`
4. `docx` 输出和动态模板编译复用现有 `nop-ooxml-docx` 和 `nop-report-docx`
5. 不把模板能力再拆成外挂的 `WordTemplateSettings`

一句话：

**Word 模型独立建设，共享叶子模型逐步沉淀到 `nop-office-model`，模板设计则像 `workbook.xdef` 一样内嵌在模型本身。**

## 13. 结构模型扩展类型决策

通用文档结构模型补齐块级/行内类型时的定型决策（拒绝的替代方案一并记录）：

1. **块级新类型用独立类**：title/list/image/hyperlink/separator/pageBreak 各为独立模型类实现 `OfficeBlock` 标记接口，经 doc.xdef 标签分派（与 `p`/`table` 并列）。拒绝的替代：复用 `OfficeParagraphModel` 的 `type` 字符串属性表达全部块类型——字符串分派失去编译期类型与字段校验，编辑器语义模糊。
2. **行内 run 类型化走包装节点分派**：新增 `OfficeRun` 标记接口；`p` 下增包装节点 `<runs>`（`xdef:bean-sub-type-prop="type"` + key-attr，先例 task.xdef steps），子标签 `r`（text，标签名保留）/`tab`/`br`/`link` 分派为独立模型类，`link` 内嵌 runs 包装节点支持递归嵌套；`p.runs` 类型为 `KeyedList<OfficeRun>`。拒绝的替代：(a) 扁平形态「多个子标签直接共挂一个 bean-prop」——全 xdef 语料无先例，codegen 不支持；(b) 单 `OfficeRunModel` + `type` 枚举属性——无法承载 link 的嵌套 runs 与类型化字段，仅作包装节点分派实测不可用时的降级预案。
3. **页面设置下沉到 page**：`OfficeDocPageModel` 增 width/height（可空，回退 doc 级值）与 `margins:OfficePageMargins`（top/right/bottom/left/header/footer/gutter，twips 口径），支撑多节文档每节独立页面设置；doc 级 assets 列表承载图片资源（`OfficeImageAsset{name,ext,data}`）。拒绝的替代：页面设置留在 doc 级单值——多节页面尺寸/边距各自独立是 OOXML sectPr 的真实语义。
4. **list 为自包含块**：`OfficeListModel{numId, items:List<OfficeListItemModel>}`，item{level, runs, style}。拒绝的替代：列表语义散进段落（每段带 numPr）——把编号信息散到块序列中，编辑器无法把「一个列表」作为整体选中/操作，round-trip 时也难保编号连续性。
5. **assets data 字段类型**：xdef 类型系统原生支持 `base64-bytes` 类型（先例 workbook.xdef ExcelImage.data），data 落为 `ByteString`（base64 序列化、`toByteArray()` 语义），等价 byte[] 且可经 xdef 反序列化，无需 String base64 兜底。
6. **下游兼容边界**：runs 类型化所需的 nop-ooxml-docx 纯机械类型拓宽（for-each 变量类型与 `buildRun` 签名）不属于功能改动；解析/生成的行为接线在后续阶段完成。补充约定：`addR`（bean-prop 生成）被包装节点结构取代后，段落/链接/列表项/标题/超链接的手写子类提供 `addRun(OfficeRun)`（懒建可变 KeyedList）作为唯一追加入口。
7. **cloneInstance 为浅拷贝**：生成基类 `copyTo` 只复制标量、子容器按引用共享（与 ExcelWorkbook 等平台模型一致）；深冻结由 `freeze(true)`（FreezeHelper.deepFreeze）承担。测试与文档按此口径，不引入深克隆工具。
8. **WordTable 无 type 属性**：word-table.xdef 定义不带 `type` 字段（doc.xdef 引用处的 `xdef:bean-tag-prop="type"` 不回写定义类），WordTable 不参与模型 XML 的 union 序列化分派；表格的模型 XML round-trip 契约由后续 docx 生成阶段的 round-trip 测试承载。

## 14. 样式体系决策

四类样式完善与命名样式容器落地时的定型决策（拒绝的替代方案一并记录）：

1. **命名样式容器**：doc 级 `styles` 列表，五类样式（run/paragraph/cell/table/list）经 `OfficeStyle` 标记接口分派（接口落 `nop-office-model`——样式类在该模块，接口放 doc-model 会倒置依赖方向），子标签 runStyle/paragraphStyle/cellStyle/tableStyle/listStyle 引用各 style xdef，key-attr=id 且子标签处 `id="!string"` 升级必填。拒绝的替代：单一 style 类 + kind 字符串——失去四类样式的编译期区分与字段校验。
2. **继承语义**：`basedOn` 沿链合并——标量字段非空覆盖，排除 id/name/basedOn/default 四字段；嵌套对象（font/四向 border/cellStyle）child 非空则整体替换；`default` 每类至多一个缺省样式；环检测与未知 basedOn id fail-fast。拒绝的替代：深逐字段合并嵌套对象——语义复杂且 OOXML styles.xml 接线时可再细化；实现模式先例为 docx 模块 `WordStylesPart.resolveInheritance`（visited 环检测 + 逐字段 inherit），此处为模型层平行实现。补充裁定：primitive 字段（int/boolean 等）永非空，其值等于新构造实例缺省值时视为「未设置」不覆盖父值（xdef 缺省如 start=1、level=0 经生成字段初始化生效）。
3. **标题级别**：`WordParagraphStyle.titleLevel` Integer 可空——null=正文，0-8=标题级别（语义对齐 OOXML outlineLvl）；拒绝枚举类型——outlineLvl 无封闭枚举集且可空语义用枚举表达不自然。
4. **列表样式**：`WordListStyle{numFmt, lvlText, start, level, indent}` 独立叶子模型落 nop-office-model；引用点为 styles 容器第五子标签 `listStyle` + `OfficeListModel.styleId`（list.numId 保留为实例级编号绑定）。listStyle 同样支持 basedOn/default 并入统一 resolver。拒绝的替代：把列表样式塞进段落样式——列表语义可被多个列表块共享，段落样式承载会重复。
5. **resolver 落位**：`OfficeStyleResolver` hand-written 落 nop-office-doc-model（消费 doc 模型中的 named styles），模型层不依赖运行时表达式引擎。

## 15. docx 解析层决策

解析器覆盖全结构时的定型决策（拒绝的替代方案一并记录）：

1. **标题识别与 pStyle 引用**：`w:pStyle` 为 Heading1-9/Title 的段落解析为 OfficeTitleModel（titleLevel 1-9；Title→1）；pStyle 引用统一走 `paragraph.style.id`（WordParagraphStyle.id 承载 pStyleId，单一事实源），删除遗留的 `para.setType(pStyleId)` 赋值——type 是 xdef 块分派属性（值恒为标签名 p），写 pStyle id 会破坏 xdef round-trip。拒绝：pStyle 挂 type 字段（与分派冲突）；保持纯 p 让消费方判断（编辑器需要块级标题类型）。
2. **列表聚合**：连续带 `w:numPr` 且相同 numId 的块级段落聚合为单个 OfficeListModel（item.level 取 ilvl），非 numPr 段落或 numId 变化即闭合；被聚合段落的块级模板 binding（c:for/c:if）迁移到 OfficeListTemplateModel，applyBlockTemplateBinding 扩展 OfficeListModel 分派。拒绝：每段独立 list 块（语义碎片化）。loop 承载见 §16.1（paragraph/title/list 三类块级模板模型均带 loop 字段）。
3. **多节边界**：`w:p` pPr 内嵌 `w:sectPr` 为节结束标记；每个 sectPr 生成一个 OfficeDocPageModel（body 为该节累积块；末尾 body 级 sectPr 归最后一节）；各节 header/footerReference 独立解析；无 headerReference 时的 fallback 头文件扫描归属该节。
4. **图片解析**：`w:drawing` inline/anchor → OfficeImageModel + doc.assets 既有 OfficeImageAsset（name=媒体部件名，data=部件字节，经 OfficePackage.getFile）；宽高 EMU→pt（÷12700）。缺关系/部件降级为占位 assetRef + 日志。
5. **styles.xml**：新解析路径直接解析 styles.xml XNode（不经 WordStylesPart——其字段集不足以承载五类样式），段落样式→paragraphStyle、字符样式→runStyle、表格样式→tableStyle、编号→listStyle；basedOn/default 直映；run 级内联 rPr 覆盖命名样式（内联优先）。
6. **容错口径**：缺失部件/关系→空结构 + 聚合 warn 日志（一次性汇总计数）；XML 结构损坏→DocxErrors 显式错误码；未知子元素→跳过计数。
7. **块级批注**：out of scope（模型无承载字段需先扩 xdef，successor WI5）；表格批注（WordTableCell.comment）已覆盖表格面。
8. **表格内联单元格样式（实现期裁定）**：tcPr 的 tcBorders（fallback tblBorders）/shd fill/vAlign 物化为 WordCellStyle，注册进 doc.styles（生成 id `__cell_t<tableIndex>_<row>_<col>`），cell.styleId 引用；vMerge continue 单元格不生成样式。边框 sz 为八分之一磅，weight 取 sz/8 四舍五入且下限 1。WordCellStyle 无横向对齐字段——tcPr 内 w:jc 跳过并登记 follow-up（WI5 扩模型时一并接线）。拒绝：cell 上挂内联 style 对象（WordTableCell 只有 styleId 字段，挂对象需扩 xdef）。
9. **run 级 tab/br 分派（实现期裁定）**：run 仅含 w:tab 或 w:br（无 w:t）时分派为对应行内节点（tab/br）；整段仅含一个 page 型 br 的段落解析为 pageBreak 块；w:type 属性值写入 br.brType。
10. **图片资产命名（实现期裁定）**：assetRef/asset.name 使用媒体部件在包内全路径（如 `word/media/image1.png`），ext 取扩展名，data 为部件字节；缺关系或部件时 assetRef 保留原值并记入聚合 skip 日志。styles.xml 段落命名样式的 run 字体不落模型（word-paragraph-style.xdef 无 font 属性），登记 follow-up；内联 rPr 与命名样式的优先级（P5 内联优先）由消费方经 OfficeStyleResolver 处理，解析产出保持内联样式对象与 style.id 引用分离。
## 16. 模板标注解析决策

docx 内模板标注解析为内嵌 model 时的定型决策（拒绝的替代方案一并记录）：

1. **块级 loop 承载**：paragraph/title/list 三类块级模板模型均带 loopVarName/loopIndexName/loopItemsName 属性与 beginLoop 子节点（items 表达式值 action）。无 endLoop——块级 binding 的 end 标记只用于配对，不产出独立语义。binding 落在单个段落节点；列表场景下 c:for 所在 item 聚合进列表块后，loop 落 list.model 表示重复整个列表块（与 c:if 合并行为一致）。拒绝的替代：仅给 list 加 loop 字段——段落级单块 c:for 会继续被消费后丢弃。
2. **xpt:table 模型化边界**：识别落 WordTableParser 层，表格首行首单元格的 commentReference 批注含标记即 `table.model.renderType="xpt"`；标记字面量在 docx 模块自有常量定义（不反向依赖报表模块）。仅顶层 WordTable 承载——单元格内嵌套表在模型链路被拍平为文本，无对象可挂标记。无 comments 部件时静默通过；仅 commentReference 悬空时记 skip。批注其余文本不解析。拒绝的替代：在解析期完成 XptTableModel 转换——那是统一出口/编译链路的职责。
3. **超链接模板语义**：`expr:`→valueExpr、`tpl_expr:`/`xpl:`→templateExpr；`linkExpr` 无既有标注形态，不强行定义语义。批注相关内联标签（commentRangeStart/End、commentReference）为已知可忽略元素。

## 17. 与 springreport 的分层对比结论


补充对 `springreport` 的实际实现分析后，可以把它的能力拆成三层：

1. 文档结构层
2. 模板配置层
3. 产品化编辑层

### 17.1 文档结构层

`springreport` 的 `DocTplSettings.header/footer/main` 本质上保存的是一组 JSON 块数据，而不是强类型文档对象模型。

从 `uploadDocx` 和 `parseTable/parseParagraph` 的实现看，它能表达的核心结构包括：

1. 页眉、正文、页脚
2. 普通段落、标题、列表
3. 文本 run 样式：字体、字号、颜色、粗斜体、下划线、删除线、高亮、上下标
4. 图片、超链接、分页、分隔线
5. 表格、列宽、行高、背景色、纵向对齐、跨行跨列

这部分能力与我们当前已经落地的 `OfficeDocModel + OfficeParagraphModel + OfficeRunModel + WordTable` 基本处于同一层，说明：

1. 当前 nop Word 模型对于 springreport 的基础文档结构表达已经基本够用
2. 不需要为了对齐 springreport 再退回到“前端 JSON DTO 即最终模型”的做法

### 17.2 模板配置层

这里需要修正一个表述：

1. 从 springreport 观察到的“模板配置能力”不能简单等价为“应该存在一个外挂配置对象”
2. 参考 `workbook.xdef`，真正参与文档生成的模板语义，应该尽量进入 Word 模型本身
3. 只有模板名称、分类、发布、权限、预览缓存等产品管理信息，才应该留在模型外部

换句话说，`springreport` 真正提示我们的不是“再加一个 settings 表”，而是 Word 模型除了静态结构之外，还需要承载模板行为。

其中值得吸收到模型内的能力包括：

1. `DocTpl.firstpageHeaderFooterShow`
2. `DocTplSettings.watermark`
3. `DocTplCharts`
4. `DocTplCodes`
5. 数据集驱动的 vertical/horizontal/group 扩展策略

这些能力多数属于：

1. 页面设置
2. 运行时绑定定义
3. 模板级业务配置

它们不应该强塞进 `WordRunStyle`、`WordParagraphStyle` 这种纯样式对象里，但应通过 `doc.model/page.model/table.model/...` 内嵌到文档模型。

`paramMerge` 这类主要影响预览查询参数拼装的策略，更接近报表运行控制，可根据最终实现决定放在文档级 model 还是外层报表定义中，但不应退化成 springreport 式的散乱外部 JSON。

### 17.3 产品化编辑层

`springreport` 前端还提供：

1. 分类管理
2. 模板上传/导入
3. 设计器交互
4. 模板预览与导出
5. 图表/二维码/条码配置面板

这些是产品功能和编辑器工作流，不等同于底层文档模型能力。

因此评估“模型是否够用”时，不能把：

1. 文档结构表达能力
2. 模板配置能力
3. 编辑器产品能力

混成一个问题。

### 17.4 对当前 nop 方案的直接结论

如果对比目标是 `springreport` 的基础 Word 文档结构表达：

1. 当前 nop Word 模型已经基本足够

如果对比目标是 `springreport` 的完整模板产品能力：

1. 当前 nop 还不完整
2. 但短板主要在“模板语义尚未内嵌到模型”，而不是 Word 结构对象本身方向错了

### 17.5 后续补充方向

下一步如果继续补齐 springreport 风格能力，优先应补：

1. `doc.model`，承载当前 `XplGenConfig` 与文档级模板生命周期能力
2. `page.model`，承载页面级条件、循环与页眉页脚行为
3. `table.model/row.model/cell.model`，承载 XPT 表格展开和结构化取值能力
4. `run.model/image.model`，承载 `expr/xpl/dataExpr/testExpr` 等常见模板语义
5. 文档模型上的扩展属性与高级 `xpl` 逃生口

不建议继续扩大底层 Word 结构模型的职责，更不建议重新依赖 `excel-table.xdef`。
