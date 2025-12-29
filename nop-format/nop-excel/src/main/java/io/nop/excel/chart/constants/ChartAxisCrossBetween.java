package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 刻度线位置枚举
 * 对应 OOXML <c:crossBetween val="…"/>
 */
public enum ChartAxisCrossBetween {
    /**
     * 在刻度线之间
     */
    BETWEEN("between"),
    
    /**
     * 在刻度线上
     */
    ON_TICK("onTick");

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