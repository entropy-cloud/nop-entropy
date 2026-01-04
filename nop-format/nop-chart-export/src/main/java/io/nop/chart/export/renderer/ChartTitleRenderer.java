package io.nop.chart.export.renderer;

import io.nop.excel.resolver.ICellRefResolver;
import io.nop.chart.export.utils.JFreeChartStyleAdapter;
import io.nop.excel.chart.model.ChartTitleModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.title.TextTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Chart title renderer
 */
public class ChartTitleRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTitleRenderer.class);
    
    /**
     * 渲染图表标题
     * @param chart JFreeChart对象
     * @param title 标题配置
     * @param resolver 数据解析器
     */
    public void renderTitle(JFreeChart chart, ChartTitleModel title, ICellRefResolver resolver) {
        if (title == null || !title.isVisible()) {
            chart.setTitle((String) null);
            return;
        }
        
        LOG.debug("Rendering chart title: {}", title.getText());
        
        // 解析标题文本
        String titleText = resolveTitleText(title, resolver);
        
        if (titleText == null || titleText.trim().isEmpty()) {
            chart.setTitle((String) null);
            return;
        }
        
        // 创建标题对象
        TextTitle textTitle = new TextTitle(titleText);
        
        // 应用样式
        applyTitleStyle(textTitle, title);
        
        // 设置标题
        chart.setTitle(textTitle);
    }
    
    private String resolveTitleText(ChartTitleModel title, ICellRefResolver resolver) {
        String text = title.getText();
        if (text == null) {
            return null;
        }
        
        // 如果是单元格引用，解析引用
        if (text.startsWith("=") && resolver != null) {
            try {
                Object value = resolver.getValue(text.substring(1));
                return value != null ? value.toString() : "";
            } catch (Exception e) {
                LOG.warn("Failed to resolve title reference: {}", text, e);
                return text; // 返回原始文本
            }
        }
        
        return text;
    }
    
    private void applyTitleStyle(TextTitle textTitle, ChartTitleModel title) {
        // 应用字体样式
        if (title.getTextStyle() != null) {
            Font font = JFreeChartStyleAdapter.convertFont(title.getTextStyle());
            if (font != null) {
                textTitle.setFont(font);
            }
            
            // 从字体中获取颜色
            if (title.getTextStyle().getFont() != null && title.getTextStyle().getFont().getFontColor() != null) {
                Color color = JFreeChartStyleAdapter.convertColor(title.getTextStyle().getFont().getFontColor());
                if (color != null) {
                    textTitle.setPaint(color);
                }
            }
        }
        
        // 设置标题背景（如果支持）
        if (title.getShapeStyle() != null && title.getShapeStyle().getFill() != null) {
            Color backgroundColor = JFreeChartStyleAdapter.convertColor(title.getShapeStyle().getFill().getBackgroundColor());
            if (backgroundColor != null) {
                textTitle.setBackgroundPaint(backgroundColor);
                // 使用正确的setBorder方法
                textTitle.setBorder(1.0, 1.0, 1.0, 1.0);
            }
        }
    }
}