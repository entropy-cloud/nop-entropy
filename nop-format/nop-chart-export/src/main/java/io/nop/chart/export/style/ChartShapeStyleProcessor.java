package io.nop.chart.export.style;

import io.nop.chart.export.utils.JFreeChartStyleAdapter;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Chart shape style processor
 */
public class ChartShapeStyleProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ChartShapeStyleProcessor.class);
    
    /**
     * 应用图表背景样式
     * @param chart JFreeChart对象
     * @param shapeStyle 形状样式
     */
    public void applyBackgroundStyle(JFreeChart chart, ChartShapeStyleModel shapeStyle) {
        LOG.debug("Applying chart background style");
        
        // 设置默认白色背景
        Color backgroundColor = Color.WHITE;
        
        // 如果有指定背景颜色，则使用指定的颜色
        if (shapeStyle.getFill() != null && shapeStyle.getFill().getBackgroundColor() != null) {
            Color specifiedColor = JFreeChartStyleAdapter.convertColor(shapeStyle.getFill().getBackgroundColor());
            if (specifiedColor != null) {
                backgroundColor = specifiedColor;
            }
        }
        
        // 应用背景填充
        chart.setBackgroundPaint(backgroundColor);
        
        // 应用边框
        if (shapeStyle.getBorder() != null) {
            Color borderColor = JFreeChartStyleAdapter.convertColor(shapeStyle.getBorder().getColor());
            if (borderColor != null) {
                // JFreeChart没有直接的边框设置，可以通过其他方式实现
                LOG.debug("Chart border color applied: {}", borderColor);
            }
        }
    }
    
    /**
     * 应用绘图区样式
     * @param plot 绘图区对象
     * @param shapeStyle 形状样式
     */
    public void applyPlotAreaStyle(Plot plot, ChartShapeStyleModel shapeStyle) {
        LOG.debug("Applying plot area style");
        
        // 设置默认白色背景
        Color backgroundColor = Color.WHITE;
        
        // 如果有指定背景颜色，则使用指定的颜色
        if (shapeStyle.getFill() != null && shapeStyle.getFill().getBackgroundColor() != null) {
            Color specifiedColor = JFreeChartStyleAdapter.convertColor(shapeStyle.getFill().getBackgroundColor());
            if (specifiedColor != null) {
                backgroundColor = specifiedColor;
            }
        }
        
        // 应用背景填充
        plot.setBackgroundPaint(backgroundColor);
        
        // 应用边框
        if (shapeStyle.getBorder() != null) {
            Color borderColor = JFreeChartStyleAdapter.convertColor(shapeStyle.getBorder().getColor());
            if (borderColor != null) {
                plot.setOutlinePaint(borderColor);
                plot.setOutlineVisible(true);
            }
        } else {
            plot.setOutlineVisible(false);
        }
    }
    
    /**
     * 应用系列样式
     * @param plot 绘图区对象
     * @param seriesIndex 系列索引
     * @param shapeStyle 形状样式
     */
    public void applySeriesStyle(Plot plot, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        LOG.debug("Applying series style: index={}", seriesIndex);
        
        if (plot instanceof CategoryPlot) {
            applyCategorySeriesStyle((CategoryPlot) plot, seriesIndex, shapeStyle);
        } else if (plot instanceof XYPlot) {
            applyXYSeriesStyle((XYPlot) plot, seriesIndex, shapeStyle);
        }
    }
    
    private void applyCategorySeriesStyle(CategoryPlot plot, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        CategoryItemRenderer renderer = plot.getRenderer();
        if (renderer == null) {
            return;
        }
        
        // 应用填充颜色
        if (shapeStyle.getFill() != null && shapeStyle.getFill().getBackgroundColor() != null) {
            Color fillColor = JFreeChartStyleAdapter.convertColor(shapeStyle.getFill().getBackgroundColor());
            if (fillColor != null) {
                renderer.setSeriesPaint(seriesIndex, fillColor);
            }
        }
        
        // 应用边框
        if (shapeStyle.getBorder() != null) {
            Color outlineColor = JFreeChartStyleAdapter.convertColor(shapeStyle.getBorder().getColor());
            if (outlineColor != null) {
                renderer.setSeriesOutlinePaint(seriesIndex, outlineColor);
                renderer.setSeriesOutlineStroke(seriesIndex, new BasicStroke(1.0f));
            }
        }
    }
    
    private void applyXYSeriesStyle(XYPlot plot, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        XYItemRenderer renderer = plot.getRenderer();
        if (renderer == null) {
            return;
        }
        
        // 应用填充颜色
        if (shapeStyle.getFill() != null && shapeStyle.getFill().getBackgroundColor() != null) {
            Color fillColor = JFreeChartStyleAdapter.convertColor(shapeStyle.getFill().getBackgroundColor());
            if (fillColor != null) {
                renderer.setSeriesPaint(seriesIndex, fillColor);
            }
        }
        
        // 应用边框
        if (shapeStyle.getBorder() != null) {
            Color outlineColor = JFreeChartStyleAdapter.convertColor(shapeStyle.getBorder().getColor());
            if (outlineColor != null) {
                renderer.setSeriesOutlinePaint(seriesIndex, outlineColor);
                renderer.setSeriesOutlineStroke(seriesIndex, new BasicStroke(1.0f));
            }
        }
    }
}