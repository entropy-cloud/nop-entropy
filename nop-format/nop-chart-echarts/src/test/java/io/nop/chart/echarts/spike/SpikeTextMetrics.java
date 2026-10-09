package io.nop.chart.echarts.spike;

/**
 * WI0 spike 的 TextMetrics：zrender 只消费 width 字段。
 */
public class SpikeTextMetrics {
    private final double width;

    public SpikeTextMetrics(double width) {
        this.width = width;
    }

    public double getWidth() {
        return width;
    }
}
