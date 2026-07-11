package io.nop.graph.api;

/**
 * Leiden 社区检测算法配置。
 */
public class LeidenConfig {
    private double resolution = 1.0;
    private int maxIterations = 10;
    private long timeoutMs = 30000;
    private int minCommunitySize = 0;

    public double getResolution() {
        return resolution;
    }

    public LeidenConfig setResolution(double resolution) {
        this.resolution = resolution;
        return this;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public LeidenConfig setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public LeidenConfig setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public int getMinCommunitySize() {
        return minCommunitySize;
    }

    public LeidenConfig setMinCommunitySize(int minCommunitySize) {
        this.minCommunitySize = minCommunitySize;
        return this;
    }

    public static LeidenConfig create() {
        return new LeidenConfig();
    }
}
