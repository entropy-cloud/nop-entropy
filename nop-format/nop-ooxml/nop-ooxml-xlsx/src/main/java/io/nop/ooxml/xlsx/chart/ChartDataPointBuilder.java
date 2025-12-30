package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartDataPointModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartDataPointBuilder - 图表数据点构建器
 * 负责生成OOXML图表中的数据点配置
 * 将内部的ChartDataPointModel转换为OOXML的c:dPt元素
 */
public class ChartDataPointBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataPointBuilder.class);
    public static final ChartDataPointBuilder INSTANCE = new ChartDataPointBuilder();

    /**
     * 构建数据点元素
     *
     * @param dataPoint 数据点模型对象
     * @param index     数据点索引
     * @return 数据点XNode，如果dataPoint为null则返回null
     */
    public XNode buildDataPoint(ChartDataPointModel dataPoint, int index) {
        if (dataPoint == null) {
            return null;
        }

        XNode dPtNode = XNode.make("c:dPt");

        // 构建数据点索引
        buildDataPointIndex(dPtNode, dataPoint.getIndex());

        // 构建气泡3D属性
        buildBubble3D(dPtNode, dataPoint);

        // 构建爆炸值
        buildExplosion(dPtNode, dataPoint);

        // 构建形状样式
        buildShapeStyle(dPtNode, dataPoint);

        return dPtNode;
    }

    /**
     * 构建数据点索引
     */
    private void buildDataPointIndex(XNode dPtNode, int index) {
        XNode idxNode = dPtNode.addChild("c:idx");
        idxNode.setAttr("val", String.valueOf(index));
    }

    /**
     * 构建气泡3D属性
     */
    private void buildBubble3D(XNode dPtNode, ChartDataPointModel dataPoint) {
        Boolean bubble3D = dataPoint.getBubble3D();
        if (bubble3D != null) {
            XNode bubble3DNode = dPtNode.addChild("c:bubble3D");
            bubble3DNode.setAttr("val", bubble3D ? "1" : "0");
        }
    }

    /**
     * 构建爆炸值
     */
    private void buildExplosion(XNode dPtNode, ChartDataPointModel dataPoint) {
        Double explosion = dataPoint.getExplosion();
        if (explosion != null) {
            XNode explosionNode = dPtNode.addChild("c:explosion");
            // 将百分比值转换为整数 (OOXML中explosion通常是百分比值)
            int explosionInt = (int) Math.round(explosion * 100);
            explosionNode.setAttr("val", String.valueOf(explosionInt));
        }
    }

    /**
     * 构建形状样式
     */
    private void buildShapeStyle(XNode dPtNode, ChartDataPointModel dataPoint) {
        ChartShapeStyleModel shapeStyle = dataPoint.getShapeStyle();
        if (shapeStyle != null) {
            XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
            if (spPrNode != null) {
                dPtNode.appendChild(spPrNode.withTagName("c:spPr"));
            }
        }
    }
}