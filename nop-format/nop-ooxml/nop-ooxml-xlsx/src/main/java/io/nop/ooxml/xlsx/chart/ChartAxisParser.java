package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartAxisPosition;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.model.ChartAxisModel;

/**
 * ChartAxisParser - 坐标轴解析器
 * 负责解析Excel图表中的坐标轴配置
 */
public class ChartAxisParser {
    public static final ChartAxisParser INSTANCE = new ChartAxisParser();
    
    /**
     * 解析坐标轴配置
     * @param axisNode 坐标轴节点
     * @param styleProvider 样式提供者
     * @return 坐标轴模型对象
     */
    public ChartAxisModel parseAxis(XNode axisNode, IChartStyleProvider styleProvider) {
        if (axisNode == null) return null;
        
        ChartAxisModel axis = new ChartAxisModel();
        
        // 解析坐标轴ID和类型
        parseBasicProperties(axis, axisNode);
        
        // 解析位置和交叉设置
        parsePositionAndCrossing(axis, axisNode);
        
        // 解析线条样式
        parseLineStyle(axis, axisNode, styleProvider);
        
        // 解析网格线
        parseGridLines(axis, axisNode, styleProvider);
        
        // 解析刻度标签
        parseTickLabels(axis, axisNode, styleProvider);
        
        // 解析比例尺
        parseScale(axis, axisNode);
        
        return axis;
    }
    
    /**
     * 解析基本属性
     */
    private void parseBasicProperties(ChartAxisModel axis, XNode axisNode) {
        // 解析坐标轴ID - 从子元素c:axId获取
        XNode axIdNode = axisNode.childByTag("c:axId");
        if (axIdNode != null) {
            String axisId = axIdNode.attrText("val");
            if (axisId != null) {
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
            if (delete != null) {
                // ChartAxisModel没有setDeleted方法，暂时忽略
                // axis.setDeleted(Boolean.parseBoolean(delete));
            }
        }
    }
    
    /**
     * 映射坐标轴类型
     */
    private ChartAxisType mapAxisType(String tagName) {
        if (tagName == null) return null;
        
        switch (tagName) {
            case "c:catAx": return ChartAxisType.CATEGORY;
            case "c:valAx": return ChartAxisType.VALUE;
            case "c:dateAx": return ChartAxisType.DATE;
            case "c:serAx": return ChartAxisType.SERIES;
            default: return null;
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
        if (position == null) return null;
        
        switch (position.toLowerCase()) {
            case "b": return ChartAxisPosition.BOTTOM;
            case "l": return ChartAxisPosition.LEFT;
            case "r": return ChartAxisPosition.RIGHT;
            case "t": return ChartAxisPosition.TOP;
            default: return ChartAxisPosition.BOTTOM;
        }
    }
    
    /**
     * 解析线条样式
     */
    private void parseLineStyle(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        XNode spPrNode = axisNode.childByTag("c:spPr");
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
                
                axis.setLineStyle(lineStyle);
            }
        }
    }
    
    /**
     * 解析网格线
     */
    private void parseGridLines(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
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
    }
    
    /**
     * 解析刻度标签
     */
    private void parseTickLabels(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider) {
        XNode tickLblNode = axisNode.childByTag("c:tickLbl");
        if (tickLblNode != null) {
            // 创建刻度标签模型
            io.nop.excel.chart.model.ChartTickLabelsModel tickLabels = new io.nop.excel.chart.model.ChartTickLabelsModel();
            
            // 解析位置
            String position = tickLblNode.attrText("lblPos");
            if (position != null) {
                // 可以映射到刻度标签位置枚举
            }
            
            // 解析文本样式
            XNode txPrNode = tickLblNode.childByTag("c:txPr");
            if (txPrNode != null) {
                io.nop.excel.chart.model.ChartTextStyleModel textStyle = ChartTextStyleParser.INSTANCE.parseTextStyle(txPrNode, styleProvider);
                if (textStyle != null) {
                    tickLabels.setTextStyle(textStyle);
                }
            }
            
            axis.setTickLabels(tickLabels);
        }
    }
    
    /**
     * 解析比例尺
     */
    private void parseScale(ChartAxisModel axis, XNode axisNode) {
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
            
            axis.setScale(scale);
        }
    }
}