package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 坐标轴刻度线标记位置枚举
 * 对应 OOXML <c:majorTickMark val="…"/> 与 <c:minorTickMark val="…"/>
 */
public enum ChartTickMark {

    /**
     * 不显示刻度线
     */
    NONE("none"),

    /**
     * 刻度线朝向绘图区内部
     */
    INSIDE("inside"),

    /**
     * 刻度线朝向绘图区外部
     */
    OUTSIDE("outside"),

    /**
     * 刻度线横跨轴线（内外同时出现）
     */
    CROSS("cross");

    private final String value;

    ChartTickMark(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * XML 值 → 枚举
     */
    @StaticFactoryMethod
    public static ChartTickMark fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartTickMark m : values()) {
            if (m.value.equals(v)) {
                return m;
            }
        }
        throw new IllegalArgumentException("illegal tickMark value: " + v);
    }
}