package io.nop.chart.report;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.ChartImageProviderRegistry;
import io.nop.excel.renderer.IChartImageProvider;
import io.nop.excel.renderer.HtmlReportRendererFactory;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.excel.renderer.SimpleHtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.core.XptConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end HTML chart rendering (plan 2365 Phase 2): xpt template → expanded
 * sheets (chart pipeline) → provider (IoC-discovered) → ChartExporter → ECharts
 * engine → data URL in the HTML output. The provider-discovery leg of this test
 * goes through the beans.xml wiring (CoreInitialization loads the module's
 * autoconfig); the provider-absent silent-skip regression is pinned by the no-IoC
 * unit tests in nop-excel (ChartImageProviderRegistryTest).
 */
public class TestHtmlChartRender extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IReportEngine newReportEngine() {
        ReportEngine reportEngine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        reportEngine.setRenderers(renderers);
        return reportEngine;
    }

    @Test
    public void testFixtureHasCharts() {
        // Sanity: the fixture carries a chart through the expansion pipeline, so the
        // E2E below actually exercises chart rendering.
        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = reportEngine.getXptModel("/nop/report/demo/test-bar-chart.xpt.xlsx");
        reportEngine.getRendererForXptModel(workbook, XptConstants.RENDER_TYPE_XLSX)
                .generateToFile(getTargetFile("result-bar-chart.xlsx"), io.nop.xlang.api.XLang.newEvalScope());

        boolean hasChart = false;
        for (IExcelSheet sheet : workbook.getSheets()) {
            List<ExcelChartModel> charts = sheet.getCharts();
            if (charts != null && !charts.isEmpty())
                hasChart = true;
        }
        assertTrue(hasChart, "fixture workbook has charts after xlsx generation");
    }

    @Test
    public void testHtmlOutputContainsChartImages() {
        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = reportEngine.getXptModel("/nop/report/demo/test-bar-chart.xpt.xlsx");

        // The provider bean is loaded via autoconfig beans.xml; resolve it through the
        // production discovery path to prove the wiring (D42/R1 M2).
        IChartImageProvider provider = ChartImageProviderRegistry.getProvider();
        assertNotNull(provider, "provider bean discovered via IoC after CoreInitialization");
        assertTrue(provider instanceof ExcelChartImageProvider, "discovered provider is the module bean");

        String html = renderHtml(reportEngine, workbook, io.nop.xlang.api.XLang.newEvalScope());

        assertTrue(html.contains("data:image/png;base64,"), "html embeds chart png data urls");
        // the chart layer is a real image payload, not an empty placeholder
        String marker = "data:image/png;base64,";
        int at = html.indexOf(marker);
        int end = html.indexOf(')', at);
        String payload = html.substring(at + marker.length(), end);
        assertTrue(payload.length() > 100,
                "chart png payload is a real image, got " + payload.length() + " chars");

        File file = getTargetFile("result-bar-chart.html");
        io.nop.commons.util.FileHelper.writeText(file, html, null);
    }

    @Test
    public void testResolverReadsExpandedTableValues() {
        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = reportEngine.getXptModel("/nop/report/demo/test-bar-chart.xpt.xlsx");
        IExcelSheet sheet = workbook.getSheets().get(0);
        ExpandedSheetCellRefResolver resolver = new ExpandedSheetCellRefResolver(sheet);
        assertTrue(resolver.isValidRef("A1"));
        // empty-range reads are null-safe (D45 / R2 problem 4)
        Object value = resolver.getValue("A1");
        assertNotNull(value, "A1 of the fixture holds a header value");
        assertTrue(resolver.getValues("ZZ99:ZZ99").stream().allMatch(java.util.Objects::isNull),
                "out-of-range reads yield nulls, not exceptions (null-safe getCellValue)");
        // cross-sheet references are explicitly unsupported (single-sheet limitation)
        try {
            resolver.getValue("Other!A1");
            org.junit.jupiter.api.Assertions.fail("cross-sheet ref must be rejected");
        } catch (UnsupportedOperationException e) {
            // expected (D45)
        }
    }

    private String renderHtml(IReportEngine reportEngine, ExcelWorkbook workbook,
                              io.nop.core.context.IEvalContext scope) {
        io.nop.core.resource.tpl.ITextTemplateOutput output =
                (io.nop.core.resource.tpl.ITextTemplateOutput)
                        reportEngine.getRendererForXptModel(workbook, XptConstants.RENDER_TYPE_HTML);
        return output.generateText(scope);
    }
}
