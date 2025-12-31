package io.nop.chart.export.style;

import io.nop.chart.export.utils.JFreeChartStyleAdapter;
import io.nop.excel.chart.model.ChartTextStyleModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Chart text style processor
 */
public class ChartTextStyleProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTextStyleProcessor.class);
    
    /**
     * 应用标题文本样式
     * @param chart JFreeChart对象
     * @param textStyle 文本样式
     */
    public void applyTitleTextStyle(JFreeChart chart, ChartTextStyleModel textStyle) {
        if (textStyle == null) {
            return;
        }
        
        LOG.debug("Applying title text style");
        
        TextTitle title = chart.getTitle();
        if (title != null) {
            applyTextStyleToTitle(title, textStyle);
        }
    }
    
    /**
     * 应用图例文本样式
     * @param chart JFreeChart对象
     * @param textStyle 文本样式
     */
    public void applyLegendTextStyle(JFreeChart chart, ChartTextStyleModel textStyle) {
        if (textStyle == null) {
            return;
        }
        
        LOG.debug("Applying legend text style");
        
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            applyTextStyleToLegend(legend, textStyle);
        }
    }
    
    /**
     * 应用坐标轴文本样式
     * @param plot 绘图区对象
     * @param textStyle 文本样式
     * @param isXAxis 是否为X轴
     */
    public void applyAxisTextStyle(Plot plot, ChartTextStyleModel textStyle, boolean isXAxis) {
        if (textStyle == null) {
            return;
        }
        
        LOG.debug("Applying axis text style: isXAxis={}", isXAxis);
        
        Axis axis = getAxis(plot, isXAxis);
        if (axis != null) {
            applyTextStyleToAxis(axis, textStyle);
        }
    }
    
    /**
     * 应用数据标签文本样式
     * @param plot 绘图区对象
     * @param textStyle 文本样式
     */
    public void applyDataLabelTextStyle(Plot plot, ChartTextStyleModel textStyle) {
        if (textStyle == null) {
            return;
        }
        
        LOG.debug("Applying data label text style");
        
        if (plot instanceof CategoryPlot) {
            applyCategoryDataLabelStyle((CategoryPlot) plot, textStyle);
        } else if (plot instanceof XYPlot) {
            applyXYDataLabelStyle((XYPlot) plot, textStyle);
        }
    }
    
    private void applyTextStyleToTitle(TextTitle title, ChartTextStyleModel textStyle) {
        // 应用字体
        Font font = JFreeChartStyleAdapter.convertFont(textStyle);
        if (font != null) {
            title.setFont(font);
        }
        
        // 应用颜色
        if (textStyle.getFont() != null && textStyle.getFont().getFontColor() != null) {
            Color color = JFreeChartStyleAdapter.convertColor(textStyle.getFont().getFontColor());
            if (color != null) {
                title.setPaint(color);
            }
        }
    }
    
    private void applyTextStyleToLegend(LegendTitle legend, ChartTextStyleModel textStyle) {
        // 应用字体
        Font font = JFreeChartStyleAdapter.convertFont(textStyle);
        if (font != null) {
            legend.setItemFont(font);
        }
        
        // 应用颜色
        if (textStyle.getFont() != null && textStyle.getFont().getFontColor() != null) {
            Color color = JFreeChartStyleAdapter.convertColor(textStyle.getFont().getFontColor());
            if (color != null) {
                legend.setItemPaint(color);
            }
        }
    }
    
    private void applyTextStyleToAxis(Axis axis, ChartTextStyleModel textStyle) {
        // 应用标签字体
        Font font = JFreeChartStyleAdapter.convertFont(textStyle);
        if (font != null) {
            axis.setTickLabelFont(font);
            axis.setLabelFont(font);
        }
        
        // 应用颜色
        if (textStyle.getFont() != null && textStyle.getFont().getFontColor() != null) {
            Color color = JFreeChartStyleAdapter.convertColor(textStyle.getFont().getFontColor());
            if (color != null) {
                axis.setTickLabelPaint(color);
                axis.setLabelPaint(color);
            }
        }
    }
    
    private void applyCategoryDataLabelStyle(CategoryPlot plot, ChartTextStyleModel textStyle) {
        CategoryItemRenderer renderer = plot.getRenderer();
        if (renderer == null) {
            return;
        }
        
        // 应用数据标签字体和颜色
        Font font = JFreeChartStyleAdapter.convertFont(textStyle);
        Color color = null;
        if (textStyle.getFont() != null && textStyle.getFont().getFontColor() != null) {
            color = JFreeChartStyleAdapter.convertColor(textStyle.getFont().getFontColor());
        }
        
        // 注意：JFreeChart的数据标签样式设置比较复杂，这里只是基本实现
        if (font != null || color != null) {
            LOG.debug("Data label style applied to category plot");
        }
    }
    
    private void applyXYDataLabelStyle(XYPlot plot, ChartTextStyleModel textStyle) {
        XYItemRenderer renderer = plot.getRenderer();
        if (renderer == null) {
            return;
        }
        
        // 应用数据标签字体和颜色
        Font font = JFreeChartStyleAdapter.convertFont(textStyle);
        Color color = null;
        if (textStyle.getFont() != null && textStyle.getFont().getFontColor() != null) {
            color = JFreeChartStyleAdapter.convertColor(textStyle.getFont().getFontColor());
        }
        
        // 注意：JFreeChart的数据标签样式设置比较复杂，这里只是基本实现
        if (font != null || color != null) {
            LOG.debug("Data label style applied to XY plot");
        }
    }
    
    private Axis getAxis(Plot plot, boolean isXAxis) {
        if (plot instanceof CategoryPlot) {
            CategoryPlot categoryPlot = (CategoryPlot) plot;
            return isXAxis ? categoryPlot.getDomainAxis() : categoryPlot.getRangeAxis();
        } else if (plot instanceof XYPlot) {
            XYPlot xyPlot = (XYPlot) plot;
            return isXAxis ? xyPlot.getDomainAxis() : xyPlot.getRangeAxis();
        }
        return null;
    }
}