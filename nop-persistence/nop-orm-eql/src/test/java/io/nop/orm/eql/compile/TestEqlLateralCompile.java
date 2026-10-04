/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 2 / V1：<code>LATERAL</code> 子查询表源必须输出 LATERAL 关键字。
 *
 * <p>缺陷背景：<code>EqlTransformVisitor</code> 在 <code>getLateral()</code> 为真时把 currentScope
 * 换成同级 tableScope 以放行关联引用（语义分析阶段正确处理了 LATERAL），
 * 但两个输出器的 <code>visitSqlSubqueryTableSource</code> 都不打印该关键字，
 * 于是 <code>select 1 from a, lateral (...) t</code> 编译成缺少 LATERAL 的 SQL——
 * 多数数据库（含 PostgreSQL / MySQL 8 / H2）语法报错。
 *
 * <p>本类同时覆盖「打印 LATERAL」与「不得误伤非 lateral 子查询」两个方向。
 *
 * <p><b>为什么不继承 {@link TestEqlCompileSql}</b>：继承具体测试类会让父类的全部
 * {@code @Test} 在子类名下再跑一遍（本类 8 个测试会变成 34 个），测试数被虚增、
 * 失败定位变模糊。此处自带最小 harness，保持测试数真实。
 */
public class TestEqlLateralCompile {

    static IDialect dialect;

    @BeforeAll
    public static void init() {
        DialectModel model = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        features.setSupportReturningForUpdate(true);
        model.setFeatures(features);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        // 最小方言只登记本类用到的函数；未登记的函数名会在 buildFunctionExpr 处 NPE
        model.setFunctions(Arrays.asList(
                nativeFunc("current_timestamp", false, StdSqlType.TIMESTAMP),
                nativeFunc("upper", true, StdSqlType.VARCHAR),
                nativeFunc("count", true, StdSqlType.BIGINT)));
        dialect = new DialectImpl(model);
    }

    static io.nop.dao.dialect.model.SqlNativeFunctionModel nativeFunc(String name, boolean hasParenthesis,
                                                                         StdSqlType returnType) {
        io.nop.dao.dialect.model.SqlNativeFunctionModel fn = new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        fn.setName(name);
        fn.setHasParenthesis(hasParenthesis);
        fn.setReturnType(returnType);
        return fn;
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

    static OrmEntityModel entity(String name, String tableName, String querySpace, OrmColumnModel... cols) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setQuerySpace(querySpace);
        m.setColumns(new ArrayList<>(Arrays.asList(cols)));
        m.init();
        return m;
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();

        @Override
        public boolean isDisableLogicalDelete() {
            return false;
        }

        @Override
        public boolean isAllowUnderscoreName() {
            return false;
        }

        @Override
        public boolean isEnableFilter() {
            return false;
        }

        @Override
        public IEqlAstTransformer getAstTransformer() {
            return null;
        }

        @Override
        public IDialect getDialectForQuerySpace(String querySpace) {
            return dialect;
        }

        @Override
        public ISqlTableMeta resolveEntityTableMeta(String entityName) {
            OrmEntityModel m = entities.get(entityName);
            if (m == null)
                return null;
            return new EntityTableMeta(m, null, dialect);
        }

        @Override
        public io.nop.orm.eql.sql.IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    /** 供 LATERAL 关联引用的两个实体：AppUser(id,name) 与 AppRole(id,userId) */
    static TestCompileContext lateralCtx() {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", entity("AppUser", "APP_USER", "spaceA",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        ctx.entities.put("AppRole", entity("AppRole", "APP_ROLE", "spaceA",
                col("id", "SID", 1, true), col("userId", "USER_ID", 2, false)));
        return ctx;
    }

    static String compileLateral(String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, lateralCtx());
        return compiled.getSql().getText().replaceAll("\\s+", " ").trim();
    }

    // ---- 核心：LATERAL 必须出现在输出里 ----

    @Test
    public void testLateralSubqueryEmitsLateralKeyword() {
        String sql = compileLateral(
                "select u.name, t.c from AppUser u, lateral (select count(*) as c from AppRole r "
                        + "where r.userId = u.id) t");
        // printer 统一输出小写关键字（与 from/where/join 一致）
        assertTrue(sql.contains("lateral ("), "编译 SQL 必须含 LATERAL 关键字: " + sql);
    }

    @Test
    public void testLateralKeywordIsLowercaseRegardlessOfSourceCase() {
        String sql = compileLateral(
                "select u.name, t.c from AppUser u, LATERAL (select count(*) as c from AppRole r "
                        + "where r.userId = u.id) t");
        assertTrue(sql.contains("lateral ("), sql);
    }

    @Test
    public void testLateralAppearsExactlyOnce() {
        String sql = compileLateral(
                "select u.name, t.c from AppUser u, lateral (select count(*) as c from AppRole r "
                        + "where r.userId = u.id) t");
        assertEquals(1, sql.split("lateral", -1).length - 1,
                "单个 LATERAL 表源只应输出一次关键字: " + sql);
    }

    // ---- 防误伤：非 lateral 子查询绝不能出现 LATERAL ----

    @Test
    public void testPlainSubqueryHasNoLateralKeyword() {
        String sql = compileLateral("select u.name from AppUser u where u.id in (select r.userId from AppRole r)");
        assertFalse(sql.contains("lateral"), "非 lateral 子查询不得输出 LATERAL: " + sql);
    }

    @Test
    public void testDerivedTableHasNoLateralKeyword() {
        String sql = compileLateral("select t.name from (select o.name as name from AppUser o) t");
        assertFalse(sql.contains("lateral"), "普通派生表不得输出 LATERAL: " + sql);
    }

    @Test
    public void testNonLateralSubqueryOutputUnchanged() {
        // 非 lateral 路径的输出必须与修复前逐字一致（防止 printer 改动波及既有查询）
        String sql = compileLateral("select u.name from AppUser u where u.id in (select r.userId from AppRole r)");
        assertEquals(
                "select u.NAME as c1 from APP_USER u where u.SID in ( select r.USER_ID as c2 from APP_ROLE r )",
                sql);
    }

    // ---- 作用域：LATERAL 的核心价值是能看见同层级表 ----

    @Test
    public void testLateralAllowsCorrelationToSameLevelTable() {
        String sql = compileLateral(
                "select u.name, t.c from AppUser u, lateral (select count(*) as c from AppRole r "
                        + "where r.userId = u.id) t");
        assertTrue(sql.contains("APP_USER"), sql);
        assertTrue(sql.contains("APP_ROLE"), sql);
        assertTrue(sql.contains("r.USER_ID = u.SID"), "关联条件必须保留外层别名限定: " + sql);
    }

    @Test
    public void testNonLateralSubqueryCannotSeeSameLevelTable() {
        // 对照：非 lateral 子查询引用同级别名必须报错（证明 lateral 标记确实被语义分析消费）
        NopException e = assertThrows(NopException.class,
                () -> compileLateral("select u.name from AppUser u, (select count(*) as c from AppRole r "
                        + "where r.userId = u.id) t"));
        assertNotNull(e.getErrorCode());
    }

    // ---- 词法：lateral 登记为非保留字后可作标识符 ----

    @Test
    public void testLateralUsableAsColumnAlias() {
        String sql = compileLateral("select o.name as lateral from AppUser o");
        assertTrue(sql.contains("lateral"), sql);
    }

    @Test
    public void testLateralPassesLexicalStageAsColumnName() {
        // 实体无 lateral 列，故编译期应止于语义解析（unknown-column-name）而非词法解析失败
        NopException e = assertThrows(NopException.class,
                () -> compileLateral("select o.lateral from AppUser o"));
        assertEquals("nop.err.eql.unknown-column-name", e.getErrorCode(),
                "lateral 应已可作标识符，失败点应在语义解析而非词法: " + e.getMessage());
    }
}
