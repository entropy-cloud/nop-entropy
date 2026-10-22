/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.HtmlReportRendererFactory;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.report.core.NopReportCoreConfigs;
import io.nop.report.core.XptErrors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7：全局展开行数上限防线（fail-fast 防 OOM）；模板级 expandMaxCount 双防线并存。
 */
public class TestMaxExpandRows {

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

    @AfterEach
    public void resetConfig() {
        AppConfig.getConfigProvider().updateConfigValue(NopReportCoreConfigs.CFG_MAX_EXPAND_ROWS, 100_000);
    }

    private String render(int listSize) {
        return renderAt("/nop/report/test/max-rows.xpt.xml", listSize, null);
    }

    private String renderAt(String path, int listSize, Integer maxRows) {
        ReportEngine engine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put("html", new HtmlReportRendererFactory());
        engine.setRenderers(renderers);

        ExcelWorkbook wb = engine.getXptModel(path);
        ITextTemplateOutput output = (ITextTemplateOutput) engine.getRendererForXptModel(wb, "html");
        io.nop.core.lang.eval.IEvalScope scope = io.nop.xlang.api.XLang.newEvalScope();
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (int i = 0; i < listSize; i++)
            list.add(Map.of("name", "row" + i));
        scope.setLocalValue(null, "list", list);
        if (maxRows != null)
            AppConfig.getConfigProvider().updateConfigValue(NopReportCoreConfigs.CFG_MAX_EXPAND_ROWS, maxRows);
        return output.generateText(scope);
    }

    @Test
    public void testTemplateExpandMaxCountCoexists() {
        // 模板级 expandMaxCount=2（LimitIterator 快败）+ 全局上限默认 10 万：两道防线独立生效
        org.opentest4j.AssertionFailedError wait = null;
        try {
            renderWithTemplate("/nop/report/test/max-rows-template-limit.xpt.xml", 6);
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.assertNotNull(e);
            return;
        }
        throw new org.opentest4j.AssertionFailedError("template expandMaxCount must enforce its own limit");
    }

    @Test
    public void testReuseRowsNotCounted() {
        // 复用行（makeRow 命中已有行）不触发 insertEmptyRow 检查——仅新增插入行计数
        io.nop.report.core.model.ExpandedTable table = new io.nop.report.core.model.ExpandedTable(2, 1);
        AppConfig.getConfigProvider().updateConfigValue(NopReportCoreConfigs.CFG_MAX_EXPAND_ROWS, 2);
        try {
            table.makeRow(1); // 复用已有行，不应抛错
            NopException ex = assertThrows(NopException.class, () -> table.makeRow(2)); // 新增插入触发检查
            assertEquals(XptErrors.ERR_XPT_MAX_EXPAND_ROWS_EXCEEDED.getErrorCode(), ex.getErrorCode());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(NopReportCoreConfigs.CFG_MAX_EXPAND_ROWS, 100_000);
        }
    }

    private String renderWithTemplate(String path, int listSize) {
        return renderAt(path, listSize, null);
    }

    @Test
    public void testUnderLimitRenders() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(NopReportCoreConfigs.CFG_MAX_EXPAND_ROWS, 100);
        String html = render(6);
        try (java.io.PrintWriter pw = new java.io.PrintWriter("target/under-limit.html")) { pw.print(html); }
        assertTrue(html.contains("row5"), html);
    }

    @Test
    public void testOverLimitFailFast() {
        AppConfig.getConfigProvider().updateConfigValue(NopReportCoreConfigs.CFG_MAX_EXPAND_ROWS, 3);
        NopException ex = assertThrows(NopException.class, () -> render(6));
        assertEquals(XptErrors.ERR_XPT_MAX_EXPAND_ROWS_EXCEEDED.getErrorCode(), ex.getErrorCode());
        assertEquals(3, ex.getParam("maxRows"));
    }
}
