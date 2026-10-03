/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
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

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 BUG-1：`LIKE … ESCAPE` 与 `ILIKE … ESCAPE` 的执行路径语义。
 *
 * <p>缺陷（修复前）：`SqlLikeExpr.escape` 被解析进 AST，但 {@code AstToSqlGenerator.visitSqlLikeExpr}
 * 不输出它 —— escape 子句被静默丢弃，静默返回错误行集（Oracle/DB2 默认 escape 为 NULL，
 * H2/PG 默认 backslash，语义分叉无法靠默认值兜住）。
 *
 * <p>修复后（裁定 = ai-dev/design/eql-cross-db-compat/2026-10-03-wi1-class-a-adjudications.md §5）：
 * 执行路径无条件输出 `escape '<literal>'`；ILIKE+ESCAPE 组合经方言函数改写后无挂载点，
 * 显式报错（复用 ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE），不允许静默丢弃。
 * 无方言门、无"该方言不支持 ESCAPE"负向用例（plan 差异登记第 4 条）。
 */
public class TestEqlLikeEscapeSql {
    static IDialect dialect;
    static TestCompileContext context;

    @BeforeAll
    public static void init() {
        dialect = buildDialect();
        context = baseContext();
    }

    static DialectModel newDialectModel() {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        model.setFeatures(new io.nop.dao.dialect.model.DialectFeatures());
        return model;
    }

    static SqlNativeFunctionModel nativeFunc(String name, boolean hasParenthesis, StdSqlType... argTypes) {
        SqlNativeFunctionModel fn = new SqlNativeFunctionModel();
        fn.setName(name);
        fn.setHasParenthesis(hasParenthesis);
        fn.setReturnType(StdSqlType.BOOLEAN);
        fn.setArgTypes(Arrays.asList(argTypes));
        return fn;
    }

    static IDialect buildDialect() {
        DialectModel model = newDialectModel();
        // 注册 ilike 原生函数后，大小写不敏感 like 走方言函数改写路径（与既有测试方言同形）
        model.setFunctions(Arrays.asList(
                // DialectImpl 构造期会解析 current_timestamp 的渲染SQL，必须注册
                nativeFunc("current_timestamp", false),
                nativeFunc("ilike", true, StdSqlType.VARCHAR, StdSqlType.VARCHAR)));
        return new DialectImpl(model);
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setStdDataType(StdDataType.STRING);
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

    static TestCompileContext baseContext() {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", entity("AppUser", "APP_USER", "spaceA",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        return ctx;
    }

    static String sqlOf(String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, context);
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();
        boolean enableFilter;

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
            return enableFilter;
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
            return new EntityTableMeta(m, null, getDialectForQuerySpace(m.getQuerySpace()));
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    @Test
    public void testLikeEscapeClauseRendered() {
        // 修复前红：escape 子句被静默丢弃，输出只有 like
        String sql = sqlOf("select o.name from AppUser o where o.name like 'a!%' escape '!'");
        assertTrue(sql.contains("like 'a!%' escape '!'"), sql);
    }

    @Test
    public void testNotLikeEscapeClauseRendered() {
        // 基类把 not 打印在表达式之前（"not o.NAME like ..."），escape 仍缀在尾部
        String sql = sqlOf("select o.name from AppUser o where o.name not like 'a!%' escape '!'");
        assertTrue(sql.contains("not o.NAME like 'a!%' escape '!'"), sql);
    }

    @Test
    public void testIlikeWithEscapeExplicitlyRejected() {
        // 修复前红：ilike 函数改写路径静默丢弃 escape —— 修复后显式报错而非静默降级
        NopException e = assertThrows(NopException.class,
                () -> sqlOf("select o.name from AppUser o where o.name ilike 'a!%' escape '!'"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("ilike+escape", e.getParam("feature"),
                "feature param must identify the ilike+escape combination");
    }
}
