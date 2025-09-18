package io.nop.ooxml.docx.model;

import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WordStylesPart extends XmlOfficePackagePart {
    private final Map<String, StyleInfo> styles = new HashMap<>();

    public WordStylesPart(String path, XNode node) {
        super(path, node);
        parse();
    }

    protected void parse() {
        // 第一步：解析所有样式的基础信息
        for (XNode child : this.getNode().getChildren()) {
            String tagName = child.getTagName();
            if (tagName.equals("w:style")) {
                String styleId = child.attrText("w:styleId");
                if (styleId != null && !styleId.isEmpty()) {
                    styles.put(styleId, parseStyleInfo(styleId, child));
                }
            }
        }

        // 第二步：处理样式继承关系
        for (StyleInfo styleInfo : styles.values()) {
            resolveInheritance(styleInfo, new HashSet<>());
        }
    }

    private StyleInfo parseStyleInfo(String styleId, XNode styleNode) {
        StyleInfo info = new StyleInfo();
        info.styleId = styleId;
        info.styleType = styleNode.attrText("w:type");

        // 解析样式名称
        XNode nameNode = styleNode.childByTag("w:name");
        if (nameNode != null) {
            info.styleName = nameNode.attrText("w:val");
        }

        // 解析基础样式
        XNode basedOnNode = styleNode.childByTag("w:basedOn");
        if (basedOnNode != null) {
            info.basedOnStyleId = basedOnNode.attrText("w:val");
        }

        // 解析段落属性
        XNode pprNode = styleNode.childByTag("w:pPr");
        if (pprNode != null) {
            parseParaProps(pprNode, info);
        }

        // 解析字符属性
        XNode rprNode = styleNode.childByTag("w:rPr");
        if (rprNode != null) {
            parseRunProps(rprNode, info);
        }

        return info;
    }

    private void parseParaProps(XNode pprNode, StyleInfo info) {
        // 大纲级别
        XNode outlineLvlNode = pprNode.childByTag("w:outlineLvl");
        if (outlineLvlNode != null) {
            String val = outlineLvlNode.attrText("w:val");
            if (val != null) {
                try {
                    info.outlineLevel = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }

        // 段落对齐
        XNode jcNode = pprNode.childByTag("w:jc");
        if (jcNode != null) {
            info.alignment = jcNode.attrText("w:val");
        }

        // 段落间距
        XNode spacingNode = pprNode.childByTag("w:spacing");
        if (spacingNode != null) {
            String before = spacingNode.attrText("w:before");
            String after = spacingNode.attrText("w:after");
            if (before != null) {
                try {
                    info.spaceBefore = Integer.parseInt(before);
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            if (after != null) {
                try {
                    info.spaceAfter = Integer.parseInt(after);
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }
    }

    private void parseRunProps(XNode rprNode, StyleInfo info) {
        // 是否加粗
        info.bold = rprNode.childByTag("w:b") != null;

        // 是否斜体
        info.italic = rprNode.childByTag("w:i") != null;

        // 字体大小
        XNode szNode = rprNode.childByTag("w:sz");
        if (szNode != null) {
            String val = szNode.attrText("w:val");
            if (val != null) {
                try {
                    info.fontSize = Integer.parseInt(val) / 2; // 转换为磅值
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }

        // 字体颜色
        XNode colorNode = rprNode.childByTag("w:color");
        if (colorNode != null) {
            info.color = colorNode.attrText("w:val");
        }

        // 字体名称
        XNode rFontsNode = rprNode.childByTag("w:rFonts");
        if (rFontsNode != null) {
            String ascii = rFontsNode.attrText("w:ascii");
            String eastAsia = rFontsNode.attrText("w:eastAsia");
            if (ascii != null) info.fontAscii = ascii;
            if (eastAsia != null) info.fontEastAsia = eastAsia;
        }
    }

    private void resolveInheritance(StyleInfo styleInfo, Set<String> visited) {
        if (styleInfo.basedOnStyleId == null || visited.contains(styleInfo.styleId)) {
            return;
        }

        visited.add(styleInfo.styleId);
        StyleInfo baseStyle = styles.get(styleInfo.basedOnStyleId);
        if (baseStyle != null) {
            // 先解析基础样式的继承
            resolveInheritance(baseStyle, visited);
            // 然后继承基础样式的属性
            styleInfo.inherit(baseStyle);
        }
    }

    /**
     * 获取指定样式的信息
     */
    public StyleInfo getStyleInfo(String styleId) {
        return styles.get(styleId);
    }

    /**
     * 判断是否为标题样式 - 简化版：只检查大纲级别
     */
    public boolean isHeadingStyle(String styleId) {
        StyleInfo styleInfo = getStyleInfo(styleId);
        return styleInfo != null && styleInfo.outlineLevel != null;
    }

    /**
     * 获取标题级别 - 简化版：只基于大纲级别
     */
    public int getHeadingLevel(String styleId) {
        StyleInfo styleInfo = getStyleInfo(styleId);
        if (styleInfo == null || styleInfo.outlineLevel == null) {
            return 0;
        }
        return Math.min(styleInfo.outlineLevel + 1, 6); // 转换为1-6级标题
    }

    /**
     * 获取样式的显示名称
     */
    public String getStyleName(String styleId) {
        StyleInfo styleInfo = getStyleInfo(styleId);
        if (styleInfo != null && styleInfo.styleName != null && !styleInfo.styleName.isEmpty()) {
            return styleInfo.styleName;
        }
        return styleId;
    }

    /**
     * 样式信息类
     */
    public static class StyleInfo {
        public String styleId;
        public String styleName;
        public String styleType;
        public String basedOnStyleId;
        public Integer outlineLevel;
        public Boolean bold;
        public Boolean italic;
        public Integer fontSize;
        public String color;
        public String fontAscii;
        public String fontEastAsia;
        public String alignment;
        public Integer spaceBefore;
        public Integer spaceAfter;

        public void inherit(StyleInfo baseInfo) {
            if (outlineLevel == null) outlineLevel = baseInfo.outlineLevel;
            if (bold == null) bold = baseInfo.bold;
            if (italic == null) italic = baseInfo.italic;
            if (fontSize == null) fontSize = baseInfo.fontSize;
            if (color == null) color = baseInfo.color;
            if (fontAscii == null) fontAscii = baseInfo.fontAscii;
            if (fontEastAsia == null) fontEastAsia = baseInfo.fontEastAsia;
            if (alignment == null) alignment = baseInfo.alignment;
            if (spaceBefore == null) spaceBefore = baseInfo.spaceBefore;
            if (spaceAfter == null) spaceAfter = baseInfo.spaceAfter;
        }

        public int getHeadingLevel() {
            if (outlineLevel != null) {
                return Math.min(outlineLevel + 1, 6);
            }
            return 0;
        }

        public boolean isHeading() {
            return outlineLevel != null;
        }

        @Override
        public String toString() {
            return String.format("StyleInfo{id='%s', name='%s', level=%d, bold=%s, fontSize=%d}",
                    styleId, styleName, getHeadingLevel(), bold, fontSize);
        }
    }

    /**
     * 获取所有样式ID
     */
    public Set<String> getAllStyleIds() {
        return new HashSet<>(styles.keySet());
    }

    /**
     * 获取所有标题样式ID
     */
    public Set<String> getHeadingStyleIds() {
        return styles.entrySet().stream()
                .filter(entry -> entry.getValue().outlineLevel != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * 打印所有样式信息（调试用）
     */
    public void printAllStyles() {
        for (StyleInfo styleInfo : styles.values()) {
            System.out.println(styleInfo);
        }
    }
}