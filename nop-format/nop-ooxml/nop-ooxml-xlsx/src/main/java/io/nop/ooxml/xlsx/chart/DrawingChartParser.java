package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import io.nop.excel.chart.model.ChartGridModel;
import io.nop.excel.chart.model.ChartTrendLineModel;
import io.nop.ooxml.xlsx.chart.ChartDataLabelsParser;
import io.nop.ooxml.xlsx.chart.ChartTrendLineParser;
import io.nop.ooxml.xlsx.chart.ChartGridParser;
import io.nop.ooxml.xlsx.chart.ChartTitleParser;
import io.nop.ooxml.xlsx.chart.ChartLegendParser;
import io.nop.ooxml.xlsx.chart.ChartAxisParser;
import io.nop.ooxml.xlsx.chart.ChartPlotAreaParser;
import io.nop.excel.chart.constants.ChartType;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.xlang.xpath.XPathHelper;

/**
 * DrawingChartParser - Excel图表解析器，仿照DrawingParser设计模式
 * 使用SELECTOR机制处理复杂嵌套节点，简单子节点直接使用childByTag
 */
public class DrawingChartParser {
    public static final DrawingChartParser INSTANCE = new DrawingChartParser();
    
    // SELECTOR机制 - 只用于复杂嵌套节点选择
    static final IXSelector<XNode> SELECTOR_TITLE = XPathHelper.parseXSelector("c:title");
    static final IXSelector<XNode> SELECTOR_LEGEND = XPathHelper.parseXSelector("c:legend");
    static final IXSelector<XNode> SELECTOR_PLOT_AREA = XPathHelper.parseXSelector("c:plotArea");
    static final IXSelector<XNode> SELECTOR_AXIS = XPathHelper.parseXSelector("c:catAx|c:valAx|c:dateAx|c:serAx");
    static final IXSelector<XNode> SELECTOR_SERIES = XPathHelper.parseXSelector("c:ser");
    
    /**
     * 解析图表，特意设计通过参数传入chartModel，不要在内部创建ChartModel
     * 
     * @param chartNode 图表节点
     * @param pkg Excel包
     * @param drawingPart 绘图部分
     * @param excelChart 目标ChartModel对象
     */
    public void parseChart(XNode chartNode, ExcelOfficePackage pkg, IOfficePackagePart drawingPart, ChartModel excelChart) {
        if (chartNode == null) return;
        
        // 创建默认样式提供者
        IChartStyleProvider styleProvider = new DefaultChartStyleProvider();
        
        // 解析基础属性
        parseBasicProperties(excelChart, chartNode);
        
        // 使用SELECTOR解析复杂嵌套结构，传入样式提供者用于theme支持
        parseTitle(excelChart, chartNode, styleProvider);
        parseLegend(excelChart, chartNode, styleProvider);
        parsePlotArea(excelChart, chartNode, styleProvider);
    }
    
    /**
     * 解析基础属性
     * @param chart 图表模型
     * @param chartNode 图表节点
     */
    private void parseBasicProperties(ChartModel chart, XNode chartNode) {
        // 解析图表类型
        XNode plotAreaNode = chartNode.childByTag("c:plotArea");
        if (plotAreaNode != null) {
            parseChartType(chart, plotAreaNode);
        }
        
        // 解析其他基础属性
        String roundedCorners = chartNode.attrText("roundedCorners");
        if (roundedCorners != null) {
            chart.setRoundedCorners(ChartPropertyHelper.convertToBoolean(roundedCorners));
        }
    }
    
    /**
     * 解析图表类型
     * @param chart 图表模型
     * @param plotAreaNode 绘图区域节点
     */
    private void parseChartType(ChartModel chart, XNode plotAreaNode) {
        // 检查图表类型
        for (XNode child : plotAreaNode.getChildren()) {
            String tagName = child.getTagName();
            if (tagName.startsWith("c:")) {
                String chartType = tagName.substring(2); // 去掉"c:"前缀
                
                // 映射到ChartType枚举
                ChartType type = mapChartType(chartType);
                if (type != null) {
                    chart.setType(type);
                    break;
                }
            }
        }
    }
    
    /**
     * 映射图表类型字符串到枚举
     * @param chartType 图表类型字符串
     * @return 对应的ChartType枚举
     */
    private ChartType mapChartType(String chartType) {
        switch (chartType) {
            case "areaChart": return ChartType.AREA;
            case "area3DChart": return ChartType.AREA_3D;
            case "barChart": return ChartType.BAR;
            case "bar3DChart": return ChartType.BAR_3D;
            case "lineChart": return ChartType.LINE;
            case "line3DChart": return ChartType.LINE_3D;
            case "pieChart": return ChartType.PIE;
            case "pie3DChart": return ChartType.PIE_3D;
            case "doughnutChart": return ChartType.DOUGHNUT;
            case "scatterChart": return ChartType.SCATTER;
            case "bubbleChart": return ChartType.BUBBLE;
            case "radarChart": return ChartType.RADAR;
            case "surfaceChart": return ChartType.SURFACE;
            case "surface3DChart": return ChartType.SURFACE_3D;
            default: return null;
        }
    }
    
    /**
     * 解析标题
     * @param chart 图表模型
     * @param chartNode 图表节点
     * @param styleProvider 样式提供者
     */
    private void parseTitle(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        XNode titleNode = chartNode.childByTag("c:title");
        if (titleNode == null) return;
        
        ChartTitleModel title = ChartTitleParser.INSTANCE.parseTitle(titleNode, styleProvider);
        chart.setTitle(title);
    }
    
    /**
     * 解析图例
     * @param chart 图表模型
     * @param chartNode 图表节点
     * @param styleProvider 样式提供者
     */
    private void parseLegend(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        XNode legendNode = chartNode.childByTag("c:legend");
        if (legendNode == null) return;
        
        ChartLegendModel legend = ChartLegendParser.INSTANCE.parseLegend(legendNode, styleProvider);
        chart.setLegend(legend);
    }
    
    /**
     * 解析绘图区域
     * @param chart 图表模型
     * @param chartNode 图表节点
     * @param styleProvider 样式提供者
     */
    private void parsePlotArea(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        XNode plotAreaNode = chartNode.childByTag("c:plotArea");
        if (plotAreaNode == null) return;
        
        ChartPlotAreaModel plotArea = ChartPlotAreaParser.INSTANCE.parsePlotArea(plotAreaNode, styleProvider);
        chart.setPlotArea(plotArea);
    }
    

}