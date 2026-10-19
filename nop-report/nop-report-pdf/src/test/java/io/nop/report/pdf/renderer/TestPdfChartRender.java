package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.excel.renderer.ChartImageProviderRegistry;
import io.nop.excel.renderer.IChartImageProvider;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF chart rendering E2E (plan 2366 Phase 1): the bar-chart fixture flows through
 * xpt expansion → PdfSheetRenderer.renderCharts → provider (IoC-discovered from the
 * test-classpath nop-chart-report module) → ChartExporter → ECharts engine →
 * image XObjects in the PDF. Provider absence keeps the legacy path intact
 * (TestPdfRenderDefects covers chart-less workbooks).
 */
public class TestPdfChartRender extends JunitBaseTestCase {
    @Inject
    IReportEngine reportEngine;

    @Test
    public void testFixtureChartRenderedAsImageXObjects() throws Exception {
        // Provider must be discoverable through the IoC wiring (nop-chart-report
        // autoconfig on the test classpath) — same production discovery path as HTML.
        IChartImageProvider provider = ChartImageProviderRegistry.getProvider();
        assertNotNull(provider, "provider bean discovered via IoC");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reportEngine.getRenderer("/test/test-bar-chart.xpt.xlsx", "pdf")
                .generateToStream(out, XLang.newEvalScope());

        byte[] pdf = out.toByteArray();
        assertTrue(pdf.length > 0, "pdf generated");
        int images = TestPdfRenderDefects.countImageXObjects(pdf);
        assertTrue(images >= 1, "fixture chart renders as image XObject(s), got " + images);
    }
}
