package io.nop.report.pdf.renderer;

import io.nop.commons.bytes.ByteString;
import io.nop.excel.model.ExcelFont;
import io.nop.report.pdf.font.FontManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class PdfRenderer {
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
            return FontManager.instance().getDefaultFont();
        return getFont(font.getFontName(), font.isBold(), font.isItalic());
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
