package io.nop.office.model;

import io.nop.commons.util.StringHelper;
import io.nop.office.model._gen._OfficeFont;
import io.nop.office.model.color.OfficeColorHelper;
import io.nop.office.model.constants.OfficeFontFamily;
import io.nop.office.model.constants.OfficeFontUnderline;

public class OfficeFont extends _OfficeFont {
    public static final String DEFAULT_FONT_NAME = "Calibri";
    public static final float DEFAULT_FONT_SIZE = 11f;
    public static final String DEFAULT_FONT_COLOR = "0x0";

    public static final OfficeFont DEFAULT_FONT = createDefaultFont();

    public OfficeFont() {
    }

    private static OfficeFont createDefaultFont() {
        OfficeFont font = new OfficeFont();
        font.setFontSize(DEFAULT_FONT_SIZE);
        font.setFontColor(DEFAULT_FONT_COLOR);
        font.setFontName(DEFAULT_FONT_NAME);
        font.setFontFamily(OfficeFontFamily.SWISS.getText());
        font.freeze(true);
        return font;
    }

    public String getCssFontFamily() {
        if (getFontFamily() == null)
            return null;
        OfficeFontFamily family = OfficeFontFamily.fromText(getFontFamily());
        if (family == null)
            return null;
        return family.getCssFontFamily();
    }

    public String getFontSizeString() {
        Float size = getFontSize();
        if (size == null)
            return null;
        String str = size.toString();
        if (str.endsWith(".0"))
            return str.substring(0, str.length() - 2);
        return str;
    }

    public void toCssStyle(StringBuilder sb) {
        if (getFontName() != null || getFontFamily() != null) {
            sb.append("font-family:");
            if (getFontName() != null) {
                sb.append(StringHelper.quote(getFontName()));
            } else {
                sb.append("Arial");
            }
            String cssFontFamily = getCssFontFamily();
            if (cssFontFamily != null)
                sb.append(',').append(cssFontFamily);
            sb.append(";\n");
        }
        if (isBold()) {
            sb.append("font-weight:bold;\n");
        }
        if (isItalic()) {
            sb.append("font-style:italic;\n");
        }
        if (getFontSize() != null && getFontSize() > 0) {
            sb.append("font-size:").append(getFontSizeString()).append("pt;\n");
        }
        if (getFontColor() != null) {
            sb.append("color:").append(OfficeColorHelper.toCssColor(getFontColor())).append(";\n");
        }
        OfficeFontUnderline underlineStyle = getUnderlineStyle();
        if (underlineStyle != null) {
            switch (underlineStyle) {
                case NONE:
                    break;
                case SINGLE_ACCOUNTING:
                    sb.append("text-decoration:underline;\n");
                    sb.append("text-underline-style:single-accounting;\n");
                    break;
                case SINGLE:
                default:
                    sb.append("text-decoration:underline;\n");
                    break;
            }
        }
    }
}
