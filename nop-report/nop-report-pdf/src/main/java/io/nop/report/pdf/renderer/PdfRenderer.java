package io.nop.report.pdf.renderer;

import io.nop.report.pdf.font.FontManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class PdfRenderer {
    private final PDDocument document;
    private final Map<String, PDFont> fontCache = new HashMap<>();

    public PdfRenderer(PDDocument document) {
        this.document = document;
    }

    public PDDocument getDocument() {
        return document;
    }

    public PDFont getFont(String fontName, boolean bold, boolean italic) {
        if (fontName == null)
            return FontManager.instance().getDefaultFont();

        String fontKey = FontManager.instance().getFontFullName(fontName, bold, italic);
        PDFont pdfFont = fontCache.get(fontKey);
        if (pdfFont != null)
            return pdfFont;

        pdfFont = FontManager.instance().getFont(fontName, bold, italic, document);
        fontCache.put(fontKey, pdfFont);
        return pdfFont;
    }

    public PDPage addPage(PDRectangle pageSize) {
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        return page;
    }

    public PDPageContentStream newContentStream(PDPage page) throws IOException {
        return new PDPageContentStream(document, page);
    }

    public void saveToStream(OutputStream outputStream) throws IOException {
        document.save(outputStream);
    }
}
