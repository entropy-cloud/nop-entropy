package io.nop.excel.chart.constants;

/**
 * Chart axis type enumeration
 */
public enum ChartAxisType {
    CATEGORY("category"),
    VALUE("value"),
    TIME("time"),
    LOG("log");

    private final String value;

    ChartAxisType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartAxisType fromValue(String value) {
        for (ChartAxisType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown axis type: " + value);
    }
}