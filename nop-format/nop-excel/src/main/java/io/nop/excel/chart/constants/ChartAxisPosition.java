package io.nop.excel.chart.constants;

/**
 * Chart axis position enumeration
 */
public enum ChartAxisPosition {
    BOTTOM("bottom"),
    TOP("top"),
    LEFT("left"),
    RIGHT("right");

    private final String value;

    ChartAxisPosition(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartAxisPosition fromValue(String value) {
        for (ChartAxisPosition position : values()) {
            if (position.value.equals(value)) {
                return position;
            }
        }
        throw new IllegalArgumentException("Unknown axis position: " + value);
    }
}