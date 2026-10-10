package io.nop.chart.export.echarts;

import io.nop.api.core.exceptions.NopException;
import io.nop.chart.export.ChartExportErrors;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartScatterConfigModel;
import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scatter chart option builder (plan 2364 D35/D38): x from numerically converted
 * categories (the xlsx parse chain leaves xValues empty; fail-fast on non-numeric
 * categories), scatterStyle lineMarker mapped to a line series with symbols.
 */
public class ScatterChartOptionBuilder extends AbstractEchartsChartBuilder {

    @Override
    public ChartType getSupportedType() {
        return ChartType.SCATTER;
    }

    @Override
    protected void customizeOption(EchartsBuildContext ctx, Map<String, Object> option) {
        addCartesianAxes(ctx, option, AxisLayout.VALUE_X);
    }

    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        ChartScatterConfigModel config = plotArea != null ? plotArea.getScatterConfig() : null;
        boolean lineMarker = config == null || config.getScatterStyle() == null
                || config.getScatterStyle().contains("lineMarker");

        List<ChartSeriesModel> seriesModels = plotArea != null ? plotArea.getSeriesList() : null;
        List<Object> seriesOut = new ArrayList<>();
        for (ChartSeriesModel series : seriesModels == null ? new ArrayList<ChartSeriesModel>() : seriesModels) {
            requireFamilyMatch(series, ChartType.SCATTER);
            Map<String, Object> map = baseSeries(series, ctx);
            map.put("type", lineMarker ? "line" : "scatter");
            map.put("data", xyData(ctx, series));
            if (lineMarker && map.get("symbol") == null)
                map.put("symbol", "circle");
            applyDataLabels(map, series, ctx, false);
            seriesOut.add(map);
            ctx.addMainSeriesName(String.valueOf(map.get("name")));
        }
        return seriesOut;
    }

    /**
     * Numeric category conversion (D35): non-numeric categories throw
     * ERR_DATA_RESOLUTION_FAILED instead of silently falling back to indices.
     */
    protected List<Object> xyData(EchartsBuildContext ctx, ChartSeriesModel series) {
        List<Number> values = ctx.values(series);
        List<Object> categories = ctx.categories();
        List<Object> data = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            Number y = values.get(i);
            if (y == null)
                continue;
            data.add(point(numericX(categories, i), y));
        }
        return data;
    }

    protected Object numericX(List<Object> categories, int index) {
        if (categories.isEmpty())
            // The xlsx parse chain only extracts cell references from c:xVal; inline
            // numLit literal x values are dropped, leaving categories empty. Fall back
            // to 1-based positions (same known-parse-chain-limitation bucket as
            // bubbleSizes, see plan 2364 Non-Blocking Follow-ups).
            return (double) (index + 1);
        if (index >= categories.size() || categories.get(index) == null)
            throw new NopException(ChartExportErrors.ERR_DATA_RESOLUTION_FAILED)
                    .param(ChartExportErrors.ARG_REASON,
                            "scatter x value at index " + index + " is missing");
        Object raw = categories.get(index);
        if (raw instanceof Number)
            return raw;
        try {
            return Double.parseDouble(raw.toString());
        } catch (NumberFormatException e) {
            throw new NopException(ChartExportErrors.ERR_DATA_RESOLUTION_FAILED, e)
                    .param(ChartExportErrors.ARG_REASON, "scatter x value '" + raw
                            + "' is not numeric");
        }
    }

    protected Map<String, Object> point(Object x, Number y) {
        Map<String, Object> point = EchartsStyleMapper.newMap();
        List<Object> coord = new ArrayList<>(2);
        coord.add(x);
        coord.add(y);
        point.put("value", coord);
        return point;
    }
}
