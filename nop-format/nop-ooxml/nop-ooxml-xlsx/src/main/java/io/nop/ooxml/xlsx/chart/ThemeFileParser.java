package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * ThemeFileParser - 主题文件解析器
 * 解析外部主题文件（styles1.xml, colors1.xml）以获取主题颜色和样式定义
 * 支持OOXML主题文件格式，提供主题数据和颜色方案解析
 */
public class ThemeFileParser {
    private static final Logger LOG = LoggerFactory.getLogger(ThemeFileParser.class);
    public static final ThemeFileParser INSTANCE = new ThemeFileParser();
    
    /**
     * 解析样式文件（styles1.xml）
     * @param stylesNode 样式文件根节点
     * @return 解析后的主题数据
     */
    public ThemeData parseStylesFile(XNode stylesNode) {
        if (stylesNode == null) {
            LOG.warn("Styles node is null, returning empty theme data");
            return new ThemeData();
        }
        
        try {
            ThemeData themeData = new ThemeData();
            
            // 解析主题元素
            XNode themeNode = stylesNode.childByTag("a:theme");
            if (themeNode != null) {
                parseThemeElements(themeData, themeNode);
            }
            
            return themeData;
        } catch (Exception e) {
            LOG.warn("Failed to parse styles file", e);
            return new ThemeData(); // 返回空主题数据而不是null
        }
    }
    
    /**
     * 解析颜色文件（colors1.xml）
     * @param colorsNode 颜色文件根节点
     * @return 解析后的颜色方案
     */
    public ColorScheme parseColorsFile(XNode colorsNode) {
        if (colorsNode == null) {
            LOG.warn("Colors node is null, returning empty color scheme");
            return new ColorScheme();
        }
        
        try {
            ColorScheme colorScheme = new ColorScheme();
            
            // 解析颜色方案
            XNode clrSchemeNode = colorsNode.childByTag("a:clrScheme");
            if (clrSchemeNode != null) {
                parseColorScheme(colorScheme, clrSchemeNode);
            }
            
            return colorScheme;
        } catch (Exception e) {
            LOG.warn("Failed to parse colors file", e);
            return new ColorScheme(); // 返回空颜色方案而不是null
        }
    }
    
    /**
     * 解析主题元素
     * @param themeData 主题数据
     * @param themeNode 主题节点
     */
    private void parseThemeElements(ThemeData themeData, XNode themeNode) {
        try {
            // 解析主题元素
            XNode themeElementsNode = themeNode.childByTag("a:themeElements");
            if (themeElementsNode != null) {
                // 解析颜色方案
                XNode clrSchemeNode = themeElementsNode.childByTag("a:clrScheme");
                if (clrSchemeNode != null) {
                    ColorScheme colorScheme = new ColorScheme();
                    parseColorScheme(colorScheme, clrSchemeNode);
                    themeData.setColorScheme(colorScheme);
                }
                
                // 解析字体方案
                XNode fontSchemeNode = themeElementsNode.childByTag("a:fontScheme");
                if (fontSchemeNode != null) {
                    parseFontScheme(themeData, fontSchemeNode);
                }
                
                // 解析格式方案
                XNode fmtSchemeNode = themeElementsNode.childByTag("a:fmtScheme");
                if (fmtSchemeNode != null) {
                    parseFormatScheme(themeData, fmtSchemeNode);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse theme elements", e);
        }
    }
    
    /**
     * 解析颜色方案
     * @param colorScheme 颜色方案
     * @param clrSchemeNode 颜色方案节点
     */
    private void parseColorScheme(ColorScheme colorScheme, XNode clrSchemeNode) {
        try {
            // 解析方案名称
            String schemeName = clrSchemeNode.attrText("name");
            if (!StringHelper.isEmpty(schemeName)) {
                colorScheme.setName(schemeName);
            }
            
            // 解析各种主题颜色
            parseThemeColor(colorScheme, clrSchemeNode, "dk1", "dark1");
            parseThemeColor(colorScheme, clrSchemeNode, "lt1", "light1");
            parseThemeColor(colorScheme, clrSchemeNode, "dk2", "dark2");
            parseThemeColor(colorScheme, clrSchemeNode, "lt2", "light2");
            parseThemeColor(colorScheme, clrSchemeNode, "accent1", "accent1");
            parseThemeColor(colorScheme, clrSchemeNode, "accent2", "accent2");
            parseThemeColor(colorScheme, clrSchemeNode, "accent3", "accent3");
            parseThemeColor(colorScheme, clrSchemeNode, "accent4", "accent4");
            parseThemeColor(colorScheme, clrSchemeNode, "accent5", "accent5");
            parseThemeColor(colorScheme, clrSchemeNode, "accent6", "accent6");
            parseThemeColor(colorScheme, clrSchemeNode, "hlink", "hyperlink");
            parseThemeColor(colorScheme, clrSchemeNode, "folHlink", "followedHyperlink");
            
            // 解析样本中发现的额外颜色
            parseThemeColor(colorScheme, clrSchemeNode, "tx1", "text1");
            parseThemeColor(colorScheme, clrSchemeNode, "tx2", "text2");
            parseThemeColor(colorScheme, clrSchemeNode, "bg1", "background1");
            parseThemeColor(colorScheme, clrSchemeNode, "bg2", "background2");
            
        } catch (Exception e) {
            LOG.warn("Failed to parse color scheme", e);
        }
    }
    
    /**
     * 解析单个主题颜色
     * @param colorScheme 颜色方案
     * @param clrSchemeNode 颜色方案节点
     * @param colorName 颜色名称
     * @param mappingKey 映射键
     */
    private void parseThemeColor(ColorScheme colorScheme, XNode clrSchemeNode, String colorName, String mappingKey) {
        try {
            XNode colorNode = clrSchemeNode.childByTag("a:" + colorName);
            if (colorNode != null) {
                String colorValue = extractColorValue(colorNode);
                if (!StringHelper.isEmpty(colorValue)) {
                    colorScheme.addColor(mappingKey, colorValue);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse theme color {}", colorName, e);
        }
    }
    
    /**
     * 提取颜色值
     * @param colorNode 颜色节点
     * @return 颜色值（十六进制格式）
     */
    private String extractColorValue(XNode colorNode) {
        try {
            // 解析srgbClr颜色
            String val = ChartPropertyHelper.getChildVal(colorNode, "a:srgbClr");
            if (!StringHelper.isEmpty(val)) {
                return "#" + val.toUpperCase();
            }
            
            // 解析sysClr颜色
            XNode sysClrNode = colorNode.childByTag("a:sysClr");
            if (sysClrNode != null) {
                String lastClr = sysClrNode.attrText("lastClr");
                if (!StringHelper.isEmpty(lastClr)) {
                    return "#" + lastClr.toUpperCase();
                }
                
                // 处理系统颜色名称
                String sysVal = sysClrNode.attrText("val");
                if (!StringHelper.isEmpty(sysVal)) {
                    return mapSystemColor(sysVal);
                }
            }
            
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to extract color value", e);
            return null;
        }
    }
    
    /**
     * 映射系统颜色
     * @param systemColorName 系统颜色名称
     * @return 对应的十六进制颜色值
     */
    private String mapSystemColor(String systemColorName) {
        switch (systemColorName) {
            case "windowText": return "#000000";
            case "window": return "#FFFFFF";
            case "btnText": return "#000000";
            case "btnFace": return "#F0F0F0";
            case "captionText": return "#000000";
            case "activeBorder": return "#B4B4B4";
            case "inactiveBorder": return "#F4F7FC";
            default:
                LOG.warn("Unknown system color: {}, using default black", systemColorName);
                return "#000000";
        }
    }
    
    /**
     * 解析字体方案
     * @param themeData 主题数据
     * @param fontSchemeNode 字体方案节点
     */
    private void parseFontScheme(ThemeData themeData, XNode fontSchemeNode) {
        try {
            // 解析主要字体
            XNode majorFontNode = fontSchemeNode.childByTag("a:majorFont");
            if (majorFontNode != null) {
                String majorFont = extractFontName(majorFontNode);
                if (!StringHelper.isEmpty(majorFont)) {
                    themeData.setMajorFont(majorFont);
                }
            }
            
            // 解析次要字体
            XNode minorFontNode = fontSchemeNode.childByTag("a:minorFont");
            if (minorFontNode != null) {
                String minorFont = extractFontName(minorFontNode);
                if (!StringHelper.isEmpty(minorFont)) {
                    themeData.setMinorFont(minorFont);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse font scheme", e);
        }
    }
    
    /**
     * 提取字体名称
     * @param fontNode 字体节点
     * @return 字体名称
     */
    private String extractFontName(XNode fontNode) {
        try {
            XNode latinNode = fontNode.childByTag("a:latin");
            if (latinNode != null) {
                return latinNode.attrText("typeface");
            }
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to extract font name", e);
            return null;
        }
    }
    
    /**
     * 解析格式方案
     * @param themeData 主题数据
     * @param fmtSchemeNode 格式方案节点
     */
    private void parseFormatScheme(ThemeData themeData, XNode fmtSchemeNode) {
        try {
            // 这里可以扩展解析格式方案的具体实现
            // 目前简化处理，只记录方案名称
            String schemeName = fmtSchemeNode.attrText("name");
            if (!StringHelper.isEmpty(schemeName)) {
                themeData.setFormatSchemeName(schemeName);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse format scheme", e);
        }
    }
    
    /**
     * 主题数据类
     */
    public static class ThemeData {
        private ColorScheme colorScheme;
        private String majorFont;
        private String minorFont;
        private String formatSchemeName;
        
        public ThemeData() {
            this.colorScheme = new ColorScheme();
        }
        
        // Getters and setters
        public ColorScheme getColorScheme() { return colorScheme; }
        public void setColorScheme(ColorScheme colorScheme) { this.colorScheme = colorScheme; }
        
        public String getMajorFont() { return majorFont; }
        public void setMajorFont(String majorFont) { this.majorFont = majorFont; }
        
        public String getMinorFont() { return minorFont; }
        public void setMinorFont(String minorFont) { this.minorFont = minorFont; }
        
        public String getFormatSchemeName() { return formatSchemeName; }
        public void setFormatSchemeName(String formatSchemeName) { this.formatSchemeName = formatSchemeName; }
    }
    
    /**
     * 颜色方案类
     */
    public static class ColorScheme {
        private String name;
        private Map<String, String> colors;
        
        public ColorScheme() {
            this.colors = new HashMap<>();
        }
        
        public void addColor(String key, String value) {
            colors.put(key, value);
        }
        
        public String getColor(String key) {
            return colors.get(key);
        }
        
        public Map<String, String> getAllColors() {
            return new HashMap<>(colors);
        }
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public Map<String, String> getColors() { return colors; }
        public void setColors(Map<String, String> colors) { this.colors = colors; }
    }
}