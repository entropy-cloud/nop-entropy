package io.nop.chart.export.echarts;

import io.nop.api.core.exceptions.NopException;
import io.nop.chart.export.ChartExportErrors;
import io.nop.chart.export.IChartOptionBuilder;
import io.nop.excel.chart.constants.ChartAxisPosition;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.constants.ChartDispBlankAs;
import io.nop.excel.chart.constants.ChartLegendPosition;
import io.nop.excel.chart.constants.ChartTitlePosition;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartAxisScaleModel;
import io.nop.excel.chart.model.ChartAxisTitleModel;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartLineStyleModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.excel.chart.model.ChartTrendLineModel;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.resolver.ICellRefResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for ECharts option builders: assembles the shared option root
 * (palette, background, title, legend, cartesian axes) and provides the cartesian
 * series factory shared by the bar/line/area/scatter family.
 *
 * <p>Series-level type overrides are honored inside the cartesian family
 * (bar/line/area/scatter/bubble); a mismatched override on pie/radar/heatmap
 * charts fails fast (plan 2364 D30/D31).</p>
 */
public abstract class AbstractEchartsChartBuilder implements IChartOptionBuilder {

    @Override
    public boolean supports(ChartType type) {
        return getSupportedType() == type;
    }

    @Override
    public final Object buildOption(ChartModel chartModel, ICellRefResolver resolver, List<ChartDataSet> dataSets) {
        if (chartModel == null)
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                    .param(ChartExportErrors.ARG_CHART_MODEL, "chart model is null");
        if (resolver == null)
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                    .param(ChartExportErrors.ARG_REASON, "cell ref resolver is null");
        if (dataSets == null)
            dataSets = new ArrayList<>();

        EchartsBuildContext ctx = new EchartsBuildContext(chartModel, resolver, dataSets);
        Map<String, Object> option = EchartsStyleMapper.newMap();

        applyPalette(chartModel, option);
        applyTitle(chartModel, resolver, option);
        List<Object> series = buildSeries(ctx);
        applyLegend(chartModel, ctx, option);
        customizeOption(ctx, option);
        option.put("series", series);

        return option;
    }

    /**
     * Type-specific coordinates (axes / radar / visualMap) and option-level settings.
     * Called after series are built; may inspect the context.
     */
    protected void customizeOption(EchartsBuildContext ctx, Map<String, Object> option) {
    }

    protected abstract List<Object> buildSeries(EchartsBuildContext ctx);

    protected void applyPalette(ChartModel chartModel, Map<String, Object> option) {
        if (chartModel.getStyle() != null && chartModel.getStyle().getColors() != null
                && !chartModel.getStyle().getColors().isEmpty()) {
            List<Object> palette = new ArrayList<>();
            for (String color : chartModel.getStyle().getColors())
                palette.add(EchartsStyleMapper.normalizeColor(color));
            option.put("color", palette);
        }
    }

    protected void applyTitle(ChartModel chartModel, ICellRefResolver resolver, Map<String, Object> option) {
        ChartTitleModel title = chartModel.getTitle();
        if (title == null || !title.isVisible())
            return;
        Map<String, Object> map = EchartsStyleMapper.newMap();
        String text = resolveText(title.getText(), title.getTextCellRef(), resolver);
        map.put("text", text == null ? "" : text);
        map.put("left", titlePosition(title.getPosition()));
        map.put("top", 0);
        option.put("title", map);
    }

    private String titlePosition(ChartTitlePosition position) {
        if (position == null)
            return "center";
        switch (position) {
            case LEFT:
                return "left";
            case RIGHT:
                return "right";
            default:
                return "center";
        }
    }

    protected void applyLegend(ChartModel chartModel, EchartsBuildContext ctx, Map<String, Object> option) {
        ChartLegendModel legend = chartModel.getLegend();
        if (legend == null || !legend.isVisible())
            return;
        Map<String, Object> map = EchartsStyleMapper.newMap();
        map.put("show", true);
        ChartLegendPosition pos = legend.getPosition() == null ? ChartLegendPosition.BOTTOM : legend.getPosition();
        switch (pos) {
            case TOP:
            case TOP_LEFT:
            case TOP_RIGHT:
                map.put("top", 0);
                break;
            case BOTTOM:
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
                map.put("bottom", 0);
                break;
            case LEFT:
                map.put("left", 0);
                break;
            case RIGHT:
                map.put("right", 0);
                break;
        }
        if (pos == ChartLegendPosition.LEFT || pos == ChartLegendPosition.RIGHT)
            map.put("orient", "vertical");
        // Trend line overlays stay out of the legend (D29): restrict legend entries
        // to the real series when any trend overlay was added.
        if (ctx.hasTrendSeries() && !ctx.mainSeriesNames().isEmpty())
            map.put("data", new ArrayList<>(ctx.mainSeriesNames()));
        option.put("legend", map);
    }

    /**
     * Resolve display text: model text if present, otherwise the resolved cell value.
     */
    protected String resolveText(String text, String textCellRef, ICellRefResolver resolver) {
        if (text != null && !text.isEmpty())
            return text;
        if (textCellRef != null && !textCellRef.isEmpty() && resolver != null) {
            Object value = resolver.getValue(textCellRef);
            return value == null ? null : value.toString();
        }
        return text;
    }

    /**
     * Effective series type: series-level override when present, else the chart type.
     */
    protected ChartType effectiveType(ChartSeriesModel series) {
        return series.getType() != null ? series.getType() : getSupportedType();
    }

    protected void requireFamilyMatch(ChartSeriesModel series, ChartType... family) {
        ChartType effective = effectiveType(series);
        for (ChartType t : family) {
            if (t == effective)
                return;
        }
        throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                .param(ChartExportErrors.ARG_CHART_TYPE, String.valueOf(effective))
                .param(ChartExportErrors.ARG_REASON, "series type override " + effective
                        + " is not supported in a " + getSupportedType() + " chart");
    }

    /**
     * Common series fields shared by all chart families.
     */
    protected Map<String, Object> baseSeries(ChartSeriesModel series, EchartsBuildContext ctx) {
        Map<String, Object> map = EchartsStyleMapper.newMap();
        map.put("name", ctx.seriesName(series));
        Map<String, Object> itemStyle = EchartsStyleMapper.itemStyle(series.getShapeStyle());
        if (!itemStyle.isEmpty())
            map.put("itemStyle", itemStyle);
        Map<String, Object> lineStyle = EchartsStyleMapper.lineStyle(series.getLineStyle());
        if (lineStyle != null)
            map.put("lineStyle", lineStyle);
        if (Boolean.TRUE.equals(series.getSmooth()))
            map.put("smooth", true);
        if (series.getMarkers() != null)
            EchartsStyleMapper.applyMarkers(map, series.getMarkers());
        return map;
    }

    /**
     * Apply data label settings; returns true when labels are enabled. Series-level
     * labels win over the chart-level dataLabels (chart.xdef puts dLbls at chart
     * root and on series).
     */
    protected boolean applyDataLabels(Map<String, Object> series, ChartSeriesModel seriesModel,
                                      EchartsBuildContext ctx, boolean pieFamily) {
        ChartDataLabelsModel labels = seriesModel.getDataLabels();
        if (labels == null)
            labels = ctx.chart().getDataLabels();
        if (labels == null)
            return false;
        series.put("label", EchartsStyleMapper.label(labels, pieFamily));
        return true;
    }

    /**
     * Append trend line overlays for one series (plan 2364 D29): LINEAR as markLine
     * on the main series, other types as dashed overlay line series recorded as
     * trend series (excluded from the legend).
     */
    protected void appendTrendLines(EchartsBuildContext ctx, ChartSeriesModel series,
                                    Map<String, Object> echartsSeries, List<Object> seriesOut) {
        if (series.getTrendLines() == null || series.getTrendLines().isEmpty())
            return;
        List<Number> values = ctx.values(series);
        for (ChartTrendLineModel trend : series.getTrendLines()) {
            boolean linear = trend.getType() == null || trend.getType() == io.nop.excel.chart.constants.ChartTrendLineType.LINEAR;
            if (linear) {
                double[] endpoints = TrendLineComputer.linearFit(values);
                Map<String, Object> markLine = EchartsStyleMapper.newMap();
                markLine.put("silent", true);
                markLine.put("symbol", "none");
                Map<String, Object> style = EchartsStyleMapper.newMap();
                style.put("type", "dashed");
                markLine.put("lineStyle", style);
                List<Object> data = new ArrayList<>(2);
                data.add(coordPoint(0, endpoints[0]));
                data.add(coordPoint(values.size() - 1, endpoints[1]));
                markLine.put("data", data);
                echartsSeries.put("markLine", markLine);
            } else {
                List<Double> overlay = TrendLineComputer.computeOverlay(trend, values.size(), values);
                if (overlay == null)
                    continue;
                Map<String, Object> trendSeries = EchartsStyleMapper.newMap();
                trendSeries.put("name", trend.getName() != null ? trend.getName()
                        : "Trend(" + trend.getType().value() + ")");
                trendSeries.put("type", "line");
                trendSeries.put("data", new ArrayList<>(overlay));
                trendSeries.put("symbol", "none");
                Map<String, Object> style = EchartsStyleMapper.newMap();
                style.put("type", "dashed");
                trendSeries.put("lineStyle", style);
                seriesOut.add(trendSeries);
                ctx.markTrendSeries();
            }
        }
    }

    private Map<String, Object> coordPoint(double x, double y) {
        Map<String, Object> map = EchartsStyleMapper.newMap();
        List<Object> coord = new ArrayList<>(2);
        coord.add(x);
        coord.add(y);
        map.put("coord", coord);
        return map;
    }

    /**
     * Build the cartesian xAxis/yAxis arrays (plan 2364 D39). When the model defines
     * axes they are mapped by position (top/bottom to xAxis, left/right to yAxis);
     * otherwise a category-x + value-y (or value-x for scatter family, or swapped for
     * horizontal bars) pair is synthesized from the resolved categories.
     */
    protected void addCartesianAxes(EchartsBuildContext ctx, Map<String, Object> option, AxisLayout layout) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        List<Map<String, Object>> xAxis = new ArrayList<>();
        List<Map<String, Object>> yAxis = new ArrayList<>();
        List<ChartAxisModel> axes = plotArea != null ? plotArea.getAxes() : null;

        if (axes == null || axes.isEmpty()) {
            ChartAxisModel cat = categoryAxis(layout.onY ? "y" : "x");
            ChartAxisModel val = valueAxis(layout.onY ? "x" : "y");
            if (layout.onY) {
                yAxis.add(axisMap(cat, ctx));
                xAxis.add(axisMap(val, ctx));
            } else {
                xAxis.add(axisMap(cat, ctx));
                yAxis.add(axisMap(val, ctx));
            }
        } else {
            for (ChartAxisModel axis : axes) {
                Map<String, Object> map = axisMap(axis, ctx);
                if (isHorizontalPlacement(axis))
                    xAxis.add(map);
                else
                    yAxis.add(map);
            }
        }
        option.put("xAxis", xAxis);
        option.put("yAxis", yAxis);
    }

    /**
     * Axis layout for synthesized (model-less) cartesian axes.
     */
    protected enum AxisLayout {
        CATEGORY_X(false), CATEGORY_Y(true), VALUE_X(true);

        final boolean onY;

        AxisLayout(boolean onY) {
            this.onY = onY;
        }
    }

    /**
     * Explicit top/bottom positions are horizontal; null positions follow the Excel
     * default (category axes at the bottom, value axes at the left).
     */
    protected boolean isHorizontalPlacement(ChartAxisModel axis) {
        if (axis.getPosition() != null)
            return axis.getPosition() == ChartAxisPosition.TOP || axis.getPosition() == ChartAxisPosition.BOTTOM;
        return axis.getType() == ChartAxisType.CATEGORY;
    }

    protected ChartAxisModel categoryAxis(String id) {
        ChartAxisModel axis = new ChartAxisModel();
        axis.setId(id);
        axis.setType(ChartAxisType.CATEGORY);
        axis.setPosition("y".equals(id) ? ChartAxisPosition.LEFT : ChartAxisPosition.BOTTOM);
        return axis;
    }

    protected ChartAxisModel valueAxis(String id) {
        ChartAxisModel axis = new ChartAxisModel();
        axis.setId(id);
        axis.setType(ChartAxisType.VALUE);
        axis.setPosition("x".equals(id) ? ChartAxisPosition.BOTTOM : ChartAxisPosition.LEFT);
        return axis;
    }

    protected Map<String, Object> axisMap(ChartAxisModel axis, EchartsBuildContext ctx) {
        Map<String, Object> map = EchartsStyleMapper.newMap();
        ChartAxisType type = axis.getType() == null ? ChartAxisType.VALUE : axis.getType();
        switch (type) {
            case CATEGORY:
                map.put("type", "category");
                map.put("data", ctx != null ? ctx.categories() : new ArrayList<>());
                break;
            case DATE:
                // String categories are parsed by the ECharts time axis; format
                // semantics beyond ISO-8601 are not covered (no fixture coverage).
                map.put("type", "time");
                break;
            case LOG:
                map.put("type", "log");
                break;
            case SERIES:
                throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                        .param(ChartExportErrors.ARG_CHART_TYPE, "series axis")
                        .param(ChartExportErrors.ARG_REASON,
                                "Excel 3D series axes have no ECharts equivalent");
            case VALUE:
            default:
                map.put("type", "value");
                break;
        }
        if (axis.getPosition() != null)
            map.put("position", axis.getPosition().name().toLowerCase());
        if (!axis.isVisible()) {
            map.put("show", false);
            return map;
        }
        ChartAxisTitleModel title = axis.getTitle();
        if (title != null && title.isVisible()) {
            String text = resolveText(title.getText(), title.getTextCellRef(),
                    ctx != null ? ctx.resolver() : null);
            map.put("name", text == null ? "" : text);
            map.put("nameLocation", "middle");
        }
        ChartAxisScaleModel scale = axis.getScale();
        if (scale != null) {
            if (scale.getMin() != null)
                map.put("min", scale.getMin());
            if (scale.getMax() != null)
                map.put("max", scale.getMax());
            if (Boolean.TRUE.equals(scale.getReverse()))
                map.put("inverse", true);
        }
        if (axis.getMajorGrid() != null)
            map.put("splitLine", EchartsStyleMapper.splitLine(axis.getMajorGrid()));
        if (axis.getLineStyle() != null) {
            Map<String, Object> axisLine = EchartsStyleMapper.newMap();
            Map<String, Object> lineStyle = EchartsStyleMapper.lineStyle(axis.getLineStyle());
            axisLine.put("lineStyle", lineStyle != null ? lineStyle : EchartsStyleMapper.newMap());
            map.put("axisLine", axisLine);
        }
        return map;
    }

    /**
     * connectNulls from the chart-level dispBlanksAs setting (D32): SPAN bridges
     * category gaps; GAP is the ECharts default; ZERO fills blanks with 0 at data
     * level (cartesian builders). Applies to the line/area family only.
     */
    protected void applyDispBlanksAs(ChartModel chart, Map<String, Object> series) {
        if (chart.getDispBlanksAs() == ChartDispBlankAs.SPAN)
            series.put("connectNulls", true);
    }

    protected void requireNonEmptyValues(EchartsBuildContext ctx, ChartSeriesModel series, String what) {
        List<Number> values = ctx.values(series);
        if (values.isEmpty())
            throw new NopException(ChartExportErrors.ERR_DATA_RESOLUTION_FAILED)
                    .param(ChartExportErrors.ARG_REASON, what + " series '" + ctx.seriesName(series)
                            + "' has no resolved values");
    }

    /**
     * Per-build context shared by the assembler and type builders.
     */
    public static class EchartsBuildContext {
        private final ChartModel chart;
        private final ICellRefResolver resolver;
        private final List<ChartDataSet> dataSets;
        private List<Object> categories;
        private final List<Object> mainSeriesNames = new ArrayList<>();
        private boolean trendSeries;

        public EchartsBuildContext(ChartModel chart, ICellRefResolver resolver, List<ChartDataSet> dataSets) {
            this.chart = chart;
            this.resolver = resolver;
            this.dataSets = dataSets;
        }

        public ChartModel chart() {
            return chart;
        }

        public ICellRefResolver resolver() {
            return resolver;
        }

        public List<ChartDataSet> dataSets() {
            return dataSets;
        }

        public void addMainSeriesName(String name) {
            if (name != null)
                mainSeriesNames.add(name);
        }

        public List<Object> mainSeriesNames() {
            return mainSeriesNames;
        }

        public void markTrendSeries() {
            trendSeries = true;
        }

        public boolean hasTrendSeries() {
            return trendSeries;
        }

        /**
         * Categories of the first series that has them (null-safe).
         */
        public List<Object> categories() {
            if (categories != null)
                return categories;
            for (ChartDataSet ds : dataSets) {
                if (ds != null && ds.getCategories() != null && !ds.getCategories().isEmpty()) {
                    categories = ds.getCategories();
                    return categories;
                }
            }
            return new ArrayList<>();
        }

        public ChartDataSet dataSet(ChartSeriesModel series) {
            for (ChartDataSet ds : dataSets) {
                if (ds != null && ds.getSeriesModel() == series)
                    return ds;
            }
            return null;
        }

        public List<Number> values(ChartSeriesModel series) {
            ChartDataSet ds = dataSet(series);
            return ds != null && ds.getValues() != null ? ds.getValues() : new ArrayList<>();
        }

        public String seriesName(ChartSeriesModel series) {
            if (series.getName() != null)
                return series.getName();
            ChartDataSet ds = dataSet(series);
            if (ds != null && ds.getName() != null)
                return ds.getName();
            return "Series" + (series.getIndex() + 1);
        }
    }
}
