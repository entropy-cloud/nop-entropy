package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartDataPointModel;
import io.nop.excel.chart.model.ChartDoughnutConfigModel;
import io.nop.excel.chart.model.ChartPieConfigModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pie / doughnut option builder: shared pie family assembly with radius semantics
 * (pie outer radius; doughnut hole size), per-data-point colors and percent labels.
 */
public class PieChartOptionBuilder extends AbstractEchartsChartBuilder {

    private final boolean doughnut;

    public PieChartOptionBuilder() {
        this(false);
    }

    protected PieChartOptionBuilder(boolean doughnut) {
        this.doughnut = doughnut;
    }

    @Override
    public ChartType getSupportedType() {
        return doughnut ? ChartType.DOUGHNUT : ChartType.PIE;
    }

    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        List<ChartSeriesModel> seriesModels = plotArea != null ? plotArea.getSeriesList() : null;
        List<Object> seriesOut = new ArrayList<>();

        for (ChartSeriesModel series : seriesModels == null ? new ArrayList<ChartSeriesModel>() : seriesModels) {
            requireFamilyMatch(series, ChartType.PIE, ChartType.DOUGHNUT);
            Map<String, Object> map = baseSeries(series, ctx);
            map.put("type", "pie");
            map.put("radius", radius(plotArea));
            map.put("data", pieData(ctx, series));
            applyDataLabels(map, series, ctx, true);
            seriesOut.add(map);
            ctx.addMainSeriesName(String.valueOf(map.get("name")));
        }
        return seriesOut;
    }

    private Object radius(ChartPlotAreaModel plotArea) {
        String outer = "75%";
        String inner = doughnut ? "50%" : "0%";
        if (plotArea != null) {
            if (doughnut) {
                ChartDoughnutConfigModel config = plotArea.getDoughnutConfig();
                if (config != null) {
                    if (config.getHoleSize() != null)
                        inner = config.getHoleSize() + "%";
                    if (config.getOuterRadius() != null)
                        outer = config.getOuterRadius() + "%";
                }
            } else {
                ChartPieConfigModel config = plotArea.getPieConfig();
                if (config != null && config.getOuterRadius() != null)
                    outer = config.getOuterRadius() + "%";
            }
        }
        if (doughnut)
            return java.util.Arrays.asList(inner, outer);
        return outer;
    }

    private List<Object> pieData(EchartsBuildContext ctx, ChartSeriesModel series) {
        List<Number> values = ctx.values(series);
        List<Object> categories = ctx.categories();
        List<Object> data = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            Number value = values.get(i);
            if (value == null)
                continue;
            Map<String, Object> point = EchartsStyleMapper.newMap();
            String name = i < categories.size() && categories.get(i) != null
                    ? categories.get(i).toString()
                    : "P" + (i + 1);
            point.put("name", name);
            point.put("value", value);
            ChartDataPointModel dp = findDataPoint(series, i);
            if (dp != null && dp.getShapeStyle() != null) {
                Map<String, Object> itemStyle = EchartsStyleMapper.itemStyle(dp.getShapeStyle());
                if (!itemStyle.isEmpty())
                    point.put("itemStyle", itemStyle);
            }
            data.add(point);
        }
        return data;
    }

    private ChartDataPointModel findDataPoint(ChartSeriesModel series, int index) {
        if (series.getDataPoints() == null)
            return null;
        for (ChartDataPointModel dp : series.getDataPoints()) {
            if (dp.getIndex() == index)
                return dp;
        }
        return null;
    }
}
