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

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 第 1/2/3 项：`update … returning` 在真实方言上的编译期门负向用例。
 *
 * <p>`supportReturningForUpdate` 的真实语义 = UPDATE 上返回<em>投影列表形式</em>（无 INTO）
 * 的 returning（`EqlTransformVisitor.visitSqlUpdate` 展开前统一校验）。翻转后的
 * mysql/oracle/h2（及其衍生 mariadb/oracle-reverse/h2gis，经继承同值）必须编译期报
 * `nop.err.eql.dialect-not-support-feature`（feature=supportReturningForUpdate），
 * 不得静默产出目标库非法的 SQL；支持方言（postgresql，XML 现值 true）为正向对照。
 *
 * <p>XML 配置值 → accessor 链由 nop-dao 的 {@code TestDialectReturningForUpdateFlag} 验证；
 * 本类用 DialectManager 加载的真实方言证明配置值真实改变编译行为（接线验证，Minimum Rules #23）。
 */
public class TestEqlUpdateReturningDialectGate {
    static final String EQL_RETURNING = "update AppUser o set o.name = 'x' where o.id = '1' returning o.name";

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

    static String sqlOf(IDialect dialect, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, contextOf(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    static void assertGateError(String dialectName) {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, () -> "dialect not loaded: " + dialectName);
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialect, EQL_RETURNING),
                () -> dialectName + " must reject update..returning at compile time");
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode(),
                dialectName);
        assertEquals("supportReturningForUpdate", e.getParam("feature"), dialectName);
    }

    /**
     * WI3 第 1 项：mysql 翻转后 `update … returning` 编译期报错（真实方言、真实校验点）。
     */
    @Test
    public void testMysqlRejectsUpdateReturning() {
        assertGateError("mysql");
    }

    /**
     * WI3 第 2 项：oracle 翻转后投影列表形式的 returning 编译期报错。
     */
    @Test
    public void testOracleRejectsUpdateReturning() {
        assertGateError("oracle");
    }

    /**
     * 继承涟漪的编译期证明：mariadb 经继承得到 false，走同一校验点同样报错；
     * oracle-reverse 经继承 oracle 同理。
     */
    @Test
    public void testDerivedDialectsInheritGate() {
        assertGateError("mariadb");
        assertGateError("oracle-reverse");
    }

    /**
     * WI3 第 3 项：h2 翻转后 `update … returning` 编译期报错；第 4 项回归：duckdb
     * （确认正确的 false）同样拒绝——UPDATE 上的 returning 在 DuckDB 仅 INSERT/DELETE/MERGE
     * 才有，UPDATE 投影列表形式不存在。
     */
    @Test
    public void testH2AndDuckdbRejectUpdateReturning() {
        assertGateError("h2");
        assertGateError("duckdb");
    }

    /**
     * 正向对照：postgresql 现值 true（`postgresql.dialect.xml` features 行），不受本 WI 翻转影响，
     * returning 投影照常输出（postgresql 列名输出为小写）。
     */
    @Test
    public void testPostgresqlPositiveControl() {
        String sql = sqlOf(DialectManager.instance().getDialect("postgresql"), EQL_RETURNING);
        assertTrue(sql.contains("returning"), sql);
        assertTrue(sql.contains("returning name"), sql);
    }
}
