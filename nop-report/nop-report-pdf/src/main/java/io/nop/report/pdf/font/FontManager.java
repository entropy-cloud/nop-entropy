/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf.font;

import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.BaseFont;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.color.ColorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FontManager {
    static final Logger LOG = LoggerFactory.getLogger(FontManager.class);

    static final FontManager _instance = new FontManager();

    private Set<String> systemFonts = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private BaseFont defaultFont;

    public static FontManager instance() {
        return _instance;
    }

    public void registerSystemFonts(Set<String> fontNames) {
        systemFonts.addAll(fontNames);
    }

    public FontManager() {
        try {
            defaultFont = BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", false);
        } catch (Exception e) {
            try {
                defaultFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
            } catch (Exception e2) {
                LOG.error("nop.pdf.create-base-font-fail", e2);
            }
        }
    }

    public Font getDefaultFont() {
        return new Font(defaultFont);
    }

    public Font getFont(ExcelFont font) {
        boolean embedded = !systemFonts.contains(font.getFontName());
        float fontSize = font.getFontSize() == null ? 14 : font.getFontSize();
        int fontStyle = Font.NORMAL;
        if (font.isBold()) {
            fontStyle |= Font.BOLD;
        }
        if (font.isItalic()) {
            fontStyle |= Font.ITALIC;
        }
        if (font.isStrikeout()) {
            fontStyle |= Font.STRIKETHRU;
        }
        if (font.getUnderlineStyle() != null) {
            fontStyle |= Font.UNDEFINED;
        }

        Color color = Color.BLACK;
        if (font.getFontColor() != null) {
            int colorInt = ColorHelper.toArgbInt(font.getFontColor());
            color = new Color(colorInt, true);
        }
        return FontFactory.getFont(font.getFontName(), BaseFont.CP1252, embedded, fontSize, fontStyle, color, true);
    }
}
