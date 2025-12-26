package io.nop.ooxml.xlsx.chart;

import io.nop.excel.chart.IChartStyleSupportModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartBorderModel;

import java.util.HashMap;
import java.util.Map;

/**
 * DefaultChartStyleProvider - 默认图表样式提供者
 * 提供基本的主题颜色解析和样式应用功能
 */
public class DefaultChartStyleProvider implements IChartStyleProvider {
    
    // 默认主题颜色映射
    private static final Map<String, String> DEFAULT_THEME_COLORS = new HashMap<>();
    
    static {
        // 设置默认主题颜色
        DEFAULT_THEME_COLORS.put("accent1", "#4472C4");
        DEFAULT_THEME_COLORS.put("accent2", "#ED7D31");
        DEFAULT_THEME_COLORS.put("accent3", "#A5A5A5");
        DEFAULT_THEME_COLORS.put("accent4", "#FFC000");
        DEFAULT_THEME_COLORS.put("accent5", "#5B9BD5");
        DEFAULT_THEME_COLORS.put("accent6", "#70AD47");
        
        DEFAULT_THEME_COLORS.put("dk1", "#000000");
        DEFAULT_THEME_COLORS.put("lt1", "#FFFFFF");
        DEFAULT_THEME_COLORS.put("dk2", "#1F497D");
        DEFAULT_THEME_COLORS.put("lt2", "#EEECE1");
        
        DEFAULT_THEME_COLORS.put("background1", "#FFFFFF");
        DEFAULT_THEME_COLORS.put("background2", "#F2F2F2");
        DEFAULT_THEME_COLORS.put("text1", "#000000");
        DEFAULT_THEME_COLORS.put("text2", "#1F497D");
    }
    
    @Override
    public String getThemeColor(String themeColor) {
        if (themeColor == null) return null;
        
        // 移除可能的"tx1"等前缀
        String colorKey = themeColor.toLowerCase();
        if (colorKey.startsWith("tx")) {
            colorKey = colorKey.substring(2);
        }
        
        return DEFAULT_THEME_COLORS.get(colorKey);
    }
    
    @Override
    public String resolveColor(String colorRef) {
        if (colorRef == null) return null;
        
        // 如果是主题颜色引用
        if (colorRef.startsWith("theme") || colorRef.startsWith("tx")) {
            return getThemeColor(colorRef);
        }
        
        // 如果是RGB颜色格式
        if (colorRef.startsWith("#")) {
            return colorRef;
        }
        
        // 如果是预定义颜色名称
        return mapColorName(colorRef);
    }
    
    /**
     * 映射颜色名称到十六进制值
     */
    private String mapColorName(String colorName) {
        if (colorName == null) return null;
        
        switch (colorName.toLowerCase()) {
            case "black": return "#000000";
            case "white": return "#FFFFFF";
            case "red": return "#FF0000";
            case "green": return "#00FF00";
            case "blue": return "#0000FF";
            case "yellow": return "#FFFF00";
            case "cyan": return "#00FFFF";
            case "magenta": return "#FF00FF";
            case "gray": return "#808080";
            case "darkgray": return "#404040";
            case "lightgray": return "#C0C0C0";
            case "orange": return "#FFA500";
            case "pink": return "#FFC0CB";
            case "brown": return "#A52A2A";
            case "purple": return "#800080";
            default: return "#000000"; // 默认黑色
        }
    }
    
    @Override
    public void applyTheme(String componentType, IChartStyleSupportModel model) {
        if (model == null) return;
        
        // 根据组件类型应用不同的主题样式
        if (model instanceof ChartShapeStyleModel) {
            applyShapeTheme(componentType, (ChartShapeStyleModel) model);
        }
    }
    
    /**
     * 应用形状主题
     */
    private void applyShapeTheme(String componentType, ChartShapeStyleModel shapeStyle) {
        if (shapeStyle == null) return;
        
        // 根据组件类型设置默认样式
        switch (componentType) {
            case "title":
                applyTitleTheme(shapeStyle);
                break;
            case "legend":
                applyLegendTheme(shapeStyle);
                break;
            case "axis":
                applyAxisTheme(shapeStyle);
                break;
            case "series":
                applySeriesTheme(shapeStyle);
                break;
            case "grid":
                applyGridTheme(shapeStyle);
                break;
            default:
                applyDefaultTheme(shapeStyle);
                break;
        }
    }
    
    /**
     * 应用标题主题
     */
    private void applyTitleTheme(ChartShapeStyleModel shapeStyle) {
        // 标题通常使用深色文本和透明背景
        if (shapeStyle.getFill() == null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType("none"); // 透明背景
            shapeStyle.setFill(fill);
        }
    }
    
    /**
     * 应用图例主题
     */
    private void applyLegendTheme(ChartShapeStyleModel shapeStyle) {
        // 图例通常使用浅色背景和细边框
        if (shapeStyle.getFill() == null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType("solid");
            fill.setColor("#FFFFFF"); // 白色背景
            shapeStyle.setFill(fill);
        }
        
        if (shapeStyle.getBorder() == null) {
            ChartBorderModel border = new ChartBorderModel();
            border.setColor("#CCCCCC"); // 浅灰色边框
            border.setWidth(1.0);
            shapeStyle.setBorder(border);
        }
    }
    
    /**
     * 应用坐标轴主题
     */
    private void applyAxisTheme(ChartShapeStyleModel shapeStyle) {
        // 坐标轴通常使用黑色线条
        if (shapeStyle.getBorder() == null) {
            ChartBorderModel border = new ChartBorderModel();
            border.setColor("#000000"); // 黑色
            border.setWidth(1.0);
            shapeStyle.setBorder(border);
        }
    }
    
    /**
     * 应用数据系列主题
     */
    private void applySeriesTheme(ChartShapeStyleModel shapeStyle) {
        // 数据系列使用主题颜色，这里设置默认填充
        if (shapeStyle.getFill() == null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType("solid");
            // 具体颜色由系列索引决定，这里设置默认值
            fill.setColor("#4472C4"); // 默认蓝色
            shapeStyle.setFill(fill);
        }
    }
    
    /**
     * 应用网格线主题
     */
    private void applyGridTheme(ChartShapeStyleModel shapeStyle) {
        // 网格线通常使用浅灰色
        if (shapeStyle.getBorder() == null) {
            ChartBorderModel border = new ChartBorderModel();
            border.setColor("#D0D0D0"); // 浅灰色
            border.setWidth(0.5);
            shapeStyle.setBorder(border);
        }
    }
    
    /**
     * 应用默认主题
     */
    private void applyDefaultTheme(ChartShapeStyleModel shapeStyle) {
        // 默认使用透明背景和黑色边框
        if (shapeStyle.getFill() == null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType("none");
            shapeStyle.setFill(fill);
        }
        
        if (shapeStyle.getBorder() == null) {
            ChartBorderModel border = new ChartBorderModel();
            border.setColor("#000000");
            border.setWidth(1.0);
            shapeStyle.setBorder(border);
        }
    }
}