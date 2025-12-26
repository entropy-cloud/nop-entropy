package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartTrendLineType;
import io.nop.excel.chart.model.ChartTrendLineModel;

/**
 * ChartTrendLineParser - 趋势线解析器
 * 负责解析Excel图表中的趋势线配置
 */
public class ChartTrendLineParser {
    public static final ChartTrendLineParser INSTANCE = new ChartTrendLineParser();
    
    /**
     * 解析趋势线配置
     * @param trendlineNode 趋势线节点
     * @param styleProvider 样式提供者
     * @return 趋势线模型对象
     */
    public ChartTrendLineModel parseTrendLine(XNode trendlineNode, IChartStyleProvider styleProvider) {
        if (trendlineNode == null) return null;
        
        ChartTrendLineModel trendLine = new ChartTrendLineModel();
        
        // 解析趋势线类型
        parseType(trendLine, trendlineNode);
        
        // 解析趋势线名称
        parseName(trendLine, trendlineNode);
        
        // 解析趋势线ID
        parseId(trendLine, trendlineNode);
        
        // 解析周期
        parsePeriod(trendLine, trendlineNode);
        
        // 解析显示选项
        parseDisplayOptions(trendLine, trendlineNode);
        
        // 解析线条样式
        parseLineStyle(trendLine, trendlineNode, styleProvider);
        
        return trendLine;
    }
    
    /**
     * 解析趋势线类型
     */
    private void parseType(ChartTrendLineModel trendLine, XNode trendlineNode) {
        // 从子元素<c:trendlineType>获取趋势线类型
        String type = ChartPropertyHelper.getChildVal(trendlineNode, "c:trendlineType");
        if (type != null) {
            ChartTrendLineType trendType = mapTrendLineType(type);
            if (trendType != null) {
                trendLine.setType(trendType);
            }
        }
    }
    
    /**
     * 映射趋势线类型字符串到枚举
     */
    private ChartTrendLineType mapTrendLineType(String type) {
        if (type == null) return null;
        
        switch (type.toLowerCase()) {
            case "linear": return ChartTrendLineType.LINEAR;
            case "movingavg": return ChartTrendLineType.MOVING_AVG;
            case "polynomial": return ChartTrendLineType.POLYNOMIAL;
            case "power": return ChartTrendLineType.POWER;
            case "exponential": return ChartTrendLineType.EXPONENTIAL;
            case "logarithmic": return ChartTrendLineType.LOGARITHMIC;
            default: return ChartTrendLineType.LINEAR;
        }
    }
    
    /**
     * 解析趋势线名称
     */
    private void parseName(ChartTrendLineModel trendLine, XNode trendlineNode) {
        XNode nameNode = trendlineNode.childByTag("c:name");
        if (nameNode != null) {
            String name = nameNode.getText();
            if (name != null) {
                trendLine.setName(name);
            }
        }
    }
    
    /**
     * 解析趋势线ID
     */
    private void parseId(ChartTrendLineModel trendLine, XNode trendlineNode) {
        String id = trendlineNode.attrText("id");
        if (id != null) {
            trendLine.setId(id);
        }
    }
    
    /**
     * 解析周期（仅对移动平均有效）
     */
    private void parsePeriod(ChartTrendLineModel trendLine, XNode trendlineNode) {
        // 从子元素<c:period>获取周期设置
        String period = ChartPropertyHelper.getChildVal(trendlineNode, "c:period");
        if (period != null) {
            try {
                trendLine.setPeriod(Integer.parseInt(period));
            } catch (NumberFormatException e) {
                // 忽略格式错误，使用默认值
            }
        }
    }
    
    /**
     * 解析显示选项
     */
    private void parseDisplayOptions(ChartTrendLineModel trendLine, XNode trendlineNode) {
        // 是否显示公式 - 从子元素<c:dispRSqr>获取
        String displayEquation = ChartPropertyHelper.getChildVal(trendlineNode, "c:dispRSqr");
        if (displayEquation != null) {
            trendLine.setDisplayEquation(ChartPropertyHelper.convertToBoolean(displayEquation));
        }
        
        // 是否显示R平方值 - 从子元素<c:dispRSqr>获取
        // 注意：dispRSqr通常用于显示R平方值，而不是公式
        // 如果需要支持公式显示，应该检查<c:dispEq>元素
        String displayEq = ChartPropertyHelper.getChildVal(trendlineNode, "c:dispEq");
        if (displayEq != null) {
            // 可以扩展支持公式显示
        }
    }
    
    /**
     * 解析线条样式
     */
    private void parseLineStyle(ChartTrendLineModel trendLine, XNode trendlineNode, IChartStyleProvider styleProvider) {
        XNode spPrNode = trendlineNode.childByTag("c:spPr");
        if (spPrNode != null) {
            // 使用ChartShapeStyleParser解析线条样式
            io.nop.excel.chart.model.ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
            if (shapeStyle != null && shapeStyle.getBorder() != null) {
                // 创建线条样式模型并设置属性
                io.nop.excel.chart.model.ChartLineStyleModel lineStyle = new io.nop.excel.chart.model.ChartLineStyleModel();
                io.nop.excel.chart.model.ChartBorderModel border = shapeStyle.getBorder();
                
                // 设置颜色
                if (border.getColor() != null) {
                    lineStyle.setColor(border.getColor());
                }
                
                // 设置宽度
                if (border.getWidth() != null) {
                    lineStyle.setWidth(border.getWidth());
                }
                
                // 设置样式
                if (border.getStyle() != null) {
                    lineStyle.setStyle(border.getStyle());
                }
                
                // 设置透明度
                if (border.getOpacity() != null) {
                    lineStyle.setOpacity(border.getOpacity());
                }
                
                trendLine.setLineStyle(lineStyle);
            }
        }
    }
    
    /**
     * 解析系列中的趋势线
     * @param seriesNode 数据系列节点
     * @param styleProvider 样式提供者
     * @return 趋势线模型列表
     */
    public java.util.List<ChartTrendLineModel> parseSeriesTrendLines(XNode seriesNode, IChartStyleProvider styleProvider) {
        java.util.List<ChartTrendLineModel> trendLines = new java.util.ArrayList<>();
        
        for (XNode trendlineNode : seriesNode.getChildren()) {
            if (trendlineNode.getTagName().equals("c:trendline")) {
                ChartTrendLineModel trendLine = parseTrendLine(trendlineNode, styleProvider);
                if (trendLine != null) {
                    trendLines.add(trendLine);
                }
            }
        }
        
        return trendLines;
    }
}