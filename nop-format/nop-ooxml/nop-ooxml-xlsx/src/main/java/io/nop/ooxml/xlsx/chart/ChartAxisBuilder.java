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
import io.nop.excel.chart.constants.ChartAxisPosition;
import io.nop.excel.chart.constants.ChartAxisTickLabelPosition;
import io.nop.excel.chart.constants.ChartAxisType;
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

        // 构建轴标题
        buildAxisTitle(axisNode, axis);

        // 构建其他缺失的属性
        buildAdditionalProperties(axisNode, axis);

        return axisNode;
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
    }

    /**
     * 构建位置和交叉设置
     */
    private void buildPositionAndCrossing(XNode axisNode, ChartAxisModel axis) {
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

        // 构建crosses属性
        if (axis.getCrosses() != null) {
            XNode crossesNode = axisNode.addChild("c:crosses");
            crossesNode.setAttr("val", axis.getCrosses().value());
        }

        // 构建crossBetween属性
        if (axis.getCrossBetween() != null) {
            XNode crossBetweenNode = axisNode.addChild("c:crossBetween");
            crossBetweenNode.setAttr("val", axis.getCrossBetween().value());
        }
    }

    /**
     * 构建数字格式
     */
    private void buildNumberFormat(XNode axisNode, ChartAxisModel axis) {
        // 从刻度配置中获取数字格式
        ChartTicksModel ticks = axis.getTicks();
        if (ticks != null && !StringHelper.isEmpty(ticks.getLabelNumFmt())) {
            XNode numFmtNode = axisNode.addChild("c:numFmt");
            numFmtNode.setAttr("formatCode", ticks.getLabelNumFmt());
            numFmtNode.setAttr("sourceLinked", "1");
        }
    }

    /**
     * 构建刻度配置
     */
    private void buildTicksConfig(XNode axisNode, ChartAxisModel axis) {
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

        // 构建标签旋转角度（通过文本属性）
        if (ticks.getLabelRotation() != null) {
            //buildTickLabelRotation(axisNode, ticks.getLabelRotation());
        }
    }

    /**
     * 映射刻度标签位置到OOXML值
     */
    private String mapTickLabelPositionToOoxml(ChartAxisTickLabelPosition position) {
        if (position == null) {
            return "nextTo";
        }
        return position.value();
    }

    /**
     * 构建刻度标签旋转角度
     */
    private void buildTickLabelRotation(XNode axisNode, Double labelRotation) {
        // 检查是否已经有c:txPr节点
        XNode txPrNode = axisNode.childByTag("c:txPr");
        if (txPrNode == null) {
            // 创建基本的文本属性节点
            txPrNode = axisNode.addChild("c:txPr");
            txPrNode.addChild("a:lstStyle");

            XNode pNode = txPrNode.addChild("a:p");
            XNode pPrNode = pNode.addChild("a:pPr");
            XNode defRPrNode = pPrNode.addChild("a:defRPr");
            defRPrNode.setAttr("sz", "900");  // 默认字体大小
            pNode.addChild("a:endParaRPr").setAttr("lang", "en-US");
        }

        // 获取或创建a:bodyPr节点
        XNode bodyPrNode = txPrNode.childByTag("a:bodyPr");
        if (bodyPrNode == null) {
            bodyPrNode = txPrNode.addChild("a:bodyPr");
            // 设置基本属性
            bodyPrNode.setAttr("vert", "horz");
            bodyPrNode.setAttr("wrap", "square");
            bodyPrNode.setAttr("anchor", "ctr");
            bodyPrNode.setAttr("anchorCtr", "1");
        }

        // 设置旋转角度
        String rotationStr = ChartPropertyHelper.degreesToOoxmlAngleString(labelRotation);
        bodyPrNode.setAttr("rot", rotationStr);

        LOG.debug("Set tick label rotation to {}° (OOXML: {})", labelRotation, rotationStr);
    }

    /**
     * 构建线条样式
     */
    private void buildLineStyle(XNode axisNode, ChartAxisModel axis) {
        ChartShapeStyleModel shapeStyle = axis.getShapeStyle();
        if (shapeStyle != null) {
            XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
            if (spPrNode != null) {
                axisNode.appendChild(spPrNode.withTagName("c:spPr"));
            }
        }
    }

    /**
     * 构建文本样式
     */
    private void buildTextStyle(XNode axisNode, ChartAxisModel axis) {
        ChartTextStyleModel textStyle = axis.getTextStyle();
        if (textStyle != null) {
            XNode txPrNode = ChartTextStyleBuilder.INSTANCE.buildTextStyle(textStyle);
            if (txPrNode != null) {
                axisNode.appendChild(txPrNode.withTagName("c:txPr"));
            }
        }
    }

    /**
     * 构建网格线
     */
    private void buildGridLines(XNode axisNode, ChartAxisModel axis) {
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
    }

    /**
     * 构建比例尺
     */
    private void buildScale(XNode axisNode, ChartAxisModel axis) {
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
    }

    /**
     * 构建简单的坐标轴（仅包含基本属性）
     * 这是一个便利方法，用于快速创建基本的坐标轴配置
     *
     * @param axisType    坐标轴类型
     * @param axisId      坐标轴ID
     * @param position    坐标轴位置
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

    /**
     * 构建轴标题
     */
    private void buildAxisTitle(XNode axisNode, ChartAxisModel axis) {
        io.nop.excel.chart.model.ChartAxisTitleModel title = axis.getTitle();
        if (title == null) {
            return;
        }

        XNode titleNode = axisNode.addChild("c:title");

        // 构建标题文本
        if (!StringHelper.isEmpty(title.getText()) || !StringHelper.isEmpty(title.getTextCellRef())) {
            XNode txNode = titleNode.addChild("c:tx");

            XNode textNode = ChartTextBuilder.INSTANCE.buildText(title.getText(),title.getTextCellRef(), title.getTitleFont());
            txNode.appendChild(textNode);
        }

        //ChartPropertyHelper.setChildBoolVal(titleNode, "c:overlay", title.getOverlay());

        // 构建标题样式
        if (title.getShapeStyle() != null) {
            XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(title.getShapeStyle());
            if (spPrNode != null) {
                titleNode.appendChild(spPrNode.withTagName("c:spPr"));
            }
        }

        // 构建标题文本样式
        if (title.getTextStyle() != null) {
            XNode txPrNode = ChartTextStyleBuilder.INSTANCE.buildTextStyle(title.getTextStyle());
            if (txPrNode != null) {
                titleNode.appendChild(txPrNode.withTagName("c:txPr"));
            }
        }
    }

    /**
     * 构建其他缺失的属性
     */
    private void buildAdditionalProperties(XNode axisNode, ChartAxisModel axis) {
        // 构建multiLevel属性 (对于分类轴)
        if (axis.getType() == ChartAxisType.CATEGORY && axis.getMultiLevel() != null && axis.getMultiLevel()) {
            XNode multiLevelNode = axisNode.addChild("c:multiLvlLbl");
            multiLevelNode.setAttr("val", "1");
        }

        // 构建dataCellRef属性
        if (!StringHelper.isEmpty(axis.getDataCellRef()) && !CellPosition.NONE_STRING.equals(axis.getDataCellRef())) {
            XNode catNode = axisNode.addChild("c:cat");
            XNode strRefNode = catNode.addChild("c:strRef");
            XNode fNode = strRefNode.addChild("c:f");
            fNode.content(axis.getDataCellRef());
        }

        // 构建labelAlign属性
        if (axis.getLabelAlign() != null) {
            XNode labelAlignNode = axisNode.addChild("c:lblAlgn");
            labelAlignNode.setAttr("val", axis.getLabelAlign().value());
        }
    }
}