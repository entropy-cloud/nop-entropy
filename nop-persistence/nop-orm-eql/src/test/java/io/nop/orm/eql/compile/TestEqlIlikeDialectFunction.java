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
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_ILIKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 第 9 项行为断言：`ilike` 语义查询在注册方言（h2）上输出含 ` ilike ` 的 SQL
 * （模板 `{0} ilike {1}` 直出原生算子）；未注册方言（mysql）保持既有负向路径
 * `ERR_EQL_NOT_SUPPORT_ILIKE`（静默降级为大小写敏感 LIKE 会改变查询语义，不允许）。
 *
 * <p>XML 配置值 → 函数表断言由 nop-dao 的 {@code TestIlikeFunctionRegistration} 承担。
 */
public class TestEqlIlikeDialectFunction {
    static final String EQL = "select o.id from AppUser o where o.name ilike 'a%'";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestEqlCompileSql.TestCompileContext contextOf(IDialect dialect) {
        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        return ctx;
    }

    /**
     * h2 注册 ilike 后，ignoreCase like 经方言函数改写输出原生 ILIKE 算子。
     */
    @Test
    public void testH2OutputsIlikeOperator() {
        IDialect h2 = DialectManager.instance().getDialect("h2");
        assertNotNull(h2);
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL, contextOf(h2));
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains(" ilike "), () -> "h2 must output native ILIKE, got: " + sql);
    }

    /**
     * 正向对照（本 WI 之前唯一注册方）：oracle 仍经 regexp_like 改写。
     */
    @Test
    public void testOracleStillRewritesViaRegexpLike() {
        IDialect oracle = DialectManager.instance().getDialect("oracle");
        assertNotNull(oracle);
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL, contextOf(oracle));
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("regexp_like"), () -> "oracle must keep regexp_like, got: " + sql);
    }

    /**
     * 未注册方言负向路径回归：mysql 编译 ilike 查询抛 ERR_EQL_NOT_SUPPORT_ILIKE。
     */
    @Test
    public void testMysqlStillThrowsNotSupportIlike() {
        IDialect mysql = DialectManager.instance().getDialect("mysql");
        assertNotNull(mysql);
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL, contextOf(mysql)));
        assertEquals(ERR_EQL_NOT_SUPPORT_ILIKE.getErrorCode(), e.getErrorCode());
    }
}
