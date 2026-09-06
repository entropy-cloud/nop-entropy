package io.nop.report.pdf.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestPdfStyleHelper {

    @Test
    public void testSanitizeTextForFont() {
        PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        // Helvetica只支持WinAnsi编码，中文字符应被替换为?
        assertEquals("??abc", PdfStyleHelper.sanitizeTextForFont("中文abc", helvetica));
        assertEquals("abc123", PdfStyleHelper.sanitizeTextForFont("abc123", helvetica));
        assertNull(PdfStyleHelper.sanitizeTextForFont(null, helvetica));
    }

    /**
     * 字体回退Helvetica时绘制中文不应抛异常导致整个导出失败
     */
    @Test
    public void testDrawUnwrappedTextWithNonWinAnsiChars() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                // 之前这里会抛IllegalArgumentException: U+XXXX is not available in the font's encoding
                PdfStyleHelper.drawUnwrappedText(cs, "中文abc", helvetica, 10f,
                        new PDRectangle(50, 700, 200, 30), null);
            }
        }
    }

    @Test
    public void testDrawWrappedTextWithNonWinAnsiChars() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PdfStyleHelper.drawWrappedText(cs, "中文abc def", helvetica, 10f,
                        new PDRectangle(50, 700, 100, 60), null);
            }
        }
    }
}
