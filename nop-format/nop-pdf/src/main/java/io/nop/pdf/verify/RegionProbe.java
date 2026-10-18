package io.nop.pdf.verify;

import io.nop.api.core.util.Guard;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Structural region probes on rendered page images: average color of a rectangle and
 * stroke width estimation along a scan line.
 *
 * <p>Unlike full-image golden comparison, region probes are portable across environments
 * (fonts, anti-aliasing) and serve as the cross-machine acceptance gate (plan 2463).</p>
 */
public final class RegionProbe {

    private RegionProbe() {
    }

    /**
     * Average color over a rectangle region. Anti-aliased edges average out; probe the
     * interior of filled shapes for exact fill colors.
     *
     * @param img rendered page image
     * @param x   region left
     * @param y   region top
     * @param w   region width, must be positive
     * @param h   region height, must be positive
     * @return the average RGB color
     */
    public static Color averageColor(BufferedImage img, int x, int y, int w, int h) {
        Guard.notNull(img, "img");
        Guard.checkArgument(w > 0 && h > 0, "region must be positive", w, h);
        long sumR = 0, sumG = 0, sumB = 0;
        long count = (long) w * h;
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                int rgb = img.getRGB(xx, yy);
                sumR += (rgb >> 16) & 0xFF;
                sumG += (rgb >> 8) & 0xFF;
                sumB += rgb & 0xFF;
            }
        }
        return new Color((int) (sumR / count), (int) (sumG / count), (int) (sumB / count));
    }

    /**
     * Estimate the thickness in pixels of a horizontal stroke (a line along the x axis)
     * by counting, at column {@code x}, the pixels between {@code yFrom} (inclusive) and
     * {@code yTo} (exclusive) whose color differs from the background beyond the tolerance.
     * Expected physical width is {@code pt * dpi / 72} pixels.
     *
     * @param img        rendered page image
     * @param x          the probe column (must cross the stroke)
     * @param yFrom      scan start (inclusive)
     * @param yTo        scan end (exclusive)
     * @param background the surrounding background color
     * @param tolerance  per-channel delta below which a pixel counts as background
     * @return number of non-background pixels on the scan segment (0 when the segment misses the stroke)
     */
    public static int measureHorizontalStrokeWidth(BufferedImage img, int x, int yFrom, int yTo,
                                                   Color background, int tolerance) {
        Guard.notNull(img, "img");
        Guard.notNull(background, "background");
        Guard.checkArgument(yTo > yFrom, "empty scan range", yFrom, yTo);
        int count = 0;
        for (int y = yFrom; y < yTo; y++) {
            if (!isBackground(img.getRGB(x, y), background, tolerance))
                count++;
        }
        return count;
    }

    /**
     * Estimate the thickness in pixels of a vertical stroke (a line along the y axis)
     * by counting, at row {@code y}, the pixels between {@code xFrom} (inclusive) and
     * {@code xTo} (exclusive) whose color differs from the background beyond the tolerance.
     *
     * @param img        rendered page image
     * @param y          the probe row (must cross the stroke)
     * @param xFrom      scan start (inclusive)
     * @param xTo        scan end (exclusive)
     * @param background the surrounding background color
     * @param tolerance  per-channel delta below which a pixel counts as background
     * @return number of non-background pixels on the scan segment
     */
    public static int measureVerticalStrokeWidth(BufferedImage img, int y, int xFrom, int xTo,
                                                 Color background, int tolerance) {
        Guard.notNull(img, "img");
        Guard.notNull(background, "background");
        Guard.checkArgument(xTo > xFrom, "empty scan range", xFrom, xTo);
        int count = 0;
        for (int x = xFrom; x < xTo; x++) {
            if (!isBackground(img.getRGB(x, y), background, tolerance))
                count++;
        }
        return count;
    }

    private static boolean isBackground(int rgb, Color background, int tolerance) {
        int dr = Math.abs(((rgb >> 16) & 0xFF) - background.getRed());
        int dg = Math.abs(((rgb >> 8) & 0xFF) - background.getGreen());
        int db = Math.abs((rgb & 0xFF) - background.getBlue());
        return dr <= tolerance && dg <= tolerance && db <= tolerance;
    }
}
