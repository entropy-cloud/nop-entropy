package io.nop.report.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.service.security.NopReportDataSecurityProvider;
import io.nop.report.service.security.ReportDataSecErrors;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证数据级安全（WI3）：行级过滤（取数前 SQL 子查询包装）+ 列级脱敏（展示层掩码）。
 * 规则见 _vfs/nop/report/data-security.yaml：reportPathPrefix=/report/auth/sec/ + dsName=ds1 + sid={userId}；fldA 按手机号脱敏。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestReportDataSecurity extends JunitAutoTestCase {

    static final String SEC_TEMPLATE = "/report/auth/sec-rows.xpt.xlsx";
    static final String PLAIN_TEMPLATE = "/report/auth/test-auth-jdbc.xpt.xlsx";

    @Inject
    IReportEngine reportEngine;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    NopReportDataSecurityProvider provider;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    void initTable() {
        jdbcTemplate.executeUpdate(new SQL("create table if not exists my_table(sid varchar(32), fldA varchar(100), fldB varchar(100))"));
        jdbcTemplate.executeUpdate(new SQL("delete from my_table"));
        jdbcTemplate.executeUpdate(new SQL("insert into my_table values('1','13812345678','100')"));
        jdbcTemplate.executeUpdate(new SQL("insert into my_table values('2','13900001111','200')"));
    }

    String findErrorCode(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof NopException
                    && ReportDataSecErrors.ERR_REPORT_DATA_SEC_NO_USER_CONTEXT.getErrorCode()
                            .equals(((NopException) t).getErrorCode()))
                return ((NopException) t).getErrorCode();
        }
        return ex instanceof NopException ? ((NopException) ex).getErrorCode() : null;
    }

    private void setUserContext(String userId) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet("user"));
        IUserContext.set(userContext);
    }

    private String render(String template) {
        ITextTemplateOutput renderer = (ITextTemplateOutput) reportEngine.getRenderer(template, "html");
        return renderer.generateText(XLang.newEvalScope());
    }

    @Test
    public void testRowFilterAndMask() {
        initTable();
        setUserContext("1");

        String html = render(SEC_TEMPLATE);
        assertTrue(html.contains("138****5678"), "masked phone expected: " + html);
        assertFalse(html.contains("13812345678"), "raw phone must not appear");
        assertFalse(html.contains("139****1111"), "row sid=2 must be filtered out by row condition sid={userId}");
        assertTrue(html.contains("100"), "surviving row fldB expected");
    }

    @Test
    public void testRowFilterOtherUser() {
        initTable();
        setUserContext("2");

        String html = render(SEC_TEMPLATE);
        assertTrue(html.contains("139****1111"));
        assertFalse(html.contains("138****5678"));
    }

    @Test
    public void testNoRowRuleMatchReturnsFullRows() {
        initTable();
        setUserContext("1");

        // 全局字段级 mask 规则对 plain 模板同样生效（dsName=null 保守语义），但行规则只挂 sec 前缀
        String html = render(PLAIN_TEMPLATE);
        assertTrue(html.contains("138****5678"));
        assertTrue(html.contains("139****1111"), "no row filter expected for non-sec prefix");
    }

    @Test
    public void testDatasetUntouchedByMask() {
        initTable();
        setUserContext("1");
        render(SEC_TEMPLATE);

        List<Map<String, Object>> rows = jdbcTemplate.findAll(
                new SQL("select sid, fldA, fldB from my_table"), jdbcTemplate.getRowMapper(null, true));
        assertEquals(2, rows.size());
        assertEquals("13812345678", rows.get(0).get("fldA"));
    }

    @Test
    public void testNoUserContextFailClosed() {
        initTable();
        IUserContext.set(null);

        NopException ex = assertThrows(NopException.class, () -> render(SEC_TEMPLATE));
        assertEquals(ReportDataSecErrors.ERR_REPORT_DATA_SEC_NO_USER_CONTEXT.getErrorCode(), findErrorCode(ex));
    }

    @Test
    public void testMaskHelper() {
        assertEquals("138****5678", provider.mask("13812345678", "phone"));
        assertEquals("王**", provider.mask("王小明", "name"));
        assertEquals("1101********1234", provider.mask("110101199001011234", "idCard"));
        assertEquals("****", provider.mask("abc", "full"));
        assertEquals("abc", provider.mask("abc", "unknown"));
    }
}
