package io.nop.excel.chart.constants;

/**
 * Top N filter criteria enumeration
 * Based on OOXML standard for top N filtering options
 */
public enum ChartTopNBy {
    /**
     * Filter by value (absolute values)
     */
    VALUE("value"),
    
    /**
     * Filter by percentage
     */
    PERCENT("percent");

    private final String value;

    ChartTopNBy(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartTopNBy fromValue(String value) {
        for (ChartTopNBy by : values()) {
            if (by.value.equals(value)) {
                return by;
            }
        }
        throw new IllegalArgumentException("Unknown top N by: " + value);
    }
}