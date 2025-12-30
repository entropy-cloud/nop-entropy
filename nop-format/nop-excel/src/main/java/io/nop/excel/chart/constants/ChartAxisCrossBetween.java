package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 刻度线位置枚举
 * 对应 OOXML <c:crossBetween val="…"/>
 */
public enum ChartAxisCrossBetween {
    /**
     * 在分类刻度线之间交叉。
     * 这是柱状图和条形图的默认行为。
     * The value axis crosses the category axis between tick marks.
     */
    BETWEEN("between"),

    /**
     * 在分类的中点（即刻度线上）交叉。
     * 这是折线图和散点图的默认行为。
     * The value axis crosses the category axis at the midpoint of a category.
     */
    MID_CAT("midCat");

    private final String value;

    ChartAxisCrossBetween(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }

    public String value() {
        return value;
    }

    @StaticFactoryMethod
    public static ChartAxisCrossBetween fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartAxisCrossBetween cb : values()) {
            if (cb.value.equals(v)) {
                return cb;
            }
        }
        throw new IllegalArgumentException("Illegal axis crossBetween value: " + v);
    }
}