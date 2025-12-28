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
import io.nop.excel.chart.constants.ChartAxisPosition;
import io.nop.excel.chart.constants.ChartAxisTickLabelPosition;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.constants.ChartLabelAlignment;
import io.nop.excel.chart.constants.ChartTickMark;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartAxisScaleModel;
import io.nop.excel.chart.model.ChartGridModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.chart.model.ChartTicksModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartAxisBuilder - 坐标轴构建器
 * 负责生成OOXML图表中的坐标轴配置
 * 将内部的ChartAxisModel转换为OOXML的坐标轴元素
 */
public class ChartAxisBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartAxisBuilder.class);

    public static final ChartAxisBuilder INSTANCE = new ChartAxisBuilder();

    /**
     * 构建坐标轴元素
     * 
     * @param axis 坐标轴模型对象
     * @return 坐标轴XNode，如果axis为null则返回null
     */
    public XNode buildAxis(ChartAxisModel axis) {
        if (axis == null) {
            return null;
        }

        try {
            // 根据轴类型创建对应的元素
            String axisTagName = getAxisTagName(axis.getType());
            if (axisTagName == null) {
                LOG.warn("Unknown axis type: {}, using default c:valAx", axis.getType());
                axisTagName = "c:valAx";
            }

            XNode axisNode = XNode.make(axisTagName);

            // 构建基本属性
            buildBasicProperties(axisNode, axis);

            // 构建位置和交叉设置
            buildPositionAndCrossing(axisNode, axis);

            // 构建数字格式
            buildNumberFormat(axisNode, axis);

            // 构建刻度配置
            buildTicksConfig(axisNode, axis);

            // 构建线条样式
            buildLineStyle(axisNode, axis);

            // 构建文本样式
            buildTextStyle(axisNode, axis);

            // 构建网格线
            buildGridLines(axisNode, axis);

            // 构建比例尺
            buildScale(axisNode, axis);

            return axisNode;

        } catch (Exception e) {
            LOG.warn("Failed to build axis configuration", e);
            return null;
        }
    }

    /**
     * 获取坐标轴标签名
     */
    private String getAxisTagName(ChartAxisType axisType) {
        if (axisType == null) {
            return null;
        }

        switch (axisType) {
            case CATEGORY:
                return "c:catAx";
            case VALUE:
                return "c:valAx";
            case DATE:
                return "c:dateAx";
            case SERIES:
                return "c:serAx";
            default:
                LOG.warn("Unsupported axis type: {}", axisType);
                return "c:valAx";
        }
    }

    /**
     * 构建基本属性
     */
    private void buildBasicProperties(XNode axisNode, ChartAxisModel axis) {
        try {
            // 构建坐标轴ID
            if (!StringHelper.isEmpty(axis.getId())) {
                XNode axisIdNode = axisNode.addChild("c:axId");
                axisIdNode.setAttr("val", axis.getId());
            }

            // 构建删除标记（如果轴不可见）
            if (!axis.isVisible()) {
                XNode deleteNode = axisNode.addChild("c:delete");
                deleteNode.setAttr("val", "1");
            }

        } catch (Exception e) {
            LOG.warn("Failed to build axis basic properties", e);
        }
    }

    /**
     * 构建位置和交叉设置
     */
    private void buildPositionAndCrossing(XNode axisNode, ChartAxisModel axis) {
        try {
            // 构建坐标轴位置
            if (axis.getPosition() != null) {
                XNode positionNode = axisNode.addChild("c:axPos");
                positionNode.setAttr("val", axis.getPosition().value());
            }

            // 构建交叉轴ID
            if (!StringHelper.isEmpty(axis.getCrossAxisId())) {
                XNode crossAxisNode = axisNode.addChild("c:crossAx");
                crossAxisNode.setAttr("val", axis.getCrossAxisId());
            }

            // 构建交叉点
            if (axis.getCrossAt() != null) {
                XNode crossAtNode = axisNode.addChild("c:crossesAt");
                crossAtNode.setAttr("val", axis.getCrossAt().toString());
            }

        } catch (Exception e) {
            LOG.warn("Failed to build axis position and crossing", e);
        }
    }

    /**
     * 构建数字格式
     */
    private void buildNumberFormat(XNode axisNode, ChartAxisModel axis) {
        try {
            // 从刻度配置中获取数字格式
            ChartTicksModel ticks = axis.getTicks();
            if (ticks != null && !StringHelper.isEmpty(ticks.getLabelNumFmt())) {
                XNode numFmtNode = axisNode.addChild("c:numFmt");
                numFmtNode.setAttr("formatCode", ticks.getLabelNumFmt());
                numFmtNode.setAttr("sourceLinked", "0");
            }

        } catch (Exception e) {
            LOG.warn("Failed to build axis number format", e);
        }
    }

    /**
     * 构建刻度配置
     */
    private void buildTicksConfig(XNode axisNode, ChartAxisModel axis) {
        try {
            ChartTicksModel ticks = axis.getTicks();
            if (ticks == null) {
                return;
            }

            // 构建主要刻度标记
            if (ticks.getMajorTickMark() != null) {
                XNode majorTickNode = axisNode.addChild("c:majorTickMark");
                majorTickNode.setAttr("val", ticks.getMajorTickMark().value());
            }

            // 构建次要刻度标记
            if (ticks.getMinorTickMark() != null) {
                XNode minorTickNode = axisNode.addChild("c:minorTickMark");
                minorTickNode.setAttr("val", ticks.getMinorTickMark().value());
            }

            // 构建刻度标签位置
            if (ticks.getLabelPosition() != null) {
                XNode labelPosNode = axisNode.addChild("c:tickLblPos");
                labelPosNode.setAttr("val", mapTickLabelPositionToOoxml(ticks.getLabelPosition()));
            }

            // 构建标签对齐。
            // @TODO 这里生成的节点不正确，如果有这些节点，则无法正常打开
            if (ticks.getLabelAlignment() != null) {
                //XNode alignmentNode = axisNode.addChild("c:lblAlgn");
                //alignmentNode.setAttr("val", ticks.getLabelAlignment().value());
            }

            // 构建标签偏移
            if (ticks.getLabelOffset() != null) {
                //XNode offsetNode = axisNode.addChild("c:lblOffset");
                //offsetNode.setAttr("val", ticks.getLabelOffset().toString());
            }

        } catch (Exception e) {
            LOG.warn("Failed to build ticks configuration", e);
        }
    }

    /**
     * 映射刻度标签位置到OOXML值
     */
    private String mapTickLabelPositionToOoxml(ChartAxisTickLabelPosition position) {
        if (position == null) {
            return "nextTo";
        }

        switch (position) {
            case NONE:
                return "none";
            case LOW:
                return "low";
            case HIGH:
                return "high";
            case NEXT_TO:
                return "nextTo";
            default:
                LOG.warn("Unknown tick label position: {}, using default nextTo", position);
                return "nextTo";
        }
    }

    /**
     * 构建线条样式
     */
    private void buildLineStyle(XNode axisNode, ChartAxisModel axis) {
        try {
            ChartShapeStyleModel shapeStyle = axis.getShapeStyle();
            if (shapeStyle != null) {
                XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
                if (spPrNode != null) {
                    axisNode.appendChild(spPrNode.withTagName("c:spPr"));
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build axis line style", e);
        }
    }

    /**
     * 构建文本样式
     */
    private void buildTextStyle(XNode axisNode, ChartAxisModel axis) {
        try {
            ChartTextStyleModel textStyle = axis.getTextStyle();
            if (textStyle != null) {
                XNode txPrNode = ChartTextStyleBuilder.INSTANCE.buildTextStyle(textStyle);
                if (txPrNode != null) {
                    axisNode.appendChild(txPrNode.withTagName("c:txPr"));
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build axis text style", e);
        }
    }

    /**
     * 构建网格线
     */
    private void buildGridLines(XNode axisNode, ChartAxisModel axis) {
        try {
            // 构建主要网格线
            ChartGridModel majorGrid = axis.getMajorGrid();
            if (majorGrid != null) {
                XNode majorGridNode = ChartGridBuilder.INSTANCE.buildMajorGridLines(majorGrid);
                if (majorGridNode != null) {
                    axisNode.appendChild(majorGridNode);
                }
            }

            // 构建次要网格线
            ChartGridModel minorGrid = axis.getMinorGrid();
            if (minorGrid != null) {
                XNode minorGridNode = ChartGridBuilder.INSTANCE.buildMinorGridLines(minorGrid);
                if (minorGridNode != null) {
                    axisNode.appendChild(minorGridNode);
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to build axis grid lines", e);
        }
    }

    /**
     * 构建比例尺
     */
    private void buildScale(XNode axisNode, ChartAxisModel axis) {
        try {

            // 必须要有scaling节点，否则excel无法正常打开
            XNode scalingNode = axisNode.addChild("c:scaling");

            ChartAxisScaleModel scale = axis.getScale();
            if (scale == null) {
                return;
            }

            // 构建对数刻度
            if (scale.getLogBase() != null) {
                scalingNode.setAttr("logBase", scale.getLogBase().toString());
            }

            // 构建最小值
            if (scale.getMin() != null) {
                XNode minNode = scalingNode.addChild("c:min");
                minNode.setAttr("val", scale.getMin().toString());
            }

            // 构建最大值
            if (scale.getMax() != null) {
                XNode maxNode = scalingNode.addChild("c:max");
                maxNode.setAttr("val", scale.getMax().toString());
            }

            // 构建方向
            if (scale.getReverse() != null && scale.getReverse()) {
                XNode orientationNode = scalingNode.addChild("c:orientation");
                orientationNode.setAttr("val", "maxMin");
            }

        } catch (Exception e) {
            LOG.warn("Failed to build axis scale", e);
        }
    }

    /**
     * 构建简单的坐标轴（仅包含基本属性）
     * 这是一个便利方法，用于快速创建基本的坐标轴配置
     * 
     * @param axisType 坐标轴类型
     * @param axisId 坐标轴ID
     * @param position 坐标轴位置
     * @param crossAxisId 交叉轴ID
     * @return 坐标轴XNode，如果axisType为null则返回null
     */
    public XNode buildSimpleAxis(ChartAxisType axisType, String axisId, 
                                ChartAxisPosition position, String crossAxisId) {
        if (axisType == null) {
            return null;
        }

        // 创建坐标轴模型
        ChartAxisModel axis = new ChartAxisModel();
        axis.setType(axisType);
        axis.setId(axisId);
        axis.setPosition(position);
        axis.setCrossAxisId(crossAxisId);

        return buildAxis(axis);
    }
}