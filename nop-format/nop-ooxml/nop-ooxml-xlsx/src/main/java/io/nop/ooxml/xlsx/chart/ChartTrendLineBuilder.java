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
import io.nop.excel.chart.constants.ChartTrendLineType;
import io.nop.excel.chart.model.ChartTrendLineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartTrendLineBuilder - 趋势线构建器
 * 负责生成OOXML图表中的趋势线配置
 * 将内部的ChartTrendLineModel转换为OOXML的c:trendline元素
 */
public class ChartTrendLineBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTrendLineBuilder.class);

    public static final ChartTrendLineBuilder INSTANCE = new ChartTrendLineBuilder();

    /**
     * 构建趋势线配置
     *
     * @param trendLine 趋势线模型对象
     * @return 趋势线XNode，如果trendLine为null则返回null
     */
    public XNode buildTrendLine(ChartTrendLineModel trendLine) {
        if (trendLine == null) {
            return null;
        }


        XNode trendlineNode = XNode.make("c:trendline");

        // 构建名称
        buildName(trendlineNode, trendLine);

        // 构建趋势线类型
        buildTrendLineType(trendlineNode, trendLine);

        // 构建多项式阶数
        buildPolynomialOrder(trendlineNode, trendLine);

        // 构建移动平均周期
        buildMovingAveragePeriod(trendlineNode, trendLine);

        // 构建前向预测
        buildForward(trendlineNode, trendLine);

        // 构建后向预测
        buildBackward(trendlineNode, trendLine);

        // 构建截距
        buildIntercept(trendlineNode, trendLine);

        // 构建显示选项
        buildDisplayOptions(trendlineNode, trendLine);

        // 构建样式
        buildStyle(trendlineNode, trendLine);

        return trendlineNode;

    }

    /**
     * 构建名称
     */
    private void buildName(XNode trendlineNode, ChartTrendLineModel trendLine) {

        if (!StringHelper.isEmpty(trendLine.getName())) {
            XNode nameNode = trendlineNode.addChild("c:name");
            nameNode.setText(trendLine.getName());
        }

    }

    /**
     * 构建趋势线类型
     */
    private void buildTrendLineType(XNode trendlineNode, ChartTrendLineModel trendLine) {

        if (trendLine.getType() != null) {
            XNode trendlineTypeNode = trendlineNode.addChild("c:trendlineType");
            trendlineTypeNode.setAttr("val", mapTrendLineTypeToOoxml(trendLine.getType()));
        }

    }

    /**
     * 映射趋势线类型到OOXML值
     */
    private String mapTrendLineTypeToOoxml(ChartTrendLineType type) {
        if (type == null) {
            return "linear";
        }

        switch (type) {
            case LINEAR:
                return "linear";
            case EXPONENTIAL:
                return "exp";
            case LOGARITHMIC:
                return "log";
            case POLYNOMIAL:
                return "poly";
            case POWER:
                return "power";
            case MOVING_AVG:
                return "movingAvg";
            default:
                LOG.warn("Unknown trend line type: {}, using default linear", type);
                return "linear";
        }
    }

    /**
     * 构建多项式阶数
     * 注意：当前模型不支持polynomialOrder属性，使用默认值
     */
    private void buildPolynomialOrder(XNode trendlineNode, ChartTrendLineModel trendLine) {

        if (trendLine.getType() == ChartTrendLineType.POLYNOMIAL) {
            XNode orderNode = trendlineNode.addChild("c:order");
            orderNode.setAttr("val", "2"); // 默认二次多项式
        }

    }

    /**
     * 构建移动平均周期
     */
    private void buildMovingAveragePeriod(XNode trendlineNode, ChartTrendLineModel trendLine) {

        if (trendLine.getPeriod() != null && trendLine.getType() == ChartTrendLineType.MOVING_AVG) {
            XNode periodNode = trendlineNode.addChild("c:period");
            periodNode.setAttr("val", trendLine.getPeriod().toString());
        }

    }

    /**
     * 构建前向预测
     * 注意：当前模型不支持forward属性，跳过此配置
     */
    private void buildForward(XNode trendlineNode, ChartTrendLineModel trendLine) {

        // 当前模型不支持forward属性，跳过
        // 如果需要支持，需要在chart.xdef中添加forward属性定义

    }

    /**
     * 构建后向预测
     * 注意：当前模型不支持backward属性，跳过此配置
     */
    private void buildBackward(XNode trendlineNode, ChartTrendLineModel trendLine) {

        // 当前模型不支持backward属性，跳过
        // 如果需要支持，需要在chart.xdef中添加backward属性定义

    }

    /**
     * 构建截距
     * 注意：当前模型不支持intercept属性，跳过此配置
     */
    private void buildIntercept(XNode trendlineNode, ChartTrendLineModel trendLine) {

        // 当前模型不支持intercept属性，跳过
        // 如果需要支持，需要在chart.xdef中添加intercept属性定义

    }

    /**
     * 构建显示选项
     */
    private void buildDisplayOptions(XNode trendlineNode, ChartTrendLineModel trendLine) {

        // 显示方程式
        if (trendLine.getDisplayEquation() != null) {
            XNode dispEqNode = trendlineNode.addChild("c:dispEq");
            dispEqNode.setAttr("val", trendLine.getDisplayEquation() ? "1" : "0");
        }

        // 注意：当前模型不支持displayRSquaredValue属性
        // 如果需要支持R平方值显示，需要在chart.xdef中添加相应属性定义

    }

    /**
     * 构建样式
     */
    private void buildStyle(XNode trendlineNode, ChartTrendLineModel trendLine) {

        // 使用lineStyle而不是shapeStyle，因为当前模型只支持lineStyle
        io.nop.excel.chart.model.ChartLineStyleModel lineStyle = trendLine.getLineStyle();
        if (lineStyle != null) {
            // 创建spPr节点来包含线条样式
            XNode spPrNode = XNode.make("c:spPr");

            // 构建线条属性
            XNode lnNode = spPrNode.addChild("a:ln");
            lnNode.setAttr("xmlns:a", "http://schemas.openxmlformats.org/drawingml/2006/main");

            // 设置线条宽度（如果有）
            if (lineStyle.getWidth() != null) {
                // OOXML中线条宽度使用EMU单位，1pt = 12700 EMU
                int widthEmu = (int) (lineStyle.getWidth() * 12700);
                lnNode.setAttr("w", String.valueOf(widthEmu));
            }

            // 设置线条颜色（如果有）
            if (lineStyle.getColor() != null) {
                XNode solidFillNode = lnNode.addChild("a:solidFill");
                XNode srgbClrNode = solidFillNode.addChild("a:srgbClr");
                srgbClrNode.setAttr("val", lineStyle.getColor().replace("#", ""));
            }

            // 设置线条样式（如果有）
            if (lineStyle.getStyle() != null) {
                XNode prstDashNode = lnNode.addChild("a:prstDash");
                String dashStyle = mapLineStyleToDash(lineStyle.getStyle());
                prstDashNode.setAttr("val", dashStyle);
            }

            trendlineNode.appendChild(spPrNode);
        }

    }

    /**
     * 映射线条样式到OOXML虚线样式
     */
    private String mapLineStyleToDash(io.nop.excel.chart.constants.ChartLineStyle style) {
        if (style == null) return "solid";

        switch (style) {
            case SOLID:
                return "solid";
            case DASH:
                return "dash";
            case DOT:
                return "dot";
            case DASH_DOT:
                return "dashDot";
            case DASH_DOT_DOT:
                return "lgDashDotDot";
            default:
                return "solid";
        }
    }

    /**
     * 构建简单的趋势线（仅包含类型）
     * 这是一个便利方法，用于快速创建基本的趋势线配置
     *
     * @param type 趋势线类型
     * @return 趋势线XNode，如果type为null则返回null
     */
    public XNode buildSimpleTrendLine(ChartTrendLineType type) {
        if (type == null) {
            return null;
        }

        // 创建趋势线模型
        ChartTrendLineModel trendLine = new ChartTrendLineModel();
        trendLine.setType(type);

        return buildTrendLine(trendLine);
    }

    /**
     * 构建带有预测的趋势线
     * 这是一个便利方法，用于快速创建带有预测的趋势线配置
     *
     * @param type     趋势线类型
     * @param forward  前向预测周期
     * @param backward 后向预测周期
     * @return 趋势线XNode，如果type为null则返回null
     */
    public XNode buildTrendLineWithForecast(ChartTrendLineType type, Double forward, Double backward) {
        if (type == null) {
            return null;
        }

        // 创建趋势线模型
        ChartTrendLineModel trendLine = new ChartTrendLineModel();
        trendLine.setType(type);
        // trendLine.setForward(forward);
        // trendLine.setBackward(backward);

        return buildTrendLine(trendLine);
    }

    /**
     * 构建多项式趋势线
     * 这是一个便利方法，用于快速创建多项式趋势线配置
     *
     * @param order 多项式阶数
     * @return 趋势线XNode
     */
    public XNode buildPolynomialTrendLine(Integer order) {
        // 创建趋势线模型
        ChartTrendLineModel trendLine = new ChartTrendLineModel();
        trendLine.setType(ChartTrendLineType.POLYNOMIAL);
        //trendLine.setPolynomialOrder(order != null ? order : 2); // 默认二次

        return buildTrendLine(trendLine);
    }

    /**
     * 构建移动平均趋势线
     * 这是一个便利方法，用于快速创建移动平均趋势线配置
     *
     * @param period 移动平均周期
     * @return 趋势线XNode
     */
    public XNode buildMovingAverageTrendLine(Integer period) {
        // 创建趋势线模型
        ChartTrendLineModel trendLine = new ChartTrendLineModel();
        trendLine.setType(ChartTrendLineType.MOVING_AVG);
        trendLine.setPeriod(period != null ? period : 2); // 默认2周期

        return buildTrendLine(trendLine);
    }
}