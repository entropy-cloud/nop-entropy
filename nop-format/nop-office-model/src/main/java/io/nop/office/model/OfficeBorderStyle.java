package io.nop.office.model;

import io.nop.office.model._gen._OfficeBorderStyle;

import java.util.Objects;

public class OfficeBorderStyle extends _OfficeBorderStyle {
    public OfficeBorderStyle() {
    }

    public static boolean isSameStyle(OfficeBorderStyle styleA, OfficeBorderStyle styleB) {
        if (styleA == styleB)
            return true;

        if (styleA == null || styleB == null)
            return false;

        if (styleA.getWeight() != styleB.getWeight())
            return false;

        if (styleA.getType() != styleB.getType())
            return false;

        return Objects.equals(styleA.getColor(), styleB.getColor());
    }
}
