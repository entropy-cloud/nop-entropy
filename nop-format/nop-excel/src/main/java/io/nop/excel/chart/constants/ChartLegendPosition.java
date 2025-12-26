package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 图表图例位置枚举
 * 对应 OOXML <c:legendPos val="…"/>
 */
public enum ChartLegendPosition {
    /**
     * 右侧
     */
    RIGHT("r"),
    
    /**
     * 左侧
     */
    LEFT("l"),
    
    /**
     * 顶部
     */
    TOP("t"),
    
    /**
     * 底部
     */
    BOTTOM("b"),
    
    /**
     * 右上角
     */
    TOP_RIGHT("tr"),
    
    /**
     * 左上角
     */
    TOP_LEFT("tl"),
    
    /**
     * 右下角
     */
    BOTTOM_RIGHT("br"),
    
    /**
     * 左下角
     */
    BOTTOM_LEFT("bl");

    private final String value;

    ChartLegendPosition(String value) {
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
    public static ChartLegendPosition fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartLegendPosition pos : values()) {
            if (pos.value.equals(v)) {
                return pos;
            }
        }
        throw new IllegalArgumentException("Illegal legend position value: " + v);
    }
}