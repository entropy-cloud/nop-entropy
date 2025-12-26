package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartLegendPosition;
import io.nop.excel.chart.constants.ChartOrientation;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelVerticalAlignment;

/**
 * ChartLegendParser - 图例解析器
 * 负责解析Excel图表中的图例配置
 */
public class ChartLegendParser {
    public static final ChartLegendParser INSTANCE = new ChartLegendParser();
    
    /**
     * 解析图例配置
     * @param legendNode 图例节点
     * @param styleProvider 样式提供者
     * @return 图例模型对象
     */
    public ChartLegendModel parseLegend(XNode legendNode, IChartStyleProvider styleProvider) {
        if (legendNode == null) return null;
        
        ChartLegendModel legend = new ChartLegendModel();
        
        // 解析可见性
        parseVisibility(legend, legendNode);
        
        // 解析位置
        parsePosition(legend, legendNode);
        
        // 解析对齐方式
        parseAlignment(legend, legendNode);
        
        // 解析方向
        parseOrientation(legend, legendNode);
        
        // 解析覆盖选项
        parseOverlay(legend, legendNode);
        
        // 解析样式
        parseStyles(legend, legendNode, styleProvider);
        
        return legend;
    }
    
    /**
     * 解析可见性
     */
    private void parseVisibility(ChartLegendModel legend, XNode legendNode) {
        // 在OOXML中，图例默认可见，通过<c:legend>元素的存在与否控制
        // 如果legendNode存在，则图例可见
        legend.setVisible(true);
        
        // 检查是否有显式删除设置
        XNode deleteNode = legendNode.childByTag("c:delete");
        if (deleteNode != null) {
            String delete = deleteNode.attrText("val");
            if (delete != null && ChartPropertyHelper.convertToBoolean(delete)) {
                legend.setVisible(false);
            }
        }
    }
    
    /**
     * 解析位置
     */
    private void parsePosition(ChartLegendModel legend, XNode legendNode) {
        // 从子元素<c:legendPos>获取位置设置
        XNode legendPosNode = legendNode.childByTag("c:legendPos");
        if (legendPosNode != null) {
            String position = legendPosNode.attrText("val");
            if (position != null) {
                ChartLegendPosition legendPos = mapPosition(position);
                if (legendPos != null) {
                    legend.setPosition(legendPos);
                }
            }
        }
    }
    
    /**
     * 映射位置字符串到枚举
     */
    private ChartLegendPosition mapPosition(String position) {
        if (position == null) return null;
        
        switch (position.toLowerCase()) {
            case "b": return ChartLegendPosition.BOTTOM;
            case "tr": return ChartLegendPosition.TOP_RIGHT;
            case "l": return ChartLegendPosition.LEFT;
            case "r": return ChartLegendPosition.RIGHT;
            case "t": return ChartLegendPosition.TOP;
            default: return ChartLegendPosition.RIGHT; // 默认右侧
        }
    }
    
    /**
     * 解析对齐方式
     */
    private void parseAlignment(ChartLegendModel legend, XNode legendNode) {
        // 在OOXML中，图例对齐方式通常通过位置设置，没有专门的align属性
        // 如果需要对齐设置，应该从相应的子元素获取
        // 目前暂时保留默认对齐设置
        
        // 解析水平对齐 - 从子元素获取
        XNode alignNode = legendNode.childByTag("c:align");
        if (alignNode != null) {
            String align = alignNode.attrText("val");
            if (align != null) {
                ExcelHorizontalAlignment horizontalAlign = mapHorizontalAlignment(align);
                if (horizontalAlign != null) {
                    legend.setAlign(horizontalAlign);
                }
            }
        }
        
        // 解析垂直对齐 - 从子元素获取
        XNode verticalAlignNode = legendNode.childByTag("c:verticalAlign");
        if (verticalAlignNode != null) {
            String verticalAlign = verticalAlignNode.attrText("val");
            if (verticalAlign != null) {
                ExcelVerticalAlignment verticalAlignment = mapVerticalAlignment(verticalAlign);
                if (verticalAlignment != null) {
                    legend.setVerticalAlign(verticalAlignment);
                }
            }
        }
    }
    
    /**
     * 映射水平对齐字符串到枚举
     */
    private ExcelHorizontalAlignment mapHorizontalAlignment(String align) {
        if (align == null) return null;
        
        switch (align.toLowerCase()) {
            case "left": return ExcelHorizontalAlignment.LEFT;
            case "center": return ExcelHorizontalAlignment.CENTER;
            case "right": return ExcelHorizontalAlignment.RIGHT;
            case "justify": return ExcelHorizontalAlignment.JUSTIFY;
            default: return ExcelHorizontalAlignment.LEFT;
        }
    }
    
    /**
     * 映射垂直对齐字符串到枚举
     */
    private ExcelVerticalAlignment mapVerticalAlignment(String verticalAlign) {
        if (verticalAlign == null) return null;
        
        switch (verticalAlign.toLowerCase()) {
            case "top": return ExcelVerticalAlignment.TOP;
            case "center": return ExcelVerticalAlignment.CENTER;
            case "bottom": return ExcelVerticalAlignment.BOTTOM;
            case "justify": return ExcelVerticalAlignment.JUSTIFY;
            default: return ExcelVerticalAlignment.CENTER;
        }
    }
    
    /**
     * 解析方向
     */
    private void parseOrientation(ChartLegendModel legend, XNode legendNode) {
        // 从子元素获取方向设置
        XNode orientationNode = legendNode.childByTag("c:orientation");
        if (orientationNode != null) {
            String orientation = orientationNode.attrText("val");
            if (orientation != null) {
                ChartOrientation chartOrientation = mapOrientation(orientation);
                if (chartOrientation != null) {
                    legend.setOrientation(chartOrientation);
                }
            }
        }
    }
    
    /**
     * 映射方向字符串到枚举
     */
    private ChartOrientation mapOrientation(String orientation) {
        if (orientation == null) return null;
        
        switch (orientation.toLowerCase()) {
            case "horizontal": return ChartOrientation.HORIZONTAL;
            case "vertical": return ChartOrientation.VERTICAL;
            default: return ChartOrientation.VERTICAL; // 默认垂直
        }
    }
    
    /**
     * 解析覆盖选项
     */
    private void parseOverlay(ChartLegendModel legend, XNode legendNode) {
        // 从子元素<c:overlay>获取覆盖设置
        XNode overlayNode = legendNode.childByTag("c:overlay");
        if (overlayNode != null) {
            String overlay = overlayNode.attrText("val");
            if (overlay != null) {
                legend.setOverlay(ChartPropertyHelper.convertToBoolean(overlay));
            }
        }
    }
    
    /**
     * 解析样式
     */
    private void parseStyles(ChartLegendModel legend, XNode legendNode, IChartStyleProvider styleProvider) {
        // 解析形状样式
        XNode spPrNode = legendNode.childByTag("c:spPr");
        if (spPrNode != null) {
            legend.setShapeStyle(ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider));
        }
        
        // 解析文本样式
        XNode txPrNode = legendNode.childByTag("c:txPr");
        if (txPrNode != null) {
            legend.setTextStyle(ChartTextStyleParser.INSTANCE.parseTextStyle(txPrNode, styleProvider));
        }
    }
}