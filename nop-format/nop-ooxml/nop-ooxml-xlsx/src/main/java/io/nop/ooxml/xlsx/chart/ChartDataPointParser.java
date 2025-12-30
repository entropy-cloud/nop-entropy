package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartDataPointModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartDataPointParser - 图表数据点解析器
 * 负责解析Excel图表中的数据点配置
 * 对应OOXML中的c:dPt元素
 */
public class ChartDataPointParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataPointParser.class);
    public static final ChartDataPointParser INSTANCE = new ChartDataPointParser();

    /**
     * 解析单个数据点
     *
     * @param dPtNode       数据点节点 (c:dPt)
     * @param styleProvider 样式提供者
     * @return 数据点模型对象
     */
    public ChartDataPointModel parseDataPoint(XNode dPtNode, IChartStyleProvider styleProvider) {
        if (dPtNode == null) {
            LOG.warn("Data point node is null, returning null");
            return null;
        }

        ChartDataPointModel dataPoint = new ChartDataPointModel();

        // 解析数据点索引 (c:idx)
        parseDataPointIndex(dataPoint, dPtNode);

        // 解析气泡3D属性 (c:bubble3D)
        parseBubble3D(dataPoint, dPtNode);

        // 解析爆炸值 (c:explosion) - 用于饼图
        parseExplosion(dataPoint, dPtNode);

        // 解析形状样式 (c:spPr)
        parseShapeStyle(dataPoint, dPtNode, styleProvider);

        return dataPoint;
    }

    /**
     * 解析数据点索引
     * 注意：索引信息通常用于确定数据点在系列中的位置，但在ChartDataPointModel中可能不直接存储
     */
    private void parseDataPointIndex(ChartDataPointModel dataPoint, XNode dPtNode) {
        Integer idx = ChartPropertyHelper.getChildIntVal(dPtNode, "c:idx");
        if (idx != null) {
            dataPoint.setIndex(idx);
        }
    }

    /**
     * 解析气泡3D属性
     */
    private void parseBubble3D(ChartDataPointModel dataPoint, XNode dPtNode) {
        Boolean bubble3D = ChartPropertyHelper.getChildBoolVal(dPtNode, "c:bubble3D");
        if (bubble3D != null) {
            dataPoint.setBubble3D(bubble3D);
            LOG.debug("Data point bubble3D: {}", bubble3D);
        }
    }

    /**
     * 解析爆炸值 (用于饼图)
     */
    private void parseExplosion(ChartDataPointModel dataPoint, XNode dPtNode) {
        Integer explosion = ChartPropertyHelper.getChildIntVal(dPtNode, "c:explosion");
        if (explosion != null) {
            // 将整数值转换为百分比 (OOXML中explosion通常是百分比值)
            double explosionPercent = explosion / 100.0;
            dataPoint.setExplosion(explosionPercent);
            LOG.debug("Data point explosion: {}%", explosion);
        }
    }

    /**
     * 解析形状样式
     */
    private void parseShapeStyle(ChartDataPointModel dataPoint, XNode dPtNode, IChartStyleProvider styleProvider) {
        XNode spPrNode = dPtNode.childByTag("c:spPr");
        if (spPrNode != null) {
            ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
            if (shapeStyle != null) {
                dataPoint.setShapeStyle(shapeStyle);
                LOG.debug("Data point shape style parsed successfully");
            }
        }
    }
}