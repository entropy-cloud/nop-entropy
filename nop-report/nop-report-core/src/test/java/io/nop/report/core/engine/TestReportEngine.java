/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.report.core.XptConstants;
import io.nop.report.core.build.XptStructureToNode;
import io.nop.report.core.engine.renderer.HtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestReportEngine extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testExpand() {
        Object ormModel = DslModelHelper.loadDslModelAsJson(
                VirtualFileSystem.instance().getResource("/nop/report/orm/test.orm.xml"), false);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, XptConstants.VAR_ENTITY, ormModel);

        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = reportEngine.buildXptModelFromImpModel("/nop/report/orm/test-orm.imp.xml");
        new XptStructureToNode().buildNode(workbook).dump();

        IObjMeta objMeta = SchemaLoader.loadXMeta(XptConstants.XDSL_SCHEMA_WORKBOOK);
        objMeta.toNode().saveToResource(getTargetResource("workbook.xmeta"), null);

        XNode model = DslModelHelper.dslModelToXNode(XptConstants.XDSL_SCHEMA_WORKBOOK, workbook);
        model.dump();

        model.saveToResource(getTargetResource("test-orm.xpt.xml"), null);

        ITextTemplateOutput htmlRenderer = (ITextTemplateOutput) reportEngine.getRendererForXptModel(workbook, "html");
        String html = htmlRenderer.generateText(scope);
        System.out.println(html);

        FileHelper.writeText(getTargetFile("test-report.html"), html, null);
    }

    @Test
    public void testStyle() {

        IReportEngine reportEngine = newReportEngine();


        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromVirtualPath("/nop/report/orm/calc.xlsx");
        ITextTemplateOutput htmlRenderer = (ITextTemplateOutput) reportEngine.getRendererForExcel(workbook, "html");
        String html = htmlRenderer.generateText(XLang.newEvalScope());
        // System.out.println(html);

        FileHelper.writeText(getTargetFile("test-calc.html"), html, null);

        ITemplateOutput output = reportEngine.getRendererForExcel(workbook, "xlsx");
        output.generateToFile(getTargetFile("test-calc.xlsx"), XLang.newEvalScope());
    }

    @Test
    public void testHidden() {
        IReportEngine reportEngine = newReportEngine();


        ExcelWorkbook workbook = reportEngine.getXptModel("/nop/report/demo/test-hidden.xpt.xlsx");
        ITextTemplateOutput htmlRenderer = (ITextTemplateOutput) reportEngine.getRendererForXptModel(workbook, "html");
        String html = htmlRenderer.generateText(XLang.newEvalScope());
        // System.out.println(html);

        assertFalse(html.contains("xpt-row xpt-hidden"));

        FileHelper.writeText(getTargetFile("test-hidden.html"), html, null);

        ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, "xlsx");
        output.generateToFile(getTargetFile("test-hidden.xlsx"), XLang.newEvalScope());
    }

    private IReportEngine newReportEngine() {
        ReportEngine reportEngine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        reportEngine.setRenderers(renderers);
        return reportEngine;
    }
}