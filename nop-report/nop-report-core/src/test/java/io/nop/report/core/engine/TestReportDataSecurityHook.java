/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.type.StdDataType;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.excel.model.XptParamModel;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.xlang.api.XLang;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReportDataSecurityHook {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    static class FakeProvider implements IReportDataSecurityProvider {
        RowCondition condition;
        String lastDsName;
        String lastReportPath;
        Object maskResult;
        String lastMaskDs;
        String lastMaskField;
        Object lastMaskValue;

        @Override
        public RowCondition getRowCondition(String reportPath, String dsName) {
            this.lastReportPath = reportPath;
            this.lastDsName = dsName;
            return condition;
        }

        @Override
        public Object maskValue(String dsName, String field, Object value) {
            this.lastMaskDs = dsName;
            this.lastMaskField = field;
            this.lastMaskValue = value;
            return maskResult != null ? maskResult : value;
        }
    }

    FakeProvider provider;
    IBeanContainer original;

    void install(FakeProvider p) {
        provider = p;
        try {
            original = BeanContainer.instance();
        } catch (Exception e) {
            original = null;
        }
        BeanContainer.registerInstance(new SingleBeanContainer(p));
    }

    @AfterEach
    public void restore() {
        if (original != null)
            BeanContainer.registerInstance(original);
    }

    @Test
    public void testNoProviderReturnsOriginalSql() {
        IXptRuntime xptRt = newXptRuntime();
        SQL sql = new SQL("select * from t");
        assertSame(sql, xptRt.applyRowCondition(sql, "ds1"));
    }

    @Test
    public void testRowConditionWrapsSqlAndKeepsAttrs() {
        FakeProvider p = new FakeProvider();
        p.condition = new IReportDataSecurityProvider.RowCondition("org_id = ?", Arrays.asList("o1"));
        install(p);

        SQL sql = new SQL("select sid, name from t where status = 1");
        SQL wrapped = newXptRuntime().applyRowCondition(sql, "ds1");

        assertTrue(wrapped.getText().contains("select * from ("), wrapped.getText());
        assertTrue(wrapped.getText().contains(") _rpt_sec where org_id = ?"), wrapped.getText());
        assertEquals(Arrays.asList("o1"), wrapped.getParams());
        assertEquals(-1, wrapped.getTimeout());
    }

    @Test
    public void testNoConditionReturnsOriginal() {
        FakeProvider p = new FakeProvider();
        install(p);

        SQL sql = new SQL("select * from t");
        assertSame(sql, newXptRuntime().applyRowCondition(sql, "ds1"));
        assertEquals("ds1", p.lastDsName);
    }

    @Test
    public void testReportPathPassedToProvider() {
        FakeProvider p = new FakeProvider();
        install(p);

        XptRuntime xptRt = newXptRuntime();
        io.nop.excel.model.ExcelWorkbook wb = new io.nop.excel.model.ExcelWorkbook();
        wb.setLocation(io.nop.api.core.util.SourceLocation.fromPath("/nop/report/test/param-model.xpt.xml"));
        xptRt.setWorkbook(wb);
        xptRt.applyRowCondition(new SQL("select 1"), "ds1");
        assertEquals("/nop/report/test/param-model.xpt.xml", p.lastReportPath);
    }

    @Test
    public void testMaskValueForwarded() {
        FakeProvider p = new FakeProvider();
        p.maskResult = "138****5678";
        install(p);

        Object masked = newXptRuntime().maskValue("ds1", "phone", "13812345678");
        assertEquals("138****5678", masked);
        assertEquals("ds1", p.lastMaskDs);
        assertEquals("phone", p.lastMaskField);
        assertEquals("13812345678", p.lastMaskValue);
    }

    private XptRuntime newXptRuntime() {
        IEvalScope scope = XLang.newEvalScope();
        return new XptRuntime(scope);
    }

    static class SingleBeanContainer implements IBeanContainer {
        final FakeProvider checker;

        SingleBeanContainer(FakeProvider checker) {
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
            return IReportDataSecurityProvider.BEAN_NAME.equals(name);
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
            return containsBean(name) ? FakeProvider.class : null;
        }

        @Override
        public String findAutowireCandidate(Class<?> clazz) {
            return null;
        }
    }
}
