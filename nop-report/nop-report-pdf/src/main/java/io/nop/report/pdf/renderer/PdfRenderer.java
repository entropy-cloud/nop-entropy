package io.nop.report.pdf.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.excel.model.ExcelFont;
import io.nop.report.pdf.ReportPdfConfigs;
import io.nop.report.pdf.ReportPdfErrors;
import io.nop.report.pdf.font.FontManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.report.pdf.ReportPdfErrors.ARG_FAILED_CHAR;
import static io.nop.report.pdf.ReportPdfErrors.ARG_FONT_NAME;
import static io.nop.report.pdf.ReportPdfErrors.ARG_TEXT_SAMPLE;

public class PdfRenderer {
    static final Logger LOG = LoggerFactory.getLogger(PdfRenderer.class);

    static final String KEY_DEFAULT_FONT = "@default";
    static final String KEY_FALLBACK_FONT = "@fallback";

    private final PDDocument document;
    private final Map<String, PDFont> fontCache = new HashMap<>();
    private final Map<ByteString, PDImageXObject> imageCache = new HashMap<>();
    private final List<PDPage> pages = new ArrayList<>();
    private final Set<Integer> pagesWithFooter = new HashSet<>();

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

        Integer failed = FontManager.instance().getLastFailedCodePoint();
        // 无回退字体时优先抛出"缺回退字体"（指引配置字体），仅在已有回退字体
        // 但该字符仍无法编码时才抛"缺字形"
        if (fallback == null && FontManager.instance().hasDefaultFontResource() == false
                && FontManager.instance().discoveredFontFiles().isEmpty()) {
            throw new NopException(ReportPdfErrors.ERR_PDF_FALLBACK_FONT_NOT_FOUND)
                    .param(ARG_TEXT_SAMPLE, StringHelper.limitLen(text, 20));
        }

        throw new NopException(ReportPdfErrors.ERR_PDF_FONT_MISSING_GLYPH)
                .param(ARG_FONT_NAME, font == null ? null : font.getName())
                .param(ARG_TEXT_SAMPLE, StringHelper.limitLen(text, 20))
                .param(ARG_FAILED_CHAR, failed == null ? "?"
                        : "U+" + Integer.toHexString(failed).toUpperCase());
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
        pages.add(page);
        return new PdfPageRenderer(document, page);
    }

    /**
     * 模板已显式配置页脚的页面不再叠加默认页码
     */
    public void markFooterDrawn() {
        if (!pages.isEmpty())
            pagesWithFooter.add(pages.size() - 1);
    }

    /**
     * 默认页码（F13）：多页文档且页面未配置页脚时，在页底居中绘制"第 x / y 页"。
     * 在所有内容渲染完成后、save之前调用
     */
    public void drawDefaultPageNumbers() throws IOException {
        if (!Boolean.TRUE.equals(ReportPdfConfigs.CFG_PDF_DEFAULT_PAGE_FOOTER.get()))
            return;

        int total = pages.size();
        if (total <= 1)
            return;

        for (int i = 0; i < total; i++) {
            if (pagesWithFooter.contains(i))
                continue;
            PDPage page = pages.get(i);
            // 无CJK回退字体的环境（纯ASCII部署）降级为英文页码，避免默认页码导致整份导出失败
            String text = "第 " + (i + 1) + " / " + total + " 页";
            PDFont font;
            try {
                font = fontForText(text, getDefaultFont());
            } catch (NopException e) {
                LOG.warn("nop.pdf.page-number-ascii-fallback:无CJK回退字体，页码降级为英文");
                text = "Page " + (i + 1) + " / " + total;
                font = fontForText(text, getDefaultFont());
            }
            float fontSize = 9f;
            float textWidth = font.getStringWidth(text) / 1000 * fontSize;

            try (PDPageContentStream cs = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setFont(font, fontSize);
                cs.beginText();
                cs.newLineAtOffset((page.getMediaBox().getWidth() - textWidth) / 2, 15f);
                cs.showText(text);
                cs.endText();
            }
        }
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
