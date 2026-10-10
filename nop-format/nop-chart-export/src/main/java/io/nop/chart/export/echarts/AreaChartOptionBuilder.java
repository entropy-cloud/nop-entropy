package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.constants.ChartDispBlankAs;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartAreaConfigModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Area chart option builder: ECharts line series with areaStyle, sharing the
 * line family grouping/stacking semantics (plan 2364 D36/D37).
 */
public class AreaChartOptionBuilder extends AbstractEchartsChartBuilder {

    @Override
    public ChartType getSupportedType() {
        return ChartType.AREA;
    }

    @Override
    protected void customizeOption(EchartsBuildContext ctx, Map<String, Object> option) {
        addCartesianAxes(ctx, option, AxisLayout.CATEGORY_X);
    }

    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        ChartAreaConfigModel config = plotArea != null ? plotArea.getAreaConfig() : null;
        ChartBarGrouping grouping = config != null && config.getGrouping() != null
                ? config.getGrouping()
                : ChartBarGrouping.CLUSTERED;
        boolean stacked = grouping == ChartBarGrouping.STACKED || grouping == ChartBarGrouping.PERCENT_STACKED;
        boolean percent = grouping == ChartBarGrouping.PERCENT_STACKED;
        boolean zeroFill = ctx.chart().getDispBlanksAs() == ChartDispBlankAs.ZERO;

        List<ChartSeriesModel> seriesModels = plotArea != null ? plotArea.getSeriesList() : null;
        List<List<Double>> percentValues = null;
        if (percent && seriesModels != null) {
            List<List<Number>> raw = new ArrayList<>();
            for (ChartSeriesModel s : seriesModels)
                raw.add(ctx.values(s));
            percentValues = EchartsDataTransforms.percentStacked(raw);
        }

        List<Object> seriesOut = new ArrayList<>();
        int index = 0;
        for (ChartSeriesModel series : seriesModels == null ? new ArrayList<ChartSeriesModel>() : seriesModels) {
            requireFamilyMatch(series, ChartType.AREA);
            Map<String, Object> map = baseSeries(series, ctx);
            map.put("type", "line");
            map.put("areaStyle", EchartsStyleMapper.newMap());
            if (percentValues != null)
                map.put("data", new ArrayList<Object>(percentValues.get(index)));
            else
                map.put("data", EchartsDataTransforms.dataValues(ctx.values(series), zeroFill));
            if (stacked)
                map.put("stack", "stack");
            applyDataLabels(map, series, ctx, false);
            applyDispBlanksAs(ctx.chart(), map);
            seriesOut.add(map);
            ctx.addMainSeriesName(String.valueOf(map.get("name")));
            appendTrendLines(ctx, series, map, seriesOut);
            index++;
        }
        return seriesOut;
    }
}
