package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;

/**
 * ChartTextParser - 文本解析器
 * 统一处理富文本和简单文本，支持RTF格式到纯文本的转换
 */
public class ChartTextParser {
    public static final ChartTextParser INSTANCE = new ChartTextParser();
    
    /**
     * 解析文本节点，提取文本内容
     * @param textNode 文本节点
     * @return 提取的文本内容
     */
    public String extractText(XNode textNode) {
        if (textNode == null) return null;
        
        // 检查是否为富文本
        XNode richNode = textNode.childByTag("a:r");
        if (richNode != null) {
            return extractRichText(textNode);
        }
        
        // 检查是否为简单文本
        XNode strRefNode = textNode.childByTag("c:strRef");
        if (strRefNode != null) {
            return extractCellReference(strRefNode);
        }
        
        // 直接获取文本内容
        return textNode.getText();
    }
    
    /**
     * 提取单元格引用
     * @param node 包含单元格引用的节点
     * @return 单元格引用字符串
     */
    public String extractCellReference(XNode node) {
        if (node == null) return null;
        
        XNode fNode = node.childByTag("c:f");
        if (fNode != null) {
            return fNode.getText();
        }
        
        return null;
    }
    
    /**
     * 提取富文本内容
     * @param richNode 富文本节点
     * @return 提取的文本内容
     */
    public String extractRichText(XNode richNode) {
        if (richNode == null) return null;
        
        StringBuilder sb = new StringBuilder();
        
        // 遍历所有文本运行节点
        for (XNode rNode : richNode.getChildren()) {
            if (rNode.getTagName().equals("a:r")) {
                XNode tNode = rNode.childByTag("a:t");
                if (tNode != null) {
                    String text = tNode.getText();
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
        }
        
        return sb.length() > 0 ? sb.toString() : null;
    }
    
    /**
     * 检查节点是否包含文本内容
     * @param node 要检查的节点
     * @return 是否包含文本内容
     */
    public boolean hasTextContent(XNode node) {
        if (node == null) return false;
        
        // 检查直接文本
        if (node.getText() != null && !node.getText().trim().isEmpty()) {
            return true;
        }
        
        // 检查富文本
        if (node.childByTag("a:r") != null) {
            return true;
        }
        
        // 检查单元格引用
        if (node.childByTag("c:strRef") != null) {
            return true;
        }
        
        return false;
    }
}