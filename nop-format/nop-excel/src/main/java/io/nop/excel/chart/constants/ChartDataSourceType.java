package io.nop.excel.chart.constants;

/**
 * Chart data source type enumeration
 */
public enum ChartDataSourceType {
    STATIC("static"),
    QUERY("query"),
    API("api"),
    EXCEL("excel"),
    EXPRESSION("expression");

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

    public static ChartDataSourceType fromValue(String value) {
        for (ChartDataSourceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown data source type: " + value);
    }
}