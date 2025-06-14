package io.nop.report.demo;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true,disableSnapshot = false)
public class TestReportDataSet extends JunitAutoTestCase {


    @Inject
    IReportEngine reportEngine;


    @EnableSnapshot(saveOutput = false)
    @Test
    public void testJdbcDataSet() {
        ITextTemplateOutput render = reportEngine.getHtmlRenderer("/report/test-jdbc-data-set.xpt.xlsx");
        String html = render.generateText(XLang.newEvalScope());
        outputText("result.html", html);
    }
}
