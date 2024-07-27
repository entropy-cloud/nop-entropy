/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.commons.util.StringHelper;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model._gen._ExcelFont;
import io.nop.excel.model.color.ColorHelper;
import io.nop.excel.model.constants.ExcelFontFamily;

public class ExcelFont extends _ExcelFont {
    public static ExcelFont DEFAULT_FONT = createDefaultFont();

    public ExcelFont() {

    }

    private static ExcelFont createDefaultFont() {
        ExcelFont font = new ExcelFont();
        font.setFontSize(ExcelConstants.DEFAULT_FONT_SIZE);
        font.setFontColor(ExcelConstants.DEFAULT_FONT_COLOR);// setTheme
        font.setFontName(ExcelConstants.DEFAULT_FONT_NAME);
        font.setFontFamily(ExcelFontFamily.SWISS.getText());
        // font.setFontScheme(FontScheme.MINOR);
        font.freeze(true);
        return font;
    }

    public String getCssFontFamily() {
        if (getFontFamily() == null)
            return null;
        ExcelFontFamily family = ExcelFontFamily.fromText(getFontFamily());
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
        if (this.getFontName() != null || this.getFontFamily() != null) {
            sb.append("font-family:");
            if (this.getFontName() != null) {
                sb.append(StringHelper.quote(this.getFontName()));
            } else {
                sb.append("Arial");
            }
            String cssFontFamily = getCssFontFamily();
            if (cssFontFamily != null)
                sb.append(',').append(cssFontFamily);
            sb.append(";\n");
        }
        if (this.isBold()) {
            sb.append("font-weight:bold;\n");
        }
        if (this.isItalic()) {
            sb.append("font-style:italic;\n");
        }
        if (this.getFontSize() != null && this.getFontSize() > 0) {
            sb.append("font-size:").append(this.getFontSizeString()).append("pt;\n");
        }

        if (this.getFontColor() != null) {
            sb.append("color:").append(ColorHelper.toCssColor(this.getFontColor())).append(";\n");
        }

        if (this.getUnderlineStyle() != null) {
            switch (this.getUnderlineStyle()) {
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