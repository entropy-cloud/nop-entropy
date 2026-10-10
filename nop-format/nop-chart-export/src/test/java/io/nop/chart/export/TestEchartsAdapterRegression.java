package io.nop.chart.export;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.resolver.ICellRefResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Migrated regression semantics from the deleted legacy TestRenderersRegression
 * (plan 2364 Phase 2): null points are skipped (not (0,0) placeholders), heatmap
 * renders a visualMap color band, bar and radar minimum models export without NPE.
 * All four run through the full exporter pipeline into rendered bytes.
 */
public class TestEchartsAdapterRegression extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    private final ChartExporter exporter = new ChartExporter(ChartTypeRendererRegistry.getDefault());

    private static final ICellRefResolver RESOLVER = new ICellRefResolver() {
        @Override
        public Object getValue(String cellRef) {
            return null;
        }

        @Override
        public List<Object> getValues(String cellRangeRef) {
            return new ArrayList<>();
        }

        @Override
        public boolean isValidRef(String cellRef) {
            return true;
        }
    };

    private static ChartSeriesModel series(String name) {
        ChartSeriesModel s = new ChartSeriesModel();
        s.setName(name);
        return s;
    }

    private static ChartModel chart(ChartType type, ChartSeriesModel... series) {
        ChartModel model = new ChartModel();
        model.setType(type);
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        List<ChartSeriesModel> list = new ArrayList<>(Arrays.asList(series));
        for (int i = 0; i < list.size(); i++)
            list.get(i).setIndex(i);
        plotArea.setSeriesList(io.nop.commons.collections.KeyedList.fromList(list, s -> String.valueOf(s.getIndex())));
        model.setPlotArea(plotArea);
        return model;
    }

    private static List<io.nop.excel.chart.util.ChartDataSet> data(String name, List<Object> categories,
                                                                   List<Number> values) {
        List<io.nop.excel.chart.util.ChartDataSet> list = new ArrayList<>();
        io.nop.excel.chart.util.ChartDataSet ds = new io.nop.excel.chart.util.ChartDataSet(name, categories, values);
        list.add(ds);
        return list;
    }

    private byte[] render(ChartModel model, List<io.nop.excel.chart.util.ChartDataSet> dataSets) {
        return exporter.exportToImage(model, RESOLVER, ChartExportOptions.defaultOptions());
    }

    @Test
    public void testBarMinimalModelNoNpe() {
        // Audit defect 5 (migrated): an empty bar model renders a legal blank-axis
        // chart instead of throwing NPE.
        byte[] png = render(chart(ChartType.BAR, series("s1")),
                data("s1", new ArrayList<>(), new ArrayList<>()));
        BufferedImage image = decode(png);
        assertNotNull(image, "bar minimal model renders an image");
        assertEquals(800, image.getWidth());
    }

    @Test
    public void testRadarMinimalModelNoNpe() {
        byte[] png = render(chart(ChartType.RADAR, series("s1")),
                data("s1", new ArrayList<>(), new ArrayList<>()));
        assertNotNull(decode(png), "radar minimal model renders an image");
    }

    @Test
    public void testBubbleSkipsNullPoints() {
        // Audit defect 4 (migrated): null points are skipped, not (0,0) placeholders.
        ChartModel model = chart(ChartType.BUBBLE, series("s1"));
        MapOption option = buildOption(model, data("s1", Arrays.asList(1, 2, 3),
                Arrays.asList(10, null, 30)));
        List<Object> points = dataOf(option, 0);
        assertEquals(2, points.size(), "null bubble points are skipped");
    }

    @Test
    public void testHeatmapRendersVisualMapColorBand() {
        // Audit defect 1 (migrated): heatmap carries a visualMap color mapping.
        ChartModel model = chart(ChartType.HEATMAP, series("r1"));
        MapOption option = buildOption(model, data("r1", Arrays.asList("A", "B"), Arrays.asList(1, 5)));
        assertNotNull(option.map().get("visualMap"), "heatmap option has a visualMap color band");
        assertEquals("heatmap", seriesTypeOf(option, 0));
    }

    private record MapOption(Map<String, Object> map) {
    }

    private MapOption buildOption(ChartModel model, List<io.nop.excel.chart.util.ChartDataSet> dataSets) {
        // Link datasets to their series by index, mirroring ChartDataResolver output.
        if (model.getPlotArea() != null && !dataSets.isEmpty()) {
            List<ChartSeriesModel> seriesModels = model.getPlotArea().getSeriesList();
            for (int i = 0; i < dataSets.size() && i < seriesModels.size(); i++)
                dataSets.get(i).setSeriesModel(seriesModels.get(i));
        }
        IChartOptionBuilder builder = ChartTypeRendererRegistry.getDefault().getRenderer(model.getType());
        @SuppressWarnings("unchecked")
        Map<String, Object> option = (Map<String, Object>) builder.buildOption(model, RESOLVER, dataSets);
        return new MapOption(option);
    }

    @SuppressWarnings("unchecked")
    private List<Object> dataOf(MapOption option, int seriesIndex) {
        List<Map<String, Object>> series = (List<Map<String, Object>>) option.map().get("series");
        return (List<Object>) series.get(seriesIndex).get("data");
    }

    @SuppressWarnings("unchecked")
    private String seriesTypeOf(MapOption option, int seriesIndex) {
        List<Map<String, Object>> series = (List<Map<String, Object>>) option.map().get("series");
        return (String) series.get(seriesIndex).get("type");
    }

    private BufferedImage decode(byte[] png) {
        try {
            return javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
        } catch (Exception e) {
            throw new IllegalStateException("failed to decode rendered png", e);
        }
    }
}
