/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.color.ColorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for chart style elements following the Parser-Builder pattern.
 * Generates OOXML chart style structures and references.
 */
public class ChartStyleBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartStyleBuilder.class);
    
    /**
     * Singleton instance for reuse across multiple building operations.
     */
    public static final ChartStyleBuilder INSTANCE = new ChartStyleBuilder();
    
    private ChartStyleBuilder() {
        // Private constructor for singleton
    }
    
    /**
     * Builds chart style references for chart space.
     */
    public void buildChartStyleReferences(XNode chartSpace, Object style) {
        LOG.debug("ChartStyleBuilder.buildChartStyleReferences: building chart style references");
        
        // Add chart style reference
        XNode styleRef = chartSpace.addChild("c:style");
        styleRef.setAttr("val", "2"); // default style
        
        // Add color style reference
        XNode colorStyleRef = chartSpace.addChild("c:colorStyle");
        colorStyleRef.setAttr("val", "2"); // default color style
        
        LOG.debug("ChartStyleBuilder.buildChartStyleReferences: completed chart style references");
    }
    
    /**
     * Builds shape properties for styling elements.
     */
    public XNode buildShapeProperties(Object styleModel) {
        LOG.debug("ChartStyleBuilder.buildShapeProperties: building shape properties");
        
        XNode spPr = XNode.make("c:spPr");
        
        // Build fill properties
        buildFillProperties(spPr, null);
        
        // Build line properties
        buildLineProperties(spPr, null);
        
        LOG.debug("ChartStyleBuilder.buildShapeProperties: completed shape properties");
        return spPr;
    }
    
    /**
     * Builds fill properties.
     */
    public void buildFillProperties(XNode spPr, Object fill) {
        LOG.debug("ChartStyleBuilder.buildFillProperties: building fill properties");
        
        // Default solid fill
        XNode solidFill = spPr.addChild("a:solidFill");
        buildColorNode(solidFill, "#4472C4"); // default blue color
        
        LOG.debug("ChartStyleBuilder.buildFillProperties: completed fill properties");
    }
    
    /**
     * Builds line properties.
     */
    public void buildLineProperties(XNode spPr, Object line) {
        LOG.debug("ChartStyleBuilder.buildLineProperties: building line properties");
        
        XNode ln = spPr.addChild("a:ln");
        
        // Set line width (default 1 point = 12700 EMUs)
        ln.setAttr("w", "12700");
        
        // Set line color
        XNode solidFill = ln.addChild("a:solidFill");
        buildColorNode(solidFill, "#4472C4"); // default blue color
        
        // Set line style
        XNode prstDash = ln.addChild("a:prstDash");
        prstDash.setAttr("val", "solid"); // default solid line
        
        LOG.debug("ChartStyleBuilder.buildLineProperties: completed line properties");
    }
    
    /**
     * Builds color node from color string.
     */
    public void buildColorNode(XNode parentNode, String color) {
        LOG.debug("ChartStyleBuilder.buildColorNode: building color node for color: {}", color);
        
        // Use ColorHelper to process color
        String normalizedColor = ColorHelper.toCssColor(color);
        if (normalizedColor == null) {
            LOG.warn("ChartStyleBuilder.buildColorNode: invalid color: {}, using default", color);
            normalizedColor = "#4472C4"; // fallback to default blue
        }
        
        // Remove # prefix for OOXML if present
        if (normalizedColor.startsWith("#")) {
            normalizedColor = normalizedColor.substring(1);
        }
        
        XNode srgbClr = parentNode.addChild("a:srgbClr");
        srgbClr.setAttr("val", normalizedColor.toUpperCase());
        
        LOG.debug("ChartStyleBuilder.buildColorNode: completed color node with normalized color: {}", normalizedColor);
    }
    
    /**
     * Builds gradient fill properties.
     */
    public void buildGradientFill(XNode spPr, Object gradient) {
        LOG.debug("ChartStyleBuilder.buildGradientFill: building gradient fill");
        
        XNode gradFill = spPr.addChild("a:gradFill");
        XNode gsLst = gradFill.addChild("a:gsLst");
        
        // Add gradient stops (simplified implementation)
        XNode gs1 = gsLst.addChild("a:gs");
        gs1.setAttr("pos", "0");
        XNode srgbClr1 = gs1.addChild("a:srgbClr");
        srgbClr1.setAttr("val", "4472C4"); // start color
        
        XNode gs2 = gsLst.addChild("a:gs");
        gs2.setAttr("pos", "100000");
        XNode srgbClr2 = gs2.addChild("a:srgbClr");
        srgbClr2.setAttr("val", "8DB4E2"); // end color
        
        // Linear gradient path
        XNode lin = gradFill.addChild("a:lin");
        lin.setAttr("ang", "5400000"); // 90 degrees
        lin.setAttr("scaled", "1");
        
        LOG.debug("ChartStyleBuilder.buildGradientFill: completed gradient fill");
    }
    
    /**
     * Builds text properties for styling.
     */
    public XNode buildTextProperties(Object textStyle) {
        LOG.debug("ChartStyleBuilder.buildTextProperties: building text properties");
        
        XNode txPr = XNode.make("c:txPr");
        XNode bodyPr = txPr.addChild("a:bodyPr");
        XNode lstStyle = txPr.addChild("a:lstStyle");
        
        XNode p = txPr.addChild("a:p");
        XNode pPr = p.addChild("a:pPr");
        XNode defRPr = pPr.addChild("a:defRPr");
        defRPr.setAttr("sz", "900"); // 9pt font
        
        XNode endParaRPr = p.addChild("a:endParaRPr");
        endParaRPr.setAttr("lang", "en-US");
        
        LOG.debug("ChartStyleBuilder.buildTextProperties: completed text properties");
        return txPr;
    }
    
    /**
     * Builds effect properties for advanced styling.
     */
    public void buildEffectProperties(XNode spPr, Object effects) {
        LOG.debug("ChartStyleBuilder.buildEffectProperties: building effect properties");
        
        XNode effectLst = spPr.addChild("a:effectLst");
        
        // Add shadow effect (example)
        XNode outerShdw = effectLst.addChild("a:outerShdw");
        outerShdw.setAttr("blurRad", "38100");
        outerShdw.setAttr("dist", "38100");
        outerShdw.setAttr("dir", "2700000");
        outerShdw.setAttr("algn", "tl");
        outerShdw.setAttr("rotWithShape", "0");
        
        XNode srgbClr = outerShdw.addChild("a:srgbClr");
        srgbClr.setAttr("val", "000000");
        XNode alpha = srgbClr.addChild("a:alpha");
        alpha.setAttr("val", "35000");
        
        LOG.debug("ChartStyleBuilder.buildEffectProperties: completed effect properties");
    }
}