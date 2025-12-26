package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 空单元格绘制方式枚举
 * 对应 OOXML <c:dispBlanksAs val="…"/>
 */
public enum ChartDispBlankAs {

    /**
     * 留空：数据点处直接断开，柱形/折线出现空缺
     */
    GAP("gap"),

    /**
     * 直线连接：用直线越过空值，仅折线/面积图有效
     */
    SPAN("span"),

    /**
     * 作零处理：空值被当成 0 绘制
     */
    ZERO("zero");

    private final String value;

    ChartDispBlankAs(String value) {
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
    public static ChartDispBlankAs fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartDispBlankAs b : values()) {
            if (b.value.equals(v)) {
                return b;
            }
        }
        throw new IllegalArgumentException("illegal dispBlanksAs value: " + v);
    }
}