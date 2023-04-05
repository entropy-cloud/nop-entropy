package io.nop.report.demo;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.TreeResultBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.report.demo.biz.ReportDemoBizModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

@NopTestConfig(localDb = true)
@Disabled
public class TestReportDemoBizModel extends JunitAutoTestCase {

    @Inject
    ReportDemoBizModel reportDemo;

    @EnableSnapshot
    @Test
    public void testReport() {
        List<TreeResultBean> reports = reportDemo.getDemoReports();
        for (TreeResultBean group : reports) {
            for (TreeResultBean report : group.getChildren()) {
                String html = reportDemo.renderHtml(report.getValue());
                outputText(report.getValue() + ".html", html);
            }
        }
    }

    @EnableSnapshot
    @Test
    public void testSingleReport() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/同比环比等财务统计表.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);
    }
}