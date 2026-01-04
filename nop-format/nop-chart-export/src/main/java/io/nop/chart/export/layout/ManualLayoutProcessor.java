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
            applyPlotAreaBounds(chart, x, y, width, height, chartWidth, chartHeight);
        }
    }
    
    /**
     * 应用绘图区边界
     * @param chart JFreeChart对象
     * @param x 绘图区左上角x坐标
     * @param y 绘图区左上角y坐标
     * @param width 绘图区宽度
     * @param height 绘图区高度
     * @param chartWidth 图表总宽度
     * @param chartHeight 图表总高度
     */
    private void applyPlotAreaBounds(JFreeChart chart, int x, int y, int width, int height, int chartWidth, int chartHeight) {
        // 应用绘图区边界
        Plot plot = chart.getPlot();
        if (plot != null) {
            LOG.debug("Applied plot area bounds: x={}, y={}, width={}, height={}", x, y, width, height);
            
            // 计算边距：(图表宽度-绘图区宽度)/2，但是这里需要根据手动布局的x,y坐标来计算
            // 注意：JFreeChart的布局系统是相对复杂的，不同类型的Plot可能需要不同的处理方式
            // 这里我们使用setInsets方法来调整绘图区的位置和大小
            
            // 计算左边距和上边距
            double leftInset = x;
            double topInset = y;
            
            // 计算右边距和下边距：图表宽度 - x - 绘图区宽度
            double rightInset = chartWidth - x - width;
            double bottomInset = chartHeight - y - height;
            
            // 创建RectangleInsets对象并设置到plot上
            org.jfree.chart.ui.RectangleInsets insets = new org.jfree.chart.ui.RectangleInsets(
                topInset, leftInset, bottomInset, rightInset);
            
            plot.setInsets(insets);
            
            // 对于不同类型的Plot，可能需要额外的调整
            // 例如，XYPlot和CategoryPlot可能需要调整AxisSpace
            if (plot instanceof org.jfree.chart.plot.XYPlot) {
                org.jfree.chart.plot.XYPlot xyPlot = (org.jfree.chart.plot.XYPlot) plot;
                // 可以根据需要调整XYPlot的AxisSpace
                // org.jfree.chart.axis.AxisSpace axisSpace = xyPlot.getFixedAxisSpace();
                // if (axisSpace == null) {
                //     axisSpace = new org.jfree.chart.axis.AxisSpace();
                //     xyPlot.setFixedAxisSpace(axisSpace);
                // }
                // // 设置AxisSpace的边距
                // axisSpace.setLeft(leftInset);
                // axisSpace.setTop(topInset);
                // axisSpace.setRight(rightInset);
                // axisSpace.setBottom(bottomInset);
            } else if (plot instanceof org.jfree.chart.plot.CategoryPlot) {
                org.jfree.chart.plot.CategoryPlot categoryPlot = (org.jfree.chart.plot.CategoryPlot) plot;
                // 可以根据需要调整CategoryPlot的AxisSpace
                // org.jfree.chart.axis.AxisSpace axisSpace = categoryPlot.getFixedAxisSpace();
                // if (axisSpace == null) {
                //     axisSpace = new org.jfree.chart.axis.AxisSpace();
                //     categoryPlot.setFixedAxisSpace(axisSpace);
                // }
                // // 设置AxisSpace的边距
                // axisSpace.setLeft(leftInset);
                // axisSpace.setTop(topInset);
                // axisSpace.setRight(rightInset);
                // axisSpace.setBottom(bottomInset);
            }
        }
    }
}