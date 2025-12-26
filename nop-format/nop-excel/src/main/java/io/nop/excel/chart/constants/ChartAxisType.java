package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 坐标轴类型枚举
 * 对应 OOXML <c:axId val="…"/> 和轴类型定义
 */
public enum ChartAxisType {
    /**
     * 分类轴（X轴）
     */
    CATEGORY("category"),

    /**
     * 数值轴（Y轴）
     */
    VALUE("value"),

    /**
     * 日期轴
     */
    DATE("time"),

    /**
     * 系列轴（3D图表）
     */
    SERIES("series"),

    /**
     * 对数轴
     */
    LOG("log");

    private final String value;

    ChartAxisType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * OOXML 值 → 枚举
     */
    @StaticFactoryMethod
    public static ChartAxisType fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartAxisType type : values()) {
            if (type.value.equals(v)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Illegal axis type OOXML value: " + v);
    }
}