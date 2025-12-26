package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 图表标签对齐方式
 * 对应 OOXML 中的 lblAlgn 属性值
 * 用于指定坐标轴标签的对齐方式
 */
public enum ChartLabelAlignment {
    /**
     * 左对齐
     * OOXML: l
     */
    LEFT("l"),

    /**
     * 居中对齐
     * OOXML: ctr
     */
    CENTER("ctr"),

    /**
     * 右对齐
     * OOXML: r
     */
    RIGHT("r");

    private final String value;

    ChartLabelAlignment(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * 根据字符串值获取对应的枚举
     */
    @StaticFactoryMethod
    public static ChartLabelAlignment fromValue(String value) {
        if (StringHelper.isEmpty(value)) {
            return null;
        }

        for (ChartLabelAlignment alignment : values()) {
            if (alignment.value.equals(value)) {
                return alignment;
            }
        }
        
        // 兼容性处理：支持常见的英文名称
        String lowerValue = value.toLowerCase();
        switch (lowerValue) {
            case "left":
                return LEFT;
            case "center":
            case "centre":
                return CENTER;
            case "right":
                return RIGHT;
            default:
                throw new IllegalArgumentException("Unknown chart label alignment: " + value);
        }
    }

    /**
     * 获取对应的 CSS text-align 值
     */
    public String toCssTextAlign() {
        switch (this) {
            case LEFT:
                return "left";
            case CENTER:
                return "center";
            case RIGHT:
                return "right";
            default:
                return "center";
        }
    }

    /**
     * 是否为水平居中
     */
    public boolean isCenter() {
        return this == CENTER;
    }

    /**
     * 是否为左对齐
     */
    public boolean isLeft() {
        return this == LEFT;
    }

    /**
     * 是否为右对齐
     */
    public boolean isRight() {
        return this == RIGHT;
    }
}