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
import io.nop.excel.chart.constants.ChartLineStyle;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartGridModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartGridBuilder - 网格线构建器
 * 负责生成OOXML图表中的网格线配置
 * 将内部的ChartGridModel转换为OOXML的网格线元素
 */
public class ChartGridBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartGridBuilder.class);

    public static final ChartGridBuilder INSTANCE = new ChartGridBuilder();

    /**
     * 构建主要网格线配置
     *
     * @param grid 网格线模型对象
     * @return 主要网格线XNode，如果grid为null或不可见则返回null
     */
    public XNode buildMajorGridLines(ChartGridModel grid) {
        if (grid == null || !grid.isVisible()) {
            return null;
        }


        XNode majorGridLinesNode = XNode.make("c:majorGridlines");
        buildGridLineStyle(majorGridLinesNode, grid);
        return majorGridLinesNode;

    }

    /**
     * 构建次要网格线配置
     *
     * @param grid 网格线模型对象
     * @return 次要网格线XNode，如果grid为null或不可见则返回null
     */
    public XNode buildMinorGridLines(ChartGridModel grid) {
        if (grid == null || !grid.isVisible()) {
            return null;
        }


        XNode minorGridLinesNode = XNode.make("c:minorGridlines");
        buildGridLineStyle(minorGridLinesNode, grid);
        return minorGridLinesNode;


    }

    /**
     * 构建网格线样式
     */
    private void buildGridLineStyle(XNode gridLinesNode, ChartGridModel grid) {

        // 创建形状样式模型
        ChartShapeStyleModel shapeStyle = createShapeStyleFromGrid(grid);
        if (shapeStyle != null) {
            XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
            if (spPrNode != null) {
                gridLinesNode.appendChild(spPrNode.withTagName("c:spPr"));
            }
        }

    }

    /**
     * 从网格线模型创建形状样式模型
     * 修复：正确创建边框模型，与parser实现保持一致
     */
    private ChartShapeStyleModel createShapeStyleFromGrid(ChartGridModel grid) {

        ChartShapeStyleModel shapeStyle = new ChartShapeStyleModel();

        // 创建边框模型 - 与parser中的extractLineProperties方法保持一致
        ChartBorderModel border = new ChartBorderModel();

        // 设置线条颜色
        if (!StringHelper.isEmpty(grid.getColor())) {
            border.setColor(grid.getColor());
        }

        // 设置线条宽度
        if (grid.getWidth() != null) {
            border.setWidth(grid.getWidth());
        }

        // 设置线条样式
        if (grid.getStyle() != null) {
            border.setStyle(grid.getStyle());
        } else {
            // 默认实线
            border.setStyle(ChartLineStyle.SOLID);
        }

        // 设置透明度
        if (grid.getOpacity() != null) {
            border.setOpacity(grid.getOpacity());
        }

        // 将边框模型设置到形状样式中
        shapeStyle.setBorder(border);

        return shapeStyle;


    }

    /**
     * 构建简单的网格线（仅包含颜色）
     * 这是一个便利方法，用于快速创建基本的网格线配置
     *
     * @param color   网格线颜色
     * @param isMajor 是否为主要网格线
     * @return 网格线XNode，如果color为null或空则返回null
     */
    public XNode buildSimpleGridLines(String color, boolean isMajor) {
        if (StringHelper.isEmpty(color)) {
            return null;
        }

        // 创建网格线模型
        ChartGridModel grid = new ChartGridModel();
        grid.setColor(color);
        grid.setVisible(true);

        return isMajor ? buildMajorGridLines(grid) : buildMinorGridLines(grid);
    }

    /**
     * 构建带有样式的网格线
     * 这是一个便利方法，用于快速创建带有样式的网格线配置
     *
     * @param color   网格线颜色
     * @param width   网格线宽度
     * @param style   网格线样式
     * @param isMajor 是否为主要网格线
     * @return 网格线XNode，如果color为null或空则返回null
     */
    public XNode buildStyledGridLines(String color, Double width, ChartLineStyle style, boolean isMajor) {
        if (StringHelper.isEmpty(color)) {
            return null;
        }

        // 创建网格线模型
        ChartGridModel grid = new ChartGridModel();
        grid.setColor(color);
        grid.setWidth(width);
        grid.setStyle(style);
        grid.setVisible(true);

        return isMajor ? buildMajorGridLines(grid) : buildMinorGridLines(grid);
    }
}