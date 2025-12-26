package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartAxisPosition;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartGridModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartAxisParser - 坐标轴解析器
 * 负责解析Excel图表中的坐标轴配置
 */
public class ChartAxisParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartAxisParser.class);
    
    public static final ChartAxisParser INSTANCE = new ChartAxisParser();
    
    /**
     * 解析坐标轴配置
     * @param axisNode 坐标轴节点
     * @param styleProvider 样式提供者
     * @return 坐标轴模型对象
     */
    public ChartAxisModel parseAxis(XNode axisNode, IChartStyleProvider styleProvider) {
        if (axisNode == null) return null;
        
        try {
            ChartAxisModel axis = new ChartAxisModel();
            
            // 解析坐标轴ID和类型
            parseBasicProperties(axis, axisNode);
            
            // 解析位置和交叉设置
            parsePositionAndCrossing(axis, axisNode);
            
            // 解析数字格式
            parseNumberFormat(axis, axisNode);
            
            // 解析刻度标记
            parseTickMarks(axis, axisNode);
            
            // 解析刻度标签
            parseTickLabels(axis, axisNode, styleProvider);
            
            // 解析线条样式
            parseLineStyle(axis, axisNode, styleProvider);
            
            // 解析文本样式
            parseTextStyle(axis, axisNode, styleProvider);
            
            // 解析网格线
            parseGridLines(axis, axisNode, styleProvider);
            
            // 解析比例尺
            parseScale(axis, axisNode);
            
            return axis;
        } catch (Exception e) {
            LOG.warn("Failed to parse axis configuration", e);
            return null;
        }
    }
    
    /**
     * 解析基本属性
     */
    private void parseBasicProperties(ChartAxisModel axis, XNode axisNode) {
        try {
            // 解析坐标轴ID - 从子元素c:axId获取
            XNode axIdNode = axisNode.childByTag("c:axId");
            if (axIdNode != null) {
                String axisId = axIdNode.attrText("val");
                if (!StringHelper.isEmpty(axisId)) {
                    axis.setId(axisId);
                }
            }
            
            // 解析坐标轴类型 - 根据标签名确定
            String tagName = axisNode.getTagName();
            ChartAxisType axisType = mapAxisType(tagName);
            if (axisType != null) {
                axis.setType(axisType);
            }
            
            // 解析删除标记 - 从子元素c:delete获取
            XNode deleteNode = axisNode.childByTag("c:delete");
            if (deleteNode != null) {
                String delete = deleteNode.attrText("val");
                if (!StringHelper.isEmpty(delete)) {
                    Boolean deleteValue = ChartPropertyHelper.convertToBoolean(delete);
                    if (deleteValue != null && deleteValue) {
                        // 坐标轴被删除，设置为不可见
                        axis.setVisible(false);
                    } else {
                        axis.setVisible(true);
                    }
                } else {
                    axis.setVisible(true);
                }
            } else {
                axis.setVisible(true);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse axis basic properties", e);
        }
    }
    
    /**
     * 映射坐标轴类型
     */
    private ChartAxisType mapAxisType(String tagName) {
        if (StringHelper.isEmpty(tagName)) return null;
        
        try {
            switch (tagName) {
                case "c:catAx": return ChartAxisType.CATEGORY;
                case "c:valAx": return ChartAxisType.VALUE;
                case "c:dateAx": return ChartAxisType.DATE;
                case "c:serAx": return ChartAxisType.SERIES;
                default: 
                    LOG.warn("Unknown axis type: {}, using default VALUE", tagName);
                    return ChartAxisType.VALUE;
            }
        } catch (Exception e) {
            LOG.warn("Failed to map axis type: {}, using default VALUE", tagName, e);
            return ChartAxisType.VALUE;
        }
    }
    
    /**
     * 解析位置和交叉设置
     */
    private void parsePositionAndCrossing(ChartAxisModel axis, XNode axisNode) {
        // 解析坐标轴位置 - 从子元素c:axPos获取
        XNode axPosNode = axisNode.childByTag("c:axPos");
        if (axPosNode != null) {
            String position = axPosNode.attrText("val");
            if (position != null) {
                ChartAxisPosition axisPos = mapAxisPosition(position);
                if (axisPos != null) {
                    axis.setPosition(axisPos);
                }
            }
        }
        
        // 解析交叉轴ID - 从子元素c:crossAx获取
        XNode crossAxNode = axisNode.childByTag("c:crossAx");
        if (crossAxNode != null) {
            String crossAxisId = crossAxNode.attrText("val");
            if (crossAxisId != null) {
                axis.setCrossAxisId(crossAxisId);
            }
        }
        
        // 解析交叉点
        XNode crossesAtNode = axisNode.childByTag("c:crossesAt");
        if (crossesAtNode != null) {
            Double crossAtValue = crossesAtNode.attrDouble("val");
            if (crossAtValue != null) {
                axis.setCrossAt(crossAtValue);
            }
        }
    }
    
    /**
     * 映射坐标轴位置
     */
    private ChartAxisPosition mapAxisPosition(String position) {
        if (StringHelper.isEmpty(position)) return null;
        
        try {
            switch (position.toLowerCase()) {
                case "b": return ChartAxisPosition.BOTTOM;
                case "l": return ChartAxisPosition.LEFT;
                case "r": return ChartAxisPosition.RIGHT;
                case "t": return ChartAxisPosition.TOP;
                default: 
                    LOG.warn("Unknown axis position: {}, using default BOTTOM", position);
                    return ChartAxisPosition.BOTTOM;
            }
        } catch (Exception e) {
            LOG.warn("Failed to map axis position: {}, using default BOTTOM", position, e);
            return ChartAxisPosition.BOTTOM;
        }
    }
    
    /**
     * 解析数字格式
     */
    private void parseNumberFormat(ChartAxisModel axis, XNode axisNode) {
        try {
            XNode numFmtNode = axisNode.childByTag("c:numFmt");
            if (numFmtNode != null) {
                String formatCode = numFmtNode.attrText("formatCode");
                if (!StringHelper.isEmpty(formatCode)) {
                    axis.setNumberFormat(formatCode);
                }
                
                String sourceLinked = numFmtNode.attrText("sourceLinked");
                if (!StringHelper.isEmpty(sourceLinked)) {
                    Boolean sourceLinkedValue = ChartPropertyHelper.convertToBoolean(sourceLinked);
                    if (sourceLinkedValue != null) {
                        axis.setSourceLinked(sourceLinkedValue);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse axis number format", e);
        }
    }
    
    /**
     * 解析刻度标记
     */
    private void parseTickMarks(ChartAxisModel axis, XNode axisNode) {
        try {
            // 解析主要刻度标记
            XNode majorTickMarkNode = axisNode.childByTag("c:majorTickMark");
            if (majorTickMarkNode != null) {
                String majorTickMark = majorTickMarkNode.attrText("val");
                if (!StringHelper.isEmpty(majorTickMark)) {
                    axis.setMajorTickMark(majorTickMark);
                }
            }
            
            // 解析次要刻度标记
            XNode minorTickMarkNode = axisNode.childByTag("c:minorTickMark");
            if (minorTickMarkNode != null) {
                String minorTickMark = minorTickMarkNode.attrText("val");
                if (!StringHelper.isEmpty(minorTickMark)) {
                    axis.setMinorTickMark(minorTickMark);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse axis tick marks", e);
        }
    }
    
    /**
     * 解析线条样式
     */
    private void parseLineStyle(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        try {
            XNode spPrNode = axisNode.childByTag("c:spPr");
            if (spPrNode != null) {
                // 使用ChartShapeStyleParser解析线条样式
                ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
                if (shapeStyle != null) {
                    axis.setShapeStyle(shapeStyle);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse axis line style", e);
        }
    }
    
    /**
     * 解析网格线
     */
    private void parseGridLines(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        try {
            // 解析主要网格线
            ChartGridModel majorGridLines = ChartGridParser.INSTANCE.parseMajorGridLines(axisNode, styleProvider);
            if (majorGridLines != null) {
                axis.setMajorGrid(majorGridLines);
            }
            
            // 解析次要网格线
            ChartGridModel minorGridLines = ChartGridParser.INSTANCE.parseMinorGridLines(axisNode, styleProvider);
            if (minorGridLines != null) {
                axis.setMinorGrid(minorGridLines);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse axis grid lines", e);
        }
    }
    
    /**
     * 解析刻度标签
     */
    private void parseTickLabels(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        try {
            // 解析刻度标签位置 - 从c:tickLblPos元素获取
            XNode tickLblPosNode = axisNode.childByTag("c:tickLblPos");
            if (tickLblPosNode != null) {
                String position = tickLblPosNode.attrText("val");
                if (!StringHelper.isEmpty(position)) {
                    axis.setTickLabelPosition(position);
                }
            }
            
            // 解析标签对齐 - 从c:lblAlgn元素获取
            XNode lblAlgnNode = axisNode.childByTag("c:lblAlgn");
            if (lblAlgnNode != null) {
                String alignment = lblAlgnNode.attrText("val");
                if (!StringHelper.isEmpty(alignment)) {
                    axis.setLabelAlignment(alignment);
                }
            }
            
            // 解析标签偏移 - 从c:lblOffset元素获取
            XNode lblOffsetNode = axisNode.childByTag("c:lblOffset");
            if (lblOffsetNode != null) {
                String offset = lblOffsetNode.attrText("val");
                if (!StringHelper.isEmpty(offset)) {
                    try {
                        Integer offsetValue = Integer.parseInt(offset);
                        axis.setLabelOffset(offsetValue);
                    } catch (NumberFormatException e) {
                        LOG.warn("Invalid label offset value: {}", offset);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse axis tick labels", e);
        }
    }
    
    /**
     * 解析文本样式
     */
    private void parseTextStyle(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        try {
            XNode txPrNode = axisNode.childByTag("c:txPr");
            if (txPrNode != null) {
                ChartTextStyleModel textStyle = ChartTextStyleParser.INSTANCE.parseTextStyle(txPrNode, styleProvider);
                if (textStyle != null) {
                    axis.setTextStyle(textStyle);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse axis text style", e);
        }
    }
    
    /**
     * 解析比例尺
     */
    private void parseScale(ChartAxisModel axis, XNode axisNode) {
        try {
            XNode scalingNode = axisNode.childByTag("c:scaling");
            if (scalingNode != null) {
                io.nop.excel.chart.model.ChartAxisScaleModel scale = new io.nop.excel.chart.model.ChartAxisScaleModel();
                
                // 解析对数刻度
                Double logBase = scalingNode.attrDouble("logBase");
                if (logBase != null) {
                    scale.setLogBase(logBase);
                }
                
                // 解析最小值
                XNode minNode = scalingNode.childByTag("c:min");
                if (minNode != null) {
                    Double minValue = minNode.attrDouble("val");
                    if (minValue != null) {
                        scale.setMin(minValue);
                    }
                }
                
                // 解析最大值
                XNode maxNode = scalingNode.childByTag("c:max");
                if (maxNode != null) {
                    Double maxValue = maxNode.attrDouble("val");
                    if (maxValue != null) {
                        scale.setMax(maxValue);
                    }
                }
                
                // 解析方向 - 从c:orientation元素获取
                XNode orientationNode = scalingNode.childByTag("c:orientation");
                if (orientationNode != null) {
                    String orientation = orientationNode.attrText("val");
                    if (!StringHelper.isEmpty(orientation)) {
                        scale.setOrientation(orientation);
                    }
                }
                
                axis.setScale(scale);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse axis scale", e);
        }
    }
}