package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

public enum ChartMarkerType {
    AUTO("auto"),           // OOXML: auto
    CIRCLE("circle"),       // OOXML: circle
    SQUARE("square"),       // OOXML: square  
    DIAMOND("diamond"),     // OOXML: diamond
    TRIANGLE("triangle"),   // OOXML: triangle
    X("x"),                 // OOXML: x
    STAR("star"),           // OOXML: star
    DASH("dash"),           // OOXML: dash
    DOT("dot"),             // OOXML: dot
    PLUS("plus"),           // OOXML: plus
    NONE("none");           // OOXML: none

    private final String value;

    ChartMarkerType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString() {
        return value;
    }

    @StaticFactoryMethod
    public static ChartMarkerType fromValue(String value) {
        if (StringHelper.isEmpty(value))
            return null;

        for (ChartMarkerType type : values()) {
            if (type.value.equals(value)) return type;
        }
        return AUTO;
    }
}