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
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.excel.model.ExcelFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartTitleBuilder - 标题构建器
 * 负责生成OOXML图表中的标题配置
 * 将内部的ChartTitleModel转换为OOXML的c:title元素
 */
public class ChartTitleBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTitleBuilder.class);

    public static final ChartTitleBuilder INSTANCE = new ChartTitleBuilder();

    /**
     * 构建标题配置
     *
     * @param title 标题模型对象
     * @return 标题XNode，如果title为null则返回null
     */
    public XNode buildTitle(ChartTitleModel title) {
        if (title == null) {
            return null;
        }


        XNode titleNode = XNode.make("c:title");

        // 构建文本内容
        buildTextContent(titleNode, title);

        // 构建手动布局
        buildManualLayout(titleNode, title);

        // 构建形状样式
        buildShapeStyle(titleNode, title);

        // 构建文本样式
        buildTextStyle(titleNode, title);

        return titleNode;

    }

    /**
     * 构建文本内容
     */
    private void buildTextContent(XNode titleNode, ChartTitleModel title) {

        // 检查是否有文本内容
        if (StringHelper.isEmpty(title.getText()) && StringHelper.isEmpty(title.getTextCellRef())) {
            return;
        }

        XNode txNode = titleNode.addChild("c:tx");

        // 使用ChartTextBuilder自动选择合适的文本类型
        // 从textStyle中提取字体信息
        ExcelFont font = null;
        ChartTextStyleModel textStyle = title.getTextStyle();
        if (textStyle != null && textStyle.getFont() != null) {
            font = textStyle.getFont();
        }

        XNode textNode = ChartTextBuilder.INSTANCE.buildText(title.getText(), title.getTextCellRef(), font);
        if (textNode != null) {
            txNode.appendChild(textNode);
        }

    }

    /**
     * 构建手动布局
     */
    private void buildManualLayout(XNode titleNode, ChartTitleModel title) {

        ChartManualLayoutModel manualLayout = title.getManualLayout();
        if (manualLayout != null) {
            XNode layoutNode = ChartManualLayoutBuilder.INSTANCE.buildManualLayout(manualLayout);
            if (layoutNode != null) {
                titleNode.appendChild(layoutNode);
            }
        }

    }

    /**
     * 构建形状样式
     */
    private void buildShapeStyle(XNode titleNode, ChartTitleModel title) {

        ChartShapeStyleModel shapeStyle = title.getShapeStyle();
        if (shapeStyle != null) {
            XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(shapeStyle);
            if (spPrNode != null) {
                spPrNode.setTagName("c:spPr");
                titleNode.appendChild(spPrNode);
            }
        }

    }

    /**
     * 构建文本样式
     */
    private void buildTextStyle(XNode titleNode, ChartTitleModel title) {

        ChartTextStyleModel textStyle = title.getTextStyle();
        if (textStyle != null) {
            XNode txPrNode = ChartTextStyleBuilder.INSTANCE.buildTextStyle(textStyle);
            if (txPrNode != null) {
                titleNode.appendChild(txPrNode.withTagName("c:txPr"));
            }
        }

    }

    /**
     * 构建标题覆盖选项（用于图例等元素）
     */
    public XNode buildTitleOverlay(ChartTitleModel title) {
        if (title == null) {
            return null;
        }


        XNode titleNode = XNode.make("c:title");

        // 只构建覆盖相关的属性
        buildTextContent(titleNode, title);
        buildShapeStyle(titleNode, title);
        buildTextStyle(titleNode, title);

        return titleNode;

    }

    /**
     * 构建简单的标题（仅包含文本）
     * 这是一个便利方法，用于快速创建基本的标题配置
     *
     * @param text 标题文本
     * @return 标题XNode，如果text为null或空则返回null
     */
    public XNode buildSimpleTitle(String text) {
        if (StringHelper.isEmpty(text)) {
            return null;
        }

        // 创建标题模型
        ChartTitleModel title = new ChartTitleModel();
        title.setText(text);
        title.setVisible(true);

        return buildTitle(title);
    }

    /**
     * 构建带有单元格引用的标题
     * 这是一个便利方法，用于快速创建引用单元格的标题配置
     *
     * @param cellRef 单元格引用
     * @return 标题XNode，如果cellRef为null或空则返回null
     */
    public XNode buildTitleWithCellRef(String cellRef) {
        if (StringHelper.isEmpty(cellRef)) {
            return null;
        }

        // 创建标题模型
        ChartTitleModel title = new ChartTitleModel();
        title.setTextCellRef(cellRef);
        title.setVisible(true);

        return buildTitle(title);
    }

    /**
     * 构建带有布局的标题
     * 这是一个便利方法，用于快速创建带有布局的标题配置
     *
     * @param text   标题文本
     * @param layout 手动布局
     * @return 标题XNode，如果text为null或空则返回null
     */
    public XNode buildTitleWithLayout(String text, ChartManualLayoutModel layout) {
        if (StringHelper.isEmpty(text)) {
            return null;
        }

        // 创建标题模型
        ChartTitleModel title = new ChartTitleModel();
        title.setText(text);
        title.setVisible(true);
        title.setManualLayout(layout);

        return buildTitle(title);
    }
}