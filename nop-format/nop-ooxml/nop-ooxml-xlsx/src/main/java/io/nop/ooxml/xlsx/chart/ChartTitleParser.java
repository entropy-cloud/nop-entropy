package io.nop.ooxml.xlsx.chart;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartTitlePosition;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.ooxml.xlsx.parse.Selector;

import static io.nop.ooxml.xlsx.parse.OOXMLLoaderHelper.attrBool;
import static io.nop.ooxml.xlsx.parse.OOXMLLoaderHelper.attrText;

/**
 * ChartTitleParser - 标题解析器
 * 负责解析Excel图表中的标题配置
 */
public class ChartTitleParser {
    public static final ChartTitleParser INSTANCE = new ChartTitleParser();
    
    /**
     * 解析标题配置
     * @param titleNode 标题节点
     * @param styleProvider 样式提供者
     * @return 标题模型对象
     */
    public ChartTitleModel parseTitle(XNode titleNode, IChartStyleProvider styleProvider) {
        if (titleNode == null) return null;
        
        ChartTitleModel title = new ChartTitleModel();
        
        // 解析可见性
        parseVisibility(title, titleNode);
        
        // 解析位置
        parsePosition(title, titleNode);
        
        // 解析覆盖选项
        parseOverlay(title, titleNode);
        
        // 解析文本内容
        parseTextContent(title, titleNode);
        
        // 解析手动布局
        parseManualLayout(title, titleNode);
        
        // 解析形状样式
        parseShapeStyle(title, titleNode, styleProvider);
        
        // 解析文本样式
        parseTextStyle(title, titleNode, styleProvider);
        
        return title;
    }
    
    /**
     * 解析可见性
     */
    private void parseVisibility(ChartTitleModel title, XNode titleNode) {
        // 在OOXML中，标题可见性通过<c:autoTitleDeleted>元素控制
        // 如果titleNode存在，则标题默认可见
        title.setVisible(true);
        
        // 检查是否有自动删除设置
        XNode autoTitleDeletedNode = titleNode.getParent().childByTag("c:autoTitleDeleted");
        if (autoTitleDeletedNode != null) {
            String autoTitleDeleted = autoTitleDeletedNode.attrText("val");
            if (autoTitleDeleted != null && ChartPropertyHelper.convertToBoolean(autoTitleDeleted)) {
                title.setVisible(false);
            }
        }
    }
    
    /**
     * 解析位置
     */
    private void parsePosition(ChartTitleModel title, XNode titleNode) {
        // 在OOXML中，标题位置通过手动布局或默认位置控制
        // 没有专门的position属性，暂时保留默认位置设置
        
        // 如果需要从子元素获取位置设置，可以添加相应的解析逻辑
        // 例如：从<c:layout>或<c:manualLayout>元素获取位置信息
    }
    
    /**
     * 解析覆盖选项
     */
    private void parseOverlay(ChartTitleModel title, XNode titleNode) {
        // 在OOXML中，标题没有专门的overlay属性
        // 覆盖选项通常通过布局设置控制，暂时保留默认设置
        
        // 如果需要从子元素获取覆盖设置，可以添加相应的解析逻辑
    }
    
    /**
     * 解析文本内容
     */
    private void parseTextContent(ChartTitleModel title, XNode titleNode) {
        // 在OOXML中，标题文本内容通过子元素获取，而不是直接属性
        // 解析富文本内容
        XNode txNode = titleNode.childByTag("tx");
        if (txNode != null) {
            parseRichTextContent(title, txNode);
        }
    }
    
    /**
     * 解析富文本内容
     */
    private void parseRichTextContent(ChartTitleModel title, XNode txNode) {
        // 解析简单文本
        XNode richNode = txNode.childByTag("rich");
        if (richNode != null) {
            // 使用ChartTextParser解析富文本
            String text = ChartTextParser.INSTANCE.parseText(richNode);
            if (!StringHelper.isEmpty(text)) {
                title.setText(text);
            }
        }
        
        // 解析字符串引用
        XNode strRefNode = txNode.childByTag("strRef");
        if (strRefNode != null) {
            XNode fNode = strRefNode.childByTag("f");
            if (fNode != null) {
                title.setTextCellRef(fNode.getTextContent());
            }
        }
    }
    
    /**
     * 解析手动布局
     */
    private void parseManualLayout(ChartTitleModel title, XNode titleNode) {
        XNode manualLayoutNode = titleNode.childByTag("manualLayout");
        if (manualLayoutNode != null) {
            ChartManualLayoutModel manualLayout = parseManualLayout(manualLayoutNode);
            title.setManualLayout(manualLayout);
        }
    }
    
    /**
     * 解析手动布局配置
     */
    private ChartManualLayoutModel parseManualLayout(XNode manualLayoutNode) {
        ChartManualLayoutModel layout = new ChartManualLayoutModel();
        
        // 解析布局目标
        String layoutTarget = manualLayoutNode.attrText("layoutTarget");
        if (!StringHelper.isEmpty(layoutTarget)) {
            layout.setLayoutTarget(layoutTarget);
        }
        
        // 解析X位置
        String xMode = manualLayoutNode.attrText("xMode");
        if (!StringHelper.isEmpty(xMode)) {
            layout.setXMode(xMode);
        }
        
        XNode xNode = manualLayoutNode.childByTag("x");
        if (xNode != null) {
            Double x = parseLayoutValue(xNode);
            if (x != null) {
                layout.setX(x);
            }
        }
        
        // 解析Y位置
        String yMode = manualLayoutNode.attrText("yMode");
        if (!StringHelper.isEmpty(yMode)) {
            layout.setYMode(yMode);
        }
        
        XNode yNode = manualLayoutNode.childByTag("y");
        if (yNode != null) {
            Double y = parseLayoutValue(yNode);
            if (y != null) {
                layout.setY(y);
            }
        }
        
        // 解析宽度
        String wMode = manualLayoutNode.attrText("wMode");
        if (!StringHelper.isEmpty(wMode)) {
            layout.setWMode(wMode);
        }
        
        XNode wNode = manualLayoutNode.childByTag("w");
        if (wNode != null) {
            Double w = parseLayoutValue(wNode);
            if (w != null) {
                layout.setW(w);
            }
        }
        
        // 解析高度
        String hMode = manualLayoutNode.attrText("hMode");
        if (!StringHelper.isEmpty(hMode)) {
            layout.setHMode(hMode);
        }
        
        XNode hNode = manualLayoutNode.childByTag("h");
        if (hNode != null) {
            Double h = parseLayoutValue(hNode);
            if (h != null) {
                layout.setH(h);
            }
        }
        
        return layout;
    }
    
    /**
     * 解析布局数值
     */
    private Double parseLayoutValue(XNode valueNode) {
        Double value = valueNode.attrDouble("val");
        if (value != null) {
            return value;
        }
        
        // 如果attrDouble返回null，尝试从文本内容解析
        String valueStr = valueNode.getTextContent();
        if (!StringHelper.isEmpty(valueStr)) {
            try {
                return Double.parseDouble(valueStr);
            } catch (NumberFormatException e) {
                throw new NopException("ERR_EXCEL_INVALID_LAYOUT_VALUE")
                    .param("value", valueStr);
            }
        }
        return null;
    }
    
    /**
     * 解析形状样式
     */
    private void parseShapeStyle(ChartTitleModel title, XNode titleNode, IChartStyleProvider styleProvider) {
        XNode spPrNode = titleNode.childByTag("spPr");
        if (spPrNode != null) {
            ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
            title.setShapeStyle(shapeStyle);
        }
    }
    
    /**
     * 解析文本样式
     */
    private void parseTextStyle(ChartTitleModel title, XNode titleNode, IChartStyleProvider styleProvider) {
        XNode txPrNode = titleNode.childByTag("txPr");
        if (txPrNode != null) {
            ChartTextStyleModel textStyle = ChartTextStyleParser.INSTANCE.parseTextStyle(txPrNode, styleProvider);
            title.setTextStyle(textStyle);
        }
    }
    
    /**
     * 解析标题覆盖选项（用于图例等元素）
     */
    public ChartTitleModel parseTitleOverlay(XNode titleNode, IChartStyleProvider styleProvider) {
        if (titleNode == null) return null;
        
        ChartTitleModel title = new ChartTitleModel();
        
        // 只解析覆盖相关的属性
        parseOverlay(title, titleNode);
        parseTextContent(title, titleNode);
        parseShapeStyle(title, titleNode, styleProvider);
        parseTextStyle(title, titleNode, styleProvider);
        
        return title;
    }
}