package io.nop.pdf.text;

/**
 * WI16：页文本抽取选项。
 */
public class PageTextOptions {
    public enum Mode { STRIP, LAYOUT }

    public enum ScanPolicy { ERROR, SKIP }

    public enum FffdPolicy { OFF, WARN, ERROR }

    private Mode mode = Mode.STRIP;
    private int maxChars = Integer.MAX_VALUE;
    private ScanPolicy scanPolicy = ScanPolicy.ERROR;
    private FffdPolicy fffdPolicy = FffdPolicy.OFF;
    private int fffdThreshold;

    public Mode getMode() {
        return mode;
    }

    public PageTextOptions setMode(Mode mode) {
        this.mode = mode == null ? Mode.STRIP : mode;
        return this;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public PageTextOptions setMaxChars(int maxChars) {
        this.maxChars = maxChars <= 0 ? Integer.MAX_VALUE : maxChars;
        return this;
    }

    public ScanPolicy getScanPolicy() {
        return scanPolicy;
    }

    public PageTextOptions setScanPolicy(ScanPolicy scanPolicy) {
        this.scanPolicy = scanPolicy == null ? ScanPolicy.ERROR : scanPolicy;
        return this;
    }

    public FffdPolicy getFffdPolicy() {
        return fffdPolicy;
    }

    public PageTextOptions setFffdPolicy(FffdPolicy fffdPolicy) {
        this.fffdPolicy = fffdPolicy == null ? FffdPolicy.OFF : fffdPolicy;
        return this;
    }

    /** 每页 U+FFFD 阈值（超过触发 policy）；fffdPolicy=OFF 时无意义 */
    public int getFffdThreshold() {
        return fffdThreshold;
    }

    public PageTextOptions setFffdThreshold(int fffdThreshold) {
        this.fffdThreshold = Math.max(0, fffdThreshold);
        return this;
    }
}
