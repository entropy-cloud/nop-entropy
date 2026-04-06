package io.nop.office.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

public enum OfficeLineStyle implements IOfficeEnumValue {
    NONE(0x0, "none", "none", "none"),
    SINGLE(0x1, "thin", "solid", "single"),
    MEDIUM(0x2, "medium", "solid", "medium"),
    DASHED(0x3, "dashed", "dashed", "dashed"),
    DOTTED(0x4, "dotted", "dotted", "dotted"),
    THICK(0x5, "thick", "solid", "thick"),
    DOUBLE(0x6, "double", "double", "double"),
    HAIR(0x7, "hair", "dotted", "dotted"),
    DASH_DOT(0x9, "dashDot", "dashed", "dotDash"),
    MEDIUM_DASH_DOT(0xA, "mediumDashDot", "dashed dotted", "mediumdashdot"),
    DASH_DOT_DOT(0xB, "dashDotDot", "dashed", "dotDotDash"),
    MEDIUM_DASH_DOT_DOT(0xC, "mediumDashDotDot", "dashed dotted", "mediumdashdotdot"),
    SLANTED_DASH_DOT(0xD, "slantDashDot", "dashed", "dotDash");

    private final short code;
    private final String cssText;
    private final String excelText;
    private final String wmlText;

    OfficeLineStyle(int code, String excelText, String cssText, String wmlText) {
        this.code = (short) code;
        this.cssText = cssText;
        this.excelText = excelText;
        this.wmlText = wmlText;
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

    public short getCode() {
        return code;
    }

    private static final OfficeLineStyle[] TABLE = new OfficeLineStyle[0xD + 1];
    private static final short[] WEIGHT = new short[0xD + 1];

    static {
        for (OfficeLineStyle c : values()) {
            TABLE[c.getCode()] = c;
            WEIGHT[c.getCode()] = 1;
        }

        TABLE[0x2] = SINGLE;
        WEIGHT[0x2] = 2;
        TABLE[0x5] = SINGLE;
        WEIGHT[0x5] = 3;
        TABLE[0x8] = DASHED;
        WEIGHT[0x8] = 2;
        TABLE[0xA] = DASH_DOT;
        WEIGHT[0xA] = 2;
        TABLE[0xC] = DASH_DOT_DOT;
        WEIGHT[0xC] = 2;
    }

    public int getWeight() {
        return WEIGHT[getCode()];
    }

    @Override
    public String toString() {
        return excelText;
    }

    public static OfficeLineStyle fromCode(short code) {
        return TABLE[code];
    }

    public static int getWeightFromCode(short code) {
        return WEIGHT[code];
    }

    private static final OfficeEnumMap<OfficeLineStyle> MAP = new OfficeEnumMap<>(values());

    @StaticFactoryMethod
    public static OfficeLineStyle fromExcelText(String text) {
        if (StringHelper.isEmpty(text))
            return NONE;
        return MAP.fromExcelText(text);
    }

    public static OfficeLineStyle fromCssText(String text) {
        return MAP.fromCssText(text);
    }

    public static OfficeLineStyle fromWmlText(String text) {
        return MAP.fromWmlText(text);
    }
}
