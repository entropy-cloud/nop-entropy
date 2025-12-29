package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 坐标轴交叉位置枚举
 * 对应 OOXML <c:crosses val="…"/>
 */
public enum ChartAxisCrosses {
    /**
     * 在零值处交叉（自动）
     */
    AUTO_ZERO("autoZero"),
    
    /**
     * 在最大值处交叉
     */
    MAX("max"),
    
    /**
     * 在最小值处交叉
     */
    MIN("min");

    private final String value;

    ChartAxisCrosses(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }

    public String value() {
        return value;
    }

    @StaticFactoryMethod
    public static ChartAxisCrosses fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartAxisCrosses crosses : values()) {
            if (crosses.value.equals(v)) {
                return crosses;
            }
        }
        throw new IllegalArgumentException("Illegal axis crosses value: " + v);
    }
}