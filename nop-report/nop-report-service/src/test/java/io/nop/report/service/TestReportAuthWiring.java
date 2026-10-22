package io.nop.report.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.dao.api.IDaoProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.report.core.ReportAuthErrors;
import io.nop.report.core.engine.IReportAuthChecker;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.dao.entity.NopReportDatasource;
import io.nop.report.dao.entity.NopReportDatasourceAuth;
import io.nop.report.dao.entity.NopReportDefinition;
import io.nop.report.dao.entity.NopReportDefinitionAuth;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证报表/数据源授权接线（WI1）：
 * - 渲染入口：getXptModel → checker（报表定义 auth 记录 allow/deny）
 * - 取数标签：xpt-rt UseJdbcDataSet 脚本 → checkDataSourceAccess → checker deny
 * - 无用户上下文 fail-closed；exempt-roles 豁免
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestReportAuthWiring extends JunitAutoTestCase {

    static final String TEMPLATE = "/report/auth/test-auth-jdbc.xpt.xlsx";
    static final String TEMPLATE_DS_DENIED = "/report/auth/test-auth-jdbc-denied.xpt.xlsx";

    @Inject
    IReportEngine reportEngine;

    @Inject
    io.nop.report.service.auth.NopReportAuthChecker authChecker;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    void initMyTable() {
        jdbcTemplate.executeUpdate(new SQL("create table if not exists my_table(sid varchar(32), fldA varchar(100), fldB varchar(100))"));
        jdbcTemplate.executeUpdate(new SQL("delete from my_table"));
        jdbcTemplate.executeUpdate(new SQL("insert into my_table values('1','a','b')"));
    }

    @Test
    public void testNoAuthRecordAllowsRender() {
        initMyTable();
        setUserContext("u1", "user");
        ITextTemplateOutput renderer = (ITextTemplateOutput) reportEngine.getRenderer(TEMPLATE, "html");
        String html = renderer.generateText(XLang.newEvalScope());
        assertNotNull(html);
        assertTrue(html.contains("<table"), "rendered report should contain a table, got: " + html);
    }

    @Test
    public void testReportLevelDenyAndAllow() {
        initMyTable();
        String rptId = saveDefinition(TEMPLATE, "rpt-admin");

        // 入口边界授权（引擎不再校验）：checker 在入口 action 处拒绝
        setUserContext("u1", "user");
        NopException ex = assertThrows(NopException.class,
                () -> authChecker.checkReportAccess(TEMPLATE));
        assertEquals(ReportAuthErrors.ERR_REPORT_AUTH_REPORT_DENIED.getErrorCode(), ex.getErrorCode());
        assertEquals(TEMPLATE, ex.getParam(ReportAuthErrors.ARG_REPORT_PATH));

        setUserContext("u2", "rpt-admin");
        ITextTemplateOutput renderer = (ITextTemplateOutput) reportEngine.getRenderer(TEMPLATE, "html");
        assertNotNull(renderer.generateText(XLang.newEvalScope()));

        deleteDefinitionAuth(rptId);
    }

    @Test
    public void testNoUserContextFailClosed() {
        saveDefinition(TEMPLATE, "rpt-admin");
        IUserContext.set(null);

        NopException ex = assertThrows(NopException.class,
                () -> authChecker.checkReportAccess(TEMPLATE));
        assertEquals(ReportAuthErrors.ERR_REPORT_AUTH_REPORT_DENIED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testExemptRoleBypasses() {
        saveDefinition(TEMPLATE, "rpt-admin");
        AppConfig.getConfigProvider().updateConfigValue(NopReportConfigs.CFG_REPORT_AUTH_EXEMPT_ROLES, "auditor");

        try {
            setUserContext("u3", "auditor");
            authChecker.checkReportAccess(TEMPLATE);
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(NopReportConfigs.CFG_REPORT_AUTH_EXEMPT_ROLES, "");
        }
    }

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private String saveDefinition(String rptText, String authRole) {
        NopReportDefinition definition = daoProvider.daoFor(NopReportDefinition.class).newEntity();
        definition.setRptId("rpt-test-1");
        definition.setRptNo("rpt-test-1");
        definition.setRptName("auth-test-report");
        definition.setRptText(rptText);
        definition.setStatus(1);
        daoProvider.daoFor(NopReportDefinition.class).saveEntity(definition);

        NopReportDefinitionAuth auth = daoProvider.daoFor(NopReportDefinitionAuth.class).newEntity();
        auth.setRptId("rpt-test-1");
        auth.setRoleId(authRole);
        daoProvider.daoFor(NopReportDefinitionAuth.class).saveEntity(auth);
        return "rpt-test-1";
    }

    private void deleteDefinitionAuth(String rptId) {
        NopReportDefinitionAuth probe = daoProvider.daoFor(NopReportDefinitionAuth.class).newEntity();
        probe.setRptId(rptId);
        daoProvider.daoFor(NopReportDefinitionAuth.class).deleteByExample(probe);
    }

    private void saveDatasourceAuth(String dsName, String roleId) {
        NopReportDatasource datasource = daoProvider.daoFor(NopReportDatasource.class).newEntity();
        datasource.setSid("ds-test-1");
        datasource.setName(dsName);
        datasource.setDatasourceType("jdbc");
        datasource.setDatasourceConfig("{}");
        daoProvider.daoFor(NopReportDatasource.class).saveEntity(datasource);

        NopReportDatasourceAuth auth = daoProvider.daoFor(NopReportDatasourceAuth.class).newEntity();
        auth.setDatasourceId("ds-test-1");
        auth.setRoleId(roleId);
        daoProvider.daoFor(NopReportDatasourceAuth.class).saveEntity(auth);
    }
}
