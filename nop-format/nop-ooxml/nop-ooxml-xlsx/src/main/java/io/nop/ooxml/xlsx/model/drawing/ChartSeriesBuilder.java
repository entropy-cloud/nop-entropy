/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.model.color.ColorHelper;
import io.nop.excel.util.UnitsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for chart series elements following the Parser-Builder pattern.
 * Generates OOXML chart series structures from ChartSeriesModel objects.
 */
public class ChartSeriesBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartSeriesBuilder.class);
    
    /**
     * Singleton instance for reuse across multiple building operations.
     */
    public static final ChartSeriesBuilder INSTANCE = new ChartSeriesBuilder();
    
    private ChartSeriesBuilder() {
        // Private constructor for singleton
    }
    
    /**
     * Builds chart series XML elements from chart model.
     */
    public void buildSeries(XNode chartNode, io.nop.excel.chart.model.ChartModel chart, io.nop.excel.chart.constants.ChartType chartType) {
        LOG.debug("ChartSeriesBuilder.buildSeries: building chart series for type: {}", chartType);
        
        if (chart.getSeries() == null || chart.getSeries().isEmpty()) {
            LOG.debug("ChartSeriesBuilder.buildSeries: no series data found");
            return;
        }
        
        int seriesIndex = 0;
        for (ChartSeriesModel series : chart.getSeries()) {
            buildSingleSeries(chartNode, series, seriesIndex++, chartType);
        }
        
        LOG.debug("ChartSeriesBuilder.buildSeries: completed {} series", seriesIndex);
    }
    
    /**
     * Builds a single series element.
     */
    public XNode buildSingleSeries(XNode chartNode, ChartSeriesModel series, int index, io.nop.excel.chart.constants.ChartType chartType) {
        if (series == null) {
            LOG.warn("ChartSeriesBuilder.buildSingleSeries: series model is null");
            return null;
        }
        
        LOG.debug("ChartSeriesBuilder.buildSingleSeries: building series '{}' at index: {}", series.getName(), index);
        
        XNode ser = chartNode.addChild("c:ser");
        
        // Series index and order
        XNode idx = ser.addChild("c:idx");
        idx.setAttr("val", String.valueOf(index));
        
        XNode order = ser.addChild("c:order");
        order.setAttr("val", String.valueOf(index));
        
        // Series text (name)
        buildSeriesText(ser, series);
        
        // Build shape properties (styling)
        if (series.getStyle() != null) {
            buildSeriesShapeProperties(ser, series);
        }
        
        // Add markers for line and scatter charts
        if (chartType == io.nop.excel.chart.constants.ChartType.LINE || 
            chartType == io.nop.excel.chart.constants.ChartType.SCATTER ||
            chartType == io.nop.excel.chart.constants.ChartType.BUBBLE) {
            buildSeriesMarkers(ser, series);
        }
        
        // Build data sources
        buildSeriesDataSources(ser, series, chartType);
        
        // Add data labels if present
        if (series.getLabels() != null && series.getLabels().getEnabled()) {
            buildSeriesDataLabels(ser, series);
        }
        
        LOG.debug("ChartSeriesBuilder.buildSingleSeries: completed series at index: {}", index);
        return ser;
    }
    
    /**
     * Builds series text (name).
     */
    private void buildSeriesText(XNode ser, ChartSeriesModel series) {
        LOG.debug("ChartSeriesBuilder.buildSeriesText: building series text");
        
        XNode tx = ser.addChild("c:tx");
        
        if (series.getName() != null && !series.getName().trim().isEmpty()) {
            // Use literal value for series name
            XNode v = tx.addChild("c:v");
            v.content(series.getName());
        } else {
            // Use string reference for default name
            XNode strRef = tx.addChild("c:strRef");
            XNode f = strRef.addChild("c:f");
            f.content("Series1");
        }
        
        LOG.debug("ChartSeriesBuilder.buildSeriesText: completed series text");
    }
    
    /**
     * Builds series shape properties (styling).
     */
    private void buildSeriesShapeProperties(XNode ser, ChartSeriesModel series) {
        LOG.debug("ChartSeriesBuilder.buildSeriesShapeProperties: building series shape properties");
        
        XNode spPr = ser.addChild("c:spPr");
        
        // Build fill properties
        if (series.getStyle().getColor() != null) {
            XNode solidFill = spPr.addChild("a:solidFill");
            buildColorNode(solidFill, series.getStyle().getColor());
        }
        
        // Build line properties
        if (series.getStyle().getLine() != null && series.getStyle().getLine().getColor() != null) {
            buildLineProperties(spPr, series);
        }
        
        LOG.debug("ChartSeriesBuilder.buildSeriesShapeProperties: completed series shape properties");
    }
    
    /**
     * Builds series markers for line and scatter charts.
     */
    private void buildSeriesMarkers(XNode ser, ChartSeriesModel series) {
        LOG.debug("ChartSeriesBuilder.buildSeriesMarkers: building series markers");
        
        XNode marker = ser.addChild("c:marker");
        
        // Marker symbol
        XNode symbol = marker.addChild("c:symbol");
        symbol.setAttr("val", "circle"); // default marker type
        
        // Marker size
        XNode size = marker.addChild("c:size");
        size.setAttr("val", "5"); // default marker size
        
        // Marker shape properties
        XNode spPr = marker.addChild("c:spPr");
        
        // Use series color for marker if available
        if (series.getStyle() != null && series.getStyle().getColor() != null) {
            XNode solidFill = spPr.addChild("a:solidFill");
            buildColorNode(solidFill, series.getStyle().getColor());
        }
        
        // Marker border
        if (series.getStyle() != null && series.getStyle().getLine() != null && series.getStyle().getLine().getColor() != null) {
            XNode ln = spPr.addChild("a:ln");
            XNode solidFill = ln.addChild("a:solidFill");
            buildColorNode(solidFill, series.getStyle().getLine().getColor());
        }
        
        LOG.debug("ChartSeriesBuilder.buildSeriesMarkers: completed series markers");
    }
    
    /**
     * Builds series data sources.
     */
    private void buildSeriesDataSources(XNode ser, ChartSeriesModel series, io.nop.excel.chart.constants.ChartType chartType) {
        LOG.debug("ChartSeriesBuilder.buildSeriesDataSources: building series data sources");
        
        if (series.getDataSource() == null) {
            LOG.warn("ChartSeriesBuilder.buildSeriesDataSources: data source is null");
            return;
        }
        
        // Build category data source (for most chart types)
        if (chartType != io.nop.excel.chart.constants.ChartType.PIE && 
            chartType != io.nop.excel.chart.constants.ChartType.DOUGHNUT) {
            XNode cat = ser.addChild("c:cat");
            buildDataSourceReference(cat, series.getDataSource(), true);
        }
        
        // Build value data source
        XNode val = ser.addChild("c:val");
        buildDataSourceReference(val, series.getDataSource(), false);
        
        LOG.debug("ChartSeriesBuilder.buildSeriesDataSources: completed series data sources");
    }
    
    /**
     * Builds series data labels.
     */
    private void buildSeriesDataLabels(XNode ser, ChartSeriesModel series) {
        LOG.debug("ChartSeriesBuilder.buildSeriesDataLabels: building series data labels");
        
        XNode dLbls = ser.addChild("c:dLbls");
        
        // Show value
        XNode showVal = dLbls.addChild("c:showVal");
        showVal.setAttr("val", "1");
        
        // Show category name
        XNode showCatName = dLbls.addChild("c:showCatName");
        showCatName.setAttr("val", "0");
        
        // Show series name
        XNode showSerName = dLbls.addChild("c:showSerName");
        showSerName.setAttr("val", "0");
        
        // Show percent (for pie charts)
        XNode showPercent = dLbls.addChild("c:showPercent");
        showPercent.setAttr("val", "0");
        
        // Show bubble size (for bubble charts)
        XNode showBubbleSize = dLbls.addChild("c:showBubbleSize");
        showBubbleSize.setAttr("val", "0");
        
        LOG.debug("ChartSeriesBuilder.buildSeriesDataLabels: completed series data labels");
    }
    
    /**
     * Builds line properties for series styling.
     */
    private void buildLineProperties(XNode spPr, ChartSeriesModel series) {
        LOG.debug("ChartSeriesBuilder.buildLineProperties: building line properties");
        
        XNode ln = spPr.addChild("a:ln");
        
        // Set line width using UnitsHelper
        if (series.getStyle().getLineWidth() != null) {
            int emuWidth = UnitsHelper.pointsToEMU(series.getStyle().getLineWidth());
            ln.setAttr("w", String.valueOf(emuWidth));
        } else if (series.getStyle().getLine() != null && series.getStyle().getLine().getWidth() != null) {
            int emuWidth = UnitsHelper.pointsToEMU(series.getStyle().getLine().getWidth());
            ln.setAttr("w", String.valueOf(emuWidth));
        } else {
            // Default line width (1 point)
            int emuWidth = UnitsHelper.pointsToEMU(1.0);
            ln.setAttr("w", String.valueOf(emuWidth));
        }
        
        // Set line color
        if (series.getStyle().getLine() != null && series.getStyle().getLine().getColor() != null) {
            XNode solidFill = ln.addChild("a:solidFill");
            buildColorNode(solidFill, series.getStyle().getLine().getColor());
        }
        
        // Set line style
        XNode prstDash = ln.addChild("a:prstDash");
        prstDash.setAttr("val", "solid"); // default solid line
        
        LOG.debug("ChartSeriesBuilder.buildLineProperties: completed line properties");
    }
    
    /**
     * Builds Excel reference for data sources.
     */
    private void buildDataSourceReference(XNode parentNode, io.nop.excel.chart.model.ChartDataSourceModel dataSource, boolean isStringRef) {
        LOG.debug("ChartSeriesBuilder.buildDataSourceReference: building data source reference, isStringRef: {}", isStringRef);
        
        if (dataSource == null) {
            LOG.warn("ChartSeriesBuilder.buildDataSourceReference: data source is null");
            return;
        }
        
        String refType = isStringRef ? "c:strRef" : "c:numRef";
        XNode ref = parentNode.addChild(refType);
        
        // Build formula based on data source type
        String formula = buildFormulaFromDataSource(dataSource, isStringRef);
        XNode f = ref.addChild("c:f");
        f.content(formula);
        
        // Add string cache or number cache (Excel compatibility)
        String cacheType = isStringRef ? "c:strCache" : "c:numCache";
        XNode cache = ref.addChild(cacheType);
        XNode formatCode = cache.addChild("c:formatCode");
        formatCode.content("General");
        
        // Add point count (Excel compatibility)
        XNode ptCount = cache.addChild("c:ptCount");
        ptCount.setAttr("val", "5"); // default count
        
        LOG.debug("ChartSeriesBuilder.buildDataSourceReference: completed data source reference");
    }
    
    /**
     * Builds Excel formula from data source.
     */
    private String buildFormulaFromDataSource(io.nop.excel.chart.model.ChartDataSourceModel dataSource, boolean isStringRef) {
        LOG.debug("ChartSeriesBuilder.buildFormulaFromDataSource: building formula from data source, isStringRef: {}", isStringRef);
        
        if (dataSource.getExcel() != null) {
            // Use Excel cell range reference
            String sheetName = dataSource.getExcel().getSheetName();
            String cellRangeRef = dataSource.getExcel().getCellRangeRef();
            if (sheetName != null && cellRangeRef != null) {
                return sheetName + "!" + cellRangeRef;
            }
        }
        
        // For static or API data sources, return default formulas
        if (isStringRef) {
            return "Sheet1!$A$1:$A$5"; // default category range
        } else {
            return "Sheet1!$B$1:$B$5"; // default value range
        }
    }
    
    /**
     * Builds color node from color string.
     */
    private void buildColorNode(XNode parentNode, String color) {
        String normalizedColor = ColorHelper.toCssColor(color);
        if (normalizedColor == null) {
            LOG.warn("ChartSeriesBuilder.buildColorNode: invalid color: {}, using default", color);
            normalizedColor = "#4472C4"; // fallback to default blue
        }
        
        // Remove # prefix for OOXML if present
        if (normalizedColor.startsWith("#")) {
            normalizedColor = normalizedColor.substring(1);
        }
        
        XNode srgbClr = parentNode.addChild("a:srgbClr");
        srgbClr.setAttr("val", normalizedColor.toUpperCase());
    }
}