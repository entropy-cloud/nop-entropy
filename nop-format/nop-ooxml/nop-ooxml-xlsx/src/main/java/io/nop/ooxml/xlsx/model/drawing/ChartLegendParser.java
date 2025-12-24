/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartLegendPosition;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.util.UnitsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for chart legend elements and styling.
 * Handles legend positioning, layout, styling, and text properties.
 */
public class ChartLegendParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartLegendParser.class);
    
    /**
     * Singleton instance for reuse across multiple parsing operations.
     */
    public static final ChartLegendParser INSTANCE = new ChartLegendParser(ChartStyleParser.INSTANCE);
    
    private final ChartStyleParser styleParser;
    
    public ChartLegendParser(ChartStyleParser styleParser) {
        this.styleParser = styleParser;
    }
    
    /**
     * Parses a chart legend element and creates a fully populated ChartLegendModel.
     */
    public void parseLegend(ChartModel chart, XNode legendNode) {
        LOG.debug("ChartLegendParser.parseLegend: parsing chart legend");
        
        if (legendNode == null) {
            LOG.warn("ChartLegendParser.parseLegend: legend node is null");
            return;
        }
        
        try {
            ChartLegendModel legend = new ChartLegendModel();
            
            // Parse legend position
            XNode legendPosNode = legendNode.childByTag("c:legendPos");
            if (legendPosNode != null) {
                String position = legendPosNode.attrText("val");
                if (position != null) {
                    ChartLegendPosition legendPosition = mapLegendPosition(position);
                    if (legendPosition != null) {
                        legend.setPosition(legendPosition);
                        LOG.debug("ChartLegendParser.parseLegend: set legend position: {}", legendPosition);
                    }
                }
            }
            
            // Parse legend layout
            XNode layoutNode = legendNode.childByTag("c:layout");
            if (layoutNode != null) {
                parseLegendLayout(legend, layoutNode);
            }
            
            // Parse legend overlay
            XNode overlayNode = legendNode.childByTag("c:overlay");
            if (overlayNode != null) {
                Boolean overlayValue = overlayNode.attrBoolean("val");
                if (overlayValue != null && overlayValue) {
                    LOG.debug("ChartLegendParser.parseLegend: legend overlay enabled (not stored in model)");
                }
            }
            
            // Parse legend styling
            XNode spPrNode = legendNode.childByTag("c:spPr");
            if (spPrNode != null) {
                parseLegendStyle(legend, spPrNode);
            }
            
            // Parse legend text properties
            XNode txPrNode = legendNode.childByTag("c:txPr");
            if (txPrNode != null) {
                parseLegendTextProperties(legend, txPrNode);
            }
            
            // Parse legend entries
            parseLegendEntries(legend, legendNode);
            
            // Set legend on chart
            chart.setLegend(legend);
            
            LOG.info("ChartLegendParser.parseLegend: successfully parsed chart legend at position: {}", legend.getPosition());
            
        } catch (Exception e) {
            LOG.error("ChartLegendParser.parseLegend: error parsing chart legend", e);
        }
    }
    
    /**
     * Parses legend layout information.
     */
    public void parseLegendLayout(ChartLegendModel legend, XNode layoutNode) {
        LOG.debug("ChartLegendParser.parseLegendLayout: parsing legend layout");
        
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
                    LOG.debug("ChartLegendParser.parseLegendLayout: layout target: {} (not stored in model)", layoutTarget);
                }
                
                // Parse x position
                XNode xModeNode = manualLayoutNode.childByTag("c:xMode");
                XNode xNode = manualLayoutNode.childByTag("c:x");
                if (xModeNode != null && xNode != null) {
                    String xMode = xModeNode.attrText("val");
                    Double x = xNode.attrDouble("val");
                    
                    if (x != null) {
                        LOG.debug("ChartLegendParser.parseLegendLayout: x position: {} {} (not stored in model)", xMode, x);
                        
                        // Try to map coordinates to standard positions
                        XNode yModeNode = manualLayoutNode.childByTag("c:yMode");
                        XNode yNode = manualLayoutNode.childByTag("c:y");
                        if (yModeNode != null && yNode != null) {
                            String yMode = yModeNode.attrText("val");
                            Double y = yNode.attrDouble("val");
                            
                            if (y != null) {
                                LOG.debug("ChartLegendParser.parseLegendLayout: y position: {} {}", yMode, y);
                                
                                // Map coordinates to position if possible
                                ChartLegendPosition mappedPosition = mapLayoutToPosition(x, y);
                                if (mappedPosition != null && legend.getPosition() == null) {
                                    legend.setPosition(mappedPosition);
                                    LOG.debug("ChartLegendParser.parseLegendLayout: mapped coordinates to position: {}", mappedPosition);
                                }
                            }
                        }
                    }
                }
                
                // Parse width and height
                XNode wModeNode = manualLayoutNode.childByTag("c:wMode");
                XNode wNode = manualLayoutNode.childByTag("c:w");
                if (wModeNode != null && wNode != null) {
                    String wMode = wModeNode.attrText("val");
                    String wValue = wNode.attrText("val");
                    LOG.debug("ChartLegendParser.parseLegendLayout: width: {} {} (not stored in model)", wMode, wValue);
                }
                
                XNode hModeNode = manualLayoutNode.childByTag("c:hMode");
                XNode hNode = manualLayoutNode.childByTag("c:h");
                if (hModeNode != null && hNode != null) {
                    String hMode = hModeNode.attrText("val");
                    String hValue = hNode.attrText("val");
                    LOG.debug("ChartLegendParser.parseLegendLayout: height: {} {} (not stored in model)", hMode, hValue);
                }
            }
            
        } catch (Exception e) {
            LOG.warn("ChartLegendParser.parseLegendLayout: error parsing legend layout", e);
        }
    }
    
    /**
     * Parses legend styling information.
     */
    public void parseLegendStyle(ChartLegendModel legend, XNode spPrNode) {
        LOG.debug("ChartLegendParser.parseLegendStyle: parsing legend styling");
        
        if (spPrNode == null) {
            return;
        }
        
        try {
            ChartShapeStyleModel style = new ChartShapeStyleModel();
            boolean hasStyleData = false;
            
            // Parse fill properties
            XNode solidFillNode = spPrNode.childByTag("a:solidFill");
            if (solidFillNode != null) {
                String fillColor = styleParser.parseColorFromFillNode(spPrNode);
                if (fillColor != null) {
                    style.setBackgroundColor(fillColor);
                    hasStyleData = true;
                    LOG.debug("ChartLegendParser.parseLegendStyle: set background color: {}", fillColor);
                }
            } else {
                XNode gradFillNode = spPrNode.childByTag("a:gradFill");
                if (gradFillNode != null) {
                    hasStyleData = true;
                    LOG.debug("ChartLegendParser.parseLegendStyle: found gradient fill (not fully implemented)");
                } else {
                    XNode noFillNode = spPrNode.childByTag("a:noFill");
                    if (noFillNode != null) {
                        style.setBackgroundColor(null); // Transparent
                        hasStyleData = true;
                        LOG.debug("ChartLegendParser.parseLegendStyle: no fill (transparent)");
                    }
                }
            }
            
            // Parse border properties
            XNode lnNode = spPrNode.childByTag("a:ln");
            if (lnNode != null) {
                parseLegendBorderStyle(style, lnNode);
                hasStyleData = true;
            }
            
            // Parse effects
            XNode effectLstNode = spPrNode.childByTag("a:effectLst");
            if (effectLstNode != null) {
                hasStyleData = true;
                LOG.debug("ChartLegendParser.parseLegendStyle: found effects list (not implemented)");
            }
            
            // Set style on legend if we have style data
            if (hasStyleData) {
                legend.setStyle(style);
                LOG.debug("ChartLegendParser.parseLegendStyle: applied style to legend");
            }
            
        } catch (Exception e) {
            LOG.warn("ChartLegendParser.parseLegendStyle: error parsing legend style", e);
        }
    }
    
    /**
     * Parses legend text properties.
     */
    public void parseLegendTextProperties(ChartLegendModel legend, XNode txPrNode) {
        LOG.debug("ChartLegendParser.parseLegendTextProperties: parsing legend text properties");
        
        if (txPrNode == null) {
            return;
        }
        
        try {
            // Parse text alignment from paragraph properties
            for (XNode pNode : txPrNode.childrenByTag("a:p")) {
                XNode pPrNode = pNode.childByTag("a:pPr");
                if (pPrNode != null) {
                    String algn = pPrNode.attrText("algn");
                    if (algn != null) {
                        io.nop.excel.model.constants.ExcelHorizontalAlignment alignment = mapTextAlignment(algn);
                        if (alignment != null) {
                            legend.setAlign(alignment);
                            LOG.debug("ChartLegendParser.parseLegendTextProperties: set text alignment: {}", alignment);
                        }
                    }
                }
            }
            
            // Parse font properties
            ExcelFont font = styleParser.parseFontProperties(txPrNode);
            if (font != null) {
                ChartShapeStyleModel style = legend.getStyle();
                if (style == null) {
                    style = new ChartShapeStyleModel();
                }
                style.setFont(font);
                legend.setStyle(style);
                LOG.debug("ChartLegendParser.parseLegendTextProperties: applied font properties to legend");
            }
            
        } catch (Exception e) {
            LOG.warn("ChartLegendParser.parseLegendTextProperties: error parsing legend text properties", e);
        }
    }
    
    /**
     * Parses legend entries.
     */
    public void parseLegendEntries(ChartLegendModel legend, XNode legendNode) {
        LOG.debug("ChartLegendParser.parseLegendEntries: parsing legend entries");
        
        try {
            for (XNode entryNode : legendNode.childrenByTag("c:legendEntry")) {
                parseLegendEntry(legend, entryNode);
            }
        } catch (Exception e) {
            LOG.warn("ChartLegendParser.parseLegendEntries: error parsing legend entries", e);
        }
    }
    
    /**
     * Parses a single legend entry.
     */
    public void parseLegendEntry(ChartLegendModel legend, XNode entryNode) {
        if (entryNode == null) {
            return;
        }
        
        try {
            // Parse entry index
            XNode idxNode = entryNode.childByTag("c:idx");
            if (idxNode != null) {
                String idx = idxNode.attrText("val");
                LOG.debug("ChartLegendParser.parseLegendEntry: legend entry index: {}", idx);
            }
            
            // Parse delete flag
            XNode deleteNode = entryNode.childByTag("c:delete");
            if (deleteNode != null) {
                Boolean delete = deleteNode.attrBoolean("val");
                boolean isDeleted = delete != null ? delete : false;
                LOG.debug("ChartLegendParser.parseLegendEntry: entry deleted: {}", isDeleted);
            }
            
            // Parse entry text properties
            XNode txPrNode = entryNode.childByTag("c:txPr");
            if (txPrNode != null) {
                LOG.debug("ChartLegendParser.parseLegendEntry: found entry text properties (not implemented)");
            }
            
        } catch (Exception e) {
            LOG.warn("ChartLegendParser.parseLegendEntry: error parsing legend entry", e);
        }
    }
    
    // Private helper methods
    
    private ChartLegendPosition mapLegendPosition(String excelPosition) {
        if (excelPosition == null) {
            return null;
        }
        
        switch (excelPosition) {
            case "t":
                return ChartLegendPosition.TOP;
            case "b":
                return ChartLegendPosition.BOTTOM;
            case "l":
                return ChartLegendPosition.LEFT;
            case "r":
                return ChartLegendPosition.RIGHT;
            case "tr":
                return ChartLegendPosition.TOP_RIGHT;
            case "tl":
                return ChartLegendPosition.TOP_LEFT;
            case "br":
                return ChartLegendPosition.BOTTOM_RIGHT;
            case "bl":
                return ChartLegendPosition.BOTTOM_LEFT;
            default:
                LOG.debug("ChartLegendParser.mapLegendPosition: unknown legend position {}, using RIGHT as default", excelPosition);
                return ChartLegendPosition.RIGHT;
        }
    }
    
    private ChartLegendPosition mapLayoutToPosition(double x, double y) {
        // Map coordinates to standard legend positions
        // Assuming coordinates are in 0.0 to 1.0 range where (0,0) is top-left
        
        if (x < 0.2) {
            if (y < 0.2) return ChartLegendPosition.TOP_LEFT;
            if (y > 0.8) return ChartLegendPosition.BOTTOM_LEFT;
            return ChartLegendPosition.LEFT;
        } else if (x > 0.8) {
            if (y < 0.2) return ChartLegendPosition.TOP_RIGHT;
            if (y > 0.8) return ChartLegendPosition.BOTTOM_RIGHT;
            return ChartLegendPosition.RIGHT;
        } else {
            if (y < 0.2) return ChartLegendPosition.TOP;
            if (y > 0.8) return ChartLegendPosition.BOTTOM;
        }
        
        // If coordinates don't map to standard positions, return null
        return null;
    }
    
    private void parseLegendBorderStyle(ChartShapeStyleModel style, XNode lnNode) {
        if (lnNode == null) {
            return;
        }
        
        // Parse line width
        Long width = lnNode.attrLong("w");
        if (width != null) {
            double widthInPoints = UnitsHelper.emuToPoints(width.intValue());
            LOG.debug("ChartLegendParser.parseLegendBorderStyle: border width: {} points (converted from {} EMUs)", 
                widthInPoints, width);
        }
        
        // Parse line color
        String borderColor = styleParser.parseColorFromFillNode(lnNode);
        if (borderColor != null) {
            style.setBorderColor(borderColor);
            LOG.debug("ChartLegendParser.parseLegendBorderStyle: set border color: {}", borderColor);
        }
        
        // Parse line dash style
        XNode prstDashNode = lnNode.childByTag("a:prstDash");
        if (prstDashNode != null) {
            String dashStyle = prstDashNode.attrText("val");
            if (dashStyle != null) {
                LOG.debug("ChartLegendParser.parseLegendBorderStyle: border dash style: {} (not stored in model)", dashStyle);
            }
        }
    }
    
    private io.nop.excel.model.constants.ExcelHorizontalAlignment mapTextAlignment(String excelAlignment) {
        if (excelAlignment == null) {
            return null;
        }
        
        switch (excelAlignment) {
            case "l":
                return io.nop.excel.model.constants.ExcelHorizontalAlignment.LEFT;
            case "r":
                return io.nop.excel.model.constants.ExcelHorizontalAlignment.RIGHT;
            case "ctr":
                return io.nop.excel.model.constants.ExcelHorizontalAlignment.CENTER;
            case "just":
                return io.nop.excel.model.constants.ExcelHorizontalAlignment.JUSTIFY;
            default:
                LOG.debug("ChartLegendParser.mapTextAlignment: unknown alignment {}, using LEFT as default", excelAlignment);
                return io.nop.excel.model.constants.ExcelHorizontalAlignment.LEFT;
        }
    }
}