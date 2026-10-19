package io.nop.chart.export.echarts;

import io.nop.chart.export.IChartOptionBuilder;
import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.model.ChartAreaConfigModel;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.excel.chart.model.ChartLineConfigModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.resolver.ChartDataResolver;
import io.nop.excel.resolver.ICellRefResolver;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural and pixel assertions shared by the nop-chart-export fixture tests
 * (plan 2364 D34: local helper, ImageDiffAssert stays in nop-chart-echarts).
 */
public final class EchartsAdapterShapeAsserts {

    private EchartsAdapterShapeAsserts() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildOption(ChartModel chart, ICellRefResolver resolver,
                                                  IChartOptionBuilder builder) {
        List<ChartDataSet> dataSets = ChartDataResolver.INSTANCE.resolveSeriesData(chart.getPlotArea(), resolver);
        return (Map<String, Object>) builder.buildOption(chart, resolver, dataSets);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> seriesList(Map<String, Object> option) {
        return (List<Map<String, Object>>) option.get("series");
    }

    public static void assertPngMagic(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[8];
            int read = in.read(magic);
            assertEquals(8, read, "PNG header readable");
            assertArrayEquals(magic, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + file, e);
        }
    }

    private static void assertArrayEquals(byte[] actual, byte[] expected) {
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], actual[i], "PNG magic byte " + i);
    }

    /**
     * Non-blank check on an alpha-bearing PNG: counts pixels with alpha > 10 or
     * luminance differing from white (same criterion as ImageDiffAssert.assertNotBlank).
     */
    public static void assertNonBlank(File file, String label) {
        try {
            java.awt.image.BufferedImage image = ImageIO.read(file);
            assertNotNull(image, label + " decodes as image");
            assertTrue(ImageDiffLike.hasContent(image), label + " renders non-blank content");
        } catch (IOException e) {
            throw new IllegalStateException("failed to decode " + file, e);
        }
    }

    public static void assertNonBlankBytes(byte[] png, String label) {
        try {
            java.awt.image.BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertNotNull(image, label + " decodes as image");
            assertTrue(ImageDiffLike.hasContent(image), label + " renders non-blank content");
        } catch (IOException e) {
            throw new IllegalStateException("failed to decode " + label, e);
        }
    }

    private static final class ImageDiffLike {
        static boolean hasContent(java.awt.image.BufferedImage image) {
            int width = image.getWidth();
            int height = image.getHeight();
            int minPixels = Math.max(100, width * height / 1000);
            int content = 0;
            for (int y = 0; y < height && content < minPixels; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha > 10) {
                        content++;
                        if (content >= minPixels)
                            return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * D36: for percentStacked charts every category's transformed series values sum
     * to ~100.
     */
    public static void assertPercentStackedSums(ChartModel chart, ICellRefResolver resolver,
                                                IChartOptionBuilder builder) {
        Map<String, Object> option = buildOption(chart, resolver, builder);
        List<Map<String, Object>> series = seriesList(option);
        assertTrue(series.size() >= 2, "percentStacked chart has multiple series");
        int categoryCount = 0;
        for (Map<String, Object> s : series) {
            List<Object> data = (List<Object>) s.get("data");
            categoryCount = Math.max(categoryCount, data.size());
        }
        for (int c = 0; c < categoryCount; c++) {
            double sum = 0;
            boolean any = false;
            for (Map<String, Object> s : series) {
                List<Object> data = (List<Object>) s.get("data");
                if (c >= data.size())
                    continue;
                Object v = data.get(c);
                if (v instanceof Number) {
                    sum += ((Number) v).doubleValue();
                    any = true;
                }
            }
            if (any)
                assertTrue(Math.abs(sum - 100.0) < 0.5,
                        "percentStacked category " + c + " sums to " + sum + " (D36)");
        }
    }

    /**
     * D38: the fixture scatter charts use scatterStyle=lineMarker and must render as
     * line series with a symbol.
     */
    public static void assertScatterLineForm(ChartModel chart, ICellRefResolver resolver,
                                             IChartOptionBuilder builder) {
        Map<String, Object> option = buildOption(chart, resolver, builder);
        for (Map<String, Object> s : seriesList(option)) {
            assertEquals("line", s.get("type"), "lineMarker scatter renders as line series (D38)");
            assertNotNull(s.get("symbol"), "lineMarker scatter shows markers");
            assertTrue((List<Object>) s.get("data") != null && !((List<Object>) s.get("data")).isEmpty(),
                    "lineMarker scatter has data points");
        }
    }

    /**
     * D37: stacked grouping puts every series into the same ECharts stack.
     */
    public static void assertStacked(ChartModel chart, ICellRefResolver resolver,
                                     IChartOptionBuilder builder) {
        Map<String, Object> option = buildOption(chart, resolver, builder);
        List<Map<String, Object>> series = seriesList(option);
        assertTrue(series.size() >= 2, "stacked chart has multiple series");
        for (Map<String, Object> s : series)
            assertEquals("stack", s.get("stack"), "all series share one stack group (D37)");
    }

    /**
     * Radar indicators mirror the resolved categories.
     */
    public static void assertRadarIndicators(ChartModel chart, ICellRefResolver resolver,
                                             IChartOptionBuilder builder) {
        Map<String, Object> option = buildOption(chart, resolver, builder);
        Map<String, Object> radar = (Map<String, Object>) option.get("radar");
        assertNotNull(radar, "radar coordinate present");
        List<Object> indicators = (List<Object>) radar.get("indicator");
        assertNotNull(indicators, "radar indicators present");
        assertTrue(!indicators.isEmpty(), "radar has indicators from categories");
        List<Map<String, Object>> series = seriesList(option);
        for (Map<String, Object> s : series)
            assertEquals("radar", s.get("type"));
    }

    /**
     * The bar/line/area grouping configured on the chart model (null when absent).
     */
    public static String groupingOf(ChartModel chart) {
        ChartPlotAreaModel plotArea = chart.getPlotArea();
        if (plotArea == null)
            return null;
        ChartBarGrouping grouping = null;
        ChartBarConfigModel bar = plotArea.getBarConfig();
        if (bar != null)
            grouping = bar.getGrouping();
        if (grouping == null) {
            ChartLineConfigModel line = plotArea.getLineConfig();
            if (line != null)
                grouping = line.getGrouping();
        }
        if (grouping == null) {
            ChartAreaConfigModel area = plotArea.getAreaConfig();
            if (area != null)
                grouping = area.getGrouping();
        }
        return grouping == null ? null : grouping.value();
    }
}
