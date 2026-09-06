package io.nop.report.pdf.font;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFontManager extends JunitBaseTestCase {

    @Test
    public void testStandard14FontsRegistered() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = FontManager.instance().getFont("Helvetica", false, false, doc);
            assertEquals("Helvetica", font.getName());
        }
    }

    @Test
    public void testBoldVariantResolvedFromSystemFonts() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            // 加粗变体应命中Standard14字体表，而不是回退到默认Helvetica
            PDFont font = FontManager.instance().getFont("Helvetica", true, false, doc);
            assertEquals("Helvetica-Bold", font.getName());

            PDFont times = FontManager.instance().getFont("Times New Roman", false, false, doc);
            assertEquals("Times-Roman", times.getName());
        }
    }

    @Test
    public void testFontAlias() throws Exception {
        FontManager.instance().addFontAlias("my-mono", "Courier");
        try (PDDocument doc = new PDDocument()) {
            PDFont font = FontManager.instance().getFont("my-mono", false, false, doc);
            assertEquals("Courier", font.getName());
        }
    }
}
