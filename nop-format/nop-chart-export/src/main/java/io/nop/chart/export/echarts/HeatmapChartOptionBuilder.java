package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartHeatmapConfigModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.util.ChartDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Heatmap option builder (plan 2364 D35): rows are series, columns are categories;
 * a continuous visualMap provides the color band (audit defect ① semantics carried
 * over from the legacy renderer's paint-scale fix). Minimum models (no series) produce an
 * empty option without NPE (D32).
 */
public class HeatmapChartOptionBuilder extends AbstractEchartsChartBuilder {

    @Override
    public ChartType getSupportedType() {
        return ChartType.HEATMAP;
    }

    @Override
    protected void customizeOption(EchartsBuildContext ctx, Map<String, Object> option) {
        Map<String, Object> x = axisMap(categoryAxis("x"), ctx);
        Map<String, Object> y = axisMap(categoryAxis("y"), ctx);
        // Row labels are the series names recorded per-build by buildSeries (the
        // builder is a shared singleton; per-build state must live on the context).
        if (!ctx.mainSeriesNames().isEmpty())
            y.put("data", new ArrayList<>(ctx.mainSeriesNames()));
        List<Object> xAxis = new ArrayList<>(1);
        List<Object> yAxis = new ArrayList<>(1);
        xAxis.add(x);
        yAxis.add(y);
        option.put("xAxis", xAxis);
        option.put("yAxis", yAxis);
        option.put("visualMap", visualMap(ctx));
    }

    private Map<String, Object> visualMap(EchartsBuildContext ctx) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ChartDataSet ds : ctx.dataSets()) {
            if (ds == null || ds.getValues() == null)
                continue;
            for (Number v : ds.getValues()) {
                if (v == null)
                    continue;
                min = Math.min(min, v.doubleValue());
                max = Math.max(max, v.doubleValue());
            }
        }
        Map<String, Object> visualMap = EchartsStyleMapper.newMap();
        visualMap.put("min", min == Double.MAX_VALUE ? 0 : min);
        visualMap.put("max", max == -Double.MAX_VALUE ? 1 : max);
        visualMap.put("calculable", true);
        visualMap.put("orient", "horizontal");
        visualMap.put("left", "center");
        visualMap.put("bottom", 0);
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        ChartHeatmapConfigModel config = plotArea != null ? plotArea.getHeatmapConfig() : null;
        if (config != null && config.getColorRange() != null && !config.getColorRange().isEmpty()) {
            Map<String, Object> inRange = EchartsStyleMapper.newMap();
            inRange.put("color", new ArrayList<>(config.getColorRange()));
            visualMap.put("inRange", inRange);
        }
        return visualMap;
    }

    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        List<ChartSeriesModel> seriesModels = plotArea != null ? plotArea.getSeriesList() : null;
        if (seriesModels == null || seriesModels.isEmpty())
            return new ArrayList<>();

        List<Object> data = new ArrayList<>();
        for (int s = 0; s < seriesModels.size(); s++) {
            ChartSeriesModel series = seriesModels.get(s);
            requireFamilyMatch(series, ChartType.HEATMAP);
            ctx.addMainSeriesName(ctx.seriesName(series));
            List<Number> values = ctx.values(series);
            for (int c = 0; c < values.size(); c++) {
                Number v = values.get(c);
                if (v == null)
                    continue;
                List<Object> cell = new ArrayList<>(3);
                cell.add(c);
                cell.add(s);
                cell.add(v);
                data.add(cell);
            }
        }

        Map<String, Object> map = baseSeries(seriesModels.get(0), ctx);
        map.put("type", "heatmap");
        map.put("data", data);
        List<Object> seriesOut = new ArrayList<>(1);
        seriesOut.add(map);
        return seriesOut;
    }
}
