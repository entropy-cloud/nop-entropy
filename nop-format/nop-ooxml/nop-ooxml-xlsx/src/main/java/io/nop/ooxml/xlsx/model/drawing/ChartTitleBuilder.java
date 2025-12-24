/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.excel.model.color.ColorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for chart title elements following the Parser-Builder pattern.
 * Generates OOXML chart title structures from ChartTitleModel objects.
 */
public class ChartTitleBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTitleBuilder.class);
    
    /**
     * Singleton instance for reuse across multiple building operations.
     */
    public static final ChartTitleBuilder INSTANCE = new ChartTitleBuilder();
    
    private ChartTitleBuilder() {
        // Private constructor for singleton
    }
    
    /**
     * Builds chart title XML element from ChartTitleModel.
     */
    public XNode buildTitle(ChartTitleModel title) {
        if (title == null || !title.getVisible()) {
            LOG.debug("ChartTitleBuilder.buildTitle: title is null or not visible");
            return null;
        }
        
        LOG.debug("ChartTitleBuilder.buildTitle: building chart title: '{}'", title.getText());
        
        XNode titleNode = XNode.make("c:title");
        
        // Build title text
        if (title.getText() != null && !title.getText().trim().isEmpty()) {
            buildTitleText(titleNode, title);
        }
        
        // Build title layout if position is specified
        if (title.getPosition() != null) {
            buildTitleLayout(titleNode, title);
        }
        
        // Build title styling if present
        if (title.getFont() != null) {
            buildTitleStyle(titleNode, title);
        }
        
        // Build title text properties if font is specified
        if (title.getFont() != null) {
            buildTitleTextProperties(titleNode, title);
        }
        
        // Add overlay setting (default to false)
        XNode overlay = titleNode.addChild("c:overlay");
        overlay.setAttr("val", "0");
        
        LOG.debug("ChartTitleBuilder.buildTitle: completed chart title");
        return titleNode;
    }
    
    /**
     * Builds title text content.
     */
    private void buildTitleText(XNode titleNode, ChartTitleModel title) {
        LOG.debug("ChartTitleBuilder.buildTitleText: building title text");
        
        XNode tx = titleNode.addChild("c:tx");
        XNode rich = tx.addChild("c:rich");
        
        // Body properties
        XNode bodyPr = rich.addChild("a:bodyPr");
        
        // List style
        XNode lstStyle = rich.addChild("a:lstStyle");
        
        // Paragraph
        XNode p = rich.addChild("a:p");
        XNode pPr = p.addChild("a:pPr");
        XNode defRPr = pPr.addChild("a:defRPr");
        
        // Text run
        XNode r = p.addChild("a:r");
        XNode rPr = r.addChild("a:rPr");
        rPr.setAttr("lang", "en-US");
        
        // Title text content
        XNode t = r.addChild("a:t");
        t.content(title.getText());
        
        LOG.debug("ChartTitleBuilder.buildTitleText: completed title text");
    }
    
    /**
     * Builds title layout and positioning.
     */
    private void buildTitleLayout(XNode titleNode, ChartTitleModel title) {
        LOG.debug("ChartTitleBuilder.buildTitleLayout: building title layout");
        
        XNode layout = titleNode.addChild("c:layout");
        XNode manualLayout = layout.addChild("c:manualLayout");
        
        // Layout target
        XNode layoutTarget = manualLayout.addChild("c:layoutTarget");
        layoutTarget.setAttr("val", "inner");
        
        // Map position to coordinates
        double[] coords = mapPositionToCoordinates(title.getPosition());
        
        // X position
        XNode xMode = manualLayout.addChild("c:xMode");
        xMode.setAttr("val", "factor");
        XNode x = manualLayout.addChild("c:x");
        x.setAttr("val", String.valueOf(coords[0]));
        
        // Y position
        XNode yMode = manualLayout.addChild("c:yMode");
        yMode.setAttr("val", "factor");
        XNode y = manualLayout.addChild("c:y");
        y.setAttr("val", String.valueOf(coords[1]));
        
        LOG.debug("ChartTitleBuilder.buildTitleLayout: completed title layout at position: {}", title.getPosition());
    }
    
    /**
     * Builds title styling.
     */
    private void buildTitleStyle(XNode titleNode, ChartTitleModel title) {
        LOG.debug("ChartTitleBuilder.buildTitleStyle: building title style");
        
        XNode spPr = titleNode.addChild("c:spPr");
        
        // Build fill properties
        if (title.getFont() != null && title.getFont().getFontColor() != null) {
            XNode solidFill = spPr.addChild("a:solidFill");
            buildColorNode(solidFill, title.getFont().getFontColor());
        }
        
        LOG.debug("ChartTitleBuilder.buildTitleStyle: completed title style");
    }
    
    /**
     * Builds title text properties and font formatting.
     */
    private void buildTitleTextProperties(XNode titleNode, ChartTitleModel title) {
        LOG.debug("ChartTitleBuilder.buildTitleTextProperties: building title text properties");
        
        XNode txPr = titleNode.addChild("c:txPr");
        
        // Body properties
        XNode bodyPr = txPr.addChild("a:bodyPr");
        
        // List style
        XNode lstStyle = txPr.addChild("a:lstStyle");
        
        // Paragraph with font properties
        XNode p = txPr.addChild("a:p");
        XNode pPr = p.addChild("a:pPr");
        XNode defRPr = pPr.addChild("a:defRPr");
        
        // Apply font properties
        if (title.getFont().getFontSize() != null) {
            // Convert points to hundredths of a point
            int fontSize = (int) (title.getFont().getFontSize() * 100);
            defRPr.setAttr("sz", String.valueOf(fontSize));
        }
        
        if (title.getFont().isBold()) {
            defRPr.setAttr("b", "1");
        }
        
        if (title.getFont().isItalic()) {
            defRPr.setAttr("i", "1");
        }
        
        if (title.getFont().getFontColor() != null) {
            XNode solidFill = defRPr.addChild("a:solidFill");
            buildColorNode(solidFill, title.getFont().getFontColor());
        }
        
        XNode endParaRPr = p.addChild("a:endParaRPr");
        endParaRPr.setAttr("lang", "en-US");
        
        LOG.debug("ChartTitleBuilder.buildTitleTextProperties: completed title text properties");
    }
    
    /**
     * Builds color node from color string.
     */
    private void buildColorNode(XNode parentNode, String color) {
        String normalizedColor = ColorHelper.toCssColor(color);
        if (normalizedColor == null) {
            LOG.warn("ChartTitleBuilder.buildColorNode: invalid color: {}, using default", color);
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
     * Maps title position to coordinates.
     */
    private double[] mapPositionToCoordinates(io.nop.excel.chart.constants.ChartTitlePosition position) {
        switch (position) {
            case TOP: return new double[]{0.5, 0.0};
            case BOTTOM: return new double[]{0.5, 1.0};
            case LEFT: return new double[]{0.0, 0.5};
            case RIGHT: return new double[]{1.0, 0.5};
            case CENTER: return new double[]{0.5, 0.5};
            default: return new double[]{0.5, 0.0}; // default to top center
        }
    }
}