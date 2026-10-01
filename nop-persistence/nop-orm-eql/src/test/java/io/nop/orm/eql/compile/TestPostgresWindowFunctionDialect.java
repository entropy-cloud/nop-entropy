/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.orm.eql.OrmEqlErrors;
import io.nop.orm.eql.compile.TestEqlCompileSql.TestCompileContext;
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4: PostgreSQL 系方言（postgresql/postgis/duckdb）窗口函数登记回归。
 * 必须加载真实 dialect.xml（CoreInitialization + DialectManager），
 * 禁止合成 DialectModel（会与被修的 x:extends 链脱钩，见 TestEqlCompileSql 的合成方言差异）。
 */
public class TestPostgresWindowFunctionDialect {
    static final String[] PG_DIALECTS = {"postgresql", "postgis", "duckdb"};
    static final String[] WINDOW_FUNCTIONS = {"rank", "dense_rank", "row_number", "lead", "lag",
            "first_value", "last_value", "nth_value", "percent_rank", "cume_dist"};

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testWindowFunctionsRegistered() {
        for (String dialectName : PG_DIALECTS) {
            IDialect dialect = DialectManager.instance().getDialect(dialectName);
            assertNotNull(dialect, "dialect not loaded: " + dialectName);
            for (String fn : WINDOW_FUNCTIONS) {
                ISQLFunction f = dialect.getFunction(fn);
                assertNotNull(f, dialectName + " missing window function: " + fn);
                assertTrue(f.isOnlyForWindowExpr(), dialectName + ":" + fn + " should be onlyForWindowExpr");
            }
        }
    }

    @Test
    public void testRankOverCompilable() {
        IDialect dialect = DialectManager.instance().getDialect("postgresql");
        TestCompileContext ctx = newContext(dialect);
        ICompiledSql compiled = new EqlCompiler().compile("test",
                "select rank() over (order by o.id) from AppUser o", ctx);
        assertTrue(compiled.getSql().getText().contains("rank()"),
                "generated sql should contain rank(): " + compiled.getSql().getText());
    }

    @Test
    public void testRankOutsideWindowExprFails() {
        IDialect dialect = DialectManager.instance().getDialect("postgresql");
        TestCompileContext ctx = newContext(dialect);
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "select rank() from AppUser o", ctx));
        assertEquals(OrmEqlErrors.ERR_EQL_FUNC_ONLY_ALLOW_IN_WINDOW_EXPR.getErrorCode(), e.getErrorCode());
    }

    static TestCompileContext newContext(IDialect dialect) {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        ctx.effectiveDialect = dialect;
        return ctx;
    }
}
