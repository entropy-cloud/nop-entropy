package io.nop.pdf.verify;

import io.nop.api.core.util.Guard;

import java.awt.image.BufferedImage;

/**
 * Pixel-level comparison of two same-size images with anti-aliasing normalization.
 *
 * <p>Adjudication (plan 2463): two pixels count as equal when every RGB channel differs
 * by at most {@link #DEFAULT_CHANNEL_TOLERANCE} levels (anti-aliasing normalization);
 * structural similarity is measured as windowed SSIM over 8x8 grayscale windows with
 * the standard constants C1=6.5025, C2=58.5225.</p>
 */
public final class ImageDiff {

    /**
     * Per-channel tolerance (levels out of 255) below which two pixel colors are considered equal.
     * Absorbs anti-aliasing edge differences.
     */
    public static final int DEFAULT_CHANNEL_TOLERANCE = 8;

    /**
     * Placeholder recommended threshold for assertClose; final adjudication lives in
     * ai-dev/analysis/2026-10/2026-10-19-pdf-visual-acceptance-toolchain.md.
     */
    public static final double RECOMMENDED_MAX_DIFF_RATIO = 0.001;

    /**
     * Placeholder recommended minimum SSIM for assertClose; final adjudication lives in
     * ai-dev/analysis/2026-10/2026-10-19-pdf-visual-acceptance-toolchain.md.
     */
    public static final double RECOMMENDED_MIN_SSIM = 0.99;

    private static final int SSIM_WINDOW = 8;
    private static final double SSIM_C1 = 6.5025;
    private static final double SSIM_C2 = 58.5225;

    private ImageDiff() {
    }

    /**
     * Compare two images with the default channel tolerance.
     *
     * @param a first image
     * @param b second image
     * @return the diff result, never null
     * @throws IllegalArgumentException when image dimensions differ (explicit failure, never a silent zero diff)
     */
    public static ImageDiffResult compare(BufferedImage a, BufferedImage b) {
        return compare(a, b, DEFAULT_CHANNEL_TOLERANCE);
    }

    /**
     * Compare two images pixel by pixel.
     *
     * @param a                first image
     * @param b                second image
     * @param channelTolerance max per-channel delta (0-255) treated as equal
     * @return the diff result, never null
     * @throws IllegalArgumentException when image dimensions differ or tolerance is out of range
     */
    public static ImageDiffResult compare(BufferedImage a, BufferedImage b, int channelTolerance) {
        Guard.notNull(a, "a");
        Guard.notNull(b, "b");
        Guard.checkArgument(channelTolerance >= 0 && channelTolerance <= 255,
                "channelTolerance out of range", channelTolerance);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new IllegalArgumentException("Image dimensions differ: " + a.getWidth() + "x" + a.getHeight()
                    + " vs " + b.getWidth() + "x" + b.getHeight()
                    + " (explicit failure is intended; images compared for diff must have the same size)");
        }

        int w = a.getWidth();
        int h = a.getHeight();
        long diffPixels = 0;
        int maxChannelDelta = 0;
        float[] grayA = new float[w * h];
        float[] grayB = new float[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgbA = a.getRGB(x, y);
                int rgbB = b.getRGB(x, y);
                int dr = Math.abs(((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF));
                int dg = Math.abs(((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF));
                int db = Math.abs((rgbA & 0xFF) - (rgbB & 0xFF));
                int maxDelta = Math.max(dr, Math.max(dg, db));
                if (maxDelta > maxChannelDelta)
                    maxChannelDelta = maxDelta;
                if (maxDelta > channelTolerance)
                    diffPixels++;
                int idx = y * w + x;
                grayA[idx] = toGray(rgbA);
                grayB[idx] = toGray(rgbB);
            }
        }

        double ssim = ssim(grayA, grayB, w, h);
        return new ImageDiffResult(w, h, diffPixels, maxChannelDelta, ssim);
    }

    private static float toGray(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (float) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    /**
     * Mean SSIM over all 8x8 windows (image edges smaller than one window are ignored).
     */
    private static double ssim(float[] ga, float[] gb, int w, int h) {
        if (w < SSIM_WINDOW || h < SSIM_WINDOW)
            throw new IllegalArgumentException("Image too small for 8x8 SSIM windows: " + w + "x" + h);
        double total = 0;
        int windows = 0;
        for (int wy = 0; wy + SSIM_WINDOW <= h; wy += SSIM_WINDOW) {
            for (int wx = 0; wx + SSIM_WINDOW <= w; wx += SSIM_WINDOW) {
                total += windowSsim(ga, gb, w, wx, wy);
                windows++;
            }
        }
        return total / windows;
    }

    private static double windowSsim(float[] ga, float[] gb, int w, int wx, int wy) {
        double meanA = 0, meanB = 0;
        int n = SSIM_WINDOW * SSIM_WINDOW;
        for (int y = wy; y < wy + SSIM_WINDOW; y++) {
            for (int x = wx; x < wx + SSIM_WINDOW; x++) {
                meanA += ga[y * w + x];
                meanB += gb[y * w + x];
            }
        }
        meanA /= n;
        meanB /= n;
        double varA = 0, varB = 0, cov = 0;
        for (int y = wy; y < wy + SSIM_WINDOW; y++) {
            for (int x = wx; x < wx + SSIM_WINDOW; x++) {
                float da = ga[y * w + x] - (float) meanA;
                float db = gb[y * w + x] - (float) meanB;
                varA += da * da;
                varB += db * db;
                cov += da * db;
            }
        }
        varA /= (n - 1);
        varB /= (n - 1);
        cov /= (n - 1);
        return (2 * meanA * meanB + SSIM_C1) * (2 * cov + SSIM_C2)
                / ((meanA * meanA + meanB * meanB + SSIM_C1) * (varA + varB + SSIM_C2));
    }
}
