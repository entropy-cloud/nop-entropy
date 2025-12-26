package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 数据标签位置枚举
 * 对应 OOXML <c:dLblPos val="…"/>
 */
public enum ChartLabelPosition {
    /**
     * 最佳位置（自动）
     */
    BEST_FIT("bestFit"),
    
    /**
     * 居中
     */
    CENTER("ctr"),
    
    /**
     * 内部底端
     */
    IN_BASE("inBase"),
    
    /**
     * 内部末端
     */
    IN_END("inEnd"),
    
    /**
     * 外部
     */
    OUTSIDE("outEnd"),
    
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

    ChartLabelPosition(String value) {
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
    public static ChartLabelPosition fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartLabelPosition pos : values()) {
            if (pos.value.equals(v)) {
                return pos;
            }
        }
        throw new IllegalArgumentException("Illegal label position value: " + v);
    }
}