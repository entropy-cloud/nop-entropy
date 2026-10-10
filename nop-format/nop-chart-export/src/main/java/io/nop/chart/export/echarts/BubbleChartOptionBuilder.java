package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bubble chart option builder (plan 2364 D35): scatter geometry with a constant
 * default symbol size — the xlsx parse chain drops bubble size values (no
 * bubbleSizes producer), recorded as a known parse-chain limitation
 * (see plan 2364 Non-Blocking Follow-ups).
 */
public class BubbleChartOptionBuilder extends ScatterChartOptionBuilder {

    private static final double DEFAULT_BUBBLE_SIZE = 10.0;

    @Override
    public ChartType getSupportedType() {
        return ChartType.BUBBLE;
    }

    /**
     * Bubbles always render as scatter points regardless of any scatterStyle on the
     * model; series-level overrides may only be scatter/bubble.
     */
    @Override
    protected List<Object> buildSeries(EchartsBuildContext ctx) {
        List<Object> seriesOut = new ArrayList<>();
        for (Object o : super.buildSeries(ctx)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) o;
            map.put("type", "scatter");
            map.put("symbol", "circle");
            map.put("symbolSize", DEFAULT_BUBBLE_SIZE);
            seriesOut.add(map);
        }
        return seriesOut;
    }

    @Override
    protected void requireFamilyMatch(ChartSeriesModel series, ChartType... family) {
        if (series.getType() != null && series.getType() != ChartType.BUBBLE
                && series.getType() != ChartType.SCATTER)
            super.requireFamilyMatch(series, ChartType.BUBBLE);
    }
}
