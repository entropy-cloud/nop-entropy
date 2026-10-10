package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.chart.export.echarts.EchartsAdapterShapeAsserts;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.resolver.ExcelCellRefResolver;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end regression (plan 2364 Phase 2): all 17 charts of the test-all-charts
 * fixture flow through xlsx parsing → ChartModel → option adapter → GraalJS engine
 * → non-blank PNG bytes, plus svg export and option-level data-shape assertions
 * (percent-stacked transform, scatter lineMarker form, stack groups, radar
 * indicators).
 */
public class TestExcelAllChartsExporter extends BaseTestCase {
    private static final int EXPECTED_CHARTS = 17;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private record ChartRecord(ExcelChartModel chart, ExcelCellRefResolver resolver) {
    }

    private List<ChartRecord> parseCharts() {
        IResource resource = new ClassPathResource("classpath:xlsx/test-all-charts.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);
        List<ExcelSheet> sheets = wk.getSheets();
        assertTrue(sheets != null && !sheets.isEmpty(), "Workbook should have at least one sheet");

        List<ChartRecord> records = new ArrayList<>();
        for (ExcelSheet sheet : sheets) {
            ExcelCellRefResolver resolver = new ExcelCellRefResolver(wk, sheet);
            List<ExcelChartModel> charts = sheet.getCharts();
            if (charts == null)
                continue;
            for (ExcelChartModel chart : charts)
                records.add(new ChartRecord(chart, resolver));
        }
        return records;
    }

    @Test
    public void testAllFixtureChartsExportNonBlankPng() {
        List<ChartRecord> records = parseCharts();
        assertEquals(EXPECTED_CHARTS, records.size(), "fixture chart count");

        File resultDir = getTargetFile("result");
        resultDir.mkdirs();
        ChartExporter exporter = new ChartExporter(ChartTypeRendererRegistry.getDefault());

        int index = 0;
        for (ChartRecord record : records) {
            File outputFile = new File(resultDir, "chart_" + index + "_" + record.chart().getName() + ".png");
            exporter.exportToFile(outputFile, record.chart(), record.resolver());

            assertTrue(outputFile.length() > 0, "chart " + index + " produced no bytes");
            EchartsAdapterShapeAsserts.assertPngMagic(outputFile);
            EchartsAdapterShapeAsserts.assertNonBlank(outputFile, "chart_" + index);
            index++;
        }
    }

    @Test
    public void testSvgExportAndUnsupportedFormatFails() {
        List<ChartRecord> records = parseCharts();
        ChartExporter exporter = new ChartExporter(ChartTypeRendererRegistry.getDefault());

        ChartExportOptions svg = ChartExportOptions.defaultOptions();
        svg.setFormat("svg");
        byte[] bytes = exporter.exportToImage(records.get(0).chart(), records.get(0).resolver(), svg);
        String markup = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(markup.contains("<svg"), "svg format must return svg markup");

        ChartExportOptions jpeg = ChartExportOptions.defaultOptions();
        jpeg.setFormat("jpeg");
        assertThrows(NopException.class,
                () -> exporter.exportToImage(records.get(0).chart(), records.get(0).resolver(), jpeg),
                "unsupported format must fail fast (D28)");
    }

    @Test
    public void testExportOptionsTakeEffect() throws Exception {
        List<ChartRecord> records = parseCharts();
        ChartExporter exporter = new ChartExporter(ChartTypeRendererRegistry.getDefault());

        ChartExportOptions options = ChartExportOptions.defaultOptions();
        options.setWidth(320);
        options.setHeight(240);
        options.setBackgroundColor("#F5F5F5");
        byte[] png = exporter.exportToImage(records.get(0).chart(), records.get(0).resolver(), options);
        BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(320, image.getWidth(), "options.width drives the render size");
        assertEquals(240, image.getHeight(), "options.height drives the render size");
    }

    @Test
    public void testDataShapeSemanticsAcrossFixture() {
        // Option-tree assertions on all parsed fixture charts (R1 m9): guards the
        // E2E green against silently-wrong-output renders.
        List<ChartRecord> records = parseCharts();
        ChartTypeRendererRegistry registry = ChartTypeRendererRegistry.getDefault();
        int percentStackedChecked = 0;
        int scatterChecked = 0;
        int stackedChecked = 0;
        int radarChecked = 0;

        for (ChartRecord record : records) {
            ExcelChartModel chart = record.chart();
            IChartOptionBuilder builder = registry.getRenderer(chart.getType());
            assertNotNull(builder);

            if (isPercentStacked(chart)) {
                EchartsAdapterShapeAsserts.assertPercentStackedSums(chart, record.resolver(), builder);
                percentStackedChecked++;
            }
            if (chart.getType() == ChartType.SCATTER) {
                EchartsAdapterShapeAsserts.assertScatterLineForm(chart, record.resolver(), builder);
                scatterChecked++;
            }
            if (isStacked(chart)) {
                EchartsAdapterShapeAsserts.assertStacked(chart, record.resolver(), builder);
                stackedChecked++;
            }
            if (chart.getType() == ChartType.RADAR) {
                EchartsAdapterShapeAsserts.assertRadarIndicators(chart, record.resolver(), builder);
                radarChecked++;
            }
        }
        // Fixture composition (verified 2026-10-10): bar clustered 2 / stacked 2 /
        // percentStacked 2, area clustered 1 / stacked 1 / percentStacked 1,
        // line standard 2, radar 2, scatter 2, pie 1, doughnut 1.
        assertEquals(3, percentStackedChecked, "percentStacked charts in fixture");
        assertEquals(2, scatterChecked, "scatter charts in fixture");
        assertEquals(3, stackedChecked, "stacked charts in fixture");
        assertEquals(2, radarChecked, "radar charts in fixture");
    }

    private boolean isPercentStacked(ExcelChartModel chart) {
        return "percentStacked".equals(EchartsAdapterShapeAsserts.groupingOf(chart));
    }

    private boolean isStacked(ExcelChartModel chart) {
        return "stacked".equals(EchartsAdapterShapeAsserts.groupingOf(chart));
    }
}
