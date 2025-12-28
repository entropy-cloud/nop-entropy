/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTrendLineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ChartSeriesBuilder - 图表系列构建器
 * 负责生成OOXML图表中的数据系列配置
 * 将内部的ChartSeriesModel转换为OOXML的c:ser元素
 */
public class ChartSeriesBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartSeriesBuilder.class);

    public static final ChartSeriesBuilder INSTANCE = new ChartSeriesBuilder();

    /**
     * 构建系列元素
     * 
     * @param series 系列模型对象
     * @param index 系列索引
     * @return 系列XNode，如果series为null则返回null
     */
    public XNode buildSeries(ChartSeriesModel series, int index) {
        if (series == null) {
            return null;
        }

        try {
            XNode serNode = XNode.make("c:ser");

            // 构建基本属性
            buildBasicProperties(serNode, series, index);

            // 构建系列格式化
            buildSeriesFormatting(serNode, series);

            // 构建数据标签
            buildDataLabels(serNode, series);

            // 构建系列数据
            buildSeriesValData(serNode, series);

            // 构建趋势线
            buildTrendLines(serNode, series);

            return serNode;

        } catch (Exception e) {
            LOG.warn("Failed to build series configuration", e);
            return null;
        }
    }

    /**
     * 构建基本属性
     */
    private void buildBasicProperties(XNode serNode, ChartSeriesModel series, int index) {
        try {
            // 构建系列索引
            XNode idxNode = serNode.addChild("c:idx");
            idxNode.setAttr("val", String.valueOf(index));

            // 构建系列顺序
            XNode orderNode = serNode.addChild("c:order");
            orderNode.setAttr("val", String.valueOf(index));

            // 构建系列名称
            buildSeriesName(serNode, series);

            // 构建可见性
            buildVisibility(serNode, series);

        } catch (Exception e) {
            LOG.warn("Failed to build series basic properties", e);
        }
    }

    /**
     * 构建系列名称
     */
    private void buildSeriesName(XNode serNode, ChartSeriesModel series) {
        try {
            if (!StringHelper.isEmpty(series.getName())) {
                XNode txNode = serNode.addChild("c:tx");
                
                // 如果有名称单元格引用，使用引用
                if (!StringHelper.isEmpty(series.getNameCellRef())) {
                    XNode strRefNode = txNode.addChild("c:strRef");
                    XNode fNode = strRefNode.addChild("c:f");
                    fNode.setText(series.getNameCellRef());
                } else {
                    // 使用静态文本
                    XNode vNode = txNode.addChild("c:v");
                    vNode.setText(series.getName());
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build series name", e);
        }
    }

    /**
     * 构建可见性
     */
    private void buildVisibility(XNode serNode, ChartSeriesModel series) {
        try {
            // 如果系列不可见，添加delete元素
            if (!series.isVisible()) {
                XNode deleteNode = serNode.addChild("c:delete");
                deleteNode.setAttr("val", "1");
            }

        } catch (Exception e) {
            LOG.warn("Failed to build series visibility", e);
        }
    }

    /**
     * 构建系列数据
     */
    private void buildSeriesValData(XNode serNode, ChartSeriesModel series) {
        try {
            // 构建分类数据
            buildSeriesCatData(serNode, series);

            // 构建数值数据
            String dataCellRef = series.getDataCellRef();
            if (!StringHelper.isEmpty(dataCellRef)) {
                buildValCellReference(serNode, dataCellRef);
            }

        } catch (Exception e) {
            LOG.warn("Failed to build series data", e);
        }
    }

    /**
     * 构建系列分类数据
     */
    private void buildSeriesCatData(XNode serNode, ChartSeriesModel series) {
        try {
            String catCellRef = series.getCatCellRef();
            if (StringHelper.isEmpty(catCellRef)) {
                return;
            }

            // 构建分类数据 (c:cat)
            XNode catNode = serNode.addChild("c:cat");
            
            // 构建字符串引用或数值引用
            if (isNumericReference(catCellRef)) {
                // 数值类型的分类数据
                XNode numRefNode = catNode.addChild("c:numRef");
                XNode fNode = numRefNode.addChild("c:f");
                fNode.setText(catCellRef);
            } else {
                // 字符串类型的分类数据（更常见）
                XNode strRefNode = catNode.addChild("c:strRef");
                XNode fNode = strRefNode.addChild("c:f");
                fNode.setText(catCellRef);
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to build series category data", e);
        }
    }

    /**
     * 判断是否为数值引用
     * 简单的启发式判断，可以根据需要扩展
     */
    private boolean isNumericReference(String cellRef) {
        // 这里可以根据实际需求进行更复杂的判断
        // 目前简单假设大部分分类数据都是字符串类型
        return false;
    }

    /**
     * 构建单元格引用数据
     */
    private void buildValCellReference(XNode serNode, String dataCellRef) {
        try {
            if (StringHelper.isEmpty(dataCellRef)) {
                return;
            }

            // 构建数值数据 (c:val) - 这是最常用的数据类型
            XNode valNode = serNode.addChild("c:val");
            
            // 直接构建单元格引用
            XNode numRefNode = valNode.addChild("c:numRef");
            XNode fNode = numRefNode.addChild("c:f");
            fNode.setText(dataCellRef);
            
        } catch (Exception e) {
            LOG.warn("Failed to build cell reference data", e);
        }
    }



    /**
     * 构建系列格式化
     */
    private void buildSeriesFormatting(XNode serNode, ChartSeriesModel series) {
        try {
            // 构建形状样式
            ChartShapeStyleModel shapeStyle = series.getShapeStyle();
            if (shapeStyle != null) {
                XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
                if (spPrNode != null) {
                    serNode.appendChild(spPrNode.withTagName("c:spPr"));
                }
            }

            // 构建反转负值颜色
            if (series.getInvertIfNegative() != null && series.getInvertIfNegative()) {
                XNode invertNode = serNode.addChild("c:invertIfNegative");
                invertNode.setAttr("val", "1");
            }

        } catch (Exception e) {
            LOG.warn("Failed to build series formatting", e);
        }
    }

    /**
     * 构建数据标签
     */
    private void buildDataLabels(XNode serNode, ChartSeriesModel series) {
        try {
            ChartDataLabelsModel dataLabels = series.getDataLabels();
            if (dataLabels != null) {
                XNode dLblsNode = ChartDataLabelsBuilder.INSTANCE.buildDataLabels(dataLabels);
                if (dLblsNode != null) {
                    serNode.appendChild(dLblsNode.withTagName("c:dLbls"));
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build data labels", e);
        }
    }

    /**
     * 构建趋势线
     */
    private void buildTrendLines(XNode serNode, ChartSeriesModel series) {
        try {
            List<ChartTrendLineModel> trendLines = series.getTrendLines();
            if (trendLines != null && !trendLines.isEmpty()) {
                for (ChartTrendLineModel trendLine : trendLines) {
                    XNode trendLineNode = ChartTrendLineBuilder.INSTANCE.buildTrendLine(trendLine);
                    if (trendLineNode != null) {
                        serNode.appendChild(trendLineNode);
                    }
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build trend lines", e);
        }
    }

    /**
     * 构建简单的系列（仅包含基本属性）
     * 这是一个便利方法，用于快速创建基本的系列配置
     * 
     * @param name 系列名称
     * @param dataRef 数据单元格引用
     * @param index 系列索引
     * @return 系列XNode，如果name为null则返回null
     */
    public XNode buildSimpleSeries(String name, String dataRef, int index) {
        if (StringHelper.isEmpty(name)) {
            return null;
        }

        // 创建系列模型
        ChartSeriesModel series = new ChartSeriesModel();
        series.setName(name);

        // 设置数据引用
        if (!StringHelper.isEmpty(dataRef)) {
            series.setDataCellRef(dataRef);
        }

        return buildSeries(series, index);
    }
}