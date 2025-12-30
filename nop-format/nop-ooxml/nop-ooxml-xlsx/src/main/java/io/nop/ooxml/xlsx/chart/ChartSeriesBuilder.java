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
import io.nop.core.model.table.CellPosition;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import io.nop.excel.chart.model.ChartDataPointModel;
import io.nop.excel.chart.model.ChartMarkersModel;
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
     * @param index  系列索引
     * @return 系列XNode，如果series为null则返回null
     */
    public XNode buildSeries(ChartSeriesModel series, int index, XNode chartNode) {
        if (series == null) {
            return null;
        }


        XNode serNode = XNode.make("c:ser");

        // 构建基本属性
        buildBasicProperties(serNode, series, index);

        // 构建系列格式化
        buildSeriesFormatting(serNode, series);

        // 构建标记配置 (对于散点图等需要标记的图表类型)
        buildMarkerConfig(serNode, series);

        // 构建数据标签
        buildDataLabels(serNode, series);

        // 构建数据点
        buildDataPoints(serNode, series);

        // 构建系列数据
        buildSeriesValData(serNode, series,chartNode);

        // 构建smooth属性 (折线图、散点图等)
        buildSmoothConfig(serNode, series);

        // 构建趋势线
        buildTrendLines(serNode, series);

        return serNode;

    }

    /**
     * 构建基本属性
     */
    private void buildBasicProperties(XNode serNode, ChartSeriesModel series, int index) {

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

    }

    /**
     * 构建系列名称
     */
    private void buildSeriesName(XNode serNode, ChartSeriesModel series) {
        XNode txNode = serNode.addChild("c:tx");

        // 如果有名称单元格引用，使用引用
        if (!StringHelper.isEmpty(series.getNameCellRef()) && !CellPosition.NONE_STRING.equals(series.getNameCellRef())) {
            XNode strRefNode = txNode.addChild("c:strRef");
            XNode fNode = strRefNode.addChild("c:f");
            fNode.setText(series.getNameCellRef());
        } else {
            // 使用静态文本
            XNode vNode = txNode.addChild("c:v");
            vNode.setText(series.getName());
        }

    }

    /**
     * 构建可见性
     */
    private void buildVisibility(XNode serNode, ChartSeriesModel series) {

        // 如果系列不可见，添加delete元素
        if (!series.isVisible()) {
            XNode deleteNode = serNode.addChild("c:delete");
            deleteNode.setAttr("val", "1");
        }

    }

    /**
     * 构建数据点
     */
    private void buildDataPoints(XNode serNode, ChartSeriesModel series) {
        List<ChartDataPointModel> dataPoints = series.getDataPoints();
        if (dataPoints != null && !dataPoints.isEmpty()) {
            for (int i = 0; i < dataPoints.size(); i++) {
                ChartDataPointModel dataPoint = dataPoints.get(i);
                XNode dPtNode = ChartDataPointBuilder.INSTANCE.buildDataPoint(dataPoint, i);
                if (dPtNode != null) {
                    serNode.appendChild(dPtNode);
                }
            }
        }
    }

    /**
     * 构建系列数据
     */
    private void buildSeriesValData(XNode serNode, ChartSeriesModel series, XNode chartNode) {

        // 检查父节点类型来确定图表类型
        String chartType = chartNode != null ? chartNode.getTagName() : "";

        // 对于散点图，使用xVal和yVal
        if ("c:scatterChart".equals(chartType)) {
            buildScatterSeriesData(serNode, series);
        } else {
            // 对于其他图表类型，使用传统的cat和val
            buildTraditionalSeriesData(serNode, series);
        }
    }

    /**
     * 构建散点图系列数据
     */
    private void buildScatterSeriesData(XNode serNode, ChartSeriesModel series) {
        // 构建X值数据 (c:xVal) - 对应catCellRef
        String catCellRef = series.getCatCellRef();
        if (!StringHelper.isEmpty(catCellRef) && !CellPosition.NONE_STRING.equals(catCellRef)) {
            buildXValCellReference(serNode, catCellRef);
        }

        // 构建Y值数据 (c:yVal) - 对应dataCellRef
        String dataCellRef = series.getDataCellRef();
        if (!StringHelper.isEmpty(dataCellRef)) {
            buildYValCellReference(serNode, dataCellRef);
        }
    }

    /**
     * 构建传统图表系列数据
     */
    private void buildTraditionalSeriesData(XNode serNode, ChartSeriesModel series) {
        // 构建分类数据
        buildSeriesCatData(serNode, series);

        // 构建数值数据
        String dataCellRef = series.getDataCellRef();
        if (!StringHelper.isEmpty(dataCellRef)) {
            buildValCellReference(serNode, dataCellRef);
        }
    }

    /**
     * 构建X值单元格引用 (散点图)
     */
    private void buildXValCellReference(XNode serNode, String xValCellRef) {
        if (StringHelper.isEmpty(xValCellRef)) {
            return;
        }

        // 构建X值数据 (c:xVal)
        XNode xValNode = serNode.addChild("c:xVal");

        // 构建数值引用
        XNode numRefNode = xValNode.addChild("c:numRef");
        XNode fNode = numRefNode.addChild("c:f");
        fNode.setText(xValCellRef);
    }

    /**
     * 构建Y值单元格引用 (散点图)
     */
    private void buildYValCellReference(XNode serNode, String yValCellRef) {
        if (StringHelper.isEmpty(yValCellRef)) {
            return;
        }

        // 构建Y值数据 (c:yVal)
        XNode yValNode = serNode.addChild("c:yVal");

        // 构建数值引用
        XNode numRefNode = yValNode.addChild("c:numRef");
        XNode fNode = numRefNode.addChild("c:f");
        fNode.setText(yValCellRef);
    }

    /**
     * 构建系列分类数据
     */
    private void buildSeriesCatData(XNode serNode, ChartSeriesModel series) {

        String catCellRef = series.getCatCellRef();
        if (StringHelper.isEmpty(catCellRef) || CellPosition.NONE_STRING.equals(catCellRef)) {
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

        if (StringHelper.isEmpty(dataCellRef)) {
            return;
        }

        // 构建数值数据 (c:val) - 这是最常用的数据类型
        XNode valNode = serNode.addChild("c:val");

        // 直接构建单元格引用
        XNode numRefNode = valNode.addChild("c:numRef");
        XNode fNode = numRefNode.addChild("c:f");
        fNode.setText(dataCellRef);

    }


    /**
     * 构建系列格式化
     */
    private void buildSeriesFormatting(XNode serNode, ChartSeriesModel series) {
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

    }

    /**
     * 构建数据标签
     */
    private void buildDataLabels(XNode serNode, ChartSeriesModel series) {

        ChartDataLabelsModel dataLabels = series.getDataLabels();
        if (dataLabels != null) {
            XNode dLblsNode = ChartDataLabelsBuilder.INSTANCE.buildDataLabels(dataLabels);
            if (dLblsNode != null) {
                serNode.appendChild(dLblsNode.withTagName("c:dLbls"));
            }
        }

    }

    /**
     * 构建标记配置
     */
    private void buildMarkerConfig(XNode serNode, ChartSeriesModel series) {
        // 检查系列是否有markers配置
        ChartMarkersModel markers = series.getMarkers();
        if (markers != null && markers.getEnabled() != null && markers.getEnabled()) {
            XNode markerNode = serNode.addChild("c:marker");
            
            // 构建标记符号
            if (markers.getType() != null) {
                XNode symbolNode = markerNode.addChild("c:symbol");
                symbolNode.setAttr("val", markers.getType().value());
            }
            
            // 构建标记大小
            if (markers.getSize() != null) {
                XNode sizeNode = markerNode.addChild("c:size");
                sizeNode.setAttr("val", String.valueOf(markers.getSize().intValue()));
            }
            
            // 构建标记的形状样式 - 使用现有的ChartShapeStyleBuilder
            if (markers.getShapeStyle() != null) {
                XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(markers.getShapeStyle());
                if (spPrNode != null) {
                    markerNode.appendChild(spPrNode.withTagName("c:spPr"));
                }
            }
        }
    }

    /**
     * 构建smooth配置
     */
    private void buildSmoothConfig(XNode serNode, ChartSeriesModel series) {
        // 构建smooth属性
        if (series.getSmooth() != null) {
            XNode smoothNode = serNode.addChild("c:smooth");
            smoothNode.setAttr("val", series.getSmooth() ? "1" : "0");
        }
    }

    /**
     * 构建趋势线
     */
    private void buildTrendLines(XNode serNode, ChartSeriesModel series) {

        List<ChartTrendLineModel> trendLines = series.getTrendLines();
        if (trendLines != null && !trendLines.isEmpty()) {
            for (ChartTrendLineModel trendLine : trendLines) {
                XNode trendLineNode = ChartTrendLineBuilder.INSTANCE.buildTrendLine(trendLine);
                if (trendLineNode != null) {
                    serNode.appendChild(trendLineNode);
                }
            }
        }

    }
}