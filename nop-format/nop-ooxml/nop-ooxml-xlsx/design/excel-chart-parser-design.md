# Excel Chart Parser 设计文档

## 1. 项目概述

### 1.1 目标
设计并实现一个OOXML XLSX Chart解析器，用于将Excel图表解析为基于Nop平台xdef元模型定义的ChartModel结构。

### 1.2 背景
- **chart.xdef**: 使用Nop平台的xdef元模型定义语言定义的Excel Chart结构
- **Java模型类**: 基于chart.xdef生成的Java Bean类，位于`io.nop.excel.chart.model`包下
- **OOXML格式**: Excel图表以XML格式存储在XLSX文件中，遵循Office Open XML标准

## 2. 架构设计

### 2.1 整体架构（仿照DrawingParser模式）
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   OOXML Chart   │───▶│ DrawingChart    │───▶│   ChartModel    │
│   XML 文件      │    │ Parser (单例)   │    │   Java 对象     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ SELECTOR机制     │
                    │ (复杂节点选择)   │
                    └─────────────────┘
```

### 2.2 核心组件

#### 2.2.1 主解析器 (ChartParser)
- 采用单例模式：`ChartParser.INSTANCE`
- 复用现有的UnitsHelper、ColorHelper等工具类
- childByTag, attrLong, attrBoolean, attrDouble等函数可能返回null

#### 2.2.2 样式提供者接口 (IChartStyleProvider)
- **IChartStyleProvider**: 提供theme支持和外部样式合并功能
- 负责将chart.xml中的样式引用与外部styles.xml、colors.xml中的定义进行合并
- 支持主题颜色映射和样式继承机制

#### 2.2.3 文本解析统一处理
- **ChartTextParser**: 统一处理富文本和简单文本
- 支持RTF格式到纯文本的转换
- 处理文本样式和格式

#### 2.2.4 样式解析器
- **ChartShapeStyleParser**: 形状样式解析（填充、边框、阴影）
- **ChartTextStyleParser**: 文本样式解析（字体、对齐）

## 3. 样式解析架构

### 3.1 样式解析原理

OOXML图表中的样式不能直接通过chart.xml文件读取，而是需要结合当前样式集合、外部styles.xml以及colors.xml中定义的内容进行合并解析。

#### 3.1.1 样式解析流程
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   chart.xml     │───▶│ IChartStyle     │───▶│   ChartModel    │
│   (样式引用)    │    │ Provider        │    │   (完整样式)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                        │
         │                        ▼
         │              ┌─────────────────┐
         └──────────────▶│ styles.xml      │
                        │ colors.xml      │
                        │ (主题定义)       │
                        └─────────────────┘
```

#### 3.1.2 样式合并策略
- **主题颜色映射**: 将主题颜色引用（如`accent1`）映射为实际颜色值
- **样式继承**: 处理样式之间的继承关系
- **外部样式合并**: 将chart.xml中的样式引用与外部样式定义进行合并

### 3.2 IChartStyleProvider接口设计

```java
/**
 * IChartStyleProvider - 图表样式提供者接口
 * 提供theme支持和外部样式合并功能
 */
public interface IChartStyleProvider {
    
    /**
     * 获取主题颜色
     * @param themeColor 主题颜色引用（如"accent1", "accent2"）
     * @return 实际颜色值
     */
    String getThemeColor(String themeColor);
      
    /**
     * 获取颜色定义
     * @param colorRef 颜色引用
     * @return 实际颜色值
     */
    String resolveColor(String colorRef);

    void applyTheme(String componentType, IChartStyleSupportModel model);
}
```

## 4. 解析器详细设计

### 4.1 主解析器接口设计（仿照DrawingParser模式）

```java
/**
 * DrawingChartParser - Excel图表解析器，仿照DrawingParser设计模式
 * 使用SELECTOR机制处理复杂嵌套节点，简单子节点直接使用childByTag
 */
public class DrawingChartParser {
    public static final DrawingChartParser INSTANCE = new DrawingChartParser();
    
    /**
     * 解析图表，特意设计通过参数传入chartModel，不要在内部创建ChartModel
     * 
     * @param chartNode 图表节点
     * @param chartStyleProvider 图表样式提供者，提供theme支持和外部样式合并
     * @param excelChart 目标ChartModel对象
     */
    public void parseChart(XNode chartNode, IChartStyleProvider chartStyleProvider, 
                           ChartModel excelChart) {
        
        // 2. 解析基础属性
        parseBasicProperties(chart, chartNode);
        
        // 3. 使用SELECTOR解析复杂嵌套结构，传入样式提供者用于theme支持
        parseTitle(chart, chartNode, chartStyleProvider);
        parseLegend(chart, chartNode, chartStyleProvider);
        parsePlotArea(chart, chartNode, chartStyleProvider);
    }
    
    private void parseTitle(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        XNode titleNode = chartNode.childByTag("c:title");
        if (titleNode == null) return;
        
        ChartTitleModel title = new ChartTitleModel();
        
        // 简单属性直接使用childByTag/attr
        parseTitleText(title, titleNode);
        title.setVisible(!titleNode.attrBoolean("delete",false));
        
        // 复杂嵌套也使用childByTag逐层获取，传入样式提供者
        parseTitleLayout(title, titleNode);
        parseTitleStyle(title, titleNode, styleProvider);
        
        chart.setTitle(title);
    }
    
    /**
     * 解析标题文本属性，分别设置text和textCellRef
     */
    private void parseTitleText(ChartTitleModel title, XNode titleNode) {
        if (titleNode == null) return;
        
        // 查找文本节点
        XNode txNode = titleNode.childByTag("c:tx");
        if (txNode == null) return;
        
        // 分别解析text和textCellRef属性
        String text = ChartTextParser.INSTANCE.extractText(txNode);
        if (text != null) {
            title.setText(text);
        }
        
        String cellRef = ChartTextParser.INSTANCE.extractCellReference(txNode);
        if (cellRef != null) {
            title.setTextCellRef(cellRef);
        }
    }
        
    private void parseLegend(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        XNode legendNode = chartNode.childByTag("c:legend");
        if (legendNode == null) return;
        
        ChartLegendModel legend = new ChartLegendModel();
        
        // 使用现有的UnitsHelper进行单位转换
        if (legendNode.hasAttr("x")) {
            legend.setX(UnitsHelper.emuToPt(legendNode.attrLong("x")));
        }
        
        // 解析legend样式，传入样式提供者
        parseLegendStyle(legend, legendNode, styleProvider);
        
        chart.setLegend(legend);
    }
    
    /**
     * 解析坐标轴标题文本属性，分别设置text和textCellRef
     */
    private void parseAxisTitleText(ChartAxisTitleModel axisTitle, XNode axisTitleNode) {
        if (axisTitleNode == null) return;
        
        // 查找文本节点
        XNode txNode = axisTitleNode.childByTag("c:tx");
        if (txNode == null) return;
        
        // 分别解析text和textCellRef属性
        String text = ChartTextParser.INSTANCE.extractText(txNode);
        if (text != null) {
            axisTitle.setText(text);
        }
        
        String cellRef = ChartTextParser.INSTANCE.extractCellReference(txNode);
        if (cellRef != null) {
            axisTitle.setTextCellRef(cellRef);
        }
    }
    
    /**
     * 解析绘图区，包含图表类型特定配置、系列和坐标轴
     */
    private void parsePlotArea(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        XNode plotAreaNode = chartNode.childByTag("c:plotArea");
        if (plotAreaNode == null) return;
        
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        
        // 解析图表类型特定配置
        parseChartTypeSpecificConfig(chart, plotAreaNode, styleProvider);
        
        // 解析系列
        parseSeries(plotArea, plotAreaNode, styleProvider);
        
        // 解析坐标轴
        parseAxes(plotArea, plotAreaNode, styleProvider);
        
        chart.setPlotArea(plotArea);
    }
    
    /**
     * 解析图表类型特定配置
     * 根据图表类型（barChart, pieChart等）解析对应的配置到ChartModel
     */
    private void parseChartTypeSpecificConfig(ChartModel chart, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        // 检测并解析柱状图配置
        XNode barChartNode = plotAreaNode.childByTag("c:barChart");
        if (barChartNode != null) {
            parseBarChartConfig(chart, barChartNode);
            return;
        }
        
        // 检测并解析饼图配置
        XNode pieChartNode = plotAreaNode.childByTag("c:pieChart");
        if (pieChartNode != null) {
            parsePieChartConfig(chart, pieChartNode);
            return;
        }
        
        // 其他图表类型...
    }
    
    /**
     * 解析柱状图特定配置
     * 从OOXML的<c:barChart>元素解析出barConfig属性
     * 
     * OOXML结构:
     * <c:barChart>
     *   <c:barDir val="bar|col"/>
     *   <c:grouping val="clustered|stacked|percentStacked"/>
     *   <c:varyColors val="0|1"/>
     *   <c:gapWidth val="150"/>
     *   <c:overlap val="100"/>
     *   <c:ser>...</c:ser>
     *   <c:axId val="40450704"/>
     * </c:barChart>
     * 
     * 对应ChartModel.barConfig:
     * - dir: bar/col
     * - grouping: clustered/stacked/percentStacked
     * - percentGapWidth: gapWidth/1000
     * - percentOverlap: overlap/100
     */
    private void parseBarChartConfig(ChartModel chart, XNode barChartNode) {
        ChartBarConfigModel barConfig = new ChartBarConfigModel();
        
        // 解析方向: <c:barDir val="bar|col"/>
        XNode barDirNode = barChartNode.childByTag("c:barDir");
        if (barDirNode != null) {
            String dir = barDirNode.attr("val");
            barConfig.setDir(ChartBarDirection.fromText(dir));
        }
        
        // 解析分组方式: <c:grouping val="clustered|stacked|percentStacked"/>
        XNode groupingNode = barChartNode.childByTag("c:grouping");
        if (groupingNode != null) {
            String grouping = groupingNode.attr("val");
            barConfig.setGrouping(ChartBarGrouping.fromText(grouping));
        }
        
        // 解析柱间空隙: <c:gapWidth val="150"/> (单位是1/1000，即150=15%)
        XNode gapWidthNode = barChartNode.childByTag("c:gapWidth");
        if (gapWidthNode != null) {
            long gapWidth = gapWidthNode.attrLong("val", 150L);
            barConfig.setPercentGapWidth(gapWidth / 1000.0);
        }
        
        // 解析重叠比例: <c:overlap val="100"/> (单位是1/100，即100=100%)
        XNode overlapNode = barChartNode.childByTag("c:overlap");
        if (overlapNode != null) {
            long overlap = overlapNode.attrLong("val", 0L);
            barConfig.setPercentOverlap(overlap / 100.0);
        }
        
        chart.setBarConfig(barConfig);
    }
    
    /**
     * 解析饼图特定配置
     * 从OOXML的<c:pieChart>元素解析出pieConfig属性
     * 
     * OOXML结构:
     * <c:pieChart>
     *   <c:varyColors val="0|1"/>
     *   <c:ser>...</c:ser>
     *   <c:firstSliceAng val="0"/>
     * </c:pieChart>
     * 
     * 对应ChartModel.pieConfig:
     * - startAngle: firstSliceAng/60000 (转换为度数)
     * - endAngle: 默认360度
     * - innerRadius: 默认0 (实心饼图)
     * - outerRadius: 默认100%
     */
    private void parsePieChartConfig(ChartModel chart, XNode pieChartNode) {
        ChartPieConfigModel pieConfig = new ChartPieConfigModel();
        
        // 解析起始角度: <c:firstSliceAng val="0"/> (单位是1/60000度)
        XNode firstSliceAngNode = pieChartNode.childByTag("c:firstSliceAng");
        if (firstSliceAngNode != null) {
            long angle = firstSliceAngNode.attrLong("val", 0L);
            pieConfig.setStartAngle(angle / 60000.0);
        }
        
        // 设置默认值
        pieConfig.setEndAngle(360.0);
        pieConfig.setInnerRadius(0.0);
        pieConfig.setOuterRadius(100.0);
        
        chart.setPieConfig(pieConfig);
    }
    
    /**
     * 解析数据系列
     * 从OOXML的<c:ser>元素解析出series配置
     */
    private void parseSeries(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        ChartSeriesListModel seriesList = new ChartSeriesListModel();
        
        // 根据图表类型查找系列节点
        XNode chartTypeNode = findChartTypeNode(plotAreaNode);
        if (chartTypeNode == null) return;
        
        // 遍历所有系列
        for (XNode serNode : chartTypeNode.childrenByTag("c:ser")) {
            ChartSeriesModel series = parseSingleSeries(serNode, styleProvider);
            if (series != null) {
                seriesList.add(series);
            }
        }
        
        plotArea.setSeries(seriesList);
    }
    
    /**
     * 查找图表类型节点
     */
    private XNode findChartTypeNode(XNode plotAreaNode) {
        String[] chartTypes = {"c:barChart", "c:pieChart", "c:lineChart", "c:areaChart", "c:scatterChart"};
        for (String type : chartTypes) {
            XNode node = plotAreaNode.childByTag(type);
            if (node != null) return node;
        }
        return null;
    }
    
    /**
     * 解析单个系列
     * OOXML结构:
     * <c:ser>
     *   <c:idx val="0"/>
     *   <c:order val="0"/>
     *   <c:tx>...</c:tx>
     *   <c:spPr>...</c:spPr>
     *   <c:cat>...</c:cat>
     *   <c:val>...</c:val>
     * </c:ser>
     */
    private ChartSeriesModel parseSingleSeries(XNode serNode, IChartStyleProvider styleProvider) {
        ChartSeriesModel series = new ChartSeriesModel();
        
        // 解析系列名称
        parseSeriesName(series, serNode);
        
        // 解析形状样式
        XNode spPrNode = serNode.childByTag("c:spPr");
        if (spPrNode != null) {
            series.setShapeStyle(ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider));
        }
        
        // 解析数据源
        parseSeriesDataSource(series, serNode);
        
        return series;
    }
    
    /**
     * 解析系列名称
     */
    private void parseSeriesName(ChartSeriesModel series, XNode serNode) {
        XNode txNode = serNode.childByTag("c:tx");
        if (txNode == null) return;
        
        String name = ChartTextParser.INSTANCE.extractText(txNode);
        if (name != null) {
            series.setName(name);
        }
        
        String cellRef = ChartTextParser.INSTANCE.extractCellReference(txNode);
        if (cellRef != null) {
            series.setNameCellRef(cellRef);
        }
    }
    
    /**
     * 解析系列数据源
     */
    private void parseSeriesDataSource(ChartSeriesModel series, XNode serNode) {
        ChartDataSourceModel dataSource = new ChartDataSourceModel();
        
        // 解析分类数据
        XNode catNode = serNode.childByTag("c:cat");
        if (catNode != null) {
            String catRef = extractDataCellReference(catNode);
            if (catRef != null) {
                dataSource.setDataCellRef(catRef);
            }
        }
        
        // 解析数值数据
        XNode valNode = serNode.childByTag("c:val");
        if (valNode != null) {
            String valRef = extractDataCellReference(valNode);
            if (valRef != null) {
                dataSource.setDataCellRef(valRef);
            }
        }
        
        series.setDataSource(dataSource);
    }
    
    /**
     * 解析坐标轴
     * 从OOXML的<c:catAx>和<c:valAx>元素解析出axes配置
     */
    private void parseAxes(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        ChartAxesModel axes = new ChartAxesModel();
        
        // 解析分类轴
        for (XNode catAxNode : plotAreaNode.childrenByTag("c:catAx")) {
            ChartAxisModel axis = parseAxis(catAxNode, ChartAxisType.CATEGORY, styleProvider);
            if (axis != null) {
                axes.add(axis);
            }
        }
        
        // 解析数值轴
        for (XNode valAxNode : plotAreaNode.childrenByTag("c:valAx")) {
            ChartAxisModel axis = parseAxis(valAxNode, ChartAxisType.VALUE, styleProvider);
            if (axis != null) {
                axes.add(axis);
            }
        }
        
        plotArea.setAxes(axes);
    }
    
    /**
     * 解析单个坐标轴
     */
    private ChartAxisModel parseAxis(XNode axisNode, ChartAxisType axisType, IChartStyleProvider styleProvider) {
        ChartAxisModel axis = new ChartAxisModel();
        
        axis.setType(axisType);
        
        // 解析坐标轴ID
        XNode axIdNode = axisNode.childByTag("c:axId");
        if (axIdNode != null) {
            axis.setId(axIdNode.attr("val"));
        }
        
        // 解析标题
        parseAxisTitle(axis, axisNode, styleProvider);
        
        // 解析刻度标签
        parseTickLabels(axis, axisNode, styleProvider);
        
        return axis;
    }
    
    /**
     * 解析坐标轴标题
     */
    private void parseAxisTitle(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        XNode titleNode = axisNode.childByTag("c:title");
        if (titleNode == null) return;
        
        ChartAxisTitleModel title = new ChartAxisTitleModel();
        
        // 解析标题文本
        parseAxisTitleText(title, titleNode);
        
        // 解析标题样式
        XNode spPrNode = titleNode.childByTag("c:spPr");
        if (spPrNode != null) {
            title.setShapeStyle(ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider));
        }
        
        axis.setTitle(title);
    }
    
    /**
     * 解析坐标轴刻度标签
     */
    private void parseTickLabels(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        ChartTickLabelsModel tickLabels = new ChartTickLabelsModel();
        
        // 解析数字格式
        XNode numFmtNode = axisNode.childByTag("c:numFmt");
        if (numFmtNode != null) {
            tickLabels.setNumFmt(numFmtNode.attr("formatCode"));
        }
        
        // 解析字体
        XNode txPrNode = axisNode.childByTag("c:txPr");
        if (txPrNode != null) {
            ChartTextStyleModel textStyle = ChartTextStyleParser.INSTANCE.parseTextStyle(txPrNode, styleProvider);
            if (textStyle != null) {
                tickLabels.setFont(textStyle.getFont());
            }
        }
        
        axis.setTickLabels(tickLabels);
    }
    
    /**
     * 从数据源节点提取单元格引用公式
     * 使用childByTag处理嵌套节点
     */
    private String extractDataCellReference(XNode dataSourceNode) {
        if (dataSourceNode == null) return null;
        
        // 检查数值引用 - 使用childByTag选择c:numRef/c:f路径
        XNode numRefNode = dataSourceNode.childByTag("c:numRef");
        if (numRefNode != null) {
            XNode formulaNode = numRefNode.childByTag("c:f");
            if (formulaNode != null) {
                return formulaNode.getText();
            }
        }
        
        // 检查字符串引用 - 复用ChartTextParser解析strRef       
        return ChartTextParser.INSTANCE.extractCellReference(dataSourceNode);
    }
}
```

### 3.2 文本解析统一处理设计

```java
/**
 * ChartTextParser - 统一文本解析器，处理富文本和简单文本
 * 仿照WordDrawing中的文本处理机制
 */
public class ChartTextParser {
    public static final ChartTextParser INSTANCE = new ChartTextParser();
    
    /**
     * 解析文本节点，提取文本内容
     * 支持富文本（tx > rich）、文本引用（tx > strRef）和直接文本（tx > v）
     */
    public String extractText(XNode textNode) {
        if (textNode == null) return null;
        
        // 优先检查富文本 - 简单子节点直接使用childByTag
        XNode richNode = textNode.childByTag("c:rich");
        if (richNode != null) {
            return extractRichText(richNode);
        }
        
        // 检查直接文本内容
        XNode vNode = textNode.childByTag("c:v");
        if (vNode != null) {
            return vNode.getText();
        }
        
        return null;
    }
    
    public String extractCellReference(XNode node) {
        XNode strRefNode = node.childByTag("c:strRef");
        if (strRefNode == null) return null;
        
        // 获取单元格引用公式（如"Sheet1!A1"）
        XNode formulaNode = strRefNode.childByTag("c:f");
        if (formulaNode == null) return null;
        
        String formula = formulaNode.getText();
        return formula != null ? formula.trim() : null;
    }
    
    /**
     * 提取富文本内容（转换为纯文本）
     */
    private String extractRichText(XNode richNode) {
        StringBuilder sb = new StringBuilder();
        
        // 遍历所有文本段落和运行
        for (XNode pNode : richNode.childrenByTag("a:p")) {
            // 段落分隔符
            if(!sb.isEmpty())
                sb.append("\n");
            for (XNode rNode : pNode.childrenByTag("a:r")) {
                XNode tNode = rNode.childByTag("a:t");
                if (tNode != null) {
                    sb.append(tNode.getText());
                }
            }
        }
        
        return sb.toString().trim();
    }
}
```

### 4.3 样式解析器设计（支持theme）

```java
/**
 * ChartShapeStyleParser - 形状样式解析器
 * 统一处理填充、边框、阴影等样式属性，支持theme颜色解析
 */
public class ChartShapeStyleParser {
    public static final ChartShapeStyleParser INSTANCE = new ChartShapeStyleParser();
    
    /**
     * 解析形状样式，支持theme颜色解析
     */
    public ChartShapeStyleModel parseShapeStyle(XNode spPrNode, IChartStyleProvider styleProvider) {
        if (spPrNode == null) return null;
        
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 解析填充 - 传入样式提供者用于theme颜色解析
        parseFill(style, spPrNode.childByTag("a:fill"), styleProvider);
        
        // 解析边框
        parseBorder(style, spPrNode.childByTag("a:ln"), styleProvider);
        
        // 解析阴影
        parseShadow(style, spPrNode.childByTag("a:effectLst").childByTag("a:outerShdw"), styleProvider);
        
        return style;
    }
    
    private void parseFill(ChartShapeStyleModel style, XNode fillNode, IChartStyleProvider styleProvider) {
        if (fillNode == null) return;
        
        ChartFillModel fill = new ChartFillModel();
        
        // 解析纯色填充 - 支持theme颜色解析
        XNode solidFillNode = fillNode.childByTag("a:solidFill");
        if (solidFillNode != null) {
            parseSolidFill(fill, solidFillNode, styleProvider);
        }
        
        // 解析渐变填充
        XNode gradFillNode = fillNode.childByTag("a:gradFill");
        if (gradFillNode != null) {
            parseGradientFill(fill, gradFillNode, styleProvider);
        }
        
        style.setFill(fill);
    }
    
    /**
     * 解析纯色填充，支持theme颜色解析
     */
    private void parseSolidFill(ChartFillModel fill, XNode solidFillNode, IChartStyleProvider styleProvider) {
        XNode schemeClrNode = solidFillNode.childByTag("a:schemeClr");
        if (schemeClrNode != null) {
            // 解析主题颜色引用
            String themeColor = schemeClrNode.attr("val");
            String actualColor = styleProvider.getThemeColor(themeColor);
            fill.setColor(actualColor);
        }
        
        XNode srgbClrNode = solidFillNode.childByTag("a:srgbClr");
        if (srgbClrNode != null) {
            // 解析直接RGB颜色
            fill.setColor(srgbClrNode.attr("val"));
        }
    }
}
```

