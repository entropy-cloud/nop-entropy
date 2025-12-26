package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

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

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @StaticFactoryMethod
    public static ChartOrientation fromValue(String value) {
        if (StringHelper.isEmpty(value))
            return null;

        for (ChartOrientation orientation : values()) {
            if (orientation.value.equals(value)) {
                return orientation;
            }
        }
        throw new IllegalArgumentException("Unknown orientation: " + value);
    }
}