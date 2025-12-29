/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartDataLabelPosition;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartDataLabelsBuilder - 数据标签构建器
 * 负责生成OOXML图表中的数据标签配置
 * 将内部的ChartDataLabelsModel转换为OOXML的c:dLbls元素
 */
public class ChartDataLabelsBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataLabelsBuilder.class);

    public static final ChartDataLabelsBuilder INSTANCE = new ChartDataLabelsBuilder();

    /**
     * 构建数据标签配置
     *
     * @param dataLabels 数据标签模型对象
     * @return 数据标签XNode，如果dataLabels为null则返回null
     */
    public XNode buildDataLabels(ChartDataLabelsModel dataLabels) {
        if (dataLabels == null) {
            return null;
        }


        XNode dLblsNode = XNode.make("c:dLbls");

        // 构建手动布局
        buildManualLayout(dLblsNode, dataLabels);

        // 构建样式
        buildStyles(dLblsNode, dataLabels);

        // 构建可见性
        buildVisibility(dLblsNode, dataLabels);

        // 构建位置
        buildPosition(dLblsNode, dataLabels);

        // 构建显示选项
        buildDisplayOptions(dLblsNode, dataLabels);

        // 构建数字格式
        buildNumberFormat(dLblsNode, dataLabels);


        return dLblsNode;

    }

    /**
     * 构建可见性
     */
    private void buildVisibility(XNode dLblsNode, ChartDataLabelsModel dataLabels) {

        // 如果数据标签不可见，添加delete元素
//            if (!dataLabels.isVisible()) {
//                XNode deleteNode = dLblsNode.addChild("c:delete");
//                deleteNode.setAttr("val", "1");
//            }

    }

    /**
     * 构建位置
     */
    private void buildPosition(XNode dLblsNode, ChartDataLabelsModel dataLabels) {

        if (dataLabels.getPosition() != null) {
            XNode dLblPosNode = dLblsNode.addChild("c:dLblPos");
            dLblPosNode.setAttr("val", mapPositionToOoxml(dataLabels.getPosition()));
        }


    }

    /**
     * 映射位置到OOXML值
     */
    private String mapPositionToOoxml(ChartDataLabelPosition position) {
        if (position == null) {
            return "bestFit";
        }

        switch (position) {
            case CENTER:
                return "ctr";
            case INSIDE_END:
                return "inEnd";
            case INSIDE_BASE:
                return "inBase";
            case OUTSIDE_END:
                return "outEnd";
            case LEFT:
                return "l";
            case RIGHT:
                return "r";
            case TOP:
                return "t";
            case BOTTOM:
                return "b";
            case BEST_FIT:
                return "bestFit";
            default:
                LOG.warn("Unknown label position: {}, using default bestFit", position);
                return "bestFit";
        }
    }

    /**
     * 构建显示选项
     */
    private void buildDisplayOptions(XNode dLblsNode, ChartDataLabelsModel dataLabels) {

        if (dataLabels.getShowLegendKey() != null) {
            XNode showNode = dLblsNode.addChild("c:showLegendKey");
            showNode.setAttr("val", dataLabels.getShowLegendKey() ? "1" : "0");
        }

        // 显示值
        if (dataLabels.getShowVal() != null) {
            XNode showValNode = dLblsNode.addChild("c:showVal");
            showValNode.setAttr("val", dataLabels.getShowVal() ? "1" : "0");
        }

        // 显示类别名称
        if (dataLabels.getShowCatName() != null) {
            XNode showCatNameNode = dLblsNode.addChild("c:showCatName");
            showCatNameNode.setAttr("val", dataLabels.getShowCatName() ? "1" : "0");
        }

        // 显示系列名称
        if (dataLabels.getShowSerName() != null) {
            XNode showSerNameNode = dLblsNode.addChild("c:showSerName");
            showSerNameNode.setAttr("val", dataLabels.getShowSerName() ? "1" : "0");
        }

        // 显示百分比
        if (dataLabels.getShowPercent() != null) {
            XNode showPercentNode = dLblsNode.addChild("c:showPercent");
            showPercentNode.setAttr("val", dataLabels.getShowPercent() ? "1" : "0");
        }

        // 显示气泡大小
        if (dataLabels.getShowBubbleSize() != null) {
            XNode showBubbleSizeNode = dLblsNode.addChild("c:showBubbleSize");
            showBubbleSizeNode.setAttr("val", dataLabels.getShowBubbleSize() ? "1" : "0");
        }

        // 显示引导线
        if (dataLabels.getShowLeaderLines() != null) {
            XNode showLeaderLinesNode = dLblsNode.addChild("c:showLeaderLines");
            showLeaderLinesNode.setAttr("val", dataLabels.getShowLeaderLines() ? "1" : "0");
        }

    }

    /**
     * 构建数字格式
     */
    private void buildNumberFormat(XNode dLblsNode, ChartDataLabelsModel dataLabels) {

        if (dataLabels.getNumberFormat() != null) {
            XNode numFmtNode = dLblsNode.addChild("c:numFmt");
            numFmtNode.setAttr("formatCode", dataLabels.getNumberFormat());
            numFmtNode.setAttr("sourceLinked", "0");
        }

    }

    /**
     * 构建手动布局
     */
    private void buildManualLayout(XNode dLblsNode, ChartDataLabelsModel dataLabels) {

        ChartManualLayoutModel manualLayout = null; //dataLabels.getManualLayout();
        if (manualLayout != null) {
            XNode layoutNode = ChartManualLayoutBuilder.INSTANCE.buildManualLayout(manualLayout);
            if (layoutNode != null) {
                dLblsNode.appendChild(layoutNode);
            }
        }


    }

    /**
     * 构建样式
     */
    private void buildStyles(XNode dLblsNode, ChartDataLabelsModel dataLabels) {

        // 构建形状样式
        ChartShapeStyleModel shapeStyle = dataLabels.getShapeStyle();
        if (shapeStyle != null) {
            XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
            if (spPrNode != null) {
                dLblsNode.appendChild(spPrNode.withTagName("c:spPr"));
            }
        }

        // 构建文本样式
        ChartTextStyleModel textStyle = dataLabels.getTextStyle();
        if (textStyle != null) {
            XNode txPrNode = ChartTextStyleBuilder.INSTANCE.buildTextStyle(textStyle);
            if (txPrNode != null) {
                dLblsNode.appendChild(txPrNode.withTagName("c:txPr"));
            }
        }

    }

    /**
     * 构建简单的数据标签（仅包含位置）
     * 这是一个便利方法，用于快速创建基本的数据标签配置
     *
     * @param position 标签位置
     * @return 数据标签XNode，如果position为null则返回null
     */
    public XNode buildSimpleDataLabels(ChartDataLabelPosition position) {
        if (position == null) {
            return null;
        }

        // 创建数据标签模型
        ChartDataLabelsModel dataLabels = new ChartDataLabelsModel();
        dataLabels.setPosition(position);
        //dataLabels.setVisible(true);
        dataLabels.setShowVal(true);

        return buildDataLabels(dataLabels);
    }

    /**
     * 构建带有显示选项的数据标签
     * 这是一个便利方法，用于快速创建带有显示选项的数据标签配置
     *
     * @param position         标签位置
     * @param showValue        显示值
     * @param showCategoryName 显示类别名称
     * @param showPercent      显示百分比
     * @return 数据标签XNode，如果position为null则返回null
     */
    public XNode buildDataLabelsWithOptions(ChartDataLabelPosition position,
                                            Boolean showValue,
                                            Boolean showCategoryName,
                                            Boolean showPercent) {
        if (position == null) {
            return null;
        }

        // 创建数据标签模型
        ChartDataLabelsModel dataLabels = new ChartDataLabelsModel();
        dataLabels.setPosition(position);
        // dataLabels.setVisible(true);
        dataLabels.setShowVal(showValue);
        dataLabels.setShowCatName(showCategoryName);
        dataLabels.setShowPercent(showPercent);

        return buildDataLabels(dataLabels);
    }

    /**
     * 构建带有完整显示选项的数据标签（包括气泡大小）
     * 这是一个便利方法，用于快速创建带有完整显示选项的数据标签配置，特别适用于气泡图
     *
     * @param position         标签位置
     * @param showValue        显示值
     * @param showCategoryName 显示类别名称
     * @param showPercent      显示百分比
     * @param showBubbleSize   显示气泡大小
     * @return 数据标签XNode，如果position为null则返回null
     */
    public XNode buildDataLabelsWithFullOptions(ChartDataLabelPosition position,
                                                Boolean showValue,
                                                Boolean showCategoryName,
                                                Boolean showPercent,
                                                Boolean showBubbleSize) {
        if (position == null) {
            return null;
        }

        // 创建数据标签模型
        ChartDataLabelsModel dataLabels = new ChartDataLabelsModel();
        dataLabels.setPosition(position);
        // dataLabels.setVisible(true);
        dataLabels.setShowVal(showValue);
        dataLabels.setShowCatName(showCategoryName);
        dataLabels.setShowPercent(showPercent);
        dataLabels.setShowBubbleSize(showBubbleSize);

        return buildDataLabels(dataLabels);
    }
}