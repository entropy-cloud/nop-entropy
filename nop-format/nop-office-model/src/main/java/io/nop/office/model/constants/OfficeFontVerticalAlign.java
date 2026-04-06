package io.nop.office.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

public enum OfficeFontVerticalAlign {
    superscript((short) 1),
    subscript((short) 2);

    private final short code;

    OfficeFontVerticalAlign(short code) {
        this.code = code;
    }

    public short getCode() {
        return code;
    }

    public static OfficeFontVerticalAlign fromCode(int code) {
        if (code == subscript.code)
            return subscript;
        if (code == superscript.code)
            return superscript;
        return null;
    }

    @StaticFactoryMethod
    public static OfficeFontVerticalAlign fromText(String text) {
        if (StringHelper.isEmpty(text) || "none".equals(text))
            return null;

        if (subscript.name().equals(text) || "2".equals(text))
            return subscript;
        if (superscript.name().equals(text) || "1".equals(text))
            return superscript;
        return null;
    }
}
