package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartDataLabelPosition;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.ooxml.xlsx.XlsxErrors.*;

/**
 * ChartDataLabelsParser - 数据标签解析器
 * 负责解析Excel图表中的数据标签配置
 */
public class ChartDataLabelsParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataLabelsParser.class);
    public static final ChartDataLabelsParser INSTANCE = new ChartDataLabelsParser();
    
    /**
     * 解析数据标签配置
     * @param dLblsNode 数据标签节点
     * @param styleProvider 样式提供者
     * @return 数据标签模型对象
     */
    public ChartDataLabelsModel parseDataLabels(XNode dLblsNode, IChartStyleProvider styleProvider) {
        if (dLblsNode == null) {
            LOG.warn("Data labels node is null, returning null");
            return null;
        }
        
        try {
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
        } catch (Exception e) {
            LOG.warn("Failed to parse data labels configuration", e);
            // 返回基本的dataLabels对象而不是null，确保图表解析能继续
            return new ChartDataLabelsModel();
        }
    }
    
    /**
     * 解析显示选项
     */
    private void parseDisplayOptions(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        try {
            // 是否显示数值 - 从子元素<c:showVal>获取
            Boolean showValBool = ChartPropertyHelper.getChildBoolVal(dLblsNode, "c:showVal");
            if (showValBool != null) {
                dataLabels.setShowVal(showValBool);
            }
            
            // 是否显示类别名称 - 从子元素<c:showCatName>获取
            Boolean showCatNameBool = ChartPropertyHelper.getChildBoolVal(dLblsNode, "c:showCatName");
            if (showCatNameBool != null) {
                dataLabels.setShowCatName(showCatNameBool);
            }
            
            // 是否显示系列名称 - 从子元素<c:showSerName>获取
            Boolean showSerNameBool = ChartPropertyHelper.getChildBoolVal(dLblsNode, "c:showSerName");
            if (showSerNameBool != null) {
                dataLabels.setShowSerName(showSerNameBool);
            }
            
            // 是否显示百分比 - 从子元素<c:showPercent>获取
            Boolean showPercentBool = ChartPropertyHelper.getChildBoolVal(dLblsNode, "c:showPercent");
            if (showPercentBool != null) {
                dataLabels.setShowPercent(showPercentBool);
            }
            
            // 是否显示图例色块 - 从子元素<c:showLegendKey>获取
            Boolean showLegendKeyBool = ChartPropertyHelper.getChildBoolVal(dLblsNode, "c:showLegendKey");
            if (showLegendKeyBool != null) {
                dataLabels.setShowLegendKey(showLegendKeyBool);
            }
            
            // 是否显示气泡大小 - 从子元素<c:showBubbleSize>获取
            Boolean showBubbleSizeBool = ChartPropertyHelper.getChildBoolVal(dLblsNode, "c:showBubbleSize");
            if (showBubbleSizeBool != null) {
                dataLabels.setShowBubbleSize(showBubbleSizeBool);
            }
            
            // 是否显示引导线 - 从子元素<c:showLeaderLines>获取
            Boolean showLeaderLinesBool = ChartPropertyHelper.getChildBoolVal(dLblsNode, "c:showLeaderLines");
            if (showLeaderLinesBool != null) {
                dataLabels.setShowLeaderLines(showLeaderLinesBool);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse data labels display options", e);
        }
    }
    
    /**
     * 解析标签位置
     */
    private void parsePosition(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        try {
            // 从子元素<c:dLblPos>获取位置设置
            String position = ChartPropertyHelper.getChildVal(dLblsNode, "c:dLblPos");
            if (!StringHelper.isEmpty(position)) {
                ChartDataLabelPosition labelPos = mapPosition(position);
                if (labelPos != null) {
                    dataLabels.setPosition(labelPos);
                } else {
                    LOG.warn("Unknown data label position: {}, using default", position);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse data label position", e);
        }
    }
    
    /**
     * 映射位置字符串到枚举
     * 修复OOXML位置值映射，使用ChartDataLabelPosition.fromValue()方法
     */
    private ChartDataLabelPosition mapPosition(String position) {
        try {
            // 使用枚举的fromValue方法进行映射
            return ChartDataLabelPosition.fromValue(position);
        } catch (IllegalArgumentException e) {
            // 如果fromValue失败，尝试手动映射一些常见的变体
            try {
                switch (position.toLowerCase()) {
                    case "bestfit":
                    case "best_fit":
                        // bestFit在当前枚举中不存在，使用CENTER作为默认
                        LOG.warn("bestFit position not supported, using CENTER instead");
                        return ChartDataLabelPosition.CENTER;
                    case "center":
                    case "ctr":
                        return ChartDataLabelPosition.CENTER;
                    case "insideend":
                    case "inend":
                        return ChartDataLabelPosition.INSIDE_END;
                    case "outsideend":
                    case "outend":
                        return ChartDataLabelPosition.OUTSIDE_END;
                    case "left":
                    case "l":
                        return ChartDataLabelPosition.LEFT;
                    case "right":
                    case "r":
                        return ChartDataLabelPosition.RIGHT;
                    case "top":
                    case "t":
                        return ChartDataLabelPosition.TOP;
                    case "bottom":
                    case "b":
                        return ChartDataLabelPosition.BOTTOM;
                    default:
                        LOG.warn("Unknown data label position: {}, using default CENTER", position);
                        return ChartDataLabelPosition.CENTER;
                }
            } catch (Exception ex) {
                LOG.warn("Failed to map data label position: {}, using default CENTER", position, ex);
                return ChartDataLabelPosition.CENTER;
            }
        }
    }
    
    /**
     * 解析数字格式
     */
    private void parseNumberFormat(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        try {
            XNode numFmtNode = dLblsNode.childByTag("c:numFmt");
            if (numFmtNode != null) {
                String formatCode = numFmtNode.attrText("formatCode");
                if (!StringHelper.isEmpty(formatCode)) {
                    dataLabels.setNumberFormat(formatCode);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse data label number format", e);
        }
    }
    
    /**
     * 解析偏移量
     */
    private void parseOffset(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        try {
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
        } catch (Exception e) {
            LOG.warn("Failed to parse data label offset", e);
        }
    }
    
    /**
     * 解析分隔符
     */
    private void parseSeparator(ChartDataLabelsModel dataLabels, XNode dLblsNode) {
        try {
            XNode separatorNode = dLblsNode.childByTag("c:separator");
            if (separatorNode != null) {
                String separator = separatorNode.getText();
                if (!StringHelper.isEmpty(separator)) {
                    dataLabels.setSeparator(separator);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse data label separator", e);
        }
    }
    
    /**
     * 解析样式
     */
    private void parseStyles(ChartDataLabelsModel dataLabels, XNode dLblsNode, IChartStyleProvider styleProvider) {
        try {
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
        } catch (Exception e) {
            LOG.warn("Failed to parse data label styles", e);
        }
    }
}