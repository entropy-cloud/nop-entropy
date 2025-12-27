/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ChartPlotAreaBuilder - 绘图区域构建器
 * 负责生成OOXML图表中的绘图区域配置，包括坐标轴、数据系列、样式等
 * 将内部的ChartPlotAreaModel转换为OOXML的c:plotArea元素
 */
public class ChartPlotAreaBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartPlotAreaBuilder.class);

    public static final ChartPlotAreaBuilder INSTANCE = new ChartPlotAreaBuilder();

    /**
     * 构建绘图区域配置
     *
     * @param plotArea 绘图区域模型对象
     * @return 绘图区域XNode，如果plotArea为null则返回null
     */
    public XNode buildPlotArea(ChartPlotAreaModel plotArea) {
        if (plotArea == null) {
            return null;
        }

        try {
            XNode plotAreaNode = XNode.make("c:plotArea");

            // 构建手动布局
            buildManualLayout(plotAreaNode, plotArea);

            // 构建图表类型特定配置（包含系列数据）
            buildChartTypeSpecificConfig(plotAreaNode, plotArea);

            // 构建坐标轴
            buildAxes(plotAreaNode, plotArea);

            // 构建形状样式
            buildShapeStyle(plotAreaNode, plotArea);

            return plotAreaNode;

        } catch (Exception e) {
            LOG.warn("Failed to build plot area configuration", e);
            return null;
        }
    }

    /**
     * 构建手动布局
     */
    private void buildManualLayout(XNode plotAreaNode, ChartPlotAreaModel plotArea) {
        try {
            ChartManualLayoutModel manualLayout = plotArea.getManualLayout();
            if (manualLayout != null) {
                XNode layoutNode = ChartManualLayoutBuilder.INSTANCE.buildManualLayout(manualLayout);
                if (layoutNode != null) {
                    plotAreaNode.appendChild(layoutNode);
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build plot area manual layout", e);
        }
    }

    /**
     * 构建图表类型特定配置
     */
    private void buildChartTypeSpecificConfig(XNode plotAreaNode, ChartPlotAreaModel plotArea) {
        try {
            XNode chartTypeNode = ChartTypeConfigBuilder.INSTANCE.buildChartTypeSpecificConfig(plotArea);
            if (chartTypeNode != null) {
                plotAreaNode.appendChild(chartTypeNode);
            } else {
                // 如果没有特定的图表类型配置，创建默认的柱状图
                LOG.debug("No specific chart type configuration found, creating default bar chart");
                buildDefaultChartType(plotAreaNode, plotArea);
            }

        } catch (Exception e) {
            LOG.warn("Failed to build chart type specific configuration", e);
            // 尝试构建默认图表类型
            buildDefaultChartType(plotAreaNode, plotArea);
        }
    }

    /**
     * 构建默认图表类型（柱状图）
     */
    private void buildDefaultChartType(XNode plotAreaNode, ChartPlotAreaModel plotArea) {
        try {
            XNode barChartNode = XNode.make("c:barChart");

            // 添加默认的柱状图配置
            XNode barDirNode = barChartNode.addChild("c:barDir");
            barDirNode.setAttr("val", "col");

            XNode groupingNode = barChartNode.addChild("c:grouping");
            groupingNode.setAttr("val", "clustered");

            // 构建系列数据
            buildSeriesForChartType(barChartNode, plotArea);

            // 构建坐标轴ID
            buildDefaultAxisIds(barChartNode);

            plotAreaNode.appendChild(barChartNode);

        } catch (Exception e) {
            LOG.warn("Failed to build default chart type", e);
        }
    }

    /**
     * 为图表类型构建系列数据
     */
    private void buildSeriesForChartType(XNode chartNode, ChartPlotAreaModel plotArea) {
        try {
            List<ChartSeriesModel> seriesList = plotArea.getSeriesList();
            if (seriesList != null && !seriesList.isEmpty()) {
                for (int i = 0; i < seriesList.size(); i++) {
                    ChartSeriesModel series = seriesList.get(i);
                    XNode seriesNode = ChartSeriesBuilder.INSTANCE.buildSeries(series, i);
                    if (seriesNode != null) {
                        chartNode.appendChild(seriesNode);
                    }
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build series for chart type", e);
        }
    }

    /**
     * 构建默认坐标轴ID
     */
    private void buildDefaultAxisIds(XNode chartNode) {
        try {
            XNode axIdNode1 = chartNode.addChild("c:axId");
            axIdNode1.setAttr("val", "1");

            XNode axIdNode2 = chartNode.addChild("c:axId");
            axIdNode2.setAttr("val", "2");

        } catch (Exception e) {
            LOG.warn("Failed to build default axis IDs", e);
        }
    }

    /**
     * 构建坐标轴
     */
    private void buildAxes(XNode plotAreaNode, ChartPlotAreaModel plotArea) {
        try {
            List<ChartAxisModel> axes = plotArea.getAxes();
            if (axes != null && !axes.isEmpty()) {
                for (ChartAxisModel axis : axes) {
                    XNode axisNode = ChartAxisBuilder.INSTANCE.buildAxis(axis);
                    if (axisNode != null) {
                        plotAreaNode.appendChild(axisNode);
                    }
                }
            } else {
                // 如果没有定义坐标轴，创建默认的坐标轴
                buildDefaultAxes(plotAreaNode);
            }

        } catch (Exception e) {
            LOG.warn("Failed to build plot area axes", e);
            // 尝试构建默认坐标轴
            buildDefaultAxes(plotAreaNode);
        }
    }

    /**
     * 构建默认坐标轴
     */
    private void buildDefaultAxes(XNode plotAreaNode) {
        try {
            // 创建默认的类别轴（X轴）
            XNode catAxNode = XNode.make("c:catAx");
            buildDefaultAxisProperties(catAxNode, "1", "2", "b");
            plotAreaNode.appendChild(catAxNode);

            // 创建默认的数值轴（Y轴）
            XNode valAxNode = XNode.make("c:valAx");
            buildDefaultAxisProperties(valAxNode, "2", "1", "l");
            plotAreaNode.appendChild(valAxNode);

        } catch (Exception e) {
            LOG.warn("Failed to build default axes", e);
        }
    }

    /**
     * 构建默认坐标轴属性
     */
    private void buildDefaultAxisProperties(XNode axisNode, String axisId, String crossAxisId, String position) {
        try {
            // 坐标轴ID
            XNode axIdNode = axisNode.addChild("c:axId");
            axIdNode.setAttr("val", axisId);

            // 坐标轴位置
            XNode axPosNode = axisNode.addChild("c:axPos");
            axPosNode.setAttr("val", position);

            // 交叉轴ID
            XNode crossAxNode = axisNode.addChild("c:crossAx");
            crossAxNode.setAttr("val", crossAxisId);

        } catch (Exception e) {
            LOG.warn("Failed to build default axis properties", e);
        }
    }

    /**
     * 构建形状样式
     */
    private void buildShapeStyle(XNode plotAreaNode, ChartPlotAreaModel plotArea) {
        try {
            ChartShapeStyleModel shapeStyle = plotArea.getShapeStyle();
            if (shapeStyle != null) {
                XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
                if (spPrNode != null) {
                    plotAreaNode.appendChild(spPrNode.withTagName("c:spPr"));
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build plot area shape style", e);
        }
    }

    /**
     * 构建简单的绘图区域（仅包含基本配置）
     * 这是一个便利方法，用于快速创建基本的绘图区域配置
     * 
     * @param seriesList 系列列表
     * @param axesList 坐标轴列表
     * @return 绘图区域XNode，如果seriesList为null或空则返回null
     */
    public XNode buildSimplePlotArea(List<ChartSeriesModel> seriesList, List<ChartAxisModel> axesList) {
        if (seriesList == null || seriesList.isEmpty()) {
            return null;
        }

        // 创建绘图区域模型
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        
        // 添加系列
        for (ChartSeriesModel series : seriesList) {
            plotArea.addSeries(series);
        }
        
        // 添加坐标轴
        if (axesList != null) {
            for (ChartAxisModel axis : axesList) {
                plotArea.addAxis(axis);
            }
        }

        return buildPlotArea(plotArea);
    }

    /**
     * 构建带有手动布局的绘图区域
     * 这是一个便利方法，用于快速创建带有布局的绘图区域配置
     * 
     * @param seriesList 系列列表
     * @param layout 手动布局
     * @return 绘图区域XNode，如果seriesList为null或空则返回null
     */
    public XNode buildPlotAreaWithLayout(List<ChartSeriesModel> seriesList, ChartManualLayoutModel layout) {
        if (seriesList == null || seriesList.isEmpty()) {
            return null;
        }

        // 创建绘图区域模型
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        plotArea.setManualLayout(layout);
        
        // 添加系列
        for (ChartSeriesModel series : seriesList) {
            plotArea.addSeries(series);
        }

        return buildPlotArea(plotArea);
    }
}