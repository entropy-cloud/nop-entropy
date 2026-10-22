/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.params;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.XptParamModel;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.report.core.ReportParamErrors;
import io.nop.excel.renderer.HtmlReportRendererFactory;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReportParamResolver {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private XptWorkbookModel modelWithParams(XptParamModel... params) {
        XptWorkbookModel model = new XptWorkbookModel();
        for (XptParamModel param : params)
            model.addParam(param);
        return model;
    }

    private XptParamModel param(String name, String type, boolean required, String defaultValue) {
        XptParamModel param = new XptParamModel();
        param.setName(name);
        if (type != null)
            param.setType(io.nop.commons.type.StdDataType.valueOf(type));
        param.setRequired(required);
        param.setDefaultValue(defaultValue);
        return param;
    }

    @Test
    public void testMissingRequiredFails() {
        XptWorkbookModel model = modelWithParams(param("pname", "STRING", true, null));
        IEvalScope scope = XLang.newEvalScope();

        NopException ex = assertThrows(NopException.class,
                () -> ReportParamResolver.resolveAndInject(model, scope));
        assertEquals(ReportParamErrors.ERR_REPORT_PARAM_MISSING_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("pname", ex.getParam(ReportParamErrors.ARG_PARAM_NAME));
    }

    @Test
    public void testDefaultValueFilled() {
        XptWorkbookModel model = modelWithParams(param("count", "INT", false, "3"));
        IEvalScope scope = XLang.newEvalScope();

        ReportParamResolver.resolveAndInject(model, scope);
        assertEquals(3, ((Map<?, ?>) scope.getValue("param")).get("count"));
    }

    @Test
    public void testTypeConversion() {
        XptWorkbookModel model = modelWithParams(
                param("count", "INT", false, null),
                param("flag", "BOOLEAN", false, null));
        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> input = new HashMap<>();
        input.put("count", "5");
        input.put("flag", "true");
        scope.setLocalValue(null, "param", input);

        ReportParamResolver.resolveAndInject(model, scope);
        Map<?, ?> resolved = (Map<?, ?>) scope.getValue("param");
        assertEquals(5, resolved.get("count"));
        assertEquals(true, resolved.get("flag"));
    }

    @Test
    public void testTypeConversionError() {
        XptWorkbookModel model = modelWithParams(param("count", "INT", false, null));
        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> input = new HashMap<>();
        input.put("count", "abc");
        scope.setLocalValue(null, "param", input);

        NopException ex = assertThrows(NopException.class,
                () -> ReportParamResolver.resolveAndInject(model, scope));
        assertEquals(ReportParamErrors.ERR_REPORT_PARAM_TYPE_ERROR.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testUnknownInputIgnoredAndPreserved() {
        XptWorkbookModel model = modelWithParams(param("pname", "STRING", false, null));
        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> input = new HashMap<>();
        input.put("pname", "v1");
        input.put("extra", "keep");
        scope.setLocalValue(null, "param", input);

        ReportParamResolver.resolveAndInject(model, scope);
        Map<?, ?> resolved = (Map<?, ?>) scope.getValue("param");
        assertEquals("v1", resolved.get("pname"));
        assertEquals("keep", resolved.get("extra"));
    }

    @Test
    public void testNullForUnsetOptional() {
        XptWorkbookModel model = modelWithParams(param("pname", "STRING", false, null));
        IEvalScope scope = XLang.newEvalScope();

        ReportParamResolver.resolveAndInject(model, scope);
        assertNull(((Map<?, ?>) scope.getValue("param")).get("pname"));
    }

    private IReportEngine newEngine() {
        ReportEngine engine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        engine.setRenderers(renderers);
        return engine;
    }

    @Test
    public void testParamE2EInTemplateExpand() {
        IReportEngine engine = newEngine();
        ITextTemplateOutput renderer = (ITextTemplateOutput) engine.getRenderer("/nop/report/test/param-model.xpt.xml", "html");
        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> input = new HashMap<>();
        input.put("pname", "报表参数值");
        scope.setLocalValue(null, "param", input);

        String html = renderer.generateText(scope);
        assertTrue(html.contains("报表参数值"), "html should contain param value: " + html);
        assertTrue(html.contains("6"), "count default 3 * 2 = 6 expected: " + html);
    }

    @Test
    public void testParamE2EMissingRequired() {
        IReportEngine engine = newEngine();
        ITextTemplateOutput renderer = (ITextTemplateOutput) engine.getRenderer("/nop/report/test/param-model.xpt.xml", "html");
        IEvalScope scope = XLang.newEvalScope();

        NopException ex = assertThrows(NopException.class, () -> renderer.generateText(scope));
        assertTrue(StringHelper.isNotEmpty(ex.getErrorCode()));
        assertEquals(ReportParamErrors.ERR_REPORT_PARAM_MISSING_REQUIRED.getErrorCode(), ex.getErrorCode());
    }
}
