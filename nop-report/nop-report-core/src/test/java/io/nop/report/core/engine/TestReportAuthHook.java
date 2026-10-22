/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.xlang.api.XLang;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.report.core.ReportAuthErrors.ARG_DS_NAME;
import static io.nop.report.core.ReportAuthErrors.ARG_REPORT_PATH;
import static io.nop.report.core.ReportAuthErrors.ERR_REPORT_AUTH_DATASOURCE_DENIED;
import static io.nop.report.core.ReportAuthErrors.ERR_REPORT_AUTH_REPORT_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证授权检查挂点：getXptModel 渲染入口、IXptRuntime.checkDataSourceAccess 取数标签入口。
 * xpt-rt 标签脚本的端到端授权测试在 nop-report-service 测试基座覆盖（core classpath 无 dao）。
 */
public class TestReportAuthHook {

    static boolean initialized = false;

    @BeforeAll
    public static void init() {
        if (!CoreInitialization.isInitialized()) {
            CoreInitialization.initialize();
            initialized = true;
        }
    }

    @AfterAll
    public static void destroy() {
        if (initialized)
            CoreInitialization.destroy();
    }

    IBeanContainer originalContainer;

    void installChecker(RecordChecker checker) {
        try {
            originalContainer = BeanContainer.instance();
        } catch (Exception e) {
            originalContainer = null;
        }
        BeanContainer.registerInstance(new SingleBeanContainer(checker));
    }

    @AfterEach
    public void restoreContainer() {
        if (originalContainer != null)
            BeanContainer.registerInstance(originalContainer);
        originalContainer = null;
    }

    @Test
    public void testNoCheckerAllowsRender() {
        ReportEngine engine = newEngine();
        ExcelWorkbook wb = engine.getXptModel("/nop/report/demo/test-range-formula.xpt.xlsx");
        assertNotNull(wb);
    }

    @Test
    public void testCheckerInvokedOnGetXptModel() {
        RecordChecker checker = new RecordChecker(false);
        installChecker(checker);

        ReportEngine engine = newEngine();
        ExcelWorkbook wb = engine.getXptModel("/nop/report/demo/test-range-formula.xpt.xlsx");
        assertNotNull(wb);
        assertEquals(List.of("/nop/report/demo/test-range-formula.xpt.xlsx"), checker.reportChecks);
    }

    @Test
    public void testCheckerDenyReport() {
        RecordChecker checker = new RecordChecker(true);
        installChecker(checker);

        ReportEngine engine = newEngine();
        NopException ex = assertThrows(NopException.class,
                () -> engine.getXptModel("/nop/report/demo/test-range-formula.xpt.xlsx"));
        assertEquals(ERR_REPORT_AUTH_REPORT_DENIED.getErrorCode(), ex.getErrorCode());
        assertEquals("/nop/report/demo/test-range-formula.xpt.xlsx", ex.getParam(ARG_REPORT_PATH));
    }

    @Test
    public void testCheckDataSourceAccessAllowed() {
        RecordChecker checker = new RecordChecker(false);
        installChecker(checker);

        IXptRuntime xptRt = newXptRuntime();
        xptRt.checkDataSourceAccess("myDs");
        assertEquals(List.of("myDs"), checker.dsChecks);
    }

    @Test
    public void testCheckDataSourceAccessDeny() {
        RecordChecker checker = new RecordChecker(true);
        installChecker(checker);

        IXptRuntime xptRt = newXptRuntime();
        NopException ex = assertThrows(NopException.class, () -> xptRt.checkDataSourceAccess("myDs"));
        assertEquals(ERR_REPORT_AUTH_DATASOURCE_DENIED.getErrorCode(), ex.getErrorCode());
    }

    private IXptRuntime newXptRuntime() {
        IEvalScope scope = XLang.newEvalScope();
        return new XptRuntime(scope);
    }

    private ReportEngine newEngine() {
        ReportEngine engine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        engine.setRenderers(renderers);
        return engine;
    }

    static class RecordChecker implements IReportAuthChecker {
        final boolean deny;
        final List<String> reportChecks = new ArrayList<>();
        final List<String> dsChecks = new ArrayList<>();

        RecordChecker(boolean deny) {
            this.deny = deny;
        }

        @Override
        public void checkReportAccess(String reportPath) {
            reportChecks.add(reportPath);
            if (deny)
                throw new NopException(ERR_REPORT_AUTH_REPORT_DENIED).param(ARG_REPORT_PATH, reportPath);
        }

        @Override
        public void checkDataSourceAccess(String querySpace) {
            dsChecks.add(querySpace);
            if (deny)
                throw new NopException(ERR_REPORT_AUTH_DATASOURCE_DENIED).param(ARG_DS_NAME, querySpace);
        }
    }

    static class SingleBeanContainer implements IBeanContainer {
        final RecordChecker checker;

        SingleBeanContainer(RecordChecker checker) {
            this.checker = checker;
        }

        @Override
        public String getId() {
            return "test";
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
            return IReportAuthChecker.BEAN_NAME.equals(name);
        }

        @Nonnull
        @Override
        public Object getBean(String name) {
            assertTrue(containsBean(name));
            return checker;
        }

        @Override
        public boolean containsBeanType(Class<?> clazz) {
            return false;
        }

        @Nonnull
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
        public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
            return Map.of();
        }

        @Override
        public String getBeanScope(String name) {
            return "singleton";
        }

        @Override
        public Class<?> getBeanClass(String name) {
            return containsBean(name) ? RecordChecker.class : null;
        }

        @Override
        public String findAutowireCandidate(Class<?> clazz) {
            return null;
        }
    }
}
