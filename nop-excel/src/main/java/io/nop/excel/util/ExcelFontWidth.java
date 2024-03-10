/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.util;

import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelFontKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 难以准确计算缺省列宽，按照已知字体和大小查表
 */
public class ExcelFontWidth {
    private static final Map<ExcelFontKey, Double> fontWidths = new ConcurrentHashMap<>();

    public static void register(ExcelFont font, double width) {
        fontWidths.put(getFontKey(font), width);
    }

    public static double getWidth(ExcelFont font) {
        Double width = fontWidths.get(getFontKey(font));
        return width == null ? UnitsHelper.DEFAULT_CHARACTER_WIDTH_IN_PT : width;
    }

    static ExcelFontKey getFontKey(ExcelFont font) {
        ExcelFontKey key = new ExcelFontKey(font.getFontName(), font.getFontSize(), font.getFontFamily());
        return key;
    }
}
