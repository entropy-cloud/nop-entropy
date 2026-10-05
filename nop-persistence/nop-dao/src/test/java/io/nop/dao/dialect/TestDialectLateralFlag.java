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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 BUG-2：`supportLateral` 四层接线的 XML → 模型 → accessor 运行时连通验证。
 *
 * <p>裁定（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi1-class-a-adjudications.md` §4）：
 * 显式 true = postgresql / mysql / duckdb（`[文档]` 证据：PG 9.0+ / MySQL 8.0.14+ /
 * DuckDB friendly_sql）；显式 false = mariadb（无 LATERAL，roadmap 登记）与 mysql5.7
 * （LATERAL 需 8.0.14+，覆盖继承）；其余方言未配置 = 保守缺省 false（unverified 纪律）。
 * 加载路径经 `DialectManager` 真实读取 `*.dialect.xml`，xdef 绑定按属性名反射 setter
 * （nop-dao 无 codegen 管线，retention 手写字段）。
 */
public class TestDialectLateralFlag {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertTrue(dialect instanceof DialectImpl, () -> name + " must resolve to DialectImpl");
        return dialect;
    }

    @Test
    public void testExplicitSupporters() {
        assertTrue(dialectOf("postgresql").isSupportLateral(), "postgresql must support lateral");
        assertTrue(dialectOf("mysql").isSupportLateral(), "mysql(8.0.14+) must support lateral");
        assertTrue(dialectOf("duckdb").isSupportLateral(), "duckdb must support lateral");
        // 继承 postgresql 的显式 true（PostGIS 运行在 PG 上）
        assertTrue(dialectOf("postgis").isSupportLateral(), "postgis inherits postgresql lateral support");
    }

    @Test
    public void testExplicitNonSupporters() {
        assertFalse(dialectOf("mariadb").isSupportLateral(), "mariadb has no LATERAL syntax");
        assertFalse(dialectOf("mysql5.7").isSupportLateral(),
                "LATERAL needs MySQL 8.0.14+; explicit false must override inherited true");
    }

    @Test
    public void testUnconfiguredDefaultsToFalse() {
        // unverified 纪律：未显式配置的方言缺省保守 false（编译期报错而非静默错误 SQL）
        assertFalse(dialectOf("oracle").isSupportLateral(), "unverified boundary must default to false");
        assertFalse(dialectOf("h2").isSupportLateral(), "h2 grammar has no LATERAL");
        assertFalse(dialectOf("mssql").isSupportLateral(), "mssql uses CROSS APPLY, no LATERAL keyword");
        assertFalse(dialectOf("tdengine").isSupportLateral(), "unverified boundary must default to false");
    }
}
