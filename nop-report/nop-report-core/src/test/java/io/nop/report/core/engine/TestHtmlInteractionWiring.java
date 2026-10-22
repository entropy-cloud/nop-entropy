/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.HtmlReportRendererFactory;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.report.core.XptConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 交互接线验证：linkExpr 求值 → ExpandedCell.linkUrl → html `<a href>`；
 * editorId/viewerId → data-editor-id/data-viewer-id；响应式 CSS 与交互脚本输出。
 */
public class TestHtmlInteractionWiring {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private String render(String path) {
        ReportEngine engine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        engine.setRenderers(renderers);

        ExcelWorkbook wb = engine.getXptModel(path);
        assertNotNull(wb);
        ITextTemplateOutput output = (ITextTemplateOutput) engine.getRendererForXptModel(wb, "html");
        return output.generateText(new io.nop.xlang.api.XLang().newEvalScope());
    }

    @Test
    public void testLinkExprRenderedAsAnchor() {
        String html = render("/nop/report/test/interact.xpt.xml");
        assertTrue(html.contains("<a href=\"/detail?id=1\""), html);
        assertEquals("/detail?id=1", firstHref(html));
    }

    @Test
    public void testEditorViewerIdAttrs() {
        String html = render("/nop/report/test/interact.xpt.xml");
        assertTrue(html.contains("data-editor-id=\"edName\""), html);
        assertTrue(html.contains("data-viewer-id=\"vwName\""), html);
    }

    @Test
    public void testInteractiveScriptAndResponsiveCss() {
        String html = render("/nop/report/test/interact.xpt.xml");
        assertTrue(html.contains("__xptInteractive"), html);
        assertTrue(html.contains("overflow-x:auto"), html);
        assertTrue(html.contains("(max-width:768px)"), html);
    }

    private String firstHref(String html) {
        int i = html.indexOf("href=\"");
        int end = html.indexOf('"', i + 6);
        return html.substring(i + 6, end);
    }
}
