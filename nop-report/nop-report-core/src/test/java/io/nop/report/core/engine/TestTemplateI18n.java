/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.HtmlReportRendererFactory;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.report.core.engine.ReportEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11：模板文本 i18n（@i18n: 平台约定 + xptRt.i18n/i18nVar）。
 */
public class TestTemplateI18n {

    @BeforeAll
    public static void init() {
        if (CoreInitialization.isInitialized())
            CoreInitialization.destroy();
        CoreInitialization.initialize();
        // 注册测试消息（registerMessages 不随 clear 清理——唯一前缀 key 避免跨用例干扰）
        io.nop.core.i18n.I18nMessageManager.instance()
                .registerMessages("zh-CN", Map.of("test.report.title", "测试报表标题"));
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private String render(String path) {
        ReportEngine engine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put("html", new HtmlReportRendererFactory());
        engine.setRenderers(renderers);

        ExcelWorkbook wb = engine.getXptModel(path);
        ITextTemplateOutput output = (ITextTemplateOutput) engine.getRendererForXptModel(wb, "html");
        return output.generateText(io.nop.xlang.api.XLang.newEvalScope());
    }

    @Test
    public void testI18nTextResolved() {
        String html = render("/nop/report/test/i18n.xpt.xml");
        assertTrue(html.contains("测试报表标题"), html);
        assertTrue(!html.contains("@i18n:"), html);
        // 非 i18n 文本不回归
        assertTrue(html.contains("静态文本"), html);
    }

    @Test
    public void testI18nHelperFallback() {
        XptRuntime xptRt = new XptRuntime(io.nop.xlang.api.XLang.newEvalScope());
        // 缺失 key 回退 default
        assertEquals("fallback", xptRt.i18n("test.report.not-exist", "fallback"));
        assertEquals("测试报表标题", xptRt.i18n("test.report.title", "fallback"));
    }
}
