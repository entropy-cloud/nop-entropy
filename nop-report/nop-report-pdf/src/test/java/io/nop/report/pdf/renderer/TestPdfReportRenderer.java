package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.IResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

public class TestPdfReportRenderer extends JunitBaseTestCase {
    @Inject
    IReportEngine reportEngine;

    @Test
    public void testPdf() {
        ITemplateOutput out = reportEngine.getRenderer("/test/test-pdf.xpt.xlsx", "pdf");
        IResource resource = getTargetResource("gen/test.pdf");
        out.generateToResource(resource, XLang.newEvalScope());
    }
}
