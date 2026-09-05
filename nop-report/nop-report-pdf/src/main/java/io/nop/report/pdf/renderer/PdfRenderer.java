package io.nop.report.pdf.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.excel.model.ExcelFont;
import io.nop.report.pdf.ReportPdfErrors;
import io.nop.report.pdf.font.FontManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import static io.nop.report.pdf.ReportPdfErrors.ARG_FONT_NAME;
import static io.nop.report.pdf.ReportPdfErrors.ARG_TEXT_SAMPLE;

public class PdfRenderer {
    static final Logger LOG = LoggerFactory.getLogger(PdfRenderer.class);

    static final String KEY_DEFAULT_FONT = "@default";
    static final String KEY_FALLBACK_FONT = "@fallback";

    private final PDDocument document;
    private final Map<String, PDFont> fontCache = new HashMap<>();
    private final Map<ByteString, PDImageXObject> imageCache = new HashMap<>();

    public PdfRenderer(PDDocument document) {
        this.document = document;
    }

    public PDDocument getDocument() {
        return document;
    }

    public PDFont getFont(ExcelFont font) {
        if (font == null)
            return getDefaultFont();
        return getFont(font.getFontName(), font.isBold(), font.isItalic());
    }

    public PDFont getFont(String fontName, boolean bold, boolean italic) {
        if (fontName == null)
            return getDefaultFont();

        String fontKey = FontManager.instance().getFontFullName(fontName, bold, italic);
        PDFont pdfFont = fontCache.get(fontKey);
        if (pdfFont != null)
            return pdfFont;

        pdfFont = FontManager.instance().getFont(fontName, bold, italic, document);
        fontCache.put(fontKey, pdfFont);
        return pdfFont;
    }

    /**
     * 默认字体：VFS提供/fonts/default.ttf时优先加载（通常为CJK字体），
     * 否则回退base-14的Helvetica
     */
    public PDFont getDefaultFont() {
        PDFont font = fontCache.get(KEY_DEFAULT_FONT);
        if (font == null) {
            font = FontManager.instance().loadDefaultFont(document);
            fontCache.put(KEY_DEFAULT_FONT, font);
        }
        return font;
    }

    /**
     * 字形回退：当前字体无法编码文本中的字符（如base-14字体遇到中文）时
     * 切换到CJK回退字体；所有字体都无法编码时抛出带配置指引的错误
     */
    public PDFont fontForText(String text, PDFont font) {
        if (FontManager.instance().canEncode(font, text))
            return font;

        PDFont fallback = getFallbackFont();
        if (fallback != null && FontManager.instance().canEncode(fallback, text)) {
            LOG.debug("nop.pdf.font-glyph-fallback:font={},fallback={}",
                    font == null ? null : font.getName(), fallback.getName());
            return fallback;
        }

        throw new NopException(ReportPdfErrors.ERR_PDF_FONT_MISSING_GLYPH)
                .param(ARG_FONT_NAME, font == null ? null : font.getName())
                .param(ARG_TEXT_SAMPLE, StringHelper.limitLen(text, 20));
    }

    /**
     * CJK回退字体（解析结果按文档缓存）
     */
    public PDFont getFallbackFont() {
        PDFont font = fontCache.get(KEY_FALLBACK_FONT);
        if (font == null) {
            font = FontManager.instance().loadFallbackFont(document);
            if (font != null)
                fontCache.put(KEY_FALLBACK_FONT, font);
        }
        return font;
    }

    public PdfPageRenderer addPage(PDRectangle pageSize) throws IOException {
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        return new PdfPageRenderer(document, page);
    }

    public void saveToStream(OutputStream outputStream) throws IOException {
        document.save(outputStream);
    }

    public PDImageXObject getImage(ByteString data) throws IOException {
        PDImageXObject image = imageCache.get(data);
        if (image != null)
            return image;

        image = PDImageXObject.createFromByteArray(document, data.toByteArray(), null);
        imageCache.put(data, image);
        return image;
    }
}
