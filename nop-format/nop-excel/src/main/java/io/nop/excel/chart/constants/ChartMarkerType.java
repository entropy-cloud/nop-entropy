package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

public enum ChartMarkerType {
    AUTO("auto"),           // OOXML: auto
    CIRCLE("circle"),       // OOXML: circle
    SQUARE("square"),       // OOXML: square  
    DIAMOND("diamond"),     // OOXML: diamond
    TRIANGLE("triangle"),   // OOXML: triangle
    X("x"),                 // OOXML: x
    STAR("star"),           // OOXML: star
    PLUS("plus"),           // OOXML: plus
    DASH("dash"),           // OOXML: dash
    DOT("dot"),             // OOXML: dot
    NONE("none");           // OOXML: none

    private final String value;

    ChartMarkerType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString() {
        return value;
    }

    /**
     * 获取自动选择时的实际标记类型
     * AUTO 类型会根据索引自动选择一个具体的标记类型
     *
     * @param index 系列索引（从0开始）
     * @return 自动选择的具体标记类型
     */
    public ChartMarkerType getAutoType(int index) {
        // 如果当前不是 AUTO 类型，直接返回自身
        if (this != AUTO) {
            return this;
        }

        // AUTO 类型的自动选择序列
        // 注意：DASH 和 DOT 通常不用于自动选择，因为它们不如其他形状明显
        // Excel 通常使用 CIRCLE, SQUARE, DIAMOND, TRIANGLE, X, STAR, PLUS 等
        // 使用索引取模确保循环
        int sequenceIndex = Math.abs(index) % AUTO_SEQUENCE.length;
        return AUTO_SEQUENCE[sequenceIndex];
    }

    static final ChartMarkerType[] AUTO_SEQUENCE = new ChartMarkerType[]{
            CIRCLE,
            SQUARE,
            DIAMOND,
            TRIANGLE,
            X,
            STAR,
            PLUS
            // 注意：不包含 DASH 和 DOT，因为它们不太明显
            // 也不包含 NONE 和 AUTO
    };

    /**
     * 获取所有可用于 AUTO 选择的标记类型
     * 这个方法提供了 AUTO 类型可选的范围
     */
    public static ChartMarkerType[] getAutoSelectableTypes() {
        return AUTO_SEQUENCE;
    }

    /**
     * 判断是否为实心标记（默认填充）
     */
    public boolean isFilledByDefault() {
        switch (this) {
            case CIRCLE:
            case SQUARE:
            case DIAMOND:
            case TRIANGLE:
            case STAR:
            case DOT:
                return true;
            case X:
            case PLUS:
            case DASH:
                return false; // 这些标记通常是空心的
            default:
                return true;
        }
    }

    /**
     * 判断是否应该显示边框
     */
    public boolean hasOutlineByDefault() {
        switch (this) {
            case DASH:
            case DOT:
                return false; // 短横线和小圆点通常不需要边框
            default:
                return true;
        }
    }

    /**
     * 获取默认大小乘数
     * 某些标记需要更大的尺寸才能清晰可见
     */
    public double getDefaultSizeMultiplier() {
        switch (this) {
            case X:
            case PLUS:
                return 1.2; // 稍大一些
            case DASH:
                return 1.5; // 长一些
            case DOT:
                return 0.5; // 小一些
            case STAR:
                return 1.1; // 稍大
            default:
                return 1.0;
        }
    }

    @StaticFactoryMethod
    public static ChartMarkerType fromValue(String value) {
        if (StringHelper.isEmpty(value))
            return null;

        for (ChartMarkerType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        // 如果未找到，尝试一些常见别名
        return fromAlias(value);
    }

    /**
     * 处理常见的别名
     */
    private static ChartMarkerType fromAlias(String alias) {
        switch (alias) {
            case "round":
            case "ellipse":
                return CIRCLE;
            case "rect":
            case "rectangle":
                return SQUARE;
            case "rhombus":
                return DIAMOND;
            case "arrow":
            case "triangle_up":
                return TRIANGLE;
            case "cross":
                return X;
            case "asterisk":
                return STAR;
            case "horizontal_bar":
            case "line":
                return DASH;
            case "point":
            case "small_circle":
                return DOT;
            case "vertical_cross":
                return PLUS;
            case "off":
            case "false":
            case "hidden":
                return NONE;
            case "true":
            case "default":
            case "":
                return AUTO;
            default:
                return AUTO; // 默认返回 AUTO
        }
    }

    /**
     * 判断是否显示标记
     */
    public boolean shouldShow() {
        return this != NONE;
    }
}