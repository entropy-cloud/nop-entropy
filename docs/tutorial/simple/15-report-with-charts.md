# Nop入门：使用Excel模板生成包含图表的报表

讲解视频：[Nop入门：使用NopReport导出Excel图表_哔哩哔哩_bilibili](https://www.bilibili.com/video/BV1NfvYBRExv/)

Nop平台内置了一个非常精简的中国式报表引擎NopReport，它可以实现商业报表引擎如FineReport和润乾报表的核心功能。NopReport很巧妙的利用Excel单元格的批注机制来存放扩展信息，可以直接使用Excel模板来作为报表模板，这样大大简化了报表制作过程，并且可以复用客户已有的各种业务表格。

在实现层面，NopReport没有使用Apache POI库，而是选择了直接使用流式接口解析Office XML文件，解析时使用的也不是Java标准的XML解析器，而是自行实现的XNodeParser(速度比标准解析器快2倍)。NopReport是一个非常精简高效的实现，核心代码只有3000多行，却可以达到比商业报表引擎的更好的性能和更好的可扩展性。

本文将介绍NopReport的基本使用方式，以及如何在Excel报表模板中配置图表显示。

### 1. 配置报表展开逻辑

NopReport采用了润乾报表发明的层次坐标展开算法，可以通过父子单元格关联展开实现非常复杂的报表逻辑。具体参见[非线性中国式报表引擎NopReport源码解析](https://mp.weixin.qq.com/s/vt9RmHNa2yBnvNe07aLdTw)

**配置展开单元格**:

1. **右键单元格** → **插入批注**
2. **在批注中输入配置参数**，每行一个参数：

```
expandType=r
ds=ds1
field=product
```

* `expandType=r`表示单元格向下展开，产生多行输出。这个单元格右侧的所有单元格缺省情况下都是它的子格。父格展开后子格自动被复制。
* ds和field配置指定了从哪个数据集的哪个字段取值。展开单元格中配置field会自动按照该字段进行分组。

以上配置表示按照ds1数据集(列表格式)的product字段进行分组汇总，并为每个分组产生一个输出结果。在子单元格上只要配置field属性就可以按照该属性值进行取值并输出。

### 2. 配置图表动态绑定

NopReport支持导出Excel的时候生成与数据关联的图表。具体做法同样是在备注文本中增加扩展信息。

右键点击图表，选择"编辑可选文字"，输入以下配置：

![图表配置界面](../../dev-guide/report/excel-chart/excel-chart-config.png)

```
销售数据柱状图
----
----
seriesDataCellRefExpr=xptRt.buildCellRef('Sheet1!B3', seriesModel.index,0,1,3)
seriesTestExpr=seriesModel.index < 2
seriesNameExpr=xptRt.ds('ds1').group('product')[seriesModel.index].key+'Series'
```

上述配置效果：

- 动态产生单元格引用表达式，比如生成 `Sheet1!B3:D3`
- 仅生成 index 为 0、1 的两个系列
- 图表名称使用动态计算得到的字符串

### 3. Java代码调用

```java
public class TestReportChart extends BaseTestCase {
    @Test
    public void testBarChart() {
        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = reportEngine.getXptModel("/nop/report/demo/test-bar-chart.xpt.xlsx");

        ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, "xlsx");
        IEvalScope scope = XLang.newEvalScope();
        output.generateToFile(getTargetFile("result-bar-chart.xlsx"),scope);
    }
}
```

可以通过scope向报表引擎传递变量，也可以在报表模板的【展开前】配置中通过XScript语言来定义数据集。

![report-data-set](images/report-data-set.png)

## 展开单元格配置参数说明：

- **expandType**：展开方向（r=right向右，l=left向左，d=down向下，u=up向上）
- **expandExpr**：展开表达式，返回一个集合，当前单元格按此集合展开
- **field**：展开字段名，按指定字段对数据集进行分组展开
- **ds**：数据源名称
- **expandInplaceCount**：原地展开的数量限制
- **expandMinCount**：最少展开元素数量
- **expandMaxCount**：最多展开元素数量
- **rowParent/colParent**：设置行/列父格

## 图表配置参数说明：

可在“----”之后配置以下常用表达式（更多项见 schema：/nop/schema/excel/chart.xdef 的 <dynamicBindings>）：

- chartTestExpr: (chartModel) => boolean
  - 决定是否生成整个图表
- chartTitleCellRefExpr: (chartModel) => string
- chartTitleExpr: (chartModel) => string
  - 标题可用单元格引用或直接文本（二选一）。若 cellRefExpr 返回非空，则优先按单元格；否则尝试 titleExpr 文本
- seriesTestExpr: (seriesModel, chartModel) => boolean
  - 决定某个系列是否保留（返回 true 保留）
- seriesNameCellRefExpr | seriesNameExpr: (seriesModel, chartModel) => string
- seriesDataCellRefExpr | seriesDataExpr: (seriesModel, chartModel) => string/any
- seriesCatCellRefExpr | seriesCatExpr: (seriesModel, chartModel) => string/any
- axisDataCellRefExpr: (axisModel, chartModel) => string
- axisTitleCellRefExpr | axisTitleExpr: (axisModel, chartModel) => string

## 模板预置原则

一个重要的设计原则是：**模板需要预置最多的系列数量**。运行时不会创建新的系列，只会基于模板中已有的系列进行过滤和配置。

## 支持的图表类型

NopReport图表功能支持丰富的图表类型，以下是一些常用图表的示例：

### 柱状图/条形图

![簇状柱形图](../../dev-guide/report/excel-chart/clustered-column-chart.png)
![堆积柱形图](../../dev-guide/report/excel-chart/stacked-column-chart.png)
![百分比堆积柱形图](../../dev-guide/report/excel-chart/100pct-stacked-column-chart.png)

### 折线图/面积图

![折线图](../dev-guide/report/excel-chart/line-chart.png)
![带数据标记的折线图](../../dev-guide/report/excel-chart/line-with-markers-chart.png)
![面积图](../../dev-guide/report/excel-chart/area-chart.png)
![堆积面积图](../../dev-guide/report/excel-chart/stacked-area-chart.png)

### 饼图/环形图

![饼图](../../dev-guide/report/excel-chart/pie-chart.png)
![环形图](../../dev-guide/report/excel-chart/doughnut-chart.png)

### 其他图表

![雷达图](../../dev-guide/report/excel-chart/radar-chart.png)
![散点图](../../dev-guide/report/excel-chart/scatter-chart.png)
![填充雷达图](../../dev-guide/report/excel-chart/filled-radar-chart.png)
