package io.nop.office.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

public enum OfficeFontUnderline implements IOfficeEnumValue {
    SINGLE(1, "single"),
    DOUBLE(2, "double"),
    SINGLE_ACCOUNTING(3, "singleAccounting"),
    DOUBLE_ACCOUNTING(4, "doubleAccounting"),
    NONE(5, "");

    private static final byte U_NONE = 0;
    private static final byte U_SINGLE = 1;
    private static final byte U_DOUBLE = 2;
    private static final byte U_SINGLE_ACCOUNTING = 0x21;
    private static final byte U_DOUBLE_ACCOUNTING = 0x22;

    private final int value;
    private final String excelText;
    private final String cssText;
    private final String wmlText;

    OfficeFontUnderline(int value, String text) {
        this.value = value;
        this.excelText = text;
        this.cssText = text.toLowerCase();
        this.wmlText = text.toLowerCase();
    }

    @Override
    public String toString() {
        return excelText;
    }

    @Override
    public String getCssText() {
        return cssText;
    }

    @Override
    public String getExcelText() {
        return excelText;
    }

    @Override
    public String getWmlText() {
        return wmlText;
    }

    public int getValue() {
        return value;
    }

    public byte getByteValue() {
        switch (this) {
            case DOUBLE:
                return U_DOUBLE;
            case DOUBLE_ACCOUNTING:
                return U_DOUBLE_ACCOUNTING;
            case SINGLE_ACCOUNTING:
                return U_SINGLE_ACCOUNTING;
            case NONE:
                return U_NONE;
            case SINGLE:
            default:
                return U_SINGLE;
        }
    }

    private static final OfficeFontUnderline[] TABLE = new OfficeFontUnderline[6];

    static {
        for (OfficeFontUnderline item : values()) {
            TABLE[item.getValue()] = item;
        }
    }

    public static OfficeFontUnderline fromValue(int value) {
        return TABLE[value];
    }

    public static OfficeFontUnderline fromByteValue(byte value) {
        switch (value) {
            case U_DOUBLE:
                return DOUBLE;
            case U_DOUBLE_ACCOUNTING:
                return DOUBLE_ACCOUNTING;
            case U_SINGLE_ACCOUNTING:
                return SINGLE_ACCOUNTING;
            case U_SINGLE:
                return SINGLE;
            default:
                return NONE;
        }
    }

    private static final OfficeEnumMap<OfficeFontUnderline> MAP = new OfficeEnumMap<>(values());

    static {
        MAP.addExcelText("none", NONE);
    }

    @StaticFactoryMethod
    public static OfficeFontUnderline fromExcelText(String text) {
        return MAP.fromExcelText(text);
    }

    public static OfficeFontUnderline fromCssText(String text) {
        return MAP.fromCssText(text);
    }

    public static OfficeFontUnderline fromWmlText(String text) {
        return MAP.fromWmlText(text);
    }
}
