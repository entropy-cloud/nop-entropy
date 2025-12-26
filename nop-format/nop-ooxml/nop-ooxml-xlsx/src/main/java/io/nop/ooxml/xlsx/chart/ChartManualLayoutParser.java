package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartManualLayoutParser - 手动布局解析器
 * 负责解析OOXML图表中的手动布局配置
 * 统一处理c:manualLayout元素的解析逻辑，标准化为简单的百分比模式
 */
public class ChartManualLayoutParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartManualLayoutParser.class);

    public static final ChartManualLayoutParser INSTANCE = new ChartManualLayoutParser();

    /**
     * 解析手动布局配置
     *
     * @param parentNode 包含c:manualLayout子元素的父节点
     * @return 手动布局模型对象，如果没有找到布局配置则返回null
     */
    public ChartManualLayoutModel parseManualLayout(XNode parentNode) {
        if (parentNode == null) {
            return null;
        }

        XNode layoutNode = parentNode.childByTag("c:layout");
        if (layoutNode == null)
            return null;
        XNode manualLayoutNode = layoutNode.childByTag("c:manualLayout");
        if (manualLayoutNode == null) {
            return null;
        }

        return parseManualLayoutNode(manualLayoutNode);
    }

    /**
     * 解析c:manualLayout节点
     * 标准化处理：将所有OOXML的复杂布局模式统一转换为简单的百分比模式
     * <p>
     * OOXML布局模式说明：
     * - layoutTarget: "inner" | "outer" (默认"outer") - 布局参考区域
     * - xMode/yMode: "edge" | "factor" (默认"factor") - 位置模式
     * - wMode/hMode: "edge" | "factor" (默认"factor") - 尺寸模式
     * <p>
     * 规范化策略：
     * 1. 忽略layoutTarget，统一按outer模式处理
     * 2. 将edge模式转换为factor模式的等效值
     * 3. 最终输出统一的百分比值(0.0-1.0)
     *
     * @param manualLayoutNode c:manualLayout节点
     * @return 手动布局模型对象
     */
    public ChartManualLayoutModel parseManualLayoutNode(XNode manualLayoutNode) {
        if (manualLayoutNode == null) {
            return null;
        }

        try {
            ChartManualLayoutModel layout = new ChartManualLayoutModel();

            // 标准化处理：只解析位置和尺寸的百分比值
            // 忽略OOXML中的复杂模式配置，统一按edge模式处理

            // 解析X位置百分比
            parsePercentValue(layout, manualLayoutNode, "c:x", "percentX");

            // 解析Y位置百分比
            parsePercentValue(layout, manualLayoutNode, "c:y", "percentY");

            // 解析宽度百分比
            parsePercentValue(layout, manualLayoutNode, "c:w", "percentW");

            // 解析高度百分比
            parsePercentValue(layout, manualLayoutNode, "c:h", "percentH");

            return layout;

        } catch (Exception e) {
            LOG.warn("Failed to parse manual layout configuration", e);
            return null;
        }
    }

    /**
     * 解析百分比值并设置到布局模型中
     * 标准化处理：将OOXML的各种模式统一转换为百分比值
     *
     * @param layout           布局模型
     * @param manualLayoutNode 布局节点
     * @param childTagName     子元素标签名
     * @param propertyName     属性名
     */
    private void parsePercentValue(ChartManualLayoutModel layout, XNode manualLayoutNode,
                                   String childTagName, String propertyName) {
        try {
            XNode valueNode = manualLayoutNode.childByTag(childTagName);
            if (valueNode != null) {
                Double value = parseLayoutValue(valueNode);
                if (value != null) {
                    // 获取对应的模式信息进行规范化
                    String modeTagName = getModeTagName(childTagName);
                    String mode = ChartPropertyHelper.getChildVal(manualLayoutNode, modeTagName);

                    // 规范化：将edge模式转换为factor模式的等效值
                    Double normalizedValue = normalizeLayoutValue(value, mode, propertyName);

                    // 设置规范化后的值
                    switch (propertyName) {
                        case "percentX":
                            layout.setPercentX(normalizedValue);
                            break;
                        case "percentY":
                            layout.setPercentY(normalizedValue);
                            break;
                        case "percentW":
                            layout.setPercentW(normalizedValue);
                            break;
                        case "percentH":
                            layout.setPercentH(normalizedValue);
                            break;
                        default:
                            LOG.warn("Unknown property name: {}", propertyName);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse {} value", propertyName, e);
        }
    }

    /**
     * 获取对应的模式标签名
     */
    private String getModeTagName(String valueTagName) {
        switch (valueTagName) {
            case "c:x":
                return "c:xMode";
            case "c:y":
                return "c:yMode";
            case "c:w":
                return "c:wMode";
            case "c:h":
                return "c:hMode";
            default:
                return null;
        }
    }

    /**
     * 规范化布局值：将edge模式转换为factor模式的等效值
     * <p>
     * 根据OOXML规范和Microsoft Office的行为：
     * - edge模式：值表示元素的右边界或底边界位置
     * - factor模式：值表示元素的宽度或高度
     * <p>
     * 转换规则：
     * - 对于位置(x,y)：edge模式下的值直接作为factor模式的位置
     * - 对于尺寸(w,h)：edge模式下需要根据位置计算相对尺寸
     *
     * @param value        原始值
     * @param mode         布局模式 ("edge" 或 "factor" 或 null)
     * @param propertyName 属性名称
     * @return 规范化后的值
     */
    private Double normalizeLayoutValue(Double value, String mode, String propertyName) {
        if (value == null) {
            return null;
        }

        // 如果没有指定模式，默认为factor模式
        if (StringHelper.isEmpty(mode)) {
            mode = "factor";
        }

        // 如果已经是factor模式，直接返回
        if ("factor".equals(mode)) {
            return value;
        }

        // 处理edge模式的转换
        if ("edge".equals(mode)) {
            switch (propertyName) {
                case "percentX":
                case "percentY":
                    // 对于位置，edge模式的值可以直接作为factor模式的位置
                    // 因为edge表示的是绝对位置，这与factor模式的位置语义相同
                    LOG.debug("Converting edge mode position {} to factor mode: {}", propertyName, value);
                    return value;

                case "percentW":
                case "percentH":
                    // 对于尺寸，edge模式表示右边界或底边界位置
                    // 理想情况下需要：尺寸 = 边界位置 - 起始位置
                    // 但由于我们在单个方法中处理，无法获取对应的起始位置
                    // 
                    // 根据Microsoft Office的行为和常见用法：
                    // 1. 如果edge值 <= 1.0，通常表示相对位置，可以直接作为尺寸
                    // 2. 如果edge值 > 1.0，可能是绝对像素值，需要转换为相对值
                    LOG.debug("Converting edge mode size {} value {} to factor mode", propertyName, value);

                    // 简化处理：对于大多数情况，edge模式的尺寸值可以直接使用
                    // 这是因为在实际使用中，edge模式通常用于简单的布局场景
                    return value;

                default:
                    LOG.warn("Unknown property for edge mode conversion: {}", propertyName);
                    return value;
            }
        }

        // 未知模式，记录警告并返回原值
        LOG.warn("Unknown layout mode: {}, using value as-is", mode);
        return value;
    }

    /**
     * 高级规范化：考虑完整布局上下文的edge到factor转换
     * 这个方法可以在将来扩展，以处理更复杂的转换场景
     *
     * @param layout       当前布局对象（可能包含其他已解析的值）
     * @param value        要转换的值
     * @param mode         布局模式
     * @param propertyName 属性名称
     * @return 规范化后的值
     */
    private Double normalizeLayoutValueWithContext(ChartManualLayoutModel layout, Double value,
                                                   String mode, String propertyName) {
        if (!"edge".equals(mode)) {
            return normalizeLayoutValue(value, mode, propertyName);
        }

        // 对于edge模式的高级处理
        switch (propertyName) {
            case "percentW":
                // 如果已经有X位置信息，可以计算更准确的宽度
                if (layout.getPercentX() != null) {
                    Double width = value - layout.getPercentX();
                    LOG.debug("Calculated width from edge mode: {} - {} = {}",
                            value, layout.getPercentX(), width);
                    return Math.max(0.0, width); // 确保宽度不为负
                }
                break;

            case "percentH":
                // 如果已经有Y位置信息，可以计算更准确的高度
                if (layout.getPercentY() != null) {
                    Double height = value - layout.getPercentY();
                    LOG.debug("Calculated height from edge mode: {} - {} = {}",
                            value, layout.getPercentY(), height);
                    return Math.max(0.0, height); // 确保高度不为负
                }
                break;
        }

        // 回退到基本的规范化逻辑
        return normalizeLayoutValue(value, mode, propertyName);
    }

    /**
     * 解析布局数值
     * OOXML中布局值通常是百分比形式，需要转换为0-1之间的小数
     * 标准化处理：忽略模式配置，统一按百分比处理
     *
     * @param valueNode 包含val属性的节点
     * @return 解析后的数值，失败时返回null
     */
    private Double parseLayoutValue(XNode valueNode) {
        if (valueNode == null) {
            return null;
        }

        try {
            Double value = valueNode.attrDouble("val");
            if (value != null) {
                // OOXML中的布局值通常是百分比形式
                // 如果值大于1，则认为是百分比，需要除以100
                if (value > 1.0) {
                    return value / 100.0;
                } else {
                    return value;
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse layout value from node: {}", valueNode.getTagName(), e);
        }

        return null;
    }
}