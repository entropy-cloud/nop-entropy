package io.nop.chart.export.renderer;

import io.nop.chart.export.utils.JFreeChartStyleAdapter;
import io.nop.excel.chart.model.ChartLegendModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Chart legend renderer
 */
public class ChartLegendRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(ChartLegendRenderer.class);
    
    /**
     * 渲染图表图例
     * @param chart JFreeChart对象
     * @param legend 图例配置
     */
    public void renderLegend(JFreeChart chart, ChartLegendModel legend) {
        if (legend == null || !legend.isVisible()) {
            chart.removeLegend();
            return;
        }
        
        LOG.debug("Rendering chart legend");
        
        // 获取或创建图例
        LegendTitle legendTitle = chart.getLegend();
        if (legendTitle == null) {
            legendTitle = new LegendTitle(chart.getPlot());
            chart.addLegend(legendTitle);
        }
        
        // 应用样式
        applyLegendStyle(legendTitle, legend);
        
        // 应用位置
        applyLegendPosition(legendTitle, legend);
    }
    
    private void applyLegendStyle(LegendTitle legendTitle, ChartLegendModel legend) {
        // 应用文本样式
        if (legend.getTextStyle() != null) {
            Font font = JFreeChartStyleAdapter.convertFont(legend.getTextStyle());
            if (font != null) {
                legendTitle.setItemFont(font);
            }
            
            // 从字体中获取颜色
            if (legend.getTextStyle().getFont() != null && legend.getTextStyle().getFont().getFontColor() != null) {
                Color color = JFreeChartStyleAdapter.convertColor(legend.getTextStyle().getFont().getFontColor());
                if (color != null) {
                    legendTitle.setItemPaint(color);
                }
            }
        }
        
        // 应用边框样式
        if (legend.getShapeStyle() != null) {
            if (legend.getShapeStyle().getBorder() != null) {
                Color borderColor = JFreeChartStyleAdapter.convertColor(legend.getShapeStyle().getBorder().getColor());
                
                if (borderColor != null) {
                    legendTitle.setFrame(new BlockBorder(borderColor));
                }
            } else {
                legendTitle.setFrame(BlockBorder.NONE);
            }
            
            // 应用背景
            if (legend.getShapeStyle().getFill() != null) {
                Paint backgroundColor = null;
                
                // 检查是否有渐变填充设置
                if (legend.getShapeStyle().getFill().getForegroundColor() != null && 
                    legend.getShapeStyle().getFill().getBackgroundColor() != null) {
                    // 创建渐变填充
                    backgroundColor = JFreeChartStyleAdapter.createGradientPaint(
                        legend.getShapeStyle().getFill().getBackgroundColor(), 
                        legend.getShapeStyle().getFill().getForegroundColor(), 
                        0, 0, 100, 0
                    );
                } else if (legend.getShapeStyle().getFill().getBackgroundColor() != null) {
                    // 单色填充
                    backgroundColor = JFreeChartStyleAdapter.convertColor(legend.getShapeStyle().getFill().getBackgroundColor());
                }
                
                if (backgroundColor != null) {
                    legendTitle.setBackgroundPaint(backgroundColor);
                }
            }
        }
        
        // 设置图例外边距
        legendTitle.setMargin(10, 10, 10, 10);
    }
    
    private void applyLegendPosition(LegendTitle legendTitle, ChartLegendModel legend) {
        // TODO: 应用图例位置 - 需要检查ChartLegendModel是否有布局相关方法
        // 暂时使用默认位置
        legendTitle.setPosition(RectangleEdge.BOTTOM);
    }
}