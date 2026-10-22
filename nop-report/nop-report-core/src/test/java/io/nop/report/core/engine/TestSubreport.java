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
import io.nop.report.core.XptErrors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9：子报表引用（html 嵌套 + 参数传递 + 递归 fail-fast + 引擎缺失 fail-fast）。
 */
public class TestSubreport {

    private ReportEngine newEngine() {
        ReportEngine engine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put("html", new HtmlReportRendererFactory());
        engine.setRenderers(renderers);
        // 模拟平台部署：注册引擎 bean（子报表渲染依赖）
        io.nop.api.core.ioc.BeanContainer.registerInstance(
                new io.nop.api.core.ioc.IBeanContainer() {
                    @Override
                    public String getId() {
                        return "engine-container";
                    }

                    @Override
                    public void start() {
                    }

                    @Override
                    public void stop() {
                    }

                    @Override
                    public void restart() {
                    }

                    @Override
                    public boolean isRunning() {
                        return true;
                    }

                    @Override
                    public boolean containsBean(String name) {
                        return "nopReportEngine".equals(name);
                    }

                    @Override
                    public Object getBean(String name) {
                        return engine;
                    }

                    @Override
                    public boolean containsBeanType(Class<?> clazz) {
                        return false;
                    }

                    @Override
                    public <T> T getBeanByType(Class<T> clazz) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public <T> T tryGetBeanByType(Class<T> clazz) {
                        return null;
                    }

                    @Override
                    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> getBeansWithAnnotation(Class<? extends java.lang.annotation.Annotation> annClass) {
                        return Map.of();
                    }

                    @Override
                    public String getBeanScope(String name) {
                        return "singleton";
                    }

                    @Override
                    public Class<?> getBeanClass(String name) {
                        return containsBean(name) ? ReportEngine.class : null;
                    }

                    @Override
                    public String findAutowireCandidate(Class<?> clazz) {
                        return null;
                    }
                });
        return engine;
    }

    private void unregisterEngine() {
        io.nop.api.core.ioc.BeanContainer.registerInstance(new EmptyBeanContainer());
    }

    private String render(String path) {
        return render(path, true);
    }

    private String render(String path, boolean register) {
        ReportEngine engine = newEngine();
        if (!register)
            unregisterEngine();
        ExcelWorkbook wb = engine.getXptModel(path);
        ITextTemplateOutput output = (ITextTemplateOutput) engine.getRendererForXptModel(wb, "html");
        return output.generateText(io.nop.xlang.api.XLang.newEvalScope());
    }

    @Test
    public void testSubreportNestedWithParams() {
        if (CoreInitialization.isInitialized())
            CoreInitialization.destroy();
        CoreInitialization.initialize();
        String html = render("/nop/report/test/subreport-main.xpt.xml");
        assertTrue(html.contains("子报表:上海"), html);
    }

    @Test
    public void testCycleDepthFailFast() {
        if (CoreInitialization.isInitialized())
            CoreInitialization.destroy();
        CoreInitialization.initialize();
        NopException ex = assertThrows(NopException.class,
                () -> render("/nop/report/test/subreport-cycle.xpt.xml"));
        assertEquals(XptErrors.ERR_XPT_SUBREPORT_DEPTH_EXCEEDED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testEngineMissingFailFast() {
        if (CoreInitialization.isInitialized())
            CoreInitialization.destroy();
        CoreInitialization.initialize();
        unregisterEngine();
        NopException ex = assertThrows(NopException.class,
                () -> render("/nop/report/test/subreport-main.xpt.xml", false));
        assertEquals(XptErrors.ERR_XPT_SUBREPORT_ENGINE_UNAVAILABLE.getErrorCode(), ex.getErrorCode());
    }
}
