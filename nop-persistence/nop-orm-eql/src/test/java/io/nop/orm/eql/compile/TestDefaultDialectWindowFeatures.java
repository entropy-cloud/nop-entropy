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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2/WI3: 窗口 frame 能力位的真实方言绑定端到端验证。
 * (1) h2 方言经 WI3 实跑矩阵开启三能力位（H2 2.4.240 七组全 PASS）；(2) fixture 方言
 * （extends h2 + 显式 supportWindowFrameRows=false）验证按属性覆盖语义——rows 关而
 * range/groups 经继承为 true，一条用例钉住 xdef 解析、wrapper setter、extends 按属性合并、
 * Boolean.TRUE.equals、IDialect getter 五环。「集中缺省 false」语义由合成方言用例
 * （TestEqlCompileSql.windowContext(false,false,false) 的关态断言）承载。
 */
public class TestDefaultDialectWindowFeatures {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testH2WindowFrameFeaturesEnabledByMatrix() {
        // WI3 实跑矩阵（TestH2WindowFrameMatrix 七组全 PASS）后 h2 显式开启
        IDialect dialect = DialectManager.instance().getDialect("h2");
        assertNotNull(dialect);
        assertTrue(dialect.isSupportWindowFrameRows());
        assertTrue(dialect.isSupportWindowFrameRange());
        assertTrue(dialect.isSupportWindowFrameGroups());
    }

    @Test
    public void testFixtureOverridesRowsOnly() {
        // fixture 显式 supportWindowFrameRows=false 覆盖 h2 的 true：按属性（非整块）合并语义
        IDialect dialect = DialectManager.instance().getDialect("test-window-features");
        assertNotNull(dialect, "fixture dialect not loaded");
        assertFalse(dialect.isSupportWindowFrameRows(), "fixture explicitly overrides rows to false");
        assertTrue(dialect.isSupportWindowFrameRange(), "range inherits true from h2");
        assertTrue(dialect.isSupportWindowFrameGroups(), "groups inherits true from h2");
    }
}
