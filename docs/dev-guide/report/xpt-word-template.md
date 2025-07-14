# 使用NopReport展开算法动态生成Word表格

## 概述

nop-ooxml-docx模块提供的Word模板插入超链接和表达式的方法功能有限，难以满足复杂表格的需求。`nop-report-docx`
模块提供了基于NopReport中国式报表展开算法的解决方案，能够实现：

- 动态列生成
- 自动单元格合并
- 复杂表格布局
- 数据分组展示

## 配置指南

### 1. 启用NopReport表格展开

在表格的第一个单元格插入以下标注：

```xml
xpt:table=true
```

### 2. 配置展开表达式

支持多种表达式配置方式：

**标注属性配置**：

- `expandType`: 定义展开类型
- `expandExpr`: 设置展开表达式

**单元格内表达式**：

- `${expr}`: 标准表达式语法
- `*=name`: 字段声明语法

![](word-template/xpt-word-table.png)

## 技术实现

```mermaid
graph LR
  A[Word模板] --> B(XptWordTemplateParser)
  B --> C[宏标签转换]
  C --> D[编译期处理]
  D --> E[XptWordTableRenderer]
  E --> F[最终文档]

  subgraph 编译期
    C -->|生成| D
  end

  subgraph 运行期
    E --> F
  end
```

### 1. 表格结构转换

`XptWordTemplateParser`识别标记表格并转换为以下结构：

```xml

<docx-gen:GenXptTable
  xpl:lib="/nop/report/xlib/docx-gen.xlib"
  dump="true">
  <w:tbl>
    <!-- 原始表格内容 -->
  </w:tbl>
</docx-gen:GenXptTable>
```

### 2. 宏标签处理

`<doc-gen:GenXptTable>`宏标签在编译的时候将Word表格解析为ExcelTable并生成XptWordTableRenderer：

```xml

<GenXptTable macro="true">
  <attr name="dump" stdDomain="boolean"/>
  <slot name="default" slotType="node"/>

  <source>
    <c:script><![CDATA[
        import io.nop.report.docx.parse.XptWordTableParser;

        // 编译期解析表格
        const output = XptWordTableParser.fromCompileScope($scope)
                         .compileTable(
                           slot_default.child(0),
                           get('ofcPkg'),
                           dump
                         );

        // 生成AST并传递编译结果
        let ast = xpl `<c:ast>${output.generateToWriter($out.writer,$scope)}</c:ast>`;
        return ast.replaceIdentifier('output', output);
     ]]></c:script>
  </source>
</GenXptTable>
```

**关键流程**：

1. 编译期调用`compileTable`生成模板对象
2. 通过AST语法树变换将编译结果传递到运行期
3. 运行期直接使用预编译的`XptWordTableRenderer`进行渲染

## 3. 报表展开和渲染

```java
public class XptWordTableRenderer implements ITextTemplateOutput {
  private final ExcelSheet xptModel;

  public XptWordTableRenderer(ExcelSheet xptModel) {
    this.xptModel = xptModel;
  }

  @Override
  public void generateToWriter(Writer out, IEvalContext context) throws IOException {
    XptRuntime xptRt = new XptRuntime(context.getEvalScope());
    ExcelWorkbook wk = new ExcelWorkbook();
    xptRt.setWorkbook(wk);

    ExpandedSheet sheet = new ExpandedSheetGenerator(wk).generateSheet(xptModel, xptRt, new HashMap<>());
    renderExpandedSheet(out, sheet, xptRt);
  }

  protected void renderExpandedSheet(Writer out, ExpandedSheet sheet, IXptRuntime xptRt) throws IOException {

    CollectXmlHandler handler = new CollectXmlHandler(out).indentRoot(false).indent(true);

    ExpandedTable table = sheet.getTable();
    handler.beginNode("w:tbl");
    XNode node = (XNode) sheet.getModel().prop_get(VAR_XPT_NODE);
    XNode tblPr = node.childByTag("w:tblPr");
    if (tblPr != null) {
      tblPr.process(handler);
    }
    handler.beginNode("w:tblGrid");
    for (int i = 0, n = table.getColCount(); i < n; i++) {
      int width = UnitsHelper.pointsToTwips(table.getColWidth(i, DEFAULT_WIDTH));
      handler.simpleNode(null, "w:gridCol", Map.of("w:w", vl(null, width)));
    }
    handler.endNode("w:tblGrid");

    for (int i = 0, n = table.getRowCount(); i < n; i++) {
      ExpandedRow row = table.getRow(i);
      XNode tr = (XNode) row.getModel().prop_get(VAR_XPT_NODE);
      XNode trPr = tr.childByTag("w:trPr");
      handler.beginNode(tr.getLocation(), tr.getTagName(), tr.attrValueLocs());
      if (trPr != null)
        trPr.process(handler);
      renderCells(row, handler, xptRt);
      handler.endNode(tr.getTagName());
    }
    handler.endNode("w:tbl");
  }
  // ...
}
```

* XptWordTableRenderer的成员变量xptTable是解析Word表格得到的报表模型对象
* ExpandedSheetGenerator执行报表展开算法，从ExcelSheet得到ExpandedSheet对象
* ExpandedSheet/ExpandedCell等可以通过getModel来返回报表模型对象，在报表模型对象的扩展属性中保存了原始的Word表格样式(XNode形式)
* 使用保存的Word样式对象生成最终的xml文件。基本不需要了解word样式的具体结构，只需要修改其中的`w:tblW/w:gridCol`等部分节点信息。
