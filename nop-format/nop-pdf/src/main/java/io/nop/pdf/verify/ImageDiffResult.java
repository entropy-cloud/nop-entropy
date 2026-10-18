package io.nop.pdf.verify;

/**
 * Immutable result of an {@link ImageDiff} comparison, with assertion helpers that throw
 * {@link AssertionError} so JUnit reports them natively.
 */
public final class ImageDiffResult {

    private final int width;
    private final int height;
    private final long diffPixels;
    private final int maxChannelDelta;
    private final double ssim;

    ImageDiffResult(int width, int height, long diffPixels, int maxChannelDelta, double ssim) {
        this.width = width;
        this.height = height;
        this.diffPixels = diffPixels;
        this.maxChannelDelta = maxChannelDelta;
        this.ssim = ssim;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Number of pixels whose max channel delta exceeds the tolerance.
     */
    public long getDiffPixels() {
        return diffPixels;
    }

    public long getTotalPixels() {
        return (long) width * height;
    }

    /**
     * Max per-channel delta observed over all pixels and channels (0-255).
     */
    public int getMaxChannelDelta() {
        return maxChannelDelta;
    }

    /**
     * Mean SSIM over 8x8 grayscale windows (1.0 for identical images).
     */
    public double getSsim() {
        return ssim;
    }

    /**
     * Ratio of differing pixels over total pixels.
     */
    public double getDiffPixelRatio() {
        long total = getTotalPixels();
        return total == 0 ? 0 : (double) diffPixels / total;
    }

    public boolean isIdentical() {
        return diffPixels == 0;
    }

    @Override
    public String toString() {
        return "ImageDiffResult{" +
                "size=" + width + "x" + height +
                ", diffPixels=" + diffPixels +
                ", diffPixelRatio=" + String.format("%.6f", getDiffPixelRatio()) +
                ", maxChannelDelta=" + maxChannelDelta +
                ", ssim=" + String.format("%.6f", ssim) +
                '}';
    }

    /**
     * Assert that the two images are structurally close enough.
     *
     * @param maxDiffRatio max allowed ratio of differing pixels
     * @param minSsim      min allowed mean SSIM
     * @throws AssertionError when either threshold is violated
     */
    public void assertClose(double maxDiffRatio, double minSsim) {
        if (getDiffPixelRatio() > maxDiffRatio || ssim < minSsim) {
            throw new AssertionError("Image diff exceeds tolerance: expected diffPixelRatio<=" + maxDiffRatio
                    + " and ssim>=" + minSsim + ", actual " + this);
        }
    }
}
