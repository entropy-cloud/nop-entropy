package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartBarDirection;
import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.constants.ChartDispBlankAs;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.excel.chart.model.ChartDataPointModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bar chart option builder (plan 2364 D36/D37): full ChartBarGrouping mapping with
 * percent-stacked data transform, column/bar direction, gap/overlap and
 * invertIfNegative semantics.
 */
public class BarChartOptionBuilder extends AbstractEchartsChartBuilder {

    @Override
    public ChartType getSupportedType() {
        return ChartType.BAR;
    }

    @Override
    protected void customizeOption(EchartsBuildContext ctx, Map<String, Object> option) {
        addCartesianAxes(ctx, option, horizontal(ctx) ? AxisLayout.CATEGORY_Y : AxisLayout.CATEGORY_X);
    }

    private boolean horizontal(EchartsBuildContext ctx) {
        ChartBarConfigModel config = barConfig(ctx);
        return config != null && config.getDir() == ChartBarDirection.BAR;
    }

    private ChartBarConfigModel barConfig(EchartsBuildContext ctx) {
        ChartPlotAreaModel plotArea = ctx.chart().getPlotArea();
        return plotArea != null ? plotArea.getBarConfig() : null;
    }

    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        ChartBarConfigModel config = barConfig(ctx);
        ChartBarGrouping grouping = config != null && config.getGrouping() != null
                ? config.getGrouping()
                : ChartBarGrouping.CLUSTERED;
        boolean stacked = grouping == ChartBarGrouping.STACKED || grouping == ChartBarGrouping.PERCENT_STACKED;

        List<ChartSeriesModel> seriesModels = ctx.chart().getPlotArea() != null
                ? ctx.chart().getPlotArea().getSeriesList()
                : null;
        List<List<Double>> percentValues = null;
        if (grouping == ChartBarGrouping.PERCENT_STACKED && seriesModels != null) {
            List<List<Number>> raw = new ArrayList<>();
            for (ChartSeriesModel s : seriesModels)
                raw.add(ctx.values(s));
            percentValues = EchartsDataTransforms.percentStacked(raw);
        }

        List<Object> seriesOut = new ArrayList<>();
        int index = 0;
        for (ChartSeriesModel series : seriesModels == null ? new ArrayList<ChartSeriesModel>() : seriesModels) {
            requireFamilyMatch(series, ChartType.BAR);
            Map<String, Object> map = baseSeries(series, ctx);
            map.put("type", "bar");

            List<Object> data;
            if (percentValues != null) {
                data = new ArrayList<>(percentValues.get(index));
            } else {
                boolean zeroFill = ctx.chart().getDispBlanksAs() == ChartDispBlankAs.ZERO;
                data = EchartsDataTransforms.dataValues(ctx.values(series), zeroFill);
            }
            map.put("data", data);

            if (stacked)
                map.put("stack", "stack");
            if (config != null) {
                if (config.getGapWidth() != null)
                    map.put("barCategoryGap", config.getGapWidth() + "%");
                if (config.getOverlap() != null)
                    map.put("barGap", config.getOverlap() + "%");
            }
            applyDataLabels(map, series, ctx, false);
            applyInvertIfNegative(map, series);
            applyDataPointStyles(map, series);

            seriesOut.add(map);
            ctx.addMainSeriesName(String.valueOf(map.get("name")));
            appendTrendLines(ctx, series, map, seriesOut);
            index++;
        }
        return seriesOut;
    }

    /**
     * invertIfNegative (Excel semantic): negative bars render in the complementary
     * fill; ECharts has no direct switch so negatives get a white overlay color.
     */
    private void applyInvertIfNegative(Map<String, Object> map, ChartSeriesModel series) {
        if (!Boolean.TRUE.equals(series.getInvertIfNegative()))
            return;
        List<Object> data = new ArrayList<>();
        for (Object v : (List<?>) map.get("data")) {
            Number num = v instanceof Number ? (Number) v : null;
            if (num != null && num.doubleValue() < 0) {
                Map<String, Object> point = EchartsStyleMapper.newMap();
                point.put("value", num);
                Map<String, Object> itemStyle = EchartsStyleMapper.newMap();
                itemStyle.put("color", "#FFFFFF");
                point.put("itemStyle", itemStyle);
                data.add(point);
            } else {
                data.add(v);
            }
        }
        map.put("data", data);
    }

    private void applyDataPointStyles(Map<String, Object> map, ChartSeriesModel series) {
        if (series.getDataPoints() == null || series.getDataPoints().isEmpty())
            return;
        List<Object> data = new ArrayList<>((List<?>) map.get("data"));
        for (ChartDataPointModel dp : series.getDataPoints()) {
            if (dp.getShapeStyle() == null || dp.getIndex() < 0 || dp.getIndex() >= data.size())
                continue;
            Map<String, Object> point = EchartsStyleMapper.newMap();
            point.put("value", data.get(dp.getIndex()));
            Map<String, Object> itemStyle = EchartsStyleMapper.itemStyle(dp.getShapeStyle());
            if (!itemStyle.isEmpty())
                point.put("itemStyle", itemStyle);
            data.set(dp.getIndex(), point);
        }
        map.put("data", data);
    }
}
