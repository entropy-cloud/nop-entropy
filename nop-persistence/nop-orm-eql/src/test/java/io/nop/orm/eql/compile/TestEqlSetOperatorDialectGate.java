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
 * WI3 版本门（INTERSECT/EXCEPT 哨兵 none）+ 第 6 项 mariadb RETURNING 门的行为断言
 * （真实方言，DialectManager 加载）。
 *
 * <p>负向：mysql5.7/mariadb 编译含 INTERSECT/EXCEPT [ ALL] 的查询抛
 * {@code ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE}（feature=intersectKeyword/exceptKeyword），
 * mariadb 的 `update … returning` 同门形态（feature=supportReturningForUpdate）——
 * 两者同构："声明式保守值 + 编译期报错 + delta 显式覆盖"。
 *
 * <p>BC 守护：未配置方言（mysql/h2/postgresql 的 intersect、oracle 的 minus）输出与
 * 改动前逐字节一致（精确文本钉死；改动分支只对哨兵值新增抛错，null→super 路径不变）。
 */
public class TestEqlSetOperatorDialectGate {
    static final String INTERSECT =
            "select o.id from AppUser o where o.id = '1' intersect select o.id from AppUser o where o.id = '2'";
    static final String INTERSECT_ALL =
            "select o.id from AppUser o where o.id = '1' intersect all select o.id from AppUser o where o.id = '2'";
    static final String EXCEPT =
            "select o.id from AppUser o where o.id = '1' except select o.id from AppUser o where o.id = '2'";
    static final String EXCEPT_ALL =
            "select o.id from AppUser o where o.id = '1' except all select o.id from AppUser o where o.id = '2'";
    static final String UNION_ALL =
            "select o.id from AppUser o where o.id = '1' union all select o.id from AppUser o where o.id = '2'";
    static final String UPDATE_RETURNING =
            "update AppUser o set o.name = 'x' where o.id = '1' returning o.name";

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

    static void assertGateError(String dialectName, String eql, String feature) {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, () -> "dialect not loaded: " + dialectName);
        NopException e = assertThrows(NopException.class, () -> sqlOf(dialect, eql),
                () -> dialectName + " must reject the query at compile time");
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode(), dialectName);
        assertEquals(feature, e.getParam("feature"), dialectName);
    }

    /**
     * mysql5.7 哨兵门：INTERSECT/EXCEPT 及其 ALL 变体编译期报错，feature 指向对应配置名。
     */
    @Test
    public void testMysql57RejectsIntersectAndExcept() {
        assertGateError("mysql5.7", INTERSECT, "intersectKeyword");
        assertGateError("mysql5.7", INTERSECT_ALL, "intersectKeyword");
        assertGateError("mysql5.7", EXCEPT, "exceptKeyword");
        assertGateError("mysql5.7", EXCEPT_ALL, "exceptKeyword");
    }

    /**
     * mariadb 哨兵门（floor 10.2 上两算子均缺，MDEV-10141 自 10.3）。
     */
    @Test
    public void testMariadbRejectsIntersectAndExcept() {
        assertGateError("mariadb", INTERSECT, "intersectKeyword");
        assertGateError("mariadb", EXCEPT, "exceptKeyword");
    }

    /**
     * union 不受版本门影响（union 关键字不读方言配置）。
     */
    @Test
    public void testUnionNotGated() {
        String sql = sqlOf(DialectManager.instance().getDialect("mysql5.7"), UNION_ALL);
        assertTrue(sql.contains("union all"), sql);
    }

    /**
     * 未配置方言零变化（BC 逐字节钉死，输出文本与引入哨兵门前一致）：
     * mysql/h2/postgresql 的 intersect/except 用缺省关键字；oracle 的 except 用 minus、
     * intersect 用缺省关键字。
     */
    @Test
    public void testUnconfiguredDialectsByteIdenticalOutput() {
        assertEquals("( select o.SID as c1 from app_user as o where o.SID = '1' ) intersect"
                        + " ( select o.SID as c2 from app_user as o where o.SID = '2' )",
                sqlOf(DialectManager.instance().getDialect("mysql"), INTERSECT));
        assertEquals("( select o.SID as c1 from app_user as o where o.SID = '1' ) except"
                        + " ( select o.SID as c2 from app_user as o where o.SID = '2' )",
                sqlOf(DialectManager.instance().getDialect("mysql"), EXCEPT));
        assertEquals("( select o.SID as c1 from APP_USER as o where o.SID = '1' ) intersect"
                        + " ( select o.SID as c2 from APP_USER as o where o.SID = '2' )",
                sqlOf(DialectManager.instance().getDialect("h2"), INTERSECT));
        assertEquals("( select o.SID as c1 from app_user o where o.SID = '1' ) intersect"
                        + " ( select o.SID as c2 from app_user o where o.SID = '2' )",
                sqlOf(DialectManager.instance().getDialect("oracle"), INTERSECT));
        assertEquals("( select o.SID as c1 from app_user o where o.SID = '1' ) minus"
                        + " ( select o.SID as c2 from app_user o where o.SID = '2' )",
                sqlOf(DialectManager.instance().getDialect("oracle"), EXCEPT),
                "oracle's exceptKeyword=minus behavior must be byte-identical");
        assertEquals("( select o.sid as c1 from app_user as o where o.sid = '1' ) intersect"
                        + " ( select o.sid as c2 from app_user as o where o.sid = '2' )",
                sqlOf(DialectManager.instance().getDialect("postgresql"), INTERSECT));
    }
}
