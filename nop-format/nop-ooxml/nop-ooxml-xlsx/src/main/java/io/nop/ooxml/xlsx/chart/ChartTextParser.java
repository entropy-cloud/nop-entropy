package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartTextParser - 文本解析器
 * 统一处理富文本和简单文本，支持RTF格式到纯文本的转换
 */
public class ChartTextParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTextParser.class);
    
    public static final ChartTextParser INSTANCE = new ChartTextParser();
    
    /**
     * 解析文本节点，提取文本内容
     * @param textNode 文本节点
     * @return 提取的文本内容
     */
    public String extractText(XNode textNode) {
        if (textNode == null) return null;
        
        try {
            // 检查是否为富文本 - 正确的OOXML结构是 <c:rich><a:p><a:r><a:t>
            XNode richNode = textNode.childByTag("c:rich");
            if (richNode != null) {
                return extractRichText(richNode);
            }
            
            // 检查是否为简单文本
            XNode strRefNode = textNode.childByTag("c:strRef");
            if (strRefNode != null) {
                return extractCellReference(strRefNode);
            }
            
            // 直接获取文本内容
            return textNode.getText();
        } catch (Exception e) {
            LOG.warn("Failed to extract text from node: {}", textNode.getTagName(), e);
            return null;
        }
    }
    
    /**
     * 提取单元格引用
     * @param node 包含单元格引用的节点（可以是strRef或numRef节点）
     * @return 单元格引用字符串
     */
    public String extractCellReference(XNode node) {
        if (node == null) return null;
        
        try {
            // 检查c:f子节点（公式引用）
            XNode fNode = node.childByTag("c:f");
            if (fNode != null) {
                return fNode.getText();
            }
            
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to extract cell reference from node", e);
            return null;
        }
    }
    
    /**
     * 从父节点中提取单元格引用（支持strRef和numRef）
     * @param parentNode 父节点，包含strRef或numRef子节点
     * @return 单元格引用字符串
     */
    public String extractCellReferenceFromParent(XNode parentNode) {
        if (parentNode == null) return null;
        
        try {
            // 检查字符串引用
            XNode strRefNode = parentNode.childByTag("c:strRef");
            if (strRefNode != null) {
                return extractCellReference(strRefNode);
            }
            
            // 检查数值引用
            XNode numRefNode = parentNode.childByTag("c:numRef");
            if (numRefNode != null) {
                return extractCellReference(numRefNode);
            }
            
            // 检查多级字符串引用
            XNode multiLvlStrRefNode = parentNode.childByTag("c:multiLvlStrRef");
            if (multiLvlStrRefNode != null) {
                return extractCellReference(multiLvlStrRefNode);
            }
            
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to extract cell reference from parent node", e);
            return null;
        }
    }
    
    /**
     * 提取富文本内容
     * 正确的OOXML结构: <c:rich><a:p><a:r><a:t>text</a:t></a:r></a:p></c:rich>
     * @param richNode 富文本节点
     * @return 提取的文本内容
     */
    public String extractRichText(XNode richNode) {
        if (richNode == null) return null;
        
        try {
            StringBuilder sb = new StringBuilder();
            
            // 遍历所有段落节点 <a:p>，直接获取段落的文本内容
            for (XNode pNode : richNode.getChildren()) {
                if ("a:p".equals(pNode.getTagName())) {
                    String text = pNode.getText();
                    if (!StringHelper.isEmpty(text)) {
                        sb.append(text);
                    }
                }
            }
            
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            LOG.warn("Failed to extract rich text from node", e);
            return null;
        }
    }
    
    /**
     * 检查节点是否包含文本内容
     * @param node 要检查的节点
     * @return 是否包含文本内容
     */
    public boolean hasTextContent(XNode node) {
        if (node == null) return false;
        
        try {
            // 检查直接文本
            if (node.getText() != null && !node.getText().trim().isEmpty()) {
                return true;
            }
            
            // 检查富文本 - 正确的OOXML结构
            XNode richNode = node.childByTag("c:rich");
            if (richNode != null) {
                // 检查是否有段落节点，直接获取段落文本内容
                for (XNode pNode : richNode.getChildren()) {
                    if ("a:p".equals(pNode.getTagName())) {
                        String text = pNode.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            return true;
                        }
                    }
                }
            }
            
            // 检查单元格引用
            if (node.childByTag("c:strRef") != null) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            LOG.warn("Failed to check text content in node: {}", node.getTagName(), e);
            return false;
        }
    }
}