package io.nop.office.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

public enum OfficeFontFamily {
    ROMAN(1, "roman", "serif"),
    SWISS(2, "swiss", "sans-serif"),
    MODERN(3, "modern", "monospace"),
    SCRIPT(4, "script", "cursive"),
    DECORATIVE(5, "decorative", "fantasy");

    private final int family;
    private final String text;
    private final String cssFontFamily;

    OfficeFontFamily(int value, String officeText, String cssFontFamily) {
        this.family = value;
        this.text = officeText;
        this.cssFontFamily = cssFontFamily;
    }

    public int getValue() {
        return family;
    }

    public String getText() {
        return text;
    }

    public String getCssFontFamily() {
        return cssFontFamily;
    }

    @Override
    public String toString() {
        return text;
    }

    private static final Map<String, OfficeFontFamily> MAP = new HashMap<>();

    static {
        for (OfficeFontFamily family : values()) {
            MAP.put(family.getText(), family);
        }
    }

    @StaticFactoryMethod
    public static OfficeFontFamily fromText(String text) {
        return MAP.get(text);
    }

    public static OfficeFontFamily fromCode(int family) {
        if (family < 1 || family > 5)
            return null;
        return values()[family - 1];
    }
}
