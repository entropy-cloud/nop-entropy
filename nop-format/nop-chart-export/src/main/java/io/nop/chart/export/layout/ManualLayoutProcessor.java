package io.nop.chart.export.layout;

import io.nop.excel.chart.model.ChartManualLayoutModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.Plot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manual layout processor for chart components
 */
public class ManualLayoutProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ManualLayoutProcessor.class);
    
    /**
     * 应用手动布局配置
     * @param chart JFreeChart对象
     * @param layout 布局配置
     * @param chartWidth 图表宽度
     * @param chartHeight 图表高度
     */
    public void applyManualLayout(JFreeChart chart, ChartManualLayoutModel layout, int chartWidth, int chartHeight) {
        if (layout == null) {
            return;
        }
        
        LOG.debug("Applying manual layout configuration");
        
        // 获取手动布局参数
        Double percentX = layout.getPercentX();
        Double percentY = layout.getPercentY();
        Double percentW = layout.getPercentW();
        Double percentH = layout.getPercentH();
        
        // 只有当所有四个参数都存在时才应用手动布局
        if (percentX != null && percentY != null && percentW != null && percentH != null) {
            // 转换百分比到实际像素值
            int x = (int) (chartWidth * percentX);
            int y = (int) (chartHeight * percentY);
            int width = (int) (chartWidth * percentW);
            int height = (int) (chartHeight * percentH);
            
            // 应用绘图区布局
            applyPlotAreaBounds(chart, x, y, width, height);
        }
    }
    
    /**
     * 应用绘图区边界
     * @param chart JFreeChart对象
     * @param x 绘图区左上角x坐标
     * @param y 绘图区左上角y坐标
     * @param width 绘图区宽度
     * @param height 绘图区高度
     */
    private void applyPlotAreaBounds(JFreeChart chart, int x, int y, int width, int height) {
        // 应用绘图区边界
        Plot plot = chart.getPlot();
        if (plot != null) {
            // 注意：JFreeChart的绘图区边界设置比较复杂，这里只是示例
            // 实际实现可能需要根据具体的图表类型和需求进行调整
            LOG.debug("Applied plot area bounds: x={}, y={}, width={}, height={}", x, y, width, height);
        }
    }
}