package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartManualLayoutParser - 手动布局解析器
 * 负责解析OOXML图表中的手动布局配置
 * 统一处理c:manualLayout元素的解析逻辑
 */
public class ChartManualLayoutParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartManualLayoutParser.class);
    
    public static final ChartManualLayoutParser INSTANCE = new ChartManualLayoutParser();
    
    /**
     * 解析手动布局配置
     * @param parentNode 包含c:manualLayout子元素的父节点
     * @return 手动布局模型对象，如果没有找到布局配置则返回null
     */
    public ChartManualLayoutModel parseManualLayout(XNode parentNode) {
        if (parentNode == null) {
            return null;
        }
        
        XNode manualLayoutNode = parentNode.childByTag("c:manualLayout");
        if (manualLayoutNode == null) {
            return null;
        }
        
        return parseManualLayoutNode(manualLayoutNode);
    }
    
    /**
     * 解析c:manualLayout节点
     * 在OOXML中，布局属性在子元素中，不是父元素的属性
     * @param manualLayoutNode c:manualLayout节点
     * @return 手动布局模型对象
     */
    public ChartManualLayoutModel parseManualLayoutNode(XNode manualLayoutNode) {
        if (manualLayoutNode == null) {
            return null;
        }
        
        try {
            ChartManualLayoutModel layout = new ChartManualLayoutModel();
            
            // 解析布局目标 - 从子元素获取
            parseLayoutTarget(layout, manualLayoutNode);
            
            // 解析X位置配置
            parseXConfiguration(layout, manualLayoutNode);
            
            // 解析Y位置配置
            parseYConfiguration(layout, manualLayoutNode);
            
            // 解析宽度配置
            parseWidthConfiguration(layout, manualLayoutNode);
            
            // 解析高度配置
            parseHeightConfiguration(layout, manualLayoutNode);
            
            return layout;
            
        } catch (Exception e) {
            LOG.warn("Failed to parse manual layout configuration", e);
            return null;
        }
    }
    
    /**
     * 解析布局目标
     */
    private void parseLayoutTarget(ChartManualLayoutModel layout, XNode manualLayoutNode) {
        try {
            XNode layoutTargetNode = manualLayoutNode.childByTag("c:layoutTarget");
            if (layoutTargetNode != null) {
                String layoutTarget = layoutTargetNode.attrText("val");
                if (!StringHelper.isEmpty(layoutTarget)) {
                    layout.setLayoutTarget(layoutTarget);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse layout target", e);
        }
    }
    
    /**
     * 解析X位置配置
     */
    private void parseXConfiguration(ChartManualLayoutModel layout, XNode manualLayoutNode) {
        try {
            // 解析X位置模式
            String xMode = ChartPropertyHelper.getChildVal(manualLayoutNode, "c:xMode");
            if (!StringHelper.isEmpty(xMode)) {
                layout.setXMode(xMode);
            }
            
            // 解析X位置值
            XNode xNode = manualLayoutNode.childByTag("c:x");
            if (xNode != null) {
                Double x = parseLayoutValue(xNode);
                if (x != null) {
                    layout.setPercentX(x);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse X configuration", e);
        }
    }
    
    /**
     * 解析Y位置配置
     */
    private void parseYConfiguration(ChartManualLayoutModel layout, XNode manualLayoutNode) {
        try {
            // 解析Y位置模式
            String yMode = ChartPropertyHelper.getChildVal(manualLayoutNode, "c:yMode");
            if (!StringHelper.isEmpty(yMode)) {
                layout.setYMode(yMode);
            }
            
            // 解析Y位置值
            XNode yNode = manualLayoutNode.childByTag("c:y");
            if (yNode != null) {
                Double y = parseLayoutValue(yNode);
                if (y != null) {
                    layout.setPercentY(y);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse Y configuration", e);
        }
    }
    
    /**
     * 解析宽度配置
     */
    private void parseWidthConfiguration(ChartManualLayoutModel layout, XNode manualLayoutNode) {
        try {
            // 解析宽度模式
            String wMode = ChartPropertyHelper.getChildVal(manualLayoutNode, "c:wMode");
            if (!StringHelper.isEmpty(wMode)) {
                layout.setWMode(wMode);
            }
            
            // 解析宽度值
            XNode wNode = manualLayoutNode.childByTag("c:w");
            if (wNode != null) {
                Double w = parseLayoutValue(wNode);
                if (w != null) {
                    layout.setPercentW(w);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse width configuration", e);
        }
    }
    
    /**
     * 解析高度配置
     */
    private void parseHeightConfiguration(ChartManualLayoutModel layout, XNode manualLayoutNode) {
        try {
            // 解析高度模式
            String hMode = ChartPropertyHelper.getChildVal(manualLayoutNode, "c:hMode");
            if (!StringHelper.isEmpty(hMode)) {
                layout.setHMode(hMode);
            }
            
            // 解析高度值
            XNode hNode = manualLayoutNode.childByTag("c:h");
            if (hNode != null) {
                Double h = parseLayoutValue(hNode);
                if (h != null) {
                    layout.setPercentH(h);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse height configuration", e);
        }
    }
    
    /**
     * 解析布局数值
     * OOXML中布局值通常是百分比形式，需要转换为0-1之间的小数
     * @param valueNode 包含val属性的节点
     * @return 解析后的数值，失败时返回null
     */
    private Double parseLayoutValue(XNode valueNode) {
        if (valueNode == null) {
            return null;
        }
        
        try {
            Double value = valueNode.attrDouble("val");
            if (value != null) {
                // OOXML中的布局值通常是百分比形式
                // 如果值大于1，则认为是百分比，需要除以100
                if (value > 1.0) {
                    return value / 100.0;
                } else {
                    return value;
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse layout value from node: {}", valueNode.getTagName(), e);
        }
        
        return null;
    }
}