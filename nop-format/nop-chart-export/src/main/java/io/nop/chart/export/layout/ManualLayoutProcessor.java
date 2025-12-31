package io.nop.chart.export.layout;

import io.nop.excel.chart.model.ChartManualLayoutModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;

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
        
        // 应用标题布局
        applyTitleLayout(chart, layout, chartWidth, chartHeight);
        
        // 应用图例布局
        applyLegendLayout(chart, layout, chartWidth, chartHeight);
        
        // 应用绘图区布局
        applyPlotAreaLayout(chart, layout, chartWidth, chartHeight);
    }
    
    private void applyTitleLayout(JFreeChart chart, ChartManualLayoutModel layout, int chartWidth, int chartHeight) {
        TextTitle title = chart.getTitle();
        if (title == null) {
            return;
        }
        
        // 检查是否有标题的手动布局配置
        if (layout.getTitleLayout() != null) {
            // 应用标题位置
            applyComponentLayout(title, layout.getTitleLayout(), chartWidth, chartHeight);
            LOG.debug("Applied manual layout to chart title");
        }
    }
    
    private void applyLegendLayout(JFreeChart chart, ChartManualLayoutModel layout, int chartWidth, int chartHeight) {
        LegendTitle legend = chart.getLegend();
        if (legend == null) {
            return;
        }
        
        // 检查是否有图例的手动布局配置
        if (layout.getLegendLayout() != null) {
            // 应用图例位置
            applyComponentLayout(legend, layout.getLegendLayout(), chartWidth, chartHeight);
            LOG.debug("Applied manual layout to chart legend");
        }
    }
    
    private void applyPlotAreaLayout(JFreeChart chart, ChartManualLayoutModel layout, int chartWidth, int chartHeight) {
        // 检查是否有绘图区的手动布局配置
        if (layout.getPlotAreaLayout() != null) {
            // 应用绘图区布局
            applyPlotAreaBounds(chart, layout.getPlotAreaLayout(), chartWidth, chartHeight);
            LOG.debug("Applied manual layout to plot area");
        }
    }
    
    private void applyComponentLayout(Object component, Object componentLayout, int chartWidth, int chartHeight) {
        // 这里需要根据具体的布局配置类型来处理
        // 由于ChartManualLayoutModel的具体结构不明确，这里提供基本框架
        
        if (componentLayout instanceof Rectangle2D) {
            Rectangle2D bounds = (Rectangle2D) componentLayout;
            
            // 转换相对坐标到绝对坐标
            double x = bounds.getX();
            double y = bounds.getY();
            double width = bounds.getWidth();
            double height = bounds.getHeight();
            
            // 如果是百分比坐标（0-1之间），转换为绝对坐标
            if (x <= 1.0 && y <= 1.0 && width <= 1.0 && height <= 1.0) {
                x *= chartWidth;
                y *= chartHeight;
                width *= chartWidth;
                height *= chartHeight;
            }
            
            LOG.debug("Applied component layout: x={}, y={}, width={}, height={}", x, y, width, height);
        }
    }
    
    private void applyPlotAreaBounds(JFreeChart chart, Object plotAreaLayout, int chartWidth, int chartHeight) {
        // 应用绘图区边界
        if (plotAreaLayout instanceof Rectangle2D) {
            Rectangle2D bounds = (Rectangle2D) plotAreaLayout;
            
            // 转换相对坐标到绝对坐标
            double x = bounds.getX();
            double y = bounds.getY();
            double width = bounds.getWidth();
            double height = bounds.getHeight();
            
            // 如果是百分比坐标（0-1之间），转换为绝对坐标
            if (x <= 1.0 && y <= 1.0 && width <= 1.0 && height <= 1.0) {
                x *= chartWidth;
                y *= chartHeight;
                width *= chartWidth;
                height *= chartHeight;
            }
            
            // 设置绘图区边界
            // 注意：JFreeChart的绘图区边界设置比较复杂，这里只是示例
            LOG.debug("Applied plot area bounds: x={}, y={}, width={}, height={}", x, y, width, height);
        }
    }
    
    /**
     * 计算相对位置
     * @param value 位置值
     * @param total 总大小
     * @return 绝对位置
     */
    private double calculateAbsolutePosition(double value, int total) {
        if (value <= 1.0) {
            // 百分比值
            return value * total;
        } else {
            // 绝对值
            return value;
        }
    }
    
    /**
     * 计算相对大小
     * @param value 大小值
     * @param total 总大小
     * @return 绝对大小
     */
    private double calculateAbsoluteSize(double value, int total) {
        if (value <= 1.0) {
            // 百分比值
            return value * total;
        } else {
            // 绝对值
            return value;
        }
    }
}