package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void testDocumentClosedAfterGenerate() throws Exception {
        PdfReportRenderer renderer = (PdfReportRenderer) reportEngine.getRenderer("/test/test-pdf.xpt.xlsx", "pdf");
        renderer.generateToStream(new ByteArrayOutputStream(), XLang.newEvalScope());

        Field rendererField = PdfReportRenderer.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        PdfRenderer pdfRenderer = (PdfRenderer) rendererField.get(renderer);
        // 生成完成后PDDocument应被关闭，避免资源泄漏
        assertTrue(pdfRenderer.getDocument().getDocument().isClosed());
    }
}
