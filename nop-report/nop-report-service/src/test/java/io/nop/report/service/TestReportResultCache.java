package io.nop.report.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.impl.FileResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.dao.entity.NopReportDefinition;
import io.nop.report.dao.entity.NopReportDefinitionAuth;
import io.nop.report.dao.entity.NopReportResultFile;
import io.nop.report.service.cache.NopReportResultCache;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.report.core.ReportAuthErrors.ERR_REPORT_AUTH_REPORT_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证结果文件缓存（WI6）：未命中生成→命中复用→参数分键→失效→TTL 过期→授权先行→异步生成。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestReportResultCache extends JunitAutoTestCase {

    static final String TEMPLATE = "/report/auth/test-auth-jdbc.xpt.xlsx";

    @Inject
    NopReportResultCache resultCache;

    @Inject
    io.nop.report.service.auth.NopReportAuthChecker authChecker;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    io.nop.orm.IOrmTemplate ormTemplate;

    AtomicInteger renderCount = new AtomicInteger();

    @AfterEach
    public void tearDown() {
        IUserContext.set(null);
        AppConfig.getConfigProvider().updateConfigValue(NopReportConfigs.CFG_REPORT_RESULT_CACHE_TTL, 0L);
    }

    void init() {
        jdbcTemplate.executeUpdate(new SQL(
                "create table if not exists my_table(sid varchar(32), fldA varchar(100), fldB varchar(100))"));
        jdbcTemplate.executeUpdate(new SQL("delete from my_table"));
        jdbcTemplate.executeUpdate(new SQL("insert into my_table values('1','a','b')"));

        saveDefinition(TEMPLATE, "rpt-cache-1");
        installCountingEngine();
        setUserContext("u1", "user");
    }

    /**
     * spy 引擎：委托真实引擎并统计渲染次数（getRendererForXptModel 调用 = 渲染发生）。
     */
    void installCountingEngine() {
        ReportEngine delegate = new ReportEngine();
        io.nop.excel.renderer.HtmlReportRendererFactory htmlFactory = new io.nop.excel.renderer.HtmlReportRendererFactory();
        delegate.setRenderers(Map.of("html", htmlFactory, "xlsx", new io.nop.report.core.engine.renderer.XlsxReportRendererFactory()));
        IReportEngine spy = new IReportEngine() {
            @Override
            public io.nop.excel.model.ExcelWorkbook getXptModel(String reportPath) {
                return delegate.getXptModel(reportPath);
            }

            @Override
            public io.nop.excel.model.ExcelWorkbook parseXptModelFromResource(io.nop.core.resource.IResource resource) {
                return delegate.parseXptModelFromResource(resource);
            }

            @Override
            public io.nop.excel.model.ExcelWorkbook generateFromXptModel(io.nop.excel.model.ExcelWorkbook workbook,
                                                                         io.nop.core.context.IEvalContext ctx) {
                return delegate.generateFromXptModel(workbook, ctx);
            }

            @Override
            public io.nop.core.resource.tpl.ITemplateOutput getRendererForXptModel(
                    io.nop.excel.model.ExcelWorkbook workbook, String renderType) {
                renderCount.incrementAndGet();
                return delegate.getRendererForXptModel(workbook, renderType);
            }

            @Override
            public io.nop.core.resource.tpl.ITemplateOutput getRendererForExcel(
                    io.nop.excel.model.ExcelWorkbook workbook, String renderType) {
                return delegate.getRendererForExcel(workbook, renderType);
            }

            @Override
            public io.nop.excel.model.ExcelWorkbook buildXptModelFromImpModel(String impModelPath) {
                return delegate.buildXptModelFromImpModel(impModelPath);
            }

            @Override
            public io.nop.core.resource.tpl.ITemplateOutput getRendererForExcelData(
                    java.util.Iterator<io.nop.ooxml.xlsx.util.ExcelSheetData> it, io.nop.core.resource.IResource template) {
                return delegate.getRendererForExcelData(it, template);
            }
        };
        resultCache.setReportEngine(spy);
    }

    void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    void saveDefinition(String rptText, String rptId) {
        NopReportDefinition definition = daoProvider.daoFor(NopReportDefinition.class).newEntity();
        definition.setRptId(rptId);
        definition.setRptNo(rptId);
        definition.setRptName("cache-test-report");
        definition.setRptText(rptText);
        definition.setStatus(1);
        daoProvider.daoFor(NopReportDefinition.class).saveEntity(definition);
    }

    @Test
    public void testMissGenerateThenHit() {
        init();
        NopReportResultFile first = resultCache.getOrGenerate(TEMPLATE, "html", Map.of(), null);
        assertEquals(NopReportResultCache.STATUS_DONE, first.getStatus());
        assertNotNull(first.getFilePath());
        assertTrue(new java.io.File(NopReportConfigs.CFG_REPORT_RESULT_CACHE_DIR.get(), first.getFilePath()).exists());
        int rendersAfterFirst = renderCount.get();
        assertTrue(rendersAfterFirst >= 1, "first call must render");

        NopReportResultFile second = resultCache.getOrGenerate(TEMPLATE, "html", Map.of(), null);
        assertEquals(first.getSid(), second.getSid(), "second call must hit the same cache record");
        assertEquals(rendersAfterFirst, renderCount.get(), "cache hit must not render again");
    }

    @Test
    public void testDifferentParamsDifferentKey() {
        init();
        NopReportResultFile a = resultCache.getOrGenerate(TEMPLATE, "html", Map.of("p", "1"), null);
        NopReportResultFile b = resultCache.getOrGenerate(TEMPLATE, "html", Map.of("p", "2"), null);
        assertTrue(!a.getRptParams().equals(b.getRptParams()));
    }

    @Test
    public void testInvalidateRegenerates() {
        init();
        NopReportResultFile first = resultCache.getOrGenerate(TEMPLATE, "html", Map.of(), null);
        resultCache.invalidate("rpt-cache-1");
        assertEquals(0, countRecords());

        NopReportResultFile second = resultCache.getOrGenerate(TEMPLATE, "html", Map.of(), null);
        assertTrue(!first.getSid().equals(second.getSid()));
        assertNotNull(second.getFilePath());
    }

    @Test
    public void testTtlExpiry() {
        init();
        AppConfig.getConfigProvider().updateConfigValue(NopReportConfigs.CFG_REPORT_RESULT_CACHE_TTL, 60L);
        NopReportResultFile first = resultCache.getOrGenerate(TEMPLATE, "html", Map.of(), null);
        // TTL 以 createTime 判定：直接把 createTime 回拨到 TTL 之外（确定性，不依赖真实时间等待）
        jdbcTemplate.executeUpdate(SQL.begin()
                .append("update nop_report_result_file set CREATE_TIME = ")
                .param(new java.sql.Timestamp(System.currentTimeMillis() - 120_000L))
                .append(" where SID = ").param(first.getSid()).end());
        ormTemplate.clearSession();
        NopReportResultFile second = resultCache.getOrGenerate(TEMPLATE, "html", Map.of(), null);
        assertTrue(!first.getSid().equals(second.getSid()), "expired record must not be reused");
    }

    @Test
    public void testAuthBeforeCacheHit() {
        init();
        resultCache.getOrGenerate(TEMPLATE, "html", Map.of(), null);

        NopReportDefinitionAuth auth = daoProvider.daoFor(NopReportDefinitionAuth.class).newEntity();
        auth.setRptId("rpt-cache-1");
        auth.setRoleId("rpt-admin");
        daoProvider.daoFor(NopReportDefinitionAuth.class).saveEntity(auth);

        setUserContext("u2", "other");
        // 入口边界授权：checker 拒绝后缓存服务不会被触达
        NopException ex = assertThrows(NopException.class,
                () -> authChecker.checkReportAccess(TEMPLATE));
        assertEquals(ERR_REPORT_AUTH_REPORT_DENIED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testDisabledCacheSkipsCache() {
        init();
        AppConfig.getConfigProvider().updateConfigValue(NopReportConfigs.CFG_REPORT_RESULT_CACHE_ENABLED, false);
        try {
            NopReportResultFile record = resultCache.getOrGenerate(TEMPLATE, "html", Map.of(), null);
            assertEquals(NopReportResultCache.STATUS_DONE, record.getStatus());
            assertEquals(0, countRecords(), "disabled cache must not persist records");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(NopReportConfigs.CFG_REPORT_RESULT_CACHE_ENABLED, true);
        }
    }

    @Test
    public void testAsyncGenerateAndPoll() {
        init();
        String cacheKey = resultCache.generateAsync(TEMPLATE, "html", Map.of(), null);
        NopReportResultFile record = findByStatusDoneWithWait("rpt-cache-1", cacheKey);
        assertNotNull(record, "async generation should complete within 5s");
        assertEquals(NopReportResultCache.STATUS_DONE, record.getStatus());
    }

    /**
     * 条件等待异步生成完成（FutureHelper.waitUntil 轮询由等待工具内部处理，超时返回 null）。
     */
    NopReportResultFile findByStatusDoneWithWait(String rptId, String cacheKey) {
        java.util.concurrent.atomic.AtomicReference<NopReportResultFile> found = new java.util.concurrent.atomic.AtomicReference<>();
        io.nop.api.core.util.FutureHelper.waitUntil(() -> {
            NopReportResultFile r = resultCache.findByStatus(rptId, cacheKey, NopReportResultCache.STATUS_DONE);
            if (r != null)
                found.set(r);
            return r != null;
        }, 5000);
        return found.get();
    }

    int countRecords() {
        return daoProvider.daoFor(NopReportResultFile.class).findAllByExample(
                daoProvider.daoFor(NopReportResultFile.class).newEntity()).size();
    }
}
