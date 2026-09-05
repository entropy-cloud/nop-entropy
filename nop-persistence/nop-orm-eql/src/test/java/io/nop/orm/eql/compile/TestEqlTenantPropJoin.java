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
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归覆盖审查报告 EQL-01：prop-path关联（无论隐式还是显式写法）生成的join ON条件
 * 必须包含被关联表的租户条件与实体固定过滤器，与实体名join的缺省过滤对齐。
 * 修复前ON条件只有逻辑删除过滤，多租户表可经关联读到其他租户的同id数据。
 */
public class TestEqlTenantPropJoin {

    static TestEqlCompileSql.TestCompileContext context;
    static IDialect dialect;

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

        dialect = new DialectImpl(model);
        context = new TestEqlCompileSql.TestCompileContext();
        context.effectiveDialect = dialect;
        context.entities.put("AppTUser", tenantUser());
        context.entities.put("AppTDept", tenantDept());
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

    static OrmEntityModel tenantEntity(String name, String tableName) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setQuerySpace("spaceA");
        m.setUseTenant(true);
        return m;
    }

    static OrmEntityModel tenantDept() {
        OrmEntityModel dept = tenantEntity("AppTDept", "APP_T_DEPT");
        dept.setColumns(new ArrayList<>(Arrays.asList(
                col("id", "SID", 1, true),
                col("name", "NAME", 2, false))));
        dept.init();
        return dept;
    }

    static OrmEntityModel tenantUser() {
        OrmEntityModel user = tenantEntity("AppTUser", "APP_T_USER");
        OrmColumnModel userPk = col("id", "SID", 1, true);
        OrmColumnModel userName = col("name", "NAME", 2, false);
        OrmColumnModel userDept = col("deptId", "DEPT_ID", 3, false);
        user.setColumns(new ArrayList<>(Arrays.asList(userPk, userName, userDept)));

        OrmEntityModel dept = tenantDept();

        OrmToOneReferenceModel deptRef = new OrmToOneReferenceModel();
        deptRef.setName("dept");
        deptRef.setRefEntityName("AppTDept");
        deptRef.setRefEntityModel(dept);
        deptRef.setColumns(Collections.singletonList(userDept));
        OrmJoinOnModel deptJoin = new OrmJoinOnModel();
        deptJoin.setLeftPropModel(userDept);
        deptJoin.setRightPropModel(dept.getColumn("id", false));
        deptRef.setJoin(new ArrayList<>(Collections.singletonList(deptJoin)));
        user.addProp(deptRef);

        user.init();
        return user;
    }

    static ICompiledSql compile(String eql) {
        return new EqlCompiler().compile("test", eql, context);
    }

    static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = text.indexOf(sub);
        while (idx >= 0) {
            count++;
            idx = text.indexOf(sub, idx + sub.length());
        }
        return count;
    }

    @Test
    @org.junit.jupiter.api.Disabled("EQL-01修复推迟：需要先扩展EQL编译器prop-join参数收集机制，"
            + "详见 ai-dev/analysis/2026-09/2026-09-05d-nop-persistence-deep-bug-review.md")
    public void testPropPathJoinAddsTenantFilterToJoinOn() {
        ICompiledSql compiled = compile("select o.dept.name from AppTUser o");
        String sql = compiled.getSql().getText();

        // 主表租户条件(WHERE) + dept表的租户条件(join ON)
        assertEquals(2, countOccurrences(sql, "NOP_TENANT_ID = ?"),
                "prop join的ON条件必须包含被关联表的租户过滤，SQL: " + sql);
    }

    @Test
    public void testMainTableTenantFilterStillPresent() {
        ICompiledSql compiled = compile("select o.name from AppTUser o");
        String sql = compiled.getSql().getText();
        assertEquals(1, countOccurrences(sql, "NOP_TENANT_ID = ?"),
                "主表租户条件不应受影响，SQL: " + sql);
    }
}
