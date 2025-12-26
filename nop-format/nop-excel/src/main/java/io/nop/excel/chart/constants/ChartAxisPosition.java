package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 坐标轴位置枚举
 * 对应 OOXML <c:axPos val="…"/>
 */
public enum ChartAxisPosition {
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

    ChartAxisPosition(String value) {
        this.value = value;
    }

    public String toString(){
        return value;
    }

    public String value() {
        return value;
    }

    /**
     * XML 值 → 枚举
     */
    @StaticFactoryMethod
    public static ChartAxisPosition fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartAxisPosition pos : values()) {
            if (pos.value.equals(v)) {
                return pos;
            }
        }
        throw new IllegalArgumentException("Illegal axis position value: " + v);
    }
}