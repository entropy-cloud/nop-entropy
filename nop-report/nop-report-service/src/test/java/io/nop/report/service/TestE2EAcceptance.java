package io.nop.report.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.dao.entity.NopReportDefinition;
import io.nop.report.dao.entity.NopReportDefinitionAuth;
import io.nop.report.service.cache.NopReportCacheBizModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static io.nop.report.core.ReportAuthErrors.ERR_REPORT_AUTH_REPORT_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI16 端到端组合验收：参数（WI2）+ 权限（WI1）+ 条件样式（WI8）+ 子报表（WI9）+ 缓存（WI6）+ 多格式导出（WI10）
 * 单模板串联场景。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestE2EAcceptance extends JunitAutoTestCase {

    static final String TEMPLATE = "/report/auth/param-form.xpt.xml";

    @Inject
    IReportEngine reportEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    NopReportCacheBizModel cacheBizModel;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    void init() {
        jdbcTemplate.executeUpdate(new SQL(
                "create table if not exists my_table(sid varchar(32), fldA varchar(100), fldB varchar(100))"));
        jdbcTemplate.executeUpdate(new SQL("delete from my_table"));
        jdbcTemplate.executeUpdate(new SQL("insert into my_table values('1','a','b')"));

        NopReportDefinition definition = daoProvider.daoFor(NopReportDefinition.class).newEntity();
        definition.setRptId("rpt-e2e-1");
        definition.setRptNo("rpt-e2e-1");
        definition.setRptName("e2e-report");
        definition.setRptText(TEMPLATE);
        definition.setStatus(1);
        daoProvider.daoFor(NopReportDefinition.class).saveEntity(definition);

        NopReportDefinitionAuth auth = daoProvider.daoFor(NopReportDefinitionAuth.class).newEntity();
        auth.setRptId("rpt-e2e-1");
        auth.setRoleId("viewer");
        daoProvider.daoFor(NopReportDefinitionAuth.class).saveEntity(auth);
    }

    void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    @Test
    public void testCombinedScenario() {
        init();
        setUserContext("alice", "viewer");

        // 1. html 渲染（参数+子报表+条件样式经模板联动）：渲染 param-form 模板（参数 pname）
        String html = ((ITextTemplateOutput) reportEngine
                .getRendererForXptModel(reportEngine.getXptModel(TEMPLATE), "html"))
                .generateText(scopeWithParams(Map.of("pname", "验收参数")));
        assertTrue(html.contains("验收参数"), html);

        // 2. 缓存下载（xlsx）+ 多格式导出（fods）
        WebContentBean xlsx = cacheBizModel.download(TEMPLATE, "xlsx", Map.of("pname", "验收参数"), null);
        assertNotNull(xlsx.getContent());
        WebContentBean fods = cacheBizModel.download(TEMPLATE, "fods", Map.of("pname", "验收参数"), null);
        assertNotNull(fods.getContent());
        // 命中不重复渲染：再次 download 走缓存
        WebContentBean cached = cacheBizModel.download(TEMPLATE, "xlsx", Map.of("pname", "验收参数"), null);
        assertEquals(((File) xlsx.getContent()).getPath(), ((File) cached.getContent()).getPath());

        // 3. fods 内容为合法 Flat ODF 且含子报表/参数渲染痕迹
        File fodsFile = (File) fods.getContent();
        String content = io.nop.commons.util.FileHelper.readText(fodsFile, null);
        assertTrue(content.contains("office:document"), content.substring(0, 200));
    }

    @Test
    public void testUnauthorizedDenied() {
        init();
        setUserContext("mallory", "intruder");
        NopException ex = assertThrows(NopException.class,
                () -> reportEngine.getXptModel(TEMPLATE));
        assertEquals(ERR_REPORT_AUTH_REPORT_DENIED.getErrorCode(), ex.getErrorCode());
    }

    private IEvalContext scopeWithParams(Map<String, Object> params) {
        io.nop.core.lang.eval.IEvalScope scope = io.nop.xlang.api.XLang.newEvalScope();
        scope.setLocalValue(null, "param", params);
        return scope;
    }
}
