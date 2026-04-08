package io.nop.office.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

public enum OfficeHorizontalAlignment implements IOfficeEnumValue {
    GENERAL("general"),
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
    FILL("fill"),
    JUSTIFY("justify"),
    CENTER_SELECTION("centerSelection", "center"),
    DISTRIBUTED("distributed");

    private final String excelText;
    private final String cssText;
    private final String wmlText;

    OfficeHorizontalAlignment(String text) {
        this.excelText = text;
        this.cssText = text.toLowerCase();
        this.wmlText = this.cssText;
    }

    OfficeHorizontalAlignment(String excelText, String cssText) {
        this.excelText = excelText;
        this.cssText = cssText;
        this.wmlText = cssText;
    }

    @Override
    public String toString() {
        return excelText;
    }

    @Override
    public String getExcelText() {
        return excelText;
    }

    @Override
    public String getCssText() {
        return cssText;
    }

    @Override
    public String getWmlText() {
        return wmlText;
    }

    public short getCode() {
        return (short) ordinal();
    }

    public static OfficeHorizontalAlignment forInt(int code) {
        if (code < 0 || code >= values().length)
            return null;
        return values()[code];
    }

    private static final OfficeEnumMap<OfficeHorizontalAlignment> MAP = new OfficeEnumMap<>(values());

    @StaticFactoryMethod
    public static OfficeHorizontalAlignment fromExcelText(String text) {
        return MAP.fromExcelText(text);
    }

    public static OfficeHorizontalAlignment fromCssText(String text) {
        return MAP.fromCssText(text);
    }

    public static OfficeHorizontalAlignment fromWmlText(String text) {
        return MAP.fromWmlText(text);
    }
}
