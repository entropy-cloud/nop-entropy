package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartAxisPosition;
import io.nop.excel.chart.constants.ChartAxisTickLabelPosition;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.constants.ChartLabelAlignment;
import io.nop.excel.chart.constants.ChartTickMark;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartGridModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.chart.model.ChartTicksModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartAxisParser - 坐标轴解析器
 * 负责解析Excel图表中的坐标轴配置
 */
public class ChartAxisParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartAxisParser.class);

    public static final ChartAxisParser INSTANCE = new ChartAxisParser();

    /**
     * 解析坐标轴配置
     *
     * @param axisNode      坐标轴节点
     * @param styleProvider 样式提供者
     * @return 坐标轴模型对象
     */
    public ChartAxisModel parseAxis(XNode axisNode, IChartStyleProvider styleProvider) {
        if (axisNode == null) return null;

        ChartAxisModel axis = new ChartAxisModel();

        // 解析坐标轴ID和类型
        parseBasicProperties(axis, axisNode);

        // 解析位置和交叉设置
        parsePositionAndCrossing(axis, axisNode);

        // 解析数字格式
        parseNumberFormat(axis, axisNode);

        // 解析刻度配置（合并的刻度标记和标签）
        parseTicksConfig(axis, axisNode, styleProvider);

        // 解析线条样式
        parseLineStyle(axis, axisNode, styleProvider);

        // 解析文本样式
        parseTextStyle(axis, axisNode, styleProvider);

        // 解析网格线
        parseGridLines(axis, axisNode, styleProvider);

        // 解析比例尺
        parseScale(axis, axisNode);

        return axis;
    }

    /**
     * 解析基本属性
     */
    private void parseBasicProperties(ChartAxisModel axis, XNode axisNode) {
        // 解析坐标轴ID
        String axisId = ChartPropertyHelper.getChildVal(axisNode, "c:axId");
        if (!StringHelper.isEmpty(axisId)) {
            axis.setId(axisId);
        }

        // 解析坐标轴类型 - 根据标签名确定
        String tagName = axisNode.getTagName();
        ChartAxisType axisType = mapAxisType(tagName);
        if (axisType != null) {
            axis.setType(axisType);
        }

        // 解析删除标记
        Boolean deleteValue = ChartPropertyHelper.getChildBoolVal(axisNode, "c:delete");
        if (deleteValue != null && deleteValue) {
            // 坐标轴被删除，设置为不可见
            axis.setVisible(false);
        } else {
            axis.setVisible(true);
        }
    }

    /**
     * 映射坐标轴类型
     */
    private ChartAxisType mapAxisType(String tagName) {
        if (StringHelper.isEmpty(tagName)) return null;

        switch (tagName) {
            case "c:catAx":
                return ChartAxisType.CATEGORY;
            case "c:valAx":
                return ChartAxisType.VALUE;
            case "c:dateAx":
                return ChartAxisType.DATE;
            case "c:serAx":
                return ChartAxisType.SERIES;
            default:
                LOG.warn("Unknown axis type: {}, using default VALUE", tagName);
                return ChartAxisType.VALUE;
        }
    }

    /**
     * 解析位置和交叉设置
     */
    private void parsePositionAndCrossing(ChartAxisModel axis, XNode axisNode) {
        // 解析坐标轴位置
        String position = ChartPropertyHelper.getChildVal(axisNode, "c:axPos");
        if (position != null) {
            ChartAxisPosition axisPos = mapAxisPosition(position);
            if (axisPos != null) {
                axis.setPosition(axisPos);
            }
        }

        // 解析交叉轴ID
        String crossAxisId = ChartPropertyHelper.getChildVal(axisNode, "c:crossAx");
        if (crossAxisId != null) {
            axis.setCrossAxisId(crossAxisId);
        }

        // 解析交叉点
        Double crossAtValue = ChartPropertyHelper.getChildDoubleVal(axisNode, "c:crossesAt");
        if (crossAtValue != null) {
            axis.setCrossAt(crossAtValue);
        }
    }

    /**
     * 映射坐标轴位置
     */
    private ChartAxisPosition mapAxisPosition(String position) {
        if (StringHelper.isEmpty(position)) return null;

        switch (position.toLowerCase()) {
            case "b":
                return ChartAxisPosition.BOTTOM;
            case "l":
                return ChartAxisPosition.LEFT;
            case "r":
                return ChartAxisPosition.RIGHT;
            case "t":
                return ChartAxisPosition.TOP;
            default:
                LOG.warn("Unknown axis position: {}, using default BOTTOM", position);
                return ChartAxisPosition.BOTTOM;
        }
    }

    /**
     * 解析数字格式
     */
    private void parseNumberFormat(ChartAxisModel axis, XNode axisNode) {
        XNode numFmtNode = axisNode.childByTag("c:numFmt");
        if (numFmtNode != null) {
            String formatCode = numFmtNode.attrText("formatCode");
            if (!StringHelper.isEmpty(formatCode)) {
                // axis.setNumberFormat(formatCode);
            }

            Boolean sourceLinkedValue = ChartPropertyHelper.convertToBoolean(numFmtNode.attrText("sourceLinked"));
            if (sourceLinkedValue != null) {
                // axis.setSourceLinked(sourceLinkedValue);
            }
        }
    }

    /**
     * 解析刻度配置（合并的刻度标记和标签）
     */
    private void parseTicksConfig(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        ChartTicksModel ticks = new ChartTicksModel();

        // 解析主要刻度标记
        String majorTickMark = ChartPropertyHelper.getChildVal(axisNode, "c:majorTickMark");
        if (!StringHelper.isEmpty(majorTickMark)) {
            ticks.setMajorTickMark(ChartTickMark.fromValue(majorTickMark));
            LOG.debug("Major tick mark: {}", majorTickMark);
        }

        // 解析次要刻度标记
        String minorTickMark = ChartPropertyHelper.getChildVal(axisNode, "c:minorTickMark");
        if (!StringHelper.isEmpty(minorTickMark)) {
            ticks.setMinorTickMark(ChartTickMark.fromValue(minorTickMark));
            LOG.debug("Minor tick mark: {}", minorTickMark);
        }

        // 解析刻度标签位置
        String position = ChartPropertyHelper.getChildVal(axisNode, "c:tickLblPos");
        if (!StringHelper.isEmpty(position)) {
            ChartAxisTickLabelPosition labelPos = mapTickLabelPosition(position);
            if (labelPos != null) {
                ticks.setLabelPosition(labelPos);
                LOG.debug("Tick label position: {}", position);
            }
        }

        // 解析刻度标签旋转角度
        parseTickLabelRotation(ticks, axisNode);

        // 解析数字格式（从轴级别的 numFmt 获取）
        XNode numFmtNode = axisNode.childByTag("c:numFmt");
        if (numFmtNode != null) {
            String formatCode = numFmtNode.attrText("formatCode");
            if (!StringHelper.isEmpty(formatCode)) {
                ticks.setLabelNumFmt(formatCode);
                LOG.debug("Tick label number format: {}", formatCode);
            }
        }

        // 解析标签对齐和偏移
        String alignment = ChartPropertyHelper.getChildVal(axisNode, "c:lblAlgn");
        if (!StringHelper.isEmpty(alignment)) {
            ChartLabelAlignment labelAlignment = ChartLabelAlignment.fromValue(alignment);
            if (labelAlignment != null) {
                ticks.setLabelAlignment(labelAlignment);
                LOG.debug("Label alignment: {}", alignment);
            }
        }

        Double offset = ChartPropertyHelper.getChildDoubleVal(axisNode, "c:lblOffset");
        if (offset != null) {
            ticks.setLabelOffset(offset);
            LOG.debug("Label offset: {}", offset);
        }

        // 解析文本属性（字体等）
        XNode txPrNode = axisNode.childByTag("c:txPr");
        if (txPrNode != null) {
            // 这里可以解析字体信息，但需要适配到 labelFont
            LOG.debug("Found text properties for tick labels");
        }

        // 设置默认值
        if (ticks.getLabelVisible() == null) {
            ticks.setLabelVisible(true);
        }
        if (ticks.isVisible()) {
            ticks.setVisible(true);
        }

        // 设置到轴模型
        axis.setTicks(ticks);
    }

    /**
     * 映射刻度标签位置
     */
    private ChartAxisTickLabelPosition mapTickLabelPosition(String position) {
        if (StringHelper.isEmpty(position)) return null;

        switch (position.toLowerCase()) {
            case "none":
                return ChartAxisTickLabelPosition.NONE;
            case "low":
                return ChartAxisTickLabelPosition.LOW;
            case "high":
                return ChartAxisTickLabelPosition.HIGH;
            case "nextto":
                return ChartAxisTickLabelPosition.NEXT_TO;
            default:
                LOG.warn("Unknown tick label position: {}, using default NEXT_TO", position);
                return ChartAxisTickLabelPosition.NEXT_TO;
        }
    }

    /**
     * 解析刻度标签旋转角度
     */
    private void parseTickLabelRotation(ChartTicksModel ticks, XNode axisNode) {
        XNode txPrNode = axisNode.childByTag("c:txPr");
        if (txPrNode != null) {
            XNode bodyPrNode = txPrNode.childByTag("a:bodyPr");
            if (bodyPrNode != null) {
                String rotStr = bodyPrNode.attrText("rot");
                if (!StringHelper.isEmpty(rotStr)) {
                    Double degrees = ChartPropertyHelper.ooxmlAngleStringToDegrees(rotStr);
                    if (degrees != null) {
                        ticks.setLabelRotation(degrees);
                        LOG.debug("Parsed tick label rotation: {}° (OOXML: {})", degrees, rotStr);
                    }
                }
            }
        }
    }

    /**
     * 解析线条样式
     */
    private void parseLineStyle(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        XNode spPrNode = axisNode.childByTag("c:spPr");
        if (spPrNode != null) {
            // 使用ChartShapeStyleParser解析线条样式
            ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
            if (shapeStyle != null) {
                axis.setShapeStyle(shapeStyle);
            }
        }
    }

    /**
     * 解析网格线
     */
    private void parseGridLines(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        // 解析主要网格线
        ChartGridModel majorGridLines = ChartGridParser.INSTANCE.parseMajorGridLines(axisNode, styleProvider);
        if (majorGridLines != null) {
            axis.setMajorGrid(majorGridLines);
        }

        // 解析次要网格线
        ChartGridModel minorGridLines = ChartGridParser.INSTANCE.parseMinorGridLines(axisNode, styleProvider);
        if (minorGridLines != null) {
            axis.setMinorGrid(minorGridLines);
        }
    }

    /**
     * 解析文本样式
     */
    private void parseTextStyle(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        XNode txPrNode = axisNode.childByTag("c:txPr");
        if (txPrNode != null) {
            ChartTextStyleModel textStyle = ChartTextStyleParser.INSTANCE.parseTextStyle(txPrNode, styleProvider);
            if (textStyle != null) {
                axis.setTextStyle(textStyle);
            }
        }
    }

    /**
     * 解析比例尺
     */
    private void parseScale(ChartAxisModel axis, XNode axisNode) {
        XNode scalingNode = axisNode.childByTag("c:scaling");
        if (scalingNode != null) {
            io.nop.excel.chart.model.ChartAxisScaleModel scale = new io.nop.excel.chart.model.ChartAxisScaleModel();

            // 解析对数刻度
            Double logBase = scalingNode.attrDouble("logBase");
            if (logBase != null) {
                scale.setLogBase(logBase);
            }

            // 解析最小值
            Double minValue = ChartPropertyHelper.getChildDoubleVal(scalingNode, "c:min");
            if (minValue != null) {
                scale.setMin(minValue);
            }

            // 解析最大值
            Double maxValue = ChartPropertyHelper.getChildDoubleVal(scalingNode, "c:max");
            if (maxValue != null) {
                scale.setMax(maxValue);
            }

            // 解析方向
            String orientation = ChartPropertyHelper.getChildVal(scalingNode, "c:orientation");
            if ("maxMin".equals(orientation)) {
                scale.setReverse(true);
            }

            axis.setScale(scale);
        }
    }
}