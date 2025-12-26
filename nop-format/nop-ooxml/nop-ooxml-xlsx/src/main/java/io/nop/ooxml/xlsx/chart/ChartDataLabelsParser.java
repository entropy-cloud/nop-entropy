package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartDataLabelPosition;
import io.nop.excel.chart.model.ChartDataLabelsModel;

/**
 * ChartDataLabelsParser - 数据标签解析器
 * 负责解析Excel图表中的数据标签配置
 */
public class ChartDataLabelsParser {
    public static final ChartDataLabelsParser INSTANCE = new ChartDataLabelsParser();
    
    /**
     * 解析数据标签配置
     * @param dLblsNode 数据标签节点
     * @param styleProvider 样式提供者
     * @return 数据标签模型对象
     */
    public ChartDataLabelsModel parseDataLabels(XNode dLblsNode, IChartStyleProvider styleProvider) {
        if (dLblsNode == null) return null;
        
        ChartDataLabelsModel dataLabels = new ChartDataLabelsModel();
        
        // 解析显示选项
        parseDisplayOptions(dataLabels, dLblsNode);
        
        // 解析标签位置
        parsePosition(dataLabels, dLblsNode);
        
        // 解析数字格式
        parseNumberFormat(dataLabels, dLblsNode);
        
        // 解析偏移量
        parseOffset(dataLabels, dLblsNode);
        
        // 解析分隔符
        parseSeparator(dataLabels, dLblsNode);
        
        // 解析样式
        parseStyles(dataLabels, dLblsNode, styleProvider);
        
        return dataLabels;
    }
    
    /**
     * 解析显示选项
     */
    private void parseDisplayOptions(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        // 是否显示数值 - 从子元素<c:showVal>获取
        XNode showValNode = dLblsNode.childByTag("c:showVal");
        if (showValNode != null) {
            String showVal = showValNode.attrText("val");
            if (showVal != null) {
                dataLabels.setShowVal(ChartPropertyHelper.convertToBoolean(showVal));
            }
        }
        
        // 是否显示类别名称 - 从子元素<c:showCatName>获取
        XNode showCatNameNode = dLblsNode.childByTag("c:showCatName");
        if (showCatNameNode != null) {
            String showCatName = showCatNameNode.attrText("val");
            if (showCatName != null) {
                dataLabels.setShowCatName(ChartPropertyHelper.convertToBoolean(showCatName));
            }
        }
        
        // 是否显示系列名称 - 从子元素<c:showSerName>获取
        XNode showSerNameNode = dLblsNode.childByTag("c:showSerName");
        if (showSerNameNode != null) {
            String showSerName = showSerNameNode.attrText("val");
            if (showSerName != null) {
                dataLabels.setShowSerName(ChartPropertyHelper.convertToBoolean(showSerName));
            }
        }
        
        // 是否显示百分比 - 从子元素<c:showPercent>获取
        XNode showPercentNode = dLblsNode.childByTag("c:showPercent");
        if (showPercentNode != null) {
            String showPercent = showPercentNode.attrText("val");
            if (showPercent != null) {
                dataLabels.setShowPercent(ChartPropertyHelper.convertToBoolean(showPercent));
            }
        }
        
        // 是否显示图例色块 - 从子元素<c:showLegendKey>获取
        XNode showLegendKeyNode = dLblsNode.childByTag("c:showLegendKey");
        if (showLegendKeyNode != null) {
            String showLegendKey = showLegendKeyNode.attrText("val");
            if (showLegendKey != null) {
                dataLabels.setShowLegendKey(ChartPropertyHelper.convertToBoolean(showLegendKey));
            }
        }
        
        // 是否显示气泡大小 - 从子元素<c:showBubbleSize>获取
        XNode showBubbleSizeNode = dLblsNode.childByTag("c:showBubbleSize");
        if (showBubbleSizeNode != null) {
            String showBubbleSize = showBubbleSizeNode.attrText("val");
            if (showBubbleSize != null) {
                dataLabels.setShowBubbleSize(ChartPropertyHelper.convertToBoolean(showBubbleSize));
            }
        }
        
        // 是否显示引导线 - 从子元素<c:showLeaderLines>获取
        XNode showLeaderLinesNode = dLblsNode.childByTag("c:showLeaderLines");
        if (showLeaderLinesNode != null) {
            String showLeaderLines = showLeaderLinesNode.attrText("val");
            if (showLeaderLines != null) {
                dataLabels.setShowLeaderLines(ChartPropertyHelper.convertToBoolean(showLeaderLines));
            }
        }
    }
    
    /**
     * 解析标签位置
     */
    private void parsePosition(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        // 从子元素<c:dLblPos>获取位置设置
        XNode dLblPosNode = dLblsNode.childByTag("c:dLblPos");
        if (dLblPosNode != null) {
            String position = dLblPosNode.attrText("val");
            if (position != null) {
                ChartDataLabelPosition labelPos = mapPosition(position);
                if (labelPos != null) {
                    dataLabels.setPosition(labelPos);
                }
            }
        }
    }
    
    /**
     * 映射位置字符串到枚举
     */
    private ChartDataLabelPosition mapPosition(String position) {
        switch (position) {
            case "bestFit": return ChartDataLabelPosition.BEST_FIT;
            case "center": return ChartDataLabelPosition.CENTER;
            case "insideEnd": return ChartDataLabelPosition.INSIDE_END;
            case "outsideEnd": return ChartDataLabelPosition.OUTSIDE_END;
            case "left": return ChartDataLabelPosition.LEFT;
            case "right": return ChartDataLabelPosition.RIGHT;
            case "top": return ChartDataLabelPosition.TOP;
            case "bottom": return ChartDataLabelPosition.BOTTOM;
            default: return null;
        }
    }
    
    /**
     * 解析数字格式
     */
    private void parseNumberFormat(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        XNode numFmtNode = dLblsNode.childByTag("c:numFmt");
        if (numFmtNode != null) {
            String formatCode = numFmtNode.attrText("formatCode");
            if (formatCode != null) {
                dataLabels.setNumberFormat(formatCode);
            }
        }
    }
    
    /**
     * 解析偏移量
     */
    private void parseOffset(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        // 解析X偏移量
        XNode xNode = dLblsNode.childByTag("c:x");
        if (xNode != null) {
            Double xValue = xNode.attrDouble("val");
            if (xValue != null) {
                dataLabels.setOffsetX(xValue);
            }
        }
        
        // 解析Y偏移量
        XNode yNode = dLblsNode.childByTag("c:y");
        if (yNode != null) {
            Double yValue = yNode.attrDouble("val");
            if (yValue != null) {
                dataLabels.setOffsetY(yValue);
            }
        }
    }
    
    /**
     * 解析分隔符
     */
    private void parseSeparator(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        XNode separatorNode = dLblsNode.childByTag("c:separator");
        if (separatorNode != null) {
            String separator = separatorNode.getText();
            if (separator != null) {
                dataLabels.setSeparator(separator);
            }
        }
    }
    
    /**
     * 解析样式
     */
    private void parseStyles(ChartDataLabelsModel dataLabels, XNode dLblsNode, IChartStyleProvider styleProvider) {
        // 解析形状样式
        XNode spPrNode = dLblsNode.childByTag("c:spPr");
        if (spPrNode != null) {
            dataLabels.setShapeStyle(ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider));
        }
        
        // 解析文本样式
        XNode txPrNode = dLblsNode.childByTag("c:txPr");
        if (txPrNode != null) {
            dataLabels.setTextStyle(ChartTextStyleParser.INSTANCE.parseTextStyle(txPrNode, styleProvider));
        }
    }
}