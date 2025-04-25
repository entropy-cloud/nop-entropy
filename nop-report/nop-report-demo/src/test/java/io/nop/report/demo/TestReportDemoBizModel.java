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
import io.nop.report.core.XptConstants;
import io.nop.report.demo.biz.ReportDemoBizModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NopTestConfig(localDb = true, disableSnapshot = false)
public class TestReportDemoBizModel extends JunitAutoTestCase {

    @Inject
    ReportDemoBizModel reportDemo;

    @EnableSnapshot
    @Test
    public void testReport() {
        CoreMetrics.today();
        Map<String, Object> data = new HashMap<>();
        data.put("title", "aaa");

        List<TreeResultBean> reports = reportDemo.getDemoReports();
        for (TreeResultBean group : reports) {
            for (TreeResultBean report : group.getChildren()) {
                String html = reportDemo.renderHtml(report.getValue(), data);
                outputText(report.getValue() + ".html", html);
            }
        }
    }


//    @EnableSnapshot
//    @TestCell
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
    public void testDynamicSheetAndDynamicCol() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/17-动态Sheet和动态列.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        FileHelper.writeText(getTargetFile(reportName + ".html"), html, null);

        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
        File file = (File) result.getContent();
        FileHelper.copyFile(file, getTargetFile("test-dynamic-sheet-and-col.xlsx"));
        file.delete();
    }

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

        String html2 = reportDemo.renderWithXmlModel(reportName, null);
        System.out.println(html2);
        // assertEquals(html, html2);

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

    @EnableSnapshot
    @Test
    public void testQrcode() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/11-打印条码和二维码.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);

        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
        File file = (File) result.getContent();
        FileHelper.copyFile(file, getTargetFile("test-qrcode.xlsx"));
        file.delete();
    }

    @EnableSnapshot
    @Test
    public void testSiblingExpand() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/15-兄弟节点同时展开.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);

        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
        File file = (File) result.getContent();
        FileHelper.copyFile(file, getTargetFile("test-sibling-expand.xlsx"));
        file.delete();
    }

    @EnableSnapshot
    @Test
    public void testMultiDsExpand() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/16-多个数据源展开.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        outputText(reportName + ".html", html);

        WebContentBean result = reportDemo.download(reportName, XptConstants.RENDER_TYPE_XLSX);
        File file = (File) result.getContent();
        FileHelper.copyFile(file, getTargetFile("test-multi-ds-expand.xlsx"));
        file.delete();
    }
}