package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 数据标签位置枚举
 * 对应 OOXML <c:dLblPos val="…"/>
 */
public enum ChartDataLabelPosition {

    /**
     * 居中（柱形/条形内部正中）
     */
    CENTER("ctr"),

    /**
     * 内侧顶端（柱形顶部内侧）
     */
    INSIDE_END("inEnd"),

    /**
     * 外侧顶端（柱形顶部外侧）
     */
    OUTSIDE_END("outEnd"),

    /**
     * 左侧
     */
    LEFT("l"),

    /**
     * 右侧
     */
    RIGHT("r"),

    /**
     * 顶部
     */
    TOP("t"),

    /**
     * 底部
     */
    BOTTOM("b");

    private final String value;

    ChartDataLabelPosition(String value) {
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
    public static ChartDataLabelPosition fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartDataLabelPosition p : values()) {
            if (p.value.equals(v)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Illegal dLblPos value: " + v);
    }
}