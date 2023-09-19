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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@Disabled
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
        System.out.println("output path:" + resource.toFile());
        for (int i = 0; i < 100; i++) {
            System.out.println("i="+i);
            output.generateToResource(resource, scope);
        }
    }

    @Test
    public void testRenderXlsx() {
        // 生成的Excel中包含12000条结果记录。报表展开时执行大量分组、汇总、比较的计算。
        String path = "/nop/report/demo/performance/测试同比环比.xpt.xlsx";
        ITemplateOutput output = reportEngine.getRenderer(path, XptConstants.RENDER_TYPE_XLSX);
        IEvalScope scope = XLang.newEvalScope();
        IResource resource = getTargetResource("/test-speed-result.xlsx");
        System.out.println("output path:" + resource.toFile());
        for (int i = 0; i < 100; i++) {
            output.generateToResource(resource, scope);
        }
    }
}
