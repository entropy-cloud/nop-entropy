package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 图表填充类型
 * 对应 OOXML 的填充类型和 POI 的 FillType
 * 用于指定 ChartFillModel 中的填充方式
 */
public enum ChartFillType {
    /**
     * 无填充
     * OOXML: noFill
     */
    NONE("none"),

    /**
     * 纯色填充
     * OOXML: solidFill
     */
    SOLID("solid"),

    /**
     * 渐变填充
     * OOXML: gradFill
     */
    GRADIENT("gradient"),

    /**
     * 图案填充
     * OOXML: pattFill
     */
    PATTERN("pattern"),

    /**
     * 图片填充
     * OOXML: blipFill
     */
    PICTURE("picture");

    private final String value;

    ChartFillType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString() {
        return value;
    }

    @StaticFactoryMethod
    public static ChartFillType fromValue(String value) {
        if (StringHelper.isEmpty(value))
            return null;

        for (ChartFillType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * 是否需要颜色信息
     */
    public boolean requiresColor() {
        return this == SOLID || this == GRADIENT || this == PATTERN;
    }

    /**
     * 是否支持透明度
     */
    public boolean supportsOpacity() {
        return this != NONE;
    }
}