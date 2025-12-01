/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.build.XptModelLoader;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.IReportRendererFactory;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.core.engine.renderer.HtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TestReportFile extends BaseTestCase {

    @BeforeAll
    public static void init() {
        // 初始化Nop平台，全局只需要调用一次
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGenFile() {
        IReportEngine reportEngine = newReportEngine();
        File xptFile = attachmentFile("test.xpt.xlsx");
        ExcelWorkbook xptModel = new XptModelLoader().loadObjectFromResource(new FileResource(xptFile));
        ITemplateOutput output = reportEngine.getRendererForXptModel(xptModel, "xlsx");

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("title", "测试报表，标题显示在右上角");

        File outputFile = getTargetFile("output.xlsx");
        output.generateToFile(outputFile, scope);
    }

    @Test
    public void testReportResource() {
        // 如果从_vfs所对应的虚拟文件目录下加载报表文件，则可以直接调用reportEngine上的方法

        IReportEngine reportEngine = newReportEngine();
        ITemplateOutput output = reportEngine.getRenderer("/nop/report/demo/test.xpt.xlsx", "xlsx");

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("title", "测试报表，标题显示在右上角");

        File outputFile = getTargetFile("output2.xlsx");
        output.generateToFile(outputFile, scope);
    }

    IReportEngine newReportEngine() {
        ReportEngine reportEngine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        reportEngine.setRenderers(renderers);
        return reportEngine;
    }
}
