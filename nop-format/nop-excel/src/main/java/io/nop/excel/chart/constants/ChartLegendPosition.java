package io.nop.excel.chart.constants;

/**
 * Chart legend position enumeration
 */
public enum ChartLegendPosition {
    TOP("top"),
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right"),
    TOP_LEFT("top-left"),
    TOP_RIGHT("top-right"),
    BOTTOM_LEFT("bottom-left"),
    BOTTOM_RIGHT("bottom-right");

    private final String value;

    ChartLegendPosition(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartLegendPosition fromValue(String value) {
        for (ChartLegendPosition position : values()) {
            if (position.value.equals(value)) {
                return position;
            }
        }
        throw new IllegalArgumentException("Unknown legend position: " + value);
    }
}