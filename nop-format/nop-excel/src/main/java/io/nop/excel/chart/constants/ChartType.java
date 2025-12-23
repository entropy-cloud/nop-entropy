package io.nop.excel.chart.constants;

/**
 * Chart type enumeration
 */
public enum ChartType {
    LINE("line"),
    BAR("bar"),
    COLUMN("column"),
    PIE("pie"),
    DOUGHNUT("doughnut"),
    SCATTER("scatter"),
    BUBBLE("bubble"),
    AREA("area"),
    RADAR("radar"),
    HEATMAP("heatmap"),
    COMBO("combo");

    private final String value;

    ChartType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartType fromValue(String value) {
        for (ChartType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown chart type: " + value);
    }
}