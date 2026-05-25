# 在线 Word 编辑模型设计

## 1. 目标

设计一个面向在线 Word 编辑器的简化模型，用于：

1. 表达页眉、正文、页脚三段结构
2. 表达段落、标题、列表、表格、图片、分页、分隔线、超链接等常见编辑能力
3. 支持模板变量、数据集绑定、动态表格、图表和二维码占位替换
4. 输出 `docx`
5. 支持从 `docx` 回读到简化模型

模型设计参考 `nop-excel` 的对象模型和 `ExcelTemplate` 的输出方式，但不直接照搬 Excel 语义。

## 2. 能力范围

在线 Word 编辑模型至少覆盖：

1. 页面宽高、方向、页边距、水印
2. 首页是否显示页眉页脚
3. 页眉、正文、页脚
4. 文本、标题、列表、表格、图片、超链接、分页、分隔线
5. 字体、字号、颜色、粗体、斜体、下划线、删除线、高亮、上下标
6. 行对齐、行距
7. 表格宽度、列宽、行高、单元格背景色、纵向对齐、跨行跨列
8. 图表、条码、二维码占位与绑定
9. 数据集驱动的纵向、横向、分组表格展开

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

## 13. 与 springreport 的分层对比结论

补充对 `springreport` 的实际实现分析后，可以把它的能力拆成三层：

1. 文档结构层
2. 模板配置层
3. 产品化编辑层

### 13.1 文档结构层

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

### 13.2 模板配置层

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

### 13.3 产品化编辑层

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

### 13.4 对当前 nop 方案的直接结论

如果对比目标是 `springreport` 的基础 Word 文档结构表达：

1. 当前 nop Word 模型已经基本足够

如果对比目标是 `springreport` 的完整模板产品能力：

1. 当前 nop 还不完整
2. 但短板主要在“模板语义尚未内嵌到模型”，而不是 Word 结构对象本身方向错了

### 13.5 后续补充方向

下一步如果继续补齐 springreport 风格能力，优先应补：

1. `doc.model`，承载当前 `XplGenConfig` 与文档级模板生命周期能力
2. `page.model`，承载页面级条件、循环与页眉页脚行为
3. `table.model/row.model/cell.model`，承载 XPT 表格展开和结构化取值能力
4. `run.model/image.model`，承载 `expr/xpl/dataExpr/testExpr` 等常见模板语义
5. 文档模型上的扩展属性与高级 `xpl` 逃生口

不建议继续扩大底层 Word 结构模型的职责，更不建议重新依赖 `excel-table.xdef`。
