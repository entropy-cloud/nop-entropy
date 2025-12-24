/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for different chart types (bar, line, pie, scatter, area).
 * Handles chart type detection and type-specific parsing.
 */
public class ChartTypeParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTypeParser.class);
    
    /**
     * Singleton instance for reuse across multiple parsing operations.
     */
    public static final ChartTypeParser INSTANCE = new ChartTypeParser(ChartSeriesParser.INSTANCE);
    
    private final ChartSeriesParser seriesParser;
    
    public ChartTypeParser(ChartSeriesParser seriesParser) {
        this.seriesParser = seriesParser;
    }
    
    /**
     * Parses the plot area element to detect chart types and parse chart-specific content.
     */
    public void parsePlotArea(ChartModel chart, XNode plotAreaNode) {
        LOG.debug("ChartTypeParser.parsePlotArea: parsing plot area for chart type detection");
        
        if (plotAreaNode == null) {
            LOG.warn("ChartTypeParser.parsePlotArea: plot area node is null");
            return;
        }
        
        try {
            for (XNode child : plotAreaNode.getChildren()) {
                String tagName = child.getTagName();
                LOG.debug("ChartTypeParser.parsePlotArea: examining plot area child: {}", tagName);
                
                switch (tagName) {
                    case "c:barChart":
                        LOG.debug("ChartTypeParser.parsePlotArea: detected bar chart");
                        chart.setType(ChartType.BAR);
                        parseBarChart(chart, child);
                        break;
                        
                    case "c:lineChart":
                        LOG.debug("ChartTypeParser.parsePlotArea: detected line chart");
                        chart.setType(ChartType.LINE);
                        parseLineChart(chart, child);
                        break;
                        
                    case "c:pieChart":
                        LOG.debug("ChartTypeParser.parsePlotArea: detected pie chart");
                        chart.setType(ChartType.PIE);
                        parsePieChart(chart, child);
                        break;
                        
                    case "c:scatterChart":
                        LOG.debug("ChartTypeParser.parsePlotArea: detected scatter chart");
                        chart.setType(ChartType.SCATTER);
                        parseScatterChart(chart, child);
                        break;
                        
                    case "c:areaChart":
                        LOG.debug("ChartTypeParser.parsePlotArea: detected area chart");
                        chart.setType(ChartType.AREA);
                        parseAreaChart(chart, child);
                        break;
                        
                    default:
                        LOG.debug("ChartTypeParser.parsePlotArea: skipping unknown plot area element: {}", tagName);
                        break;
                }
            }
            
            if (chart.getType() != null) {
                LOG.info("ChartTypeParser.parsePlotArea: successfully detected chart type: {}", chart.getType());
            } else {
                LOG.warn("ChartTypeParser.parsePlotArea: no chart type detected in plot area");
            }
            
        } catch (Exception e) {
            LOG.error("ChartTypeParser.parsePlotArea: error parsing plot area", e);
        }
    }
    
    /**
     * Parses a bar chart element and extracts chart structure.
     */
    public void parseBarChart(ChartModel chart, XNode barChartNode) {
        LOG.debug("ChartTypeParser.parseBarChart: parsing bar chart structure");
        
        if (barChartNode == null) {
            LOG.warn("ChartTypeParser.parseBarChart: bar chart node is null");
            return;
        }
        
        try {
            // Parse bar direction (bar vs column)
            XNode barDirNode = barChartNode.childByTag("c:barDir");
            if (barDirNode != null) {
                String barDir = barDirNode.attrText("val");
                LOG.debug("ChartTypeParser.parseBarChart: bar direction: {}", barDir);
                
                if ("col".equals(barDir)) {
                    chart.setType(ChartType.COLUMN);
                    LOG.debug("ChartTypeParser.parseBarChart: updated chart type to COLUMN based on bar direction");
                }
            }
            
            // Parse grouping type
            XNode groupingNode = barChartNode.childByTag("c:grouping");
            if (groupingNode != null) {
                String grouping = groupingNode.attrText("val");
                LOG.debug("ChartTypeParser.parseBarChart: grouping type: {}", grouping);
                // Grouping information can be stored in chart properties if needed
            }
            
            // Parse series elements
            seriesParser.parseChartSeries(chart, barChartNode);
            
            LOG.debug("ChartTypeParser.parseBarChart: completed bar chart parsing");
            
        } catch (Exception e) {
            LOG.error("ChartTypeParser.parseBarChart: error parsing bar chart", e);
        }
    }
    
    /**
     * Parses a line chart element and extracts chart structure.
     */
    public void parseLineChart(ChartModel chart, XNode lineChartNode) {
        LOG.debug("ChartTypeParser.parseLineChart: parsing line chart structure");
        
        if (lineChartNode == null) {
            LOG.warn("ChartTypeParser.parseLineChart: line chart node is null");
            return;
        }
        
        try {
            // Parse grouping type
            XNode groupingNode = lineChartNode.childByTag("c:grouping");
            if (groupingNode != null) {
                String grouping = groupingNode.attrText("val");
                LOG.debug("ChartTypeParser.parseLineChart: grouping type: {}", grouping);
            }
            
            // Parse series elements
            seriesParser.parseChartSeries(chart, lineChartNode);
            
            LOG.debug("ChartTypeParser.parseLineChart: completed line chart parsing");
            
        } catch (Exception e) {
            LOG.error("ChartTypeParser.parseLineChart: error parsing line chart", e);
        }
    }
    
    /**
     * Parses a pie chart element and extracts chart structure.
     */
    public void parsePieChart(ChartModel chart, XNode pieChartNode) {
        LOG.debug("ChartTypeParser.parsePieChart: parsing pie chart structure");
        
        if (pieChartNode == null) {
            LOG.warn("ChartTypeParser.parsePieChart: pie chart node is null");
            return;
        }
        
        try {
            // Parse first slice angle
            XNode firstSliceAngNode = pieChartNode.childByTag("c:firstSliceAng");
            if (firstSliceAngNode != null) {
                String firstSliceAng = firstSliceAngNode.attrText("val");
                LOG.debug("ChartTypeParser.parsePieChart: first slice angle: {}", firstSliceAng);
                // First slice angle can be stored in chart properties if needed
            }
            
            // Parse series elements
            seriesParser.parseChartSeries(chart, pieChartNode);
            
            LOG.debug("ChartTypeParser.parsePieChart: completed pie chart parsing");
            
        } catch (Exception e) {
            LOG.error("ChartTypeParser.parsePieChart: error parsing pie chart", e);
        }
    }
    
    /**
     * Parses a scatter chart element and extracts chart structure.
     */
    public void parseScatterChart(ChartModel chart, XNode scatterChartNode) {
        LOG.debug("ChartTypeParser.parseScatterChart: parsing scatter chart structure");
        
        if (scatterChartNode == null) {
            LOG.warn("ChartTypeParser.parseScatterChart: scatter chart node is null");
            return;
        }
        
        try {
            // Parse scatter style
            XNode scatterStyleNode = scatterChartNode.childByTag("c:scatterStyle");
            if (scatterStyleNode != null) {
                String scatterStyle = scatterStyleNode.attrText("val");
                LOG.debug("ChartTypeParser.parseScatterChart: scatter style: {}", scatterStyle);
                
                if ("bubble".equals(scatterStyle)) {
                    chart.setType(ChartType.BUBBLE);
                    LOG.debug("ChartTypeParser.parseScatterChart: updated chart type to BUBBLE based on scatter style");
                }
            }
            
            // Parse series elements
            seriesParser.parseChartSeries(chart, scatterChartNode);
            
            LOG.debug("ChartTypeParser.parseScatterChart: completed scatter chart parsing");
            
        } catch (Exception e) {
            LOG.error("ChartTypeParser.parseScatterChart: error parsing scatter chart", e);
        }
    }
    
    /**
     * Parses an area chart element and extracts chart structure.
     */
    public void parseAreaChart(ChartModel chart, XNode areaChartNode) {
        LOG.debug("ChartTypeParser.parseAreaChart: parsing area chart structure");
        
        if (areaChartNode == null) {
            LOG.warn("ChartTypeParser.parseAreaChart: area chart node is null");
            return;
        }
        
        try {
            // Parse grouping type
            XNode groupingNode = areaChartNode.childByTag("c:grouping");
            if (groupingNode != null) {
                String grouping = groupingNode.attrText("val");
                LOG.debug("ChartTypeParser.parseAreaChart: grouping type: {}", grouping);
            }
            
            // Parse series elements
            seriesParser.parseChartSeries(chart, areaChartNode);
            
            LOG.debug("ChartTypeParser.parseAreaChart: completed area chart parsing");
            
        } catch (Exception e) {
            LOG.error("ChartTypeParser.parseAreaChart: error parsing area chart", e);
        }
    }
}