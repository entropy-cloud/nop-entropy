package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartPlotAreaParser - 绘图区域解析器
 * 负责解析Excel图表中的绘图区域配置，包括坐标轴、数据系列、样式等
 */
public class ChartPlotAreaParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartPlotAreaParser.class);
    public static final ChartPlotAreaParser INSTANCE = new ChartPlotAreaParser();

    /**
     * 解析绘图区域配置
     *
     * @param plotAreaNode  绘图区域节点
     * @param styleProvider 样式提供者
     * @return 绘图区域模型对象
     */
    public ChartPlotAreaModel parsePlotArea(XNode plotAreaNode, IChartStyleProvider styleProvider) {
        if (plotAreaNode == null) {
            LOG.warn("PlotArea node is null, returning null");
            return null;
        }

        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();

        // 解析形状样式
        parseShapeStyle(plotArea, plotAreaNode, styleProvider);

        // 解析手动布局
        ChartManualLayoutModel manualLayout = ChartManualLayoutParser.INSTANCE.parseManualLayout(plotAreaNode);
        if (manualLayout != null) {
            plotArea.setManualLayout(manualLayout);
        }

        // 解析坐标轴
        parseAxes(plotArea, plotAreaNode, styleProvider);

        // 解析数据系列
        parseSeries(plotArea, plotAreaNode, styleProvider);

        // 解析图表类型特定配置
        ChartTypeConfigParser.INSTANCE.parseChartTypeSpecificConfig(plotArea, plotAreaNode);

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
        if (chartTypeNode == null) {
            LOG.warn("No chart type node found in plot area, skipping series parsing");
            return;
        }

        int index = 0;
        // 遍历所有系列
        for (XNode serNode : chartTypeNode.childrenByTag("c:ser")) {
            ChartSeriesModel series = parseSingleSeries(serNode, index++, styleProvider);
            if (series != null) {
                plotArea.addSeries(series);
            }
        }

    }

    /**
     * 查找图表类型节点
     * 扩展支持更多OOXML图表类型
     */
    private XNode findChartTypeNode(XNode plotAreaNode) {
        // 按优先级顺序查找图表类型
        String[] chartTypes = {
                "c:barChart", "c:bar3DChart",           // 柱状图
                "c:pieChart", "c:pie3DChart",           // 饼图
                "c:lineChart", "c:line3DChart",         // 折线图
                "c:areaChart", "c:area3DChart",         // 面积图
                "c:scatterChart",                       // 散点图
                "c:radarChart",                         // 雷达图
                "c:surfaceChart", "c:surface3DChart",   // 曲面图
                "c:doughnutChart",                      // 环形图
                "c:bubbleChart",                        // 气泡图
                "c:stockChart",                         // 股价图
                "c:ofPieChart"                          // 复合饼图
        };

        for (String type : chartTypes) {
            XNode node = plotAreaNode.childByTag(type);
            if (node != null) {
                return node;
            }
        }

        // 如果没有找到已知类型，记录警告
        LOG.warn("No recognized chart type found in plot area. Available child nodes: {}",
                plotAreaNode.getChildren().stream()
                        .map(XNode::getTagName)
                        .filter(name -> name.startsWith("c:"))
                        .toArray());

        return null;
    }

    /**
     * 解析单个系列
     * 使用ChartSeriesParser进行完整的系列解析
     */
    private ChartSeriesModel parseSingleSeries(XNode serNode, int index, IChartStyleProvider styleProvider) {
        if (serNode == null) {
            return null;
        }

        // 使用ChartSeriesParser进行完整的系列解析
        return ChartSeriesParser.INSTANCE.parseSeries(serNode, index, styleProvider);

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
     * 扩展支持更多OOXML图表类型
     */
    private ChartType mapChartType(String chartType) {

        switch (chartType) {
            case "barChart":
            case "bar3DChart":
                return ChartType.BAR;
            case "pieChart":
            case "pie3DChart":
                return ChartType.PIE;
            case "lineChart":
            case "line3DChart":
                return ChartType.LINE;
            case "areaChart":
            case "area3DChart":
                return ChartType.AREA;
            case "scatterChart":
                return ChartType.SCATTER;
            case "radarChart":
                return ChartType.RADAR;
            case "surfaceChart":
            case "surface3DChart":
                return ChartType.HEATMAP; // 映射到热力图
            case "doughnutChart":
                return ChartType.DOUGHNUT;
            case "bubbleChart":
                return ChartType.BUBBLE;
            case "stockChart":
            case "ofPieChart":
                return ChartType.COMBO; // 复合图表类型
            default:
                LOG.warn("Unknown chart type: {}, using default COLUMN", chartType);
                return ChartType.BAR;
        }

    }
}