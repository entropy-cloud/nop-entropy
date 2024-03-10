/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.demo;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.TreeResultBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.report.core.XptConstants;
import io.nop.report.demo.biz.ReportDemoBizModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

@NopTestConfig(localDb = true, disableSnapshot = false)
public class TestReportDemoBizModel extends JunitAutoTestCase {

    @Inject
    ReportDemoBizModel reportDemo;

    @EnableSnapshot
    @Test
    public void testReport() {
        CoreMetrics.today();
        List<TreeResultBean> reports = reportDemo.getDemoReports();
        for (TreeResultBean group : reports) {
            for (TreeResultBean report : group.getChildren()) {
                String html = reportDemo.renderHtml(report.getValue());
                outputText(report.getValue() + ".html", html);
            }
        }
    }


//    @EnableSnapshot
//    @Test
//    public void runOnce() {
//        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
//        String reportName = "/base/02-段落明细表.xpt.xlsx";
//        String html = reportDemo.renderHtml(reportName);
//        outputText(reportName + ".html", html);
//
//        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
//        File file = (File) result.getContent();
//        FileHelper.copyFile(file, getTargetFile("test-report.xlsx"));
//        file.delete();
//    }

    @EnableSnapshot
    @Test
    public void testSingleReport() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/05-同比环比等财务统计表.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);

        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
        File file = (File) result.getContent();
        FileHelper.copyFile(file, getTargetFile("test-report.xlsx"));
        file.delete();
    }

    @EnableSnapshot
    @Test
    public void testFormPrinting() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/09-套打.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);

        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
        File file = (File) result.getContent();
        FileHelper.copyFile(file, getTargetFile("test-form-printing.xlsx"));
        file.delete();
    }


    @EnableSnapshot
    @Test
    public void testExportFormula() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/04-交叉报表—数据双向扩展.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);

        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
        File file = (File) result.getContent();
        FileHelper.copyFile(file, getTargetFile("test-report-04.xlsx"));
        file.delete();
    }

    @EnableSnapshot
    @Test
    public void testExportFilterFormula() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/10-导出Excel公式.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);

        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
        File file = (File) result.getContent();
        FileHelper.copyFile(file, getTargetFile("test-export-formula.xlsx"));
        file.delete();
    }
}