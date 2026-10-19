package io.nop.chart.export.echarts;

import io.nop.api.core.exceptions.NopException;
import io.nop.chart.export.ChartExportErrors;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Combo chart option builder (plan 2364 D30): each series is dispatched by its
 * series-level type to the cartesian family (bar/line/area/scatter/bubble), keeping
 * every series' own name and style (audit defect ③). The xlsx parse chain records
 * the chart-level type of the FIRST plot-area group only and never sets series-level
 * types, so a combo model without per-series types fails fast instead of silently
 * rendering every series as the same type.
 */
public class ComboChartOptionBuilder extends AbstractEchartsChartBuilder {

    @Override
    public ChartType getSupportedType() {
        return ChartType.COMBO;
    }

    @Override
    protected void customizeOption(EchartsBuildContext ctx, Map<String, Object> option) {
        addCartesianAxes(ctx, option, axisLayout(ctx));
    }

    private AxisLayout axisLayout(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        if (plotArea == null)
            return AxisLayout.CATEGORY_X;
        for (ChartSeriesModel series : plotArea.getSeriesList()) {
            ChartType type = effectiveType(series);
            if (type == ChartType.SCATTER || type == ChartType.BUBBLE)
                return AxisLayout.VALUE_X;
        }
        return AxisLayout.CATEGORY_X;
    }

    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        List<ChartSeriesModel> seriesModels = plotArea != null ? plotArea.getSeriesList() : null;
        List<Object> seriesOut = new ArrayList<>();
        for (ChartSeriesModel series : seriesModels == null ? new ArrayList<ChartSeriesModel>() : seriesModels) {
            ChartType type = series.getType();
            if (type == null || type == ChartType.COMBO)
                throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                        .param(ChartExportErrors.ARG_CHART_TYPE, String.valueOf(type))
                        .param(ChartExportErrors.ARG_REASON, "combo chart series '"
                                + ctx.seriesName(series)
                                + "' has no series-level chart type; the xlsx parse chain "
                                + "does not record per-series types for combo charts");
            switch (type) {
                case BAR:
                    seriesOut.add(barSeries(ctx, series, seriesOut));
                    break;
                case LINE:
                    seriesOut.add(lineSeries(ctx, series, false, seriesOut));
                    break;
                case AREA:
                    seriesOut.add(lineSeries(ctx, series, true, seriesOut));
                    break;
                case SCATTER:
                case BUBBLE:
                    seriesOut.add(scatterSeries(ctx, series));
                    break;
                default:
                    throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                            .param(ChartExportErrors.ARG_CHART_TYPE, String.valueOf(type))
                            .param(ChartExportErrors.ARG_REASON,
                                    "chart type " + type + " cannot be combined in a combo chart");
            }
        }
        return seriesOut;
    }

    private Map<String, Object> barSeries(EchartsBuildContext ctx, ChartSeriesModel series, List<Object> seriesOut) {
        Map<String, Object> map = baseSeries(series, ctx);
        map.put("type", "bar");
        map.put("data", EchartsDataTransforms.dataValues(ctx.values(series), false));
        applyDataLabels(map, series, ctx, false);
        ctx.addMainSeriesName(String.valueOf(map.get("name")));
        appendTrendLines(ctx, series, map, seriesOut);
        return map;
    }

    private Map<String, Object> lineSeries(EchartsBuildContext ctx, ChartSeriesModel series, boolean area,
                                           List<Object> seriesOut) {
        Map<String, Object> map = baseSeries(series, ctx);
        map.put("type", "line");
        if (area)
            map.put("areaStyle", EchartsStyleMapper.newMap());
        map.put("data", EchartsDataTransforms.dataValues(ctx.values(series), false));
        applyDataLabels(map, series, ctx, false);
        ctx.addMainSeriesName(String.valueOf(map.get("name")));
        appendTrendLines(ctx, series, map, seriesOut);
        return map;
    }

    private Map<String, Object> scatterSeries(EchartsBuildContext ctx, ChartSeriesModel series) {
        Map<String, Object> map = baseSeries(series, ctx);
        map.put("type", "scatter");
        // Shared category axes: emit y values only (documented approximation for
        // scatter members of a combo chart).
        map.put("data", new ArrayList<Object>(ctx.values(series)));
        applyDataLabels(map, series, ctx, false);
        ctx.addMainSeriesName(String.valueOf(map.get("name")));
        return map;
    }
}
