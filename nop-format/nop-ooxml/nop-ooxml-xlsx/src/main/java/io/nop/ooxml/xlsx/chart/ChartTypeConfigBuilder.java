/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartBarDirection;
import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.model.ChartAreaConfigModel;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.excel.chart.model.ChartDoughnutConfigModel;
import io.nop.excel.chart.model.ChartLineConfigModel;
import io.nop.excel.chart.model.ChartPieConfigModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ChartTypeConfigBuilder - 图表类型特定配置构建器
 * 负责生成各种图表类型的特定配置，如柱状图、饼图、折线图等的专有属性
 * 将内部的图表类型配置模型转换为OOXML的图表类型元素
 */
public class ChartTypeConfigBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTypeConfigBuilder.class);

    public static final ChartTypeConfigBuilder INSTANCE = new ChartTypeConfigBuilder();

    /**
     * 构建图表类型特定配置
     *
     * @param plotArea 绘图区域模型
     * @return 图表类型配置XNode，如果plotArea为null则返回null
     */
    public XNode buildChartTypeSpecificConfig(ChartPlotAreaModel plotArea) {
        if (plotArea == null) {
            return null;
        }

        try {
            // 检测并构建柱状图配置
            if (plotArea.getBarConfig() != null) {
                return buildBarChartConfig(plotArea);
            }

            // 检测并构建饼图配置
            if (plotArea.getPieConfig() != null) {
                return buildPieChartConfig(plotArea);
            }

            // 检测并构建折线图配置
            if (plotArea.getLineConfig() != null) {
                return buildLineChartConfig(plotArea);
            }

            // 检测并构建面积图配置
            if (plotArea.getAreaConfig() != null) {
                return buildAreaChartConfig(plotArea);
            }

            // 其他图表类型的处理
            return buildOtherChartTypes(plotArea);

        } catch (Exception e) {
            LOG.warn("Failed to build chart type specific configuration", e);
            return null;
        }
    }

    /**
     * 构建柱状图特定配置
     */
    public XNode buildBarChartConfig(ChartPlotAreaModel plotArea) {
        try {
            ChartBarConfigModel barConfig = plotArea.getBarConfig();
            if (barConfig == null) {
                return null;
            }

            // 根据是否3D决定元素名称
            String chartTagName = barConfig.getIs3D() != null && barConfig.getIs3D() ? "c:bar3DChart" : "c:barChart";
            XNode barChartNode = XNode.make(chartTagName);

            // 构建柱状图方向
            if (barConfig.getDir() != null) {
                XNode barDirNode = barChartNode.addChild("c:barDir");
                barDirNode.setAttr("val", barConfig.getDir().value());
            }

            // 构建分组方式
            if (barConfig.getGrouping() != null) {
                XNode groupingNode = barChartNode.addChild("c:grouping");
                groupingNode.setAttr("val", barConfig.getGrouping().value());
            }

            // 构建系列数据
            buildSeriesForChartType(barChartNode, plotArea);

            // 构建间隙宽度
            if (barConfig.getPercentGapWidth() != null) {
                XNode gapWidthNode = barChartNode.addChild("c:gapWidth");
                gapWidthNode.setAttr("val", barConfig.getPercentGapWidth().toString());
            }

            // 构建重叠比例
            if (barConfig.getPercentOverlap() != null) {
                XNode overlapNode = barChartNode.addChild("c:overlap");
                overlapNode.setAttr("val", barConfig.getPercentOverlap().toString());
            }

            // 构建坐标轴ID
            buildAxisIds(barChartNode, plotArea);

            return barChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build bar chart configuration", e);
            return null;
        }
    }

    /**
     * 构建饼图特定配置
     */
    public XNode buildPieChartConfig(ChartPlotAreaModel plotArea) {
        try {
            ChartPieConfigModel pieConfig = plotArea.getPieConfig();
            if (pieConfig == null) {
                return null;
            }

            // 根据是否3D决定元素名称
            String chartTagName = pieConfig.getIs3D() != null && pieConfig.getIs3D() ? "c:pie3DChart" : "c:pieChart";
            XNode pieChartNode = XNode.make(chartTagName);

            // 构建颜色变化
            XNode varyColorsNode = pieChartNode.addChild("c:varyColors");
            varyColorsNode.setAttr("val", "1"); // 饼图通常使用不同颜色

            // 构建系列数据
            buildSeriesForChartType(pieChartNode, plotArea);

            // 构建第一个扇区角度
            if (pieConfig.getStartAngle() != null) {
                XNode firstSliceAngNode = pieChartNode.addChild("c:firstSliceAng");
                firstSliceAngNode.setAttr("val", pieConfig.getStartAngle().toString());
            }

            return pieChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build pie chart configuration", e);
            return null;
        }
    }

    /**
     * 构建折线图特定配置
     */
    public XNode buildLineChartConfig(ChartPlotAreaModel plotArea) {
        try {
            ChartLineConfigModel lineConfig = plotArea.getLineConfig();
            if (lineConfig == null) {
                return null;
            }

            // 根据是否3D决定元素名称
            String chartTagName = lineConfig.getIs3D() != null && lineConfig.getIs3D() ? "c:line3DChart" : "c:lineChart";
            XNode lineChartNode = XNode.make(chartTagName);

            // 构建分组方式
            if (lineConfig.getGrouping() != null) {
                XNode groupingNode = lineChartNode.addChild("c:grouping");
                groupingNode.setAttr("val", lineConfig.getGrouping().value());
            }

            // 构建系列数据
            buildSeriesForChartType(lineChartNode, plotArea);

            // 构建是否显示标记
            if (lineConfig.getMarker() != null) {
                XNode markerNode = lineChartNode.addChild("c:marker");
                markerNode.setAttr("val", lineConfig.getMarker() ? "1" : "0");
            }

            // 构建是否平滑曲线
            if (lineConfig.getSmooth() != null) {
                XNode smoothNode = lineChartNode.addChild("c:smooth");
                smoothNode.setAttr("val", lineConfig.getSmooth() ? "1" : "0");
            }

            // 构建坐标轴ID
            buildAxisIds(lineChartNode, plotArea);

            return lineChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build line chart configuration", e);
            return null;
        }
    }

    /**
     * 构建面积图特定配置
     */
    public XNode buildAreaChartConfig(ChartPlotAreaModel plotArea) {
        try {
            ChartAreaConfigModel areaConfig = plotArea.getAreaConfig();
            if (areaConfig == null) {
                return null;
            }

            // 根据是否3D决定元素名称
            String chartTagName = areaConfig.getIs3D() != null && areaConfig.getIs3D() ? "c:area3DChart" : "c:areaChart";
            XNode areaChartNode = XNode.make(chartTagName);

            // 构建分组方式
            if (areaConfig.getGrouping() != null) {
                XNode groupingNode = areaChartNode.addChild("c:grouping");
                groupingNode.setAttr("val", areaConfig.getGrouping().value());
            }

            // 构建系列数据
            buildSeriesForChartType(areaChartNode, plotArea);

            // 构建坐标轴ID
            buildAxisIds(areaChartNode, plotArea);

            return areaChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build area chart configuration", e);
            return null;
        }
    }

    /**
     * 构建其他图表类型配置
     */
    private XNode buildOtherChartTypes(ChartPlotAreaModel plotArea) {
        try {
            // 检测并构建散点图配置
            if (plotArea.getScatterConfig() != null) {
                return buildScatterChartConfig(plotArea);
            }

            // 检测并构建气泡图配置
            if (plotArea.getBubbleConfig() != null) {
                return buildBubbleChartConfig(plotArea);
            }

            // 检测并构建雷达图配置
            if (plotArea.getRadarConfig() != null) {
                return buildRadarChartConfig(plotArea);
            }

            // 检测并构建股票图配置
            if (plotArea.getStockConfig() != null) {
                return buildStockChartConfig(plotArea);
            }

            // 检测并构建曲面图配置
            if (plotArea.getSurfaceConfig() != null) {
                return buildSurfaceChartConfig(plotArea);
            }

            // 检测并构建环形图配置
            if (plotArea.getDoughnutConfig() != null) {
                return buildDoughnutChartConfig(plotArea);
            }

            LOG.debug("No specific chart type configuration found, using default");
            return null;

        } catch (Exception e) {
            LOG.warn("Failed to build other chart types configuration", e);
            return null;
        }
    }

    /**
     * 构建散点图配置
     */
    private XNode buildScatterChartConfig(ChartPlotAreaModel plotArea) {
        try {
            XNode scatterChartNode = XNode.make("c:scatterChart");

            // 构建系列数据
            buildSeriesForChartType(scatterChartNode, plotArea);

            // 构建坐标轴ID
            buildAxisIds(scatterChartNode, plotArea);

            return scatterChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build scatter chart configuration", e);
            return null;
        }
    }

    /**
     * 构建气泡图配置
     */
    private XNode buildBubbleChartConfig(ChartPlotAreaModel plotArea) {
        try {
            XNode bubbleChartNode = XNode.make("c:bubbleChart");

            // 构建系列数据
            buildSeriesForChartType(bubbleChartNode, plotArea);

            // 构建坐标轴ID
            buildAxisIds(bubbleChartNode, plotArea);

            return bubbleChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build bubble chart configuration", e);
            return null;
        }
    }

    /**
     * 构建雷达图配置
     */
    private XNode buildRadarChartConfig(ChartPlotAreaModel plotArea) {
        try {
            XNode radarChartNode = XNode.make("c:radarChart");

            // 构建系列数据
            buildSeriesForChartType(radarChartNode, plotArea);

            return radarChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build radar chart configuration", e);
            return null;
        }
    }

    /**
     * 构建股票图配置
     */
    private XNode buildStockChartConfig(ChartPlotAreaModel plotArea) {
        try {
            XNode stockChartNode = XNode.make("c:stockChart");

            // 构建系列数据
            buildSeriesForChartType(stockChartNode, plotArea);

            // 构建坐标轴ID
            buildAxisIds(stockChartNode, plotArea);

            return stockChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build stock chart configuration", e);
            return null;
        }
    }

    /**
     * 构建曲面图配置
     */
    private XNode buildSurfaceChartConfig(ChartPlotAreaModel plotArea) {
        try {
            XNode surfaceChartNode = XNode.make("c:surfaceChart");

            // 构建系列数据
            buildSeriesForChartType(surfaceChartNode, plotArea);

            // 构建坐标轴ID
            buildAxisIds(surfaceChartNode, plotArea);

            return surfaceChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build surface chart configuration", e);
            return null;
        }
    }

    /**
     * 构建环形图配置
     */
    private XNode buildDoughnutChartConfig(ChartPlotAreaModel plotArea) {
        try {
            ChartDoughnutConfigModel doughnutConfig = plotArea.getDoughnutConfig();
            if (doughnutConfig == null) {
                return null;
            }

            // 根据是否3D决定元素名称
            String chartTagName = doughnutConfig.getIs3D() != null && doughnutConfig.getIs3D() ? "c:doughnut3DChart" : "c:doughnutChart";
            XNode doughnutChartNode = XNode.make(chartTagName);

            // 构建颜色变化
            if (doughnutConfig.getVaryColors() != null) {
                XNode varyColorsNode = doughnutChartNode.addChild("c:varyColors");
                varyColorsNode.setAttr("val", doughnutConfig.getVaryColors() ? "1" : "0");
            }

            // 构建系列数据
            buildSeriesForChartType(doughnutChartNode, plotArea);

            // 构建第一个扇区角度
            if (doughnutConfig.getStartAngle() != null) {
                XNode firstSliceAngNode = doughnutChartNode.addChild("c:firstSliceAng");
                firstSliceAngNode.setAttr("val", doughnutConfig.getStartAngle().toString());
            }

            // 构建内半径（通过 holeSize）
            if (doughnutConfig.getHoleSize() != null) {
                XNode holeSizeNode = doughnutChartNode.addChild("c:holeSize");
                holeSizeNode.setAttr("val", doughnutConfig.getHoleSize().toString());
            }

            return doughnutChartNode;

        } catch (Exception e) {
            LOG.warn("Failed to build doughnut chart configuration", e);
            return null;
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
     * 构建坐标轴ID
     */
    private void buildAxisIds(XNode chartNode, ChartPlotAreaModel plotArea) {
        try {
            // 构建坐标轴ID引用
            if(plotArea.getAxes() != null) {
                for(ChartAxisModel axisModel: plotArea.getAxes()) {
                    XNode axIdNode1 = chartNode.addChild("c:axId");
                    axIdNode1.setAttr("val", axisModel.getId());
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build axis IDs", e);
        }
    }

    /**
     * 构建简单的柱状图配置
     * 这是一个便利方法，用于快速创建基本的柱状图配置
     * 
     * @param direction 柱状图方向
     * @param grouping 分组方式
     * @param gapWidth 间隙宽度百分比
     * @return 柱状图XNode，如果direction为null则返回null
     */
    public XNode buildSimpleBarChart(ChartBarDirection direction, ChartBarGrouping grouping, Double gapWidth) {
        if (direction == null) {
            return null;
        }

        // 创建柱状图配置模型
        ChartBarConfigModel barConfig = new ChartBarConfigModel();
        barConfig.setDir(direction);
        barConfig.setGrouping(grouping);
        barConfig.setPercentGapWidth(gapWidth);

        // 创建绘图区域模型
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        plotArea.setBarConfig(barConfig);

        return buildBarChartConfig(plotArea);
    }

    /**
     * 构建简单的饼图配置
     * 这是一个便利方法，用于快速创建基本的饼图配置
     * 
     * @param startAngle 起始角度
     * @param is3D 是否3D
     * @return 饼图XNode
     */
    public XNode buildSimplePieChart(Double startAngle, Boolean is3D) {
        // 创建饼图配置模型
        ChartPieConfigModel pieConfig = new ChartPieConfigModel();
        pieConfig.setStartAngle(startAngle);
        pieConfig.setIs3D(is3D);

        // 创建绘图区域模型
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        plotArea.setPieConfig(pieConfig);

        return buildPieChartConfig(plotArea);
    }
}