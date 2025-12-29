package io.nop.report.core.engine;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.HtmlReportRendererFactory;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class TestReportChart extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testBarChart() {
        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = reportEngine.getXptModel("/nop/report/demo/test-bar-chart.xpt.xlsx");

        ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, "xlsx");
        output.generateToFile(getTargetFile("result-bar-chart.xlsx"), XLang.newEvalScope());
    }

    private IReportEngine newReportEngine() {
        ReportEngine reportEngine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        reportEngine.setRenderers(renderers);
        return reportEngine;
    }
}
