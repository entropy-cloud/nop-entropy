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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartManualLayoutBuilder - 手动布局构建器
 * 负责生成OOXML图表中的手动布局配置
 * 将内部的ChartManualLayoutModel转换为OOXML的c:manualLayout元素
 */
public class ChartManualLayoutBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartManualLayoutBuilder.class);

    public static final ChartManualLayoutBuilder INSTANCE = new ChartManualLayoutBuilder();

    /**
     * 构建手动布局元素
     *
     * @param layout 手动布局模型对象
     * @return 包含c:layout和c:manualLayout的XNode，如果layout为null则返回null
     */
    public XNode buildManualLayout(ChartManualLayoutModel layout) {
        if (layout == null) {
            return null;
        }

        try {
            // 检查是否有任何布局属性被设置
            if (!hasAnyLayoutProperty(layout)) {
                LOG.debug("No layout properties set, skipping manual layout generation");
                return null;
            }

            // 创建c:layout元素
            XNode layoutNode = XNode.make("c:layout");

            // 创建c:manualLayout子元素
            XNode manualLayoutNode = layoutNode.addChild("c:manualLayout");

            // 构建布局属性
            buildLayoutProperties(manualLayoutNode, layout);

            return layoutNode;

        } catch (Exception e) {
            LOG.warn("Failed to build manual layout configuration", e);
            return null;
        }
    }

    /**
     * 检查布局模型是否有任何属性被设置
     */
    private boolean hasAnyLayoutProperty(ChartManualLayoutModel layout) {
        return layout.getPercentX() != null ||
                layout.getPercentY() != null ||
                layout.getPercentW() != null ||
                layout.getPercentH() != null;
    }

    /**
     * 构建布局属性元素
     *
     * @param manualLayoutNode c:manualLayout节点
     * @param layout           布局模型
     */
    private void buildLayoutProperties(XNode manualLayoutNode, ChartManualLayoutModel layout) {
        // 添加默认的布局模式配置

        // 添加默认的layoutTarget
        addLayoutTarget(manualLayoutNode);

        // 统一使用edge模式，这与解析器的规范化策略保持一致
        addLayoutModes(manualLayoutNode, layout);

        // 构建X位置
        buildLayoutValue(manualLayoutNode, "c:x", layout.getPercentX());

        // 构建Y位置
        buildLayoutValue(manualLayoutNode, "c:y", layout.getPercentY());

        // 构建宽度
        buildLayoutValue(manualLayoutNode, "c:w", layout.getPercentW());

        // 构建高度
        buildLayoutValue(manualLayoutNode, "c:h", layout.getPercentH());

    }

    /**
     * 构建单个布局值元素
     *
     * @param parentNode 父节点
     * @param tagName    元素标签名
     * @param value      布局值（0.0-1.0的百分比）
     */
    private void buildLayoutValue(XNode parentNode, String tagName, Double value) {
        if (value == null) {
            return;
        }

        try {
            // 创建值元素
            XNode valueNode = parentNode.addChild(tagName);

            // 设置val属性
            // OOXML中布局值通常是百分比形式，需要转换
            Double ooxmlValue = convertToOoxmlValue(value);
            valueNode.setAttr("val", ooxmlValue.toString());

            LOG.debug("Built layout value {}: {} -> {}", tagName, value, ooxmlValue);

        } catch (Exception e) {
            LOG.warn("Failed to build layout value for {}", tagName, e);
        }
    }

    /**
     * 将内部百分比值转换为OOXML格式
     * 内部使用0.0-1.0的小数，OOXML中通常使用相同格式
     *
     * @param value 内部百分比值
     * @return OOXML格式的值
     */
    private Double convertToOoxmlValue(Double value) {
        if (value == null) {
            return null;
        }

        // 确保值在合理范围内
        if (value < 0.0) {
            LOG.warn("Layout value {} is negative, clamping to 0.0", value);
            return 0.0;
        }

        if (value > 1.0) {
            LOG.warn("Layout value {} is greater than 1.0, clamping to 1.0", value);
            return 1.0;
        }

        return value;
    }


    private void addLayoutModes(XNode manualLayoutNode, ChartManualLayoutModel layout) {

        addModeElement(manualLayoutNode, "c:xMode", "edge");
        addModeElement(manualLayoutNode, "c:yMode", "edge");

    }

    /**
     * 添加模式元素
     *
     * @param parentNode 父节点
     * @param tagName    模式元素标签名
     * @param mode       模式值
     */
    private void addModeElement(XNode parentNode, String tagName, String mode) {
        if (StringHelper.isEmpty(mode)) {
            return;
        }

        XNode modeNode = parentNode.addChild(tagName);
        modeNode.setAttr("val", mode);

        LOG.debug("Added layout mode {}: {}", tagName, mode);
    }

    /**
     * 添加布局目标配置
     *
     * @param manualLayoutNode c:manualLayout节点
     */
    private void addLayoutTarget(XNode manualLayoutNode) {
        XNode targetNode = manualLayoutNode.addChild("c:layoutTarget");
        targetNode.setAttr("val", "inner");

        LOG.debug("Added layout target: inner");
    }

    /**
     * 构建简单的手动布局（仅包含位置和尺寸）
     * 这是一个便利方法，用于快速创建基本的布局配置
     *
     * @param x X位置百分比 (0.0-1.0)
     * @param y Y位置百分比 (0.0-1.0)
     * @param w 宽度百分比 (0.0-1.0)
     * @param h 高度百分比 (0.0-1.0)
     * @return 布局XNode，如果所有参数都为null则返回null
     */
    public XNode buildSimpleLayout(Double x, Double y, Double w, Double h) {
        // 检查是否有任何参数被设置
        if (x == null && y == null && w == null && h == null) {
            return null;
        }

        // 创建布局模型
        ChartManualLayoutModel layout = new ChartManualLayoutModel();
        layout.setPercentX(x);
        layout.setPercentY(y);
        layout.setPercentW(w);
        layout.setPercentH(h);

        return buildManualLayout(layout);
    }
}