package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.constants.ChartDispBlankAs;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartLineConfigModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Line chart option builder (plan 2364 D32/D37): stacked grouping, dispBlanksAs
 * gap/zero/span semantics and per-series smooth/markers.
 */
public class LineChartOptionBuilder extends AbstractEchartsChartBuilder {

    @Override
    public ChartType getSupportedType() {
        return ChartType.LINE;
    }

    @Override
    protected void customizeOption(EchartsBuildContext ctx, Map<String, Object> option) {
        addCartesianAxes(ctx, option, AxisLayout.CATEGORY_X);
    }

    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        ChartLineConfigModel config = plotArea != null ? plotArea.getLineConfig() : null;
        ChartBarGrouping grouping = config != null && config.getGrouping() != null
                ? config.getGrouping()
                : ChartBarGrouping.CLUSTERED;
        boolean stacked = grouping == ChartBarGrouping.STACKED || grouping == ChartBarGrouping.PERCENT_STACKED;
        boolean zeroFill = ctx.chart().getDispBlanksAs() == ChartDispBlankAs.ZERO;

        List<ChartSeriesModel> seriesModels = plotArea != null ? plotArea.getSeriesList() : null;
        List<Object> seriesOut = new ArrayList<>();
        for (ChartSeriesModel series : seriesModels == null ? new ArrayList<ChartSeriesModel>() : seriesModels) {
            requireFamilyMatch(series, ChartType.LINE);
            Map<String, Object> map = baseSeries(series, ctx);
            map.put("type", "line");
            map.put("data", EchartsDataTransforms.dataValues(ctx.values(series), zeroFill));
            if (stacked)
                map.put("stack", "stack");
            if (config != null && Boolean.TRUE.equals(config.getSmooth()) && series.getSmooth() == null)
                map.put("smooth", true);
            applyDataLabels(map, series, ctx, false);
            applyDispBlanksAs(ctx.chart(), map);
            seriesOut.add(map);
            ctx.addMainSeriesName(String.valueOf(map.get("name")));
            appendTrendLines(ctx, series, map, seriesOut);
        }
        return seriesOut;
    }
}
