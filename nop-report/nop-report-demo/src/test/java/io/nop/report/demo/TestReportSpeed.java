package io.nop.report.demo;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@NopTestConfig(localDb = true)
public class TestReportSpeed extends JunitBaseTestCase {

    @Inject
    IReportEngine reportEngine;

    @Test
    public void testRenderHtml() {
        String path = "/nop/report/demo/performance/测试同比环比.xpt.xlsx";
        ITextTemplateOutput output = reportEngine.getHtmlRenderer(path);
        IEvalScope scope = XLang.newEvalScope();
        IResource resource = getTargetResource("/test-speed-result.xpt.html");
        output.generateToResource(resource, scope);

    }

    @Test
    public void testRenderXlsx() {
        String path = "/nop/report/demo/performance/测试同比环比.xpt.xlsx";
        ITemplateOutput output = reportEngine.getRenderer(path, XptConstants.RENDER_TYPE_XLSX);
        IEvalScope scope = XLang.newEvalScope();
        IResource resource = getTargetResource("/test-speed-result.xlsx");
        output.generateToResource(resource, scope);
    }
}
