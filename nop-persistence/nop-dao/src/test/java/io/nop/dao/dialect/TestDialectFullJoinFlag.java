/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.impl.DialectImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 BUG-6：`supportFullJoin` 接线的 XML → 模型 → accessor 运行时连通验证。
 *
 * <p>裁定（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi2-class-b-adjudications.md`
 * §三/§五）：已确认无 FULL JOIN 的只有 MySQL/MariaDB（roadmap WI7b 节证据：TDengine
 * 自 3.3.0.0 起支持、MSSQL 支持 FULL OUTER JOIN）⇒ mysql 显式落 false、mariadb 显式
 * 落 false（负向点名载体，自文档化）；default 的 true 不动，确认支持者继承它。
 * 编译期门行为由 nop-orm-eql 的 `TestEqlFullJoinDialectGate` 验证（两模块测试合起来
 * 构成"XML 配置值 → features → accessor → 门行为"的运行时连通证明）。
 */
public class TestDialectFullJoinFlag {

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
    public void testExplicitNonSupporters() {
        // 已确认无 FULL JOIN 的两家（roadmap WI7b 节证据），显式配置 false 作负向载体
        assertFalse(dialectOf("mysql").isSupportFullJoin(), "MySQL has no FULL JOIN syntax");
        assertFalse(dialectOf("mariadb").isSupportFullJoin(), "MariaDB has no FULL JOIN syntax");
        // mysql5.7 extends mysql ⇒ 继承 false（顺带锁定）
        assertFalse(dialectOf("mysql5.7").isSupportFullJoin(), "mysql5.7 inherits mysql's explicit false");
    }

    @Test
    public void testConfirmedSupportersInheritDefaultTrue() {
        // default.dialect.xml 的 supportFullJoin="true" 不动：确认支持者继承它
        assertTrue(dialectOf("postgresql").isSupportFullJoin(), "postgresql supports FULL JOIN");
        assertTrue(dialectOf("h2").isSupportFullJoin(), "h2 supports FULL JOIN");
        assertTrue(dialectOf("mssql").isSupportFullJoin(), "mssql supports FULL OUTER JOIN [文档]");
        assertTrue(dialectOf("tdengine").isSupportFullJoin(), "tdengine supports FULL JOIN since 3.3.0.0 [文档]");
        assertTrue(dialectOf("duckdb").isSupportFullJoin(), "duckdb supports FULL JOIN");
        assertTrue(dialectOf("oracle").isSupportFullJoin(), "oracle supports FULL JOIN");
        // postgis extends postgresql ⇒ 继承 true
        assertTrue(dialectOf("postgis").isSupportFullJoin(), "postgis inherits postgresql");
    }
}
