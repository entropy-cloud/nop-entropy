/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartGradientModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartStyleModel;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.color.ColorHelper;
import io.nop.excel.model.constants.ExcelFontUnderline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parser for chart styling and color scheme information.
 * Handles color schemes, gradients, fonts, and other styling elements.
 */
public class ChartStyleParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartStyleParser.class);
    
    /**
     * Singleton instance for reuse across multiple parsing operations.
     */
    public static final ChartStyleParser INSTANCE = new ChartStyleParser();
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private ChartStyleParser() {
        // Private constructor for singleton
    }
    
    /**
     * Parses color scheme information from chart XML and applies it to the chart style model.
     * This method ensures all color information is properly set in the ChartStyleModel including
     * theme colors, color palette, and color variations with complete model population.
     */
    public void parseColorScheme(ChartModel chart, XNode chartSpaceNode) {
        LOG.debug("ChartStyleParser.parseColorScheme: parsing color scheme information");
        
        if (chart == null || chartSpaceNode == null) {
            LOG.warn("ChartStyleParser.parseColorScheme: null input parameters");
            return;
        }
        
        try {
            ChartStyleModel styleModel = chart.getStyle();
            if (styleModel == null) {
                styleModel = new ChartStyleModel();
                chart.setStyle(styleModel);
                LOG.debug("ChartStyleParser.parseColorScheme: created new ChartStyleModel");
            }
            
            boolean hasColorData = false;
            
            // Parse theme information and set in model
            XNode themeNode = chartSpaceNode.childByTag("c:theme");
            if (themeNode != null) {
                String themeId = themeNode.attrText("r:id");
                if (themeId != null && !themeId.trim().isEmpty()) {
                    ChartPropertyHelper.setModelPropertySafely(styleModel, "theme", themeId, String.class);
                    hasColorData = true;
                    LOG.debug("ChartStyleParser.parseColorScheme: set theme ID: {}", themeId);
                }
            }
            
            // Parse color scheme from theme override and apply to model
            XNode themeOverrideNode = chartSpaceNode.childByTag("c:themeOverride");
            if (themeOverrideNode != null) {
                boolean themeOverrideApplied = parseColorSchemeFromThemeOverride(styleModel, themeOverrideNode);
                hasColorData = hasColorData || themeOverrideApplied;
            }
            
            // Parse color scheme from chart style elements and apply to model
            XNode styleNode = chartSpaceNode.childByTag("c:style");
            if (styleNode != null) {
                boolean styleApplied = parseColorSchemeFromStyle(styleModel, styleNode);
                hasColorData = hasColorData || styleApplied;
            }
            
            // Parse color variations from color map override and apply to model
            XNode colorMapOvrNode = chartSpaceNode.childByTag("c:colorMapOvr");
            if (colorMapOvrNode != null) {
                boolean colorMapApplied = parseColorMapOverride(styleModel, colorMapOvrNode);
                hasColorData = hasColorData || colorMapApplied;
            }
            
            // Parse additional color scheme elements from chart space
            boolean additionalColorsApplied = parseAdditionalColorSchemeElements(styleModel, chartSpaceNode);
            hasColorData = hasColorData || additionalColorsApplied;
            
            // Set default color palette if none found, ensuring model always has colors
            if (styleModel.getColors() == null || styleModel.getColors().isEmpty()) {
                setDefaultColorPalette(styleModel);
                hasColorData = true;
                LOG.debug("ChartStyleParser.parseColorScheme: applied default color palette to model");
            }
            
            // Apply resolved colors to chart model for immediate use
            if (hasColorData) {
                applyResolvedColorsToChart(chart, styleModel);
            }
            
            LOG.info("ChartStyleParser.parseColorScheme: successfully applied color scheme to model - theme: {}, colors: {}, palette: {}", 
                styleModel.getTheme(), 
                styleModel.getColors() != null ? styleModel.getColors().size() : 0,
                styleModel.getColorPalette());
            
        } catch (Exception e) {
            LOG.error("ChartStyleParser.parseColorScheme: error parsing color scheme", e);
        }
    }
    
    /**
     * Parses color from fill node elements.
     */
    public String parseColorFromFillNode(XNode fillNode) {
        if (fillNode == null) {
            return null;
        }
        
        String baseColor = null;
        XNode colorNode = null;
        
        // Look for solid fill first
        XNode solidFillNode = fillNode.childByTag("a:solidFill");
        if (solidFillNode != null) {
            // Look for different color types in solid fill
            XNode srgbClrNode = solidFillNode.childByTag("a:srgbClr");
            if (srgbClrNode != null) {
                String srgbVal = srgbClrNode.attrText("val");
                if (srgbVal != null) {
                    baseColor = ColorHelper.toCssColor(srgbVal);
                    colorNode = srgbClrNode;
                    LOG.debug("ChartStyleParser.parseColorFromFillNode: found sRGB color: {}", baseColor);
                }
            }
            
            XNode schemeClrNode = solidFillNode.childByTag("a:schemeClr");
            if (schemeClrNode != null && baseColor == null) {
                String schemeVal = schemeClrNode.attrText("val");
                if (schemeVal != null) {
                    baseColor = mapSchemeColor(schemeVal);
                    colorNode = schemeClrNode;
                    LOG.debug("ChartStyleParser.parseColorFromFillNode: mapped scheme color {} to {}", schemeVal, baseColor);
                }
            }
            
            XNode sysClrNode = solidFillNode.childByTag("a:sysClr");
            if (sysClrNode != null && baseColor == null) {
                String sysVal = sysClrNode.attrText("val");
                String lastClr = sysClrNode.attrText("lastClr");
                if (lastClr != null) {
                    baseColor = ColorHelper.toCssColor(lastClr);
                    colorNode = sysClrNode;
                    LOG.debug("ChartStyleParser.parseColorFromFillNode: found system color with lastClr: {}", baseColor);
                } else if (sysVal != null) {
                    baseColor = mapSystemColor(sysVal);
                    colorNode = sysClrNode;
                    LOG.debug("ChartStyleParser.parseColorFromFillNode: mapped system color {} to {}", sysVal, baseColor);
                }
            }
        }
        
        // Apply color modifications if we have a base color
        if (baseColor != null && colorNode != null) {
            String modifiedColor = applyColorModifications(baseColor, colorNode);
            if (modifiedColor != null) {
                baseColor = modifiedColor;
                LOG.debug("ChartStyleParser.parseColorFromFillNode: applied color modifications, result: {}", baseColor);
            }
        }
        
        return baseColor;
    }
    
    /**
     * Parses gradient fill information and creates gradient models.
     */
    public ChartGradientModel parseGradientFill(XNode gradFillNode) {
        if (gradFillNode == null) {
            return null;
        }
        
        LOG.debug("ChartStyleParser.parseGradientFill: parsing gradient fill");
        
        try {
            ChartGradientModel gradient = new ChartGradientModel();
            
            // Parse gradient stops
            List<String> colors = new ArrayList<>();
            List<Double> positions = new ArrayList<>();
            
            for (XNode gsNode : gradFillNode.childrenByTag("a:gs")) {
                Long pos = gsNode.attrLong("pos");
                if (pos != null) {
                    // Position is in percentage (0-100000, where 100000 = 100%)
                    double position = ColorModificationHelper.ooxmlPercentageToDouble(pos);
                    positions.add(position);
                    
                    // Parse color for this stop
                    String color = parseColorFromFillNode(gsNode);
                    colors.add(color != null ? color : "#000000");
                    
                    LOG.debug("ChartStyleParser.parseGradientFill: added gradient stop at {} with color {}", position, color);
                }
            }
            
            // Set gradient properties
            if (!colors.isEmpty()) {
                gradient.setStartColor(colors.get(0));
                if (colors.size() > 1) {
                    gradient.setEndColor(colors.get(colors.size() - 1));
                }
                LOG.debug("ChartStyleParser.parseGradientFill: created gradient with {} colors", colors.size());
            }
            
            // Parse gradient direction/type
            XNode linNode = gradFillNode.childByTag("a:lin");
            if (linNode != null) {
                Long ang = linNode.attrLong("ang");
                if (ang != null) {
                    // Angle is in 60000ths of a degree
                    double angle = ColorModificationHelper.ooxmlAngleToDegrees(ang);
                    // Store angle information if model supports it
                    LOG.debug("ChartStyleParser.parseGradientFill: gradient angle: {} degrees", angle);
                }
            }
            
            return gradient;
            
        } catch (Exception e) {
            LOG.error("ChartStyleParser.parseGradientFill: error parsing gradient fill", e);
            return null;
        }
    }
    
    /**
     * Parses font properties from text property nodes.
     */
    public ExcelFont parseFontProperties(XNode txPrNode) {
        if (txPrNode == null) {
            return null;
        }
        
        LOG.debug("ChartStyleParser.parseFontProperties: parsing font properties");
        
        try {
            ExcelFont font = new ExcelFont();
            boolean hasFontData = false;
            
            // Parse default run properties
            XNode defRPrNode = txPrNode.childByTag("a:defRPr");
            if (defRPrNode != null) {
                hasFontData |= parseFontFromRunProperties(font, defRPrNode);
            }
            
            // Parse paragraph properties for additional font info
            XNode pNode = txPrNode.childByTag("a:p");
            if (pNode != null) {
                XNode rNode = pNode.childByTag("a:r");
                if (rNode != null) {
                    XNode rPrNode = rNode.childByTag("a:rPr");
                    if (rPrNode != null) {
                        hasFontData |= parseFontFromRunProperties(font, rPrNode);
                    }
                }
            }
            
            return hasFontData ? font : null;
            
        } catch (Exception e) {
            LOG.warn("ChartStyleParser.parseFontProperties: error parsing font properties", e);
            return null;
        }
    }
    
    // Private helper methods
    
    private boolean parseColorSchemeFromThemeOverride(ChartStyleModel styleModel, XNode themeOverrideNode) {
        XNode themeNode = themeOverrideNode.childByTag("a:theme");
        if (themeNode != null) {
            XNode themeElementsNode = themeNode.childByTag("a:themeElements");
            if (themeElementsNode != null) {
                XNode clrSchemeNode = themeElementsNode.childByTag("a:clrScheme");
                if (clrSchemeNode != null) {
                    return parseColorSchemeElements(styleModel, clrSchemeNode);
                }
            }
        }
        return false;
    }
    
    private boolean parseColorSchemeFromStyle(ChartStyleModel styleModel, XNode styleNode) {
        String styleVal = styleNode.attrText("val");
        if (styleVal != null) {
            String colorPalette = mapStyleToColorPalette(styleVal);
            if (colorPalette != null) {
                ChartPropertyHelper.setModelPropertySafely(styleModel, "colorPalette", colorPalette, String.class);
                LOG.debug("ChartStyleParser.parseColorSchemeFromStyle: set color palette: {}", colorPalette);
                return true;
            }
        }
        return false;
    }
    
    private boolean parseColorMapOverride(ChartStyleModel styleModel, XNode colorMapOvrNode) {
        boolean hasOverrides = false;
        for (XNode child : colorMapOvrNode.getChildren()) {
            String tagName = child.getTagName();
            if (tagName.startsWith("a:") && tagName.endsWith("Clr")) {
                String colorName = tagName.substring(2, tagName.length() - 3);
                String colorValue = parseColorFromFillNode(child);
                if (colorValue != null) {
                    applyColorOverride(styleModel, colorName, colorValue);
                    hasOverrides = true;
                }
            }
        }
        return hasOverrides;
    }
    
    private boolean parseColorSchemeElements(ChartStyleModel styleModel, XNode clrSchemeNode) {
        List<String> colors = new ArrayList<>();
        
        String[] standardColors = {
            "dk1", "lt1", "dk2", "lt2", "accent1", "accent2", 
            "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink"
        };
        
        for (String colorName : standardColors) {
            XNode colorNode = clrSchemeNode.childByTag("a:" + colorName);
            if (colorNode != null) {
                String colorValue = parseColorFromColorSchemeElement(colorNode);
                if (colorValue != null) {
                    colors.add(colorValue);
                }
            }
        }
        
        if (!colors.isEmpty()) {
            ChartPropertyHelper.setModelPropertySafely(styleModel, "colors", colors, List.class);
            LOG.debug("ChartStyleParser.parseColorSchemeElements: set {} colors in model", colors.size());
            return true;
        }
        
        return false;
    }
    
    /**
     * Parses additional color scheme elements that may be present in chart space.
     * This method looks for color definitions in various chart space child elements
     * and applies them to the style model.
     */
    private boolean parseAdditionalColorSchemeElements(ChartStyleModel styleModel, XNode chartSpaceNode) {
        boolean hasAdditionalColors = false;
        
        try {
            // Parse color definitions from chart space level elements
            for (XNode child : chartSpaceNode.getChildren()) {
                String tagName = child.getTagName();
                
                // Look for color definition elements
                if (tagName.contains("clr") || tagName.contains("Color")) {
                    String colorValue = parseColorFromFillNode(child);
                    if (colorValue != null) {
                        // Add to existing colors or create new list
                        List<String> existingColors = styleModel.getColors();
                        if (existingColors == null) {
                            existingColors = new ArrayList<>();
                        } else {
                            existingColors = new ArrayList<>(existingColors); // Create mutable copy
                        }
                        
                        if (!existingColors.contains(colorValue)) {
                            existingColors.add(colorValue);
                            ChartPropertyHelper.setModelPropertySafely(styleModel, "colors", existingColors, List.class);
                            hasAdditionalColors = true;
                            LOG.debug("ChartStyleParser.parseAdditionalColorSchemeElements: added color: {}", colorValue);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            LOG.warn("ChartStyleParser.parseAdditionalColorSchemeElements: error parsing additional colors", e);
        }
        
        return hasAdditionalColors;
    }
    
    /**
     * Applies resolved colors from the style model to the chart for immediate use.
     * This method ensures that color information is available at the chart level
     * for components that need direct access to color data.
     */
    private void applyResolvedColorsToChart(ChartModel chart, ChartStyleModel styleModel) {
        try {
            // Apply color palette name to chart if supported
            String colorPalette = styleModel.getColorPalette();
            if (colorPalette != null) {
                ChartPropertyHelper.setModelPropertySafely(chart, "colorPalette", colorPalette, String.class);
                LOG.debug("ChartStyleParser.applyResolvedColorsToChart: applied color palette '{}' to chart", colorPalette);
            }
            
            // Apply theme name to chart if supported
            String theme = styleModel.getTheme();
            if (theme != null) {
                ChartPropertyHelper.setModelPropertySafely(chart, "theme", theme, String.class);
                LOG.debug("ChartStyleParser.applyResolvedColorsToChart: applied theme '{}' to chart", theme);
            }
            
            // Apply color list to chart if supported
            List<String> colors = styleModel.getColors();
            if (colors != null && !colors.isEmpty()) {
                ChartPropertyHelper.setModelPropertySafely(chart, "colors", colors, List.class);
                LOG.debug("ChartStyleParser.applyResolvedColorsToChart: applied {} colors to chart", colors.size());
            }
            
        } catch (Exception e) {
            LOG.warn("ChartStyleParser.applyResolvedColorsToChart: error applying colors to chart", e);
        }
    }
    
    private String parseColorFromColorSchemeElement(XNode colorNode) {
        if (colorNode == null) return null;
        
        XNode sysClrNode = colorNode.childByTag("a:sysClr");
        if (sysClrNode != null) {
            String lastClr = sysClrNode.attrText("lastClr");
            if (lastClr != null) {
                return ColorHelper.toCssColor(lastClr);
            }
            String sysClrVal = sysClrNode.attrText("val");
            if (sysClrVal != null) {
                return mapSystemColor(sysClrVal);
            }
        }
        
        XNode srgbClrNode = colorNode.childByTag("a:srgbClr");
        if (srgbClrNode != null) {
            String srgbVal = srgbClrNode.attrText("val");
            if (srgbVal != null) {
                return ColorHelper.toCssColor(srgbVal);
            }
        }
        
        XNode schemeClrNode = colorNode.childByTag("a:schemeClr");
        if (schemeClrNode != null) {
            String schemeVal = schemeClrNode.attrText("val");
            if (schemeVal != null) {
                return mapSchemeColor(schemeVal);
            }
        }
        
        return null;
    }
    
    private String mapStyleToColorPalette(String styleId) {
        if (styleId == null) return null;
        
        try {
            int styleNum = Integer.parseInt(styleId);
            switch (styleNum) {
                case 1: return "Office";
                case 2: return "Grayscale";
                case 3: return "Apex";
                case 4: return "Aspect";
                case 5: return "Civic";
                case 6: return "Concourse";
                case 7: return "Equity";
                case 8: return "Flow";
                case 9: return "Foundry";
                case 10: return "Median";
                case 11: return "Metro";
                case 12: return "Module";
                case 13: return "Opulent";
                case 14: return "Oriel";
                case 15: return "Origin";
                case 16: return "Paper";
                case 17: return "Solstice";
                case 18: return "Technic";
                case 19: return "Trek";
                case 20: return "Urban";
                case 21: return "Verve";
                default: return "Office";
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private void applyColorOverride(ChartStyleModel styleModel, String colorName, String colorValue) {
        List<String> colors = styleModel.getColors();
        if (colors == null) {
            colors = new ArrayList<>();
        } else {
            colors = new ArrayList<>(colors); // Create mutable copy
        }
        
        int colorIndex = getColorSchemeIndex(colorName);
        if (colorIndex >= 0) {
            while (colors.size() <= colorIndex) {
                colors.add("#000000");
            }
            String normalizedColor = ColorHelper.toCssColor(colorValue);
            if (normalizedColor != null) {
                colors.set(colorIndex, normalizedColor);
                ChartPropertyHelper.setModelPropertySafely(styleModel, "colors", colors, List.class);
                LOG.debug("ChartStyleParser.applyColorOverride: applied color override for '{}' at index {}: {}", 
                    colorName, colorIndex, normalizedColor);
            }
        }
    }
    
    private int getColorSchemeIndex(String colorName) {
        switch (colorName.toLowerCase()) {
            case "dk1": return 0;
            case "lt1": return 1;
            case "dk2": return 2;
            case "lt2": return 3;
            case "accent1": return 4;
            case "accent2": return 5;
            case "accent3": return 6;
            case "accent4": return 7;
            case "accent5": return 8;
            case "accent6": return 9;
            case "hlink": return 10;
            case "folhlink": return 11;
            default: return -1;
        }
    }
    
    private void setDefaultColorPalette(ChartStyleModel styleModel) {
        List<String> defaultColors = Arrays.asList(
            "#000000", "#FFFFFF", "#1F497D", "#EEECE1", "#4F81BD", "#F79646",
            "#9CBB58", "#8064A2", "#4BACC6", "#F24C4C", "#0000FF", "#800080"
        );
        
        ChartPropertyHelper.setModelPropertySafely(styleModel, "colors", defaultColors, List.class);
        ChartPropertyHelper.setModelPropertySafely(styleModel, "colorPalette", "Office", String.class);
        ChartPropertyHelper.setModelPropertySafely(styleModel, "theme", "Office", String.class);
        
        LOG.debug("ChartStyleParser.setDefaultColorPalette: applied default Office color palette with {} colors", 
            defaultColors.size());
    }
    
    private String mapSchemeColor(String schemeColor) {
        if (schemeColor == null) return "#000000";
        
        switch (schemeColor) {
            case "accent1": return "#4F81BD";
            case "accent2": return "#F79646";
            case "accent3": return "#9CBB58";
            case "accent4": return "#8064A2";
            case "accent5": return "#4BACC6";
            case "accent6": return "#F24C4C";
            case "bg1":
            case "lt1": return "#FFFFFF";
            case "bg2":
            case "lt2": return "#EEECE1";
            case "tx1":
            case "dk1": return "#000000";
            case "tx2":
            case "dk2": return "#44546A";
            case "hlink": return "#0000FF";
            case "folHlink": return "#800080";
            default: return "#000000";
        }
    }
    
    private String mapSystemColor(String systemColor) {
        if (systemColor == null) return "#000000";
        
        switch (systemColor) {
            case "windowtext": return "#000000";
            case "window": return "#FFFFFF";
            case "btnface": return "#F0F0F0";
            case "btntext": return "#000000";
            case "btnshadow": return "#A0A0A0";
            case "menu": return "#F0F0F0";
            case "menutext": return "#000000";
            case "highlight": return "#0078D4";
            case "highlighttext": return "#FFFFFF";
            case "infotext": return "#000000";
            case "infobk": return "#FFFFE1";
            default: return "#000000";
        }
    }
    
    private String applyColorModifications(String baseColor, XNode colorNode) {
        return ColorModificationHelper.applyColorModifications(baseColor, colorNode);
    }
    
    private int[] parseHexColor(String hexColor) {
        if (hexColor == null) {
            return new int[]{0, 0, 0};
        }
        
        try {
            float[] normalizedRgb = ColorHelper.toNormalizedRgb(hexColor);
            return new int[]{
                Math.round(normalizedRgb[0] * 255),
                Math.round(normalizedRgb[1] * 255),
                Math.round(normalizedRgb[2] * 255)
            };
        } catch (Exception e) {
            LOG.warn("ChartStyleParser.parseHexColor: error parsing hex color '{}': {}", hexColor, e.getMessage());
            return new int[]{0, 0, 0};
        }
    }
    
    private boolean parseFontFromRunProperties(ExcelFont font, XNode rPrNode) {
        boolean hasFontData = false;
        
        // Parse font size
        Long sz = rPrNode.attrLong("sz");
        if (sz != null) {
            double fontSize = sz / 100.0; // Convert from points * 100 to points
            font.setFontSize((float) fontSize);
            hasFontData = true;
        }
        
        // Parse bold
        Boolean bold = rPrNode.attrBoolean("b");
        if (bold != null) {
            font.setBold(bold);
            hasFontData = true;
        }
        
        // Parse italic
        Boolean italic = rPrNode.attrBoolean("i");
        if (italic != null) {
            font.setItalic(italic);
            hasFontData = true;
        }
        
        // Parse underline
        String underline = rPrNode.attrText("u");
        if (underline != null) {
            font.setUnderlineStyle("none".equals(underline) ? ExcelFontUnderline.NONE : ExcelFontUnderline.SINGLE);
            hasFontData = true;
        }
        
        // Parse font family
        XNode latinNode = rPrNode.childByTag("a:latin");
        if (latinNode != null) {
            String typeface = latinNode.attrText("typeface");
            if (typeface != null) {
                font.setFontName(typeface);
                hasFontData = true;
            }
        }
        
        // Parse font color
        XNode solidFillNode = rPrNode.childByTag("a:solidFill");
        if (solidFillNode != null) {
            String color = parseColorFromFillNode(rPrNode);
            if (color != null) {
                font.setFontColor(color);
                hasFontData = true;
            }
        }
        
        return hasFontData;
    }
}