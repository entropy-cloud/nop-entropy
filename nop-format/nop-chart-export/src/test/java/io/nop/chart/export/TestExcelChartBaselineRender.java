package io.nop.chart.export;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.resolver.ExcelCellRefResolver;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9 baseline rendering (plan 2367): all 17 fixture charts rendered on a white
 * background with D53 mapping names, for visual comparison against the Excel
 * screenshots in docs/dev-guide/report/excel-chart/. Products land in
 * target/result-wi9/; the acceptance report copies them to
 * ai-dev/analysis/2026-10/wi9-render/ as evidence attachments.
 *
 * <p>D53 mapping (render order = workbook.xml order; final calibration anchor =
 * the Chinese chart title rendered inside each PNG): 0 filled-radar, 1 radar,
 * 2 scatter-with-lines, 3 scatter, 4 doughnut, 5 pie, 6 100pct-stacked-area,
 * 7 stacked-area, 8 area, 9 line-with-markers, 10 line, 11 100pct-stacked-bar,
 * 12 stacked-bar, 13 clustered-bar, 14 100pct-stacked-column, 15 stacked-column,
 * 16 clustered-column.</p>
 */
public class TestExcelChartBaselineRender extends BaseTestCase {
    private static final String[] D53_NAMES = {
            "filled-radar", "radar", "scatter-with-lines", "scatter", "doughnut", "pie",
            "100pct-stacked-area", "stacked-area", "area", "line-with-markers", "line",
            "100pct-stacked-bar", "stacked-bar", "clustered-bar",
            "100pct-stacked-column", "stacked-column", "clustered-column"
    };

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testRenderAllSeventeenWithMappingNames() throws Exception {
        IResource resource = new ClassPathResource("classpath:xlsx/test-all-charts.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);

        List<ExcelChartModel> charts = new ArrayList<>();
        List<ExcelCellRefResolver> resolvers = new ArrayList<>();
        for (ExcelSheet sheet : wk.getSheets()) {
            List<ExcelChartModel> sheetCharts = sheet.getCharts();
            if (sheetCharts == null || sheetCharts.isEmpty())
                continue;
            ExcelCellRefResolver resolver = new ExcelCellRefResolver(wk, sheet);
            for (ExcelChartModel chart : sheetCharts) {
                charts.add(chart);
                resolvers.add(resolver);
            }
        }
        assertEquals(17, charts.size(), "fixture chart count");

        ChartExporter exporter = new ChartExporter(ChartTypeRendererRegistry.getDefault());
        ChartExportOptions options = ChartExportOptions.defaultOptions();
        options.setWidth(1000);
        options.setHeight(750);
        options.setBackgroundColor("#FFFFFF");

        File outDir = getTargetFile("result-wi9");
        outDir.mkdirs();

        for (int i = 0; i < charts.size(); i++) {
            assertTrue(i < D53_NAMES.length, "D53 mapping covers index " + i);
            byte[] png = exporter.exportToImage(charts.get(i), resolvers.get(i), options);
            File target = new File(outDir, D53_NAMES[i] + ".png");
            java.nio.file.Files.write(target.toPath(), png);
            assertTrue(target.length() > 0, D53_NAMES[i] + ".png written");
        }
    }
}
