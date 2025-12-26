package io.nop.excel.chart.constants;

import io.nop.commons.util.StringHelper;

/**
 * Chart title position enumeration
 */
public enum ChartTitlePosition {
    TOP("top"),
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right"),
    CENTER("center");

    private final String value;

    ChartTitlePosition(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartTitlePosition fromValue(String value) {
        if(StringHelper.isEmpty(value))
            return null;

        for (ChartTitlePosition position : values()) {
            if (position.value.equals(value)) {
                return position;
            }
        }
        throw new IllegalArgumentException("Unknown title position: " + value);
    }
}