package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 雷达图样式枚举
 * 对应 Excel 中的雷达图样式类型
 * 根据 OOXML 规范 CT_RadarStyle
 */
public enum ChartRadarStyle {
    /**
     * 标准雷达图 (线条)
     * 对应 Excel 中的"雷达图"
     */
    STANDARD("standard"),

    /**
     * 标记雷达图。仅显示数据点标记，线条不可见或很细
     */
    MARKER("marker"),

    /**
     * 填充雷达图 (填充区域)
     * 对应 Excel 中的"填充雷达图"
     */
    FILLED("filled");

    private final String value;

    ChartRadarStyle(String value) {
        this.value = value;
    }

    public String value() {
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
    public static ChartRadarStyle fromValue(String value) {
        if (StringHelper.isEmpty(value)) {
            return null;
        }

        for (ChartRadarStyle style : values()) {
            if (style.value.equals(value)) {
                return style;
            }
        }
        throw new IllegalArgumentException("Unknown radar chart style: " + value);

    }
}