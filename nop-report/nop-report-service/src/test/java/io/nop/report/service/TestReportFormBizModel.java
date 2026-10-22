package io.nop.report.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.report.dao.entity.NopReportDefinition;
import io.nop.report.dao.entity.NopReportDefinitionAuth;
import io.nop.report.service.form.NopReportFormBizModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.report.core.ReportAuthErrors.ERR_REPORT_AUTH_REPORT_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证报表参数表单接口（WI2）：schema 生成、三种候选来源、授权先行（WI1 联动）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestReportFormBizModel extends JunitAutoTestCase {

    static final String TEMPLATE_XML = "/report/auth/param-form.xpt.xml";

    @Inject
    NopReportFormBizModel formBizModel;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    @Test
    public void testFormSchemaGenerated() {
        setUserContext("u1", "user");
        Map<String, Object> schema = formBizModel.getReportFormSchema(TEMPLATE_XML);

        assertEquals("form", schema.get("type"));
        List<Map<String, Object>> body = (List<Map<String, Object>>) schema.get("body");
        assertEquals(5, body.size());

        Map<String, Object> pname = body.get(0);
        assertEquals("pname", pname.get("name"));
        assertEquals("名称", pname.get("label"));
        assertEquals(true, pname.get("required"));
        assertEquals("input-text", pname.get("type"));

        Map<String, Object> dept = body.get(1);
        assertEquals("select", dept.get("type"));
        assertEquals("static", dept.get("optionsKind"));

        Map<String, Object> status = body.get(2);
        assertEquals("dict", status.get("optionsKind"));

        Map<String, Object> city = body.get(3);
        assertEquals("sql", city.get("optionsKind"));
        assertEquals("securedDs", city.get("querySpace"));

        Map<String, Object> cityDefault = body.get(4);
        assertEquals("sql", cityDefault.get("optionsKind"));
    }

    @Test
    public void testStaticOptions() {
        setUserContext("u1", "user");
        List<Map<String, Object>> options = formBizModel.getReportParamOptions(TEMPLATE_XML, "dept", new HashMap<>());
        assertEquals(2, options.size());
        assertEquals("研发", options.get(0).get("label"));
        assertEquals("rd", options.get(0).get("value"));
    }

    @Test
    public void testDictOptions() {
        setUserContext("u1", "user");
        List<Map<String, Object>> options = formBizModel.getReportParamOptions(TEMPLATE_XML, "status", new HashMap<>());
        assertNotNull(options);
        assertTrue(options.size() >= 2, "active-status dict should have options, got: " + options);
        assertNotNull(options.get(0).get("label"));
        assertNotNull(options.get(0).get("value"));
    }

    @Test
    public void testSqlOptionsWithAuthorizedQuerySpace() {
        initCityTable();
        saveDatasourceAuth("securedDs", "ds-admin");
        setUserContext("u1", "ds-admin");

        List<Map<String, Object>> options = formBizModel.getReportParamOptions(TEMPLATE_XML, "cityDefault", new HashMap<>());
        assertEquals(1, options.size());
        assertEquals("上海", options.get(0).get("label"));
        assertEquals("sh", options.get(0).get("value"));
    }

    @Test
    public void testSqlOptionsDeniedForUnauthorizedQuerySpace() {
        initCityTable();
        saveDatasourceAuth("securedDs", "ds-admin");
        setUserContext("u1", "other-role");

        NopException ex = assertThrows(NopException.class,
                () -> formBizModel.getReportParamOptions(TEMPLATE_XML, "city", new HashMap<>()));
        assertEquals("nop.err.report.auth.datasource-denied", ex.getErrorCode());
    }

    @Test
    public void testSchemaDeniedByReportAuth() {
        saveReportAuth(TEMPLATE_XML, "rpt-admin");
        setUserContext("u1", "user");

        NopException ex = assertThrows(NopException.class,
                () -> formBizModel.getReportFormSchema(TEMPLATE_XML));
        assertEquals(ERR_REPORT_AUTH_REPORT_DENIED.getErrorCode(), ex.getErrorCode());

        NopException ex2 = assertThrows(NopException.class,
                () -> formBizModel.getReportParamOptions(TEMPLATE_XML, "dept", new HashMap<>()));
        assertEquals(ERR_REPORT_AUTH_REPORT_DENIED.getErrorCode(), ex2.getErrorCode());
    }

    @Test
    public void testUnknownParamOptions() {
        setUserContext("u1", "user");
        NopException ex = assertThrows(NopException.class,
                () -> formBizModel.getReportParamOptions(TEMPLATE_XML, "no-such", new HashMap<>()));
        assertEquals("nop.err.report.param.unknown", ex.getErrorCode());
    }

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private void initCityTable() {
        jdbcTemplate.executeUpdate(new SQL("create table if not exists param_city(code varchar(32), name varchar(100))"));
        jdbcTemplate.executeUpdate(new SQL("delete from param_city"));
        jdbcTemplate.executeUpdate(new SQL("insert into param_city values('sh','上海')"));
    }

    private void saveReportAuth(String rptText, String roleId) {
        NopReportDefinition definition = daoProvider.daoFor(NopReportDefinition.class).newEntity();
        definition.setRptId("rpt-form-1");
        definition.setRptNo("rpt-form-1");
        definition.setRptName("form-test-report");
        definition.setRptText(rptText);
        definition.setStatus(1);
        daoProvider.daoFor(NopReportDefinition.class).saveEntity(definition);

        NopReportDefinitionAuth auth = daoProvider.daoFor(NopReportDefinitionAuth.class).newEntity();
        auth.setRptId("rpt-form-1");
        auth.setRoleId(roleId);
        daoProvider.daoFor(NopReportDefinitionAuth.class).saveEntity(auth);
    }

    private void saveDatasourceAuth(String dsName, String roleId) {
        io.nop.report.dao.entity.NopReportDatasource datasource =
                daoProvider.daoFor(io.nop.report.dao.entity.NopReportDatasource.class).newEntity();
        datasource.setSid("ds-form-1");
        datasource.setName(dsName);
        datasource.setDatasourceType("jdbc");
        datasource.setDatasourceConfig("{}");
        daoProvider.daoFor(io.nop.report.dao.entity.NopReportDatasource.class).saveEntity(datasource);

        io.nop.report.dao.entity.NopReportDatasourceAuth auth =
                daoProvider.daoFor(io.nop.report.dao.entity.NopReportDatasourceAuth.class).newEntity();
        auth.setDatasourceId("ds-form-1");
        auth.setRoleId(roleId);
        daoProvider.daoFor(io.nop.report.dao.entity.NopReportDatasourceAuth.class).saveEntity(auth);
    }
}
