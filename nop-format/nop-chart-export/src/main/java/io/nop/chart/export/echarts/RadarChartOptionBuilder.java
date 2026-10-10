package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartRadarStyle;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartRadarConfigModel;
import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Radar chart option builder: indicators derived from the resolved categories with
 * per-indicator maxima computed across series (plan 2364 Phase 1, WI9 refines
 * visuals); radarStyle FILLED adds an areaStyle, MARKER adds symbols.
 */
public class RadarChartOptionBuilder extends AbstractEchartsChartBuilder {

    @Override
    public ChartType getSupportedType() {
        return ChartType.RADAR;
    }

    @Override
    protected void customizeOption(EchartsBuildContext ctx, Map<String, Object> option) {
        option.put("radar", radarCoordinate(ctx));
    }

    private Map<String, Object> radarCoordinate(EchartsBuildContext ctx) {
        Map<String, Object> radar = EchartsStyleMapper.newMap();
        List<Object> categories = ctx.categories();
        List<Number> firstValues = ctx.dataSets().isEmpty() || ctx.dataSets().get(0) == null
                ? new ArrayList<Number>()
                : ctx.dataSets().get(0).getValues();
        int count = Math.max(categories.size(), firstValues.size());

        // WI9 对版回灌（plan 2367 D55）：全部辐条共享一个全局最大值——Excel 雷达的
        // 值轴是单一刻度；per-spoke max 会把每个系列在各辐条上都归一化到近满值，
        // 数据形状退化为正多边形。
        Double globalMax = globalMax(ctx);
        List<Object> indicators = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> indicator = EchartsStyleMapper.newMap();
            indicator.put("name", i < categories.size() && categories.get(i) != null
                    ? categories.get(i).toString()
                    : "I" + (i + 1));
            if (globalMax != null)
                indicator.put("max", globalMax);
            indicators.add(indicator);
        }
        radar.put("indicator", indicators);

        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        ChartRadarConfigModel config = plotArea != null ? plotArea.getRadarConfig() : null;
        if (config != null && config.getRadius() != null)
            radar.put("radius", config.getRadius() + "%");
        return radar;
    }

    /**
     * Global maximum across every series value (10% headroom, rounded up); zero or
     * empty data keeps the ECharts auto maximum.
     */
    private Double globalMax(EchartsBuildContext ctx) {
        double max = 0;
        boolean found = false;
        for (io.nop.excel.chart.util.ChartDataSet ds : ctx.dataSets()) {
            if (ds == null || ds.getValues() == null)
                continue;
            for (Number v : ds.getValues()) {
                if (v != null) {
                    max = Math.max(max, Math.abs(v.doubleValue()));
                    found = true;
                }
            }
        }
        if (!found || max == 0)
            return null;
        return Math.ceil(max * 1.1);
    }

    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        ChartRadarConfigModel config = plotArea != null ? plotArea.getRadarConfig() : null;
        ChartRadarStyle style = config != null ? config.getRadarStyle() : ChartRadarStyle.STANDARD;

        List<ChartSeriesModel> seriesModels = plotArea != null ? plotArea.getSeriesList() : null;
        List<Object> seriesOut = new ArrayList<>();
        for (ChartSeriesModel series : seriesModels == null ? new ArrayList<ChartSeriesModel>() : seriesModels) {
            requireFamilyMatch(series, ChartType.RADAR);
            Map<String, Object> map = EchartsStyleMapper.newMap();
            map.put("type", "radar");
            map.put("name", ctx.seriesName(series));

            List<Object> data = new ArrayList<>(1);
            List<Number> values = ctx.values(series);
            if (!values.isEmpty()) {
                // An empty value array crashes ECharts radarLayout; a minimum model
                // (D32) renders as a series with no data items instead.
                Map<String, Object> item = EchartsStyleMapper.newMap();
                item.put("name", ctx.seriesName(series));
                item.put("value", new ArrayList<Object>(values));
                if (style == ChartRadarStyle.FILLED) {
                    Map<String, Object> areaStyle = EchartsStyleMapper.itemStyle(series.getShapeStyle());
                    item.put("areaStyle", areaStyle);
                }
                data.add(item);
            }
            map.put("data", data);

            if (style == ChartRadarStyle.MARKER)
                map.put("symbol", "circle");
            else
                map.put("symbol", "none");

            seriesOut.add(map);
            ctx.addMainSeriesName(ctx.seriesName(series));
        }
        return seriesOut;
    }
}
