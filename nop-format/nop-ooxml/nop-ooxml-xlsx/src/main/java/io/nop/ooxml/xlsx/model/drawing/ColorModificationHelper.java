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
import io.nop.excel.util.UnitsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for OOXML color modifications.
 * Extends the existing ColorHelper with OOXML-specific functionality.
 */
public class ColorModificationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ColorModificationHelper.class);
    
    /**
     * Applies all color modifications from an OOXML color node to a base color.
     * 
     * @param baseColor the base color in hex format (e.g., "#FF0000")
     * @param colorNode the OOXML color node containing modification elements
     * @return the modified color in hex format, or the original color if modifications fail
     */
    public static String applyColorModifications(String baseColor, XNode colorNode) {
        if (baseColor == null || colorNode == null) {
            return baseColor;
        }
        
        try {
            String workingColor = normalizeColorFormat(baseColor);
            
            // Apply luminance modulation (multiply brightness)
            workingColor = applyLuminanceModification(workingColor, colorNode);
            
            // Apply luminance offset (add/subtract brightness)
            workingColor = applyLuminanceOffset(workingColor, colorNode);
            
            // Apply tint (blend towards white) - use existing ColorHelper
            workingColor = applyTintFromNode(workingColor, colorNode);
            
            // Apply shade (blend towards black)
            workingColor = applyShade(workingColor, colorNode);
            
            return ColorHelper.toCssColor(workingColor);
            
        } catch (Exception e) {
            LOG.warn("ColorModificationHelper.applyColorModifications: error applying modifications to {}: {}", 
                baseColor, e.getMessage());
            return baseColor;
        }
    }
    
    /**
     * Applies tint modification using the existing ColorHelper.
     */
    private static String applyTintFromNode(String color, XNode colorNode) {
        XNode tintNode = colorNode.childByTag("a:tint");
        if (tintNode != null) {
            Long tint = tintNode.attrLong("val");
            if (tint != null) {
                double tintFactor = ooxmlPercentageToDouble(tint);
                String result = ColorHelper.applyTint(color, tintFactor);
                LOG.debug("ColorModificationHelper: applied tint factor: {}", tintFactor);
                return result;
            }
        }
        return color;
    }
    
    /**
     * Applies luminance modulation (brightness multiplication).
     * Formula: newValue = originalValue * (modValue / 100000)
     */
    private static String applyLuminanceModification(String color, XNode colorNode) {
        XNode lumModNode = colorNode.childByTag("a:lumMod");
        if (lumModNode != null) {
            Long lumMod = lumModNode.attrLong("val");
            if (lumMod != null) {
                double factor = ooxmlPercentageToDouble(lumMod);
                String result = applyColorFactor(color, factor);
                LOG.debug("ColorModificationHelper: applied luminance modulation factor: {}", factor);
                return result;
            }
        }
        return color;
    }
    
    /**
     * Applies luminance offset (brightness addition/subtraction).
     * Formula: newValue = originalValue + (offsetValue / 100000 * 255)
     */
    private static String applyLuminanceOffset(String color, XNode colorNode) {
        XNode lumOffNode = colorNode.childByTag("a:lumOff");
        if (lumOffNode != null) {
            Long lumOff = lumOffNode.attrLong("val");
            if (lumOff != null) {
                double offset = ooxmlPercentageToDouble(lumOff) * 255;
                String result = applyColorOffset(color, offset);
                LOG.debug("ColorModificationHelper: applied luminance offset: {}", offset);
                return result;
            }
        }
        return color;
    }
    
    /**
     * Applies shade (blend towards black).
     * Formula: newValue = originalValue * (shadeValue / 100000)
     */
    private static String applyShade(String color, XNode colorNode) {
        XNode shadeNode = colorNode.childByTag("a:shade");
        if (shadeNode != null) {
            Long shade = shadeNode.attrLong("val");
            if (shade != null) {
                double shadeFactor = ooxmlPercentageToDouble(shade);
                String result = applyColorFactor(color, shadeFactor);
                LOG.debug("ColorModificationHelper: applied shade factor: {}", shadeFactor);
                return result;
            }
        }
        return color;
    }
    
    /**
     * Applies a multiplication factor to all RGB components.
     */
    private static String applyColorFactor(String color, double factor) {
        float[] rgb = ColorHelper.toNormalizedRgb(color);
        
        for (int i = 0; i < 3; i++) {
            rgb[i] = (float) Math.max(0.0, Math.min(1.0, rgb[i] * factor));
        }
        
        return formatNormalizedRgbToHex(rgb);
    }
    
    /**
     * Applies an offset to all RGB components.
     */
    private static String applyColorOffset(String color, double offset) {
        float[] rgb = ColorHelper.toNormalizedRgb(color);
        double normalizedOffset = offset / 255.0;
        
        for (int i = 0; i < 3; i++) {
            rgb[i] = (float) Math.max(0.0, Math.min(1.0, rgb[i] + normalizedOffset));
        }
        
        return formatNormalizedRgbToHex(rgb);
    }
    
    /**
     * Formats normalized RGB values back to hex string.
     */
    private static String formatNormalizedRgbToHex(float[] rgb) {
        int r = Math.round(rgb[0] * 255);
        int g = Math.round(rgb[1] * 255);
        int b = Math.round(rgb[2] * 255);
        return String.format("%02X%02X%02X", r, g, b);
    }
    
    /**
     * Normalizes color format for processing (removes # prefix if present).
     */
    private static String normalizeColorFormat(String color) {
        if (color == null) {
            return null;
        }
        return color.startsWith("#") ? color.substring(1) : color;
    }
    
    /**
     * Converts OOXML percentage value to double.
     * OOXML uses 100000 to represent 100%.
     * 
     * @param ooxmlValue the OOXML percentage value (0-100000)
     * @return percentage as double (0.0-1.0)
     */
    public static double ooxmlPercentageToDouble(long ooxmlValue) {
        return ooxmlValue / 100000.0;
    }
    
    /**
     * Converts OOXML angle value to degrees.
     * OOXML uses 60000ths of a degree.
     * 
     * @param ooxmlAngle the OOXML angle value
     * @return angle in degrees
     */
    public static double ooxmlAngleToDegrees(long ooxmlAngle) {
        return ooxmlAngle / 60000.0;
    }
}