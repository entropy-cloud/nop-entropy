package io.nop.excel.chart.constants;

/**
 * Chart orientation enumeration
 */
public enum ChartOrientation {
    HORIZONTAL("horizontal"),
    VERTICAL("vertical");

    private final String value;

    ChartOrientation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartOrientation fromValue(String value) {
        for (ChartOrientation orientation : values()) {
            if (orientation.value.equals(value)) {
                return orientation;
            }
        }
        throw new IllegalArgumentException("Unknown orientation: " + value);
    }
}