/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 BUG-5 骨架：`sqlIntervalLiteral` 配置通路契约（accessor 已配置/未配置两侧 +
 * xdef 绑定真实读写）。逐方言模板内容由 WI7b 收口（唯一 owner），本 WI 不在任何生产
 * 方言 XML 上配置模板；已配置侧由 test resources 的 fixture 方言承载（先例
 * test-window-features.dialect.xml），未配置侧用 h2（生产方言）与手构模型双证。
 */
public class TestDialectSqlIntervalLiteral {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testConfiguredFixtureRoundTripsThroughXdefBinding() {
        // 接线验证：fixture XML 的 <sqlIntervalLiteral> 经 xdef 按元素名反射 setter
        // 装入 DialectSqls retention 字段，再经 IDialect accessor 读出
        IDialect dialect = DialectManager.instance().getDialect("test-interval-literal");
        assertNotNull(dialect, "fixture dialect not loaded");
        assertTrue(dialect instanceof DialectImpl);
        assertEquals("interval {value} {unit}", dialect.getSqlIntervalLiteral());
    }

    @Test
    public void testUnconfiguredProductionDialectReturnsNull() {
        // 生产方言（h2，未配置模板）→ null：WI1 不填模板内容
        IDialect h2 = DialectManager.instance().getDialect("h2");
        assertNull(h2.getSqlIntervalLiteral(), "WI1 must not configure interval templates");
    }

    @Test
    public void testHandBuiltModelContract() {
        // 手构模型契约：已配置返回文本 / 未配置返回 null（与 getForUpdateSql 同契约）
        DialectModel model = new DialectModel();
        model.setReservedKeywords(java.util.Collections.emptySet());
        DialectSqls sqls = new DialectSqls();
        model.setSqls(sqls);
        // DialectImpl 构造期会解析 current_timestamp 的渲染SQL，必须注册
        io.nop.dao.dialect.model.SqlNativeFunctionModel currentTimestamp = new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        model.setFunctions(java.util.Collections.singletonList(currentTimestamp));

        IDialect unconfigured = new DialectImpl(model);
        assertNull(unconfigured.getSqlIntervalLiteral());

        sqls.setSqlIntervalLiteral("INTERVAL '{value}'");
        IDialect configured = new DialectImpl(model);
        assertEquals("INTERVAL '{value}'", configured.getSqlIntervalLiteral());
    }
}
