package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.constants.ChartType;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DrawingChartParser - Excel图表解析器
 * 负责解析OOXML chartSpace结构，协调各个子解析器
 * 修复OOXML结构解析和错误处理
 */
public class DrawingChartParser {
    private static final Logger LOG = LoggerFactory.getLogger(DrawingChartParser.class);
    public static final DrawingChartParser INSTANCE = new DrawingChartParser();
    
    /**
     * 解析图表，遵循OOXML chartSpace结构规范
     * 
     * @param chartRefNode 图表引用节点
     * @param pkg Excel包
     * @param drawingPart 绘图部分
     * @param excelChart 目标ChartModel对象
     */
    public void parseChartRef(XNode chartRefNode, ExcelOfficePackage pkg, IOfficePackagePart drawingPart, ChartModel excelChart) {
        XNode chartNode = getChartNode(chartRefNode, pkg, drawingPart);
        if (chartNode == null) {
            LOG.warn("Chart node is null, cannot parse chart");
            return;
        }
        
        try {
            // 验证chartSpace结构
            validateChartStructure(chartNode);
            
            // 创建样式提供者
            IChartStyleProvider styleProvider = createStyleProvider(pkg);
            
            // 查找实际的chart节点 (c:chartSpace/c:chart)
            XNode actualChartNode = findActualChartNode(chartNode);
            if (actualChartNode == null) {
                LOG.warn("No actual chart node found in chartSpace, using chartSpace as chart node");
                actualChartNode = chartNode;
            }
            
            // 解析基础属性
            parseBasicProperties(excelChart, actualChartNode);
            
            // 解析图表组件
            parseTitle(excelChart, actualChartNode, styleProvider);
            parseLegend(excelChart, actualChartNode, styleProvider);
            parsePlotArea(excelChart, actualChartNode, styleProvider);
            
        } catch (Exception e) {
            LOG.warn("Failed to parse chart", e);
            // 设置默认图表类型，确保图表对象可用
            if (excelChart.getType() == null) {
                excelChart.setType(ChartType.COLUMN);
            }
        }
    }
    
    /**
     * 验证图表结构
     */
    private void validateChartStructure(XNode chartNode) {
        String tagName = chartNode.getTagName();
        if (!"c:chartSpace".equals(tagName) && !"c:chart".equals(tagName)) {
            LOG.warn("Unexpected chart root node: {}, expected c:chartSpace or c:chart", tagName);
        }
    }
    
    /**
     * 创建样式提供者
     */
    private IChartStyleProvider createStyleProvider(ExcelOfficePackage pkg) {
        try {
            // TODO: 在后续任务中实现主题文件解析
            // 目前使用默认样式提供者
            return new DefaultChartStyleProvider();
        } catch (Exception e) {
            LOG.warn("Failed to create style provider, using default", e);
            return new DefaultChartStyleProvider();
        }
    }
    
    /**
     * 查找实际的图表节点
     * OOXML结构: c:chartSpace/c:chart 或直接是 c:chart
     */
    private XNode findActualChartNode(XNode chartNode) {
        if ("c:chartSpace".equals(chartNode.getTagName())) {
            XNode chartChild = chartNode.childByTag("c:chart");
            if (chartChild != null) {
                return chartChild;
            }
        }
        return chartNode;
    }
    
    /**
     * 解析基础属性
     * @param chart 图表模型
     * @param chartNode 图表节点
     */
    private void parseBasicProperties(ChartModel chart, XNode chartNode) {
        try {
            // 解析图表类型
            XNode plotAreaNode = chartNode.childByTag("c:plotArea");
            if (plotAreaNode != null) {
                parseChartType(chart, plotAreaNode);
            }
            
            // 解析其他基础属性
            String roundedCorners = chartNode.attrText("roundedCorners");
            if (!StringHelper.isEmpty(roundedCorners)) {
                Boolean roundedCornersBool = ChartPropertyHelper.convertToBoolean(roundedCorners);
                if (roundedCornersBool != null) {
                    chart.setRoundedCorners(roundedCornersBool);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart basic properties", e);
        }
    }
    
    /**
     * 解析图表类型
     * @param chart 图表模型
     * @param plotAreaNode 绘图区域节点
     */
    private void parseChartType(ChartModel chart, XNode plotAreaNode) {
        try {
            // 检查图表类型
            for (XNode child : plotAreaNode.getChildren()) {
                String tagName = child.getTagName();
                if (tagName.startsWith("c:")) {
                    String chartType = tagName.substring(2); // 去掉"c:"前缀
                    
                    // 映射到ChartType枚举
                    ChartType type = mapChartType(chartType);
                    if (type != null) {
                        chart.setType(type);
                        // 设置3D标志
                        chart.setIs3D(is3DChartType(chartType));
                        return;
                    }
                }
            }
            
            // 如果没有找到图表类型，设置默认类型
            LOG.warn("No recognized chart type found in plot area, using default COLUMN");
            chart.setType(ChartType.COLUMN);
            chart.setIs3D(false);
            
        } catch (Exception e) {
            LOG.warn("Failed to parse chart type, using default COLUMN", e);
            chart.setType(ChartType.COLUMN);
            chart.setIs3D(false);
        }
    }
    
    /**
     * 映射图表类型字符串到枚举
     * 修复3D图表类型映射，因为ChartType枚举中没有3D类型
     * @param chartType 图表类型字符串
     * @return 对应的ChartType枚举
     */
    private ChartType mapChartType(String chartType) {
        try {
            switch (chartType) {
                case "areaChart":
                case "area3DChart":
                    return ChartType.AREA;
                case "barChart":
                case "bar3DChart":
                    return ChartType.BAR;
                case "lineChart":
                case "line3DChart":
                    return ChartType.LINE;
                case "pieChart":
                case "pie3DChart":
                    return ChartType.PIE;
                case "doughnutChart":
                    return ChartType.DOUGHNUT;
                case "scatterChart":
                    return ChartType.SCATTER;
                case "bubbleChart":
                    return ChartType.BUBBLE;
                case "radarChart":
                    return ChartType.RADAR;
                case "surfaceChart":
                case "surface3DChart":
                    return ChartType.HEATMAP; // 映射曲面图到热力图
                case "stockChart":
                case "ofPieChart":
                    return ChartType.COMBO; // 复合图表类型
                default:
                    LOG.warn("Unknown chart type: {}, returning null", chartType);
                    return null;
            }
        } catch (Exception e) {
            LOG.warn("Failed to map chart type: {}, returning null", chartType, e);
            return null;
        }
    }
    
    /**
     * 检查图表类型是否为3D
     * @param chartType OOXML图表类型字符串
     * @return 是否为3D图表
     */
    private boolean is3DChartType(String chartType) {
        return chartType != null && chartType.endsWith("3DChart");
    }
    
    /**
     * 解析标题
     * @param chart 图表模型
     * @param chartNode 图表节点
     * @param styleProvider 样式提供者
     */
    private void parseTitle(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        try {
            XNode titleNode = chartNode.childByTag("c:title");
            if (titleNode != null) {
                ChartTitleModel title = ChartTitleParser.INSTANCE.parseTitle(titleNode, styleProvider);
                if (title != null) {
                    chart.setTitle(title);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart title", e);
        }
    }
    
    /**
     * 解析图例
     * @param chart 图表模型
     * @param chartNode 图表节点
     * @param styleProvider 样式提供者
     */
    private void parseLegend(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        try {
            XNode legendNode = chartNode.childByTag("c:legend");
            if (legendNode != null) {
                ChartLegendModel legend = ChartLegendParser.INSTANCE.parseLegend(legendNode, styleProvider);
                if (legend != null) {
                    chart.setLegend(legend);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart legend", e);
        }
    }
    
    /**
     * 解析绘图区域
     * @param chart 图表模型
     * @param chartNode 图表节点
     * @param styleProvider 样式提供者
     */
    private void parsePlotArea(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        try {
            XNode plotAreaNode = chartNode.childByTag("c:plotArea");
            if (plotAreaNode != null) {
                ChartPlotAreaModel plotArea = ChartPlotAreaParser.INSTANCE.parsePlotArea(plotAreaNode, styleProvider);
                if (plotArea != null) {
                    chart.setPlotArea(plotArea);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart plot area", e);
        }
    }
}