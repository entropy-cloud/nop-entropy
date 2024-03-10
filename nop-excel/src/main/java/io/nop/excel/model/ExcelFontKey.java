/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.excel.model.constants.ExcelFontFamily;

@DataBean
public class ExcelFontKey {
    private final String fontName;
    private final int fontSize;
    private final String fontFamily;

    public ExcelFontKey(String fontName, Short fontSize, String fontFamily) {
        this.fontName = fontName;
        this.fontSize = fontSize == null ? 11 : fontSize;
        this.fontFamily = fontFamily == null ? ExcelFontFamily.SWISS.name() : fontFamily;
    }

    public int hashCode() {
        int h = fontName.hashCode();
        h = h * 31 + fontSize;
        h = h * 31 + fontFamily.length();
        return h;
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof ExcelFontKey))
            return false;

        ExcelFontKey other = (ExcelFontKey) o;
        return getFontName().equals(other.getFontName())
                && fontSize == other.getFontSize()
                && getFontFamily().equals(other.getFontFamily());
    }

    public String getFontName() {
        return fontName;
    }

    public int getFontSize() {
        return fontSize;
    }

    public String getFontFamily() {
        return fontFamily;
    }
}
