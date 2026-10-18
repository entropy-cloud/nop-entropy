package io.nop.pdf.verify;

import io.nop.core.unittest.BaseTestCase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPdfImageRenderer extends BaseTestCase {

    @Test
    public void testRenderAllPages() throws Exception {
        try (PDDocument doc = VerifyFixtures.newTwoPageDoc()) {
            List<BufferedImage> images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            assertEquals(2, images.size());
            for (BufferedImage img : images) {
                assertNotNull(img);
                // A4 (595.3 x 841.9 pt) at 110 DPI is about 909 x 1287 px
                assertTrue(img.getWidth() >= 900 && img.getWidth() <= 920, "unexpected width " + img.getWidth());
                assertTrue(img.getHeight() >= 1280 && img.getHeight() <= 1300, "unexpected height " + img.getHeight());
            }
        }
    }

    @Test
    public void testRenderSinglePage() throws Exception {
        try (PDDocument doc = VerifyFixtures.newTwoPageDoc()) {
            BufferedImage page2 = PdfImageRenderer.renderPage(doc, 1, PdfImageRenderer.DEFAULT_DPI);
            assertNotNull(page2);
        }
    }

    @Test
    public void testRenderPageOutOfRangeFailsExplicitly() throws Exception {
        try (PDDocument doc = VerifyFixtures.newTwoPageDoc()) {
            assertThrows(IllegalArgumentException.class,
                    () -> PdfImageRenderer.renderPage(doc, 5, PdfImageRenderer.DEFAULT_DPI));
        }
    }
}
