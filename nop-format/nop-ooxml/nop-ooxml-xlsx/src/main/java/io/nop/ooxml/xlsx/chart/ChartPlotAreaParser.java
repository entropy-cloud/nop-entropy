package io.nop.ooxml.xlsx.chart;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.ooxml.xlsx.parse.Selector;

import static io.nop.ooxml.xlsx.parse.OOXMLLoaderHelper.attrText;

/**
 * ChartPlotAreaParser - 绘图区域解析器
 * 负责解析Excel图表中的绘图区域配置，包括坐标轴、数据系列、样式等
 */
public class ChartPlotAreaParser {
    public static final ChartPlotAreaParser INSTANCE = new ChartPlotAreaParser();
    
    /**
     * 解析绘图区域配置
     * @param plotAreaNode 绘图区域节点
     * @param styleProvider 样式提供者
     * @return 绘图区域模型对象
     */
    public ChartPlotAreaModel parsePlotArea(XNode plotAreaNode, IChartStyleProvider styleProvider) {
        if (plotAreaNode == null) return null;
        
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        
        // 解析形状样式
        parseShapeStyle(plotArea, plotAreaNode, styleProvider);
        
        // 解析手动布局
        parseManualLayout(plotArea, plotAreaNode);
        
        // 解析坐标轴
        parseAxes(plotArea, plotAreaNode, styleProvider);
        
        // 解析数据系列
        parseSeries(plotArea, plotAreaNode, styleProvider);
        
        // 解析图表类型特定配置
        parseChartTypeSpecificConfig(plotArea, plotAreaNode, styleProvider);
        
        return plotArea;
    }
    
    /**
     * 解析形状样式
     */
    private void parseShapeStyle(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        XNode spPrNode = plotAreaNode.childByTag("c:spPr");
        if (spPrNode != null) {
            ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
            plotArea.setShapeStyle(shapeStyle);
        }
    }
    
    /**
     * 解析手动布局
     */
    private void parseManualLayout(ChartPlotAreaModel plotArea, XNode plotAreaNode) {
        XNode manualLayoutNode = plotAreaNode.childByTag("c:manualLayout");
        if (manualLayoutNode != null) {
            ChartManualLayoutModel manualLayout = parseManualLayout(manualLayoutNode);
            plotArea.setManualLayout(manualLayout);
        }
    }
    
    /**
     * 解析手动布局配置
     */
    private ChartManualLayoutModel parseManualLayout(XNode manualLayoutNode) {
        ChartManualLayoutModel layout = new ChartManualLayoutModel();
        
        // 解析布局目标
        // 在OOXML中，layoutTarget属性直接存在于<c:manualLayout>元素上
        String layoutTarget = manualLayoutNode.attrText("layoutTarget");
        if (!StringHelper.isEmpty(layoutTarget)) {
            layout.setLayoutTarget(layoutTarget);
        }
        
        // 解析X位置模式
        // 在OOXML中，xMode属性直接存在于<c:manualLayout>元素上
        String xMode = manualLayoutNode.attrText("xMode");
        if (!StringHelper.isEmpty(xMode)) {
            layout.setXMode(xMode);
        }
        
        XNode xNode = manualLayoutNode.childByTag("c:x");
        if (xNode != null) {
            Double x = parseLayoutValue(xNode);
            if (x != null) {
                layout.setX(x);
            }
        }
        
        // 解析Y位置模式
        // 在OOXML中，yMode属性直接存在于<c:manualLayout>元素上
        String yMode = manualLayoutNode.attrText("yMode");
        if (!StringHelper.isEmpty(yMode)) {
            layout.setYMode(yMode);
        }
        
        XNode yNode = manualLayoutNode.childByTag("c:y");
        if (yNode != null) {
            Double y = parseLayoutValue(yNode);
            if (y != null) {
                layout.setY(y);
            }
        }
        
        // 解析宽度模式
        // 在OOXML中，wMode属性直接存在于<c:manualLayout>元素上
        String wMode = manualLayoutNode.attrText("wMode");
        if (!StringHelper.isEmpty(wMode)) {
            layout.setWMode(wMode);
        }
        
        XNode wNode = manualLayoutNode.childByTag("c:w");
        if (wNode != null) {
            Double w = parseLayoutValue(wNode);
            if (w != null) {
                layout.setW(w);
            }
        }
        
        // 解析高度模式
        // 在OOXML中，hMode属性直接存在于<c:manualLayout>元素上
        String hMode = manualLayoutNode.attrText("hMode");
        if (!StringHelper.isEmpty(hMode)) {
            layout.setHMode(hMode);
        }
        
        XNode hNode = manualLayoutNode.childByTag("c:h");
        if (hNode != null) {
            Double h = parseLayoutValue(hNode);
            if (h != null) {
                layout.setH(h);
            }
        }
        
        return layout;
    }
    
    /**
     * 解析布局数值
     */
    private Double parseLayoutValue(XNode valueNode) {
        Double value = valueNode.attrDouble("val");
        if (value != null) {
            return value;
        }
        
        // 如果attrDouble返回null，尝试从文本内容解析
        String valueStr = valueNode.getTextContent();
        if (!StringHelper.isEmpty(valueStr)) {
            try {
                return Double.parseDouble(valueStr);
            } catch (NumberFormatException e) {
                throw new NopException("ERR_EXCEL_INVALID_LAYOUT_VALUE")
                    .param("value", valueStr);
            }
        }
        return null;
    }
    
    /**
     * 解析坐标轴
     */
    private void parseAxes(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        for (XNode axisNode : plotAreaNode.getChildren()) {
            String tagName = axisNode.getTagName();
            if (tagName.startsWith("c:") && (tagName.endsWith("Ax") || tagName.equals("c:serAx"))) {
                ChartAxisModel axis = ChartAxisParser.INSTANCE.parseAxis(axisNode, styleProvider);
                if (axis != null) {
                    plotArea.addAxis(axis);
                }
            }
        }
    }
    
    /**
     * 解析数据系列
     */
    private void parseSeries(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        // 根据图表类型查找系列节点
        XNode chartTypeNode = findChartTypeNode(plotAreaNode);
        if (chartTypeNode == null) return;
        
        // 遍历所有系列
        for (XNode serNode : chartTypeNode.childrenByTag("c:ser")) {
            ChartSeriesModel series = parseSingleSeries(serNode, styleProvider);
            if (series != null) {
                plotArea.addSeries(series);
            }
        }
    }
    
    /**
     * 查找图表类型节点
     */
    private XNode findChartTypeNode(XNode plotAreaNode) {
        String[] chartTypes = {"c:barChart", "c:pieChart", "c:lineChart", "c:areaChart", "c:scatterChart", 
                               "c:radarChart", "c:surfaceChart", "c:doughnutChart", "c:bubbleChart"};
        for (String type : chartTypes) {
            XNode node = plotAreaNode.childByTag(type);
            if (node != null) return node;
        }
        return null;
    }
    
    /**
     * 解析单个系列
     */
    private ChartSeriesModel parseSingleSeries(XNode serNode, IChartStyleProvider styleProvider) {
        // 这里需要创建ChartSeriesParser来处理系列解析
        // 暂时返回null，后续需要实现ChartSeriesParser
        return null;
    }
    
    /**
     * 解析图表类型特定配置
     */
    private void parseChartTypeSpecificConfig(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        // 检测并解析柱状图配置
        XNode barChartNode = plotAreaNode.childByTag("c:barChart");
        if (barChartNode != null) {
            parseBarChartConfig(plotArea, barChartNode);
            return;
        }
        
        // 检测并解析饼图配置
        XNode pieChartNode = plotAreaNode.childByTag("c:pieChart");
        if (pieChartNode != null) {
            parsePieChartConfig(plotArea, pieChartNode);
            return;
        }
        
        // 其他图表类型...
    }
    
    /**
     * 解析柱状图特定配置
     */
    private void parseBarChartConfig(ChartPlotAreaModel plotArea, XNode barChartNode) {
        // 解析柱状图方向
        XNode barDirNode = barChartNode.childByTag("c:barDir");
        if (barDirNode != null) {
            String barDir = barDirNode.attr("val");
            // 设置到plotArea的特定配置中
        }
        
        // 解析分组方式
        XNode groupingNode = barChartNode.childByTag("c:grouping");
        if (groupingNode != null) {
            String grouping = groupingNode.attr("val");
            // 设置到plotArea的特定配置中
        }
        
        // 解析间隙宽度
        XNode gapWidthNode = barChartNode.childByTag("c:gapWidth");
        if (gapWidthNode != null) {
            String gapWidth = gapWidthNode.attr("val");
            // 设置到plotArea的特定配置中
        }
        
        // 解析重叠比例
        XNode overlapNode = barChartNode.childByTag("c:overlap");
        if (overlapNode != null) {
            String overlap = overlapNode.attr("val");
            // 设置到plotArea的特定配置中
        }
    }
    
    /**
     * 解析饼图特定配置
     */
    private void parsePieChartConfig(ChartPlotAreaModel plotArea, XNode pieChartNode) {
        // 解析饼图是否分离
        XNode varyColorsNode = pieChartNode.childByTag("c:varyColors");
        if (varyColorsNode != null) {
            String varyColors = varyColorsNode.attr("val");
            // 设置到plotArea的特定配置中
        }
        
        // 解析第一个扇区角度
        XNode firstSliceAngNode = pieChartNode.childByTag("c:firstSliceAng");
        if (firstSliceAngNode != null) {
            String firstSliceAng = firstSliceAngNode.attr("val");
            // 设置到plotArea的特定配置中
        }
        
        // 解析饼图分离程度
        XNode explosionNode = pieChartNode.childByTag("c:explosion");
        if (explosionNode != null) {
            String explosion = explosionNode.attr("val");
            // 设置到plotArea的特定配置中
        }
    }
    
    /**
     * 检测图表类型
     */
    public ChartType detectChartType(XNode plotAreaNode) {
        if (plotAreaNode == null) return null;
        
        for (XNode child : plotAreaNode.getChildren()) {
            String tagName = child.getTagName();
            if (tagName.startsWith("c:")) {
                String chartType = tagName.substring(2); // 去掉"c:"前缀
                
                // 映射到ChartType枚举
                return mapChartType(chartType);
            }
        }
        
        return null;
    }
    
    /**
     * 映射图表类型
     */
    private ChartType mapChartType(String chartType) {
        switch (chartType) {
            case "barChart": return ChartType.BAR;
            case "pieChart": return ChartType.PIE;
            case "lineChart": return ChartType.LINE;
            case "areaChart": return ChartType.AREA;
            case "scatterChart": return ChartType.SCATTER;
            case "radarChart": return ChartType.RADAR;
            case "surfaceChart": return ChartType.SURFACE;
            case "doughnutChart": return ChartType.DOUGHNUT;
            case "bubbleChart": return ChartType.BUBBLE;
            default: return null;
        }
    }
}