/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.model.color.ColorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for chart legend elements following the Parser-Builder pattern.
 * Generates OOXML chart legend structures from ChartLegendModel objects.
 */
public class ChartLegendBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartLegendBuilder.class);
    
    /**
     * Singleton instance for reuse across multiple building operations.
     */
    public static final ChartLegendBuilder INSTANCE = new ChartLegendBuilder();
    
    private ChartLegendBuilder() {
        // Private constructor for singleton
    }
    
    /**
     * Builds chart legend XML element from ChartLegendModel.
     */
    public XNode buildLegend(ChartLegendModel legend) {
        if (legend == null || !legend.getVisible()) {
            LOG.debug("ChartLegendBuilder.buildLegend: legend is null or not visible");
            return null;
        }
        
        LOG.debug("ChartLegendBuilder.buildLegend: building chart legend at position: {}", legend.getPosition());
        
        XNode legendNode = XNode.make("c:legend");
        
        // Build legend position
        if (legend.getPosition() != null) {
            buildLegendPosition(legendNode, legend);
        }
        
        // Build legend entries (default entry for compatibility)
        buildLegendEntries(legendNode, legend);
        
        // Build legend layout if needed
        buildLegendLayout(legendNode, legend);
        
        // Build legend styling if present
        if (legend.getStyle() != null) {
            buildLegendStyle(legendNode, legend);
        }
        
        // Build legend text properties if font is specified
        if (legend.getStyle() != null && legend.getStyle().getFont() != null) {
            buildLegendTextProperties(legendNode, legend);
        }
        
        // Add overlay setting (default to false)
        XNode overlay = legendNode.addChild("c:overlay");
        overlay.setAttr("val", "0");
        
        LOG.debug("ChartLegendBuilder.buildLegend: completed chart legend");
        return legendNode;
    }
    
    /**
     * Builds legend position.
     */
    private void buildLegendPosition(XNode legendNode, ChartLegendModel legend) {
        LOG.debug("ChartLegendBuilder.buildLegendPosition: building legend position");
        
        XNode legendPos = legendNode.addChild("c:legendPos");
        String positionValue = mapLegendPosition(legend.getPosition());
        legendPos.setAttr("val", positionValue);
        
        LOG.debug("ChartLegendBuilder.buildLegendPosition: set position to: {}", positionValue);
    }
    
    /**
     * Builds legend entries for Excel compatibility.
     */
    private void buildLegendEntries(XNode legendNode, ChartLegendModel legend) {
        LOG.debug("ChartLegendBuilder.buildLegendEntries: building legend entries");
        
        // Add default legend entry for Excel compatibility
        XNode legendEntry = legendNode.addChild("c:legendEntry");
        XNode idx = legendEntry.addChild("c:idx");
        idx.setAttr("val", "0");
        XNode delete = legendEntry.addChild("c:delete");
        delete.setAttr("val", "0");
        
        LOG.debug("ChartLegendBuilder.buildLegendEntries: completed legend entries");
    }
    
    /**
     * Builds legend layout.
     */
    private void buildLegendLayout(XNode legendNode, ChartLegendModel legend) {
        LOG.debug("ChartLegendBuilder.buildLegendLayout: building legend layout");
        
        XNode layout = legendNode.addChild("c:layout");
        
        // For custom positioning, we could add manual layout here
        // Currently using automatic layout based on position
        
        LOG.debug("ChartLegendBuilder.buildLegendLayout: completed legend layout");
    }
    
    /**
     * Builds legend styling.
     */
    private void buildLegendStyle(XNode legendNode, ChartLegendModel legend) {
        LOG.debug("ChartLegendBuilder.buildLegendStyle: building legend style");
        
        XNode spPr = legendNode.addChild("c:spPr");
        
        // Build fill properties
        if (legend.getStyle().getBackgroundColor() != null) {
            XNode solidFill = spPr.addChild("a:solidFill");
            buildColorNode(solidFill, legend.getStyle().getBackgroundColor());
        }
        
        // Build border properties
        if (legend.getStyle().getBorderColor() != null) {
            XNode ln = spPr.addChild("a:ln");
            XNode solidFill = ln.addChild("a:solidFill");
            buildColorNode(solidFill, legend.getStyle().getBorderColor());
        }
        
        LOG.debug("ChartLegendBuilder.buildLegendStyle: completed legend style");
    }
    
    /**
     * Builds legend text properties.
     */
    private void buildLegendTextProperties(XNode legendNode, ChartLegendModel legend) {
        LOG.debug("ChartLegendBuilder.buildLegendTextProperties: building legend text properties");
        
        XNode txPr = legendNode.addChild("c:txPr");
        XNode bodyPr = txPr.addChild("a:bodyPr");
        XNode lstStyle = txPr.addChild("a:lstStyle");
        
        XNode p = txPr.addChild("a:p");
        XNode pPr = p.addChild("a:pPr");
        
        // Apply text alignment if specified
        if (legend.getAlign() != null) {
            String alignment = mapTextAlignment(legend.getAlign());
            if (alignment != null) {
                pPr.setAttr("algn", alignment);
            }
        }
        
        XNode defRPr = pPr.addChild("a:defRPr");
        
        // Apply font properties
        if (legend.getStyle().getFont().getFontSize() != null) {
            // Convert points to hundredths of a point
            int fontSize = (int) (legend.getStyle().getFont().getFontSize() * 100);
            defRPr.setAttr("sz", String.valueOf(fontSize));
        }
        
        if (legend.getStyle().getFont().isBold()) {
            defRPr.setAttr("b", "1");
        }
        
        if (legend.getStyle().getFont().isItalic()) {
            defRPr.setAttr("i", "1");
        }
        
        if (legend.getStyle().getFont().getFontColor() != null) {
            XNode solidFill = defRPr.addChild("a:solidFill");
            buildColorNode(solidFill, legend.getStyle().getFont().getFontColor());
        }
        
        XNode endParaRPr = p.addChild("a:endParaRPr");
        endParaRPr.setAttr("lang", "en-US");
        
        LOG.debug("ChartLegendBuilder.buildLegendTextProperties: completed legend text properties");
    }
    
    /**
     * Builds color node from color string.
     */
    private void buildColorNode(XNode parentNode, String color) {
        String normalizedColor = ColorHelper.toCssColor(color);
        if (normalizedColor == null) {
            LOG.warn("ChartLegendBuilder.buildColorNode: invalid color: {}, using default", color);
            normalizedColor = "#000000"; // fallback to black
        }
        
        // Remove # prefix for OOXML if present
        if (normalizedColor.startsWith("#")) {
            normalizedColor = normalizedColor.substring(1);
        }
        
        XNode srgbClr = parentNode.addChild("a:srgbClr");
        srgbClr.setAttr("val", normalizedColor.toUpperCase());
    }
    
    /**
     * Maps legend position to OOXML value.
     */
    private String mapLegendPosition(io.nop.excel.chart.constants.ChartLegendPosition position) {
        switch (position) {
            case TOP: return "t";
            case BOTTOM: return "b";
            case LEFT: return "l";
            case RIGHT: return "r";
            case TOP_RIGHT: return "tr";
            case TOP_LEFT: return "tl";
            case BOTTOM_RIGHT: return "br";
            case BOTTOM_LEFT: return "bl";
            default: return "r"; // default to right
        }
    }
    
    /**
     * Maps text alignment to OOXML value.
     */
    private String mapTextAlignment(io.nop.excel.model.constants.ExcelHorizontalAlignment alignment) {
        switch (alignment) {
            case LEFT: return "l";
            case RIGHT: return "r";
            case CENTER: return "ctr";
            case JUSTIFY: return "just";
            default: return "l"; // default to left
        }
    }
}