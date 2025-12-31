package io.nop.chart.export;

import java.awt.Color;

/**
 * Chart export options configuration
 */
public class ChartExportOptions {
    private int width = 800;           // 默认宽度
    private int height = 600;          // 默认高度
    private int dpi = 96;              // 默认DPI
    private float quality = 0.9f;      // 图片质量 (0.0-1.0)
    private boolean antiAlias = true;
    private int timeoutSeconds = 30; // 默认30秒超时  // 抗锯齿
    private Color backgroundColor;     // 背景色
    private long timeoutMs = 30000;    // 超时时间(毫秒)
    private int maxDataSize = 10000;   // 最大数据量
    private IProgressCallback progressCallback; // 进度回调
    
    public ChartExportOptions() {
    }
    
    public static ChartExportOptions defaultOptions() {
        return new ChartExportOptions();
    }
    
    // Getters and Setters
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
    
    public int getDpi() {
        return dpi;
    }
    
    public void setDpi(int dpi) {
        this.dpi = dpi;
    }
    
    public float getQuality() {
        return quality;
    }
    
    public void setQuality(float quality) {
        this.quality = quality;
    }
    
    public boolean isAntiAlias() {
        return antiAlias;
    }
    
    public void setAntiAlias(boolean antiAlias) {
        this.antiAlias = antiAlias;
    }
    
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
    
    public Color getBackgroundColor() {
        return backgroundColor;
    }
    
    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
    
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
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