package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
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

        try {
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
            parseChartTypeSpecificConfig(plotArea, plotAreaNode, styleProvider);

            return plotArea;
        } catch (Exception e) {
            LOG.warn("Failed to parse plot area configuration", e);
            // 返回基本的plotArea对象而不是null，确保图表解析能继续
            return new ChartPlotAreaModel();
        }
    }

    /**
     * 解析形状样式
     */
    private void parseShapeStyle(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        try {
            XNode spPrNode = plotAreaNode.childByTag("c:spPr");
            if (spPrNode != null) {
                ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
                plotArea.setShapeStyle(shapeStyle);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse plot area shape style", e);
        }
    }

    /**
     * 解析坐标轴
     */
    private void parseAxes(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        try {
            for (XNode axisNode : plotAreaNode.getChildren()) {
                String tagName = axisNode.getTagName();
                if (tagName.startsWith("c:") && (tagName.endsWith("Ax") || tagName.equals("c:serAx"))) {
                    ChartAxisModel axis = ChartAxisParser.INSTANCE.parseAxis(axisNode, styleProvider);
                    if (axis != null) {
                        plotArea.addAxis(axis);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse plot area axes", e);
        }
    }

    /**
     * 解析数据系列
     */
    private void parseSeries(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        try {
            // 根据图表类型查找系列节点
            XNode chartTypeNode = findChartTypeNode(plotAreaNode);
            if (chartTypeNode == null) {
                LOG.warn("No chart type node found in plot area, skipping series parsing");
                return;
            }

            // 遍历所有系列
            for (XNode serNode : chartTypeNode.childrenByTag("c:ser")) {
                ChartSeriesModel series = parseSingleSeries(serNode, styleProvider);
                if (series != null) {
                    plotArea.addSeries(series);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse plot area series", e);
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
    private ChartSeriesModel parseSingleSeries(XNode serNode, IChartStyleProvider styleProvider) {
        if (serNode == null) {
            return null;
        }

        try {
            // 使用ChartSeriesParser进行完整的系列解析
            return ChartSeriesParser.INSTANCE.parseSeries(serNode, styleProvider);

        } catch (Exception e) {
            LOG.warn("Failed to parse series using ChartSeriesParser", e);
            return null;
        }
    }

    /**
     * 解析图表类型特定配置
     */
    private void parseChartTypeSpecificConfig(ChartPlotAreaModel plotArea, XNode plotAreaNode, IChartStyleProvider styleProvider) {
        try {
            // 检测并解析柱状图配置
            XNode barChartNode = plotAreaNode.childByTag("c:barChart");
            if (barChartNode == null) {
                barChartNode = plotAreaNode.childByTag("c:bar3DChart");
            }
            if (barChartNode != null) {
                parseBarChartConfig(plotArea, barChartNode);
                return;
            }

            // 检测并解析饼图配置
            XNode pieChartNode = plotAreaNode.childByTag("c:pieChart");
            if (pieChartNode == null) {
                pieChartNode = plotAreaNode.childByTag("c:pie3DChart");
            }
            if (pieChartNode != null) {
                parsePieChartConfig(plotArea, pieChartNode);
                return;
            }

            // 检测并解析折线图配置
            XNode lineChartNode = plotAreaNode.childByTag("c:lineChart");
            if (lineChartNode == null) {
                lineChartNode = plotAreaNode.childByTag("c:line3DChart");
            }
            if (lineChartNode != null) {
                parseLineChartConfig(plotArea, lineChartNode);
                return;
            }

            // 检测并解析面积图配置
            XNode areaChartNode = plotAreaNode.childByTag("c:areaChart");
            if (areaChartNode == null) {
                areaChartNode = plotAreaNode.childByTag("c:area3DChart");
            }
            if (areaChartNode != null) {
                parseAreaChartConfig(plotArea, areaChartNode);
                return;
            }

            // 其他图表类型的基本处理
            parseOtherChartTypes(plotArea, plotAreaNode);

        } catch (Exception e) {
            LOG.warn("Failed to parse chart type specific configuration", e);
        }
    }

    /**
     * 解析柱状图特定配置
     */
    private void parseBarChartConfig(ChartPlotAreaModel plotArea, XNode barChartNode) {
        try {
            // 解析柱状图方向
            String barDir = ChartPropertyHelper.getChildVal(barChartNode, "c:barDir");
            if (!StringHelper.isEmpty(barDir)) {
                // TODO: 将barDir设置到plotArea的特定配置中
                LOG.debug("Bar chart direction: {}", barDir);
            }

            // 解析分组方式
            String grouping = ChartPropertyHelper.getChildVal(barChartNode, "c:grouping");
            if (!StringHelper.isEmpty(grouping)) {
                // TODO: 将grouping设置到plotArea的特定配置中
                LOG.debug("Bar chart grouping: {}", grouping);
            }

            // 解析间隙宽度
            String gapWidth = ChartPropertyHelper.getChildVal(barChartNode, "c:gapWidth");
            if (!StringHelper.isEmpty(gapWidth)) {
                // TODO: 将gapWidth设置到plotArea的特定配置中
                LOG.debug("Bar chart gap width: {}", gapWidth);
            }

            // 解析重叠比例
            String overlap = ChartPropertyHelper.getChildVal(barChartNode, "c:overlap");
            if (!StringHelper.isEmpty(overlap)) {
                // TODO: 将overlap设置到plotArea的特定配置中
                LOG.debug("Bar chart overlap: {}", overlap);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse bar chart configuration", e);
        }
    }

    /**
     * 解析饼图特定配置
     */
    private void parsePieChartConfig(ChartPlotAreaModel plotArea, XNode pieChartNode) {
        try {
            // 解析饼图是否分离
            Boolean varyColorsBool = ChartPropertyHelper.getChildBoolVal(pieChartNode, "c:varyColors");
            if (varyColorsBool != null) {
                // TODO: 将varyColors设置到plotArea的特定配置中
                LOG.debug("Pie chart vary colors: {}", varyColorsBool);
            }

            // 解析第一个扇区角度
            String firstSliceAng = ChartPropertyHelper.getChildVal(pieChartNode, "c:firstSliceAng");
            if (!StringHelper.isEmpty(firstSliceAng)) {
                // TODO: 将firstSliceAng设置到plotArea的特定配置中
                LOG.debug("Pie chart first slice angle: {}", firstSliceAng);
            }

            // 解析饼图分离程度
            String explosion = ChartPropertyHelper.getChildVal(pieChartNode, "c:explosion");
            if (!StringHelper.isEmpty(explosion)) {
                // TODO: 将explosion设置到plotArea的特定配置中
                LOG.debug("Pie chart explosion: {}", explosion);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse pie chart configuration", e);
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
     * 扩展支持更多OOXML图表类型
     */
    private ChartType mapChartType(String chartType) {
        try {
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
                    return ChartType.COLUMN;
            }
        } catch (Exception e) {
            LOG.warn("Failed to map chart type: {}, using default COLUMN", chartType, e);
            return ChartType.COLUMN;
        }
    }

    /**
     * 解析折线图特定配置
     */
    private void parseLineChartConfig(ChartPlotAreaModel plotArea, XNode lineChartNode) {
        try {
            // 解析分组方式
            String grouping = ChartPropertyHelper.getChildVal(lineChartNode, "c:grouping");
            if (!StringHelper.isEmpty(grouping)) {
                LOG.debug("Line chart grouping: {}", grouping);
            }

            // 解析是否显示标记
            String marker = ChartPropertyHelper.getChildVal(lineChartNode, "c:marker");
            if (!StringHelper.isEmpty(marker)) {
                LOG.debug("Line chart marker: {}", marker);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse line chart configuration", e);
        }
    }

    /**
     * 解析面积图特定配置
     */
    private void parseAreaChartConfig(ChartPlotAreaModel plotArea, XNode areaChartNode) {
        try {
            // 解析分组方式
            String grouping = ChartPropertyHelper.getChildVal(areaChartNode, "c:grouping");
            if (!StringHelper.isEmpty(grouping)) {
                LOG.debug("Area chart grouping: {}", grouping);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse area chart configuration", e);
        }
    }

    /**
     * 解析其他图表类型
     */
    private void parseOtherChartTypes(ChartPlotAreaModel plotArea, XNode plotAreaNode) {
        try {
            // 散点图
            XNode scatterNode = plotAreaNode.childByTag("c:scatterChart");
            if (scatterNode != null) {
                LOG.debug("Found scatter chart configuration");
                return;
            }

            // 气泡图
            XNode bubbleNode = plotAreaNode.childByTag("c:bubbleChart");
            if (bubbleNode != null) {
                LOG.debug("Found bubble chart configuration");
                return;
            }

            // 雷达图
            XNode radarNode = plotAreaNode.childByTag("c:radarChart");
            if (radarNode != null) {
                LOG.debug("Found radar chart configuration");
                return;
            }

            // 环形图
            XNode doughnutNode = plotAreaNode.childByTag("c:doughnutChart");
            if (doughnutNode != null) {
                LOG.debug("Found doughnut chart configuration");
                return;
            }

            // 曲面图
            XNode surfaceNode = plotAreaNode.childByTag("c:surfaceChart");
            if (surfaceNode == null) {
                surfaceNode = plotAreaNode.childByTag("c:surface3DChart");
            }
            if (surfaceNode != null) {
                LOG.debug("Found surface chart configuration");
                return;
            }

            // 股价图
            XNode stockNode = plotAreaNode.childByTag("c:stockChart");
            if (stockNode != null) {
                LOG.debug("Found stock chart configuration");
                return;
            }

        } catch (Exception e) {
            LOG.warn("Failed to parse other chart types", e);
        }
    }
}