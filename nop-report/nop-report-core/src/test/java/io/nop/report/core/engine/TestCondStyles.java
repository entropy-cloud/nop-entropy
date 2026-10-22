/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.HtmlReportRendererFactory;
import io.nop.excel.renderer.IReportRendererFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8：条件格式规则集（expr 谓词/数值区间，首中生效，覆盖 styleIdExpr）。
 */
public class TestCondStyles {

    @BeforeAll
    public static void init() {
        if (CoreInitialization.isInitialized())
            CoreInitialization.destroy();
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private String render(java.util.function.Consumer<io.nop.core.lang.eval.IEvalScope> scopeInit, String path) {
        {
            ReportEngine engine = new ReportEngine();
            Map<String, IReportRendererFactory> renderers = new HashMap<>();
            renderers.put("html", new HtmlReportRendererFactory());
            engine.setRenderers(renderers);

            ExcelWorkbook wb = engine.getXptModel(path);
            ITextTemplateOutput output = (ITextTemplateOutput) engine.getRendererForXptModel(wb, "html");
            io.nop.core.lang.eval.IEvalScope scope = io.nop.xlang.api.XLang.newEvalScope();
            List<Map<String, Object>> list = new ArrayList<>();
            list.add(Map.of("amount", 95));
            list.add(Map.of("amount", 70));
            list.add(Map.of("amount", 10));
            scope.setLocalValue(null, "list", list);
            if (scopeInit != null)
                scopeInit.accept(scope);
            return output.generateText(scope);
        }
    }

    @Test
    public void testRulesFirstMatchWins() {
        String html = render(null, "/nop/report/test/cond-styles.xpt.xml");
        assertTrue(html.contains("high-style"));
        assertTrue(html.contains("mid-style"));
        assertTrue(html.contains("low-style"));
        // styleIdExpr 被首中规则覆盖
        assertTrue(!html.contains("\"base-style\""), html);
    }

    @Test
    public void testNoMatchKeepsStyleIdExpr() {
        String html = render(scope -> {
            List<Map<String, Object>> list = new ArrayList<>();
            list.add(Map.of("amount", 50));
            scope.setLocalValue(null, "list", list);
        }, "/nop/report/test/cond-styles-none.xpt.xml");
        assertTrue(html.contains("base-style"), html);
    }

    @Test
    public void testExprErrorFailLoud() {
        // fail-loud：规则表达式异常向上传播（xlang 异常保留原错误码，包装异常为 cond-style-expr-error）
        assertThrows(NopException.class,
                () -> render(null, "/nop/report/test/cond-styles-error.xpt.xml"));
    }
}
