/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.excel.model.ExcelFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for chart title elements and styling.
 * Handles title text, layout, positioning, and styling.
 */
public class ChartTitleParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTitleParser.class);
    
    /**
     * Singleton instance for reuse across multiple parsing operations.
     */
    public static final ChartTitleParser INSTANCE = new ChartTitleParser(ChartStyleParser.INSTANCE);
    
    private final ChartStyleParser styleParser;
    
    public ChartTitleParser(ChartStyleParser styleParser) {
        this.styleParser = styleParser;
    }
    
    /**
     * Parses a chart title element and creates a fully populated ChartTitleModel.
     */
    public void parseTitle(ChartModel chart, XNode titleNode) {
        LOG.debug("ChartTitleParser.parseTitle: parsing chart title");
        
        if (titleNode == null) {
            LOG.warn("ChartTitleParser.parseTitle: title node is null");
            return;
        }
        
        try {
            ChartTitleModel title = new ChartTitleModel();
            
            // Parse title text
            XNode txNode = titleNode.childByTag("c:tx");
            if (txNode != null) {
                String titleText = parseTitleText(txNode);
                if (titleText != null && !titleText.trim().isEmpty()) {
                    title.setText(titleText);
                    LOG.debug("ChartTitleParser.parseTitle: set title text: '{}'", titleText);
                }
            }
            
            // Parse title layout/positioning
            XNode layoutNode = titleNode.childByTag("c:layout");
            if (layoutNode != null) {
                parseTitleLayout(title, layoutNode);
            }
            
            // Parse title overlay setting
            XNode overlayNode = titleNode.childByTag("c:overlay");
            if (overlayNode != null) {
                Boolean overlayValue = overlayNode.attrBoolean("val");
                if (overlayValue != null && overlayValue) {
                    LOG.debug("ChartTitleParser.parseTitle: title overlay enabled (not stored in model)");
                }
            }
            
            // Parse title shape properties (styling)
            XNode spPrNode = titleNode.childByTag("c:spPr");
            if (spPrNode != null) {
                parseTitleStyle(title, spPrNode);
            }
            
            // Parse title text properties (font formatting)
            XNode txPrNode = titleNode.childByTag("c:txPr");
            if (txPrNode != null) {
                parseTitleTextProperties(title, txPrNode);
            }
            
            // Set title on chart
            chart.setTitle(title);
            
            LOG.info("ChartTitleParser.parseTitle: successfully parsed chart title: '{}'", title.getText());
            
        } catch (Exception e) {
            LOG.error("ChartTitleParser.parseTitle: error parsing chart title", e);
        }
    }
    
    /**
     * Parses title text from various text sources.
     */
    public String parseTitleText(XNode txNode) {
        if (txNode == null) {
            return null;
        }
        
        try {
            // Check for rich text first
            XNode richNode = txNode.childByTag("c:rich");
            if (richNode != null) {
                return parseTitleRichText(richNode);
            }
            
            // Check for string reference
            XNode strRefNode = txNode.childByTag("c:strRef");
            if (strRefNode != null) {
                return parseTitleStringReference(strRefNode);
            }
            
            // Check for literal value
            XNode vNode = txNode.childByTag("c:v");
            if (vNode != null) {
                String literalValue = vNode.contentText();
                LOG.debug("ChartTitleParser.parseTitleText: found literal title: {}", literalValue);
                return literalValue;
            }
            
            LOG.debug("ChartTitleParser.parseTitleText: no title text found");
            return null;
            
        } catch (Exception e) {
            LOG.warn("ChartTitleParser.parseTitleText: error parsing title text", e);
            return null;
        }
    }
    
    /**
     * Parses rich text title content.
     */
    public String parseTitleRichText(XNode richNode) {
        if (richNode == null) {
            return null;
        }
        
        try {
            StringBuilder titleText = new StringBuilder();
            
            // Parse body properties
            XNode bodyPrNode = richNode.childByTag("a:bodyPr");
            if (bodyPrNode != null) {
                // Body properties like rotation, margins, etc.
                String rot = bodyPrNode.attrText("rot");
                if (rot != null) {
                    LOG.debug("ChartTitleParser.parseTitleRichText: text rotation: {} (not stored in model)", rot);
                }
            }
            
            // Parse list style
            XNode lstStyleNode = richNode.childByTag("a:lstStyle");
            if (lstStyleNode != null) {
                LOG.debug("ChartTitleParser.parseTitleRichText: found list style");
            }
            
            // Parse paragraphs
            for (XNode pNode : richNode.childrenByTag("a:p")) {
                // Parse paragraph properties
                XNode pPrNode = pNode.childByTag("a:pPr");
                if (pPrNode != null) {
                    String algn = pPrNode.attrText("algn");
                    if (algn != null) {
                        LOG.debug("ChartTitleParser.parseTitleRichText: text alignment: {} (not stored in model)", algn);
                    }
                }
                
                // Parse runs (text segments)
                for (XNode rNode : pNode.childrenByTag("a:r")) {
                    // Parse run properties (font formatting)
                    XNode rPrNode = rNode.childByTag("a:rPr");
                    if (rPrNode != null) {
                        // Font properties are handled separately
                        LOG.debug("ChartTitleParser.parseTitleRichText: found run properties");
                    }
                    
                    // Extract text content
                    XNode tNode = rNode.childByTag("a:t");
                    if (tNode != null) {
                        String text = tNode.contentText();
                        if (text != null) {
                            titleText.append(text);
                        }
                    }
                }
                
                // Add line break between paragraphs (except for the last one)
                if (titleText.length() > 0) {
                    titleText.append("\n");
                }
            }
            
            String result = titleText.toString().trim();
            LOG.debug("ChartTitleParser.parseTitleRichText: extracted rich text: '{}'", result);
            return result.isEmpty() ? null : result;
            
        } catch (Exception e) {
            LOG.warn("ChartTitleParser.parseTitleRichText: error parsing rich text", e);
            return null;
        }
    }
    
    /**
     * Parses title from string reference.
     */
    public String parseTitleStringReference(XNode strRefNode) {
        if (strRefNode == null) {
            return null;
        }
        
        try {
            // Parse formula reference
            XNode fNode = strRefNode.childByTag("c:f");
            if (fNode != null) {
                String formula = fNode.contentText();
                LOG.debug("ChartTitleParser.parseTitleStringReference: found title formula: {}", formula);
                
                // Look for cached value
                XNode strCacheNode = strRefNode.childByTag("c:strCache");
                if (strCacheNode != null) {
                    XNode ptCountNode = strCacheNode.childByTag("c:ptCount");
                    if (ptCountNode != null) {
                        XNode ptNode = strCacheNode.childByTag("c:pt");
                        if (ptNode != null) {
                            XNode vNode = ptNode.childByTag("c:v");
                            if (vNode != null) {
                                String cachedValue = vNode.contentText();
                                LOG.debug("ChartTitleParser.parseTitleStringReference: found cached title: {}", cachedValue);
                                return cachedValue;
                            }
                        }
                    }
                }
            }
            
            LOG.debug("ChartTitleParser.parseTitleStringReference: no cached title value found");
            return null;
            
        } catch (Exception e) {
            LOG.warn("ChartTitleParser.parseTitleStringReference: error parsing string reference", e);
            return null;
        }
    }
    
    /**
     * Parses title layout and positioning.
     */
    public void parseTitleLayout(ChartTitleModel title, XNode layoutNode) {
        LOG.debug("ChartTitleParser.parseTitleLayout: parsing title layout");
        
        if (layoutNode == null) {
            return;
        }
        
        try {
            // Parse manual layout
            XNode manualLayoutNode = layoutNode.childByTag("c:manualLayout");
            if (manualLayoutNode != null) {
                // Parse layout target
                XNode layoutTargetNode = manualLayoutNode.childByTag("c:layoutTarget");
                if (layoutTargetNode != null) {
                    String layoutTarget = layoutTargetNode.attrText("val");
                    LOG.debug("ChartTitleParser.parseTitleLayout: layout target: {} (not stored in model)", layoutTarget);
                }
                
                // Parse position coordinates
                XNode xModeNode = manualLayoutNode.childByTag("c:xMode");
                XNode xNode = manualLayoutNode.childByTag("c:x");
                if (xModeNode != null && xNode != null) {
                    String xMode = xModeNode.attrText("val");
                    Double x = xNode.attrDouble("val");
                    
                    if (x != null) {
                        LOG.debug("ChartTitleParser.parseTitleLayout: x position: {} {} (not stored in model)", xMode, x);
                        
                        // Try to determine position from coordinates
                        XNode yModeNode = manualLayoutNode.childByTag("c:yMode");
                        XNode yNode = manualLayoutNode.childByTag("c:y");
                        if (yModeNode != null && yNode != null) {
                            String yMode = yModeNode.attrText("val");
                            Double y = yNode.attrDouble("val");
                            
                            if (y != null) {
                                LOG.debug("ChartTitleParser.parseTitleLayout: y position: {} {}", yMode, y);
                                
                                // Map coordinates to title position if model supports it
                                io.nop.excel.chart.constants.ChartTitlePosition position = mapCoordinatesToPosition(x, y);
                                if (position != null) {
                                    title.setPosition(position);
                                    LOG.debug("ChartTitleParser.parseTitleLayout: mapped coordinates to position: {}", position);
                                }
                            }
                        }
                    }
                }
                
                // Parse dimensions
                XNode wModeNode = manualLayoutNode.childByTag("c:wMode");
                XNode wNode = manualLayoutNode.childByTag("c:w");
                if (wModeNode != null && wNode != null) {
                    String wMode = wModeNode.attrText("val");
                    String wValue = wNode.attrText("val");
                    LOG.debug("ChartTitleParser.parseTitleLayout: width: {} {} (not stored in model)", wMode, wValue);
                }
                
                XNode hModeNode = manualLayoutNode.childByTag("c:hMode");
                XNode hNode = manualLayoutNode.childByTag("c:h");
                if (hModeNode != null && hNode != null) {
                    String hMode = hModeNode.attrText("val");
                    String hValue = hNode.attrText("val");
                    LOG.debug("ChartTitleParser.parseTitleLayout: height: {} {} (not stored in model)", hMode, hValue);
                }
            }
            
        } catch (Exception e) {
            LOG.warn("ChartTitleParser.parseTitleLayout: error parsing title layout", e);
        }
    }
    
    /**
     * Parses title styling information.
     * Note: ChartTitleModel only supports font styling via the font property.
     * Shape properties like fill and border are not supported in the current model.
     */
    public void parseTitleStyle(ChartTitleModel title, XNode spPrNode) {
        LOG.debug("ChartTitleParser.parseTitleStyle: parsing title styling");
        
        if (spPrNode == null) {
            return;
        }
        
        try {
            // Parse fill properties (not stored in ChartTitleModel)
            XNode solidFillNode = spPrNode.childByTag("a:solidFill");
            if (solidFillNode != null) {
                String fillColor = styleParser.parseColorFromFillNode(spPrNode);
                if (fillColor != null) {
                    LOG.debug("ChartTitleParser.parseTitleStyle: found fill color: {} (not stored in model)", fillColor);
                }
            }
            
            // Parse gradient fill (not stored in ChartTitleModel)
            XNode gradFillNode = spPrNode.childByTag("a:gradFill");
            if (gradFillNode != null) {
                LOG.debug("ChartTitleParser.parseTitleStyle: found gradient fill (not stored in model)");
            }
            
            // Parse no fill (not stored in ChartTitleModel)
            XNode noFillNode = spPrNode.childByTag("a:noFill");
            if (noFillNode != null) {
                LOG.debug("ChartTitleParser.parseTitleStyle: no fill (not stored in model)");
            }
            
            // Parse border properties (not stored in ChartTitleModel)
            XNode lnNode = spPrNode.childByTag("a:ln");
            if (lnNode != null) {
                String borderColor = styleParser.parseColorFromFillNode(lnNode);
                if (borderColor != null) {
                    LOG.debug("ChartTitleParser.parseTitleStyle: found border color: {} (not stored in model)", borderColor);
                }
            }
            
            LOG.debug("ChartTitleParser.parseTitleStyle: shape properties parsed (not stored in ChartTitleModel)");
            
        } catch (Exception e) {
            LOG.warn("ChartTitleParser.parseTitleStyle: error parsing title style", e);
        }
    }
    
    /**
     * Parses title text properties and font formatting.
     */
    public void parseTitleTextProperties(ChartTitleModel title, XNode txPrNode) {
        LOG.debug("ChartTitleParser.parseTitleTextProperties: parsing title text properties");
        
        if (txPrNode == null) {
            return;
        }
        
        try {
            // Parse body properties
            XNode bodyPrNode = txPrNode.childByTag("a:bodyPr");
            if (bodyPrNode != null) {
                String rot = bodyPrNode.attrText("rot");
                if (rot != null) {
                    LOG.debug("ChartTitleParser.parseTitleTextProperties: text rotation: {} (not stored in model)", rot);
                }
            }
            
            // Parse list style
            XNode lstStyleNode = txPrNode.childByTag("a:lstStyle");
            if (lstStyleNode != null) {
                LOG.debug("ChartTitleParser.parseTitleTextProperties: found list style");
            }
            
            // Parse paragraph properties for alignment
            for (XNode pNode : txPrNode.childrenByTag("a:p")) {
                XNode pPrNode = pNode.childByTag("a:pPr");
                if (pPrNode != null) {
                    String algn = pPrNode.attrText("algn");
                    if (algn != null) {
                        LOG.debug("ChartTitleParser.parseTitleTextProperties: text alignment: {} (not stored in model)", algn);
                    }
                }
            }
            
            // Parse font properties
            ExcelFont font = styleParser.parseFontProperties(txPrNode);
            boolean hasFontData = font != null;
            
            if (hasFontData) {
                title.setFont(font);
                LOG.debug("ChartTitleParser.parseTitleTextProperties: applied font properties to title");
            }
            
        } catch (Exception e) {
            LOG.warn("ChartTitleParser.parseTitleTextProperties: error parsing title text properties", e);
        }
    }
    
    // Private helper methods
    
    private io.nop.excel.chart.constants.ChartTitlePosition mapCoordinatesToPosition(double x, double y) {
        // Map coordinates to standard title positions
        // Assuming coordinates are in 0.0 to 1.0 range where (0,0) is top-left
        
        if (y < 0.2) {
            // Top area
            return io.nop.excel.chart.constants.ChartTitlePosition.TOP;
        } else if (y > 0.8) {
            // Bottom area
            return io.nop.excel.chart.constants.ChartTitlePosition.BOTTOM;
        } else {
            // Middle area
            if (x < 0.2) return io.nop.excel.chart.constants.ChartTitlePosition.LEFT;
            if (x > 0.8) return io.nop.excel.chart.constants.ChartTitlePosition.RIGHT;
            return io.nop.excel.chart.constants.ChartTitlePosition.CENTER;
        }
    }
}