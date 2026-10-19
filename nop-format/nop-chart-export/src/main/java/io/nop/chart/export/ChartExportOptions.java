package io.nop.chart.export;

/**
 * Chart export options configuration.
 *
 * <p>Plan 2364 D28 adjudication of the audited dead configs: every retained field
 * has a real consumption point in {@link ChartExporter}; dpi/quality/antiAlias and
 * the overlapping timeoutMs were removed (no implementable ECharts semantics --
 * rendering is vector-consistent and anti-aliased by the engine). This is a
 * deliberate contract change with zero production callers (verified 2026-10-10).</p>
 */
public class ChartExportOptions {
    /** Export format: "png" (default) or "svg". */
    private String format = "png";
    private int width = 800;
    private int height = 600;
    /** Optional chart background color (CSS color); absent = transparent PNG. */
    private String backgroundColor;
    private int timeoutSeconds = 30;
    /** Maximum data points per series before ERR_DATA_VOLUME_EXCEEDED. */
    private int maxDataSize = 10000;
    private IProgressCallback progressCallback;

    public ChartExportOptions() {
    }

    public static ChartExportOptions defaultOptions() {
        return new ChartExportOptions();
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxDataSize() {
        return maxDataSize;
    }

    public void setMaxDataSize(int maxDataSize) {
        this.maxDataSize = maxDataSize;
    }

    public IProgressCallback getProgressCallback() {
        return progressCallback;
    }

    public void setProgressCallback(IProgressCallback progressCallback) {
        this.progressCallback = progressCallback;
    }
}
