package io.nop.report.demo;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

@NopTestConfig(localDb = true)
public class TestReportEngine extends JunitBaseTestCase {
    @Inject
    IReportEngine reportEngine;

    @Test
    public void testUseDataInJava() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportPath = "/report/通过scope传递数据.xpt.xlsx";
        ITextTemplateOutput output = reportEngine.getHtmlRenderer(reportPath);

        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> data = classpathBean("_vfs/report/data.json5", Map.class);
        scope.setLocalValues(data);
        output.generateToFile(getTargetFile("test-scope.html"), scope);
    }
}
