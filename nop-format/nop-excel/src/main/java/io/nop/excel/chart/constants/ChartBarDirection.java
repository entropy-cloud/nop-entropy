package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 柱形/条形图“方向”枚举
 * 对应 OOXML <c:barDir val="…"/>
 */
public enum ChartBarDirection {
    /**
     * 垂直柱形图：类别轴在底部，数值轴在左侧
     */
    COLUMN("col"),

    /**
     * 水平条形图：类别轴在左侧，数值轴在底部
     */
    BAR("bar");


    private final String value;

    ChartBarDirection(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * XML 值 → 枚举
     */
    @StaticFactoryMethod
    public static ChartBarDirection fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartBarDirection d : values()) {
            if (d.value.equals(v)) {
                return d;
            }
        }
        throw new IllegalArgumentException("illegal barDir value: " + v);
    }
}