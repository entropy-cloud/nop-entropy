/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartLegendPosition;
import io.nop.excel.chart.constants.ChartOrientation;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelVerticalAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartLegendBuilder - 图例构建器
 * 负责生成OOXML图表中的图例配置
 * 将内部的ChartLegendModel转换为OOXML的c:legend元素
 */
public class ChartLegendBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartLegendBuilder.class);

    public static final ChartLegendBuilder INSTANCE = new ChartLegendBuilder();

    /**
     * 构建图例配置
     *
     * @param legend 图例模型对象
     * @return 图例XNode，如果legend为null则返回null
     */
    public XNode buildLegend(ChartLegendModel legend) {
        if (legend == null) {
            return null;
        }

        XNode legendNode = XNode.make("c:legend");

        // 构建可见性
        buildVisibility(legendNode, legend);

        // 构建位置
        buildPosition(legendNode, legend);

        // 构建对齐方式
        buildAlignment(legendNode, legend);

        // 构建方向
        buildOrientation(legendNode, legend);

        // 构建覆盖选项
        buildOverlay(legendNode, legend);

        // 构建手动布局
        buildManualLayout(legendNode, legend);

        // 构建样式
        buildStyles(legendNode, legend);

        return legendNode;

    }

    /**
     * 构建可见性
     */
    private void buildVisibility(XNode legendNode, ChartLegendModel legend) {

        // 如果图例不可见，添加delete元素
        if (!legend.isVisible()) {
            XNode deleteNode = legendNode.addChild("c:delete");
            deleteNode.setAttr("val", "1");
        }

    }

    /**
     * 构建位置
     */
    private void buildPosition(XNode legendNode, ChartLegendModel legend) {

        if (legend.getPosition() != null) {
            XNode legendPosNode = legendNode.addChild("c:legendPos");
            legendPosNode.setAttr("val", legend.getPosition().value());
        }

    }

    /**
     * 构建对齐方式
     */
    private void buildAlignment(XNode legendNode, ChartLegendModel legend) {

        // 构建水平对齐
        if (legend.getAlign() != null) {
            XNode alignNode = legendNode.addChild("c:align");
            alignNode.setAttr("val", mapHorizontalAlignmentToOoxml(legend.getAlign()));
        }

        // 构建垂直对齐
        if (legend.getVerticalAlign() != null) {
            XNode verticalAlignNode = legendNode.addChild("c:verticalAlign");
            verticalAlignNode.setAttr("val", mapVerticalAlignmentToOoxml(legend.getVerticalAlign()));
        }

    }

    /**
     * 映射水平对齐到OOXML值
     */
    private String mapHorizontalAlignmentToOoxml(ExcelHorizontalAlignment align) {
        if (align == null) {
            return "left";
        }

        switch (align) {
            case LEFT:
                return "left";
            case CENTER:
                return "center";
            case RIGHT:
                return "right";
            case JUSTIFY:
                return "justify";
            default:
                LOG.warn("Unknown horizontal alignment: {}, using default left", align);
                return "left";
        }
    }

    /**
     * 映射垂直对齐到OOXML值
     */
    private String mapVerticalAlignmentToOoxml(ExcelVerticalAlignment verticalAlign) {
        if (verticalAlign == null) {
            return "center";
        }

        switch (verticalAlign) {
            case TOP:
                return "top";
            case CENTER:
                return "center";
            case BOTTOM:
                return "bottom";
            case JUSTIFY:
                return "justify";
            default:
                LOG.warn("Unknown vertical alignment: {}, using default center", verticalAlign);
                return "center";
        }
    }

    /**
     * 构建方向
     */
    private void buildOrientation(XNode legendNode, ChartLegendModel legend) {

        if (legend.getOrientation() != null) {
            XNode orientationNode = legendNode.addChild("c:orientation");
            orientationNode.setAttr("val", mapOrientationToOoxml(legend.getOrientation()));
        }

    }

    /**
     * 映射方向到OOXML值
     */
    private String mapOrientationToOoxml(ChartOrientation orientation) {
        if (orientation == null) {
            return "horizontal";
        }

        switch (orientation) {
            case HORIZONTAL:
                return "horizontal";
            case VERTICAL:
                return "vertical";
            default:
                LOG.warn("Unknown orientation: {}, using default horizontal", orientation);
                return "horizontal";
        }
    }

    /**
     * 构建覆盖选项
     */
    private void buildOverlay(XNode legendNode, ChartLegendModel legend) {

        if (legend.getOverlay() != null) {
            XNode overlayNode = legendNode.addChild("c:overlay");
            overlayNode.setAttr("val", legend.getOverlay() ? "1" : "0");
        }

    }

    /**
     * 构建手动布局
     */
    private void buildManualLayout(XNode legendNode, ChartLegendModel legend) {

        ChartManualLayoutModel manualLayout = legend.getManualLayout();
        if (manualLayout != null) {
            XNode layoutNode = ChartManualLayoutBuilder.INSTANCE.buildManualLayout(manualLayout);
            if (layoutNode != null) {
                legendNode.appendChild(layoutNode);
            }
        }

    }

    /**
     * 构建样式
     */
    private void buildStyles(XNode legendNode, ChartLegendModel legend) {

        // 构建形状样式
        ChartShapeStyleModel shapeStyle = legend.getShapeStyle();
        if (shapeStyle != null) {
            XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
            if (spPrNode != null) {
                legendNode.appendChild(spPrNode.withTagName("c:spPr"));
            }
        }

        // 构建文本样式
        ChartTextStyleModel textStyle = legend.getTextStyle();
        if (textStyle != null) {
            XNode txPrNode = ChartTextStyleBuilder.INSTANCE.buildTextStyle(textStyle);
            if (txPrNode != null) {
                legendNode.appendChild(txPrNode.withTagName("c:txPr"));
            }
        }

    }

    /**
     * 构建简单的图例（仅包含位置）
     * 这是一个便利方法，用于快速创建基本的图例配置
     *
     * @param position 图例位置
     * @return 图例XNode，如果position为null则返回null
     */
    public XNode buildSimpleLegend(ChartLegendPosition position) {
        if (position == null) {
            return null;
        }

        // 创建图例模型
        ChartLegendModel legend = new ChartLegendModel();
        legend.setPosition(position);
        legend.setVisible(true);

        return buildLegend(legend);
    }

    /**
     * 构建带有对齐的图例
     * 这是一个便利方法，用于快速创建带有对齐的图例配置
     *
     * @param position        图例位置
     * @param horizontalAlign 水平对齐
     * @param verticalAlign   垂直对齐
     * @return 图例XNode，如果position为null则返回null
     */
    public XNode buildLegendWithAlignment(ChartLegendPosition position,
                                          ExcelHorizontalAlignment horizontalAlign,
                                          ExcelVerticalAlignment verticalAlign) {
        if (position == null) {
            return null;
        }

        // 创建图例模型
        ChartLegendModel legend = new ChartLegendModel();
        legend.setPosition(position);
        legend.setAlign(horizontalAlign);
        legend.setVerticalAlign(verticalAlign);
        legend.setVisible(true);

        return buildLegend(legend);
    }

    /**
     * 构建带有布局的图例
     * 这是一个便利方法，用于快速创建带有布局的图例配置
     *
     * @param position 图例位置
     * @param layout   手动布局
     * @return 图例XNode，如果position为null则返回null
     */
    public XNode buildLegendWithLayout(ChartLegendPosition position, ChartManualLayoutModel layout) {
        if (position == null) {
            return null;
        }

        // 创建图例模型
        ChartLegendModel legend = new ChartLegendModel();
        legend.setPosition(position);
        legend.setManualLayout(layout);
        legend.setVisible(true);

        return buildLegend(legend);
    }
}