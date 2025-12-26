package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 图表数据源类型枚举
 * 对应 Excel POI 中的 ChartDataSource，支持多种数据来源
 */
public enum ChartDataSourceType {
    /**
     * 单元格引用数据源
     * 对应 Excel 中的单元格范围引用，如 "Sheet1!A1:A10"
     */
    CELL_REFERENCE("cellReference"),
    
    /**
     * 静态内联数据
     * 对应 POI 中直接设置数据值的方式
     */
    STATIC_DATA("staticData"),
    
    /**
     * REST API 数据源
     * 对应外部 API 数据获取
     */
    API("api"),
    
    /**
     * 动态表达式数据源
     * 对应计算字段和动态数据
     */
    DATA_EXPR("dataExpr");

    private final String value;

    ChartDataSourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * 根据字符串值获取对应的枚举
     */
    @StaticFactoryMethod
    public static ChartDataSourceType fromValue(String value) {
        if (StringHelper.isEmpty(value)) {
            return null;
        }

        for (ChartDataSourceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown chart data source type: " + value);
    }
}