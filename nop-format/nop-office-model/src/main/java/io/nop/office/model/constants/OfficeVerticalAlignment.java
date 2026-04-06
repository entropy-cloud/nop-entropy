package io.nop.office.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

public enum OfficeVerticalAlignment implements IOfficeEnumValue {
    TOP("top"),
    CENTER("center", "middle", "center"),
    BOTTOM("bottom"),
    JUSTIFY("justify"),
    DISTRIBUTED("distributed");

    private final String excelText;
    private final String cssText;
    private final String wmlText;

    OfficeVerticalAlignment(String text) {
        this.excelText = text;
        this.cssText = text.toLowerCase();
        this.wmlText = this.cssText;
    }

    OfficeVerticalAlignment(String excelText, String cssText, String wmlText) {
        this.excelText = excelText;
        this.cssText = cssText;
        this.wmlText = wmlText;
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

    public static OfficeVerticalAlignment forInt(int code) {
        if (code < 0 || code >= values().length)
            throw new IllegalArgumentException("Invalid VerticalAlignment code: " + code);
        return values()[code];
    }

    private static final OfficeEnumMap<OfficeVerticalAlignment> MAP = new OfficeEnumMap<>(values());

    @StaticFactoryMethod
    public static OfficeVerticalAlignment fromExcelText(String text) {
        return MAP.fromExcelText(text);
    }

    public static OfficeVerticalAlignment fromCssText(String text) {
        return MAP.fromCssText(text);
    }

    public static OfficeVerticalAlignment fromWmlText(String text) {
        return MAP.fromWmlText(text);
    }
}
