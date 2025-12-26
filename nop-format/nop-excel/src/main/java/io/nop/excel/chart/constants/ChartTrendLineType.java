package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 趋势线类型枚举
 * 对应 OOXML <c:trendlineType val="…"/>
 */
public enum ChartTrendLineType {
    /**
     * 线性趋势线
     */
    LINEAR("linear"),
    
    /**
     * 对数趋势线
     */
    LOGARITHMIC("log"),
    
    /**
     * 多项式趋势线
     */
    POLYNOMIAL("poly"),
    
    /**
     * 幂趋势线
     */
    POWER("power"),
    
    /**
     * 指数趋势线
     */
    EXPONENTIAL("exp"),
    
    /**
     * 移动平均趋势线
     */
    MOVING_AVG("movingAvg");

    private final String value;

    ChartTrendLineType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * XML 值 → 枚举
     */
    @StaticFactoryMethod
    public static ChartTrendLineType fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartTrendLineType type : values()) {
            if (type.value.equals(v)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Illegal trendline type value: " + v);
    }
}