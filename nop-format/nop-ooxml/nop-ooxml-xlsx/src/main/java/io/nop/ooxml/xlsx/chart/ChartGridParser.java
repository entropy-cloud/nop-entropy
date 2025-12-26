package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartLineStyle;
import io.nop.excel.chart.model.ChartGridModel;

/**
 * ChartGridParser - 网格线解析器
 * 负责解析Excel图表中的网格线配置
 */
public class ChartGridParser {
    public static final ChartGridParser INSTANCE = new ChartGridParser();
    
    /**
     * 解析网格线配置
     * @param gridLinesNode 网格线节点
     * @param styleProvider 样式提供者
     * @return 网格线模型对象
     */
    public ChartGridModel parseGridLines(XNode gridLinesNode, IChartStyleProvider styleProvider) {
        if (gridLinesNode == null) return null;
        
        ChartGridModel grid = new ChartGridModel();
        
        // 解析可见性
        parseVisibility(grid, gridLinesNode);
        
        // 解析线条样式
        parseLineStyle(grid, gridLinesNode, styleProvider);
        
        return grid;
    }
    
    /**
     * 解析可见性
     */
    private void parseVisibility(ChartGridModel grid, XNode gridLinesNode) {
        // 检查是否有spPr节点，如果有则说明网格线可见
        XNode spPrNode = gridLinesNode.childByTag("c:spPr");
        if (spPrNode != null) {
            grid.setVisible(true);
        } else {
            // 如果没有样式定义，可能网格线不可见
            grid.setVisible(false);
        }
    }
    
    /**
     * 解析线条样式
     */
    private void parseLineStyle(ChartGridModel grid, XNode gridLinesNode, IChartStyleProvider styleProvider) {
        XNode spPrNode = gridLinesNode.childByTag("c:spPr");
        if (spPrNode == null) return;
        
        // 使用ChartShapeStyleParser解析线条样式
        io.nop.excel.chart.model.ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
        if (shapeStyle != null) {
            // 从形状样式中提取线条属性
            extractLineProperties(grid, shapeStyle);
        }
    }
    
    /**
     * 从形状样式中提取线条属性
     */
    private void extractLineProperties(ChartGridModel grid, io.nop.excel.chart.model.ChartShapeStyleModel shapeStyle) {
        if (shapeStyle.getBorder() != null) {
            io.nop.excel.chart.model.ChartBorderModel border = shapeStyle.getBorder();
            
            // 设置颜色
            if (border.getColor() != null) {
                grid.setColor(border.getColor());
            }
            
            // 设置宽度
            if (border.getWidth() != null) {
                grid.setWidth(border.getWidth());
            }
            
            // 设置线条样式
            if (border.getStyle() != null) {
                grid.setStyle(mapLineStyle(border.getStyle()));
            }
            
            // 设置透明度
            if (border.getOpacity() != null) {
                grid.setOpacity(border.getOpacity());
            }
        }
    }
    
    /**
     * 映射线条样式字符串到枚举
     * 只映射OOXML ST_PresetLineDashVal规范中的有效值
     */
    private ChartLineStyle mapLineStyle(String style) {
        if (style == null) return null;
        
        switch (style.toLowerCase()) {
            case "solid": return ChartLineStyle.SOLID;
            case "dash": return ChartLineStyle.DASH;
            case "dot": return ChartLineStyle.DOT;
            case "dashdot": return ChartLineStyle.DASH_DOT;
            case "dashdotdot": return ChartLineStyle.DASH_DOT_DOT;
            case "lgdash": return ChartLineStyle.LONG_DASH;
            case "lgdashdot": return ChartLineStyle.LONG_DASH_DOT;
            case "lgdashdotdot": return ChartLineStyle.LONG_DASH_DOT_DOT;
            case "sysdash": return ChartLineStyle.SYS_DASH;
            case "sysdot": return ChartLineStyle.SYS_DOT;
            case "sysdashdot": return ChartLineStyle.SYS_DASH_DOT;
            default: return ChartLineStyle.SOLID;
        }
    }
    
    /**
     * 解析主要网格线
     * @param axisNode 坐标轴节点
     * @param styleProvider 样式提供者
     * @return 主要网格线模型
     */
    public ChartGridModel parseMajorGridLines(XNode axisNode, IChartStyleProvider styleProvider) {
        XNode majorGridLinesNode = axisNode.childByTag("c:majorGridlines");
        return parseGridLines(majorGridLinesNode, styleProvider);
    }
    
    /**
     * 解析次要网格线
     * @param axisNode 坐标轴节点
     * @param styleProvider 样式提供者
     * @return 次要网格线模型
     */
    public ChartGridModel parseMinorGridLines(XNode axisNode, IChartStyleProvider styleProvider) {
        XNode minorGridLinesNode = axisNode.childByTag("c:minorGridlines");
        return parseGridLines(minorGridLinesNode, styleProvider);
    }
}