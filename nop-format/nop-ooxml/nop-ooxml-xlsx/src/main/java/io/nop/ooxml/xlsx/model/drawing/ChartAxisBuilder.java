/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.model.color.ColorHelper;
import io.nop.excel.util.UnitsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for chart axis elements following the Parser-Builder pattern.
 * Generates OOXML chart axis structures from ChartAxisModel objects.
 */
public class ChartAxisBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartAxisBuilder.class);
    
    /**
     * Singleton instance for reuse across multiple building operations.
     */
    public static final ChartAxisBuilder INSTANCE = new ChartAxisBuilder();
    
    private ChartAxisBuilder() {
        // Private constructor for singleton
    }
    
    /**
     * Builds chart axes XML elements from chart model.
     */
    public void buildAxes(XNode plotArea, io.nop.excel.chart.model.ChartModel chart) {
        LOG.debug("ChartAxisBuilder.buildAxes: building chart axes");
        
        if (chart.getAxes() != null && !chart.getAxes().isEmpty()) {
            // Build axes from chart model
            for (ChartAxisModel axis : chart.getAxes()) {
                buildAxis(plotArea, axis);
            }
        } else {
            // Build default axes for chart types that need them
            buildDefaultAxes(plotArea, chart);
        }
        
        LOG.debug("ChartAxisBuilder.buildAxes: completed chart axes");
    }
    
    /**
     * Builds a single axis element.
     */
    public XNode buildAxis(XNode plotArea, ChartAxisModel axis) {
        if (axis == null) {
            LOG.warn("ChartAxisBuilder.buildAxis: axis model is null");
            return null;
        }
        
        LOG.debug("ChartAxisBuilder.buildAxis: building {} axis with ID: {}", axis.getType(), axis.getId());
        
        XNode axisNode;
        
        // Create appropriate axis type
        if (axis.getType() == ChartAxisType.CATEGORY) {
            axisNode = buildCategoryAxis(plotArea, axis);
        } else if (axis.getType() == ChartAxisType.VALUE) {
            axisNode = buildValueAxis(plotArea, axis);
        } else if (axis.getType() == ChartAxisType.TIME) {
            axisNode = buildDateAxis(plotArea, axis);
        } else {
            LOG.warn("ChartAxisBuilder.buildAxis: unknown axis type: {}, using value axis", axis.getType());
            axisNode = buildValueAxis(plotArea, axis);
        }
        
        LOG.debug("ChartAxisBuilder.buildAxis: completed {} axis", axis.getType());
        return axisNode;
    }
    
    /**
     * Builds category axis (X-axis).
     */
    private XNode buildCategoryAxis(XNode plotArea, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildCategoryAxis: building category axis");
        
        XNode catAx = plotArea.addChild("c:catAx");
        
        // Build common axis properties
        buildCommonAxisProperties(catAx, axis);
        
        // Category axis specific properties
        XNode auto = catAx.addChild("c:auto");
        auto.setAttr("val", "1");
        
        XNode lblAlgn = catAx.addChild("c:lblAlgn");
        lblAlgn.setAttr("val", "ctr");
        
        XNode lblOffset = catAx.addChild("c:lblOffset");
        lblOffset.setAttr("val", "100");
        
        XNode noMultiLvlLbl = catAx.addChild("c:noMultiLvlLbl");
        noMultiLvlLbl.setAttr("val", "0");
        
        LOG.debug("ChartAxisBuilder.buildCategoryAxis: completed category axis");
        return catAx;
    }
    
    /**
     * Builds value axis (Y-axis).
     */
    private XNode buildValueAxis(XNode plotArea, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildValueAxis: building value axis");
        
        XNode valAx = plotArea.addChild("c:valAx");
        
        // Build common axis properties
        buildCommonAxisProperties(valAx, axis);
        
        // Value axis specific properties
        XNode crossBetween = valAx.addChild("c:crossBetween");
        crossBetween.setAttr("val", "between");
        
        LOG.debug("ChartAxisBuilder.buildValueAxis: completed value axis");
        return valAx;
    }
    
    /**
     * Builds date axis.
     */
    private XNode buildDateAxis(XNode plotArea, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildDateAxis: building date axis");
        
        XNode dateAx = plotArea.addChild("c:dateAx");
        
        // Build common axis properties
        buildCommonAxisProperties(dateAx, axis);
        
        // Date axis specific properties
        XNode auto = dateAx.addChild("c:auto");
        auto.setAttr("val", "1");
        
        XNode lblOffset = dateAx.addChild("c:lblOffset");
        lblOffset.setAttr("val", "100");
        
        LOG.debug("ChartAxisBuilder.buildDateAxis: completed date axis");
        return dateAx;
    }
    
    /**
     * Builds common axis properties shared by all axis types.
     */
    private void buildCommonAxisProperties(XNode axisNode, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildCommonAxisProperties: building common axis properties");
        
        // Axis ID
        XNode axId = axisNode.addChild("c:axId");
        String axisId = axis.getId() != null ? axis.getId() : "1";
        axId.setAttr("val", axisId);
        
        // Scaling
        buildAxisScaling(axisNode, axis);
        
        // Delete axis (visibility)
        XNode delete = axisNode.addChild("c:delete");
        delete.setAttr("val", "0"); // visible by default
        
        // Axis position
        XNode axPos = axisNode.addChild("c:axPos");
        String position = mapAxisPosition(axis.getPosition());
        axPos.setAttr("val", position);
        
        // Major gridlines
        if (axis.getGrid() != null && axis.getGrid().getVisible()) {
            buildMajorGridlines(axisNode, axis);
        }
        
        // Axis title
        if (axis.getTitle() != null && axis.getTitle().getText() != null) {
            buildAxisTitle(axisNode, axis);
        }
        
        // Number format for labels
        if (axis.getLabels() != null && axis.getLabels().getFormat() != null) {
            buildNumberFormat(axisNode, axis);
        }
        
        // Tick marks
        buildTickMarks(axisNode, axis);
        
        // Tick label position
        buildTickLabelPosition(axisNode, axis);
        
        // Cross axis ID (will be set by the calling context)
        XNode crossAx = axisNode.addChild("c:crossAx");
        crossAx.setAttr("val", "2"); // default cross axis ID
        
        // Crosses
        XNode crosses = axisNode.addChild("c:crosses");
        crosses.setAttr("val", "autoZero");
        
        LOG.debug("ChartAxisBuilder.buildCommonAxisProperties: completed common axis properties");
    }
    
    /**
     * Builds axis scaling properties.
     */
    private void buildAxisScaling(XNode axisNode, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildAxisScaling: building axis scaling");
        
        XNode scaling = axisNode.addChild("c:scaling");
        XNode orientation = scaling.addChild("c:orientation");
        orientation.setAttr("val", "minMax");
        
        if (axis.getScale() != null) {
            // Minimum value
            if (axis.getScale().getMin() != null) {
                XNode min = scaling.addChild("c:min");
                min.setAttr("val", String.valueOf(axis.getScale().getMin()));
            }
            
            // Maximum value
            if (axis.getScale().getMax() != null) {
                XNode max = scaling.addChild("c:max");
                max.setAttr("val", String.valueOf(axis.getScale().getMax()));
            }
            
            // Logarithmic base
            if ("logarithmic".equals(axis.getScale().getType()) && axis.getScale().getBase() != null) {
                XNode logBase = scaling.addChild("c:logBase");
                logBase.setAttr("val", String.valueOf(axis.getScale().getBase()));
            }
        }
        
        // Major unit (interval)
        if (axis.getScale() != null && axis.getScale().getInterval() != null) {
            XNode majorUnit = axisNode.addChild("c:majorUnit");
            majorUnit.setAttr("val", String.valueOf(axis.getScale().getInterval()));
        }
        
        LOG.debug("ChartAxisBuilder.buildAxisScaling: completed axis scaling");
    }
    
    /**
     * Builds major gridlines.
     */
    private void buildMajorGridlines(XNode axisNode, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildMajorGridlines: building major gridlines");
        
        XNode majorGridlines = axisNode.addChild("c:majorGridlines");
        XNode spPr = majorGridlines.addChild("c:spPr");
        XNode ln = spPr.addChild("a:ln");
        
        // Use UnitsHelper for line width (0.75 points for thin gridlines)
        double gridlineWidthPoints = 0.75;
        int emuWidth = UnitsHelper.pointsToEMU(gridlineWidthPoints);
        ln.setAttr("w", String.valueOf(emuWidth));
        
        XNode solidFill = ln.addChild("a:solidFill");
        XNode srgbClr = solidFill.addChild("a:srgbClr");
        srgbClr.setAttr("val", "D9D9D9"); // light gray
        
        XNode prstDash = ln.addChild("a:prstDash");
        prstDash.setAttr("val", "solid");
        
        LOG.debug("ChartAxisBuilder.buildMajorGridlines: completed major gridlines");
    }
    
    /**
     * Builds axis title.
     */
    private void buildAxisTitle(XNode axisNode, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildAxisTitle: building axis title");
        
        XNode title = axisNode.addChild("c:title");
        
        // Title text
        XNode tx = title.addChild("c:tx");
        XNode v = tx.addChild("c:v");
        v.content(axis.getTitle().getText());
        
        // Layout
        XNode layout = title.addChild("c:layout");
        
        // Overlay
        XNode overlay = title.addChild("c:overlay");
        overlay.setAttr("val", "0");
        
        LOG.debug("ChartAxisBuilder.buildAxisTitle: completed axis title");
    }
    
    /**
     * Builds number format for axis labels.
     */
    private void buildNumberFormat(XNode axisNode, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildNumberFormat: building number format");
        
        XNode numFmt = axisNode.addChild("c:numFmt");
        numFmt.setAttr("formatCode", axis.getLabels().getFormat());
        numFmt.setAttr("sourceLinked", "1");
        
        LOG.debug("ChartAxisBuilder.buildNumberFormat: completed number format");
    }
    
    /**
     * Builds tick marks.
     */
    private void buildTickMarks(XNode axisNode, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildTickMarks: building tick marks");
        
        // Major tick marks
        XNode majorTickMark = axisNode.addChild("c:majorTickMark");
        if (axis.getTicks() != null && axis.getTicks().getVisible()) {
            majorTickMark.setAttr("val", "out");
        } else {
            majorTickMark.setAttr("val", "none");
        }
        
        // Minor tick marks
        XNode minorTickMark = axisNode.addChild("c:minorTickMark");
        minorTickMark.setAttr("val", "none");
        
        LOG.debug("ChartAxisBuilder.buildTickMarks: completed tick marks");
    }
    
    /**
     * Builds tick label position.
     */
    private void buildTickLabelPosition(XNode axisNode, ChartAxisModel axis) {
        LOG.debug("ChartAxisBuilder.buildTickLabelPosition: building tick label position");
        
        XNode tickLblPos = axisNode.addChild("c:tickLblPos");
        if (axis.getLabels() != null && axis.getLabels().getVisible()) {
            tickLblPos.setAttr("val", "nextTo");
        } else {
            tickLblPos.setAttr("val", "none");
        }
        
        LOG.debug("ChartAxisBuilder.buildTickLabelPosition: completed tick label position");
    }
    
    /**
     * Builds default axes for charts that need them.
     */
    private void buildDefaultAxes(XNode plotArea, io.nop.excel.chart.model.ChartModel chart) {
        LOG.debug("ChartAxisBuilder.buildDefaultAxes: building default axes");
        
        // Create default category axis
        ChartAxisModel categoryAxis = new ChartAxisModel();
        categoryAxis.setType(ChartAxisType.CATEGORY);
        categoryAxis.setId("1");
        categoryAxis.setPosition(io.nop.excel.chart.constants.ChartAxisPosition.BOTTOM);
        buildCategoryAxis(plotArea, categoryAxis);
        
        // Create default value axis
        ChartAxisModel valueAxis = new ChartAxisModel();
        valueAxis.setType(ChartAxisType.VALUE);
        valueAxis.setId("2");
        valueAxis.setPosition(io.nop.excel.chart.constants.ChartAxisPosition.LEFT);
        buildValueAxis(plotArea, valueAxis);
        
        LOG.debug("ChartAxisBuilder.buildDefaultAxes: completed default axes");
    }
    
    /**
     * Maps axis position to OOXML value.
     */
    private String mapAxisPosition(io.nop.excel.chart.constants.ChartAxisPosition position) {
        if (position == null) {
            return "b"; // default to bottom
        }
        
        switch (position) {
            case LEFT: return "l";
            case RIGHT: return "r";
            case TOP: return "t";
            case BOTTOM: return "b";
            default: return "b"; // default to bottom
        }
    }
}