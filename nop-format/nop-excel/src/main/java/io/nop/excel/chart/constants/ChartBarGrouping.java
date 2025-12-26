package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 柱形/条形图“分组方式”枚举
 * 对应 OOXML <c:grouping val="…"/>
 */
public enum ChartBarGrouping {
    /**
     * 同一类别下多系列并排站立，高度互不影响
     */
    CLUSTERED("clustered"),

    /**
     * 与 clustered 同义，老版本兼容值；效果 = 簇状
     */
    STANDARD("standard"),

    /**
     * 同一类别下多系列上下堆叠，总高度 = 各值之和
     */
    STACKED("stacked"),

    /**
     * 同一类别下多系列上下堆叠，总高度恒 = 100%，各段显示百分比
     */
    PERCENT_STACKED("percentStacked");

    private final String value;

    ChartBarGrouping(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString(){
        return value;
    }

    /**
     * XML 值 → 枚举
     */
    @StaticFactoryMethod
    public static ChartBarGrouping fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartBarGrouping g : values()) {
            if (g.value.equals(v)) {
                return g;
            }
        }
        throw new IllegalArgumentException("Illegal grouping value " + v);
    }
}