/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖：EQL 的 update / delete 写语句必须与 select 一样自动追加缺省租户过滤条件。
 *
 * 缺陷背景：select 的缺省过滤由 {@code addTableFilter} 注入，但 update/delete 走
 * {@code visitSqlUpdate} / {@code visitSqlDelete}，此前只设置 useTenantModel 这个
 * 「查询计划缓存标记」，并不生成 SQL 条件。实测（修复前）：
 * <pre>
 *   delete from AppTUser o where o.id = ?   -&gt;  delete from APP_T_USER where SID = ?
 *   update AppTUser o set o.name = ? ...    -&gt;  update APP_T_USER set NAME=? where SID = ?
 * </pre>
 * 即写语句完全绕过租户隔离（GenSqlHelper 只覆盖按主键与 example 的入口，EQL 写语句不经由它）。
 *
 * 同时钉住两条边界：
 * 1. 未启用租户的实体不得被注入（默认配置下全平台 199 个实体均未启用，注入必须惰性）
 * 2. 每条语句只允许出现一次租户条件，不得重复注入
 */
public class TestEqlTenantWrite {

    static TestEqlCompileSql.TestCompileContext context;

    static final String TENANT_CONDITION = "NOP_TENANT_ID = ?";

    @BeforeAll
    public static void init() {
        DialectModel model = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        features.setSupportReturningForUpdate(true);
        model.setFeatures(features);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());

        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);
        model.setFunctions(Collections.singletonList(currentTimestamp));

        IDialect dialect = new DialectImpl(model);
        // 独立 context，不复用 TestEqlTenantPropJoin 的静态实例，避免跨测试类污染
        context = new TestEqlCompileSql.TestCompileContext();
        context.effectiveDialect = dialect;
        context.entities.put("AppTUser", tenantEntity("AppTUser", "APP_T_USER", true));
        context.entities.put("AppUser", tenantEntity("AppUser", "APP_USER", false));
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setPrimary(primary);
        return c;
    }

    static OrmEntityModel tenantEntity(String name, String tableName, boolean useTenant) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setQuerySpace("spaceA");
        m.setUseTenant(useTenant);
        m.setColumns(new ArrayList<>(Arrays.asList(
                col("id", "SID", 1, true),
                col("name", "NAME", 2, false))));
        m.init();
        return m;
    }

    static String compile(String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, context);
        return compiled.getSql().getText().replaceAll("\\s+", " ").trim();
    }

    static int countTenantConditions(String sql) {
        int count = 0;
        int idx = sql.indexOf(TENANT_CONDITION);
        while (idx >= 0) {
            count++;
            idx = sql.indexOf(TENANT_CONDITION, idx + TENANT_CONDITION.length());
        }
        return count;
    }

    @Test
    public void testSelectIsBaselineWithOneTenantCondition() {
        String sql = compile("select o.id from AppTUser o");
        assertEquals(1, countTenantConditions(sql), "select 应有且仅有一个租户条件: " + sql);
    }

    @Test
    public void testDeleteAddsTenantCondition() {
        String sql = compile("delete from AppTUser o where o.id = ?");
        assertEquals(1, countTenantConditions(sql), "delete 应自动追加一个租户条件: " + sql);
        assertTrue(sql.contains("SID = ?"), "原有 where 条件必须保留: " + sql);
    }

    @Test
    public void testUpdateAddsTenantCondition() {
        String sql = compile("update AppTUser o set o.name = ? where o.id = ?");
        assertEquals(1, countTenantConditions(sql), "update 应自动追加一个租户条件: " + sql);
        assertTrue(sql.contains("NAME=?"), "set 子句必须保留: " + sql);
        assertTrue(sql.contains("SID = ?"), "原有 where 条件必须保留: " + sql);
    }

    @Test
    public void testDeleteWithoutAliasAddsTenantCondition() {
        String sql = compile("delete from AppTUser where id = ?");
        assertEquals(1, countTenantConditions(sql), "无别名写法同样应追加租户条件: " + sql);
    }

    @Test
    public void testNonTenantEntityIsNotInjected() {
        String deleteSql = compile("delete from AppUser o where o.id = ?");
        String updateSql = compile("update AppUser o set o.name = ? where o.id = ?");
        assertEquals(0, countTenantConditions(deleteSql), "未启用租户的实体不应注入: " + deleteSql);
        assertEquals(0, countTenantConditions(updateSql), "未启用租户的实体不应注入: " + updateSql);
    }

    @Test
    public void testWhereWithSubQueryInjectsOncePerScope() {
        // 外层 update/delete 各注入一次，内层 select 走 addEntityFilter 各注入一次
        String deleteSql = compile("delete from AppTUser o where o.id in (select u.id from AppTUser u)");
        assertEquals(2, countTenantConditions(deleteSql),
                "外层 delete 与内层 select 各一个租户条件，不应重复: " + deleteSql);
    }

    @Test
    public void testDeleteWithNoWhereStillBoundedByTenant() {
        String sql = compile("delete from AppTUser");
        assertEquals(1, countTenantConditions(sql), "无 where 的全表 delete 也必须受租户约束: " + sql);
        assertFalse(sql.trim().endsWith("where"), "不应产生悬空的 where: " + sql);
    }
}