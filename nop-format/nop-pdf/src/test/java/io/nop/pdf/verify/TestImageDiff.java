package io.nop.pdf.verify;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestImageDiff {

    private static BufferedImage solid(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, rgb);
        return img;
    }

    @Test
    public void testIdenticalImages() {
        BufferedImage a = solid(32, 32, 0xFFFFFF);
        BufferedImage b = solid(32, 32, 0xFFFFFF);
        ImageDiffResult result = ImageDiff.compare(a, b);
        assertTrue(result.isIdentical());
        assertEquals(0, result.getDiffPixels());
        assertEquals(1.0, result.getSsim(), 1e-9);
        result.assertClose(ImageDiff.RECOMMENDED_MAX_DIFF_RATIO, ImageDiff.RECOMMENDED_MIN_SSIM);
    }

    @Test
    public void testPerturbedImageDetected() {
        BufferedImage a = solid(32, 32, 0xFFFFFF);
        BufferedImage b = solid(32, 32, 0xFFFFFF);
        // perturb a 4x4 block
        for (int y = 10; y < 14; y++)
            for (int x = 10; x < 14; x++)
                b.setRGB(x, y, 0x000000);
        ImageDiffResult result = ImageDiff.compare(a, b);
        assertEquals(16, result.getDiffPixels());
        assertFalse(result.isIdentical());
        assertEquals(255, result.getMaxChannelDelta());
        assertTrue(result.getSsim() < 1.0);
        AssertionError err = assertThrows(AssertionError.class,
                () -> result.assertClose(ImageDiff.RECOMMENDED_MAX_DIFF_RATIO, ImageDiff.RECOMMENDED_MIN_SSIM));
        assertTrue(err.getMessage().contains("diff exceeds tolerance"));
    }

    @Test
    public void testChannelToleranceAbsorbsSmallDeltas() {
        BufferedImage a = solid(16, 16, 0xFFFFFF);
        BufferedImage b = solid(16, 16, 0xFAFAFA); // delta 5 per channel
        assertTrue(ImageDiff.compare(a, b, 8).isIdentical());
        assertFalse(ImageDiff.compare(a, b, 4).isIdentical());
    }

    @Test
    public void testDimensionMismatchFailsExplicitly() {
        BufferedImage a = solid(32, 32, 0xFFFFFF);
        BufferedImage b = solid(32, 31, 0xFFFFFF);
        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
                () -> ImageDiff.compare(a, b));
        assertTrue(err.getMessage().contains("dimensions differ"));
    }

    @Test
    public void testSsimDistinguishesDifferentStructures() {
        // left half black, right half white vs left half black, right half gray:
        // same black region, different bright region
        BufferedImage a = new BufferedImage(64, 16, BufferedImage.TYPE_INT_RGB);
        BufferedImage b = new BufferedImage(64, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 64; x++) {
                a.setRGB(x, y, x < 32 ? 0x000000 : 0xFFFFFF);
                b.setRGB(x, y, x < 32 ? 0x000000 : 0xB4B4B4);
            }
        }
        ImageDiffResult result = ImageDiff.compare(a, b, 8);
        assertTrue(result.getDiffPixels() > 0);
        assertTrue(result.getSsim() < 1.0 && result.getSsim() > 0.0,
                "ssim should be in (0,1) for partially different images, got " + result.getSsim());
    }
}
