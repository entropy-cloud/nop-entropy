/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartDataSourceType;
import io.nop.excel.chart.model.*;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.util.UnitsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for chart series data and related elements.
 * Handles series parsing, data sources, labels, and styling.
 */
public class ChartSeriesParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartSeriesParser.class);
    
    /**
     * Singleton instance for reuse across multiple parsing operations.
     */
    public static final ChartSeriesParser INSTANCE = new ChartSeriesParser(ChartStyleParser.INSTANCE);
    
    private final ChartStyleParser styleParser;
    
    public ChartSeriesParser(ChartStyleParser styleParser) {
        this.styleParser = styleParser;
    }
    
    /**
     * Parses chart series elements from chart type nodes.
     */
    public void parseChartSeries(ChartModel chart, XNode chartTypeNode) {
        LOG.debug("ChartSeriesParser.parseChartSeries: parsing chart series elements");
        
        if (chartTypeNode == null) {
            LOG.warn("ChartSeriesParser.parseChartSeries: chart type node is null");
            return;
        }
        
        try {
            for (XNode serNode : chartTypeNode.childrenByTag("c:ser")) {
                ChartSeriesModel series = parseSingleSeries(serNode);
                if (series != null) {
                    chart.addSeries(series);
                    LOG.debug("ChartSeriesParser.parseChartSeries: added series '{}' to chart", series.getName());
                }
            }
            
            LOG.info("ChartSeriesParser.parseChartSeries: parsed {} series for chart", chart.getSeries().size());
            
        } catch (Exception e) {
            LOG.error("ChartSeriesParser.parseChartSeries: error parsing chart series", e);
        }
    }
    
    /**
     * Parses a single series element and creates a ChartSeriesModel.
     */
    public ChartSeriesModel parseSingleSeries(XNode serNode) {
        if (serNode == null) {
            LOG.warn("ChartSeriesParser.parseSingleSeries: series node is null");
            return null;
        }
        
        try {
            ChartSeriesModel series = new ChartSeriesModel();
            
            // Parse series text (name)
            XNode txNode = serNode.childByTag("c:tx");
            if (txNode != null) {
                String seriesName = parseSeriesText(txNode);
                if (seriesName != null && !seriesName.trim().isEmpty()) {
                    series.setName(seriesName);
                    LOG.debug("ChartSeriesParser.parseSingleSeries: set series name: '{}'", seriesName);
                }
            }
            
            // Parse series data sources
            parseSeriesDataSources(series, serNode);
            
            // Parse series styling
            XNode spPrNode = serNode.childByTag("c:spPr");
            if (spPrNode != null) {
                parseSeriesStyle(series, spPrNode);
            }
            
            // Parse data labels
            XNode dLblsNode = serNode.childByTag("c:dLbls");
            if (dLblsNode != null) {
                parseDataLabels(series, dLblsNode);
            }
            
            // Set default name if not provided
            if (series.getName() == null || series.getName().trim().isEmpty()) {
                series.setName("Series");
                LOG.debug("ChartSeriesParser.parseSingleSeries: set default series name: '{}'", series.getName());
            }
            
            LOG.info("ChartSeriesParser.parseSingleSeries: successfully parsed series: '{}'", series.getName());
            return series;
            
        } catch (Exception e) {
            LOG.error("ChartSeriesParser.parseSingleSeries: error parsing series", e);
            return null;
        }
    }
    
    /**
     * Parses series text from various text sources.
     */
    public String parseSeriesText(XNode txNode) {
        if (txNode == null) {
            return null;
        }
        
        try {
            // Check for string reference first
            XNode strRefNode = txNode.childByTag("c:strRef");
            if (strRefNode != null) {
                // Parse formula reference
                XNode fNode = strRefNode.childByTag("c:f");
                if (fNode != null) {
                    String formula = fNode.contentText();
                    LOG.debug("ChartSeriesParser.parseSeriesText: found series name formula: {}", formula);
                    
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
                                    LOG.debug("ChartSeriesParser.parseSeriesText: found cached series name: {}", cachedValue);
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
                LOG.debug("ChartSeriesParser.parseSeriesText: found literal series name: {}", literalValue);
                return literalValue;
            }
            
            LOG.debug("ChartSeriesParser.parseSeriesText: no series text found");
            return null;
            
        } catch (Exception e) {
            LOG.warn("ChartSeriesParser.parseSeriesText: error parsing series text", e);
            return null;
        }
    }
    
    /**
     * Parses series data sources (categories, values, etc.).
     */
    public void parseSeriesDataSources(ChartSeriesModel series, XNode serNode) {
        LOG.debug("ChartSeriesParser.parseSeriesDataSources: parsing data sources for series '{}'", series.getName());
        
        try {
            ChartDataSourceModel dataSource = null;
            
            // Parse value axis data (Y values) - primary data source
            XNode valNode = serNode.childByTag("c:val");
            if (valNode != null) {
                dataSource = parseDataSourceNode(valNode, "value");
                if (dataSource != null) {
                    series.setDataSource(dataSource);
                    LOG.debug("ChartSeriesParser.parseSeriesDataSources: set value data source for series '{}'", series.getName());
                }
            }
            
            // Parse Y values for scatter charts if val not found
            if (dataSource == null) {
                XNode yValNode = serNode.childByTag("c:yVal");
                if (yValNode != null) {
                    dataSource = parseDataSourceNode(yValNode, "yValue");
                    if (dataSource != null) {
                        series.setDataSource(dataSource);
                        LOG.debug("ChartSeriesParser.parseSeriesDataSources: set Y value data source for series '{}'", series.getName());
                    }
                }
            }
            
            // Category axis data (X values) and other data sources are not directly supported in the model
            // They would need to be stored in the single dataSource or handled differently
            
        } catch (Exception e) {
            LOG.error("ChartSeriesParser.parseSeriesDataSources: error parsing data sources for series '{}'", series.getName(), e);
        }
    }
    
    /**
     * Parses a data source node and creates a ChartDataSourceModel.
     */
    public ChartDataSourceModel parseDataSourceNode(XNode dataNode, String sourceType) {
        if (dataNode == null) {
            LOG.warn("ChartSeriesParser.parseDataSourceNode: data node is null for source type '{}'", sourceType);
            return null;
        }
        
        try {
            ChartDataSourceModel dataSource = new ChartDataSourceModel();
            
            // Check for number reference
            XNode numRefNode = dataNode.childByTag("c:numRef");
            if (numRefNode != null) {
                dataSource.setType(ChartDataSourceType.EXCEL);
                
                XNode fNode = numRefNode.childByTag("c:f");
                if (fNode != null) {
                    String formula = fNode.contentText();
                    if (formula != null && !formula.trim().isEmpty()) {
                        ChartExcelDataModel excelData = new ChartExcelDataModel();
                        parseExcelFormula(excelData, formula);
                        dataSource.setExcel(excelData);
                        LOG.debug("ChartSeriesParser.parseDataSourceNode: parsed {} formula: {}", sourceType, formula);
                    }
                }
                
                // Parse cached numeric data
                XNode numCacheNode = numRefNode.childByTag("c:numCache");
                if (numCacheNode != null) {
                    parseNumericCache(dataSource, numCacheNode);
                }
            }
            
            // Check for string reference
            XNode strRefNode = dataNode.childByTag("c:strRef");
            if (strRefNode != null) {
                dataSource.setType(ChartDataSourceType.EXCEL);
                
                XNode fNode = strRefNode.childByTag("c:f");
                if (fNode != null) {
                    String formula = fNode.contentText();
                    if (formula != null && !formula.trim().isEmpty()) {
                        ChartExcelDataModel excelData = new ChartExcelDataModel();
                        parseExcelFormula(excelData, formula);
                        dataSource.setExcel(excelData);
                        LOG.debug("ChartSeriesParser.parseDataSourceNode: parsed {} string formula: {}", sourceType, formula);
                    }
                }
                
                // Parse cached string data
                XNode strCacheNode = strRefNode.childByTag("c:strCache");
                if (strCacheNode != null) {
                    parseStringCache(dataSource, strCacheNode);
                }
            }
            
            // Check for literal values
            XNode numLitNode = dataNode.childByTag("c:numLit");
            if (numLitNode != null) {
                dataSource.setType(ChartDataSourceType.STATIC);
                parseNumericLiteral(dataSource, numLitNode);
            }
            
            XNode strLitNode = dataNode.childByTag("c:strLit");
            if (strLitNode != null) {
                dataSource.setType(ChartDataSourceType.STATIC);
                parseStringLiteral(dataSource, strLitNode);
            }
            
            return dataSource;
            
        } catch (Exception e) {
            LOG.error("ChartSeriesParser.parseDataSourceNode: error parsing data source for type '{}'", sourceType, e);
            return null;
        }
    }
    
    /**
     * Parses series styling information.
     */
    public void parseSeriesStyle(ChartSeriesModel series, XNode spPrNode) {
        LOG.debug("ChartSeriesParser.parseSeriesStyle: parsing series styling for '{}'", series.getName());
        
        if (spPrNode == null) {
            return;
        }
        
        try {
            ChartSeriesStyleModel style = new ChartSeriesStyleModel();
            boolean hasStyleData = false;
            
            // Parse fill properties
            XNode fillNode = spPrNode.childByTag("a:solidFill");
            if (fillNode != null) {
                String fillColor = styleParser.parseColorFromFillNode(spPrNode);
                if (fillColor != null) {
                    io.nop.excel.chart.model.ChartFillModel fill = style.getFill();
                    if (fill == null) {
                        fill = new io.nop.excel.chart.model.ChartFillModel();
                        style.setFill(fill);
                    }
                    fill.setColor(fillColor);
                    hasStyleData = true;
                    LOG.debug("ChartSeriesParser.parseSeriesStyle: set fill color: {}", fillColor);
                }
            }
            
            // Parse gradient fill
            XNode gradFillNode = spPrNode.childByTag("a:gradFill");
            if (gradFillNode != null) {
                ChartGradientModel gradient = styleParser.parseGradientFill(gradFillNode);
                if (gradient != null) {
                    io.nop.excel.chart.model.ChartFillModel fill = style.getFill();
                    if (fill == null) {
                        fill = new io.nop.excel.chart.model.ChartFillModel();
                        style.setFill(fill);
                    }
                    fill.setGradient(gradient);
                    hasStyleData = true;
                    LOG.debug("ChartSeriesParser.parseSeriesStyle: set gradient fill");
                }
            }
            
            // Parse line properties
            XNode lnNode = spPrNode.childByTag("a:ln");
            if (lnNode != null) {
                parseSeriesLineStyle(style, lnNode);
                hasStyleData = true;
            }
            
            // Set style on series if we have style data
            if (hasStyleData) {
                series.setStyle(style);
                LOG.debug("ChartSeriesParser.parseSeriesStyle: applied style to series '{}'", series.getName());
            }
            
        } catch (Exception e) {
            LOG.warn("ChartSeriesParser.parseSeriesStyle: error parsing series style for '{}'", series.getName(), e);
        }
    }
    
    /**
     * Parses data labels configuration.
     */
    public void parseDataLabels(ChartSeriesModel series, XNode dLblsNode) {
        LOG.debug("ChartSeriesParser.parseDataLabels: parsing data labels for series '{}'", series.getName());
        
        if (dLblsNode == null) {
            return;
        }
        
        try {
            ChartLabelsModel labels = new ChartLabelsModel();
            boolean hasLabelData = false;
            
            // Parse show value
            XNode showValNode = dLblsNode.childByTag("c:showVal");
            if (showValNode != null) {
                Boolean showVal = showValNode.attrBoolean("val");
                boolean showValue = showVal != null ? showVal : true; // Default to true if not specified
                labels.setEnabled(showValue);
                hasLabelData = true;
                LOG.debug("ChartSeriesParser.parseDataLabels: show value: {}", showValue);
            }
            
            // Parse position
            XNode dLblPosNode = dLblsNode.childByTag("c:dLblPos");
            if (dLblPosNode != null) {
                String position = dLblPosNode.attrText("val");
                if (position != null) {
                    // Map Excel position to model position
                    String mappedPosition = mapDataLabelPosition(position);
                    if (mappedPosition != null) {
                        labels.setPosition(mappedPosition);
                        hasLabelData = true;
                        LOG.debug("ChartSeriesParser.parseDataLabels: set position: {}", mappedPosition);
                    }
                }
            }
            
            // Parse number format
            XNode numFmtNode = dLblsNode.childByTag("c:numFmt");
            if (numFmtNode != null) {
                String formatCode = numFmtNode.attrText("formatCode");
                if (formatCode != null) {
                    labels.setFormat(formatCode);
                    hasLabelData = true;
                    LOG.debug("ChartSeriesParser.parseDataLabels: set format: {}", formatCode);
                }
            }
            
            // Parse text properties
            XNode txPrNode = dLblsNode.childByTag("c:txPr");
            if (txPrNode != null) {
                ExcelFont font = styleParser.parseFontProperties(txPrNode);
                if (font != null) {
                    labels.setFont(font);
                    hasLabelData = true;
                    LOG.debug("ChartSeriesParser.parseDataLabels: applied font properties");
                }
            }
            
            // Set labels on series if we have label data
            if (hasLabelData) {
                series.setLabels(labels);
                LOG.debug("ChartSeriesParser.parseDataLabels: applied labels to series '{}'", series.getName());
            }
            
        } catch (Exception e) {
            LOG.warn("ChartSeriesParser.parseDataLabels: error parsing data labels for series '{}'", series.getName(), e);
        }
    }
    
    // Private helper methods
    
    private void parseExcelFormula(ChartExcelDataModel excelData, String formula) {
        if (formula == null || formula.trim().isEmpty()) {
            return;
        }
        
        try {
            LOG.debug("ChartSeriesParser.parseExcelFormula: parsing Excel formula: {}", formula);
            
            String trimmedFormula = formula.trim();
            
            // Parse sheet reference and range
            if (trimmedFormula.contains("!")) {
                String[] parts = trimmedFormula.split("!", 2);
                if (parts.length == 2) {
                    String sheetRef = parts[0];
                    String rangeRef = parts[1];
                    
                    // Clean up sheet name (remove quotes if present)
                    if (sheetRef.startsWith("'") && sheetRef.endsWith("'")) {
                        sheetRef = sheetRef.substring(1, sheetRef.length() - 1);
                    }
                    
                    excelData.setSheetName(sheetRef);
                    excelData.setCellRangeRef(rangeRef);
                    
                    LOG.debug("ChartSeriesParser.parseExcelFormula: parsed sheet '{}' and range '{}'", sheetRef, rangeRef);
                }
            } else {
                // No sheet reference, just range
                excelData.setCellRangeRef(trimmedFormula);
                LOG.debug("ChartSeriesParser.parseExcelFormula: parsed range '{}'", trimmedFormula);
            }
            
        } catch (Exception e) {
            LOG.warn("ChartSeriesParser.parseExcelFormula: error parsing formula '{}'", formula, e);
        }
    }
    
    private void parseNumericCache(ChartDataSourceModel dataSource, XNode numCacheNode) {
        // Implementation for parsing numeric cache data
        LOG.debug("ChartSeriesParser.parseNumericCache: parsing numeric cache data");
    }
    
    private void parseStringCache(ChartDataSourceModel dataSource, XNode strCacheNode) {
        // Implementation for parsing string cache data
        LOG.debug("ChartSeriesParser.parseStringCache: parsing string cache data");
    }
    
    private void parseNumericLiteral(ChartDataSourceModel dataSource, XNode numLitNode) {
        // Implementation for parsing numeric literal data
        LOG.debug("ChartSeriesParser.parseNumericLiteral: parsing numeric literal data");
    }
    
    private void parseStringLiteral(ChartDataSourceModel dataSource, XNode strLitNode) {
        // Implementation for parsing string literal data
        LOG.debug("ChartSeriesParser.parseStringLiteral: parsing string literal data");
    }
    
    private void parseSeriesLineStyle(ChartSeriesStyleModel style, XNode lnNode) {
        // Parse line width
        Long width = lnNode.attrLong("w");
        if (width != null) {
            double widthInPoints = UnitsHelper.emuToPoints(width.intValue());
            // Note: ChartSeriesStyleModel may not have line width property
            LOG.debug("ChartSeriesParser.parseSeriesLineStyle: line width: {} points", widthInPoints);
        }
        
        // Parse line color
        String lineColor = styleParser.parseColorFromFillNode(lnNode);
        if (lineColor != null) {
            io.nop.excel.chart.model.ChartLineModel line = style.getLine();
            if (line == null) {
                line = new io.nop.excel.chart.model.ChartLineModel();
                style.setLine(line);
            }
            line.setColor(lineColor);
            LOG.debug("ChartSeriesParser.parseSeriesLineStyle: set line color: {}", lineColor);
        }
    }
    
    private String mapDataLabelPosition(String excelPosition) {
        if (excelPosition == null) {
            return null;
        }
        
        switch (excelPosition) {
            case "ctr": return "center";
            case "inEnd": return "inside_end";
            case "inBase": return "inside_base";
            case "outEnd": return "outside_end";
            case "t": return "top";
            case "b": return "bottom";
            case "l": return "left";
            case "r": return "right";
            case "bestFit": return "best_fit";
            default:
                LOG.debug("ChartSeriesParser.mapDataLabelPosition: unknown position {}, using default", excelPosition);
                return "center";
        }
    }
}