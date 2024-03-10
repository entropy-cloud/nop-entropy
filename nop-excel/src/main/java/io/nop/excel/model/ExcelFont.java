/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

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

    public void toCssStyle(StringBuilder sb) {
        if (this.getFontName() != null) {
            // fontName对应css中的fontFamily
            sb.append("font-family:");
            sb.append(this.getFontName()).append(";\n");
        }
        if (this.isBold()) {
            sb.append("font-weight:bold;\n");
        }
        if (this.isItalic()) {
            sb.append("font-style:italic;\n");
        }
        if (this.getFontSize() != null && this.getFontSize() > 0) {
            sb.append("font-size:").append(this.getFontSize()).append("pt;\n");
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