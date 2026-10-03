/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.impl.DialectImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 第 9 项：h2 / postgresql 注册 `ilike` 函数模板（原生 ILIKE 算子直出）。
 *
 * <p>本项是**补函数注册**而非改标志：消费点 `AstToSqlGenerator.visitSqlLikeExpr` 在
 * ignoreCase 时取 `dialect.getFunction("ilike")`，取不到抛 `ERR_EQL_NOT_SUPPORT_ILIKE`。
 * ⚠️ 不改任何 `supportILike` 标志（`default.dialect.xml` 的 false 与 `h2.dialect.xml`
 * 的 true 均保持——翻标志会引入假阴性）。duckdb/h2gis/postgis 经 x:extends 传递获得。
 */
public class TestIlikeFunctionRegistration {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static DialectImpl dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertTrue(dialect instanceof DialectImpl, () -> name + " must resolve to DialectImpl");
        return (DialectImpl) dialect;
    }

    /**
     * 直接注册方言 + 继承面（h2gis 经 h2、postgis/duckdb 经 postgresql）加载后
     * `getFunction("ilike")` 均可取到。
     */
    @Test
    public void testIlikeRegisteredOnH2AndPostgresFamily() {
        for (String name : new String[]{"h2", "postgresql", "duckdb", "h2gis", "postgis"}) {
            assertNotNull(dialectOf(name).getFunction("ilike"),
                    () -> name + " must register the ilike function (WI3 item 9)");
        }
    }

    /**
     * 既有注册方 oracle 防回归（本 WI 之前全仓唯一注册方）。
     */
    @Test
    public void testOracleIlikeStillRegistered() {
        assertNotNull(dialectOf("oracle").getFunction("ilike"),
                "oracle's existing ilike template must stay (regression guard)");
    }

    /**
     * 未注册方言仍为 null（负向路径载体：AstToSqlGenerator 对其抛 ERR_EQL_NOT_SUPPORT_ILIKE，
     * 行为断言由 nop-orm-eql 承担）。
     */
    @Test
    public void testUnregisteredDialectsRemainNull() {
        assertNull(dialectOf("mysql").getFunction("ilike"),
                "mysql has no ILIKE and must not register it");
        assertNull(dialectOf("mssql").getFunction("ilike"),
                "mssql has no ILIKE and must not register it");
    }

    /**
     * 标志不被翻转的锁定断言：default 系（postgresql 继承）supportILike=false、
     * h2 显式 true——本 WI 只补函数注册，不动标志。
     */
    @Test
    public void testSupportIlikeFlagsUntouched() {
        assertFalse(dialectOf("postgresql").isSupportILike(),
                "postgresql inherits default's supportILike=false; flag must not be flipped");
        assertFalse(dialectOf("duckdb").isSupportILike(),
                "duckdb inherits postgresql's supportILike=false; flag must not be flipped");
        assertTrue(dialectOf("h2").isSupportILike(),
                "h2's explicit supportILike=true must stay");
    }
}
