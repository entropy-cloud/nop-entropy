/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartAxisPosition;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for chart axis elements and related components.
 * Handles axis parsing, scaling, titles, labels, grid lines, and tick marks.
 */
public class ChartAxisParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartAxisParser.class);
    
    /**
     * Singleton instance for reuse across multiple parsing operations.
     */
    public static final ChartAxisParser INSTANCE = new ChartAxisParser(ChartStyleParser.INSTANCE);
    
    private final ChartStyleParser styleParser;
    
    public ChartAxisParser(ChartStyleParser styleParser) {
        this.styleParser = styleParser;
    }
    
    /**
     * Parses a chart axis element and creates a fully populated ChartAxisModel.
     */
    public void parseAxis(ChartModel chart, XNode axisNode, ChartAxisType axisType) {
        LOG.debug("ChartAxisParser.parseAxis: parsing {} axis", axisType);
        
        if (axisNode == null) {
            LOG.warn("ChartAxisParser.parseAxis: axis node is null for type {}", axisType);
            return;
        }
        
        try {
            ChartAxisModel axis = new ChartAxisModel();
            
            // Set axis type
            axis.setType(axisType);
            
            // Parse axis ID
            XNode axisIdNode = axisNode.childByTag("c:axId");
            if (axisIdNode != null) {
                String axisId = axisIdNode.attrText("val");
                if (axisId != null) {
                    axis.setId(axisId);
                    LOG.debug("ChartAxisParser.parseAxis: set axis ID: {}", axisId);
                }
            }
            
            // Parse axis position
            XNode axPosNode = axisNode.childByTag("c:axPos");
            if (axPosNode != null) {
                String position = axPosNode.attrText("val");
                if (position != null) {
                    axis.setPosition(mapAxisPosition(position));
                    LOG.debug("ChartAxisParser.parseAxis: set axis position: {}", position);
                }
            }
            
            // Parse axis scaling
            parseAxisScaling(axis, axisNode);
            
            // Parse axis title
            parseAxisTitle(axis, axisNode);
            
            // Parse axis labels
            parseAxisLabels(axis, axisNode);
            
            // Parse grid lines
            parseAxisGridLines(axis, axisNode);
            
            // Parse tick marks
            parseAxisTickMarks(axis, axisNode);
            
            // Add axis to chart
            chart.addAxis(axis);
            
            LOG.info("ChartAxisParser.parseAxis: successfully parsed {} axis with ID '{}'", axisType, axis.getId());
            
        } catch (Exception e) {
            LOG.error("ChartAxisParser.parseAxis: error parsing {} axis", axisType, e);
        }
    }
    
    /**
     * Parses axis scaling information.
     */
    public void parseAxisScaling(ChartAxisModel axis, XNode axisNode) {
        LOG.debug("ChartAxisParser.parseAxisScaling: parsing axis scaling for axis '{}'", axis.getId());
        
        try {
            XNode scalingNode = axisNode.childByTag("c:scaling");
            if (scalingNode == null) {
                LOG.debug("ChartAxisParser.parseAxisScaling: no scaling node found for axis '{}'", axis.getId());
                return;
            }
            
            ChartAxisScaleModel scale = new ChartAxisScaleModel();
            boolean hasScaleData = false;
            
            // Parse minimum value
            XNode minNode = scalingNode.childByTag("c:min");
            if (minNode != null) {
                Double min = minNode.attrDouble("val");
                if (min != null) {
                    scale.setMin(min);
                    hasScaleData = true;
                    LOG.debug("ChartAxisParser.parseAxisScaling: set minimum value: {}", min);
                }
            }
            
            // Parse maximum value
            XNode maxNode = scalingNode.childByTag("c:max");
            if (maxNode != null) {
                Double max = maxNode.attrDouble("val");
                if (max != null) {
                    scale.setMax(max);
                    hasScaleData = true;
                    LOG.debug("ChartAxisParser.parseAxisScaling: set maximum value: {}", max);
                }
            }
            
            // Parse logarithmic base
            XNode logBaseNode = scalingNode.childByTag("c:logBase");
            if (logBaseNode != null) {
                Double logBase = logBaseNode.attrDouble("val");
                if (logBase != null) {
                    scale.setType("logarithmic");
                    scale.setBase(logBase);
                    hasScaleData = true;
                    LOG.debug("ChartAxisParser.parseAxisScaling: set logarithmic base: {}", logBase);
                }
            }
            
            // Parse orientation (normal vs reversed)
            XNode orientationNode = scalingNode.childByTag("c:orientation");
            if (orientationNode != null) {
                String orientation = orientationNode.attrText("val");
                if ("maxMin".equals(orientation)) {
                    LOG.debug("ChartAxisParser.parseAxisScaling: axis is reversed (not stored in model)");
                }
            }
            
            // Parse major unit from axis node
            XNode majorUnitNode = axisNode.childByTag("c:majorUnit");
            if (majorUnitNode != null) {
                Double majorUnit = majorUnitNode.attrDouble("val");
                if (majorUnit != null) {
                    scale.setInterval(majorUnit);
                    hasScaleData = true;
                    LOG.debug("ChartAxisParser.parseAxisScaling: set major unit (interval): {}", majorUnit);
                }
            }
            
            // Parse minor unit from axis node
            XNode minorUnitNode = axisNode.childByTag("c:minorUnit");
            if (minorUnitNode != null) {
                Double minorUnit = minorUnitNode.attrDouble("val");
                if (minorUnit != null) {
                    // If major unit is not set, use minor unit as interval
                    if (scale.getInterval() == null) {
                        scale.setInterval(minorUnit);
                        hasScaleData = true;
                    }
                    LOG.debug("ChartAxisParser.parseAxisScaling: found minor unit: {}", minorUnit);
                }
            }
            
            // Set scale on axis if we have scale data
            if (hasScaleData) {
                axis.setScale(scale);
                LOG.debug("ChartAxisParser.parseAxisScaling: applied scaling to axis '{}'", axis.getId());
            }
            
        } catch (Exception e) {
            LOG.error("ChartAxisParser.parseAxisScaling: error parsing axis scaling for axis '{}'", axis.getId(), e);
        }
    }
    
    /**
     * Parses axis title information.
     */
    public void parseAxisTitle(ChartAxisModel axis, XNode axisNode) {
        LOG.debug("ChartAxisParser.parseAxisTitle: parsing axis title for axis '{}'", axis.getId());
        
        try {
            XNode titleNode = axisNode.childByTag("c:title");
            if (titleNode == null) {
                LOG.debug("ChartAxisParser.parseAxisTitle: no title node found for axis '{}'", axis.getId());
                return;
            }
            
            ChartAxisTitleModel title = new ChartAxisTitleModel();
            boolean hasTitleData = false;
            
            // Parse title text
            XNode txNode = titleNode.childByTag("c:tx");
            if (txNode != null) {
                String titleText = parseAxisTitleText(txNode);
                if (titleText != null && !titleText.trim().isEmpty()) {
                    title.setText(titleText);
                    hasTitleData = true;
                    LOG.debug("ChartAxisParser.parseAxisTitle: set title text: '{}'", titleText);
                }
            }
            
            // Parse title layout
            XNode layoutNode = titleNode.childByTag("c:layout");
            if (layoutNode != null) {
                LOG.debug("ChartAxisParser.parseAxisTitle: found title layout element (not implemented)");
            }
            
            // Parse title overlay
            XNode overlayNode = titleNode.childByTag("c:overlay");
            if (overlayNode != null) {
                Boolean overlay = overlayNode.attrBoolean("val");
                if (Boolean.TRUE.equals(overlay)) {
                    LOG.debug("ChartAxisParser.parseAxisTitle: title overlay enabled (not stored in model)");
                }
            }
            
            // Set title on axis if we have title data
            if (hasTitleData) {
                axis.setTitle(title);
                LOG.debug("ChartAxisParser.parseAxisTitle: applied title to axis '{}'", axis.getId());
            }
            
        } catch (Exception e) {
            LOG.error("ChartAxisParser.parseAxisTitle: error parsing axis title for axis '{}'", axis.getId(), e);
        }
    }
    
    /**
     * Parses axis labels configuration.
     */
    public void parseAxisLabels(ChartAxisModel axis, XNode axisNode) {
        LOG.debug("ChartAxisParser.parseAxisLabels: parsing axis labels for axis '{}'", axis.getId());
        
        try {
            ChartAxisLabelsModel labels = new ChartAxisLabelsModel();
            boolean hasLabelData = false;
            
            // Parse tick label position (visibility)
            XNode tickLblPosNode = axisNode.childByTag("c:tickLblPos");
            if (tickLblPosNode != null) {
                String position = tickLblPosNode.attrText("val");
                if (position != null) {
                    switch (position) {
                        case "low":
                        case "high":
                            labels.setVisible(true);
                            hasLabelData = true;
                            LOG.debug("ChartAxisParser.parseAxisLabels: labels visible at position: {}", position);
                            break;
                        case "none":
                            labels.setVisible(false);
                            hasLabelData = true;
                            LOG.debug("ChartAxisParser.parseAxisLabels: labels hidden");
                            break;
                        default:
                            LOG.debug("ChartAxisParser.parseAxisLabels: label position {} (not stored in model)", position);
                            break;
                    }
                }
            }
            
            // Parse number format
            XNode numFmtNode = axisNode.childByTag("c:numFmt");
            if (numFmtNode != null) {
                String formatCode = numFmtNode.attrText("formatCode");
                if (formatCode != null && !formatCode.trim().isEmpty()) {
                    labels.setFormat(formatCode);
                    hasLabelData = true;
                    LOG.debug("ChartAxisParser.parseAxisLabels: set number format: {}", formatCode);
                }
                
                Boolean sourceLinked = numFmtNode.attrBoolean("sourceLinked");
                if (Boolean.FALSE.equals(sourceLinked)) {
                    LOG.debug("ChartAxisParser.parseAxisLabels: number format not source linked (not stored in model)");
                }
            }
            
            // Parse text properties
            XNode txPrNode = axisNode.childByTag("c:txPr");
            if (txPrNode != null) {
                hasLabelData = true;
                LOG.debug("ChartAxisParser.parseAxisLabels: found text properties element (not fully implemented)");
            }
            
            // Set labels on axis if we have label data
            if (hasLabelData) {
                axis.setLabels(labels);
                LOG.debug("ChartAxisParser.parseAxisLabels: applied labels to axis '{}'", axis.getId());
            }
            
        } catch (Exception e) {
            LOG.error("ChartAxisParser.parseAxisLabels: error parsing axis labels for axis '{}'", axis.getId(), e);
        }
    }
    
    /**
     * Parses axis grid lines configuration.
     */
    public void parseAxisGridLines(ChartAxisModel axis, XNode axisNode) {
        LOG.debug("ChartAxisParser.parseAxisGridLines: parsing grid lines for axis '{}'", axis.getId());
        
        try {
            ChartGridModel grid = new ChartGridModel();
            boolean hasGridData = false;
            
            // Parse major grid lines
            XNode majorGridNode = axisNode.childByTag("c:majorGridlines");
            if (majorGridNode != null) {
                grid.setVisible(true);
                hasGridData = true;
                LOG.debug("ChartAxisParser.parseAxisGridLines: major grid lines enabled");
                
                // Parse major grid line styling
                XNode spPrNode = majorGridNode.childByTag("c:spPr");
                if (spPrNode != null) {
                    LOG.debug("ChartAxisParser.parseAxisGridLines: found major grid line styling (not implemented)");
                }
            }
            
            // Parse minor grid lines
            XNode minorGridNode = axisNode.childByTag("c:minorGridlines");
            if (minorGridNode != null) {
                LOG.debug("ChartAxisParser.parseAxisGridLines: minor grid lines found (not separately stored in model)");
                
                // Parse minor grid line styling
                XNode spPrNode = minorGridNode.childByTag("c:spPr");
                if (spPrNode != null) {
                    LOG.debug("ChartAxisParser.parseAxisGridLines: found minor grid line styling (not implemented)");
                }
            }
            
            // Set grid on axis if we have grid data
            if (hasGridData) {
                axis.setGrid(grid);
                LOG.debug("ChartAxisParser.parseAxisGridLines: applied grid lines to axis '{}'", axis.getId());
            }
            
        } catch (Exception e) {
            LOG.error("ChartAxisParser.parseAxisGridLines: error parsing grid lines for axis '{}'", axis.getId(), e);
        }
    }
    
    /**
     * Parses axis tick marks configuration.
     */
    public void parseAxisTickMarks(ChartAxisModel axis, XNode axisNode) {
        LOG.debug("ChartAxisParser.parseAxisTickMarks: parsing tick marks for axis '{}'", axis.getId());
        
        try {
            ChartTicksModel ticks = new ChartTicksModel();
            boolean hasTickData = false;
            
            // Parse major tick marks
            XNode majorTickNode = axisNode.childByTag("c:majorTickMark");
            if (majorTickNode != null) {
                String majorTickType = majorTickNode.attrText("val");
                if (majorTickType != null && !"none".equals(majorTickType)) {
                    ticks.setVisible(true);
                    hasTickData = true;
                    LOG.debug("ChartAxisParser.parseAxisTickMarks: major tick type: {}", majorTickType);
                }
            }
            
            // Parse minor tick marks
            XNode minorTickNode = axisNode.childByTag("c:minorTickMark");
            if (minorTickNode != null) {
                String minorTickType = minorTickNode.attrText("val");
                if (minorTickType != null) {
                    LOG.debug("ChartAxisParser.parseAxisTickMarks: minor tick type: {} (not separately stored)", minorTickType);
                }
            }
            
            // Set ticks on axis if we have tick data
            if (hasTickData) {
                axis.setTicks(ticks);
                LOG.debug("ChartAxisParser.parseAxisTickMarks: applied tick marks to axis '{}'", axis.getId());
            }
            
        } catch (Exception e) {
            LOG.error("ChartAxisParser.parseAxisTickMarks: error parsing tick marks for axis '{}'", axis.getId(), e);
        }
    }
    
    // Private helper methods
    
    private String parseAxisTitleText(XNode txNode) {
        if (txNode == null) {
            return null;
        }
        
        try {
            // Check for string reference first
            XNode strRefNode = txNode.childByTag("c:strRef");
            if (strRefNode != null) {
                XNode fNode = strRefNode.childByTag("c:f");
                if (fNode != null) {
                    String formula = fNode.contentText();
                    LOG.debug("ChartAxisParser.parseAxisTitleText: found title formula: {}", formula);
                    
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
                                    LOG.debug("ChartAxisParser.parseAxisTitleText: found cached title: {}", cachedValue);
                                    return cachedValue;
                                }
                            }
                        }
                    }
                }
            }
            
            // Check for literal value
            XNode vNode = txNode.childByTag("c:v");
            if (vNode != null) {
                String literalValue = vNode.contentText();
                LOG.debug("ChartAxisParser.parseAxisTitleText: found literal title: {}", literalValue);
                return literalValue;
            }
            
            // Check for rich text
            XNode richNode = txNode.childByTag("c:rich");
            if (richNode != null) {
                return parseAxisTitleRichText(richNode);
            }
            
            LOG.debug("ChartAxisParser.parseAxisTitleText: no title text found");
            return null;
            
        } catch (Exception e) {
            LOG.warn("ChartAxisParser.parseAxisTitleText: error parsing axis title text", e);
            return null;
        }
    }
    
    private String parseAxisTitleRichText(XNode richNode) {
        if (richNode == null) {
            return null;
        }
        
        try {
            StringBuilder titleText = new StringBuilder();
            
            XNode bodyPrNode = richNode.childByTag("a:bodyPr");
            XNode lstStyleNode = richNode.childByTag("a:lstStyle");
            
            for (XNode pNode : richNode.childrenByTag("a:p")) {
                for (XNode rNode : pNode.childrenByTag("a:r")) {
                    XNode tNode = rNode.childByTag("a:t");
                    if (tNode != null) {
                        String text = tNode.contentText();
                        if (text != null) {
                            titleText.append(text);
                        }
                    }
                }
            }
            
            String result = titleText.toString().trim();
            LOG.debug("ChartAxisParser.parseAxisTitleRichText: extracted rich text: '{}'", result);
            return result.isEmpty() ? null : result;
            
        } catch (Exception e) {
            LOG.warn("ChartAxisParser.parseAxisTitleRichText: error parsing rich text", e);
            return null;
        }
    }
    
    private ChartAxisPosition mapAxisPosition(String excelPosition) {
        if (excelPosition == null) {
            return null;
        }
        
        switch (excelPosition) {
            case "l": return ChartAxisPosition.LEFT;
            case "r": return ChartAxisPosition.RIGHT;
            case "t": return ChartAxisPosition.TOP;
            case "b": return ChartAxisPosition.BOTTOM;
            default:
                LOG.debug("ChartAxisParser.mapAxisPosition: unknown position {}, using as-is", excelPosition);
                try {
                    return ChartAxisPosition.fromValue(excelPosition);
                } catch (IllegalArgumentException e) {
                    LOG.warn("ChartAxisParser.mapAxisPosition: invalid position {}, using default", excelPosition);
                    return ChartAxisPosition.BOTTOM;
                }
        }
    }
}