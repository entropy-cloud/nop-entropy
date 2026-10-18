package io.nop.pdf.verify;

import io.nop.core.unittest.BaseTestCase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRegionProbe extends BaseTestCase {

    private static final int TOLERANCE = 8;
    private static final Color WHITE = Color.WHITE;

    private static BufferedImage renderPage1() throws Exception {
        try (PDDocument doc = VerifyFixtures.newTwoPageDoc()) {
            return PdfImageRenderer.renderPage(doc, 0, PdfImageRenderer.DEFAULT_DPI);
        }
    }

    @Test
    public void testAverageColorOfFilledRect() throws Exception {
        BufferedImage img = renderPage1();
        // interior of the red rect (pdf 100,600 - 300,700)
        Color red = RegionProbe.averageColor(img, 200, 250, 100, 50);
        assertTrue(red.getRed() > 240, "red channel: " + red);
        assertTrue(red.getGreen() < 16, "green channel: " + red);
        assertTrue(red.getBlue() < 16, "blue channel: " + red);

        // background far from all shapes
        Color bg = RegionProbe.averageColor(img, 20, 20, 40, 40);
        assertEquals(WHITE, bg);
    }

    @Test
    public void testAverageColorPage2() throws Exception {
        try (PDDocument doc = VerifyFixtures.newTwoPageDoc()) {
            BufferedImage img = PdfImageRenderer.renderPage(doc, 1, PdfImageRenderer.DEFAULT_DPI);
            Color blue = RegionProbe.averageColor(img, 300, 600, 50, 50);
            assertTrue(blue.getBlue() > 240, "blue channel: " + blue);
            assertTrue(blue.getRed() < 16, "red channel: " + blue);
        }
    }

    @Test
    public void testHorizontalStrokeWidth() throws Exception {
        BufferedImage img = renderPage1();
        // 2pt horizontal line at pdf y=400 -> image y ~675; expected width 2*110/72 = 3.06 px
        int width = RegionProbe.measureHorizontalStrokeWidth(img, 300, 660, 690, WHITE, TOLERANCE);
        assertTrue(width >= 2 && width <= 5, "measured stroke width " + width + ", expected ~3");
    }

    @Test
    public void testVerticalStrokeWidth() throws Exception {
        BufferedImage img = renderPage1();
        // 2pt vertical line at pdf x=500 -> image x ~764
        int width = RegionProbe.measureVerticalStrokeWidth(img, 675, 750, 780, WHITE, TOLERANCE);
        assertTrue(width >= 2 && width <= 5, "measured stroke width " + width + ", expected ~3");
    }

    @Test
    public void testStrokeProbeMissingLineReturnsZero() throws Exception {
        BufferedImage img = renderPage1();
        // scan far away from any stroke
        int width = RegionProbe.measureHorizontalStrokeWidth(img, 300, 30, 60, WHITE, TOLERANCE);
        assertEquals(0, width);
    }
}
