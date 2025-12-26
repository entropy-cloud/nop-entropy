package io.nop.excel.chart.constants;

/**
 * Funnel chart sorting enumeration
 * Based on OOXML standard for funnel chart sorting options
 */
public enum ChartFunnelSort {
    /**
     * Sort values in descending order (largest to smallest)
     */
    DESC("desc"),
    
    /**
     * Sort values in ascending order (smallest to largest)
     */
    ASC("asc"),
    
    /**
     * No sorting, use original data order
     */
    NONE("none");

    private final String value;

    ChartFunnelSort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartFunnelSort fromValue(String value) {
        for (ChartFunnelSort sort : values()) {
            if (sort.value.equals(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Unknown funnel sort: " + value);
    }
}