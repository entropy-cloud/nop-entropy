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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main builder for chart OOXML generation following the Parser-Builder pattern.
 * Orchestrates sub-builders to generate complete chart structures from ChartModel objects.
 * This is the chart equivalent of DrawingBuilder for images.
 */
public class DrawingChartBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(DrawingChartBuilder.class);
    
    /**
     * Singleton instance for reuse across multiple building operations.
     */
    public static final DrawingChartBuilder INSTANCE = new DrawingChartBuilder();
    
    // Chart namespace constants
    private static final String CHART_NS = "http://schemas.openxmlformats.org/drawingml/2006/chart";
    private static final String DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    
    // Sub-builder instances
    private final ChartTitleBuilder titleBuilder;
    private final ChartLegendBuilder legendBuilder;
    private final ChartAxisBuilder axisBuilder;
    private final ChartSeriesBuilder seriesBuilder;
    private final ChartStyleBuilder styleBuilder;
    
    private DrawingChartBuilder() {
        // Private constructor for singleton
        this.titleBuilder = ChartTitleBuilder.INSTANCE;
        this.legendBuilder = ChartLegendBuilder.INSTANCE;
        this.axisBuilder = ChartAxisBuilder.INSTANCE;
        this.seriesBuilder = ChartSeriesBuilder.INSTANCE;
        this.styleBuilder = ChartStyleBuilder.INSTANCE;
    }
    
    /**
     * Main entry point for building chart XML from ChartModel.
     */
    public XNode build(ChartModel chart) {
        if (chart == null) {
            LOG.warn("DrawingChartBuilder.build: chart model is null");
            return null;
        }
        
        LOG.debug("DrawingChartBuilder.build: building chart with type: {}", chart.getType());
        return buildChartSpace(chart);
    }
    
    /**
     * Build chart reference for embedding in drawings.
     */
    public XNode buildChartReference(ChartModel chart, String relationshipId) {
        if (chart == null || relationshipId == null) {
            LOG.warn("DrawingChartBuilder.buildChartReference: chart or relationshipId is null");
            return null;
        }
        
        XNode chartRef = XNode.make("c:chart");
        chartRef.setAttr("xmlns:c", CHART_NS);
        chartRef.setAttr("xmlns:r", RELATIONSHIP_NS);
        chartRef.setAttr("r:id", relationshipId);
        
        LOG.debug("DrawingChartBuilder.buildChartReference: created chart reference with id: {}", relationshipId);
        return chartRef;
    }
    
    /**
     * Build graphic frame for chart embedding in drawings.
     */
    public XNode buildGraphicFrame(ChartModel chart, String relationshipId, int index) {
        LOG.debug("DrawingChartBuilder.buildGraphicFrame: building graphic frame for chart embedding");
        
        XNode graphicFrame = XNode.make("xdr:graphicFrame");
        graphicFrame.setAttr("macro", "");
        
        // Non-visual graphic frame properties
        XNode nvGraphicFramePr = graphicFrame.addChild("xdr:nvGraphicFramePr");
        XNode cNvPr = nvGraphicFramePr.addChild("xdr:cNvPr");
        cNvPr.setAttr("id", String.valueOf(index + 1));
        cNvPr.setAttr("name", chart.getName() != null ? chart.getName() : "Chart " + (index + 1));
        
        XNode cNvGraphicFramePr = nvGraphicFramePr.addChild("xdr:cNvGraphicFramePr");
        
        // Transform
        XNode xfrm = graphicFrame.addChild("xdr:xfrm");
        XNode off = xfrm.addChild("a:off");
        off.setAttr("x", "0");
        off.setAttr("y", "0");
        XNode ext = xfrm.addChild("a:ext");
        ext.setAttr("cx", "0");
        ext.setAttr("cy", "0");
        
        // Graphic
        XNode graphic = graphicFrame.addChild("a:graphic");
        XNode graphicData = graphic.addChild("a:graphicData");
        graphicData.setAttr("uri", CHART_NS);
        
        // Chart reference
        XNode chartRef = buildChartReference(chart, relationshipId);
        graphicData.appendChild(chartRef);
        
        LOG.debug("DrawingChartBuilder.buildGraphicFrame: completed graphic frame");
        return graphicFrame;
    }
    
    /**
     * Build chart anchor for drawing embedding (compatible with DrawingBuilder patterns).
     */
    public XNode buildChartAnchor(ChartModel chart, String relationshipId, int index) {
        LOG.debug("DrawingChartBuilder.buildChartAnchor: building chart anchor");
        
        XNode anchor = XNode.make("xdr:twoCellAnchor");
        anchor.setAttr("editAs", "oneCell");
        
        // From position (default to A1)
        XNode from = XNode.make("xdr:from");
        from.addChild("xdr:col").content("0");
        from.addChild("xdr:colOff").content("0");
        from.addChild("xdr:row").content("0");
        from.addChild("xdr:rowOff").content("0");
        anchor.appendChild(from);
        
        // To position (default size)
        XNode to = XNode.make("xdr:to");
        to.addChild("xdr:col").content("8");
        to.addChild("xdr:colOff").content("0");
        to.addChild("xdr:row").content("15");
        to.addChild("xdr:rowOff").content("0");
        anchor.appendChild(to);
        
        // Graphic frame
        XNode graphicFrame = buildGraphicFrame(chart, relationshipId, index);
        anchor.appendChild(graphicFrame);
        
        // Client data
        XNode clientData = anchor.addChild("xdr:clientData");
        
        LOG.debug("DrawingChartBuilder.buildChartAnchor: completed chart anchor");
        return anchor;
    }
    
    /**
     * Build the chart space root element with proper namespaces and Excel compatibility elements.
     */
    private XNode buildChartSpace(ChartModel chart) {
        XNode chartSpace = XNode.makeDocNode("c:chartSpace");
        chartSpace.setAttr("xmlns:c", CHART_NS);
        chartSpace.setAttr("xmlns:a", DRAWING_NS);
        chartSpace.setAttr("xmlns:r", RELATIONSHIP_NS);
        
        LOG.debug("DrawingChartBuilder.buildChartSpace: creating chart space for chart: {}", chart.getName());
        
        // Add chart date format (required by Excel)
        XNode date1904 = chartSpace.addChild("c:date1904");
        date1904.setAttr("val", "0");
        
        // Add language settings
        XNode lang = chartSpace.addChild("c:lang");
        lang.setAttr("val", "en-US");
        
        // Add rounding in chart (Excel compatibility)
        XNode roundedCorners = chartSpace.addChild("c:roundedCorners");
        roundedCorners.setAttr("val", "0");
        
        // Include chart properties
        includeChartProperties(chartSpace, chart);
        
        // Build main chart element
        XNode chartNode = chartSpace.addChild("c:chart");
        buildChart(chartNode, chart);
        
        // Add chart style references if present
        if (chart.getStyle() != null) {
            styleBuilder.buildChartStyleReferences(chartSpace, chart.getStyle());
        }
        
        // Add print settings
        XNode printSettings = chartSpace.addChild("c:printSettings");
        buildPrintSettings(printSettings);
        
        LOG.debug("DrawingChartBuilder.buildChartSpace: completed chart space generation");
        return chartSpace;
    }
    
    /**
     * Include chart properties like dimensions and positioning.
     */
    private void includeChartProperties(XNode chartSpace, ChartModel chart) {
        LOG.debug("DrawingChartBuilder.includeChartProperties: including chart properties");
        
        // Add chart dimensions as external data if available
        if (chart.getWidth() != null || chart.getHeight() != null) {
            XNode externalData = chartSpace.addChild("c:externalData");
            externalData.setAttr("r:id", "rId1"); // Will be set by the calling context
            
            // Add auto update setting
            XNode autoUpdate = externalData.addChild("c:autoUpdate");
            autoUpdate.setAttr("val", "0");
            
            LOG.debug("DrawingChartBuilder.includeChartProperties: added external data reference");
        }
        
        LOG.debug("DrawingChartBuilder.includeChartProperties: chart properties included - id: {}, name: {}", 
                 chart.getId(), chart.getName());
    }
    
    /**
     * Build the main chart element content using sub-builders.
     */
    private void buildChart(XNode chartNode, ChartModel chart) {
        LOG.debug("DrawingChartBuilder.buildChart: building chart content");
        
        // Add chart title if present using TitleBuilder
        if (chart.getTitle() != null && chart.getTitle().getVisible()) {
            XNode titleNode = titleBuilder.buildTitle(chart.getTitle());
            if (titleNode != null) {
                chartNode.appendChild(titleNode);
            }
        }
        
        // Add auto title deletion (Excel compatibility)
        XNode autoTitleDeleted = chartNode.addChild("c:autoTitleDeleted");
        autoTitleDeleted.setAttr("val", chart.getTitle() == null ? "1" : "0");
        
        // Build plot area (main chart content)
        XNode plotArea = chartNode.addChild("c:plotArea");
        buildPlotArea(plotArea, chart);
        
        // Add legend if present using LegendBuilder
        if (chart.getLegend() != null && chart.getLegend().getVisible()) {
            XNode legendNode = legendBuilder.buildLegend(chart.getLegend());
            if (legendNode != null) {
                chartNode.appendChild(legendNode);
            }
        }
        
        // Add plot visibility (Excel compatibility)
        XNode plotVisOnly = chartNode.addChild("c:plotVisOnly");
        plotVisOnly.setAttr("val", "1");
        
        // Add display blanks as (Excel compatibility)
        XNode dispBlanksAs = chartNode.addChild("c:dispBlanksAs");
        dispBlanksAs.setAttr("val", "gap");
        
        // Add show data labels over maximum (Excel compatibility)
        XNode showDLblsOverMax = chartNode.addChild("c:showDLblsOverMax");
        showDLblsOverMax.setAttr("val", "0");
    }
    
    /**
     * Build plot area using sub-builders.
     */
    private void buildPlotArea(XNode plotArea, ChartModel chart) {
        LOG.debug("DrawingChartBuilder.buildPlotArea: building plot area for chart type: {}", chart.getType());
        
        // Add layout (Excel compatibility)
        XNode layout = plotArea.addChild("c:layout");
        
        // Build chart type-specific elements
        if (chart.getType() != null) {
            switch (chart.getType()) {
                case BAR:
                case COLUMN:
                    buildBarChart(plotArea, chart);
                    break;
                case LINE:
                    buildLineChart(plotArea, chart);
                    break;
                case PIE:
                case DOUGHNUT:
                    buildPieChart(plotArea, chart);
                    break;
                case SCATTER:
                case BUBBLE:
                    buildScatterChart(plotArea, chart);
                    break;
                case AREA:
                    buildAreaChart(plotArea, chart);
                    break;
                case RADAR:
                case HEATMAP:
                case COMBO:
                default:
                    LOG.warn("DrawingChartBuilder.buildPlotArea: unsupported chart type: {}, falling back to bar chart", chart.getType());
                    buildBarChart(plotArea, chart); // fallback to bar chart
            }
        } else {
            LOG.warn("DrawingChartBuilder.buildPlotArea: chart type is null, using bar chart as default");
            buildBarChart(plotArea, chart); // default to bar chart
        }
        
        // Build axes for chart types that need them using AxisBuilder
        if (needsAxes(chart.getType())) {
            axisBuilder.buildAxes(plotArea, chart);
        }
        
        LOG.debug("DrawingChartBuilder.buildPlotArea: completed plot area for chart type: {}", chart.getType());
    }
    
    /**
     * Check if the chart type needs axes.
     */
    private boolean needsAxes(io.nop.excel.chart.constants.ChartType chartType) {
        if (chartType == null) return true; // default to needing axes
        
        switch (chartType) {
            case PIE:
            case DOUGHNUT:
                return false; // pie charts don't need axes
            case BAR:
            case COLUMN:
            case LINE:
            case SCATTER:
            case BUBBLE:
            case AREA:
            case RADAR:
            case HEATMAP:
            case COMBO:
            default:
                return true;
        }
    }
    
    // Chart type-specific builder methods using SeriesBuilder
    private void buildBarChart(XNode plotArea, ChartModel chart) {
        LOG.debug("DrawingChartBuilder.buildBarChart: building bar chart");
        
        XNode barChart = plotArea.addChild("c:barChart");
        
        // Set bar direction (bar vs column)
        XNode barDir = barChart.addChild("c:barDir");
        if (chart.getType() == io.nop.excel.chart.constants.ChartType.BAR) {
            barDir.setAttr("val", "bar"); // horizontal bars
        } else {
            barDir.setAttr("val", "col"); // vertical columns (default)
        }
        
        // Set grouping type
        XNode grouping = barChart.addChild("c:grouping");
        grouping.setAttr("val", "clustered"); // default to clustered
        
        // Add variable colors (Excel compatibility)
        XNode varyColors = barChart.addChild("c:varyColors");
        varyColors.setAttr("val", "0");
        
        // Build series using SeriesBuilder
        seriesBuilder.buildSeries(barChart, chart, chart.getType());
        
        // Set gap width (spacing between categories)
        XNode gapWidth = barChart.addChild("c:gapWidth");
        gapWidth.setAttr("val", "150"); // Excel default
        
        // Set overlap (spacing between series)
        XNode overlap = barChart.addChild("c:overlap");
        overlap.setAttr("val", "0"); // Excel default
        
        // Add axis IDs (will be matched with actual axes)
        XNode axId1 = barChart.addChild("c:axId");
        axId1.setAttr("val", "1");
        XNode axId2 = barChart.addChild("c:axId");
        axId2.setAttr("val", "2");
        
        LOG.debug("DrawingChartBuilder.buildBarChart: completed bar chart");
    }
    
    private void buildLineChart(XNode plotArea, ChartModel chart) {
        LOG.debug("DrawingChartBuilder.buildLineChart: building line chart");
        
        XNode lineChart = plotArea.addChild("c:lineChart");
        
        // Set grouping type
        XNode grouping = lineChart.addChild("c:grouping");
        grouping.setAttr("val", "standard");
        
        // Add variable colors
        XNode varyColors = lineChart.addChild("c:varyColors");
        varyColors.setAttr("val", "0");
        
        // Build series using SeriesBuilder
        seriesBuilder.buildSeries(lineChart, chart, chart.getType());
        
        // Add marker settings
        XNode marker = lineChart.addChild("c:marker");
        marker.setAttr("val", "1"); // show markers by default
        
        // Add smooth lines setting
        XNode smooth = lineChart.addChild("c:smooth");
        smooth.setAttr("val", "0"); // no smoothing by default
        
        // Add axis IDs
        XNode axId1 = lineChart.addChild("c:axId");
        axId1.setAttr("val", "1");
        XNode axId2 = lineChart.addChild("c:axId");
        axId2.setAttr("val", "2");
        
        LOG.debug("DrawingChartBuilder.buildLineChart: completed line chart");
    }
    
    private void buildPieChart(XNode plotArea, ChartModel chart) {
        LOG.debug("DrawingChartBuilder.buildPieChart: building pie chart");
        
        XNode pieChart = plotArea.addChild("c:pieChart");
        
        // Add variable colors (typically true for pie charts)
        XNode varyColors = pieChart.addChild("c:varyColors");
        varyColors.setAttr("val", "1");
        
        // Build series using SeriesBuilder
        seriesBuilder.buildSeries(pieChart, chart, chart.getType());
        
        // Set first slice angle
        XNode firstSliceAng = pieChart.addChild("c:firstSliceAng");
        firstSliceAng.setAttr("val", "0");
        
        // For doughnut charts, add hole size
        if (chart.getType() == io.nop.excel.chart.constants.ChartType.DOUGHNUT) {
            XNode holeSize = pieChart.addChild("c:holeSize");
            holeSize.setAttr("val", "50"); // 50% hole size for doughnut
        }
        
        LOG.debug("DrawingChartBuilder.buildPieChart: completed pie chart");
    }
    
    private void buildScatterChart(XNode plotArea, ChartModel chart) {
        LOG.debug("DrawingChartBuilder.buildScatterChart: building scatter chart");
        
        XNode scatterChart = plotArea.addChild("c:scatterChart");
        
        // Scatter style
        XNode scatterStyle = scatterChart.addChild("c:scatterStyle");
        scatterStyle.setAttr("val", "lineMarker"); // default style
        
        // Add variable colors
        XNode varyColors = scatterChart.addChild("c:varyColors");
        varyColors.setAttr("val", "0");
        
        // Build series using SeriesBuilder
        seriesBuilder.buildSeries(scatterChart, chart, chart.getType());
        
        // Add axis IDs
        XNode axId1 = scatterChart.addChild("c:axId");
        axId1.setAttr("val", "1");
        XNode axId2 = scatterChart.addChild("c:axId");
        axId2.setAttr("val", "2");
        
        LOG.debug("DrawingChartBuilder.buildScatterChart: completed scatter chart");
    }
    
    private void buildAreaChart(XNode plotArea, ChartModel chart) {
        LOG.debug("DrawingChartBuilder.buildAreaChart: building area chart");
        
        XNode areaChart = plotArea.addChild("c:areaChart");
        
        // Set grouping type
        XNode grouping = areaChart.addChild("c:grouping");
        grouping.setAttr("val", "standard");
        
        // Add variable colors
        XNode varyColors = areaChart.addChild("c:varyColors");
        varyColors.setAttr("val", "0");
        
        // Build series using SeriesBuilder
        seriesBuilder.buildSeries(areaChart, chart, chart.getType());
        
        // Add axis IDs
        XNode axId1 = areaChart.addChild("c:axId");
        axId1.setAttr("val", "1");
        XNode axId2 = areaChart.addChild("c:axId");
        axId2.setAttr("val", "2");
        
        LOG.debug("DrawingChartBuilder.buildAreaChart: completed area chart");
    }
    
    /**
     * Build print settings for Excel compatibility.
     */
    private void buildPrintSettings(XNode printSettings) {
        XNode headerFooter = printSettings.addChild("c:headerFooter");
        XNode pageMargins = printSettings.addChild("c:pageMargins");
        pageMargins.setAttr("b", "0.75");
        pageMargins.setAttr("l", "0.7");
        pageMargins.setAttr("r", "0.7");
        pageMargins.setAttr("t", "0.75");
        pageMargins.setAttr("header", "0.3");
        pageMargins.setAttr("footer", "0.3");
        
        XNode pageSetup = printSettings.addChild("c:pageSetup");
        pageSetup.setAttr("orientation", "portrait");
    }
}