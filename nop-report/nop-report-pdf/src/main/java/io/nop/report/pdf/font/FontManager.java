/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf.font;

import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FontManager {
    static final Logger LOG = LoggerFactory.getLogger(FontManager.class);

    static final FontManager _instance = new FontManager();

    private final Map<String, PDFont> systemFonts = new CaseInsensitiveMap<>();
    private final Map<String, String> fontNameAliases = new HashMap<>();

    private PDFont defaultFont;
    private IResource defaultFontResource;
    private boolean inited;

    public static FontManager instance() {
        return _instance;
    }

    private void registerSystemFonts() {
        for (Standard14Fonts.FontName fontName : Standard14Fonts.FontName.values()) {
            systemFonts.put(fontName.getName(), new PDType1Font(fontName));
        }

        systemFonts.put("Times New Roman", new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN));
        systemFonts.put("Helvetica-BoldItalic", new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE));
        systemFonts.put("Courier-BoldItalic", new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE));
        systemFonts.put("Helvetica-Italic", new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
        systemFonts.put("Courier-Italic", new PDType1Font(Standard14Fonts.FontName.COURIER_OBLIQUE));
    }

    public FontManager() {

    }

    protected synchronized void init() {
        if (inited)
            return;

        try {
            IResource resource = getFontResource("default", false, false);
            if (resource != null && resource.exists()) {
                defaultFontResource = resource;
            }

            // Try to use Helvetica as default font in PDFBox
            defaultFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        } catch (Exception e) {
            LOG.error("nop.pdf.create-base-font-fail", e);
        }
        inited = true;
    }

    public PDFont getDefaultFont() {

        return defaultFont;
    }

    public void addFontAlias(String alias, String fontName) {
        fontNameAliases.put(alias, fontName);
    }

    public PDFont getFont(String fontName, boolean bold, boolean italic, PDDocument doc) {
        init();

        // 1. 获取基础字体名称（处理可能为null的情况）
        if (fontName == null)
            fontName = "Helvetica";

        PDFont font = getSystemFont(fontName, bold, italic);
        if (font != null) {
            return font;
        }

        // 4. 尝试加载字体
        font = loadFont(fontName, bold, italic, doc);
        if (font != null) {
            return font;
        }
        // 6. 回退到默认字体
        LOG.warn("nop.pdf.font-not-found:fontName={}, using default font", fontName);
        return getDefaultFont();
    }

    public PDFont getSystemFont(String fontName, boolean bold, boolean italic) {
        String fullName = getFontFullName(fontName, bold, italic);
        PDFont font = systemFonts.get(fullName);
        if (font == null)
            font = systemFonts.get(fontName);
        return font;
    }

    public String getFontFullName(String fontName, boolean bold, boolean italic) {
        String fullName = fontName.replace(' ', '-');
        if (bold && italic) {
            fullName += "-BoldItalic";
        } else if (bold) {
            fullName += "-Bold";
        } else if (italic) {
            fullName += "-Italic";
        }
        return fullName;
    }

    private PDFont loadFont(String fontName, boolean bold, boolean italic, PDDocument doc) {

        IResource fontResource = getFontResource(fontName, bold, italic);
        if (fontResource == null)
            fontResource = defaultFontResource;

        if (fontResource == null) {
            return null;
        }

        InputStream is = fontResource.getInputStream();
        if (is != null) {
            try {
                return PDType0Font.load(doc, is);
            } catch (Exception e) {
                LOG.error("nop.pdf.load-font-fail:fontName={}, bold={}, italic={}", fontName, bold, italic, e);
                return null;
            } finally {
                IoHelper.safeClose(is);
            }
        }
        return null;
    }

    // 辅助方法
    protected IResource getFontResource(String fontName, boolean bold, boolean italic) {
        String fullName = getFontFullName(fontName, bold, italic);
        IResource resource = VirtualFileSystem.instance().getResource("/fonts/" + fullName + ".ttf");
        if (!resource.exists()) {
            resource = VirtualFileSystem.instance().getResource("/fonts/" + fontName + ".ttf");
            if (!resource.exists()) {
                resource = VirtualFileSystem.instance().getResource("/fonts/" + fullName + ".otf");
                if (!resource.exists()) {
                    resource = VirtualFileSystem.instance().getResource("/fonts/" + fontName + ".otf");
                    if (!resource.exists())
                        return null;
                }
            }
        }
        return resource;
    }
}