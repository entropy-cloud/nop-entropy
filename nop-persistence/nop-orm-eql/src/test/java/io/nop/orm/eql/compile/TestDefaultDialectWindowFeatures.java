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
 * WI2: 窗口 frame 能力位的真实方言绑定端到端验证。
 * (h) default 方言三能力位缺省 false；(h2) fixture 方言（x:extends h2 + supportWindowFrameRows=true）
 * 经 xdef 解析 + wrapper setter + extends 合并 + IDialect getter 全链为 true（排除绑定静默 no-op）。
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
    public void testDefaultDialectWindowFrameFeaturesOff() {
        // default.dialect.xml 是被继承的基文件（缺 driverClassName 等必备节点），不能独立加载；
        // 用可独立加载的 h2 方言验证「未显式开启即 false」的集中缺省语义
        IDialect dialect = DialectManager.instance().getDialect("h2");
        assertNotNull(dialect);
        assertFalse(dialect.isSupportWindowFrameRows());
        assertFalse(dialect.isSupportWindowFrameRange());
        assertFalse(dialect.isSupportWindowFrameGroups());
    }

    @Test
    public void testFixtureDialectRowsEnabled() {
        IDialect dialect = DialectManager.instance().getDialect("test-window-features");
        assertNotNull(dialect, "fixture dialect not loaded");
        assertTrue(dialect.isSupportWindowFrameRows(), "supportWindowFrameRows should be true via x:extends merge");
        assertFalse(dialect.isSupportWindowFrameRange());
        assertFalse(dialect.isSupportWindowFrameGroups());
    }
}
