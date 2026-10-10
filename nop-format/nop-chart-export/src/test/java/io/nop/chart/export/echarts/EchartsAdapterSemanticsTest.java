package io.nop.chart.export.echarts;

import io.nop.excel.chart.model.ChartModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.excel.chart.constants.ChartTrendLineType;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartTrendLineModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.nop.chart.export.echarts.TestChartModels.chart;
import static io.nop.chart.export.echarts.TestChartModels.data;
import static io.nop.chart.export.echarts.TestChartModels.option;
import static io.nop.chart.export.echarts.TestChartModels.series;
import static io.nop.chart.export.echarts.TestChartModels.seriesList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adjudication behavior tests (plan 2364 D29/D30/D32/D35/D36/D37/D39):
 * trend lines, combo dispatch, fail-fast error paths.
 */
class EchartsAdapterSemanticsTest {

    private static final List<Object> CATS = Arrays.asList("1", "2", "3", "4");

    @Test
    void linearTrendLineBecomesMarkLine() {
        LineChartOptionBuilder line = new LineChartOptionBuilder();
        ChartModel model = chart(ChartType.LINE, series("s1"));
        ChartTrendLineModel trend = new ChartTrendLineModel();
        trend.setType(ChartTrendLineType.LINEAR);
        model.getPlotArea().getSeriesList().get(0).setTrendLines(
                io.nop.commons.collections.KeyedList.fromList(
                        new ArrayList<>(List.of(trend)), t -> "t"));

        Map<String, Object> opt = option(line, model, data("s1", CATS, Arrays.asList(1, 3, 5, 7)));
        Map<String, Object> s = seriesList(opt).get(0);
        Map<String, Object> markLine = (Map<String, Object>) s.get("markLine");
        assertNotNull(markLine, "LINEAR trendline renders as markLine (D29)");
        List<Object> points = (List<Object>) markLine.get("data");
        assertEquals(2, points.size());
        // y = 1 + 2x over category indices 0..3
        assertEquals(Arrays.asList(0.0, 1.0), ((Map<String, Object>) points.get(0)).get("coord"));
        assertEquals(Arrays.asList(3.0, 7.0), ((Map<String, Object>) points.get(1)).get("coord"));
    }

    @Test
    void movingAverageTrendLineBecomesOverlaySeries() {
        LineChartOptionBuilder line = new LineChartOptionBuilder();
        ChartModel model = chart(ChartType.LINE, series("s1"));
        ChartTrendLineModel trend = new ChartTrendLineModel();
        trend.setType(ChartTrendLineType.MOVING_AVG);
        trend.setPeriod(2);
        model.getPlotArea().getSeriesList().get(0).setTrendLines(
                io.nop.commons.collections.KeyedList.fromList(
                        new ArrayList<>(List.of(trend)), t -> "t"));

        Map<String, Object> opt = option(line, model, data("s1", CATS, Arrays.asList(1, 3, 5, 7)));
        List<Map<String, Object>> series = seriesList(opt);
        assertEquals(2, series.size(), "non-linear trendline adds an overlay series (D29)");
        Map<String, Object> overlay = series.get(1);
        assertEquals("line", overlay.get("type"));
        assertEquals("none", overlay.get("symbol"));
        assertEquals(Arrays.asList(null, 2.0, 4.0, 6.0), overlay.get("data"));
    }

    @Test
    void linearTrendDegenerateInputsFailFast() {
        // With category-index x values, n=1 is the only zero-x-variance case (R2 N2).
        assertThrows(NopException.class, () -> TrendLineComputer.linearFit(List.of(5)),
                "n=1 cannot fit a line (R2 N2)");
        // y-constant series are a valid horizontal line, not an error (R2 N2).
        assertArrayEquals(new double[]{1.0, 1.0}, TrendLineComputer.linearFit(Arrays.asList(1, 1, 1, 1)), 1e-9);
    }

    @Test
    void comboDispatchesBySeriesType() {
        ComboChartOptionBuilder combo = new ComboChartOptionBuilder();
        ChartSeriesModel bar = series("b");
        bar.setType(ChartType.BAR);
        ChartSeriesModel line = series("l");
        line.setType(ChartType.LINE);
        ChartModel model = chart(ChartType.COMBO, bar, line);

        Map<String, Object> opt = option(combo, model,
                data("b", CATS, Arrays.asList(1, 2, 3, 4)),
                data("l", CATS, Arrays.asList(2, 3, 4, 5)));
        List<Map<String, Object>> series = seriesList(opt);
        assertEquals("bar", series.get(0).get("type"));
        assertEquals("line", series.get(1).get("type"));
        assertEquals("b", series.get(0).get("name"), "combo keeps series names (audit defect 3)");
        assertEquals("l", series.get(1).get("name"));
    }

    @Test
    void comboWithoutSeriesTypesFailsFast() {
        ComboChartOptionBuilder combo = new ComboChartOptionBuilder();
        ChartModel model = chart(ChartType.COMBO, series("s1"));
        // The xlsx parse chain never records series-level types for combos: fail fast.
        NopException e = assertThrows(NopException.class,
                () -> option(combo, model, data("s1", CATS, Arrays.asList(1, 2, 3, 4))));
        String code = e.getErrorCode();
        assertTrue(code.contains("unsupported-chart-type"), "got " + code);
    }

    @Test
    void seriesAxisTypeFailsFast() {
        BarChartOptionBuilder bar = new BarChartOptionBuilder();
        ChartModel model = chart(ChartType.BAR, series("s1"));
        List<io.nop.excel.chart.model.ChartAxisModel> axes = new ArrayList<>();
        axes.add(TestChartModels.axis("x", io.nop.excel.chart.constants.ChartAxisType.SERIES));
        model.getPlotArea().setAxes(io.nop.commons.collections.KeyedList.fromList(axes, a -> a.getId()));
        NopException e = assertThrows(NopException.class,
                () -> option(bar, model, data("s1", CATS, Arrays.asList(1, 2, 3, 4))));
        assertTrue(e.getErrorCode().contains("unsupported-chart-type"),
                "series axis has no ECharts equivalent (D39)");
    }

    @Test
    void scatterNonNumericXCategoriesFailFast() {
        ScatterChartOptionBuilder scatter = new ScatterChartOptionBuilder();
        ChartModel model = chart(ChartType.SCATTER, series("s1"));
        NopException e = assertThrows(NopException.class,
                () -> option(scatter, model,
                        data("s1", Arrays.asList("Q1", "Q2"), Arrays.asList(1, 2))));
        assertTrue(e.getErrorCode().contains("data-resolution-failed"),
                "non-numeric scatter x must fail fast, not fall back to index (D35/R2 N1)");
    }

    @Test
    void emptyModelsProduceValidOptions() {
        // D32: bar/line minimum models (no data) produce a legal option, no NPE.
        Map<String, Object> barOpt = option(new BarChartOptionBuilder(),
                chart(ChartType.BAR, series("s1")), data("s1", new ArrayList<>(), new ArrayList<>()));
        assertEquals(1, seriesList(barOpt).size());

        Map<String, Object> radarOpt = option(new RadarChartOptionBuilder(),
                chart(ChartType.RADAR, series("s1")), data("s1", new ArrayList<>(), new ArrayList<>()));
        assertNotNull(((Map<String, Object>) radarOpt.get("radar")).get("indicator"));

        Map<String, Object> heatmapOpt = option(new HeatmapChartOptionBuilder(),
                chart(ChartType.HEATMAP));
        assertTrue(seriesList(heatmapOpt).isEmpty(), "heatmap without series renders empty, no NPE");
    }
}
