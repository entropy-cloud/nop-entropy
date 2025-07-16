# 集成NopReport动态生成Word表格

讲解视频： https://www.bilibili.com/video/BV1jxu8znEo8/

## 一. 背景与需求

在[如何用800行代码实现类似poi-tl的可视化Word模板](https://zhuanlan.zhihu.com/p/537439335)一文中，我们介绍了通过Word超链接嵌入扩展信息实现报表模板的方案。其核心原理是：

1. 利用Nop平台内置的XML格式XPL模板语言
2. 将Word模板中的超链接替换为XPL模板标签

![](word-template/loop-demo.png)

比如上面的Word模板将会被翻译为如下XPL实现

```xml
<orm-docx:for-each-table xpl:slotScope="table">
  <w:tbl>
    ...
    <c:for var="col" items="${table.columns.subList(0,3)}">
      <w:tr w:rsidR="00AD00A2" w14:paraId="2EAAC2A3" w14:textId="77777777" w:rsidTr="00AD00A2">
        ...
      </w:tr>
    </c:for>
  </w:tbl>
</orm-docx:for-each-table>
```

以上设计的关键要点是，**我们完全不需要了解Office XML的样式细节，只需要在合适的地方插入循环标签、表达式标签就可以实现动态Word生成**。因此实现起来非常简单，只需要不到1000行代码。

但是，它的缺点是功能有限，难以满足复杂表格的需求。另外一方面，Nop平台内置了一个非常强大的中国式报表引擎NopReport，可以直接使用Excel模板来定义报表，具体参见[采用Excel作为设计器的开源中国式报表引擎：NopReport](https://zhuanlan.zhihu.com/p/620250740)
一文的介绍。

那么，能不能将NopReport的能力引入Word模板，使用NopReport来动态生成Word表格呢？答案是，可以，而且实现起来非常简单。

`nop-report-docx`模块提供了基于NopReport中国式报表展开算法的解决方案，能够实现：

- 动态列生成
- 自动单元格合并
- 复杂表格布局
- 数据分组展示

## 二. 表格模板配置

### 2.1 启用NopReport表格展开

在表格的第一个单元格插入以下标注：

```xml
xpt:table=true
```

### 2.2 配置展开表达式

支持多种表达式配置方式：

**标注属性配置**：

- `expandType`: 定义展开类型
- `expandExpr`: 设置展开表达式

**单元格内表达式**：

- `${expr}`: 标准表达式语法
- `*=name`: 字段声明语法

![](word-template/xpt-word-table.png)

### 2.3 调用示例

使用XptWordTemplateParser解析得到WordTemplate，然后调用WordTemplate的generateToFile函数。

```java
public class TestXptWordTemplate extends JunitBaseTestCase {
    @Test
    public void testXptTable() {
        IResource resource = getResource("/test/test-word-report.docx");
        WordTemplate tpl = new XptWordTemplateParser().parseFromResource(resource);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("data", Arrays.asList(Map.of("name", "a", "amount", 100),
                Map.of("name", "b", "amount", 200)));
        tpl.generateToFile(getTargetFile("test-result.docx"), scope);
    }
}
```

## 三. 技术实现

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

### 3.1 表格结构转换

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

### 3.2 宏标签处理

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

### 3.3 报表展开和渲染

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
* ExpandedSheet/ExpandedCell等可以通过getModel来返回报表模型对象，在报表模型对象的扩展属性中保存了原始的Word表格样式(
  XNode形式)
* 使用保存的Word样式对象生成最终的xml文件。基本不需要了解word样式的具体结构，只需要修改其中的`w:tblW/w:gridCol`等部分节点信息。

## 四. 核心设计要点

XptWordTemplate的设计充分利用Nop平台内置的各种可扩展能力，利用元编程将各种结构变换联系在一起。

1. 总体实现原理并没有改变，仍然是根据Word模板中的标注信息来将WordXML转换为XPL标签。
2. `<docx-gen:GenXptTable>`在编译期执行了一个局部结构变换，利用编译期变量来保存解析结果，不需要额外的中间变量存储机制
3. 将Word表格解析为ExcelTable时只处理了单元格大小、合并关系等，并不需要解析单元格的样式配置，只是将Word单元格对应的XNode作为扩展属性保存在ExcelCell中。
4. XptWordTableRenderer先调用ExpandedSheetGenerator执行报表展开算法，然后再结合保存的Word表格样式信息动态生成Word XML。
