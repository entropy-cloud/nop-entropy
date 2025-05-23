package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestPdfReportRenderer extends JunitBaseTestCase {
    @Inject
    IReportEngine reportEngine;

    @Test
    public void testRender() {
        File file = getTargetFile("result.pdf");
        reportEngine.getRenderer("/test/test-pdf.xpt.xlsx", "pdf").generateToFile(file, XLang.newEvalScope());
    }

    @Test
    public void testRenderImage() {
        File file = getTargetFile("result-image.pdf");
        reportEngine.getRenderer("/test/test-image.xpt.xlsx", "pdf").generateToFile(file, XLang.newEvalScope());
    }
}
