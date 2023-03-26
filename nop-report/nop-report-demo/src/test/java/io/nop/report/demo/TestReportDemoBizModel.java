package io.nop.report.demo;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.TreeResultBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.report.demo.biz.ReportDemoBizModel;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

@NopTestConfig(localDb = true)
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
        String reportName = "/base/交叉报表—数据双向扩展.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);
    }
}