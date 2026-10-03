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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 版本门（第 6 项 + INTERSECT/EXCEPT 门）配置断言：XML → retention 模型 → accessor
 * 的运行时连通证明（Minimum Rules #23）。
 *
 * <p>值面契约（`ai-dev/design/eql-cross-db-compat/2026-10-04-wi3-phase0-adjudications.md` §二）：
 * intersectKeyword 为 WI3 新增 xdef 属性（retention 手写字段）；null = 未配置（生成器回落
 * 缺省）；"none" = 方言无该集合算子（生成器编译期报错）；其他非空值 = 直接使用（oracle 的
 * exceptKeyword="minus" 既有在用）。空串经 xdef 绑定实测被规范化为 null，故哨兵用 none。
 *
 * <p>门行为（负向用例/未配置零变化断言）由 nop-orm-eql 的
 * {@code TestEqlSetOperatorDialectGate} 验证。
 */
public class TestDialectSetOperatorKeywords {

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
     * mysql5.7（MySQL 8.0.31 起才有 INTERSECT/EXCEPT）与 mariadb（10.3 起才有，MDEV-10141；
     * 承诺下限 10.2）显式配置哨兵 none；经 retention 字段真实读写。
     */
    @Test
    public void testSentinelConfiguredOnMysql57AndMariadb() {
        for (String name : new String[]{"mysql5.7", "mariadb"}) {
            DialectImpl dialect = dialectOf(name);
            assertEquals("none", dialect.getDialectModel().getSqls().getIntersectKeyword(),
                    () -> name + " must configure intersectKeyword=none (WI3 version gate)");
            assertEquals("none", dialect.getDialectModel().getSqls().getExceptKeyword(),
                    () -> name + " must configure exceptKeyword=none (WI3 version gate)");
            assertEquals("none", dialect.getIntersectKeyword(), name);
            assertEquals("none", dialect.getExceptKeyword(), name);
        }
    }

    /**
     * 未配置方零变化：mysql（floor 8.0.31 已含两算子，不配置）与 h2 的模型值为 null，
     * accessor 经 DialectImpl 覆写返回 null（生成器回落缺省，与引入覆写前逐字节一致）。
     */
    @Test
    public void testUnconfiguredDialectsRemainNull() {
        for (String name : new String[]{"mysql", "h2", "postgresql"}) {
            DialectImpl dialect = dialectOf(name);
            assertNull(dialect.getDialectModel().getSqls().getIntersectKeyword(),
                    () -> name + " must not configure intersectKeyword");
            assertNull(dialect.getIntersectKeyword(),
                    () -> name + " accessor returns raw null (generator falls back to default)");
        }
    }

    /**
     * 既有 minus 用法防回归：oracle 的 exceptKeyword="minus" 原样读出，intersectKeyword 未配置。
     */
    @Test
    public void testOracleMinusKeywordPreserved() {
        DialectImpl oracle = dialectOf("oracle");
        assertEquals("minus", oracle.getExceptKeyword(), "oracle's existing minus must stay");
        assertNull(oracle.getIntersectKeyword(), "oracle does not customize intersect");
    }

    /**
     * WI3 第 6 项：mariadb 显式 supportReturningForUpdate="false"（与 mysql 的 Phase 1
     * 翻转解耦、独立成声明，floor=10.2 保守值；≥13.0 经 delta 显式开启）。
     */
    @Test
    public void testMariadbReturningExplicitFalse() {
        assertFalse(dialectOf("mariadb").isSupportReturningForUpdate(),
                "mariadb declares supportReturningForUpdate=false explicitly (WI3 item 6)");
    }

    /**
     * mariadb 的 reservedKeywords 不动（roadmap 明令）：EXCEPT/INTERSECT 仍是保留字
     * （10.3+ 它们成为保留字；低版本 quote 掉也无害）。
     */
    @Test
    public void testMariadbReservedKeywordsUntouched() {
        DialectImpl mariadb = dialectOf("mariadb");
        assertNotNull(mariadb.getDialectModel().getReservedKeywords());
        assertTrue(mariadb.getDialectModel().getReservedKeywords().contains("EXCEPT"),
                "mariadb reservedKeywords must keep EXCEPT (do not touch; roadmap explicit)");
        assertTrue(mariadb.getDialectModel().getReservedKeywords().contains("INTERSECT"),
                "mariadb reservedKeywords must keep INTERSECT (do not touch; roadmap explicit)");
    }
}
