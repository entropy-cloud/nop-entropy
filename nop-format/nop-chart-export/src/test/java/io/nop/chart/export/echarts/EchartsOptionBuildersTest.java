package io.nop.chart.export.echarts;

import io.nop.chart.export.IChartOptionBuilder;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.constants.ChartBarDirection;
import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.constants.ChartDispBlankAs;
import io.nop.excel.chart.constants.ChartLegendPosition;
import io.nop.excel.chart.constants.ChartMarkerType;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartGradientModel;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartLineStyleModel;
import io.nop.excel.chart.model.ChartMarkersModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartStyleModel;
import io.nop.excel.chart.model.ChartTitleModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.nop.chart.export.echarts.TestChartModels.axis;
import static io.nop.chart.export.echarts.TestChartModels.chart;
import static io.nop.chart.export.echarts.TestChartModels.data;
import static io.nop.chart.export.echarts.TestChartModels.option;
import static io.nop.chart.export.echarts.TestChartModels.series;
import static io.nop.chart.export.echarts.TestChartModels.seriesList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural option assertions for every ChartType builder (plan 2364 Phase 1):
 * series kinds, coordinates, palette/title/legend/data-label components.
 */
class EchartsOptionBuildersTest {

    private static final List<Object> CATS = Arrays.asList("Q1", "Q2", "Q3");

    @Test
    void coverageAllTenChartTypesHaveBuilders() {
        List<IChartOptionBuilder> builders = Arrays.asList(
                new BarChartOptionBuilder(),
                new LineChartOptionBuilder(),
                new AreaChartOptionBuilder(),
                new PieChartOptionBuilder(),
                new DoughnutChartOptionBuilder(),
                new ScatterChartOptionBuilder(),
                new BubbleChartOptionBuilder(),
                new RadarChartOptionBuilder(),
                new HeatmapChartOptionBuilder(),
                new ComboChartOptionBuilder());

        List<ChartType> covered = new ArrayList<>();
        for (IChartOptionBuilder b : builders)
            covered.add(b.getSupportedType());
        // Anti-hollow: the builder set must cover all 10 enum values.
        for (ChartType type : ChartType.values())
            assertTrue(covered.contains(type), "no builder for " + type);
        assertEquals(10, builders.size());
    }

    @Test
    void barGroupingsAndDirection() {
        BarChartOptionBuilder builder = new BarChartOptionBuilder();

        ChartModel stacked = chart(ChartType.BAR, series("s1"), series("s2"));
        ChartBarConfigModel cfg = new ChartBarConfigModel();
        cfg.setGrouping(ChartBarGrouping.STACKED);
        stacked.getPlotArea().setBarConfig(cfg);
        Map<String, Object> opt = option(builder, stacked,
                data("s1", CATS, Arrays.asList(1, 2, 3)),
                data("s2", CATS, Arrays.asList(4, 5, 6)));
        List<Map<String, Object>> series = seriesList(opt);
        assertEquals("bar", series.get(0).get("type"));
        assertEquals("stack", series.get(0).get("stack"));
        assertEquals("stack", series.get(1).get("stack"));

        ChartModel standard = chart(ChartType.BAR, series("s1"));
        ChartBarConfigModel stdCfg = new ChartBarConfigModel();
        stdCfg.setGrouping(ChartBarGrouping.STANDARD); // clustered alias (D37)
        standard.getPlotArea().setBarConfig(stdCfg);
        Map<String, Object> stdOpt = option(builder, standard, data("s1", CATS, Arrays.asList(1, 2, 3)));
        assertTrue(!seriesList(stdOpt).get(0).containsKey("stack"),
                "STANDARD must behave as clustered (no stack)");

        ChartModel horizontal = chart(ChartType.BAR, series("s1"));
        ChartBarConfigModel hCfg = new ChartBarConfigModel();
        hCfg.setDir(ChartBarDirection.BAR);
        horizontal.getPlotArea().setBarConfig(hCfg);
        Map<String, Object> hOpt = option(builder, horizontal, data("s1", CATS, Arrays.asList(1, 2, 3)));
        assertCategoryOnY(hOpt);
    }

    @SuppressWarnings("unchecked")
    private void assertCategoryOnY(Map<String, Object> option) {
        List<Map<String, Object>> yAxis = (List<Map<String, Object>>) option.get("yAxis");
        List<Map<String, Object>> xAxis = (List<Map<String, Object>>) option.get("xAxis");
        assertEquals("category", yAxis.get(0).get("type"));
        assertEquals("value", xAxis.get(0).get("type"));
    }

    @Test
    void percentStackedBarSumsToHundredPerCategory() {
        BarChartOptionBuilder builder = new BarChartOptionBuilder();
        ChartModel model = chart(ChartType.BAR, series("s1"), series("s2"));
        ChartBarConfigModel cfg = new ChartBarConfigModel();
        cfg.setGrouping(ChartBarGrouping.PERCENT_STACKED);
        model.getPlotArea().setBarConfig(cfg);
        Map<String, Object> opt = option(builder, model,
                data("s1", CATS, Arrays.asList(1, 2, null)),
                data("s2", CATS, Arrays.asList(3, 6, 5)));

        List<Map<String, Object>> series = seriesList(opt);
        List<Object> d1 = (List<Object>) series.get(0).get("data");
        List<Object> d2 = (List<Object>) series.get(1).get("data");
        for (int c = 0; c < 3; c++) {
            Number v1 = (Number) d1.get(c);
            Number v2 = (Number) d2.get(c);
            if (v1 == null && v2 == null)
                continue;
            double sum = (v1 == null ? 0 : v1.doubleValue()) + (v2 == null ? 0 : v2.doubleValue());
            assertTrue(Math.abs(sum - 100.0) < 0.001,
                    "category " + c + " percent sum " + sum + " != 100 (D36)");
        }
    }

    @Test
    void lineFamilyStackAndDispBlanksAs() {
        LineChartOptionBuilder line = new LineChartOptionBuilder();
        ChartModel model = chart(ChartType.LINE, series("s1"));
        model.setDispBlanksAs(ChartDispBlankAs.ZERO);
        Map<String, Object> opt = option(line, model, data("s1", CATS, Arrays.asList(1, null, 3)));
        List<Object> values = (List<Object>) seriesList(opt).get(0).get("data");
        assertEquals(0.0, ((Number) values.get(1)).doubleValue(), "ZERO fills blanks with 0 (D32)");

        ChartModel span = chart(ChartType.LINE, series("s1"));
        span.setDispBlanksAs(ChartDispBlankAs.SPAN);
        Map<String, Object> spanOpt = option(line, span, data("s1", CATS, Arrays.asList(1, null, 3)));
        assertEquals(true, seriesList(spanOpt).get(0).get("connectNulls"), "SPAN bridges gaps (D32)");

        AreaChartOptionBuilder area = new AreaChartOptionBuilder();
        Map<String, Object> areaOpt = option(area, chart(ChartType.AREA, series("s1")),
                data("s1", CATS, Arrays.asList(1, 2, 3)));
        Map<String, Object> areaSeries = seriesList(areaOpt).get(0);
        assertEquals("line", areaSeries.get("type"));
        assertNotNull(areaSeries.get("areaStyle"), "area series must carry areaStyle");
    }

    @Test
    void pieFamilyRadiusDataAndPercentLabel() {
        PieChartOptionBuilder pie = new PieChartOptionBuilder();
        Map<String, Object> opt = option(pie, chart(ChartType.PIE, series("s1")),
                data("s1", CATS, Arrays.asList(1, 2, 3)));
        Map<String, Object> s = seriesList(opt).get(0);
        assertEquals("pie", s.get("type"));
        assertEquals("75%", s.get("radius"));
        List<Object> pieData = (List<Object>) s.get("data");
        assertEquals("Q1", ((Map<String, Object>) pieData.get(0)).get("name"));
        assertEquals(1, ((Map<String, Object>) pieData.get(0)).get("value"));

        DoughnutChartOptionBuilder doughnut = new DoughnutChartOptionBuilder();
        Map<String, Object> dOpt = option(doughnut, chart(ChartType.DOUGHNUT, series("s1")),
                data("s1", CATS, Arrays.asList(1, 2, 3)));
        Object radius = seriesList(dOpt).get(0).get("radius");
        assertTrue(radius instanceof List, "doughnut radius is [inner, outer]");
        assertEquals("50%", ((List<Object>) radius).get(0));
    }

    @Test
    void scatterLineMarkerAndNullSkip() {
        ScatterChartOptionBuilder scatter = new ScatterChartOptionBuilder();
        // D38: lineMarker (fixture form) renders as a line series with symbols.
        Map<String, Object> opt = option(scatter, chart(ChartType.SCATTER, series("s1")),
                data("s1", Arrays.asList(1, 2, 3), Arrays.asList(10, null, 30)));
        Map<String, Object> s = seriesList(opt).get(0);
        assertEquals("line", s.get("type"));
        assertEquals("circle", s.get("symbol"));
        List<Object> points = (List<Object>) s.get("data");
        assertEquals(2, points.size(), "null y points are skipped, not zero-filled (D32)");
        assertEquals(Arrays.asList(1, 10), ((Map<String, Object>) points.get(0)).get("value"));
    }

    @Test
    void bubbleRendersScatterPoints() {
        BubbleChartOptionBuilder bubble = new BubbleChartOptionBuilder();
        Map<String, Object> opt = option(bubble, chart(ChartType.BUBBLE, series("s1")),
                data("s1", Arrays.asList(1, 2), Arrays.asList(10, 20)));
        Map<String, Object> s = seriesList(opt).get(0);
        assertEquals("scatter", s.get("type"));
        assertNotNull(s.get("symbolSize"), "bubble size falls back to a constant (D35)");
    }

    @Test
    void radarIndicatorsFromCategories() {
        RadarChartOptionBuilder radar = new RadarChartOptionBuilder();
        Map<String, Object> opt = option(radar, chart(ChartType.RADAR, series("s1")),
                data("s1", CATS, Arrays.asList(2, 8, 5)));
        Map<String, Object> radarCoord = (Map<String, Object>) opt.get("radar");
        List<Map<String, Object>> indicators = (List<Map<String, Object>>) radarCoord.get("indicator");
        assertEquals(3, indicators.size(), "indicator count = category count");
        assertEquals("Q1", indicators.get(0).get("name"));
        assertEquals(9.0, ((Number) indicators.get(1).get("max")).doubleValue(),
                "indicator max = value * 1.1 headroom");
        assertEquals("radar", seriesList(opt).get(0).get("type"));
    }

    @Test
    void heatmapGridAndVisualMap() {
        HeatmapChartOptionBuilder heatmap = new HeatmapChartOptionBuilder();
        Map<String, Object> opt = option(heatmap, chart(ChartType.HEATMAP, series("r1"), series("r2")),
                data("r1", CATS, Arrays.asList(1, null, 3)),
                data("r2", CATS, Arrays.asList(4, 5, 6)));
        Map<String, Object> visualMap = (Map<String, Object>) opt.get("visualMap");
        assertNotNull(visualMap, "heatmap needs a visualMap color band (audit defect 1 semantics)");
        assertEquals(1.0, ((Number) visualMap.get("min")).doubleValue());
        assertEquals(6.0, ((Number) visualMap.get("max")).doubleValue());
        List<Object> cells = (List<Object>) seriesList(opt).get(0).get("data");
        assertEquals(5, cells.size(), "null cells are skipped (D32)");
        assertEquals(Arrays.asList(0, 0, 1), cells.get(0));
        assertEquals(Arrays.asList(1, 1, 5), cells.get(3));
    }

    @Test
    void paletteTitleLegendAndGradient() {
        BarChartOptionBuilder builder = new BarChartOptionBuilder();
        ChartModel model = chart(ChartType.BAR, series("s1"));

        ChartStyleModel style = new ChartStyleModel();
        style.setColors(Arrays.asList("#111111", "4472C4"));
        model.setStyle(style);

        ChartTitleModel title = new ChartTitleModel();
        title.setText("Revenue");
        title.setVisible(true);
        model.setTitle(title);

        ChartLegendModel legend = new ChartLegendModel();
        legend.setVisible(true);
        legend.setPosition(ChartLegendPosition.TOP);
        model.setLegend(legend);

        ChartSeriesModel s = model.getPlotArea().getSeriesList().get(0);
        ChartFillModel fill = new ChartFillModel();
        ChartGradientModel grad = new ChartGradientModel();
        grad.setEnabled(true);
        grad.setStartColor("#FF0000");
        grad.setEndColor("0000FF");
        fill.setGradient(grad);
        s.setShapeStyle(newShapeStyle(fill));

        Map<String, Object> opt = option(builder, model, data("s1", CATS, Arrays.asList(1, 2, 3)));
        assertEquals(Arrays.asList("#111111", "#4472c4"), opt.get("color"),
                "palette maps to option.color with hex normalization");
        assertEquals("Revenue", ((Map<String, Object>) opt.get("title")).get("text"));
        assertEquals(0, ((Map<String, Object>) opt.get("legend")).get("top"));

        Map<String, Object> series = seriesList(opt).get(0);
        Map<String, Object> itemStyle = (Map<String, Object>) series.get("itemStyle");
        Map<String, Object> gradient = (Map<String, Object>) itemStyle.get("color");
        assertEquals("linear", gradient.get("type"));
        List<Map<String, Object>> stops = (List<Map<String, Object>>) gradient.get("colorStops");
        assertEquals("#ff0000", stops.get(0).get("color"));
        assertEquals("#0000ff", stops.get(1).get("color"));
    }

    private io.nop.excel.chart.model.ChartShapeStyleModel newShapeStyle(ChartFillModel fill) {
        io.nop.excel.chart.model.ChartShapeStyleModel shape = new io.nop.excel.chart.model.ChartShapeStyleModel();
        shape.setFill(fill);
        return shape;
    }

    @Test
    void axisTypesAndGridlines() {
        BarChartOptionBuilder builder = new BarChartOptionBuilder();
        ChartModel model = chart(ChartType.BAR, series("s1"));
        List<io.nop.excel.chart.model.ChartAxisModel> axes = new ArrayList<>();
        axes.add(axis("x", ChartAxisType.CATEGORY));
        axes.add(axis("y", ChartAxisType.LOG));
        model.getPlotArea().setAxes(io.nop.commons.collections.KeyedList.fromList(axes, a -> a.getId()));
        Map<String, Object> opt = option(builder, model, data("s1", CATS, Arrays.asList(1, 2, 3)));
        List<Map<String, Object>> xAxes = (List<Map<String, Object>>) opt.get("xAxis");
        List<Map<String, Object>> yAxes = (List<Map<String, Object>>) opt.get("yAxis");
        assertEquals("category", xAxes.get(0).get("type"));
        assertEquals("log", yAxes.get(0).get("type"), "LOG maps to type:'log' (D39)");
        assertEquals(CATS, xAxes.get(0).get("data"));
    }

    @Test
    void markersMapToSymbols() {
        LineChartOptionBuilder line = new LineChartOptionBuilder();
        ChartModel model = chart(ChartType.LINE, series("s1"));
        ChartMarkersModel markers = new ChartMarkersModel();
        markers.setEnabled(true);
        markers.setType(ChartMarkerType.SQUARE);
        markers.setSize(8.0);
        model.getPlotArea().getSeriesList().get(0).setMarkers(markers);
        ChartLineStyleModel lineStyle = new ChartLineStyleModel();
        lineStyle.setStyle(io.nop.excel.chart.constants.ChartLineStyle.DASH);
        lineStyle.setWidth(2.5);
        model.getPlotArea().getSeriesList().get(0).setLineStyle(lineStyle);

        Map<String, Object> opt = option(line, model, data("s1", CATS, Arrays.asList(1, 2, 3)));
        Map<String, Object> s = seriesList(opt).get(0);
        assertEquals("rect", s.get("symbol"));
        assertEquals(8.0, s.get("symbolSize"));
        assertEquals("dashed", ((Map<String, Object>) s.get("lineStyle")).get("type"));
    }
}
